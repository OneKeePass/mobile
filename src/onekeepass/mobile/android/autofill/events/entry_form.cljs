(ns onekeepass.mobile.android.autofill.events.entry-form
  (:require [onekeepass.mobile.android.autofill.events.common :refer [android-af-active-db-key]]
            [onekeepass.mobile.background :as bg]
            [onekeepass.mobile.events.common :refer [on-ok]]
            [onekeepass.mobile.events.entry-form-common :refer [entry-form-key
                                                                extract-form-otp-fields]]
            [onekeepass.mobile.events.native-events :as native-events]
            [onekeepass.mobile.utils :as u :refer [contains-val?]]
            [re-frame.core :refer [dispatch reg-event-db reg-event-fx reg-fx
                                   reg-sub subscribe]]))

(defn entry-form-field-visibility-toggle
  "Called with the field name as key that is toggled between show/hide"
  [key]
  (dispatch [:android-af-entry-form-field-visibility-toggle key]))

(defn entry-form-data-fields
  " 
  Called to get value of one more of form top level fields. 
  The arg is a single field name or  fields in a vector of two more field 
  names (Keywords) like [:title :icon-id]
  Returns an atom which resolves to a single value  or a map when derefenced
  e.g {:title 'value'} or {:tags 'value} or {:title 'value' :icon-id 'value}
   "
  [fields]
  (subscribe [:android-af-entry-form-data-fields fields]))

(defn entry-form-uuid []
  (subscribe [:android-af-entry-form-data-fields :uuid]))

(defn entry-form
  "Returns an atom that has the map entry-form"
  []
  (subscribe [:android-af-entry-form]))

(defn entry-form-field
  "Gets the value of any field at the top level in entry-form itself. See other subs to get the field
  values from :data or [:data :section-fields] 
  "
  [file-name-kw]
  (subscribe [:android-af-entry-form-field file-name-kw]))

(defn visible? [key]
  (subscribe [:android-af-entry-form-field-in-visibile-list key]))

(defn- on-entry-find [api-response]
  ;; :entry-form-data-load-error from main entry-form event is reused
  (when-let [entry (on-ok api-response #(dispatch [:entry-form-data-load-error %]))]
    (dispatch [:android-af-entry-form-data-load-completed entry])
    (dispatch [:android-af-entry-list/form-loaded])))

;; Called when user selects an entry uuid by long press on entry list page
(reg-event-fx
 :android-af-entry-form/find-entry-by-id
 (fn [{:keys [db]} [_event-id entry-uuid]]
   {:fx [[:bg-android-af-find-entry-by-id [(android-af-active-db-key db) entry-uuid on-entry-find]]]}))

(reg-fx
 :bg-android-af-find-entry-by-id
 (fn [[db-key entry-uuid dispatch-fn]]
   (bg/find-entry-by-id db-key entry-uuid dispatch-fn)))


;; IMPORTANT: Valid values for :showing are [:selected :new :history-form]
(defn- set-on-entry-load [app-db entry-form-data]
  (let [otp-fields (extract-form-otp-fields entry-form-data)]
    (-> app-db
        (assoc-in [:android-af entry-form-key :data] entry-form-data)
        (assoc-in [:android-af entry-form-key :undo-data] entry-form-data)
        (assoc-in [:android-af entry-form-key :otp-fields] otp-fields)
        (assoc-in [:android-af entry-form-key :showing] :selected)
        (assoc-in [:android-af entry-form-key :edit] false))))

(reg-event-fx
 :android-af-entry-form-data-load-completed
 (fn [{:keys [db]} [_event-id entry-form-data]]
   {:db  (set-on-entry-load db entry-form-data)}))

;;;;;
;; Toggles the a field's membership in a list of visibility fields
(reg-event-db
 :android-af-entry-form-field-visibility-toggle
 (fn [db [_event-id key]]
   (let [vl (get-in db [:android-af entry-form-key :visibility-list])]
     (if (contains-val? vl key)
       (assoc-in db [:android-af entry-form-key :visibility-list] (filterv #(not= % key) vl))
       (assoc-in db [:android-af entry-form-key :visibility-list] (conj vl key))))))


;; Checks whether a form field is visible or not
(reg-sub
 :android-af-entry-form-field-in-visibile-list
 (fn [db [_query-id key]]
   (contains-val? (get-in db [:android-af entry-form-key :visibility-list]) key)))

(reg-sub
 :android-af-entry-form
 (fn [db _query-vec]
   (get-in db [:android-af entry-form-key])))

(reg-sub
 :android-af-entry-form-data
 (fn [db _query-vec]
   (get-in db [:android-af entry-form-key :data])))

;; Gets a :data level field value
(reg-sub
 :android-af-entry-form-data-fields
 :<- [:android-af-entry-form-data]
 (fn [data [_query-id fields]]
   (if-not (vector? fields)
     ;; fields is a single field name
     (get data fields)
     ;; a vector field names
     (select-keys data fields))))


;; Gets the value of a field at top level 'entry-form' itself
(reg-sub
 :android-af-entry-form-field
 :<- [:android-af-entry-form]
 (fn [form [_query-id field]]
   ;;(println "form-db called... " form)
   (get form field)))

;; (reg-sub
;;  :entry-form-edit
;;  (fn [db _query-vec]
;;    (get-in db [entry-form-key :edit])))


;;;;;;;;;;;;;;;;;;;;; OTP ;;;;;;;;;;;;;;;;;;;;;;

(defn entry-form-otp-start-polling
  "Called to start polling from use effect"
  []
  (dispatch [:android-af/entry-form-otp-start-polling]))

(defn entry-form-otp-stop-polling
  "Called to stop polling from use effect"
  []
  (dispatch [:android-af/entry-form-otp-stop-polling]))

(defn otp-currrent-token [opt-field-name]
  (subscribe [:android-af/otp-currrent-token opt-field-name]))

;; Called to start polling from use effect
(reg-event-fx
 :android-af/entry-form-otp-start-polling
 (fn [{:keys [db]} [_event-id]]
   (let [form-status (get-in db [:android-af entry-form-key :showing])
         entry-uuid (get-in db [:android-af entry-form-key :data :uuid])
         otp-fields (extract-form-otp-fields (get-in db [:android-af entry-form-key :data]))]
     (if (= form-status :selected)
       {:fx [[:otp/start-polling-otp-fields [(android-af-active-db-key db)
                                             entry-uuid
                                             otp-fields]]]}
       {}))))

;; Called to stop polling from use effect
(reg-event-fx
 :android-af/entry-form-otp-stop-polling
 (fn [{:keys [db]} [_event-id]]
   (let [form-status (get-in db [:android-af entry-form-key :showing])]
     (if (= form-status :selected)
       {:fx [[:otp/stop-all-entry-form-polling [(android-af-active-db-key db) nil]]]}
       {}))))

;; Called from backend event handler (see native_events.cljs) to update with the current tokens 
(reg-event-fx
 :android-af/entry-form-update-otp-tokens
 (fn [{:keys [db]} [_event-id entry-uuid current-opt-tokens-by-field]]
   ;; First we need to ensure that the incoming entry id is the same one showing
   (if (= entry-uuid (get-in db [:android-af entry-form-key :data :uuid]))

     ;; otp-fields is a map with otp field name as key and its token info with time ttl 
     (let [db (reduce (fn [db [otp-field-name {:keys [token ttl]}]]
                        (let [otp-field-name (name otp-field-name) ;; make sure field name is string
                              otp-field-m (get-in db [:android-af entry-form-key :otp-fields otp-field-name])
                              otp-field-m (if (nil? token)
                                            (assoc otp-field-m :ttl ttl)
                                            (assoc otp-field-m :token token :ttl ttl))]
                          (assoc-in db [:android-af entry-form-key :otp-fields otp-field-name] otp-field-m)))
                      db current-opt-tokens-by-field)]

       ;;(println "After otp-fields " (get-in db [entry-form-key :otp-fields]))

       {:db db})
     {})))

;; Returns a map with otp fileds as key and its token info as value
;; e.g {"My Git OTP Code" {:token "576331", :ttl 9, :period 30}, "otp" {:token "145214", :ttl 9, :period 30}}
(reg-sub
 :android-af/otp-currrent-token
 (fn [db [_query-id otp-field-name]]
   (get-in db [:android-af entry-form-key :otp-fields otp-field-name])))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Native event listener ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn register-entry-otp-update-handler 
 "The register-event-listener is called to register android autofill specific listener " 
  []
  #_(println "register-entry-otp-update-handler is called")
  (bg/register-event-listener :android-af  native-events/EVENT_ENTRY_OTP_UPDATE
                              (fn [event-message]
                                (let [converted (bg/transform-api-response event-message 
                                                                           {:convert-response-fn native-events/token-response-converter})]
                                  (when-let [{:keys [entry-uuid reply-field-tokens]} (on-ok converted)]
                                    (dispatch [:android-af/entry-form-update-otp-tokens entry-uuid reply-field-tokens]))))))


(register-entry-otp-update-handler)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(comment
  (in-ns 'onekeepass.mobile.android.autofill.events.entry-form)

  (def db-key-af (-> @re-frame.db/app-db :android-af :current-db-file-name)))
