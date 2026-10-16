(ns onekeepass.mobile.events.app-lock-settings
  "App lock settings specific events"
  (:require
   [onekeepass.mobile.constants :refer [APP_LOCK_SETTINGS_PAGE_ID]]
   [onekeepass.mobile.events.common :as cmn-events :refer [on-error
                                                           update-app-lock-preference-field-data]]
   [re-frame.core :refer [dispatch reg-event-fx reg-sub subscribe]]
   [onekeepass.mobile.background :as bg]))

(defn to-app-lock-settings-page
  "Navigates to the app lock settings page"
  []
  (dispatch [:to-app-lock-settings-page]))

(defn app-lock-settings-preference-update [kw value]
  (dispatch [:app-lock-settings-preference-update kw value]))

(defn pin-entered
  "Stores a new PIN. 'pin-length' is the digit count of a fixed length PIN (iOS) or nil"
  [pin pin-length]
  (bg/pin-entered pin
                  (fn [api-reponse]
                    (if (on-error api-reponse)
                      ;; The error is shown and the settings page is shown again
                      (dispatch [:app-lock-settings-pin-setup-cancel])
                      ;; :pin-lock-enabled is already set to true in the backend 
                      ;; and the local copy is set to true in pin-store-action-completed
                      (dispatch [:pin-store-action-completed true pin-length])))))

(defn pin-setup-start
  "Shows the PIN pad to enter and confirm a new PIN in place of the settings"
  []
  (dispatch [:app-lock-settings-pin-setup-start]))

(defn pin-setup-cancel []
  (dispatch [:app-lock-settings-pin-setup-cancel]))

(defn pin-setup-active []
  (subscribe [:app-lock-settings-pin-setup-active]))

(defn pin-removed []
  (bg/pin-removed (fn [api-response]
                    (when-not (on-error api-response)
                      ;; :pin-lock-enabled is already set to false in the backend 
                      ;; and the local copy is set to false
                      (dispatch [:pin-store-action-completed false])))))

(defn app-lock-settings-pin-verified-success []
  (dispatch [:app-lock-settings-pin-verified-success]))

(defn app-lock-settings-pin-auth-completed 
  "A flag indicating that user has validated with PIN before showing app lock settings"
  []
  (subscribe [:app-lock-settings-pin-auth-completed]))

;;;;;;;;


;; Navigates to the app lock settings page
;; pin-auth-completed is used to trigger PIN auth before entering this page
(reg-event-fx
 :to-app-lock-settings-page
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db
            (assoc-in [:app-lock-settings :pin-auth-completed] false)
            (assoc-in [:app-lock-settings :pin-setup-active] false))
    :fx [[:dispatch [:common/next-page APP_LOCK_SETTINGS_PAGE_ID "appLockSettings"]]]}))

(reg-event-fx
 :app-lock-settings-pin-setup-start
 (fn [{:keys [db]} [_event-id]]
   {:db (assoc-in db [:app-lock-settings :pin-setup-active] true)}))

(reg-event-fx
 :app-lock-settings-pin-setup-cancel
 (fn [{:keys [db]} [_event-id]]
   {:db (assoc-in db [:app-lock-settings :pin-setup-active] false)}))

(reg-sub
 :app-lock-settings-pin-setup-active
 (fn [db _query-vec]
   (boolean (get-in db [:app-lock-settings :pin-setup-active]))))

;; Lock delay applied whenever the PIN lock is turned on. Same as the 'lock_timeout' default
;; in db-service-ffi app_preference.rs
;; The delay picker is hidden while the PIN lock is off, so a stored value at that point
;; was never seen by the user (older installs stored 0 - "Immediately" - by default)
(def ^:private DEFAULT-LOCK-TIMEOUT 60000)

(reg-event-fx
 :pin-store-action-completed
 (fn [{:keys [db]} [_event-id flag pin-length]]
   ;; local data upate as backend is updated
   {:db  (-> db
             (update-app-lock-preference-field-data :pin-lock-enabled flag)
             ;; The backend clears the length on both enabling and disabling and the length
             ;; of a new fixed length PIN is stored below
             (update-app-lock-preference-field-data :pin-length pin-length)
             (assoc-in [:app-lock-settings :pin-setup-active] false))
    :fx [;; Need to set :pin-auth-completed to true so that user is not asked to enter pin
         ;; after they enable the app lock and set up a new pins
         (when flag [:dispatch [:app-lock-settings-pin-verified-success]])
         (when flag [:dispatch [:app-lock-settings-preference-update :lock-timeout DEFAULT-LOCK-TIMEOUT]])
         (when (and flag pin-length)
           [:app-settings/bg-update-preference [{:app-lock-pin-length pin-length} (fn [_m])]])]}))

;; See :pin-store-completed for pin-lock-enabled updating
;; :lock-timeout :attempts-allowed 
(reg-event-fx
 :app-lock-settings-preference-update
 (fn [{:keys [db]} [_event-id kw value]]
   (let [pref-m (condp = kw
                  :lock-timeout
                  {:app-lock-timeout value}

                  :attempts-allowed
                  {:app-lock-attempts-allowed value}
                  ;; default
                  {})]
     {;; Updates local copy (should this be called after backend update ?)
      :db  (update-app-lock-preference-field-data db kw value)
      ;; Backend update only :lock-timeout :attempts-allowed 
      :fx [(when-not (empty? pref-m)
             [:app-settings/bg-update-preference [pref-m
                                                  ;; Called after a sucessfull pref update 
                                                  ;; The arg is the same as 'pref-m'
                                                  (fn [_m])]])]})))
(reg-event-fx
 :app-lock-settings-pin-verified-success
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db (assoc-in [:app-lock-settings :pin-auth-completed] true))}))

(reg-sub
 :app-lock-settings-pin-auth-completed
 (fn [db _query-vec]
   (get-in db [:app-lock-settings :pin-auth-completed])))


(comment
  (in-ns 'onekeepass.mobile.events.app-lock-settings)
  (-> @re-frame.db/app-db :app-preference :data :app-lock-preference)
  (def db-key (-> @re-frame.db/app-db :current-db-file-name)))



#_(reg-event-fx
   :pin-entered
   (fn [{:keys [_db]} [_event-id pin]]
     {:fx  [[:bg-pin-entered [pin]]]}))

#_(reg-fx
   :bg-pin-entered
   (fn [[pin]]
     (bg/pin-entered pin
                     (fn [api-reponse]
                       (when-not (on-error api-reponse)
                         (dispatch [:pin-store-completed]))))))

#_(reg-event-fx
   :pin-store-completed
   (fn [{:keys [db]} [_event-id]]
   ;; local data upate as backend is updated 
     {:db  (update-app-lock-preference-field-data db :pin-lock-enabled true)}))