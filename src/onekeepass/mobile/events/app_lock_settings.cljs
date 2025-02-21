(ns onekeepass.mobile.events.app-lock-settings
  "App lock settings specific events"
  (:require
   [onekeepass.mobile.constants :refer [APP_LOCK_SETTINGS_PAGE_ID]]
   [onekeepass.mobile.events.common :as cmn-events :refer [on-error
                                                           update-app-lock-preference-field-data]]
   [re-frame.core :refer [dispatch reg-event-fx reg-fx]]
   [onekeepass.mobile.background :as bg]))

(defn to-app-lock-settings-page
  "Navigates to the app lock settings page"
  []
  (dispatch [:to-app-lock-settings-page]))

(defn app-lock-preference-update [kw value]
  (dispatch [:app-lock-preference-update kw value]))

(defn pin-entered [pin]
  (bg/pin-entered pin
                  (fn [api-reponse]
                    (when-not (on-error api-reponse)
                      ;; :pin-lock-enabled is lready set to true in the backend 
                      ;; and the local copy is set to true
                      (dispatch [:pin-store-action-completed true])))))

(defn pin-removed []
  (bg/pin-removed (fn [api-response]
                    (when-not (on-error api-response)
                      ;; :pin-lock-enabled is lready set to false in the backend 
                      ;; and the local copy is set to false
                      (dispatch [:pin-store-action-completed false])))))

;; Navigates to the app lock settings page
(reg-event-fx
 :to-app-lock-settings-page
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:dispatch [:common/next-page APP_LOCK_SETTINGS_PAGE_ID "appLock"]]]}))

(reg-event-fx
 :pin-store-action-completed
 (fn [{:keys [db]} [_event-id flag]]
   ;; local data upate as backend is updated 
   {:db  (update-app-lock-preference-field-data db :pin-lock-enabled flag)}))


;; See :pin-store-completed for pin-lock-enabled updating
;; :lock-timeout :attempts-allowed 
(reg-event-fx
 :app-lock-preference-update
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
                                                  (fn [_m]
                                                    #_(println " m is " m))]])]})))


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