(ns onekeepass.mobile.events.app-lock
  "App lock specific events"
  (:require
   [onekeepass.mobile.events.common :as cmn-events :refer [app-lock-preference  on-ok on-error]]
   [re-frame.core :refer [dispatch subscribe reg-event-fx reg-sub reg-fx]]
   [onekeepass.mobile.translation :refer [lstr-mt]]
   [onekeepass.mobile.background :as bg]))

(defn app-lock-verify-pin-handler
  "Called when pin verification backend returns. This is fn is passed from the pin entry dialog"
  [verify-result]
  (dispatch [:app-lock-verified verify-result]))

(defn verify-pin-entered [pin verify-pin-handler-fn]
  (bg/pin-verify pin
                 (fn [api-reponse]
                   ;; Need to use when-some instead of when-let
                   ;; when-some evaluates when 'on-ok' return a non nil value

                   ;; However when-let evaluates when 'on-ok' return value is true 
                   ;; ( i.e (boolean (on-ok ...)) = true and not false)

                   ;; (:ok api-reponse) is either true or false 
                   (when-some [verify-result (on-ok api-reponse)]
                     (verify-pin-handler-fn verify-result)))))

(defn app-lock-state []
  (subscribe [:app-lock-state]))

;;;;;;;;;; Some generic dialog direct events ;;;;;;;;

(defn locked-app-log-in-dialog-close-disp-fx-vec []
  [:generic-dialog-close :locked-app-log-in-dialog])

(defn locked-app-log-in-dialog-update-with-map-disp-fx-vec [state-m]
  [:generic-dialog-update-with-map :locked-app-log-in-dialog state-m])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#_(reg-event-fx
   :app-lock-app-locked
   (fn [{:keys [db]} [_event-id]]
     {:db (-> db (assoc-in [:app-lock :state] :locked))}))

#_(reg-event-fx
   :app-lock-app-unlocked
   (fn [{:keys [db]} [_event-id]]
     {:db (-> db (assoc-in [:app-lock]
                           {:state :unlocked
                            :last-user-action-time (js/Date.now)}))}))

;; Called from the native event when app goes background
(reg-event-fx
 :app-lock/app-becoming-inactive
 (fn [{:keys [db]} [_event-id]]
   (let [{:keys [pin-lock-enabled lock-timeout attempts-allowed]} (app-lock-preference db)]
     ;;(println ":app-lock/app-becoming-inactive is called...")
     (if (and pin-lock-enabled (= lock-timeout 0)) 
       {:db (-> db (assoc-in [:app-lock :state] :locked)
                (assoc-in [:app-lock :attempts-count-remaining] attempts-allowed))}
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
   (let [{:keys [pin-lock-enabled attempts-allowed]} (app-lock-preference db)
         app-lock (get-in db [:app-lock])
         ;; NOTE: app-lock is nil when the app starts first time
         lock-app? (and (nil? app-lock) pin-lock-enabled)]
     ;; (println ":app-lock/app-launched is called and pin-lock-enabled , attempts-allowed, lock-app? are  " pin-lock-enabled attempts-allowed lock-app?)
     (if lock-app?
       {:db (-> db (assoc-in [:app-lock] {:state :locked
                                          :attempts-count-remaining attempts-allowed
                                          :app-launched true}))}
       {}))))

(reg-event-fx
 :app-lock/update-app-active-time
 (fn [{:keys [db]} [_event-id time-now]]
   ;;(println "time-now is " time-now)
   {:db (-> db (assoc-in [:app-lock :last-user-action-time] time-now))}))

(def ^:private ENFORCED-TIMEOUT-IN-MILLISECONDS 60000)


;; In case of '(= lock-timeout 0)' (app lockout is set with the option "Immediately"), we use this
;; 'ENFORCED-TIMEOUT-IN-MILLISECONDS' as default app lock timeout time to be safe.

;; If we do not do that, the app lock will happen only when any db is locked but db-session-timeout may be larger
;; Also the home page needs protection when user after entering PIN and keeps the app in the foreground without doing anything

;; App lock timeout checked and the app will be locked accordingly
(reg-event-fx
 :app-lock/lock-on-timeout
 (fn [{:keys [db]} [_event-id tick]]
   (let [{:keys [pin-lock-enabled lock-timeout attempts-allowed]} (app-lock-preference db)
         last-user-action-time (get-in db [:app-lock :last-user-action-time])
         final-time-out (if (= lock-timeout 0) ENFORCED-TIMEOUT-IN-MILLISECONDS  lock-timeout)]
     ;; (println " tick last-user-action-time lock-timeout > test"  tick last-user-action-time lock-timeout (and (not (nil? last-user-action-time)) (> (- tick last-user-action-time) lock-timeout))) 
     (if pin-lock-enabled
       (if (and (not (nil? last-user-action-time)) (> (- tick last-user-action-time) final-time-out))
         {:db (-> db (assoc-in [:app-lock :state] :locked)
                  (assoc-in [:app-lock :attempts-count-remaining] attempts-allowed))}
         {})
       {}))))

;; This is called from event ':lock-on-session-timeout' when the current db is locked
;; The app is also then locked
;; Should we do only for  '(= lock-timeout 0)' (app lockout is set as "Immediately") ?
(reg-event-fx
 :app-lock/current-db-locked-on-timeout
 (fn [{:keys [db]} [_event-id]]
   (let [{:keys [pin-lock-enabled attempts-allowed]} (app-lock-preference db)]
     ;;(println ":app-lock/current-db-locked-on-timeout is called")
     (if pin-lock-enabled
       {:db (-> db (assoc-in [:app-lock :state] :locked)
                (assoc-in [:app-lock :attempts-count-remaining] attempts-allowed))}
       {}))))

;; Called when after user entered PIN is verified from backend
(reg-event-fx
 :app-lock-verified
 (fn [{:keys [db]} [_event-id verify-result]]

   (if verify-result
     ;; PIN verification is successful
     (let [{:keys [attempts-allowed]} (app-lock-preference db)]
       {:db (-> db (assoc-in [:app-lock :state] :unlocked)
                ;; Reset the attemps check
                (assoc-in [:app-lock :attempts-count-remaining] attempts-allowed)
                ;; The user action time is updated
                (assoc-in [:app-lock :last-user-action-time] (js/Date.now)))
        :fx [[:dispatch (locked-app-log-in-dialog-close-disp-fx-vec)]]})

     ;; PIN verification failed (else part)
     (let [{:keys [attempts-count-remaining]} (get-in db [:app-lock])
           ;;_ (println ":app-lock attempts-count-remaining " attempts-count-remaining)
           attempts-count-remaining (dec attempts-count-remaining)
           attempts-exceeded (if (= attempts-count-remaining 0) true false)]

       ;;(println "In :app-lock-verified attempts-count-remaining attempts-exceeded " attempts-count-remaining attempts-exceeded)

       (if attempts-exceeded
         ;; Call app reset
         {:fx [[:bg-app-reset]]}
         {:db (-> db (assoc-in [:app-lock :attempts-count-remaining] attempts-count-remaining))
          :fx [[:dispatch (locked-app-log-in-dialog-update-with-map-disp-fx-vec
                           {:error-text (lstr-mt 'enterPin 'validPinRequired)})]]})))))

(reg-fx
 :bg-app-reset
 (fn [] 
   (bg/app-reset (fn [api-reponse]
                   (when-not (on-error api-reponse)
                     (dispatch [:app-lock-app-reset-completed]))))))

(reg-event-fx
 :app-lock-app-reset-completed
 (fn [{:keys [db]} [_event-id]]
   ;; Ensure that app-lock is in unlocked state as app is reset
   {:db (-> db (assoc-in [:app-lock :state] :unlocked)) 
    :fx [;; This event loads app peference and then navigates to home page
         [:dispatch [:common/to-home-page]]
         ;; Need to close the dialog 
         [:dispatch (locked-app-log-in-dialog-close-disp-fx-vec)]]}))

(reg-sub
 :app-lock-state
 (fn [db _query-vec]
   (let [s (get-in db [:app-lock :state])]
     (if-not (nil? s) s
             :unlocked))))

(comment
  (in-ns 'onekeepass.mobile.events.app-lock)
  (-> @re-frame.db/app-db :app-preference :data :app-lock-preference)
  (def db-key (-> @re-frame.db/app-db :current-db-file-name)))