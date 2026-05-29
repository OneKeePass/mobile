(ns onekeepass.mobile.events.external-db-change
  "Detects external modifications to open remote (SFTP/WebDAV) kdbx databases
   and surfaces a Merge / Ignore dialog. Mirrors the desktop's external-db-change
   flow (desktop/src-cljs/.../events/external_db_change.cljs)."
  (:require
   [onekeepass.mobile.background :as bg]
   [onekeepass.mobile.background-remote-server :as bg-rs]
   [onekeepass.mobile.events.common :refer [active-db-key
                                            is-db-locked
                                            on-ok
                                            opened-db-keys
                                            remote-db-key?]]
   [re-frame.core :refer [dispatch reg-event-fx reg-fx]]))

;; ============================================================================
;; OPTIONAL: smart-route Merge by save_pending
;; ----------------------------------------------------------------------------
;; When true (default), the Merge button checks the in-memory db's save_pending
;; flag before acting:
;;   - save_pending=false (the common case for foreground poll / manual check /
;;     post-unlock — these all fire when the user isn't mid-edit): RELOAD via
;;     rs_reload_with_remote (replaces in-memory + backup content + iOS autofill
;;     copy, clears save_pending).
;;   - save_pending=true (rare; happens when the user pressed Save, hit a
;;     conflict, then tapped Cancel on the save-error-modal — their in-memory
;;     edits remain): true three-way MERGE via rs_merge_with_remote (preserves
;;     in-memory edits) then auto-saves to upload the merged result.
;;
;; To DISABLE smart routing (always reload, simpler but will silently discard
;; the rare sticky in-memory edits): set this to false.
(def ^:private smart-route-by-save-pending? true)
;; ============================================================================

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; public defns ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn poll-open-remote-dbs []
  (dispatch [:external-db-change/poll-open-remote-dbs]))

(defn manual-check-remote-changes []
  (println "Calling manual-check-remote-changes")
  (dispatch [:external-db-change/manual-check]))

(defn external-change-merge-start [db-key]
  (dispatch [:external-db-change-merge-start db-key]))

(defn external-change-ignore [db-key]
  (dispatch [:external-db-change-ignore db-key]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; events ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Auto-poll triggered by app-becomes-active. Silent on connection errors
;; (cellular timeouts shouldn't snackbar per db).
(reg-event-fx
 :external-db-change/poll-open-remote-dbs
 (fn [{:keys [db]} _]
   (let [remote-keys (filter remote-db-key? (opened-db-keys db))]
     (when (seq remote-keys)
       {:fx (mapv (fn [k] [:bg-rs-check-remote-modified [k false]]) remote-keys)}))))

;; Manual menu trigger — always reports back (an "Up to date" snackbar on no
;; change, an error snackbar on connection failure).
(reg-event-fx
 :external-db-change/manual-check
 (fn [{:keys [db]} _]
   (let [db-key (active-db-key db)]
     (when (and db-key (remote-db-key? db-key))
       ;; Clear the session-level Ignore snooze first — manual check is an
       ;; explicit re-ask, so a still-modified remote must re-surface the dialog.
       {:db (update db db-key dissoc :external-change-ignored)
        :fx [[:bg-rs-check-remote-modified [db-key true]]]}))))

(reg-fx
 :bg-rs-check-remote-modified
 (fn [[db-key manual?]]
   (println "Calling bg-rs/check-remote-modified for db-key manual?" db-key manual? )
   (bg-rs/check-remote-modified
    db-key
    (fn [api-response]
      (if manual?
        (when-some [modified? (on-ok api-response)]
          (println "In :bg-rs-check-remote-modified modified?" modified?)
          (if modified?
            (dispatch [:external-db-change/db-file-changed-externally db-key])
            (dispatch [:common/message-snackbar-open 'remoteUpToDate])))
        ;; auto-poll path: pass a no-op error fn so failures don't snackbar
        (when-some [modified? (on-ok api-response (fn [_err] nil))]
          (when modified?
            (dispatch [:external-db-change/db-file-changed-externally db-key]))))))))

;; Routes the change-detected notification. The dialog is mounted globally
;; (see core.cljs), so it can pop on whichever page the user is currently on.
;; Suppression cases:
;;   1. save-error-modal is already open — the user is already resolving the
;;      same remote-change conflict via the save-error path; piling another
;;      dialog on top would be redundant.
;;   2. db is locked — defer to post-unlock pickup via the pending flag.
;; Otherwise, show the dialog immediately.
(reg-event-fx
 :external-db-change/db-file-changed-externally
 (fn [{:keys [db]} [_event-id db-key]]
   (cond
     ;; Save-error-modal owns the conflict resolution right now — do nothing.
     (get-in db [:save-error-modal :dialog-show])
     {}

     ;; Session-level Ignore snooze — user already dismissed this change.
     ;; Cleared by manual check (the explicit re-ask path).
     (get-in db [db-key :external-change-ignored])
     {}

     (and (= db-key (active-db-key db)) (not (is-db-locked db db-key)))
     {:fx [[:dispatch [:external-db-change-show-dialog db-key]]]}

     :else
     {:db (assoc-in db [db-key :external-change-pending] true)
      :fx [[:dispatch [:common/message-snackbar-open 'externalChangePending]]]})))

;; Called after unlock or tab switch to surface a stashed pending change. If no
;; pending flag exists, silently check the remote in case it changed while locked.
(reg-event-fx
 :external-db-change/check-external-change-pending
 (fn [{:keys [db]} [_event-id db-key]]
   (cond
     (get-in db [db-key :external-change-pending])
     {:db (assoc-in db [db-key :external-change-pending] false)
      :fx [[:dispatch [:external-db-change-show-dialog db-key]]]}

     (remote-db-key? db-key)
     {:fx [[:bg-rs-check-remote-modified [db-key false]]]}

     :else
     {})))

(reg-event-fx
 :external-db-change-show-dialog
 (fn [{:keys [_db]} [_event-id db-key]]
   {:fx [[:dispatch [:generic-dialog-show-with-state
                     :external-db-change-dialog
                     {:data {:db-key db-key}}]]]}))

;; User picked Merge on the external-db-change dialog. With smart routing on
;; (default), this either reloads (save_pending=false) or true-merges and
;; auto-saves (save_pending=true). With smart routing off, this always reloads.
;; The save-error merge flow continues to use rs_merge_with_remote directly.
(reg-event-fx
 :external-db-change-merge-start
 (fn [{:keys [_db]} [_event-id db-key]]
   {:fx [[:dispatch [:generic-dialog-close :external-db-change-dialog]]
         [:dispatch [:common/message-modal-show nil "Updating from remote..."]]
         (if smart-route-by-save-pending?
           [:bg-fetch-save-pending-then-route [db-key]]
           [:bg-rs-reload-with-remote [db-key]])]}))

(reg-fx
 :bg-rs-reload-with-remote
 (fn [[db-key]]
   (bg-rs/reload-with-remote
    db-key
    (fn [api-response]
      (when-some [merge-result (on-ok api-response
                                      (fn [err]
                                        (dispatch [:external-db-change-merge-error err])))]
        (dispatch [:external-db-change-merge-completed merge-result]))))))

;; ============================================================================
;; OPTIONAL: smart routing implementation (used only when
;; smart-route-by-save-pending? is true). If you toggle the flag off OR want to
;; rip out the optional path entirely, delete this entire block — everything
;; here is unreferenced when the flag is false.
;; ----------------------------------------------------------------------------

;; Fetch save_pending from the backend, then route Merge action accordingly.
;; On any error fetching, default to reload (safer for the common no-edit case).
(reg-fx
 :bg-fetch-save-pending-then-route
 (fn [[db-key]]
   (bg/kdbx-save-pending
    db-key
    (fn [api-response]
      (when-some [save-pending? (on-ok api-response (fn [_err] false))]
        (if save-pending?
          (dispatch [:external-db-change-true-merge-with-remote db-key])
          (dispatch [:bg-rs-reload-with-remote-after-route db-key])))))))

;; Wrapper event so we can dispatch (vs reg-fx vector) from the callback above.
(reg-event-fx
 :bg-rs-reload-with-remote-after-route
 (fn [_ [_event-id db-key]]
   {:fx [[:bg-rs-reload-with-remote [db-key]]]}))

;; True three-way merge to preserve user's in-memory edits, then chain a save
;; to upload the merged content. Reuses rs_merge_with_remote (the same backend
;; the save-error path uses).
(reg-event-fx
 :external-db-change-true-merge-with-remote
 (fn [_ [_event-id db-key]]
   {:fx [[:bg-rs-true-merge-with-remote [db-key]]]}))

(reg-fx
 :bg-rs-true-merge-with-remote
 (fn [[db-key]]
   (bg-rs/merge-with-remote
    db-key
    (fn [api-response]
      (when-some [merge-result (on-ok api-response
                                      (fn [err]
                                        (dispatch [:external-db-change-merge-error err])))]
        (dispatch [:external-db-change-true-merge-then-save merge-result]))))))

(reg-event-fx
 :external-db-change-true-merge-then-save
 (fn [_ [_event-id merge-result]]
   {:fx [[:dispatch [:save/save-current-kdbx
                     {:error-title "Save after external-change merge"
                      :save-message "Saving merged database..."
                      :on-save-ok (fn []
                                    (dispatch [:external-db-change-merge-completed merge-result]))}]]]}))

;; ============================================================================

(reg-event-fx
 :external-db-change-merge-completed
 (fn [{:keys [_db]} [_event-id merge-result]]
   {:fx [[:dispatch [:common/message-modal-hide]]
         [:dispatch [:common/refresh-forms]]
         [:dispatch [:generic-dialog-show-with-state :merge-result-dialog {:data merge-result}]]
         [:dispatch [:common/message-snackbar-open 'remoteUpdated]]]}))

(reg-event-fx
 :external-db-change-merge-error
 (fn [{:keys [_db]} [_event-id error]]
   {:fx [[:dispatch [:common/message-modal-hide]]
         [:dispatch [:common/error-box-show 'mergeFailed error]]]}))

;; Session-level snooze. Sets a cljs flag (not a backend acknowledge), so:
;;   - Foreground poll suppresses the dialog while the flag is set.
;;   - The save-time guard (rs_write_file → is_rs_file_modified) stays accurate;
;;     a subsequent Save still triggers save-error-modal as a safety net.
;;   - Manual check clears the flag (the explicit re-ask path).
;;   - Flag is in-memory only; lost on app restart, so a fresh foreground poll
;;     after restart will re-detect.
(reg-event-fx
 :external-db-change-ignore
 (fn [{:keys [db]} [_event-id db-key]]
   {:db (assoc-in db [db-key :external-change-ignored] true)
    :fx [[:dispatch [:generic-dialog-close :external-db-change-dialog]]
         [:dispatch [:common/message-box-show 'externalDbChangedIgnored 'externalDbChangedIgnored]]]}))
