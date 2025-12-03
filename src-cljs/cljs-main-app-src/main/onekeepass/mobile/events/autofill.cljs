(ns onekeepass.mobile.events.autofill
  (:require [onekeepass.mobile.background :as bg]
            [onekeepass.mobile.constants :refer [AUTOFILL_SETTINGS_PAGE_ID]]
            [onekeepass.mobile.events.common :refer [active-db-key
                                                     assoc-in-key-db
                                                     get-in-key-db on-error
                                                     on-ok]]
            [re-frame.core :refer [dispatch reg-event-fx reg-fx reg-sub subscribe]]))



(defn to-autofill-settings-page
  "Called to navigate to the autofill settings page"
  []
  (dispatch [:to-autofill-settings-start]))

(defn ios-copy-file-to-group 
  "Called when user enables autofill for the current database"
  []
  (dispatch [:ios-copy-file-to-group]))

(defn ios-delete-copied-autofill-details 
  "Called when user removes the current database from using in autofill"
  []
  (dispatch [:ios-delete-copied-autofill-details]))

(defn ios-autofill-db-info 
  "Gets the autofill use info or nil"
  []
  (subscribe [:ios-autofill-db-info]))

(reg-event-fx
 :ios-copy-file-to-group
 (fn [{:keys [db]} [_event-id]]
   {:fx [[:bg-ios-copy-file-to-group [(active-db-key db)]]]}))

(reg-fx
 :bg-ios-copy-file-to-group
 (fn [[db-key]]
   (bg/ios-copy-files-to-group db-key 
                               (fn [api-response]
                                 (when-let [info (on-ok api-response)]
                                   (dispatch [:ios-autofill-db-info-updated info]))))))

;; Navigates to the autofill settings page
(reg-event-fx
 :to-autofill-settings-page
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:dispatch [:common/next-page AUTOFILL_SETTINGS_PAGE_ID "autofillSettings"]]]}))


;; Called to initiate backend api call 
;; and then navigates to the autofill settings page
(reg-event-fx
 :to-autofill-settings-start
 (fn [{:keys [db]} [_event-id]]
   {:fx [[:bg-ios-query-autofill-db-info [(active-db-key db)]]]}))


(reg-fx
 :bg-ios-query-autofill-db-info
 (fn [[db-key]]
   (bg/ios-query-autofill-db-info db-key
                                  (fn [api-response]
                                    (when-not (on-error api-response)
                                      ;; Need to get the info found in :ok 
                                      (dispatch [:ios-autofill-db-info-loaded (:ok api-response)]))))))

;; Called when user navigates to the autofill settings page
(reg-event-fx
 :ios-autofill-db-info-loaded
 (fn [{:keys [db]} [_event-id info]]
   ;; info is a map corresponding to the struct 'CopiedDbFileInfo'
   {:db (assoc-in-key-db db [:ios-autofill-db-info] info)
    :fx [[:dispatch [:to-autofill-settings-page]]]}))

;; This event is called when the user is in the autofill settings page itself
(reg-event-fx
 :ios-autofill-db-info-updated
 (fn [{:keys [db]} [_event-id info]]
   {:db (assoc-in-key-db db [:ios-autofill-db-info] info)
    :fx [(if (nil? info)
           [:dispatch [:common/message-snackbar-open "Database Autofill is diabled"]]
           [:dispatch [:common/message-snackbar-open "Database Autofill ready"]])]}))


(reg-event-fx
 :ios-delete-copied-autofill-details
 (fn [{:keys [db]} [_event-id]]
   {:fx [[:bg-ios-delete-copied-autofill-details [(active-db-key db)]]]}))

(reg-fx
 :bg-ios-delete-copied-autofill-details
 (fn [[db-key]]
   (bg/ios-delete-copied-autofill-details db-key
                                          (fn [api-response]
                                            (when-not (on-error api-response)
                                              (dispatch [:ios-autofill-db-info-updated nil]))))))

(reg-sub
 :ios-autofill-db-info
 (fn [db [_event-id]]
   (get-in-key-db db [:ios-autofill-db-info])))