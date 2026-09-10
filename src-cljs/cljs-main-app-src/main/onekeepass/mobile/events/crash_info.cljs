(ns onekeepass.mobile.events.crash-info
  "Shows what the native fatal handler recorded when the app was last ended by an
   unhandled javascript error

   The record is written as the process dies - see OkpCrashLog on the iOS side - so there
   is no chance to show anything at that moment. It is shown on the next launch instead,
   when the app is running normally, and the user can copy it and send it on"
  (:require
   [clojure.string :as str]
   [onekeepass.mobile.background :as bg]
   [onekeepass.mobile.events.common :refer [on-ok]]
   [re-frame.core :refer [dispatch reg-event-db reg-event-fx reg-fx reg-sub
                          subscribe]]))

(defn load-recorded-crashes
  "Called during startup. Only the iOS side records these"
  []
  (when (bg/is-iOS)
    (dispatch [:crash-info/load])))

(defn crash-info-copy []
  (dispatch [:crash-info-copy]))

(defn crash-info-close []
  (dispatch [:crash-info-close]))

(defn crash-info-data []
  (subscribe [:crash-info-data]))

(reg-event-fx
 :crash-info/load
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:bg-last-crash-records]]}))

(reg-fx
 :bg-last-crash-records
 (fn [_call-args]
   (bg/last-crash-records
    (fn [api-response]
      ;; Nothing is shown if these cannot be read. The user did not ask for this and a
      ;; failure here is not worth an error message
      (when-let [text (on-ok api-response
                             (fn [error]
                               (js/console.warn "Reading the recorded crashes failed" error)))]
        (when-not (str/blank? text)
          (dispatch [:crash-info-loaded text])))))))

(reg-event-db
 :crash-info-loaded
 (fn [db [_event-id text]]
   (-> db (assoc-in [:crash-info :text] text)
       (assoc-in [:crash-info :dialog-show] true))))

(reg-event-fx
 :crash-info-copy
 (fn [{:keys [db]} [_event-id]]
   (bg/write-string-to-clipboard (get-in db [:crash-info :text]))
   {:fx [[:dispatch [:common/message-snackbar-open "Copied"]]]}))

;; The records are removed once seen so that the same ones are not shown on every launch
(reg-event-fx
 :crash-info-close
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db (assoc-in [:crash-info :dialog-show] false)
            (assoc-in [:crash-info :text] nil))
    :fx [[:bg-clear-last-crash-records]]}))

(reg-fx
 :bg-clear-last-crash-records
 (fn [_call-args]
   (bg/clear-last-crash-records
    (fn [api-response]
      (on-ok api-response
             (fn [error]
               (js/console.warn "Clearing the recorded crashes failed" error)))))))

(reg-sub
 :crash-info-data
 (fn [db _query-vec]
   ;; Preferences load asynchronously. With PIN lock enabled, wait for an explicit
   ;; unlock so the interval before :app-lock/app-launched cannot expose the report.
   (let [info (get-in db [:crash-info])
         preferences-loaded? (= :loaded (get-in db [:app-preference :status]))
         pin-enabled? (get-in db [:app-preference :data :app-lock-preference :pin-lock-enabled])
         lock-state (get-in db [:app-lock :state])
         accessible? (and preferences-loaded?
                          (not= :locked lock-state)
                          (or (not pin-enabled?) (= :unlocked lock-state)))]
     (when (and accessible? (:dialog-show info))
       info))))
