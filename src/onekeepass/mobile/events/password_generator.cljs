(ns onekeepass.mobile.events.password-generator
  (:require
   [re-frame.core :refer [reg-event-db reg-event-fx reg-fx reg-sub dispatch subscribe]]
   [onekeepass.mobile.events.common :as cmn-events :refer [on-ok]]
   [onekeepass.mobile.background :as bg]))

(def write-string-to-clipboard bg/write-string-to-clipboard)

(defn generator-data-update [kw value]
  (dispatch [:generator-data-update kw value]))

(defn password-options-update [kw value]
  (dispatch [:password-options-update kw value]))

(defn generate-password
  "Receives a callback function that accepts one argument and opens up the password generator
   with the generated password for the default options. 
   on-selection-callback can be nil and in that case no function is called with the generated password
   "
  ([on-selection-callback]
   (dispatch [:password-generator/start on-selection-callback]))
  ([]
   (dispatch [:password-generator/start nil])))

(defn regenerate-password []
  (dispatch [:regenerate-password]))

(defn generated-password-selected []
  (dispatch [:generated-password-selected]))

(defn password-generation-options []
  (subscribe [:password-generation-options]))

(defn generator-password-result []
  (subscribe [:generator-password-result]))

(defn generated-analyzed-password []
  (subscribe [:generated-analyzed-password]))

(defn password-length-slider-value []
  (subscribe [:password-length-slider-value]))

(defn on-selection-available []
  (subscribe [:on-selection-available]))

(def generator-init-data {;; reflects the slider component value
                          :slider-value nil
                          ;; A function that is called when 'Select' is pressed
                          :on-selection nil
                          :password-options {;; All fields from struct PasswordGenerationOptions
                                             :length 8
                                             :numbers true
                                             :lowercase-letters true
                                             :uppercase-letters true
                                             :symbols true
                                             :spaces false
                                             :exclude-similar-characters true
                                             :strict true}
                                 ;;;
                          :password-result {:password nil
                                            :analyzed-password nil
                                            :is-common false
                                            :score {:name nil
                                                    :raw-value nil
                                                    :score-text nil}}})

(defn- init-dialog-data [app-db]
  (assoc-in app-db [:generator :data] generator-init-data))

;; Updates all top level fields of data in :data
(defn- to-generator-data [db & {:as kws}]
  (let [data (get-in db [:generator :data])
        data (merge data kws)]
    (assoc-in db [:generator :data] data)))

;; Updates the password-options fields
(defn- to-password-options-data [db & {:as kws}]
  (let [data (get-in db [:generator :data :password-options])
        data (merge data kws)]
    (assoc-in db [:generator :data :password-options] data)))

(reg-event-db
 :generator-data-update
 (fn [db [_event-id field-name-kw value]]
   (-> db
       (to-generator-data field-name-kw value))))

(reg-event-fx
 :password-options-update
 (fn [{:keys [db]} [_event-id field-name-kw value]]
   (let [db (-> db
                (to-password-options-data field-name-kw value)
                (to-generator-data :text-copied false))
         po (get-in db [:generator :data :password-options])]
     {:db db
      :fx [[:bg-analyzed-password po]]})))

(reg-event-fx
 :generated-password-selected
 (fn [{:keys [db]} [_event-id]]
   (let [on-selection-callback-fn (get-in db [:generator :data :on-selection])
         analyzed-password (get-in db [:generator :data :password-result :analyzed-password])]
     (when on-selection-callback-fn
       (on-selection-callback-fn analyzed-password)) ;; side effect
     {:fx [[:dispatch [:common/previous-page]]]})))

(reg-event-fx
 :password-generator/start
 (fn [{:keys [db]} [_event-id on-selection]]
   (let [db (init-dialog-data db)
         po (get-in db [:generator :data :password-options])]
     {:db (-> db (assoc-in [:generator :data :on-selection] on-selection))
      :fx [[:bg-analyzed-password po]]})))

(reg-event-fx
 :regenerate-password
 (fn [{:keys [db]} [_event-id]]
   {:fx [[:bg-analyzed-password (get-in db [:generator :data :password-options])]]}))

(reg-fx
 :bg-analyzed-password
 (fn [password-options]
   (bg/analyzed-password password-options
                         (fn [api-response]
                           (when-let [result (on-ok api-response)]
                             (dispatch [:password-generation-complete result]))))))

(reg-event-fx
 :password-generation-complete
 (fn [{:keys [db]} [_event-id password-result]]
   {:db (-> db
            (assoc-in  [:generator :data :password-result] password-result)
            (assoc-in  [:generator :data :slider-value] (:length password-result)))

    :fx [[:dispatch [:common/next-page :password-generator  "generator"]]]}))

(reg-sub
 :password-generation-options
 (fn [db [_query-id]]
   (get-in db [:generator :data :password-options])))

(reg-sub
 :generator-password-result
 (fn [db [_query-id]]
   (get-in db [:generator :data :password-result])))

(reg-sub
 :generated-analyzed-password
 (fn [db [_query-id]]
   (get-in db [:generator :data :password-result :analyzed-password])))

(reg-sub
 :password-length-slider-value
 (fn [db [_query-id]]
   (get-in db [:generator :data :slider-value])))

(reg-sub
 :on-selection-available
 (fn [db [_query-id]]
   (if (get-in db [:generator :data :on-selection]) true false)))