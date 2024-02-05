(ns onekeepass.mobile.events.native-events
  "Handlers for the native side events"
  (:require
   [re-frame.core :refer [dispatch]]
   [onekeepass.mobile.background :as bg]))


(defn open-url 
  "Makes a corresponding UI side event for the received 'onApplicationOpenURL' event from backend"
  [event] 
  ;;(println "open-url is called using the native events")
  (dispatch [:open-database/database-file-picked (:ok (bg/transform-api-response event {}))]))

(defn register-open-url-handler 
  ;; The event 'onApplicationOpenURL' will will be called when user 
  ;; presses a 'xxxx.kdbx' file and the app is in the background
  [] 
  (bg/register-event-listener "onApplicationOpenURL" open-url))

;; Mostly useful during development
#_(defn remove-open-url-handler []
  (bg/unregister-event-listener "onApplicationOpenURL" open-url))

