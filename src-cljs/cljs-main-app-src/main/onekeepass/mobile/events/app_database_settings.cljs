(ns onekeepass.mobile.events.app-database-settings
  (:require
   [onekeepass.mobile.constants :refer [ADDITIONAL_DATABASE_ACCESS_SETTINGS_PAGE_ID]]
   [onekeepass.mobile.events.common :as cmn-events :refer [active-db-key
                                                           database-preference-by-db-key
                                                           update-database-preference-list]]
   [re-frame.core :refer [dispatch reg-event-fx]]))


(defn to-additional-db-acess-settings-page 
  "Called from a database specific settings page"
  []
  (dispatch [:to-additional-db-acess-settings-page]))

(defn set-db-open-biometric-enabled [value]
  (dispatch [:app-database-preference-update :db-open-biometric-enabled value]))

(defn set-db-unlock-biometric-enabled [value]
  (dispatch [:app-database-preference-update :db-unlock-biometric-enabled value]))

#_(defn app-database-preference-update [kw value]
    (dispatch [:app-database-preference-update kw value]))

#_(defn app-database-preference []
    (subscribe [:app-database-preference]))

;; Navigates to the biometric settings page
(reg-event-fx
 :to-additional-db-acess-settings-page
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:dispatch [:common/next-page ADDITIONAL_DATABASE_ACCESS_SETTINGS_PAGE_ID "databaseAccess"]]]}))

;; Valid kw are [:db-open-biometric-enabled :db-unlock-biometric-enabled]
;; Should be called from database preference field update after a database is opened
;; At this time only two fields - db-open-biometric-enabled and  db-unlock-biometric-enabled - are used 

;; When we change enable or disbable the field, the backend is called immediately  
;; For now it is similar to ':app-preference-update-data' in 'onekeepass.mobile.events.app-settings'
(reg-event-fx
 :app-database-preference-update
 (fn [{:keys [db]} [_event-id kw value]]
   (let [db-pref (database-preference-by-db-key db (active-db-key db))
         db-pref (merge db-pref {kw value})]
     {;; The local data is upated 
      :db  (update-database-preference-list db db-pref)
      ;; Call backend to update  
      :fx [[:app-settings/bg-update-preference [{:database_preference db-pref}
                                                ;; on success, this fn is called
                                                (fn [_m]
                                                  (when (and (= kw :db-open-biometric-enabled) value)
                                                    (dispatch [:common/message-box-show 'biometricDbOpenEnabled 'biometricDbOpenEnabled]))
                                                  (dispatch [:common/message-snackbar-open 'updatedSettings]))]]]})))

;; Gets the DatabasePreference for the current opened database
;; Should be called when a database is opened
#_(reg-sub
   :app-database-preference
   (fn [db [_event-id]]
     (let [db-key (active-db-key db)]
       (if-not (nil? db-key)
         (database-preference-by-db-key db db-key)
         nil))))


(comment
  (in-ns 'onekeepass.mobile.events.app-database-settings)
  (def db-key (-> @re-frame.db/app-db :current-db-file-name)))
