(ns onekeepass.mobile.events.entry-form-otp
  (:require 
   [re-frame.core :refer [reg-event-db
                          reg-event-fx
                          reg-sub
                          dispatch
                          reg-fx
                          subscribe]]
   [onekeepass.mobile.events.entry-form-common :refer [entry-form-key]]
   [onekeepass.mobile.constants :refer [ONE_TIME_PASSWORD_TYPE]]
   
   [onekeepass.mobile.events.common :as cmn-events :refer [assoc-in-key-db
                                                           get-in-key-db]]
   [onekeepass.mobile.utils :as u :refer [contains-val?]]))


(reg-sub
 :otp-currrent-token
 (fn [db [_query-id otp-field-name]]
   (get-in-key-db db [entry-form-key :otp-fields otp-field-name])))