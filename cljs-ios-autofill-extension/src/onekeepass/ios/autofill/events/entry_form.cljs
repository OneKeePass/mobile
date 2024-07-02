(ns onekeepass.ios.autofill.events.entry-form
  (:require [clojure.string :as str]
            [onekeepass.ios.autofill.background :as bg]
            [onekeepass.ios.autofill.constants :refer [ONE_TIME_PASSWORD_TYPE
                                                       PASSWORD USERNAME]]
            [onekeepass.ios.autofill.events.common :refer [active-db-key
                                                           on-error on-ok]]
            [onekeepass.ios.autofill.utils :refer [contains-val?]]
            [re-frame.core :refer [dispatch reg-event-db reg-event-fx reg-fx
                                   reg-sub subscribe]]))


;; Time in seconds
(def CLIPBOARD_CLEAR_AFTER 20)

(def ^:private standard-kv-fields ["Title" "Notes"])

(def entry-form-key :entry-form)

(def Favorites "Favorites")

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn entry-form-field-visibility-toggle
  "Called with the field name as key that is toggled between show/hide"
  [key]
  (dispatch [:entry-form-field-visibility-toggle key]))

(defn cancel-entry-form []
  #_(dispatch [:common/previous-page])
  (dispatch [:cancel-entry-form]))

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

(defn visible? [key]
  (subscribe [:entry-form-field-in-visibile-list key]))

(defn form-edit-mode
  "Returns an atom to indiacate editing is true or not"
  []
  (subscribe [:entry-form-edit]))

;; IMPORTANT: Valid values for :showing are [:selected :new :history-form]
(defn- set-on-entry-load [app-db entry-form-data]
  (let [otp-fields (extract-form-otp-fields entry-form-data)]
    (-> app-db
        (assoc-in [entry-form-key :data] entry-form-data)
        (assoc-in [entry-form-key :undo-data] entry-form-data)
        (assoc-in [entry-form-key :otp-fields] otp-fields)
        (assoc-in [entry-form-key :showing] :selected)
        (assoc-in [entry-form-key :edit] false))))

#_(defn- on-entry-find [api-response]
    (when-let [entry (on-ok api-response #(dispatch [:entry-form-data-load-error %]))]
      (dispatch [:entry-form-data-load-completed entry true])))

#_(reg-event-fx
   :entry-form/find-entry-by-id
   (fn [{:keys [db]} [_event-id entry-uuid]]
     #_(println "entry-form/find-entry-by-id is called for uuid " entry-uuid)
     {:fx [[:bg-find-entry-by-id [(active-db-key db) entry-uuid on-entry-find]]]}))

(defn- on-entry-find [api-response]
  (when-let [entry (on-ok api-response #(dispatch [:entry-form-data-load-error %]))]
    (dispatch [:entry-form-data-load-completed entry])
    (dispatch [:entry-list/form-loaded])))

;; Called when user selects an entry uuid by long press on entry list page
(reg-event-fx
 :entry-form/find-entry-by-id
 (fn [{:keys [db]} [_event-id entry-uuid]]
   {:fx [[:bg-find-entry-by-id [(active-db-key db) entry-uuid on-entry-find]]]}))

(reg-fx
 :bg-find-entry-by-id
 (fn [[db-key entry-uuid dispatch-fn]]
   (bg/find-entry-by-id db-key entry-uuid dispatch-fn)))

#_(reg-event-fx
   :entry-form-data-load-completed
   (fn [{:keys [db]} [_event-id entry-form-data navigate?]]
     #_(println "entry-form-data-load-completed called with data " entry-form-data)
   ;; When navigate? is false, we just set the loaded entry data and not change the page 
     {:db  (set-on-entry-load db entry-form-data)
      :fx [(when navigate? [:dispatch [:common/next-page ENTRY_FORM_PAGE_ID  "Entry"]])]}))

(reg-event-fx
 :entry-form-data-load-completed
 (fn [{:keys [db]} [_event-id entry-form-data]]
   {:db  (set-on-entry-load db entry-form-data)}))

(reg-event-fx
 :entry-form-data-load-error
 (fn [{:keys [_db]} [_event-id error]]
   {:fx [[:dispatch [:common/error-box-show "Find entry" error]]]}))

(reg-event-fx
 :cancel-entry-form
 (fn [{:keys [db]} [_event-id]]
   {:db (assoc-in db [entry-form-key :error-fields] {})
    :fx [[:dispatch [:common/previous-page]]]}))

(reg-sub
 :entry-form
 (fn [db _query-vec]
   (get-in db [entry-form-key])))

(reg-sub
 :entry-form-data
 (fn [db _query-vec]
   (get-in db [entry-form-key :data])))

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

(reg-sub
 :entry-form-edit
 (fn [db _query-vec]
   (get-in db [entry-form-key :edit])))

;;;;;
;; Toggles the a field's membership in a list of visibility fields
(reg-event-db
 :entry-form-field-visibility-toggle
 (fn [db [_event-id key]]
   (let [vl (get-in db [entry-form-key :visibility-list])]
     (if (contains-val? vl key)
       (assoc-in db [entry-form-key :visibility-list] (filterv #(not= % key) vl))
       (assoc-in db [entry-form-key :visibility-list] (conj vl key))))))

;; Checks whether a form is field is visible or not
(reg-sub
 :entry-form-field-in-visibile-list
 (fn [db [_query-id key]]
   (contains-val? (get-in db [entry-form-key :visibility-list]) key)))

;;;;;;;;;;;;;;;;;;;;;;;;; Send credentials event ;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- find-field 
  "Finds the KV data map (KeyValueData struct) for a given field name from an entries data map
   The arg 'form-data' is a map based on struct EntryFormData
   The field name is a string 
   "
  [form-data field-name]
  (let [kvds (flatten (vals (:section-fields form-data)))]
    (first (filter (fn [m] (= field-name (:key m))) kvds))))

;; This is event is triggered when user press an entry in 'entry list' page
(reg-event-fx
 :entry-form/send-credentials-selected
 (fn [{:keys [db]} [_event-id entry-uuid]]
   ;; Calls the backend api to find the selected entry's details
   (bg/find-entry-by-id
    (active-db-key db)
    entry-uuid
    (fn [api-response]
      (when-let [entry (on-ok api-response #(dispatch [:entry-form-data-load-error %]))]
        ;; Entry data is loaded 
        (let [kv1 (find-field entry USERNAME)
              kv2 (find-field entry PASSWORD)]
          ;; Calling this backend api will send the user name and password to the app page 
          ;; from where OneKeePass autofill was launched and the extension will close after this 
          (bg/credentials-selected (:value kv1) (:value kv2) #())))))
   {}))

;;;;;;;;;;;;;;;;;;;;;;;;; Clipboard events  ;;;;;;;;;;;;;;;;;;;;;;;;;

(defn copy-field-to-clipboard [field-name]
  (dispatch [:entry-form/copy-field-to-clipboard field-name]))

(defn copy-otp-token-to-clipboard [field-name token-value]
  (dispatch [:entry-form/copy-otp-token-to-clipboard field-name token-value]))

(defn- to-clipboard-arg-info
  "Called to convert the entry form's KeyValue field data to arg map required to copy to clipboard"
  [{:keys [key value protected]}]
  ;; cleanup-after is in seconds
  {:field-name key :field-value value :protected protected :cleanup-after CLIPBOARD_CLEAR_AFTER})

(reg-event-fx
 :entry-form/copy-field-to-clipboard
 (fn [{:keys [db]} [_event-id field-name]]
   (let [form-data (get-in db [entry-form-key :data])
         {:keys [value] :as info} (find-field form-data field-name)]

     (if-not (str/blank? value)
       (let [arg (to-clipboard-arg-info info)]
         {:fx [[:entry-form/bg-copy-to-clipboard [arg #()]]
               [:dispatch [:common/message-snackbar-open 'copied]]]})
       {:fx [[:dispatch [:common/message-snackbar-open 'noCopy]]]}))))

;; OTP clipboard copy event 
(reg-event-fx
 :entry-form/copy-otp-token-to-clipboard
 (fn [{:keys [_db]} [_event-id field-name token-value]]
   {:fx [[:entry-form/bg-copy-to-clipboard
          [{:field-name field-name
            :field-value token-value
            :protected true
            :cleanup-after CLIPBOARD_CLEAR_AFTER}
           #(dispatch [:common/message-snackbar-open 'copied])]]]}))

(reg-fx
 :entry-form/bg-copy-to-clipboard
 (fn [[field-info call-on-dispatch]]
   (bg/copy-to-clipboard field-info call-on-dispatch)))


#_(reg-sub
   :entry-form-data-selected-field
   :<- [:entry-form-data]
   (fn [data [_query-id field-name]]
     (find-field data field-name)))

;;;;;;;;;;;;;;;;;;;;; OTP ;;;;;;;;;;;;;;;;;;;;;;

(defn entry-form-otp-start-polling
  "Called to start polling from use effect"
  []
  (dispatch [:entry-form-otp-start-polling]))

(defn entry-form-otp-stop-polling
  "Called to stop polling from use effect"
  []
  (dispatch [:entry-form-otp-stop-polling]))

(defn otp-currrent-token [opt-field-name]
  (subscribe [:otp-currrent-token opt-field-name]))

;; Called to start polling from use effect
(reg-event-fx
 :entry-form-otp-start-polling
 (fn [{:keys [db]} [_event-id]]
   (let [form-status (get-in db [entry-form-key :showing])
         entry-uuid (get-in db [entry-form-key :data :uuid])
         otp-fields (extract-form-otp-fields (get-in db [entry-form-key :data]))]
     (if (= form-status :selected)
       {:fx [[:otp/start-polling-otp-fields [(active-db-key db)
                                             entry-uuid
                                             otp-fields]]]}
       {}))))

;; Called to stop polling from use effect
(reg-event-fx
 :entry-form-otp-stop-polling
 (fn [{:keys [db]} [_event-id]]
   (let [form-status (get-in db [entry-form-key :showing])]
     (if (= form-status :selected)
       {:fx [[:otp/stop-all-entry-form-polling [(active-db-key db) nil]]]}
       {}))))

;; Called from backend event handler (see native_events.cljs) to update with the current tokens 
(reg-event-fx
 :entry-form/update-otp-tokens
 (fn [{:keys [db]} [_event-id entry-uuid current-opt-tokens-by-field]]
   ;; First we need to ensure that the incoming entry id is the same one showing
   (if (= entry-uuid (get-in db [entry-form-key :data :uuid]))

     ;; otp-fields is a map with otp field name as key and its token info with time ttl 
     (let [db (reduce (fn [db [otp-field-name {:keys [token ttl]}]]
                        (let [otp-field-name (name otp-field-name) ;; make sure field name is string
                              otp-field-m (get-in db [entry-form-key :otp-fields otp-field-name])
                              otp-field-m (if (nil? token)
                                            (assoc otp-field-m :ttl ttl)
                                            (assoc otp-field-m :token token :ttl ttl))]
                          (assoc-in db [entry-form-key :otp-fields otp-field-name] otp-field-m)))
                      db current-opt-tokens-by-field)]

       ;;(println "After otp-fields " (get-in db [entry-form-key :otp-fields]))

       {:db db})
     {})))

(reg-fx
 :otp/start-polling-otp-fields
 (fn [[db-key  entry-uuid otp-field-m]]
   ;; otp-field-m is a map with otp field name as key and token data as its value
   ;; See 'start-polling-otp-fields' fn
   (let [fields-m (into {} (filter (fn [[_k v]] (not (nil? v))) otp-field-m))]
     (if (boolean (seq fields-m))
       (bg/start-polling-entry-otp-fields
        db-key
        entry-uuid fields-m #(on-error %))
       (bg/stop-polling-all-entries-otp-fields
        db-key
        #(on-error %))))))

(reg-fx
 :otp/stop-all-entry-form-polling
 (fn [[db-key dispatch-fn]]
   ;;(println "Stopping form polling for db-key " (last (str/split db-key "/")))
   (bg/stop-polling-all-entries-otp-fields db-key (if-not (nil? dispatch-fn) dispatch-fn #(on-error %)))))

;; Returns a map with otp fileds as key and its token info as value
;; e.g {"My Git OTP Code" {:token "576331", :ttl 9, :period 30}, "otp" {:token "145214", :ttl 9, :period 30}}
(reg-sub
 :otp-currrent-token
 (fn [db [_query-id otp-field-name]]
   (get-in db [entry-form-key :otp-fields otp-field-name])))


(comment
  (in-ns 'onekeepass.ios.autofill.events.entry-form)
  (def db-key (-> @re-frame.db/app-db :current-db-file-name))
  (-> @re-frame.db/app-db (get db-key) keys))