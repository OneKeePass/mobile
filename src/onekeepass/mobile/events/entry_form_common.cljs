(ns onekeepass.mobile.events.entry-form-common
  "Common fns used acrosss entry form related events"
  (:require [clojure.string :as str]
            [onekeepass.mobile.constants :refer [ONE_TIME_PASSWORD_TYPE]]
            [onekeepass.mobile.events.common :as cmn-events :refer [assoc-in-key-db
                                                                    get-in-key-db]]
            [onekeepass.mobile.translation :refer [lstr-mt]]
            [onekeepass.mobile.utils :as u :refer [contains-val?]]))



(def ^:private standard-kv-fields ["Title" "Notes"])

(def entry-form-key :entry-form)

(def Favorites "Favorites")

(defn is-field-exist
  "Checks that a given field name exists in the entry form or not "
  [app-db field-name]
  (let [field-name (str/trim field-name)
        all-section-fields (-> (get-in-key-db
                                app-db
                                [entry-form-key :data :section-fields])
                               vals flatten) ;;all-section-fields is a list of maps for all sections 
        ]
    (or (contains-val? standard-kv-fields field-name)
        (-> (filter (fn [m] (= field-name (:key m))) all-section-fields) seq boolean))))


(defn add-section-field
  "Creates a new KV for the added section field and updates the 'section-name' section
  Returns the updated app-db
  "
  [app-db {:keys [section-name
                  field-name
                  protected
                  required
                  data-type]}] 
  (let [section-fields-m (get-in-key-db
                          app-db
                          [entry-form-key :data :section-fields])
        ;;_ (println "section-fields-m " section-fields-m)
        ;; fields is a vec of KVs for a given section
        fields (-> section-fields-m (get section-name []))
        fields (conj fields {:key field-name
                             :value nil
                             :protected protected
                             :required required
                             :data-type data-type
                             :standard-field false})]
    (assoc-in-key-db app-db [entry-form-key :data :section-fields]
                     (assoc section-fields-m section-name fields))))

(defn merge-section-key-value [db section key value]
  (let [section-kvs (get-in-key-db db [entry-form-key :data :section-fields section])
        section-kvs (mapv (fn [m] (if (= (:key m) key) (assoc m :value value) m)) section-kvs)]
    section-kvs))

(defn extract-form-otp-fields
  "Returns a map with a otp field name as key and current-opt-token value as value"
  [form-data]
  ;; :section-fields returns a map with section name as keys
  ;; vals fn return 'values' ( a vec of field info map) for all sections. Once vec for each section. 
  ;; And need to use flatten to combine all section values
  ;; For example if two sections, vals call will return a two member ( 2 vec)
  ;; sequence. Flatten combines both vecs and returns a single sequence of field info maps
  (let [fields (-> form-data :section-fields vals flatten)
        otp-fields (filter (fn [m] (=  ONE_TIME_PASSWORD_TYPE (:data-type m))) fields)
        names-values (into {} (for [{:keys [key current-opt-token]} otp-fields] [key current-opt-token]))]
    names-values))


(defn validate-entry-form-data
  "Verifies that the user has entered valid values in some of the required fields of the entry form
  Returns a map of fileds with errors and error-fields will be {} in case no error is found
  "
  [{:keys [group-uuid title]}]
  (let [error-fields (cond-> {}
                       ;; An entry's group is required
                       (u/uuid-nil-or-default? group-uuid)
                       (assoc :group-selection (lstr-mt 'entryForm 'selectGroup))

                       ;; Required entry form title is missing
                       (str/blank? title)
                       (assoc :title (lstr-mt 'entryForm 'enterTitleName)))]
    error-fields))