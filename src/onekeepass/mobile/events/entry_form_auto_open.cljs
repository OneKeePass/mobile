(ns onekeepass.mobile.events.entry-form-auto-open
  "Auto opening of a child databases related events"
  (:require-macros [onekeepass.mobile.okp-macros
                    :refer  [as-map]])
  (:require
   [clojure.string :as str]
   [onekeepass.mobile.events.common :refer [assoc-in-key-db
                                            get-in-key-db
                                            active-db-key
                                            on-ok]]
   [onekeepass.mobile.events.entry-form-common :refer [get-form-data
                                                       place-holder-resolved-value
                                                       form-data-kv-data
                                                       extract-form-field-names-values]]
   [re-frame.core :refer [reg-event-db
                          reg-event-fx
                          reg-sub
                          dispatch
                          reg-fx
                          subscribe]]
   [onekeepass.mobile.background :as bg]
   [onekeepass.mobile.constants :as const :refer [URL
                                                  USERNAME
                                                  PASSWORD
                                                  UUID_OF_ENTRY_TYPE_AUTO_OPEN
                                                  IFDEVICE]]))

(defn is-kdbx-url [url-field-value]
  (if (nil? url-field-value) false
      (str/starts-with? url-field-value "kdbx:")))

(defn entry-form-open-kdbx-url [url-field-value]
  (dispatch [:entry-form-open-kdbx-url url-field-value]))

;; Called when user presses the url field 
(reg-event-fx
 :entry-form-open-kdbx-url
 (fn [{:keys [_db]} [_event-id url-value]]
   (if (str/starts-with? url-value "kdbx:")
     {:fx [[:dispatch [:entry-form-auto-open-resolve-properties]]]}
     {})))

(defn auto-open-properties-dispatch-fn
  "The arg auto-open-props is used after auto open props resolving"
  [auto-open-props api-response]
  (when-let [auto-props-resolved
             (on-ok api-response
                    #(dispatch [:entry-form-auto-open-properties-resolve-error %]))]
    (dispatch [:entry-form-auto-open-properties-resolved auto-open-props auto-props-resolved])))

(reg-event-fx
 :entry-form-auto-open-resolve-properties
 (fn [{:keys [db]} [_event-id]]
   (let [{:keys [entry-type-uuid] :as form-data} (get-form-data db)]
     ;; Should entry-type-uuid check is required ?
     (if (= entry-type-uuid const/UUID_OF_ENTRY_TYPE_AUTO_OPEN)
       (let [field-m (extract-form-field-names-values form-data)
             parsed-fields (:parsed-fields form-data)
             url (place-holder-resolved-value parsed-fields URL (get field-m URL))
             key-file-path (place-holder-resolved-value parsed-fields USERNAME (get field-m USERNAME))
             auto-open-props {:url-field-value url
                              :key-file-path key-file-path
                              :device_if_val (get field-m const/IFDEVICE)}]
         {:fx [[:bg-resolve-auto-open-properties [auto-open-props (partial auto-open-properties-dispatch-fn auto-open-props)]]]})
       {}))))

;; dispatch-fn is a fn that accepts the api-response
(reg-fx
 :bg-resolve-auto-open-properties
 (fn [[auto-props dispatch-fn]]
   (bg/resolve-auto-open-properties auto-props dispatch-fn)))

;; auto-open-props is a map from struct AutoOpenProperties
;; auto-open-props-resolved is map from struct 'AutoOpenPropertiesResolved'
(reg-event-fx
 :entry-form-auto-open-properties-resolved
 (fn [{:keys [db]} [_event-id
                    {:keys [key-file-path] :as _auto-open-props}
                    {:keys [db-key key-file-full-path key-file-name] :as auto-open-props-resolved}]]
   (cond
     (str/blank? db-key)
     {:fx [[:dispatch [:entry-form-auto-open-db-key-not-found]]]}

     (and (not (str/blank? key-file-path)) (str/blank? key-file-full-path))
     {:fx [[:dispatch [:entry-form-auto-open-pick-key-file]]]}

      ;; Open the child database
     :else
     {:fx [[:dispatch [:entry-form-auto-open-load-child-database auto-open-props-resolved]]]})))

(reg-event-fx
 :entry-form-auto-open-db-key-not-found
 (fn [{:keys [db]} [_event_id]]
   (println "entry-form-auto-open-db-key-not-found is called")
   {}))

(reg-event-fx
 :entry-form-auto-open-pick-key-file
 (fn [{:keys [db]} [_event_id]]
   (println "entry-form-auto-open-pick-key-file is called")
   {}))

;; :open-database/database-file-picked-in-auto-open
;; db-key db-file-name password key-file-full-path key-file-name
(reg-event-fx
 :entry-form-auto-open-load-child-database
 (fn [{:keys [db]} [_event_id {:keys [db-key db-file-name key-file-full-path key-file-name] :as auto-open-props-resolved}]] 
   (let [form-data (get-form-data db)
         parsed-fields (:parsed-fields form-data)
         {:keys [value]} (form-data-kv-data form-data PASSWORD)
         password (place-holder-resolved-value parsed-fields PASSWORD value)
         args (as-map [db-key db-file-name password key-file-full-path key-file-name])]
     ;; (println "args is " args)
     {:fx [[:dispatch [:common/to-home-page]]
           [:dispatch [:open-database/database-file-picked-in-auto-open args]]]})))

(reg-event-fx
 :entry-form-auto-open-properties-resolve-error
 (fn [{:keys [_db]} [_event-id error]]
   {:fx [#_[:dispatch [:common/error-info-box-show {:title  "Auto open error" :error-text error}]]]}))


(comment
  (in-ns 'onekeepass.mobile.events.entry-form-auto-open))

;; (println "In resolved auto-open-props " auto-open-props)
;; (println "In resolved auto-open-props-resolved " auto-open-props-resolved)
;; (println "first second third "  (str/blank? db-key) (and (not (str/blank? key-file-path)) (str/blank? key-file-full-path)) (not (nil? db-key)))

#_(cond
    (str/blank? db-key)
    {:fx [[:dispatch [:entry-form-auto-open-db-key-not-found]]]}

    (and (not (str/blank? key-file-path)) (str/blank? key-file-full-path))
    {:fx [[:dispatch [:entry-form-auto-open-pick-key-file]]]}

                  ;; Open the child database
    (not (nil? db-key))
    (do
      (println " in else clause")
      {:fx [[:dispatch [:entry-form-auto-open-load-child-database]]]}))

#_(let [form-data (get-form-data db)
        parsed-fields (:parsed-fields form-data)
        {:keys [value]} (form-data-kv-data form-data PASSWORD)
        password (place-holder-resolved-value parsed-fields PASSWORD value)]
    {:fx [#_[:dispatch [:open-db/auto-open-with-credentials url-field-value password key-file-path]]]})

#_(if (not can-open)
    {:fx [[:dispatch [:common/error-info-box-show {:title "Database auto open" :error-text "The device is excluded in opening this database url"}]]]}
    (let [form-data (get-form-data db)
          parsed-fields (:parsed-fields form-data)
          {:keys [value]} (form-data-kv-data form-data PASSWORD)
          password (place-holder-resolved-value parsed-fields PASSWORD value)]
      {:fx [[:dispatch [:open-db/auto-open-with-credentials url-field-value password key-file-path]]]}))
#_(defn verify-reolved-properties
    "Verifies whether we are able to get the db-key, "
    [{:keys [key-file-path] :as auto-open-props} {:keys [db-key db-file-name key-file-full-path] :as auto-open-props-resolved}]
    (cond
      (nil? db-key)
      :db-key-not-found

      (and (not (nil? db-key)) (nil? db-file-name))
      :db-filename-is-not-available

      (and (not (nil? key-file-path)) (nil? key-file-full-path))
      :pick-key-file))
