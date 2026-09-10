(ns onekeepass.mobile.events.native-events
  "Handlers for the native side events"
  (:require
   [re-frame.core :refer [dispatch]]
   [onekeepass.mobile.events.common :refer [on-ok]]
   [onekeepass.mobile.background :as bg]))

;; These exact event names are defined in Swift and Kotlin side also 

;; Android
;; android/app/src/main/java/com/onekeepassmobile/EventEmitter.kt

;; iOS
;; ios/OneKeePassMobile/RnModules/OkpEvents.swift
;; ios/OneKeePassAutoFill/RnModules/AutoFillEvents.swift

(def EVENT_ON_APPLICATION_URL "onApplicationOpenURL")

(def EVENT_ON_TIME_TICK "onTimerTick")

(def EVENT_ENTRY_OTP_UPDATE "onEntryOtpUpdate")

(def EVENT_APP_BECOMES_ACTIVE  "onAppBecomingActive")

(def EVENT_APP_BECOMES_INACTIVE  "onAppBecomingInActive")

;; Emitted on Android only - see EventEmitter.onNewIntent
(def EVENT_ON_OTP_AUTH_URL "onOtpAuthUrl")

;; These callbacks are called by the native event emitter and not through 'call-api-async',
;; so the try/catch used for the api calls does not cover them. A throw here - parsing of a
;; malformed json body is the one to expect - reaches the react native fatal handler and
;; the app is killed. Every listener is registered through this wrapper for that reason
(defn- guarded-listener [event-name listener-fn]
  (fn [event-message]
    (try
      (listener-fn event-message)
      (catch :default err
        (js/console.error (str "Handling the native event " event-name " failed") err)))))

(defn- register-guarded-event-listener [event-name listener-fn]
  (bg/register-event-listener event-name (guarded-listener event-name listener-fn)))

#_(defn- open-url
  "Makes a corresponding UI side event for the received 'onApplicationOpenURL' event from backend"
  [event-message]
  (println "open-url is called using the native events: arg" event-message)
  (dispatch [:open-database/app-opened-with-db-file (:ok (bg/transform-api-response event-message {}))]))

(defn open-url
    "Makes a corresponding UI side event for the received 'onApplicationOpenURL' event from backend"
    [event-message]
    #_(println "open-url is called using the native events: arg" event-message "transformed one" (bg/transform-api-response event-message {}))
    ;; (dispatch [:open-database/app-opened-with-db-file (:ok (bg/transform-api-response event {}))]))
    (when-let [file-info (on-ok (bg/transform-api-response event-message {}))]
      (dispatch [:open-database/app-opened-with-db-file file-info])))

(defn register-open-url-handler
  ;; The event 'onApplicationOpenURL' will will be called when user
  ;; presses a 'xxxx.kdbx' file and the app is in the background
  []
  (register-guarded-event-listener EVENT_ON_APPLICATION_URL open-url))

;; Mostly useful during development
#_(defn remove-open-url-handler []
    (bg/unregister-event-listener "onApplicationOpenURL" open-url))

(defn register-otp-auth-url-handler
  ;; The event 'onOtpAuthUrl' is called when the user presses an 'otpauth://' link in
  ;; another app - the device Camera app shows one after scanning a 2FA QR code - and
  ;; our app is already running
  []
  (register-guarded-event-listener EVENT_ON_OTP_AUTH_URL
                                   (fn [event-message]
                                     (when-let [{:keys [otp-url]} (on-ok (bg/transform-api-response event-message {}))]
                                       (dispatch [:otp-url-received/url-received otp-url])))))

(def ^private token-response-converter (partial bg/transform-response-excluding-keys #(-> % (get "reply_field_tokens") keys vec)))

(defn register-entry-otp-update-handler []
  (register-guarded-event-listener EVENT_ENTRY_OTP_UPDATE
                                   (fn [event-message]
                                     (let [converted (bg/transform-api-response event-message {:convert-response-fn token-response-converter})]
                                       (when-let [{:keys [entry-uuid reply-field-tokens]} (on-ok converted)]
                                         (dispatch [:entry-form/update-otp-tokens entry-uuid reply-field-tokens]))))))

(defn register-timer-tick-handler []
  (register-guarded-event-listener EVENT_ON_TIME_TICK
                                   (fn [event-message]
                                     (println "EVENT_ON_TIME_TICK event-message is " (bg/transform-api-response event-message {})))))


(defn register-app-becomes-active []
  (register-guarded-event-listener EVENT_APP_BECOMES_ACTIVE
                                   (fn [_event-message]
                                     (dispatch [:external-db-change/poll-open-remote-dbs])
                                     ;; Neither the entry list's refresh timer nor the native
                                     ;; animation of its token bars can be trusted across a
                                     ;; spell in the background
                                     (dispatch [:entry-list-otp/refresh-all]))))

(defn register-app-becomes-inactive []
  (register-guarded-event-listener EVENT_APP_BECOMES_INACTIVE
                                   (fn [_event-message]
                                     (dispatch [:app-lock/app-becoming-inactive]))))


(defn register-backend-event-handlers []
  (register-app-becomes-active)
  (register-app-becomes-inactive)
  (register-open-url-handler)
  ;; Only Android emits 'onOtpAuthUrl'. The iOS event emitter does not declare it, and
  ;; RCTEventEmitter raises 'not a supported event type' when a listener is added for an
  ;; event a platform does not declare
  (when (bg/is-Android)
    (register-otp-auth-url-handler))
  (register-entry-otp-update-handler)
  (register-timer-tick-handler))



