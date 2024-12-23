(ns onekeepass.mobile.events.app-database-settings
  (:require
   [onekeepass.mobile.constants :refer [BIOMETRIC_SETTINGS_PAGE_ID]]
   [onekeepass.mobile.events.common :as cmn-events :refer [active-db-key
                                                           assoc-in-key-db
                                                           database-preference-by-db-key
                                                           update-database-preference-list]]
   [re-frame.core :refer [dispatch reg-event-fx reg-fx reg-sub subscribe]]))




;; Called to initiate backend api call 
;; and then navigates to the biometric settings page
(reg-event-fx
 :to-biometric-settings-start
 (fn [{:keys [db]} [_event-id]]
   {:fx [[:bg-biometric-settings-info [(active-db-key db)]]]}))

(reg-fx
 :bg-biometric-settings-db-info
 (fn [[db-key]]
   #_(bg/biometric-settings-info db-key
                                 (fn [api-response]
                                   (when-not (on-error api-response)
                                      ;; Need to get the info found in :ok 
                                     (dispatch [:biometric-settings-info-loaded (:ok api-response)]))))))


;; Called when user navigates to the biometric settings page
(reg-event-fx
 :biometric-settings-info-loaded
 (fn [{:keys [db]} [_event-id info]]
   ;; info is a map corresponding to the struct 'CopiedDbFileInfo'
   {:db (assoc-in-key-db db [:biometric-settings] info)
    :fx [[:dispatch [:to-biometric-settings-page]]]}))


;; Navigates to the biometric settings page
(reg-event-fx
 :to-biometric-settings-page
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:dispatch [:common/next-page BIOMETRIC_SETTINGS_PAGE_ID "biometric"]]]}))


;;;;;;;;;;;;;;;;;;;;;;;;


#_(defn app-database-preference-update [kw value]
  (dispatch [:app-database-preference-update kw value]))

(defn set-db-open-biometric-enabled [value] 
  (dispatch [:app-database-preference-update :db-open-biometric-enabled value]))

(defn set-db-unlock-biometric-enabled [value]
  (dispatch [:app-database-preference-update :db-unlock-biometric-enabled value]))

(defn app-database-preference []
  (subscribe [:app-database-preference]))

;; Valid kw are [:db-open-biometric-enabled :db-unlock-biometric-enabled]
;; Should be called when a database is opened
(reg-event-fx
 :app-database-preference-update
 (fn [{:keys [db]} [_event-id kw value]]
   (let [db-pref (database-preference-by-db-key db (active-db-key db))
         db-pref (merge db-pref {kw value})] 
     {:db  (update-database-preference-list db  db-pref)
      ;; Call backend update also
      :fx [[:app-settings/bg-update-preference [{:database_preference db-pref} nil]]]})))


#_(reg-event-fx
   :app-preference-update-data
   (fn [{:keys [db]} [_event-id kw value call-on-success]]
   ;; First we update the UI side app prefence. 
     {:db  (update-preference-field-data db kw value) #_(assoc-in db [:app-preference :data kw] value)
    ;; When this event is called in on-change handler of a 'list-item-modal-selector', 
    ;; calling any modal window (:common/message-modal-show) in this event did not work
    ;; Need to complete on-change call and then call any messaging then
      :fx [[:app-settings/bg-update-preference [{kw value} call-on-success]]]}))

;; Gets the DatabasePreference for the current opened database
;; Should be called when a database is opened
(reg-sub
 :app-database-preference
 (fn [db [_event-id]]
   (let [db-key (active-db-key db)]
     (if-not (nil? db-key)
       (database-preference-by-db-key db db-key)
       nil))))


(comment
  (in-ns 'onekeepass.mobile.events.app-database-settings)
  (def db-key (-> @re-frame.db/app-db :current-db-file-name)))