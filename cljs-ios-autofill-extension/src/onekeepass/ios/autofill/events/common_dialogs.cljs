(ns onekeepass.ios.autofill.events.common-dialogs
  (:require [onekeepass.ios.autofill.background]
            [clojure.string :as str]
            [re-frame.core :refer [dispatch dispatch-sync reg-event-db
                                   reg-event-fx reg-fx reg-sub subscribe]]))


;;; IMPORTANT: 
;;; ns onekeepass.ios.autofill.events.common-dialogs imported in onekeepass.ios.autofill.events.common
;;; Do not use fns from common here. Otherwise circular dependecies will happen

;;;;;;;;;;;;;;; Error dialog ;;;;;;;;;;;;;

;; (defn close-message-dialog []
;;   (dispatch [:message-box-hide]))

;; (defn message-dialog-data []
;;   (subscribe [:message-box]))

(reg-event-db
 :common/message-box-show
 (fn [db [_event-id title message]]
   ;; Incoming 'message' may be a map with key :message or string or an object
   ;; We need to convert message as '(str message)' to ensure it can be used 
   ;; in UI component
   ;; The message may be a symbol meaning that it is to be used as a key to get the translated text
   (let [msg (if (symbol? message) message
                 (get message :message (str message)))]
     (-> db
         (assoc-in [:message-box :dialog-show] true)
         (assoc-in [:message-box :title] title)
         (assoc-in [:message-box :category] :message)
         (assoc-in [:message-box :message] msg)))))

(reg-event-db
 :common/error-box-show
 (fn [db [_event-id title message]]
   ;; Incoming 'message' may be a map with key :message or string or an object
   ;; We need to convert message as '(str message)' to ensure it can be used 
   ;; in UI component
   ;; The message may be a symbol meaning that it is to be used as a key to get the translated text
   (let [msg (if (symbol? message) message
                 (get message :message (str message)))]
     (-> db
         (assoc-in [:message-box :dialog-show] true)
         (assoc-in [:message-box :title] (if (str/blank? title) "Error" title))
         (assoc-in [:message-box :category] :error)
         (assoc-in [:message-box :message] msg)))))

(reg-event-db
 :message-box-hide
 (fn [db [_event-id]]
   (-> db
       (assoc-in [:message-box :dialog-show] false)
       (assoc-in [:message-box :title] nil)
       (assoc-in [:message-box :category] nil)
       (assoc-in [:message-box :message] nil))))

(reg-sub
 :message-box
 (fn [db _query-vec]
   (-> db :message-box)))