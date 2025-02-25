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

(defn pin-entered [pin]
  (bg/pin-entered pin
                  (fn [api-reponse]
                    (when-not (on-error api-reponse)
                      ;; :pin-lock-enabled is already set to true in the backend 
                      ;; and the local copy is set to true in pin-store-action-completed
                      (dispatch [:pin-store-action-completed true])))))

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
   {:db (-> db (assoc-in [:app-lock-settings :pin-auth-completed] false))
    :fx [[:dispatch [:common/next-page APP_LOCK_SETTINGS_PAGE_ID "appLockSettings"]]]}))

(reg-event-fx
 :pin-store-action-completed
 (fn [{:keys [db]} [_event-id flag]]
   ;; local data upate as backend is updated 
   {:db  (update-app-lock-preference-field-data db :pin-lock-enabled flag)
    ;; Need to set :pin-auth-completed to true so that user is not asked to enter pin 
    ;; after they enable the app lock and set up a new pins
    :fx [(when flag [:dispatch [:app-lock-settings-pin-verified-success]])]}))

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