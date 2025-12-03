(ns onekeepass.ios.autofill.events.native-events
  (:require [onekeepass.ios.autofill.background :as bg]
            [onekeepass.ios.autofill.events.common :refer [on-ok]]
            [re-frame.core :refer [dispatch]])) 


(def EVENT_ENTRY_OTP_UPDATE "onEntryOtpUpdate")


(def ^private token-response-converter (partial bg/transform-response-excluding-keys #(-> % (get "reply_field_tokens") keys vec)))


(defn register-entry-otp-update-handler []
  (bg/register-event-listener EVENT_ENTRY_OTP_UPDATE
                              (fn [event-message]
                                (let [converted (bg/transform-api-response event-message {:convert-response-fn token-response-converter})]
                                  (when-let [{:keys [entry-uuid reply-field-tokens]} (on-ok converted)]
                                    (dispatch [:entry-form/update-otp-tokens entry-uuid reply-field-tokens]))))))  