(ns 
 onekeepass.mobile.events.entry-form
  "All entry form specific events"
  (:require
   [onekeepass.mobile.events.common :refer [active-db-key
                                            assoc-in-key-db
                                            get-in-key-db
                                            current-page
                                            on-ok on-error]]
   [re-frame.core :refer [reg-event-db
                          reg-event-fx
                          reg-sub
                          dispatch
                          reg-fx
                          subscribe]]

   [clojure.string :as str]
   [onekeepass.mobile.utils :as u :refer [contains-val?]]
   [onekeepass.mobile.background :as bg]))

(def ^:private entry-form-key :entry-form)

(def ^:private Favorites "Favorites")

(defn update-section-value-on-change
  "Updates a section's KeyValue map with the given key and value"
  [section key value]
  (dispatch [:entry-form-update-section-value section key value]))

(defn entry-form-data-update-field-value
  "Update a field found in :data"
  [field-name-kw value]
  (dispatch [:entry-form-data-update-field-value field-name-kw value]))

;; Not used at this time
#_(defn entry-form-update-field-value
  "Update a field found in :entry-form top level"
  [field-name-kw value]
  (dispatch [:entry-form-update-field-value field-name-kw value]))

(defn entry-form-field-visibility-toggle 
  "Called with the field name as key that is toggled between show/hide"
  [key]
  (dispatch [:entry-form-field-visibility-toggle key]))

(defn edit-mode-on-press []
  (dispatch [:entry-form/edit true]))

(defn cancel-entry-form [] 
  (dispatch [:common/previous-page]))

(defn favorite-menu-checked
  "Called when an entry is marked as favorite or not"
  [yes?]
  (dispatch [:entry-form/entry-is-favorite yes?]))

(defn delete-entry [entry-id]
  (dispatch [:move-delete/entry-delete-start entry-id true]))

(defn entry-form-data-fields
  " 
  Called to get value of one more of form top level fields. 
  The arg is a single field name or  fields in a vector of two more field 
  names (Keywords) like [:title :icon-id]
  Returns an atom which resolves to a single value  or a map when derefenced
  e.g {:title 'value'} or {:tags 'value} or {:title 'value' :icon-id 'value}
   "
  [fields]
  (subscribe [:entry-form-data-fields fields]))

(defn entry-form-uuid []
  (subscribe [:entry-form-data-fields :uuid]))

(defn entry-form
  "Returns an atom that has the map entry-form"
  []
  (subscribe [:entry-form]))

(defn entry-form-field
  "Gets the value of any field at the top level in entry-form itself. See other subs to get the field
  values from :data or [:data :section-fields] 
  "
  [file-name-kw]
  (subscribe [:entry-form-field file-name-kw]))

(defn groups-listing []
  (subscribe [:groups/listing]))

(defn visible? [key]
  (subscribe [:entry-form-field-in-visibile-list key]))

(defn form-edit-mode
  "Returns an atom to indiacate editing is true or not"
  []
  (subscribe [:entry-form-edit]))

(defn form-modified
  "Checks whether any data changed or added"
  []
  (subscribe [:entry-form-modified]))

(defn deleted-category-showing []
  (subscribe [:entry-list/deleted-category-showing]))

(defn favorites? []
  (subscribe [:entry-form/favorites-status]))

(defn new-entry-form? []
  (subscribe [:entry-form-new]))

(defn history-entry-form? []
  (subscribe [:entry-form-history]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  
(defn- set-on-entry-load [app-db entry-form-data]
  (-> app-db
      (assoc-in-key-db [entry-form-key :data] entry-form-data)
      (assoc-in-key-db [entry-form-key :undo-data] entry-form-data)
      (assoc-in-key-db [entry-form-key :showing] :selected)
      (assoc-in-key-db [entry-form-key :edit] false)))

;; Deprecate ?
(reg-event-fx
 :entry-delete
 (fn [{:keys [db]} [_event-id]]
   (println "Delete is called for.. " (get-in-key-db db [entry-form-key :data :uuid]))
   {:fx [[:dispatch [:move-delete/entry-delete-start (get-in-key-db db [entry-form-key :data :uuid]) true]]]}))

(defn- on-entry-find [api-response]
  (when-let [entry (on-ok api-response #(dispatch [:entry-form-data-load-error %]))]
    (dispatch [:entry-form-data-load-completed entry true])))

(reg-event-fx
 :entry-form/find-entry-by-id
 (fn [{:keys [db]} [_event-id entry-uuid]]
   {:fx [[:bg-find-entry-by-id [(active-db-key db) entry-uuid on-entry-find]]]}))

(reg-event-fx
 :reload-entry-by-id
 (fn [{:keys [db]} [_event-id entry-uuid]]
   {:fx [[:bg-find-entry-by-id [(active-db-key db)
                                entry-uuid
                                (fn [api-reponse]
                                  (when-let [entry (on-ok api-reponse)]
                                    (dispatch [:entry-form-data-load-completed entry false])))]]]}))

(reg-fx
 :bg-find-entry-by-id
 (fn [[db-key entry-uuid dispatch-fn]]
   (bg/find-entry-by-id db-key entry-uuid dispatch-fn)))

(reg-event-fx
 :entry-form-data-load-error
 (fn [{:keys [_db]} [_event-id error]]
   {:fx [[:dispatch [:common/error-box-show "Find entry" error]]]}))

(reg-event-fx
 :entry-form-data-load-completed
 (fn [{:keys [db]} [_event-id entry-form-data navigate?]]
   ;; When navigate? is false, we just set the loaded entry data and not change the page 
   {:db  (set-on-entry-load db entry-form-data)
    :fx [(when navigate? [:dispatch [:common/next-page :entry-form  "page.titles.entry"]])]}))

;; Update a field found in :data
(reg-event-db
 :entry-form-data-update-field-value
 (fn [db [_event-id field-name-kw value]]
   (assoc-in-key-db db [entry-form-key :data field-name-kw] value)))

;; Not used at this time
;; Update a field found in the top level :entry-form itself
#_(reg-event-db
 :entry-form-update-field-value
 (fn [db [_event-id field-name-kw value]]
   (assoc-in-key-db db [entry-form-key field-name-kw] value)))

(reg-event-db
 :entry-form-update-section-value
 (fn [db [_event-id section key value]]
   (let [section-kvs (get-in-key-db db [entry-form-key :data :section-fields section])
         section-kvs (mapv (fn [m] (if (= (:key m) key) (assoc m :value value) m)) section-kvs)]
     (assoc-in-key-db db [entry-form-key :data :section-fields section] section-kvs))))

(reg-event-db
 :entry-form/edit
 (fn [db [_event-id edit?]]
   (if edit?
     (-> db
         ;;(assoc-in-key-db [entry-form-key :undo-data] (get-in-key-db db [entry-form-key :data]))
         (assoc-in-key-db [entry-form-key :edit] edit?))
     (assoc-in-key-db db [entry-form-key :edit] edit?))))

;; Called to mark or unmark as favorites
;; A tag with name "Favorites" is added or removed accordingly
;; Need to make this language netural to support i18n 
(reg-event-fx
 :entry-form/entry-is-favorite
 (fn fn [{:keys [db]} [_event-id yes?]]
   (let [tags (get-in-key-db db [entry-form-key :data :tags])
         tags (if yes?
                (into [Favorites] tags)
                (filterv #(not= Favorites %) tags))]
     {:db (assoc-in-key-db db [entry-form-key :data :tags] tags)
      :fx [[:dispatch [:entry-save]]]})))

(reg-sub
 :entry-form
 (fn [db _query-vec]
   (get-in-key-db db [entry-form-key])))

(reg-sub
 :entry-form-data
 (fn [db _query-vec]
   (get-in-key-db db [entry-form-key :data])))

;; Gets the only section data
#_(reg-sub
 :entry-form-section-data
 :<- [:entry-form-data]
 (fn [data [_query-id section]]
   (get-in data [:section-fields section])))

;; Gets a :data level field value
(reg-sub
 :entry-form-data-fields
 :<- [:entry-form-data]
 (fn [data [_query-id fields]]
   (if-not (vector? fields)
     ;; fields is a single field name
     (get data fields)
     ;; a vector field names
     (select-keys data fields))))

;; Is this entry Favorites ?
(reg-sub
 :entry-form/favorites-status
 :<- [:entry-form-data]
 (fn [data _query-vec]
   (contains-val? (:tags data) Favorites)))

;; Gets the value of a field at top level 'entry-form' itself
(reg-sub
 :entry-form-field
 :<- [:entry-form]
 (fn [form [_query-id field]]
   ;;(println "form-db called... " form)
   (get form field)))

;; An entry is for new entry when :showing field is :new
(reg-sub
 :entry-form-new
 (fn [db _query-vec]
   (= :new (get-in-key-db db [entry-form-key :showing]))))

;; An entry is for history entry when :showing field is :history-entry
(reg-sub
 :entry-form-history
 (fn [db _query-vec]
   (= :history-entry (get-in-key-db db [entry-form-key :showing]))))

(reg-sub
 :entry-form-edit
 (fn [db _query-vec]
   (get-in-key-db db [entry-form-key :edit])))

(reg-sub
 :entry-form-modified
 (fn [db _query-vec]
   (let [undo-data (get-in-key-db db [entry-form-key :undo-data])
         data (get-in-key-db db [entry-form-key :data])]
     (if (and (seq undo-data) (not= undo-data data))
       true
       false))))

;; Toggles the a field's membership in a list of visibility fields
(reg-event-db
 :entry-form-field-visibility-toggle
 (fn [db [_event-id key]]
   (let [vl (get-in-key-db db [entry-form-key :visibility-list])]
     (if (contains-val? vl key)
       (assoc-in-key-db db [entry-form-key :visibility-list] (filterv #(not= % key) vl))
       (assoc-in-key-db db [entry-form-key :visibility-list] (conj vl key))))))

;; Checks whether a form is field is visible or not
(reg-sub
 :entry-form-field-in-visibile-list
 (fn [db [_query-id key]]
   (contains-val? (get-in-key-db db [entry-form-key :visibility-list]) key)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn validate-entry-form-data
  "Verifies that the user has entered valid values in some of the required fields of the entry form
  Returns a map of fileds with errors and error-fields will be {} in case no error is found
  "
  [{:keys [group-uuid title]}] 
  (let [error-fields (cond-> {}
                       (u/uuid-nil-or-default? group-uuid)
                       (assoc :group-selection "Please select a group ")

                       (str/blank? title)
                       (assoc :title "Please enter a title for this form"))]
    error-fields))

(defn validate-required-fields 
  "Checks that all keys (form fields) that are marked as required are having some valid values 
   in a list of KV maps"
  [error-fields kvsd]
  (loop [{:keys [key value required] :as m} (first kvsd)
         rest-kvsd (next kvsd)
         acc error-fields]
    (if (nil? m) acc
        (let [acc (if (and required (str/blank? value))
                    (assoc acc key "Please enter a valid value for this required field")
                    acc)]
          (recur (first rest-kvsd) (next rest-kvsd) acc)))))

(defn validate-all
  "Validates all required fields including title, parent group etc
  Returns the error-fields map or an empty map if all required values are present
   "
  [form-data]
  (let [error-fields (validate-entry-form-data form-data)
         ;; We get all fields across all sections
         ;; Need to use make a sequence of all KV maps
        kvds (flatten (vals (:section-fields form-data)))
        error-fields (validate-required-fields error-fields kvds)]
    error-fields))

(defn- init-expiry-duration-selection
  "Iniatializes the expiry related data in entry-form top level field. 
  Returns the updated app-db"
  [app-db entry-form-data]
  (if (:expires entry-form-data)
    (assoc-in-key-db app-db [entry-form-key :expiry-duration-selection] "custom-date")
    (assoc-in-key-db app-db [entry-form-key :expiry-duration-selection] "no-expiry")))

;;;;;;;;;;;;;;;;;;;;;    Section Field Add/Modify    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def field-edit-dialog-key :section-field-dialog-data)

(def standard-kv-fields ["Title" "Notes"])

(def section-field-dialog-init-data {:dialog-show false
                                     :popper-anchor-el nil
                                     :section-name nil
                                     :field-name nil
                                     :field-value nil
                                     :protected false
                                     :required false
                                     :error-fields {}  ;; keys in error-fields map are the field-names 
                                     :add-more false
                                     :mode :add ;; or :modify
                                     :current-field-name nil
                                     :data-type "Text"})

(defn open-section-field-dialog [section-name]
  (dispatch [:section-field-dialog-open section-name nil]))

(defn open-section-field-modify-dialog
  "kv is a map with the existing field data"
  [kv]
  (dispatch [:section-field-modify-dialog-open kv]))

(defn close-section-field-dialog []
  (dispatch [:section-field-dialog-update :dialog-show false]))

(defn section-field-add
  "Receives a map and dispatches that to the add event"
  [field-data-m]
  (dispatch [:section-field-add field-data-m]))

(defn section-field-modify
  "Receives a map and dispatches that to the modify event"
  [field-data-m]
  (dispatch [:section-field-modify field-data-m]))

(defn section-field-dialog-update [field-name-kw value]
  (dispatch [:section-field-dialog-update field-name-kw value]))

(defn section-field-dialog-data []
  (subscribe [:section-field-dialog-data]))

(defn- to-section-field-data 
  "Gets one or more 'kw value' combination as variable parameters and merges with the old data 
   The arg db is the app-db 
   Returns the updated app-db
  "
  [db & {:as kws}] 
  (let [data (get-in-key-db db [entry-form-key field-edit-dialog-key])
        data (merge data kws)]
    (assoc-in-key-db db [entry-form-key field-edit-dialog-key] data)))

(defn- init-section-field-dialog-data 
  [db]
  (assoc-in-key-db db [entry-form-key field-edit-dialog-key] section-field-dialog-init-data))

(defn- is-field-exist
  "Checks that a given field name exists in the entry form or not "
  [app-db field-name] 
  (let [all-section-fields (-> (get-in-key-db
                                app-db
                                [entry-form-key :data :section-fields])
                               vals flatten) ;;all-section-fields is a list of maps for all sections 
        ]
    (or (contains-val? standard-kv-fields field-name)
        (-> (filter (fn [m] (= field-name (:key m))) all-section-fields) seq boolean))))

(defn- add-section-field
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

(defn- modify-section-field [app-db {:keys [section-name
                                            current-field-name
                                            field-name
                                            required
                                            protected]}]
  (let [section-fields-m (get-in-key-db
                          app-db
                          [entry-form-key :data :section-fields]) ;; This is a map
        ;; fields is vector of KVs
        fields (get section-fields-m section-name)
        fields (mapv (fn [m] (if (= (:key m) current-field-name) (assoc m
                                                                        :key field-name
                                                                        :protected protected
                                                                        :required required) m)) fields)
        section-fields-m (assoc section-fields-m section-name fields)]
    (assoc-in-key-db app-db [entry-form-key :data :section-fields] section-fields-m)))


(reg-event-db
 :section-field-dialog-open
 (fn [db [_event-id section-name popper-anchor-el]]
   (-> db
       (init-section-field-dialog-data)
       (to-section-field-data :dialog-show true
                              :section-name section-name
                              :popper-anchor-el popper-anchor-el))))

(reg-event-db
 :section-field-modify-dialog-open
 (fn [db [_event-id {:keys [key section-name protected required]}]]
   (-> db
       (init-section-field-dialog-data)
       (to-section-field-data
        :section-name section-name
        :mode :modify
        :field-name key
        :current-field-name key
        :protected protected
        :required required
        :dialog-show true))))

(reg-event-db
 :section-field-dialog-update
 (fn [db [_event-id field-name-kw value]]
   (if (and (= field-name-kw :dialog-show) (not value))
     (init-section-field-dialog-data db)
     (-> db
         (to-section-field-data field-name-kw value)))))

(reg-event-fx
 :section-field-add
 (fn [{:keys [db]} [_event-id {:keys [section-name field-name] :as m}]] ;; other fields in m are field-value protected
   (if-not (str/blank? field-name)
     (if (is-field-exist db field-name)
       {:db (to-section-field-data db 
                                   :error-fields 
                                   {field-name (str "Field with name " field-name " already exists in this form")})}
       {:db (-> db (add-section-field  m)
                (init-section-field-dialog-data)
                (to-section-field-data :section-name section-name)
                ;;(to-section-field-data :dialog-show true) ;; continue to show dialog 
                (to-section-field-data :dialog-show false) ;; TODO: some ui field added indication
                (to-section-field-data :add-more true))})
     {:db (to-section-field-data db :dialog-show false)})))

(reg-event-fx
 :section-field-modify
 (fn [{:keys [db]} [_event-id {:keys [current-field-name field-name] :as m}]] ;; field-value protected
   (if-not (str/blank? field-name)
     (if (and (not= current-field-name field-name) (is-field-exist db field-name))
       {:db (to-section-field-data db :error-fields {field-name (str "Field with name " field-name " already exists in this form")})}
       {:db (-> db (modify-section-field m)
                (to-section-field-data :dialog-show false))})
     {:db (to-section-field-data db :dialog-show false)})))


(reg-sub
 :section-field-dialog-data
 (fn [db [_query-id]]
   (get-in-key-db db [entry-form-key field-edit-dialog-key])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  Section field delete ;;;;;;;;;;;;;;;;;;;;;;;;

(defn field-delete [section-name field-name-kw]
  (dispatch [:field-delete section-name field-name-kw]))

(defn field-delete-confirm [yes?]
  (dispatch [:field-delete-confirm yes?]))

(defn field-delete-dialog-data []
  (subscribe [:field-delete-dialog-data]))

(reg-event-db
 :field-delete
 (fn [db [_event-id section-name field-name-kw]]
   (assoc-in-key-db db [entry-form-key :field-delete-dialog-data] {:dialog-show true
                                                                   :section-name section-name
                                                                   :field-name field-name-kw})))

(defn- delete-section-field
  "Deletes a field in a section. 
  Returns the updated app-db
  "
  [app-db section-name field-name]
  (let [section-fields (get-in-key-db
                        app-db
                        [entry-form-key :data :section-fields]) 
        kvs (->> (get section-fields section-name)
                 (filterv (fn [m] (not= field-name (:key m)))))
        section-fields (assoc section-fields section-name kvs)] 
    (assoc-in-key-db app-db [entry-form-key :data :section-fields] section-fields)))

(reg-event-db
 :field-delete-confirm
 (fn [db [_event-id yes?]] 
   (if yes?
     (let [section-name (get-in-key-db db [entry-form-key :field-delete-dialog-data :section-name])
           field-name (get-in-key-db db [entry-form-key :field-delete-dialog-data :field-name])]
       (-> db (delete-section-field section-name field-name)
           (assoc-in-key-db [entry-form-key :field-delete-dialog-data]
                            {:dialog-show false :section-name nil :field-name nil})))

     (assoc-in-key-db db [entry-form-key :field-delete-dialog-data]
                      {:dialog-show false :section-name nil :field-name nil}))))

(reg-sub
 :field-delete-dialog-data
 (fn [db [_query-id]]
   (get-in-key-db db [entry-form-key :field-delete-dialog-data])))


;;;;;;;;;;;;;;;;;;;;;;; Section name add/modify ;;;;;;;;;;;;;;;;;;;;;;;;

(defn open-section-name-dialog []
  (dispatch [:section-name-dialog-open]))

(defn open-section-name-modify-dialog [section-name]
  (dispatch [:section-name-modify-dialog-open section-name]))

(defn section-name-dialog-update [kw value]
  (dispatch [:section-name-dialog-update kw value]))

(defn section-name-add-modify [dialog-data]
  (dispatch [:section-name-add-modify dialog-data]))

(defn section-name-dialog-data []
  (subscribe [:section-name-dialog-data]))

(def section-name-dialog-init-data {:dialog-show false
                                    :section-name nil
                                    :error-fields {}  ;; key is the section-name  
                                    :mode :add ;; or :modify
                                    :current-section-name nil})

(defn- to-section-name-dialog-data [db & {:as kws}] 
  (let [data (get-in-key-db db [entry-form-key :section-name-dialog-data])
        data (merge data kws)]
    (assoc-in-key-db db [entry-form-key :section-name-dialog-data] data)))

(defn- init-section-name-dialog-data [db]
  (assoc-in-key-db db [entry-form-key :section-name-dialog-data] section-name-dialog-init-data))

(reg-event-db
 :section-name-dialog-open
 (fn [db [_event-id]] 
   (-> db
       (init-section-name-dialog-data)
       (to-section-name-dialog-data :dialog-show true))))

(reg-event-db
 :section-name-modify-dialog-open
 (fn [db [_event-id section-name]]
   (-> db
       (init-section-name-dialog-data)
       (to-section-name-dialog-data :dialog-show true
                                    :section-name section-name
                                    :mode :modify
                                    :current-section-name section-name))))

(reg-event-db
 :section-name-dialog-update
 (fn [db [_event-id section-kw value]]
   (-> db
       (to-section-name-dialog-data section-kw value))))

(defn- modify-section-name [app-db {:keys [current-section-name section-name]}]
  (let [section-names (get-in-key-db app-db [entry-form-key :data :section-names])
        section-names (mapv
                       (fn [n]
                         (if (= n current-section-name) section-name n))
                       section-names)
        section-fields (get-in-key-db
                        app-db
                        [entry-form-key :data :section-fields]) ;; This is a map

        section-fields  (into {}
                              (map
                               (fn [[k v]]
                                 (if (= k current-section-name) [section-name v] [k v]))
                               section-fields))]

    (-> app-db
        (assoc-in-key-db [entry-form-key :data :section-names] section-names)
        (assoc-in-key-db [entry-form-key :data :section-fields] section-fields))))

;; Called for both add or modify
(reg-event-fx
 :section-name-add-modify
 (fn [{:keys [db]} [_event-id {:keys [section-name current-section-name mode] :as m}]]
   (if (or (str/blank? section-name) (and (= section-name current-section-name) (= mode :modify)))
     {:db (-> db (to-section-name-dialog-data :dialog-show false))}
     (let [section-names (get-in-key-db db [entry-form-key :data :section-names])
           found (contains-val? section-names section-name)]
       (if found
         {:db (-> db (to-section-name-dialog-data
                      :error-fields
                      {:section-name "The name is not unique in this form"}
                      :section-name section-name))}
         (if (= mode :add)
           {:db (-> db
                    (assoc-in-key-db  [entry-form-key :data :section-names] (conj section-names section-name))
                    (to-section-name-dialog-data :dialog-show false))
            :fx [[:dispatch [:common/message-snackbar-open "New section is created"]]]}
           {:db (-> db (modify-section-name m)
                    (to-section-name-dialog-data :dialog-show false))
            :fx [[:dispatch [:common/message-snackbar-open "Section name is changed"]]]}))))))

(reg-sub
 :section-name-dialog-data
 (fn [db [_query-id]]
   (get-in-key-db db [entry-form-key :section-name-dialog-data])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;    Section delete   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; 
;; TODO
;;  Here we are removing/deleting a custom section and its fields during edit on confirming

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;   New Entry ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn on-entry-type-selection
  "Called whenever an entry type name is selected in the New Entry Form"
  [entry-type-uuid]
  (dispatch [:entry-form-entry-type-selected entry-type-uuid]))

(defn on-group-selection
  "Called on selecting a group for the entry in the new entry form view
  The option selected in autocomplete component is passed as 'group-info' - a javacript object
  "
  [group-info]
  (dispatch [:entry-form-group-selected group-info]))

(defn entry-save []
  (dispatch [:entry-save]))

;; Creates a blank New Entry Form with the given type and group are preselected 
;; group-info is a map with keys :name, :uuid
(reg-event-fx
 :entry-form/add-new-entry
 (fn [{:keys [db]} [_event-id group-info entry-type-name]]
   {:fx [[:bg-new-entry-form-data [(active-db-key db) group-info entry-type-name]]]}))

;; Backend API call 
(reg-fx
 :bg-new-entry-form-data
 ;; fn in 'reg-fx' accepts single argument - vector arg typically 
 ;; used so that we can pass more than one input
 (fn [[db-key group-info entry-type-name]]
   (bg/new-entry-form-data db-key entry-type-name (fn [api-response]
                                                    (when-let [form-data (on-ok api-response)]
                                                      (dispatch [:new-blank-entry-created form-data group-info]))))))

;; Called with the result from the background API call which returns
;; a blank entry map that can be used to create a new Entry 
(reg-event-fx
 :new-blank-entry-created
 (fn [{:keys [db]} [_ form-data group-info]]
   (let [form-data (assoc form-data :group-uuid (:uuid group-info)) ;; set the group uuid 
         curr-page (current-page db)]
     {:db (-> db (assoc-in-key-db [entry-form-key :data] form-data)
              (assoc-in-key-db [entry-form-key :undo-data] form-data)
              (init-expiry-duration-selection form-data)
              (assoc-in-key-db [entry-form-key :showing] :new)
              (assoc-in-key-db [entry-form-key :entry-type-name-selection] (:entry-type-name form-data))
              (assoc-in-key-db [entry-form-key :group-selection-info] group-info)
              (assoc-in-key-db [entry-form-key :edit] true)
              (assoc-in-key-db [entry-form-key :error-fields] {}))
      :fx [(when-not (= :entry-form curr-page)
             [:dispatch [:common/next-page :entry-form  "page.titles.entry"]])]})))

(reg-event-db
 :entry-form-group-selected
 (fn [db [_event-id group-info]]
   ;;(println "Calling with group in event " group-info)
   (-> db (assoc-in-key-db  [entry-form-key :group-selection-info] group-info)
       ;; IMPORTANT: Set the entry form data's group uuid. 
       (assoc-in-key-db  [entry-form-key :data :group-uuid] (:uuid group-info)))))

;; Shows a blank New Entry Form with the selected entry uuid
(reg-event-fx
 :entry-form-entry-type-selected
 (fn [{:keys [db]} [_event-id entry-type-uuid]]
   (let [group-info (get-in-key-db db [entry-form-key :group-selection-info])]
     {:fx [[:dispatch [:entry-form/add-new-entry group-info entry-type-uuid]]]})))

(reg-event-fx
 :entry-save
 (fn [{:keys [db]} [_event-id]]
   (let [form-data (get-in-key-db db [entry-form-key :data])
         showing (get-in-key-db db [entry-form-key :showing]) ;; :new or :selected
         error-fields (validate-all form-data) 
         errors-found (boolean (seq error-fields))] 
     (if errors-found
       {:db (assoc-in-key-db db [entry-form-key :error-fields] error-fields)}
       {:db (assoc-in-key-db db [entry-form-key :error-fields] error-fields)
        :fx [[:dispatch [:common/message-modal-show nil "Entry insert/update ..."]]
             (if (= showing :new)
               [:bg-insert-entry [(active-db-key db) form-data]]
               [:bg-update-entry [(active-db-key db) form-data]])]}))))

(reg-fx
 :bg-insert-entry
 (fn [[db-key new-entry-form-data]] 
   (bg/insert-entry db-key
                    new-entry-form-data
                    (fn [api-response]
                      (when-not (on-error api-response (fn [error]
                                                         (dispatch [:insert-update-entry-form-data-error error])))
                        (dispatch [:insert-update-entry-form-data-complete]))))))

(reg-fx
 :bg-update-entry
 (fn [[db-key entry-form-data]] 
   (bg/update-entry db-key
                    entry-form-data
                    (fn [api-response]
                      (when-not (on-error api-response (fn [error]
                                                         (dispatch [:insert-update-entry-form-data-error error])))
                        (dispatch [:insert-update-entry-form-data-complete]))))))

(defn- on-save-complete [api-response] 
  (when-not (on-error api-response (fn [error]
                                     (dispatch [:entry-insert-update-save-error error])))
    (dispatch [:entry-insert-update-save-complete])))

(reg-event-fx
 :insert-update-entry-form-data-complete
 (fn [{:keys [db]} [_event-id]]
   {:fx [;; Need to save after inserting or updating an entry data
         [:dispatch [:common/message-modal-show nil "Saving ..."]]
         [:common/bg-save-kdbx [(active-db-key db) on-save-complete]]]}))

(reg-event-fx
 :insert-update-entry-form-data-error
 (fn [{:keys [_db]} [_event-id error]] 
   {:fx [[:dispatch [:common/message-modal-hide]]
         [:dispatch [:common/error-box-show "Entry insert/update" error]]]}))

(reg-event-fx
 :entry-insert-update-save-complete
 (fn [{:keys [db]} [_event-id]]
   (let [current-showing (get-in-key-db db [entry-form-key :showing])
         restored? (get-in-key-db db [entry-form-key :entry-history-form :history-entry-restore-confirmed])
         db (if (= :new current-showing)
              (assoc-in-key-db db [entry-form-key :undo-data] (get-in-key-db db [entry-form-key :data]))
              db)]
     ;; If showing is already :selected, then this is update and we need to reload the entry data
     {:db (-> db (assoc-in-key-db  [entry-form-key :edit] false)
              (assoc-in-key-db [entry-form-key :showing] :selected))
      :fx [;; Reload entry data again to reflect the saved changes
           (when (= :selected current-showing)
             [:dispatch [:reload-entry-by-id (get-in-key-db db [entry-form-key :data :uuid])]])
           (if (= :new current-showing)
             [:dispatch [:common/message-snackbar-open "Created Entry"]]
             [:dispatch [:common/message-snackbar-open "Updated Entry"]])
           ;; Need to reload categories, groups and list data on an entry insert/update
           [:dispatch [:entry-category/load-categories-to-show]]
           [:dispatch [:groups/load]]
           [:dispatch [:entry-list/reload-selected-entry-items]]
           [:dispatch [:search/reload]]
           [:dispatch [:common/message-modal-hide]]
           ;; Entry update is due to restoring from history
           (when restored?
             [:dispatch [:history-entry-restore-complete]])]})))

(reg-event-fx
 :entry-insert-update-save-error
 (fn [{:keys [_db]} [_event-id error]]
   {:fx [[:dispatch [:common/message-modal-hide]]
         [:dispatch [:common/error-box-show "Save entry" error]]]}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  Entry History  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn load-history-entries-summary [entry-uuid]
  (dispatch [:load-history-entries-summary entry-uuid]))

(defn load-selected-history-entry [entry-uuid index]
  (dispatch [:load-selected-history-entry entry-uuid index]))

(defn cancel-history-entry-form []
  (dispatch [:cancel-history-entry-form]))

(defn show-history-entry-delete-confirm-dialog []
  (dispatch [:history-entry-delete-confirm-open true]))

(defn close-history-entry-delete-confirm-dialog []
  (dispatch [:history-entry-delete-confirm-open false]))

(defn delete-history-entry-by-index [entry-uuid index]
  (dispatch [:delete-history-entry-by-index entry-uuid index]))

(defn show-history-entry-restore-confirm-dialog []
  (dispatch [:history-entry-restore-confirm-open true]))

(defn close-history-entry-restore-confirm-dialog []
  (dispatch [:history-entry-restore-confirm-open false]))

(defn restore-entry-from-history
  "Retores the selected history form data. This event is triggered from 
   the history entry form screen
  "
  []
  (dispatch [:restore-entry-from-history]))

(defn delete-all-history-entries [entry-uuid]
  (dispatch [:delete-all-history-entries entry-uuid]))

(defn show-history-entry-delete-all-confirm-dialog []
  (dispatch [:history-entry-delete-all-confirm-open true]))

(defn close-history-entry-delete-all-confirm-dialog []
  (dispatch [:history-entry-delete-all-confirm-open false]))

(defn history-available []
  (subscribe [:history-available]))

(defn history-entry-selected-index []
  (subscribe [:history-entry-selected-index]))

(defn history-summary-list
  "Gets history entries list data"
  []
  (subscribe [:history-entries-summary-list]))

(defn history-entry-delete-flag []
  (subscribe [:history-entry-delete-flag]))

(defn history-entry-restore-flag []
  (subscribe [:history-entry-restore-flag]))

(defn history-entry-delete-all-flag []
  (subscribe [:history-entry-delete-all-flag]))

(reg-event-fx
 :load-history-entries-summary
 (fn [{:keys [db]} [_event-id entry-uuid]]
   (bg/history-entries-summary (active-db-key db)
                               entry-uuid
                               (fn [api-response] 
                                 (when-let [summary-list (on-ok api-response)]
                                   (dispatch [:load-history-entries-summary-complete summary-list]))))
   {}))

(reg-event-fx
 :load-history-entries-summary-complete
 (fn [{:keys [db]} [_event-id summary-list]]
   {:db (-> db
            (assoc-in-key-db [entry-form-key :entry-history-form] {})
            (assoc-in-key-db [entry-form-key :entry-history-form :entries-summary-list] summary-list))
    :fx [[:dispatch [:common/next-page :entry-history-list "page.titles.histories"]]]}))


(reg-event-fx
 :load-selected-history-entry
 (fn [{:keys [db]} [_event-id entry-id index]]
   {:db (-> db
            (assoc-in-key-db  [entry-form-key :entry-history-form :entry-selected] true)
            (assoc-in-key-db  [entry-form-key :entry-history-form :selected-index] index))
    :fx [[:bg-history-entry-by-index [(active-db-key db) entry-id index]]]}))

(reg-fx
 :bg-history-entry-by-index
 (fn [[db-key entry-id index]]
   (bg/history-entry-by-index db-key entry-id index
                              (fn [api-response]
                                (when-let [entry (on-ok api-response)]
                                  (dispatch [:history-entry-form-data-load-completed entry]))))))

(reg-event-fx
 :history-entry-form-data-load-completed
 (fn [{:keys [db]} [_event-id entry]] ;;result is  
   {:db (-> db
            (assoc-in-key-db [entry-form-key :data] entry)
            (assoc-in-key-db [entry-form-key :edit] false)
            (assoc-in-key-db [entry-form-key :error-fields] {})
            (assoc-in-key-db [entry-form-key :showing] :history-entry))
    :fx [[:dispatch [:common/next-page :entry-form  "page.titles.historyEntries"]]]}))


(reg-event-fx
 :cancel-history-entry-form
 (fn [{:keys [db]} [_event-id]]
   {:fx [;; Navigates to the history list page first
         [:dispatch [:common/previous-page]]
         ;; Need to reload the entry so that the entry form data is loaded before user backs to that page
         [:dispatch [:reload-entry-by-id (get-in-key-db db [entry-form-key :data :uuid])]]]}))

(reg-event-db
 :history-entry-delete-confirm-open
 (fn [db [_ open?]]
   (assoc-in-key-db db  [entry-form-key :entry-history-form :history-entry-delete-flag] open?)))

(reg-event-fx
 :delete-history-entry-by-index
 (fn [{:keys [db]} [_event-id entry-id index]]
   {:fx [[:bg-delete-history-entry-by-index [(active-db-key db) entry-id index]]]}))

(reg-fx
 :bg-delete-history-entry-by-index
 (fn [[db-key entry-id index]]
   (bg/delete-history-entry-by-index db-key entry-id index
                                     (fn [api-response]
                                       (when-not (on-error api-response)
                                         (dispatch [:history-entry-delete-complete entry-id]))))))

(reg-event-fx
 :history-entry-delete-complete
 (fn [{:keys [db]} [_event-id entry-id]]
   (let [in-entry-form? (= (current-page db) :entry-form)]
     {:fx [[:dispatch [:history-entry-delete-confirm-open false]]
           ;; Closes the history entry form and reloads the entry data without making 
           ;; it as current page and goes to the history list
           (when in-entry-form?
             [:dispatch [:cancel-history-entry-form]])
           ;; reload the histories to reflect the delete
           [:dispatch [:load-history-entries-summary entry-id]]
           [:dispatch [:common/save-current-kdbx {:error-title "Save entry history delete"
                                                  :save-message "Saving entry history delete...."}]]]})))


;;; Delete all histories

(reg-event-db
 :history-entry-delete-all-confirm-open
 (fn [db [_ open?]]
   (assoc-in-key-db db  [entry-form-key :entry-history-form :history-entry-delete-all-flag] open?)))

(reg-event-fx
 :delete-all-history-entries
 (fn [{:keys [db]} [_event-id entry-id]]
   {:fx [#_[:dispatch [:common/message-modal-show nil "Deleting histories..."]]
         [:bg-delete-history-entries [(active-db-key db) entry-id]]]}))

(reg-fx
 :bg-delete-history-entries
 (fn [[db-key entry-id]]
   (bg/delete-history-entries db-key entry-id
                              (fn [api-response]
                                (when-not (on-error api-response)
                                  (dispatch [:delete-all-history-entries-complete entry-id]))))))


(reg-event-fx
 :delete-all-history-entries-complete
 (fn [{:keys [_db]} [_event-id entry-id]]
   {:fx [[:dispatch [:history-entry-delete-all-confirm-open false]] 
         [:dispatch [:common/save-current-kdbx {:error-title "Deleting and saving histories"
                                                :save-message "Deleting all histories and saving"
                                                :on-save-ok (fn []
                                                              (dispatch [:reload-entry-by-id entry-id])
                                                              (dispatch [:common/message-snackbar-open "snackbar.messages.historiesDeleted"])
                                                              (dispatch [:common/previous-page]))}]]]}))


(reg-event-db
 :history-entry-restore-confirm-open
 (fn [db [_ open?]]
   (assoc-in-key-db db  [entry-form-key :entry-history-form :history-entry-restore-flag] open?)))

(reg-event-fx
 :restore-entry-from-history
 (fn [{:keys [db]} [_event-id]]
   ;; Update the entry with the selected version of history entry data 
   (let [form-data (get-in-key-db db [entry-form-key :data])]
     {:db (-> db (assoc-in-key-db [entry-form-key :entry-history-form :history-entry-restore-confirmed] true))
      ;; Call the entry update
      :fx [[:bg-update-entry [(active-db-key db) form-data]]]})))

;; Will be called after restoring - follow bg-update-entry ..
(reg-event-fx
 :history-entry-restore-complete
 (fn [{:keys [db]} [_event-id]]
   (let [in-entry-form? (= (current-page db) :entry-form)
         entry-id (get-in-key-db db [entry-form-key :data :uuid])]
     {:db (-> db (assoc-in-key-db [entry-form-key :entry-history-form :history-entry-restore-confirmed] false))
      :fx [[:dispatch [:history-entry-restore-confirm-open false]]
           (when in-entry-form?
             [:dispatch [:cancel-history-entry-form]])
           ;; reload the histories to reflect the restore
           [:dispatch [:load-history-entries-summary entry-id]]]})))

(reg-sub
 :history-available
 :<- [:entry-form-data]
 (fn [data _query-vec]
   (> (:history-count data) 0)))

(reg-sub
 :entry-history-form
 (fn [db _query-vec]
   (get-in-key-db db [entry-form-key :entry-history-form])))

(reg-sub
 :history-entries-summary-list
 :<- [:entry-history-form]
 (fn [history-form _query-vec]
   (:entries-summary-list history-form)))

(reg-sub
 :history-entry-delete-flag
 :<- [:entry-history-form]
 (fn [history-form _query-vec]
   (:history-entry-delete-flag history-form)))

(reg-sub
 :history-entry-delete-all-flag
 :<- [:entry-history-form]
 (fn [history-form _query-vec]
   (:history-entry-delete-all-flag history-form)))

(reg-sub
 :history-entry-restore-flag
 :<- [:entry-history-form]
 (fn [history-form _query-vec]
   (:history-entry-restore-flag history-form)))

(reg-sub
 :history-entry-selected-index
 :<- [:entry-history-form]
 (fn [history-form _query-vec]
   (:selected-index history-form)))

(reg-sub
 :loaded-history-entry-uuid
 :<- [:history-entries-summary-list]
 (fn [list _query-vec]
   (:uuid (first list))))

(comment
  (in-ns 'onekeepass.mobile.events.entry-form)
 
  (def db-key (-> @re-frame.db/app-db :current-db-file-name))
  (-> (get @re-frame.db/app-db db-key) :entry-form :data keys)
  (-> (get @re-frame.db/app-db db-key) :entry-form :data :group-uuid))