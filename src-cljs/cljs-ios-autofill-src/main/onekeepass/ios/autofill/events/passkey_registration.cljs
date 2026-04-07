(ns onekeepass.ios.autofill.events.passkey-registration
  "Re-frame events and effects for the passkey registration (iOS autofill) flow."
  (:require
   [onekeepass.ios.autofill.background :as bg]
   [onekeepass.ios.autofill.constants :refer [PASSKEY_REGISTRATION_PAGE_ID]]
   [onekeepass.ios.autofill.events.common :refer [on-ok org-db-file-path]]
   [onekeepass.ios.autofill.translation :refer [lstr-pt]]
   [re-frame.core :refer [dispatch reg-event-fx reg-fx reg-sub subscribe]]))

;; ── Public API (for UI — no direct dispatch/subscribe in UI code) ───────────

(defn registration-context []
  (subscribe [:passkey-registration/context]))

(defn registration-groups []
  (subscribe [:passkey-registration/groups]))

(defn registration-entries []
  (subscribe [:passkey-registration/entries]))

(defn registration-selected-group []
  (subscribe [:passkey-registration/selected-group]))

(defn registration-new-entry-name []
  (subscribe [:passkey-registration/new-entry-name]))

(defn registration-new-group-name []
  (subscribe [:passkey-registration/new-group-name]))

(defn update-new-group-name [name]
  (dispatch [:passkey-registration/update-new-group-name name]))

(defn create-new-group-and-continue []
  (dispatch [:passkey-registration/create-new-group-and-continue]))

(defn registration-step []
  (subscribe [:passkey-registration/step]))

(defn registration-error-message []
  (subscribe [:passkey-registration/error-message]))

(defn close-after-error []
  (dispatch [:passkey-registration/close-after-error]))

(defn select-group [group]
  (dispatch [:passkey-registration/select-group group]))

(defn update-new-entry-name [name]
  (dispatch [:passkey-registration/update-new-entry-name name]))

(defn select-existing-entry [entry]
  (dispatch [:passkey-registration/select-existing-entry entry]))

(defn create-new-entry []
  (dispatch [:passkey-registration/create-new-entry]))

;; ── Subscriptions ────────────────────────────────────────────────────────────

(reg-sub
 :passkey-registration/context
 (fn [db _]
   (get-in db [:passkey-registration])))

(reg-sub
 :passkey-registration/groups
 (fn [db _]
   (get-in db [:passkey-registration :groups] [])))

(reg-sub
 :passkey-registration/entries
 (fn [db _]
   (get-in db [:passkey-registration :entries] [])))

(reg-sub
 :passkey-registration/selected-group
 (fn [db _]
   (get-in db [:passkey-registration :selected-group])))

(reg-sub
 :passkey-registration/new-entry-name
 (fn [db _]
   (get-in db [:passkey-registration :new-entry-name] "")))

(reg-sub
 :passkey-registration/new-group-name
 (fn [db _]
   (get-in db [:passkey-registration :new-group-name] "")))

(reg-sub
 :passkey-registration/step
 (fn [db _]
   (get-in db [:passkey-registration :step] :group-picker)))

(reg-sub
 :passkey-registration/error-message
 (fn [db _]
   (get-in db [:passkey-registration :error-message])))

;; ── Events ───────────────────────────────────────────────────────────────────

;; Dispatched at startup to check whether iOS launched this extension
;; for a passkey registration request.
(reg-event-fx
 :passkey-registration/check-context
 (fn [_ _]
   {:fx [[:bg/get-pending-passkey-registration-context nil]]}))

;; Stores the registration context returned by getPendingPasskeyRegistrationContext.
;; context is {:rp-id "..." :user-name "..." :user-handle-b64url "..."
;;             :client-data-hash-b64url "..." :algorithm -7|-8} or nil.
(reg-event-fx
 :passkey-registration/context-loaded
 (fn [{:keys [db]} [_ context]]
   {:db (if context
          (-> db
              (assoc-in [:passkey-registration :rp-id] (:rp-id context))
              (assoc-in [:passkey-registration :user-name] (:user-name context))
              (assoc-in [:passkey-registration :user-handle-b64url] (:user-handle-b64url context))
              (assoc-in [:passkey-registration :client-data-hash-b64url] (:client-data-hash-b64url context))
              (assoc-in [:passkey-registration :algorithm] (or (:algorithm context) -7)))
          db)}))

;; Called after DB unlock when in registration mode. Fetches groups from the opened database.
(reg-event-fx
 :passkey-registration/load-groups
 (fn [{:keys [_db]} [_ db-key]]
   {:fx [[:bg/get-db-groups [db-key]]]}))

;; Groups loaded — store them, navigate to registration page, set step to :group-picker.
(reg-event-fx
 :passkey-registration/groups-loaded
 (fn [{:keys [db]} [_ groups]]
   (let [rp-id (get-in db [:passkey-registration :rp-id] "")]
     {:db (-> db
              (assoc-in [:passkey-registration :groups] groups)
              (assoc-in [:passkey-registration :step] :group-picker)
              (assoc-in [:passkey-registration :new-entry-name] rp-id))
      :fx [[:dispatch [:common/next-page PASSKEY_REGISTRATION_PAGE_ID (lstr-pt 'registerPasskey)]]]})))

;; User selected a group — store it, clear new-group-name, and fetch entries.
(reg-event-fx
 :passkey-registration/select-group
 (fn [{:keys [db]} [_ group]]
   (let [db-key (:current-db-file-name db)]
     {:db (-> db
              (assoc-in [:passkey-registration :selected-group] group)
              (assoc-in [:passkey-registration :new-group-name] ""))
      :fx [[:bg/get-group-entries [db-key (:group-uuid group)]]]})))

;; Entries loaded — store them and switch to :entry-picker step.
(reg-event-fx
 :passkey-registration/entries-loaded
 (fn [{:keys [db]} [_ entries]]
   {:db (-> db
            (assoc-in [:passkey-registration :entries] entries)
            (assoc-in [:passkey-registration :step] :entry-picker))}))

;; User edited the new entry name text field.
(reg-event-fx
 :passkey-registration/update-new-entry-name
 (fn [{:keys [db]} [_ name]]
   {:db (assoc-in db [:passkey-registration :new-entry-name] name)}))

;; User edited the new group name text field.
(reg-event-fx
 :passkey-registration/update-new-group-name
 (fn [{:keys [db]} [_ name]]
   {:db (assoc-in db [:passkey-registration :new-group-name] name)}))

;; User tapped "Create new group" — skip entry fetching, jump straight to entry-picker.
(reg-event-fx
 :passkey-registration/create-new-group-and-continue
 (fn [{:keys [db]} _]
   {:db (-> db
            (assoc-in [:passkey-registration :entries] [])
            (assoc-in [:passkey-registration :step] :entry-picker))}))

;; User selected an existing entry — complete registration with that entry.
(reg-event-fx
 :passkey-registration/select-existing-entry
 (fn [{:keys [db]} [_ entry]]
   (let [db-key     (:current-db-file-name db)
         org-db-key (org-db-file-path db db-key)
         rp-id      (get-in db [:passkey-registration :rp-id])
         user-name  (get-in db [:passkey-registration :user-name])
         user-hdl   (get-in db [:passkey-registration :user-handle-b64url])
         hash       (get-in db [:passkey-registration :client-data-hash-b64url])
         group      (get-in db [:passkey-registration :selected-group])
         algorithm  (get-in db [:passkey-registration :algorithm] -7)]
     {:fx [[:bg/complete-passkey-registration
            [org-db-key rp-id rp-id user-name user-hdl hash
             (:entry-uuid entry) nil (:group-uuid group) nil algorithm]]]})))

;; User chose to create a new entry — complete registration with new-entry-name.
;; When new-group-name is set, pass it as the last arg and nil for group-uuid so
;; the backend creates the group on the fly.
(reg-event-fx
 :passkey-registration/create-new-entry
 (fn [{:keys [db]} [_]]
   (let [db-key         (:current-db-file-name db)
         org-db-key     (org-db-file-path db db-key)
         rp-id          (get-in db [:passkey-registration :rp-id])
         user-name      (get-in db [:passkey-registration :user-name])
         user-hdl       (get-in db [:passkey-registration :user-handle-b64url])
         hash           (get-in db [:passkey-registration :client-data-hash-b64url])
         group          (get-in db [:passkey-registration :selected-group])
         new-entry-name (get-in db [:passkey-registration :new-entry-name] "")
         new-group-name (not-empty (get-in db [:passkey-registration :new-group-name] ""))
         algorithm      (get-in db [:passkey-registration :algorithm] -7)]
     {:fx [[:bg/complete-passkey-registration
            [org-db-key rp-id rp-id user-name user-hdl hash
             nil new-entry-name
             (when-not new-group-name (:group-uuid group))
             new-group-name algorithm]]]})))

;; Called after completePasskeyRegistration returns — extension is already completed by Swift.
(reg-event-fx
 :passkey-registration/completed
 (fn [_ _]
   {}))

;; Sets step to :error and stores error message in db
(reg-event-fx
 :passkey-registration/registration-failed
 (fn [{:keys [db]} [_ error]]
   {:db (-> db
            (assoc-in [:passkey-registration :step] :error)
            (assoc-in [:passkey-registration :error-message] (str error)))}))

;; Cancels the extension after user dismisses the error view
(reg-event-fx
 :passkey-registration/close-after-error
 (fn [_ _]
   {:fx [[:bg/cancel-extension nil]]}))

;; ── Effects ──────────────────────────────────────────────────────────────────

(reg-fx
 :bg/get-pending-passkey-registration-context
 (fn [_]
   (bg/get-pending-passkey-registration-context
    (fn [response]
      #_(println "REGISTRATION - bg/get-pending-passkey-registration-context handles response" response)
      (dispatch [:passkey-registration/context-loaded (on-ok response)])))))

(reg-fx
 :bg/get-db-groups
 (fn [[db-key]]
   (bg/get-db-groups db-key
    (fn [response]
      (when-let [groups (on-ok response)]
        (dispatch [:passkey-registration/groups-loaded groups]))))))

(reg-fx
 :bg/get-group-entries
 (fn [[db-key group-uuid]]
   (bg/get-group-entries db-key group-uuid
    (fn [response]
      (when-let [entries (on-ok response)]
        (dispatch [:passkey-registration/entries-loaded entries]))))))

(reg-fx
 :bg/complete-passkey-registration
 (fn [[org-db-key rp-id rp-name user-name user-handle-b64url client-data-hash-b64url
       entry-uuid new-entry-name group-uuid new-group-name algorithm]]
   (bg/complete-passkey-registration
    org-db-key rp-id rp-name user-name user-handle-b64url client-data-hash-b64url
    entry-uuid new-entry-name group-uuid new-group-name algorithm
    (fn [response]
      #_(println "bg/complete-passkey-registration response" response)
      (if-let [_ok (on-ok response
                          (fn [error]
                            (dispatch [:passkey-registration/registration-failed error])))]
        (dispatch [:passkey-registration/completed])
        nil)))))

(reg-fx
 :bg/cancel-extension
 (fn [_]
   (bg/cancel-extension (fn [_] nil))))
