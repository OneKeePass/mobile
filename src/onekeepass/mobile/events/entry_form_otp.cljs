(ns onekeepass.mobile.events.entry-form-otp
  "Entry form otp events"
  (:require [onekeepass.mobile.background :as bg]
            [onekeepass.mobile.constants :refer [ONE_TIME_PASSWORD_TYPE]]
            [onekeepass.mobile.events.common :as cmn-events :refer [active-db-key
                                                                    assoc-in-key-db
                                                                    get-in-key-db
                                                                    on-error]]
            [onekeepass.mobile.events.entry-form-common :refer [add-section-field
                                                                entry-form-key
                                                                extract-form-otp-fields
                                                                merge-section-key-value]]
            [re-frame.core :refer [dispatch reg-event-fx reg-fx reg-sub]]))


(reg-sub
 :otp-currrent-token
 (fn [db [_query-id otp-field-name]]
   (get-in-key-db db [entry-form-key :otp-fields otp-field-name])))


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


(reg-event-fx
 :entry-form-otp-start-polling
 (fn [{:keys [db]} [_event-id]]
   (let [form-status (get-in-key-db db [entry-form-key :showing])
         entry-uuid (get-in-key-db db [entry-form-key :data :uuid])
         otp-fields (extract-form-otp-fields (get-in-key-db db [entry-form-key :data]))]
     (if (= form-status :selected)
       {:fx [[:otp/start-polling-otp-fields [(active-db-key db)
                                             entry-uuid
                                             otp-fields]]]}
       {}))))


(reg-event-fx
 :entry-form-otp-stop-polling
 (fn [{:keys [db]} [_event-id]]
   (let [form-status (get-in-key-db db [entry-form-key :showing])]
     (if (= form-status :selected)
       {:fx [[:otp/stop-all-entry-form-polling [(active-db-key db) nil]]]}
       {}))))



;; Called from backend event handler (see native_events.cljs) to update with the current tokens 
(reg-event-fx
 :entry-form/update-otp-tokens
 (fn [{:keys [db]} [_event-id entry-uuid current-opt-tokens-by-field]]
   ;; First we need to ensure that the incoming entry id is the same one showing
   (if (= entry-uuid (get-in-key-db db [entry-form-key :data :uuid]))

     ;; otp-fields is a map with otp field name as key and its token info with time ttl 
     (let [db (reduce (fn [db [otp-field-name {:keys [token ttl]}]]
                        (let [otp-field-name (name otp-field-name) ;; make sure field name is string
                              otp-field-m (get-in-key-db db [entry-form-key :otp-fields otp-field-name])
                              otp-field-m (if (nil? token)
                                            (assoc otp-field-m :ttl ttl)
                                            (assoc otp-field-m :token token :ttl ttl))]
                          (assoc-in-key-db db [entry-form-key :otp-fields otp-field-name] otp-field-m)))
                      db current-opt-tokens-by-field)]

       ;;(println "After otp-fields " (get-in-key-db db [entry-form-key :otp-fields]))

       {:db db})
     {})))


(reg-event-fx
 :entry-form-delete-otp-field
 (fn [{:keys [db]} [_event-id section otp-field-name]]
   (let [dispatch-fn (fn [api-response]
                       (when-not (on-error api-response)
                         (dispatch [:entry-form-delete-otp-field-complete section otp-field-name])))]
     ;; First we stop all otp update polling
     {:fx [[:otp/stop-all-entry-form-polling [(active-db-key db) dispatch-fn]]]})))


(defn remove-section-otp-field [otp-field-name {:keys [key] :as section-field-m}]
  (cond
    ;; current-opt-token is Option type in struct CurrentOtpTokenData and should be set to nil and not {}
    (= key "otp")
    (assoc section-field-m :value nil :current-opt-token nil)

    (= key otp-field-name)
    nil

    :else
    section-field-m))

;; Called after stopping the otp update polling
(reg-event-fx
 :entry-form-delete-otp-field-complete
 (fn [{:keys [db]} [_event-id section otp-field-name]]
   (let [section-kvs (get-in-key-db db [entry-form-key :data :section-fields section])
         section-kvs (mapv (fn [m] (remove-section-otp-field otp-field-name m)) section-kvs)
         ;; Remove nil values
         section-kvs (filterv (fn [m] m) section-kvs)
         otp-fields (-> db (get-in-key-db [entry-form-key :otp-fields])
                        (dissoc otp-field-name))
         ;; Set the db before using in fx
         db (-> db
                (assoc-in-key-db [entry-form-key :data :section-fields section] section-kvs)
                (assoc-in-key-db [entry-form-key :otp-fields] otp-fields))]
     {:db db
      ;; Calling update will reload the entry form 
      :fx [[:bg-update-entry [(active-db-key db) (get-in-key-db db [entry-form-key :data])]]]})))

;; Called when backend returns a valid otp url that is formed when user 
;; scans the QR code or enters secret code manually 
(reg-event-fx
 :entry-form/otp-url-formed
 (fn [{:keys [db]} [_event-id section otp-field-name otp-url]]
   (let [form-status (get-in-key-db db [entry-form-key :showing])
         section-kvs (merge-section-key-value db section otp-field-name otp-url)
         ;; Set the db before using in fx
         db (-> db
                (assoc-in-key-db [entry-form-key :data :section-fields section] section-kvs))]
     ;;(println "entry-form/otp-url-formed form-status is  " form-status)
     {:db db
      :fx [(if (= form-status :new)
             [:bg-insert-entry [(active-db-key db) (get-in-key-db db [entry-form-key :data])]]
             [:bg-update-entry [(active-db-key db) (get-in-key-db db [entry-form-key :data])]])]})))

;; dispatch-kw is any previously created reg-event-fx name - a keyword
(reg-event-fx
 :entry-form/otp-url-form-success
 (fn [{:keys [db]} [_event-id section-name otp-field-name otp-url standard-field dispatch-kw]]
   ;;(println "entry-form/otp-url-form-success is called for section-name otp-field-name otp-url dispatch-kw " section-name otp-field-name otp-url dispatch-kw)
   (let [;; Need to add the field to the section if it is not standard field
         ;; A map corresponding to struct KeyValueData is passed to add this extra otp field
         db (if standard-field  db (add-section-field db {:section-name section-name
                                                          :field-name otp-field-name
                                                          :protected true
                                                          :required false
                                                          :data-type ONE_TIME_PASSWORD_TYPE}))
         form-status (get-in-key-db db [entry-form-key :showing])
         section-kvs (merge-section-key-value db section-name otp-field-name otp-url)
         ;; Set the db before using in fx
         db (-> db
                (assoc-in-key-db [entry-form-key :data :section-fields section-name] section-kvs))]
     ;;(println "entry-form/otp-url-formed form-status is  " form-status)
     {:db db
      :fx [(if (= form-status :new)
             [:bg-insert-entry [(active-db-key db) (get-in-key-db db [entry-form-key :data])]]
             [:bg-update-entry [(active-db-key db) (get-in-key-db db [entry-form-key :data])]])

           (when dispatch-kw
             [:dispatch [dispatch-kw {}]]
             )]})))

;;api-response-handler is a fn that accepts a single argument
(reg-fx
 :entry-form/bg-form-otp-url
 (fn [[otp-settings api-response-handler]] 
   (bg/form-otp-url otp-settings api-response-handler)))



