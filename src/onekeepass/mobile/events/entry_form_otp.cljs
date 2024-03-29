(ns onekeepass.mobile.events.entry-form-otp
  (:require
   [re-frame.core :refer [reg-event-db
                          reg-event-fx
                          reg-sub
                          dispatch
                          reg-fx
                          subscribe]]
   [onekeepass.mobile.events.entry-form-common :refer [merge-section-key-value extract-form-otp-fields entry-form-key]]
   [onekeepass.mobile.constants :refer [ONE_TIME_PASSWORD_TYPE]]

   [onekeepass.mobile.events.common :as cmn-events :refer [on-ok
                                                           on-error
                                                           active-db-key
                                                           assoc-in-key-db
                                                           get-in-key-db]]
   [onekeepass.mobile.utils :as u :refer [contains-val?]]

   [onekeepass.mobile.background :as bg]))


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
 :entry-form/otp-start-polling
 (fn [{:keys [db]} [_event-id]]
   {}
   #_(let [form-status (get-in-key-db db [entry-form-key :showing])
           entry-uuid (get-in-key-db db [entry-form-key :data :uuid])
           otp-fields (extract-form-otp-fields (get-in-key-db db [entry-form-key :data]))]
       (if (= form-status :selected)
         {:fx [[:otp/start-polling-otp-fields [(active-db-key db)
                                               entry-uuid
                                               otp-fields]]]}
         {}))))


(reg-event-fx
 :entry-form/otp-stop-polling
 (fn [{:keys [db]} [_event-id]]
   {}
   #_(let [form-status (get-in-key-db db [entry-form-key :showing])]
       (if (= form-status :selected)
         {:fx [[:otp/stop-all-entry-form-polling [(active-db-key db) nil]]]}
         {}))))

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



;; Called to update with the current tokens 
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
