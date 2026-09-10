(ns onekeepass.mobile.js-error
  "Handling of javascript errors that no other code caught

   A release build has no red box. React native passes an uncaught error to its fatal
   handler and the process is killed, and the message does not survive into the crash
   reports that App Store Connect shows. Three places deal with that:

   - onekeepass.mobile.rn-components/cust-error-boundary keeps a throw while rendering
     from reaching the fatal handler at all and puts the message on the screen
   - the handler installed here logs anything that gets past the boundary
   - OkpCrashLog on the iOS side writes the message and the javascript stack to a file
     when the fatal handler does run")

;; The error is passed on to the handler that react native itself installed, so that a
;; truly uncaught error still ends the process the way it does today and is recorded by
;; the native fatal handler. Dropping the error here instead would keep the app running
;; with state that nothing has checked. Change this only with a way to test what the app
;; does after each kind of error it then survives.
(defonce ^:private previous-handler (atom nil))

(defn install-global-error-handler
  "Called once during startup. Repeat calls are ignored so that a reload during
   development does not chain the handler onto itself"
  []
  (when (and (exists? js/ErrorUtils) (nil? @previous-handler))
    (reset! previous-handler (.getGlobalHandler js/ErrorUtils))
    (.setGlobalHandler
     js/ErrorUtils
     (fn [error is-fatal]
       (js/console.error "Uncaught javascript error. Fatal:" is-fatal error)
       (when-let [handler @previous-handler]
         (handler error is-fatal))))))

(defn report-render-error
  "Called by the error boundary with what it caught"
  [message stack component-stack]
  (js/console.error "Render failed:" message stack component-stack))
