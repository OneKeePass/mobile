(ns onekeepass.mobile.events.app-settings
  (:require
   [cljs.core.async :refer [go go-loop timeout <!]]
   [re-frame.core :refer [reg-event-db reg-event-fx reg-fx reg-sub dispatch subscribe]]
   [onekeepass.mobile.events.common :refer [set-clipboard-session-timeout
                                            active-db-key]] 
   [onekeepass.mobile.utils  :refer [str->int]]
   [onekeepass.mobile.background :as bg]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  Session timeout ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn user-action-detected []
  (dispatch [:user-action-detected]))

(def db-session-timeout "Timeout in milliseconds" (atom 15000))

#_(defn set-db-session-timeout
  "Called to set a new timeout to use"
  [time-in-seconds]
  (let [in-timeout (str->int time-in-seconds)
        in-timeout (if (nil? in-timeout) 10 in-timeout)
        in-timeout (* in-timeout 1000)]
    (reset! db-session-timeout in-timeout)))

(defn set-db-session-timeout
  "Called to set a new timeout to use"
  [time-in-milli-seconds]
  (let [in-timeout (str->int time-in-milli-seconds)
        in-timeout (if (nil? in-timeout) 10000 in-timeout)]
    (reset! db-session-timeout in-timeout)))

;; This is used during dev time to stop the tick loop
;; Call (reset! continue-tick false) in repl
(def continue-tick (atom true))

;;The static Date.now() method returns the number of milliseconds elapsed since January 1, 1970 00:00:00 UTC

(defn init-session-timeout-tick
  "Needs to be called once to start the periodic ticking which is required for session timeout
  Called in the main init function
  "
  []
  (go-loop []
    ;; Every 5 sec, we send the tick
    (<! (timeout 5000))
    (dispatch [:check-db-list-to-lock (js/Date.now)])
    (when @continue-tick
      (recur))))

;; Called whenever user clicks on any of the content page of the current opened db to reset its last action time
;; opened-db-list has a vec of maps (one map for each opened database)
(reg-event-fx
 :user-action-detected
 (fn [{:keys [db]} []]
   (let [db-key (active-db-key db)
         ;; Updates the user active time for the current active database
         dbs (mapv (fn [m]
                     (if (= db-key (:db-key m))
                       (assoc m :user-action-time (js/Date.now))
                       m))
                   (:opened-db-list db))]
     {:db (-> db (assoc-in [:opened-db-list] dbs))})))

;; This is used when user unlock the previously openned locked database
;; See 'db-opened' fn in 'onekeepass.mobile.events.common' where  'user-action-time'
;; is set when the db is opened first time
(reg-event-fx
 :app-settings/update-user-active-time
 (fn [{:keys [db]} [_event-id db-key]]
   (let [;; Updates the user active time for the database identified by db-key
         dbs (mapv (fn [m]
                     (if (= db-key (:db-key m))
                       (assoc m :user-action-time (js/Date.now))
                       m))
                   (:opened-db-list db))]
     {:db (-> db (assoc-in [:opened-db-list] dbs))})))

;; Locks all openned dbs whose last actions exceeds the timeout duration
;; Called periodically in the go loop to check 
(reg-event-fx
 :check-db-list-to-lock
 (fn [{:keys [db]} [_event-id tick]]
   ;; (:opened-db-list db) returns a vec of maps with keys [db-key database-name file-name user-action-time]
   (let [_db (reduce (fn [db {:keys [db-key user-action-time database-name]}]
                       (let [locked? (-> db (get db-key) :locked)]
                        ;; If the user is not active for more than db-session-timeout time, the screen is locked
                        ;; Lock all dbs that are timed out that are not yet locked 
                        ;; 2 min = 120000 milli seconds , 5 min = 300000 
                         (if  (and (not locked?)
                                   (> (- tick user-action-time) @db-session-timeout))
                           (do
                             (println "Locking database.. " database-name)
                             (dispatch [:lock-on-session-timeout db-key])
                             db)
                           db)))
                     db (:opened-db-list db))]
     {})))



;;;;;;;;;;;;;;; 

(defn to-app-settings-page
  "Called to navigate to the app settings page"
  []
  (dispatch [:to-app-settings]))

(defn update-db-session-timeout [value-in-milli-seconds]
  ;; session-timeout should be in milli seconds
  (dispatch [:db-session-timeout-update value-in-milli-seconds]))

(defn update-clipboard-timeout [value-in-milli-seconds]
  (dispatch [:clipboard-timeout-update value-in-milli-seconds]))

(defn db-session-timeout-value 
  "An atom that gives the db session timeout in milli seconds"
  []
  (subscribe [:db-session-timeout]))

(defn clipboard-timeout-value []
  (subscribe [:clipboard-timeout]))

(reg-event-fx
 :to-app-settings
 (fn [{:keys [db]} [_event-id]]
   {:fx [[:dispatch [:common/next-page :app-settings "page.titles.appSettings"]]]}))

#_(reg-event-db
 :db-session-timeout-update
 (fn [db [_event-id value]]
   (assoc-in db [:app-preference :data :db-session-timeout ] value)))

#_(reg-event-db
   :clipboard-timeout-update
   (fn [db [_event-id value]]
     (assoc-in db [:app-preference :data :clipboard-timeout] value)))

(reg-event-fx
 :db-session-timeout-update
 (fn [{:keys [db]} [_event-id value]]
   (set-db-session-timeout value)
   {:db (assoc-in db [:app-preference :data :db-session-timeout] value)}))

(reg-event-fx
 :clipboard-timeout-update
 (fn [{:keys [db]} [_event-id value]]
   (set-clipboard-session-timeout value)
   {:db (assoc-in db [:app-preference :data :clipboard-timeout] value)}))

(reg-sub
 :db-session-timeout
 (fn [db [_event-id]]
   (let [r (get-in db [:app-preference :data :db-session-timeout])]
     (if (nil? r) 15000 r))))

(reg-sub
 :clipboard-timeout
 (fn [db [_event-id]]
   (let [r (get-in db [:app-preference :data :clipboard-timeout])]
     (if (nil? r) 10000 r))))


(comment
  (in-ns 'onekeepass.mobile.events.app-settings)
  
  (def db-key (-> @re-frame.db/app-db :current-db-file-name))
  (-> @re-frame.db/app-db (get db-key) keys)
  )