(ns onekeepass.mobile.events.app-lock
  "App lock specific events"
  (:require
   [onekeepass.mobile.constants :refer [APP_LOCK_PAGE_ID]]
   [onekeepass.mobile.events.common :as cmn-events :refer [app-lock-preference on-ok on-error]]
   [re-frame.core :refer [dispatch subscribe reg-event-fx reg-sub reg-fx]]
   [onekeepass.mobile.translation :refer [lstr-mt]]
   [onekeepass.mobile.background :as bg]))

(defn lock-app []
  (dispatch [:app-lock-app-locked]))

(defn unlock-app []
  (dispatch [:app-lock-app-unlocked]))

(defn verify-pin-entered [pin]
  (bg/pin-verify pin
                 (fn [api-reponse]
                   ;; Need to use when-some instead of when-let
                   ;; when-some evaluates when 'on-ok' return a non nil value

                   ;; However when-let evaluates when 'on-ok' return value is true 
                   ;; ( i.e (boolean (on-ok ...)) = true and not false)

                   ;; (:ok api-reponse) is either true or false 
                   (when-some [verify-result (on-ok api-reponse)]
                     (dispatch [:app-lock-verified verify-result])))))

(defn app-lock-state []
  (subscribe [:app-lock-state]))

;;;;;;;;;; Some generic dialog direct events ;;;;;;;;
(defn locked-app-log-in-dialog-close-disp-fx-vec []
  [:generic-dialog-close :locked-app-log-in-dialog])

(defn locked-app-log-in-dialog-update-with-map-disp-fx-vec [state-m]
  [:generic-dialog-update-with-map :locked-app-log-in-dialog state-m])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(reg-event-fx
 :app-lock-app-locked
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db (assoc-in [:app-lock :state] :locked))}))

(reg-event-fx
 :app-lock-app-unlocked
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db (assoc-in [:app-lock]  
                         {:state :unlocked
                          :last-user-action-time (js/Date.now)}))}))

;; Called from the native event
(reg-event-fx
 :app-lock/app-becoming-inactive
 (fn [{:keys [db]} [_event-id]]
   (let [{:keys [pin-lock-enabled lock-timeout]} (app-lock-preference db)]
     (if (and pin-lock-enabled (= lock-timeout 0))
       {:db (-> db (assoc-in [:app-lock :state] :locked))}
       {}))))


;; Called when the app starts and this event called in this sequence: 
;; :load-app-preference ->  :bg-app-preference -> :app-preference-loaded

;; Because the events ':load-app-preference' or ':bg-app-preference' called in some other ocassions 
;; in addition to app startup, we need make sure this event is effective only when app 
;; starts and all other time the app lock is not done by this event
;; This is done through the flag 'lock-app?'
(reg-event-fx
 :app-lock/app-launched
 (fn [{:keys [db]} [_event-id]] 
   (let [{:keys [pin-lock-enabled]} (app-lock-preference db)
         app-lock (get-in db [:app-lock])
         lock-app? (and (nil? app-lock) pin-lock-enabled)
         ]
     ;;(println ":app-lock/app-launched is called and pin-lock-enabled , lock-app? are  " pin-lock-enabled lock-app?)
     (if lock-app?
       {:db (-> db (assoc-in [:app-lock] {:state :locked :app-launched true})) }
       {}))))

(reg-event-fx
 :app-lock/update-app-active-time
 (fn [{:keys [db]} [_event-id time-now]]
   ;;(println "time-now is " time-now)
   {:db (-> db (assoc-in [:app-lock :last-user-action-time] time-now))}))

(reg-event-fx
 :app-lock/lock-on-timeout
 (fn [{:keys [db]} [_event-id tick]]
   (let [{:keys [pin-lock-enabled lock-timeout]} (app-lock-preference db)
         last-user-action-time (get-in db [:app-lock :last-user-action-time])]
     ;;(println "tick last-user-action-time lock-timeout " tick last-user-action-time lock-timeout  (> (- tick last-user-action-time) lock-timeout))
     (if  (and pin-lock-enabled (not= lock-timeout 0))
       (if (and (not (nil? last-user-action-time)) (> (- tick last-user-action-time) lock-timeout))
         {:db (-> db (assoc-in [:app-lock :state] :locked))}
         {})
       {}))))

(reg-event-fx
 :app-lock-verified
 (fn [{:keys [db]} [_event-id verify-result]]
   (if verify-result
     {:db (-> db (assoc-in [:app-lock :state] :unlocked))
      :fx [[:dispatch (locked-app-log-in-dialog-close-disp-fx-vec)]]}
     {:fx [[:dispatch (locked-app-log-in-dialog-update-with-map-disp-fx-vec
                       {:error-text (lstr-mt 'enterPin 'validPinRequired)})]]})))

(reg-sub
 :app-lock-state
 (fn [db _query-vec]
   (let [s (get-in db [:app-lock :state])]
     (if-not (nil? s) s
             :unlocked))))
;;;;; 

(defn to-app-lock-page
  "Navigates to the app lock page"
  []
  (dispatch [:to-app-lock-page]))

;; Navigates to the app locked page
(reg-event-fx
 :to-app-lock-page
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:dispatch [:common/next-page APP_LOCK_PAGE_ID "home"]]]}))


(comment
  (in-ns 'onekeepass.mobile.events.app-lock)
  (-> @re-frame.db/app-db :app-preference :data :app-lock-preference)
  (def db-key (-> @re-frame.db/app-db :current-db-file-name)))