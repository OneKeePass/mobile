(ns onekeepass.mobile.android.autofill.events.passkey-registration
  "Re-frame events and effects for the Android passkey registration (Credential Manager) flow.
   All event names and db keys are prefixed with :android-pk-registration to avoid conflicts."
  (:require
   [onekeepass.mobile.android.autofill.events.common :refer [android-af-active-db-key
                                                             PASSKEY_REGISTRATION_PAGE_ID
                                                             to-page]]
   [onekeepass.mobile.background :as bg]
   [onekeepass.mobile.events.common :refer [on-error on-ok]]
   [re-frame.core :refer [dispatch reg-event-fx reg-fx reg-sub subscribe]]))

;; ── Public API (for UI — no direct dispatch/subscribe in UI code) ─────────────

(defn registration-context []
  (subscribe [:android-pk-registration/context]))

(defn registration-groups []
  (subscribe [:android-pk-registration/groups]))

(defn registration-entries []
  (subscribe [:android-pk-registration/entries]))

(defn registration-selected-group []
  (subscribe [:android-pk-registration/selected-group]))

(defn registration-step []
  (subscribe [:android-pk-registration/step]))

(defn registration-new-entry-name []
  (subscribe [:android-pk-registration/new-entry-name]))

(defn registration-new-group-name []
  (subscribe [:android-pk-registration/new-group-name]))

(defn registration-error-message []
  (subscribe [:android-pk-registration/error-message]))

(defn select-group [group]
  (dispatch [:android-pk-registration/select-group group]))

(defn update-new-entry-name [name]
  (dispatch [:android-pk-registration/update-new-entry-name name]))

(defn update-new-group-name [name]
  (dispatch [:android-pk-registration/update-new-group-name name]))

(defn create-new-group-and-continue []
  (dispatch [:android-pk-registration/create-new-group-and-continue]))

(defn select-existing-entry [entry]
  (dispatch [:android-pk-registration/select-existing-entry entry]))

(defn close-after-error []
  (dispatch [:android-pk-registration/close-after-error]))

(defn create-new-entry []
  (dispatch [:android-pk-registration/create-new-entry]))

(defn registration-back []
  (dispatch [:android-pk-registration/back]))

;; ── Subscriptions ─────────────────────────────────────────────────────────────

(reg-sub
 :android-pk-registration/context
 (fn [db _]
   (get-in db [:android-af :passkey-registration])))

(reg-sub
 :android-pk-registration/groups
 (fn [db _]
   (get-in db [:android-af :passkey-registration :groups] [])))

(reg-sub
 :android-pk-registration/entries
 (fn [db _]
   (get-in db [:android-af :passkey-registration :entries] [])))

(reg-sub
 :android-pk-registration/selected-group
 (fn [db _]
   (get-in db [:android-af :passkey-registration :selected-group])))

(reg-sub
 :android-pk-registration/step
 (fn [db _]
   (get-in db [:android-af :passkey-registration :step] :group-picker)))

(reg-sub
 :android-pk-registration/new-entry-name
 (fn [db _]
   (get-in db [:android-af :passkey-registration :new-entry-name] "")))

(reg-sub
 :android-pk-registration/new-group-name
 (fn [db _]
   (get-in db [:android-af :passkey-registration :new-group-name] "")))

(reg-sub
 :android-pk-registration/error-message
 (fn [db _]
   (get-in db [:android-af :passkey-registration :error-message])))

;; ── Events ────────────────────────────────────────────────────────────────────

;; Dedicated registration context check — mirrors :android-pk/check-assertion-context.
;; Dispatched from common.cljs when android-af-context-mode is :passkey-registration-context.
(reg-event-fx
 :android-pk/check-registration-context
 (fn [{:keys [db]} _]
   ;; Ensure any previous values are reset on this launch
   {:db (-> db (assoc-in [:android-af :passkey-registration] {}))
    :fx [[:bg/android-get-registration-context nil]]}))

;; Stores registration context fields then triggers group fetch from the opened db.
;; Mirrors :android-pk/assertion-context-loaded.
(reg-event-fx
 :android-pk/registration-context-loaded
 (fn [{:keys [db]} [_ {:keys [rp-id rp-name user-name user-handle-b64url
                               client-data-hash-b64url client-data-json-b64url
                               algorithm] :as context}]]
   (println "android-pk/registration-context-loaded" context)
   (let [db-key (android-af-active-db-key db)]
     {:db (-> db
              (assoc-in [:android-af :passkey-registration :rp-id] rp-id)
              (assoc-in [:android-af :passkey-registration :rp-name] (or rp-name rp-id))
              (assoc-in [:android-af :passkey-registration :user-name] user-name)
              (assoc-in [:android-af :passkey-registration :user-handle-b64url] user-handle-b64url)
              (assoc-in [:android-af :passkey-registration :client-data-hash-b64url] client-data-hash-b64url)
              (assoc-in [:android-af :passkey-registration :client-data-json-b64url] client-data-json-b64url)
              (assoc-in [:android-af :passkey-registration :algorithm] (or algorithm -7)))
      :fx [[:bg/android-passkey-get-db-groups [db-key]]]})))

;; Dispatched by the shared :android-pk/context-loaded handler (in passkey_assertion ns)
;; when mode is "registration".
(reg-event-fx
 :android-pk-registration/context-loaded
 (fn [{:keys [db]} [_ context]]
   {:db (-> db
            (assoc-in [:android-af :passkey-registration :rp-id] (:rp-id context))
            (assoc-in [:android-af :passkey-registration :rp-name]
                      (or (:rp-name context) (:rp-id context)))
            (assoc-in [:android-af :passkey-registration :user-name] (:user-name context))
            (assoc-in [:android-af :passkey-registration :user-handle-b64url]
                      (:user-handle-b64url context))
            (assoc-in [:android-af :passkey-registration :client-data-hash-b64url]
                      (:client-data-hash-b64url context))
            (assoc-in [:android-af :passkey-registration :client-data-json-b64url]
                      (:client-data-json-b64url context))
            (assoc-in [:android-af :passkey-registration :algorithm]
                      (or (:algorithm context) -7)))}))

;; Groups loaded — store them, set step to :group-picker and navigate to registration page.
(reg-event-fx
 :android-pk-registration/groups-loaded
 (fn [{:keys [db]} [_ groups]]
   (let [rp-id (get-in db [:android-af :passkey-registration :rp-id] "")]
     {:db (-> db
              (assoc-in [:android-af :passkey-registration :groups] groups)
              (assoc-in [:android-af :passkey-registration :step] :group-picker)
              (assoc-in [:android-af :passkey-registration :new-entry-name] rp-id))
      :fx [[:dispatch-passkey-registration-page nil]]})))

;; User selected a group — store it, clear new-group-name, and fetch entries.
(reg-event-fx
 :android-pk-registration/select-group
 (fn [{:keys [db]} [_ group]]
   (let [db-key (android-af-active-db-key db)]
     {:db (-> db
              (assoc-in [:android-af :passkey-registration :selected-group] group)
              (assoc-in [:android-af :passkey-registration :new-group-name] ""))
      :fx [[:bg/android-passkey-get-group-entries [db-key (:group-uuid group)]]]})))

;; Entries loaded — store them and switch to :entry-picker step.
(reg-event-fx
 :android-pk-registration/entries-loaded
 (fn [{:keys [db]} [_ entries]]
   {:db (-> db
            (assoc-in [:android-af :passkey-registration :entries] entries)
            (assoc-in [:android-af :passkey-registration :step] :entry-picker))}))

(reg-event-fx
 :android-pk-registration/update-new-entry-name
 (fn [{:keys [db]} [_ name]]
   {:db (assoc-in db [:android-af :passkey-registration :new-entry-name] name)}))

(reg-event-fx
 :android-pk-registration/update-new-group-name
 (fn [{:keys [db]} [_ name]]
   {:db (assoc-in db [:android-af :passkey-registration :new-group-name] name)}))

;; User tapped "Create new group" — skip entry fetching, jump to entry-picker.
(reg-event-fx
 :android-pk-registration/create-new-group-and-continue
 (fn [{:keys [db]} _]
   {:db (-> db
            (assoc-in [:android-af :passkey-registration :entries] [])
            (assoc-in [:android-af :passkey-registration :step] :entry-picker))}))

;; User selected an existing entry — complete registration with that entry.
(reg-event-fx
 :android-pk-registration/select-existing-entry
 (fn [{:keys [db]} [_ entry]]
   (let [org-db-key (android-af-active-db-key db)
         rp-id      (get-in db [:android-af :passkey-registration :rp-id])
         rp-name    (get-in db [:android-af :passkey-registration :rp-name])
         user-name  (get-in db [:android-af :passkey-registration :user-name])
         user-hdl   (get-in db [:android-af :passkey-registration :user-handle-b64url])
         hash       (get-in db [:android-af :passkey-registration :client-data-hash-b64url])
         cdj        (get-in db [:android-af :passkey-registration :client-data-json-b64url])
         group      (get-in db [:android-af :passkey-registration :selected-group])
         algorithm  (get-in db [:android-af :passkey-registration :algorithm] -7)]
     {:fx [[:bg/android-complete-passkey-registration
            [org-db-key rp-id rp-name user-name user-hdl hash cdj
             (:entry-uuid entry) nil (:group-uuid group) nil algorithm]]]})))

;; User chose to create a new entry.
(reg-event-fx
 :android-pk-registration/create-new-entry
 (fn [{:keys [db]} _]
   (let [org-db-key     (android-af-active-db-key db)
         rp-id          (get-in db [:android-af :passkey-registration :rp-id])
         rp-name        (get-in db [:android-af :passkey-registration :rp-name])
         user-name      (get-in db [:android-af :passkey-registration :user-name])
         user-hdl       (get-in db [:android-af :passkey-registration :user-handle-b64url])
         hash           (get-in db [:android-af :passkey-registration :client-data-hash-b64url])
         cdj            (get-in db [:android-af :passkey-registration :client-data-json-b64url])
         group          (get-in db [:android-af :passkey-registration :selected-group])
         new-entry-name (get-in db [:android-af :passkey-registration :new-entry-name] "")
         new-group-name (not-empty (get-in db [:android-af :passkey-registration :new-group-name] ""))
         algorithm      (get-in db [:android-af :passkey-registration :algorithm] -7)]
     {:fx [[:bg/android-complete-passkey-registration
            [org-db-key rp-id rp-name user-name user-hdl hash cdj
             nil new-entry-name
             (when-not new-group-name (:group-uuid group))
             new-group-name algorithm]]]})))

(reg-event-fx
 :android-pk-registration/registration-failed
 (fn [{:keys [db]} [_ error]]
   {:db (-> db
            (assoc-in [:android-af :passkey-registration :step] :error)
            (assoc-in [:android-af :passkey-registration :error-message] (str error)))}))

;; Handles back navigation within the registration page.
;; :entry-picker → reverts to :group-picker (stays on same page)
;; :group-picker → closes db and pops the pages-stack (returns to home)
(reg-event-fx
 :android-pk-registration/back
 (fn [{:keys [db]} _]
   (let [step (get-in db [:android-af :passkey-registration :step] :group-picker)]
     (condp = step
       :entry-picker
       {:db (assoc-in db [:android-af :passkey-registration :step] :group-picker)}

       :group-picker
       {:fx [[:dispatch [:android-af/close-current-db]]
             [:dispatch [:android-af-common/previous-page]]]}

       {:fx [[:dispatch [:android-af-common/previous-page]]]}))))

;; Navigate back to home after user dismisses the error view.
;; Also cancels PasskeyActivity (RESULT_CANCELED) — needed when save failed and the activity
;; is still alive. Safe when activity is already gone (cancelPasskeyRegistration is a no-op).
(reg-event-fx
 :android-pk-registration/close-after-error
 (fn [_ _]
   {:fx [[:bg/android-cancel-passkey-registration nil]
         [:dispatch [:android-af-common/next-page :home "Home"]]]}))


(reg-event-fx
 :android-pk-registration/save-kdbx
 (println ":android-pk-registration/save-kdbx called")
 (fn [{:keys [db]} [_ _]]
   {:fx [[:dispatch [:common/message-modal-show nil 'saving]]
         [:android-pk-registration/bg-save-kdbx [(android-af-active-db-key db) false]]]}))

(reg-fx
 :android-pk-registration/bg-save-kdbx
 (fn [[db-key overwrite]]
   ;; db-key is the full file name 
   (bg/save-kdbx db-key overwrite (fn [api-response]
                                    (dispatch [:common/message-modal-hide])
                                    (when-not (on-error api-response)
                                      (println "In on-save-ok of :android-pk-registration/save-kdbx ")
                                      (dispatch [:android-pk-registration/complete]))))))

(reg-event-fx
 :android-pk-registration/complete
 (fn [{:keys [_db]} [_ _]]
   (println ":android-pk-registration/complete is called ")
   {:fx [[:bg/android-complete-passkey-registration-final]]}))

;; ── Effects ───────────────────────────────────────────────────────────────────

(reg-fx
 :bg/android-passkey-get-db-groups
 (fn [[db-key]]
   (bg/android-passkey-get-db-groups db-key
                                     (fn [response]
                                       (when-let [groups (on-ok response)]
                                         (dispatch [:android-pk-registration/groups-loaded groups]))))))

(reg-fx
 :bg/android-passkey-get-group-entries
 (fn [[db-key group-uuid]]
   (bg/android-passkey-get-group-entries db-key group-uuid
                                         (fn [response]
                                           (when-let [entries (on-ok response)]
                                             (dispatch [:android-pk-registration/entries-loaded entries]))))))

(reg-fx
 :bg/android-complete-passkey-registration
 (fn [[org-db-key rp-id rp-name user-name user-handle-b64url client-data-hash-b64url
       client-data-json-b64url entry-uuid new-entry-name group-uuid new-group-name algorithm]]

   ;; Does the first step of registration
   (bg/android-start-passkey-registration org-db-key rp-id rp-name user-name user-handle-b64url
                                          client-data-hash-b64url client-data-json-b64url
                                          entry-uuid new-entry-name group-uuid new-group-name
                                          algorithm

                                          (fn [api-response]
                                            (when-not (on-error api-response #((dispatch [:android-pk-registration/registration-failed %])))
                                              ;; Saves newly created passkey before reponding to the caller
                                              (dispatch [:android-pk-registration/save-kdbx]))))))

;; Sends the passkey registration respone to the caller
(reg-fx
 :bg/android-complete-passkey-registration-final
 (fn [_]
   (bg/android-complete-passkey-registration-final
    (fn [api-response]
      (when-not (on-error api-response)
        (println "Passkey registration completed finally")
        (dispatch [:android-af/close-current-db]))))))


;; Mirrors :bg/android-get-assertion-context in passkey_assertion ns.
(reg-fx
 :bg/android-get-registration-context
 (fn [_]
   (bg/android-get-passkey-context
    (fn [response]
      (println "bg/android-get-registration-context response" response)
      (dispatch [:android-pk/registration-context-loaded (on-ok response)])))))

(reg-fx
 :dispatch-passkey-registration-page
 (fn [_]
   (to-page PASSKEY_REGISTRATION_PAGE_ID "Register Passkey")))

(reg-fx
 :bg/android-get-registration-save-error
 (fn [dispatch-fn]
   (bg/android-get-registration-save-error dispatch-fn)))

(reg-fx
 :bg/android-cancel-passkey-registration
 (fn [_]
   (bg/android-cancel-passkey-registration (fn [_] (dispatch [:android-af/close-current-db])))))
