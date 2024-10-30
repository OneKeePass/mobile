(ns onekeepass.mobile.events.native-events
  "Handlers for the native side events"
  (:require
   [re-frame.core :refer [dispatch]]
   [onekeepass.mobile.events.common :refer [on-ok]]
   [onekeepass.mobile.background :as bg]))

;; These exact event names are defined in Swift and Kotlin side also 
(def EVENT_ON_APPLICATION_URL "onApplicationOpenURL")

(def EVENT_ON_TIME_TICK "onTimerTick")

(def EVENT_ENTRY_OTP_UPDATE "onEntryOtpUpdate")

(defn open-url
  "Makes a corresponding UI side event for the received 'onApplicationOpenURL' event from backend"
  [event]
  ;;(println "open-url is called using the native events")
  (dispatch [:open-database/database-file-picked (:ok (bg/transform-api-response event {}))]))

(defn register-open-url-handler
  ;; The event 'onApplicationOpenURL' will will be called when user 
  ;; presses a 'xxxx.kdbx' file and the app is in the background
  []
  (bg/register-event-listener EVENT_ON_APPLICATION_URL open-url))

;; Mostly useful during development
#_(defn remove-open-url-handler []
    (bg/unregister-event-listener "onApplicationOpenURL" open-url))

(def ^private token-response-converter (partial bg/transform-response-excluding-keys #(-> % (get "reply_field_tokens") keys vec)))

(defn register-entry-otp-update-handler []
  (bg/register-event-listener EVENT_ENTRY_OTP_UPDATE
                              (fn [event-message]
                                (let [converted (bg/transform-api-response event-message {:convert-response-fn token-response-converter})] 
                                  (when-let [{:keys [entry-uuid reply-field-tokens]} (on-ok converted)] 
                                    (dispatch [:entry-form/update-otp-tokens entry-uuid reply-field-tokens]))))))

  (defn register-timer-tick-handler []
    (bg/register-event-listener EVENT_ON_TIME_TICK (fn [event-message]
                                                     (println "EVENT_ON_TIME_TICK event-message is " (bg/transform-api-response event-message {})))))

  (defn register-backend-event-handlers []
    (register-open-url-handler)
    (register-entry-otp-update-handler)
    (register-timer-tick-handler))



