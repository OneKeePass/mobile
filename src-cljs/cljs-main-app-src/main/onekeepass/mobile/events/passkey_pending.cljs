(ns onekeepass.mobile.events.passkey-pending
  "Events, effects and subscriptions for pending passkey pickup in the main app.
   Pending passkeys are created by the iOS Autofill extension during passkey registration
   and must be committed to the real KDBX database by the main app."
  (:require [onekeepass.mobile.background :as bg]
            [onekeepass.mobile.constants :refer [PASSKEY_PENDING_REVIEW_PAGE_ID]]
            [onekeepass.mobile.events.common :refer [on-error]]
            [re-frame.core :refer [dispatch reg-event-db reg-event-fx reg-fx reg-sub subscribe]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Public API ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI components must NOT use dispatch/subscribe directly - use these functions

(defn check
  "Called after a database is opened to check for pending passkeys (iOS only)"
  [db-key]
  (dispatch [:passkey-pending/check db-key]))

(defn show-review
  "Navigate to the pending passkey review page"
  []
  (dispatch [:passkey-pending/show-review]))

(defn commit
  "Save a pending passkey into the open KDBX database"
  [record-uuid db-key]
  (dispatch [:passkey-pending/commit record-uuid db-key]))

(defn discard
  "Discard a pending passkey without saving it"
  [record-uuid db-key]
  (dispatch [:passkey-pending/discard record-uuid db-key]))

(defn commit-all
  "Commit every pending passkey for the current database, then save once"
  []
  (dispatch [:passkey-pending/commit-all]))


;; Not required
#_(defn check-before-autofill-disable
    "Called when user toggles off autofill; checks for pending passkeys first (iOS only)"
    []
    (dispatch [:passkey-pending/ios-autofill-disable-check-pending-passkeys]))

#_(defn close-snackbar
    "Close the pending passkeys notification snackbar"
    []
    (dispatch [:passkey-pending/snackbar-close]))

;;;;;;;;;;;;;;;;;;;;;;;;;;; Subscriptions ;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn pending-items
  "Returns the list of pending passkey records"
  []
  (subscribe [:passkey-pending/items]))

#_(defn snackbar-open?
    "Returns true when the pending passkeys notification snackbar should be visible"
    []
    (subscribe [:passkey-pending/snackbar-open]))

(defn pending-count
  "Returns the number of pending passkey records"
  []
  (subscribe [:passkey-pending/count]))

(reg-sub
 :passkey-pending/items
 (fn [db _]
   (get-in db [:passkey-pending :items] [])))

#_(reg-sub
   :passkey-pending/snackbar-open
   (fn [db _]
     (get-in db [:passkey-pending :snackbar-open] false)))

(reg-sub
 :passkey-pending/count
 :<- [:passkey-pending/items]
 (fn [items _]
   (count items)))

;;;;;;;;;;;;;;;;;;;;;;;;;;; Events ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Called after a database is opened (iOS only).
;; Loads the pending passkey list for the given org-db-key.
(reg-event-fx
 :passkey-pending/check
 (fn [{:keys [_db]} [_event-id db-key]]
   {:fx [[:bg-passkey-pending-list [db-key]]]}))

(reg-fx
 :bg-passkey-pending-list
 (fn [[db-key]]
   (bg/pending-passkeys-list
    db-key
    (fn [api-response]
      (when-not (on-error api-response)
        #_(println "bg/pending-passkeys-list api-response" api-response)
        (dispatch [:passkey-pending/loaded (:ok api-response) db-key]))))))

;; Stores the loaded items. If non-empty, opens the notification snackbar.
#_(reg-event-db
   :passkey-pending/loaded
   (fn [db [_event-id items db-key]]
     (-> db
         (assoc-in [:passkey-pending :items] (or items []))
         (assoc-in [:passkey-pending :db-key] db-key)
         (assoc-in [:passkey-pending :snackbar-open] (boolean (seq items))))))

(reg-event-fx
 :passkey-pending/loaded
 (fn [{:keys [db]} [_event-id items db-key]]
   {:db (-> db
            (assoc-in [:passkey-pending :items] (or items []))
            (assoc-in [:passkey-pending :db-key] db-key))
    ;; This will show the dialog
    :fx [(when (> (count items) 0)
           [:dispatch [:generic-dialog-show-with-state
                       :ios-pending-passkey-notification-dialog
                       {:items-count (count items)}]])]}))

#_(reg-event-db
   :passkey-pending/snackbar-close
   (fn [db _]
     (assoc-in db [:passkey-pending :snackbar-open] false)))

;; Navigate to the review page and close the snackbar.
(reg-event-fx
 :passkey-pending/show-review
 (fn [{:keys [_db]} _]
   {:fx [#_[:dispatch [:passkey-pending/snackbar-close]]
         [:dispatch [:common/next-page PASSKEY_PENDING_REVIEW_PAGE_ID "pendingPasskeys"]]]}))

;; Commit a pending passkey to the open KDBX database.
(reg-event-fx
 :passkey-pending/commit
 (fn [{:keys [_db]} [_event-id record-uuid db-key]]
   {:fx [[:bg-passkey-commit [record-uuid db-key]]]}))

(reg-fx
 :bg-passkey-commit
 (fn [[record-uuid db-key]]
   (bg/commit-pending-passkey
    record-uuid
    db-key
    (fn [api-response]
      (when-not (on-error api-response)
        (dispatch [:passkey-pending/committed record-uuid]))))))

;; Remove committed item from the list and show success snackbar.
(reg-event-fx
 :passkey-pending/committed
 (fn [{:keys [db]} [_event-id record-uuid]]
   (let [updated (filterv #(not= (:record-uuid %) record-uuid)
                          (get-in db [:passkey-pending :items] []))]
     {:db (assoc-in db [:passkey-pending :items] updated)
      :fx [[:dispatch [:save/save-current-kdbx {}]]
           [:dispatch [:common/refresh-forms]]
           [:dispatch [:common/message-snackbar-open 'passkeySaved]]
           (when (empty? updated)
             [:dispatch [:common/previous-page]])]})))

;; Commit all pending passkeys for the current database, save once when all done.
(reg-event-fx
 :passkey-pending/commit-all
 (fn [{:keys [db]} _]
   (let [items (get-in db [:passkey-pending :items] [])]
     {:db (assoc-in db [:passkey-pending :commit-all-remaining] (count items))
      :fx (mapv (fn [{:keys [record-uuid org-db-key]}]
                  [:bg-passkey-commit-batch [record-uuid org-db-key]])
                items)})))

(reg-fx
 :bg-passkey-commit-batch
 (fn [[record-uuid db-key]]
   (bg/commit-pending-passkey
    record-uuid
    db-key
    (fn [api-response]
      (when-not (on-error api-response)
        (dispatch [:passkey-pending/committed-in-batch record-uuid]))))))

;; Removes committed item; when the last one completes: save once, show snackbar, navigate back.
(reg-event-fx
 :passkey-pending/committed-in-batch
 (fn [{:keys [db]} [_event-id record-uuid]]
   (let [updated   (filterv #(not= (:record-uuid %) record-uuid)
                            (get-in db [:passkey-pending :items] []))
         remaining (dec (get-in db [:passkey-pending :commit-all-remaining] 0))]
     {:db (-> db
              (assoc-in [:passkey-pending :items] updated)
              (assoc-in [:passkey-pending :commit-all-remaining] remaining))
      :fx (if (zero? remaining)
            [[:dispatch [:save/save-current-kdbx {}]]
             [:dispatch [:common/refresh-forms]]
             [:dispatch [:common/message-snackbar-open 'passkeySaved]]
             [:dispatch [:common/previous-page]]]
            [])})))

;; Called when user toggles off autofill.
;; If pending passkeys exist, shows warning dialog; otherwise proceeds with disable.
(reg-event-fx
 :passkey-pending/ios-autofill-disable-check-pending-passkeys
 (fn [{:keys [db]} _]
   (let [pending-count (count (get-in db [:passkey-pending :items] []))]
     (if (pos? pending-count)
       {:fx [[:dispatch [:generic-dialog-show-with-state
                         :ios-autofill-disable-pending-passkey-dialog
                         {:items-count pending-count}]]]}
       {:fx [[:dispatch [:ios-delete-copied-autofill-details]]]}))))

;; Discard a pending passkey without saving.
(reg-event-fx
 :passkey-pending/discard
 (fn [{:keys [_db]} [_event-id record-uuid db-key]]
   {:fx [[:bg-passkey-discard [record-uuid db-key]]]}))

(reg-fx
 :bg-passkey-discard
 (fn [[record-uuid db-key]]
   (bg/discard-pending-passkey
    record-uuid
    db-key
    (fn [api-response]
      (when-not (on-error api-response)
        (dispatch [:passkey-pending/discarded record-uuid]))))))

;; Remove discarded item from the list. Navigate back if list is now empty.
(reg-event-fx
 :passkey-pending/discarded
 (fn [{:keys [db]} [_event-id record-uuid]]
   (let [updated (filterv #(not= (:record-uuid %) record-uuid)
                          (get-in db [:passkey-pending :items] []))]
     {:db (assoc-in db [:passkey-pending :items] updated)
      :fx [(when (empty? updated)
             [:dispatch [:common/previous-page]])]})))


(comment
  (def db-key (-> @re-frame.db/app-db :current-db-file-name)))