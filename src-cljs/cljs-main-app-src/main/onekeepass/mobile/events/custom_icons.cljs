(ns onekeepass.mobile.events.custom-icons
  "Per-database custom icons (favicon download / file upload, list / delete,
   assignment to entries and groups). Mirrors the desktop ns of the same name."
  (:require
   [onekeepass.mobile.background :as bg]
   [onekeepass.mobile.background-custom-icons :as bg-ci]
   [onekeepass.mobile.events.common :as cmn-events :refer [active-db-key
                                                           assoc-in-key-db
                                                           get-in-key-db
                                                           on-error
                                                           on-ok]]
   [re-frame.core :refer [dispatch reg-event-fx reg-fx reg-sub subscribe]]))

;; State under [(active-db-key) :custom-icons]:
;;   :icons      vector of {:uuid :name :last-modification-time}
;;   :data-urls  map of uuid -> "data:image/png;base64,..."
;;
;; data-urls is populated lazily as the UI subscribes to individual uuids.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  Subscriptions ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn icons-list
  "Returns an atom holding the vector of CustomIconSummary maps for the
   currently active database."
  []
  (subscribe [:custom-icons-list]))

(defn icon-data-url
  "Returns an atom holding the base64 data URL for the given custom icon
   uuid, fetching it lazily on first access."
  [uuid]
  (subscribe [:custom-icon-data-url uuid]))

(reg-sub
 :custom-icons-list
 (fn [db _]
   (or (get-in-key-db db [:custom-icons :icons]) [])))

(reg-sub
 :custom-icon-data-url
 (fn [db [_ uuid]]
   (get-in-key-db db [:custom-icons :data-urls uuid])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  Load / refresh  ;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn load
  "Fetches the current custom-icons list and resets the per-uuid data-url cache."
  []
  (dispatch [:custom-icons/load]))

(defn refresh
  "Reloads the list and clears the data-url cache so stale entries (e.g.
   after delete) don't linger."
  []
  (dispatch [:custom-icons/refresh]))

(reg-event-fx
 :custom-icons/load
 (fn [{:keys [db]} _]
   {:fx [[:bg-list-custom-icons [(active-db-key db)]]]}))

(reg-event-fx
 :custom-icons/refresh
 (fn [{:keys [db]} _]
   {:db (assoc-in-key-db db [:custom-icons :data-urls] {})
    :fx [[:bg-list-custom-icons [(active-db-key db)]]]}))

(reg-event-fx
 :custom-icons-loaded
 (fn [{:keys [db]} [_ icons]]
   {:db (assoc-in-key-db db [:custom-icons :icons] (vec icons))}))

(reg-fx
 :bg-list-custom-icons
 (fn [[db-key]]
   (when db-key
     (bg-ci/list-custom-icons
      db-key
      (fn [api-response]
        (when-let [icons (on-ok api-response)]
          (dispatch [:custom-icons-loaded icons])))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  Bytes (lazy data url)  ;;;;;;;;;;;;;;;;;;;

(reg-event-fx
 :custom-icons-fetch-data-url
 (fn [{:keys [db]} [_ uuid]]
   ;; If we already have it cached, do nothing.
   (let [cached (get-in-key-db db [:custom-icons :data-urls uuid])]
     (if cached
       {}
       {:fx [[:bg-get-custom-icon-data [(active-db-key db) uuid]]]}))))

(reg-event-fx
 :custom-icons-data-loaded
 (fn [{:keys [db]} [_ uuid data-url]]
   {:db (assoc-in-key-db db [:custom-icons :data-urls uuid] data-url)}))

(reg-fx
 :bg-get-custom-icon-data
 (fn [[db-key uuid]]
   (bg-ci/get-custom-icon-data
    db-key uuid
    (fn [api-response]
      (when-let [{:keys [data]} (on-ok api-response)]
        (dispatch [:custom-icons-data-loaded
                   uuid
                   (str "data:image/png;base64," data)]))))))

;; Public side-effecting fn for views — kicks off the lazy fetch.
;; A reagent component should call this in a let binding so the fetch
;; is scheduled exactly once per uuid.
(defn ensure-icon-data-url [uuid]
  (when uuid
    (dispatch [:custom-icons-fetch-data-url uuid])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  Add from URL  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn add-icon-from-url
  "Downloads a favicon from `url`, adds it as a custom icon, and dispatches
   `on-success-event` (a re-frame event vector) with the new icon uuid
   conj'd as the final argument."
  [url on-success-event]
  (dispatch [:custom-icons/add-from-url url on-success-event]))

(reg-event-fx
 :custom-icons/add-from-url
 (fn [{:keys [db]} [_ url on-success-event]]
   {:fx [[:dispatch [:common/message-modal-show nil 'downloadingFavicon]]
         [:bg-add-custom-icon-from-url
          [(active-db-key db) url on-success-event]]]}))

(reg-event-fx
 :custom-icons-add-from-url-completed
 (fn [_ [_ on-success-event {:keys [uuid]}]]
   (cond-> {:fx [[:dispatch [:common/message-modal-hide]]
                 [:dispatch [:custom-icons/refresh]]]}
     on-success-event
     (update :fx conj [:dispatch (conj on-success-event uuid)]))))

(reg-event-fx
 :custom-icons-add-failed
 (fn [_ [_ error]]
   {:fx [[:dispatch [:common/message-modal-hide]]
         [:dispatch [:common/error-box-show 'apiError error]]]}))

(reg-fx
 :bg-add-custom-icon-from-url
 (fn [[db-key url on-success-event]]
   (bg-ci/add-custom-icon-from-url
    db-key url
    (fn [api-response]
      (if (on-error api-response
                    #(dispatch [:custom-icons-add-failed %]))
        nil
        (dispatch [:custom-icons-add-from-url-completed
                   on-success-event
                   (:ok api-response)]))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  Add from file  ;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Two-step:
;; 1. User picks a file via the platform document picker. We get a URI back.
;; 2. We read the picked file's bytes server-side, normalize to PNG, and add.
;;
;; We remember `on-success-event` on app-db between those two steps so the
;; document-picked callback knows where to dispatch the new uuid.

(defn add-icon-from-file
  "Launches the platform file picker, then adds the picked image as a custom
   icon. Calls `on-success-event` (a re-frame event vector) with the new
   icon uuid conj'd as the final argument."
  [on-success-event]
  (dispatch [:custom-icons/add-from-file on-success-event]))

(reg-event-fx
 :custom-icons/add-from-file
 (fn [{:keys [db]} [_ on-success-event]]
   {:db (assoc-in-key-db db [:custom-icons :pending-on-success-event]
                         on-success-event)
    :fx [[:bg-pick-icon-file []]]}))

(reg-fx
 :bg-pick-icon-file
 (fn [_]
   ;; Reuse the existing file picker — it returns a uri via dispatch
   (bg/pick-upload-attachment
    (fn [api-response]
      (when-let [picked-uri (on-ok api-response)]
        ;; picked-uri is a string when called via call-api-async :error-transform
        (dispatch [:custom-icons-file-picked picked-uri]))))))

(reg-event-fx
 :custom-icons-file-picked
 (fn [{:keys [db]} [_ picked-uri]]
   (let [on-success-event (get-in-key-db db [:custom-icons :pending-on-success-event])]
     {:fx [[:dispatch [:common/message-modal-show nil 'addingCustomIcon]]
           [:bg-add-custom-icon-from-file
            [(active-db-key db) picked-uri on-success-event]]]})))

(reg-event-fx
 :custom-icons-add-from-file-completed
 (fn [{:keys [db]} [_ on-success-event {:keys [uuid]}]]
   (cond-> {:db (assoc-in-key-db db [:custom-icons :pending-on-success-event] nil)
            :fx [[:dispatch [:common/message-modal-hide]]
                 [:dispatch [:custom-icons/refresh]]]}
     on-success-event
     (update :fx conj [:dispatch (conj on-success-event uuid)]))))

(reg-fx
 :bg-add-custom-icon-from-file
 (fn [[db-key picked-uri on-success-event]]
   (bg-ci/add-custom-icon-from-file
    db-key picked-uri
    (fn [api-response]
      (if (on-error api-response
                    #(dispatch [:custom-icons-add-failed %]))
        nil
        (dispatch [:custom-icons-add-from-file-completed
                   on-success-event
                   (:ok api-response)]))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  Remove  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn remove-icon
  "Removes the custom icon with the given uuid from the active database.
   The remove call also clears the assignment from any entry / group that
   referenced it (handled core-side)."
  [uuid]
  (dispatch [:custom-icons/remove uuid]))

(reg-event-fx
 :custom-icons/remove
 (fn [{:keys [db]} [_ uuid]]
   {:fx [[:bg-remove-custom-icon [(active-db-key db) uuid]]]}))

(reg-event-fx
 :custom-icons-removed
 (fn [_ _]
   ;; Refresh affected lists so entries / groups that lost their custom
   ;; icon re-render with their fallback icon. There is no single
   ;; "refresh everything" event on mobile, so we trigger the lists we
   ;; know about explicitly.
   {:fx [[:dispatch [:custom-icons/refresh]]
         [:dispatch [:entry-list/reload-selected-entry-items]]
         [:dispatch [:groups/load]]]}))

(reg-fx
 :bg-remove-custom-icon
 (fn [[db-key uuid]]
   (bg-ci/remove-custom-icon
    db-key uuid
    (fn [api-response]
      (when-not (on-error api-response)
        (dispatch [:custom-icons-removed]))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  Assign  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-entry-icon
  "Assigns (or clears, with `nil`) the custom icon uuid on the given entry.
   `on-success-event` is dispatched if provided."
  [entry-uuid custom-icon-uuid on-success-event]
  (dispatch [:custom-icons/set-entry-icon
             entry-uuid custom-icon-uuid on-success-event]))

(defn set-group-icon
  [group-uuid custom-icon-uuid on-success-event]
  (dispatch [:custom-icons/set-group-icon
             group-uuid custom-icon-uuid on-success-event]))

(reg-event-fx
 :custom-icons/set-entry-icon
 (fn [{:keys [db]} [_ entry-uuid custom-icon-uuid on-success-event]]
   {:fx [[:bg-set-entry-custom-icon
          [(active-db-key db) entry-uuid custom-icon-uuid on-success-event]]]}))

(reg-event-fx
 :custom-icons/set-group-icon
 (fn [{:keys [db]} [_ group-uuid custom-icon-uuid on-success-event]]
   {:fx [[:bg-set-group-custom-icon
          [(active-db-key db) group-uuid custom-icon-uuid on-success-event]]]}))

(reg-fx
 :bg-set-entry-custom-icon
 (fn [[db-key entry-uuid custom-icon-uuid on-success-event]]
   (bg-ci/set-entry-custom-icon
    db-key entry-uuid custom-icon-uuid
    (fn [api-response]
      (when-not (on-error api-response)
        (when on-success-event (dispatch on-success-event)))))))

(reg-fx
 :bg-set-group-custom-icon
 (fn [[db-key group-uuid custom-icon-uuid on-success-event]]
   (bg-ci/set-group-custom-icon
    db-key group-uuid custom-icon-uuid
    (fn [api-response]
      (when-not (on-error api-response)
        (when on-success-event (dispatch on-success-event)))))))
