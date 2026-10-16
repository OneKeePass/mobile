(ns onekeepass.mobile.events.app-lock
  "App lock specific events"
  (:require
   [onekeepass.mobile.events.common :as cmn-events :refer [app-lock-preference  on-ok on-error]]
   [re-frame.core :refer [dispatch subscribe reg-event-fx reg-sub reg-fx]]
   [onekeepass.mobile.background :as bg]))

(defn verify-pin-entered
  "Checks the PIN with the stored one. 'verify-pin-handler-fn' receives true or false, or nil
   when the check itself failed (the error is already shown by 'on-ok')"
  [pin verify-pin-handler-fn]
  (bg/pin-verify pin
                 (fn [api-reponse]
                   ;; (:ok api-reponse) is either true or false
                   (verify-pin-handler-fn (on-ok api-reponse)))))

(defn verify-app-lock-pin
  "Checks the PIN entered on the app lock screen. 'rejected-fn' is called when the PIN is wrong
   or could not be checked. A failed check does not count as a wrong attempt"
  [pin rejected-fn]
  (verify-pin-entered pin
                      (fn [verified?]
                        (when (some? verified?)
                          (dispatch [:app-lock-verified verified?]))
                        (when-not verified?
                          (rejected-fn)))))

(defn app-lock-state []
  (subscribe [:app-lock-state]))

(defn app-locked?
  "True when the app lock screen is showing. Used in other events that should not act on
   the app db while the user has yet to get past the app lock"
  [app-db]
  (= :locked (get-in app-db [:app-lock :state])))

(defn attempts-remaining
  "The number of wrong PINs still allowed before the app is reset"
  []
  (subscribe [:app-lock-attempts-remaining]))

(defn keyboard-visibility-changed [visible?]
  (dispatch [:app-lock/keyboard-visibility-changed visible?]))

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

;; Time allowed in a picker or share sheet that the app itself opened, used in place of a shorter
;; lock delay. Without this, the time spent locating a database file counts against
;; a delay like 15 seconds and the app locks while the user is still opening the database
(def ^:private OWN-ACTIVITY-MIN-LOCK-TIMEOUT 300000)

;; Idle time in the foreground before the app locks when the lock delay is "Immediately"
;; That option is about leaving the app and locking within seconds while the user reads an entry
;; is not wanted. Same as the default lock delay so that "Immediately" is never less strict
;; in the foreground than the default
(def ^:private IMMEDIATELY-FOREGROUND-IDLE-TIMEOUT 60000)

(defn- lock-app [db attempts-allowed]
  (-> db (assoc-in [:app-lock :state] :locked)
      (assoc-in [:app-lock :attempts-count-remaining] attempts-allowed)))

;; Called from the native event when app goes background
;; 'own-activity?' is true when the app went to the background because it opened a picker
;; or share sheet (Android only)
;; The time spent out of the app is checked in ':app-lock/app-becoming-active'
;; The foreground idle time is checked in ':app-lock/lock-on-idle'
(reg-event-fx
 :app-lock/app-becoming-inactive
 (fn [{:keys [db]} [_event-id own-activity?]]
   (let [{:keys [pin-lock-enabled lock-timeout attempts-allowed]} (app-lock-preference db)]
     (cond
       (not pin-lock-enabled)
       {}

       (and (= lock-timeout 0) (not own-activity?))
       {:db (lock-app db attempts-allowed)}

       ;; iOS may send more than one inactive event before the app is active again
       ;; and the earliest one is kept
       (nil? (get-in db [:app-lock :inactive-since]))
       {:db (assoc-in db [:app-lock :inactive-since] {:time (js/Date.now)
                                                       :own-activity own-activity?})}

       :else
       {}))))

;; Called from the native event when app comes to foreground
(reg-event-fx
 :app-lock/app-becoming-active
 (fn [{:keys [db]} [_event-id]]
   (let [{:keys [pin-lock-enabled lock-timeout attempts-allowed]} (app-lock-preference db)
         {:keys [time own-activity]} (get-in db [:app-lock :inactive-since])
         allowed-time (if own-activity (max lock-timeout OWN-ACTIVITY-MIN-LOCK-TIMEOUT) lock-timeout)
         lock? (and pin-lock-enabled
                    (some? time)
                    (> (- (js/Date.now) time) allowed-time))
         db (update db :app-lock dissoc :inactive-since)]
     {:db (if lock?
            (lock-app db attempts-allowed)
            ;; Coming back to the app is a user action. The time away was checked above and
            ;; must not count again against the foreground idle time
            (assoc-in db [:app-lock :last-user-action-time] (js/Date.now)))})))

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
     (cond
       lock-app?
       {:db (-> db (assoc-in [:app-lock] {:state :locked
                                          :attempts-count-remaining attempts-allowed
                                          :app-launched true}))}

       ;; Marks the app start as done. Otherwise, after the PIN lock is turned on in settings,
       ;; the next ':app-preference-loaded' would lock the app as if it were just launched
       (nil? app-lock)
       {:db (assoc db :app-lock {:state :unlocked})}

       :else
       {}))))

(reg-event-fx
 :app-lock/update-app-active-time
 (fn [{:keys [db]} [_event-id time-now]]
   {:db (assoc-in db [:app-lock :last-user-action-time] time-now)}))

;; Typing does not produce touch events. While the keyboard is showing (e.g. entering the
;; database password), the app is taken as being in use
(reg-event-fx
 :app-lock/keyboard-visibility-changed
 (fn [{:keys [db]} [_event-id visible?]]
   {:db (cond-> (assoc-in db [:app-lock :keyboard-visible] visible?)
          (not visible?) (assoc-in [:app-lock :last-user-action-time] (js/Date.now)))}))

;; Called on every tick while the app is in the foreground. The app locks when there was no
;; user action for the lock delay. Time out of the app is not counted here. That is checked
;; in ':app-lock/app-becoming-active'
(reg-event-fx
 :app-lock/lock-on-idle
 (fn [{:keys [db]} [_event-id tick]]
   (let [{:keys [pin-lock-enabled lock-timeout attempts-allowed]} (app-lock-preference db)
         {:keys [last-user-action-time inactive-since keyboard-visible]} (get-in db [:app-lock])
         idle-timeout (if (= lock-timeout 0) IMMEDIATELY-FOREGROUND-IDLE-TIMEOUT lock-timeout)]
     (if (and pin-lock-enabled
              (not (app-locked? db))
              ;; Android may still run the tick while the app is in a picker
              (nil? inactive-since)
              (not keyboard-visible)
              (some? last-user-action-time)
              (> (- tick last-user-action-time) idle-timeout))
       {:db (lock-app db attempts-allowed)}
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
                ;; The idle time starts again from the unlock
                (assoc-in [:app-lock :last-user-action-time] (js/Date.now)))
        ;; Any 'otpauth://' url that arrived while the app lock screen was showing
        ;; waits for this
        :fx [[:dispatch [:otp-url-received/check-pending]]]})

     ;; PIN verification failed (else part)
     (let [{:keys [attempts-count-remaining]} (get-in db [:app-lock])
           ;;_ (println ":app-lock attempts-count-remaining " attempts-count-remaining)
           attempts-count-remaining (dec attempts-count-remaining)
           attempts-exceeded (if (= attempts-count-remaining 0) true false)]

       ;;(println "In :app-lock-verified attempts-count-remaining attempts-exceeded " attempts-count-remaining attempts-exceeded)

       (if attempts-exceeded
         ;; Call app reset
         {:fx [[:bg-app-reset]]}
         ;; The PIN pad shakes and clears its dots for a wrong PIN
         {:db (-> db (assoc-in [:app-lock :attempts-count-remaining] attempts-count-remaining))})))))

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
         [:dispatch [:common/to-home-page]]]}))

(reg-sub
 :app-lock-state
 (fn [db _query-vec]
   (let [s (get-in db [:app-lock :state])]
     (if-not (nil? s) s
             :unlocked))))

(reg-sub
 :app-lock-attempts-remaining
 (fn [db _query-vec]
   (get-in db [:app-lock :attempts-count-remaining])))

(comment
  (in-ns 'onekeepass.mobile.events.app-lock)
  (-> @re-frame.db/app-db :app-preference :data :app-lock-preference)
  (def db-key (-> @re-frame.db/app-db :current-db-file-name)))