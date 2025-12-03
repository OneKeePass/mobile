(ns onekeepass.ios.autofill.events.app-lock
  "App lock specific events"
  (:require
   [re-frame.core :refer [dispatch subscribe reg-event-fx reg-sub]]
   [onekeepass.ios.autofill.events.common :refer [on-ok app-lock-preference]]
   [onekeepass.ios.autofill.translation :refer [lstr-mt]]
   [onekeepass.ios.autofill.background :as bg]))

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

(defn app-lock-update-data [field-name-kw value]
  (dispatch [:app-lock-update-data field-name-kw value]))

(defn app-lock-data []
  (subscribe [:app-lock-data]))

(reg-event-fx
 :app-lock-update-data
 (fn [{:keys [db]} [_event-id field-name-kw value]]
   {:db (-> db (assoc-in [:app-lock field-name-kw] value))}))

(reg-event-fx
 :app-lock/app-launched
 (fn [{:keys [db]} [_event-id]]
   (let [{:keys [pin-lock-enabled attempts-allowed]} (app-lock-preference db)
         attempts-count-remaining (if (= attempts-allowed 1) 1 3)
         app-lock (get-in db [:app-lock])
         ;; NOTE: app-lock is nil when the app starts first time
         lock-app? (and (nil? app-lock) pin-lock-enabled)]
     ;; (println ":app-lock/app-launched is called and pin-lock-enabled , attempts-allowed, lock-app? are  " pin-lock-enabled attempts-allowed lock-app?)
     (if lock-app?
       {:db (-> db (assoc-in [:app-lock] {:state :locked
                                          :pin-entered nil
                                          :error-text nil
                                          :password-visible false
                                          :attempts-count-remaining attempts-count-remaining}))}
       {:db (-> db (assoc-in [:app-lock :state] :unlocked))}))))


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
        ;; call backend to update verified time for later use
        })

     ;; PIN verification failed (else part)
     (let [{:keys [attempts-count-remaining]} (get-in db [:app-lock])
           ;;_ (println ":app-lock attempts-count-remaining " attempts-count-remaining)
           attempts-count-remaining (dec attempts-count-remaining)
           attempts-exceeded (if (= attempts-count-remaining 0) true false)]

       ;;(println "In :app-lock-verified attempts-count-remaining attempts-exceeded " attempts-count-remaining attempts-exceeded)

       (if attempts-exceeded
         ;; Call cancel autofill
         (do
           (bg/cancel-extension #(println %))
           {:fx []})

         {:db (-> db (assoc-in [:app-lock :attempts-count-remaining] attempts-count-remaining)
                  (assoc-in [:app-lock :error-text] (lstr-mt 'enterPin 'validPinRequired)))
          :fx []})))))

(reg-sub
 :app-lock-data
 (fn [db _query-vec]
   (get-in db [:app-lock])))



