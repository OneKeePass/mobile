(ns onekeepass.mobile.events.entry-form-auto-open
  "Auto opening of a child databases related events"
  (:require-macros [onekeepass.mobile.okp-macros
                    :refer  [as-map]])
  (:require
   [clojure.string :as str]
   [onekeepass.mobile.events.common :refer [on-ok]]
   [onekeepass.mobile.events.entry-form-common :refer [get-form-data
                                                       place-holder-resolved-value
                                                       form-data-kv-data
                                                       extract-form-field-names-values]]
   [re-frame.core :refer [reg-event-fx
                          dispatch
                          reg-fx]]
   [onekeepass.mobile.background :as bg]
   [onekeepass.mobile.constants :as const :refer [URL
                                                  USERNAME
                                                  PASSWORD
                                                  UUID_OF_ENTRY_TYPE_AUTO_OPEN
                                                  IFDEVICE]]))


(defn show-key-file-form []
  (dispatch [:key-file-form/show nil nil]))

(defn entry-form-auto-open-show-select-storage
  "Called from the dialog 'auto-open-db-file-required-info-dialog' 
   The arg 'auto-open-props' is map see comments in the event ':entry-form-auto-open-db-key-not-found'
   "
  [auto-open-props]
  (dispatch [:open-database/auto-open-show-select-storage auto-open-props]))

(defn entry-form-open-url [url-field-value]
  (dispatch [:entry-form-open-url url-field-value]))

;;;;;;;;;  To be removed ;;;;;;;;;;;;;;
#_(defn is-kdbx-url [url-field-value]
    (if (nil? url-field-value) false
        (str/starts-with? url-field-value "kdbx:")))

#_(defn entry-form-open-kdbx-url [url-field-value]
    (dispatch [:entry-form-open-kdbx-url url-field-value]))

;; Called when user presses the url field 
#_(reg-event-fx
   :entry-form-open-kdbx-url
   (fn [{:keys [_db]} [_event-id url-value]]
     (if (str/starts-with? url-value "kdbx:")
       {:fx [[:dispatch [:entry-form-auto-open-resolve-properties]]]}
       {})))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(reg-event-fx
 :entry-form-open-url
 (fn [{:keys [_db]} [_event-id url-value]]
   (let [l-url-value (str/lower-case url-value)]
     (cond

       (str/starts-with? l-url-value "kdbx:")
       {:fx [[:dispatch [:entry-form-auto-open-resolve-properties]]]}

       (or (str/starts-with? l-url-value "https://") (str/starts-with? url-value "http://"))
       {:fx [[:common/bg-open-https-url [url-value]]]}

       (str/starts-with? l-url-value "file://")
       {}

       ;; Otherwise we assume it is a web url without the protocol prefix. Just add the prefix and open
       :else
       {:fx [[:common/bg-open-https-url [(str "https://" l-url-value)]]]}))))

(defn auto-open-properties-dispatch-fn
  "The arg 'auto-open-props' is passed to the dispatch response handler fn"
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
     (if (= entry-type-uuid UUID_OF_ENTRY_TYPE_AUTO_OPEN)
       (let [field-m (extract-form-field-names-values form-data)
             parsed-fields (:parsed-fields form-data)
             url (place-holder-resolved-value parsed-fields URL (get field-m URL))
             key-file-path (place-holder-resolved-value parsed-fields USERNAME (get field-m USERNAME))
             ;; auto-open-props is created from the values of form data fields url, username and password
             auto-open-props {:url-field-value url
                              :key-file-path key-file-path
                              ;; This is not yet used in mobile apps
                              :device_if_val (get field-m IFDEVICE)}]
         {:fx [[:bg-resolve-auto-open-properties [auto-open-props (partial auto-open-properties-dispatch-fn auto-open-props)]]]})
       {}))))

;; dispatch-fn is a fn that accepts the api-response
(reg-fx
 :bg-resolve-auto-open-properties
 (fn [[auto-props dispatch-fn]]
   (bg/resolve-auto-open-properties auto-props dispatch-fn)))

(defn- include-password [db {:keys [db-key db-file-name key-file-full-path key-file-name]}]
  (let [form-data (get-form-data db)
        parsed-fields (:parsed-fields form-data)
        {:keys [value]} (form-data-kv-data form-data PASSWORD)
        password (place-holder-resolved-value parsed-fields PASSWORD value)
        ;; password is added 
        args (as-map [db-key db-file-name password key-file-full-path key-file-name])]
    args))

;; auto-open-props is a map from struct AutoOpenProperties
;; auto-open-props-resolved is map from struct 'AutoOpenPropertiesResolved'
(reg-event-fx
 :entry-form-auto-open-properties-resolved
 (fn [{:keys [_db]} [_event-id
                     {:keys [key-file-path] :as _auto-open-props}
                     {:keys [db-key _db-file-name key-file-full-path key-file-name] :as auto-open-props-resolved}]]
   (cond

     ;; key-file-path (from form data's UserName field) is a non nil value 
     ;; But the 'key-file-full-path' is not available as user has not yet uploaded the key file yet 
     (and (not (str/blank? key-file-path)) (str/blank? key-file-full-path))
     {:fx [[:dispatch [:entry-form-auto-open-pick-key-file key-file-name]]]}

     (str/blank? db-key)
     {:fx [[:dispatch [:entry-form-auto-open-db-key-not-found auto-open-props-resolved]]]}

     ;; Open the child database
     :else
     {:fx [[:dispatch [:entry-form-auto-open-load-child-database auto-open-props-resolved]]]})))

;; Called so that user can pick a database (through Select storage dialog) to use as child database to open
(reg-event-fx
 :entry-form-auto-open-db-key-not-found
 (fn [{:keys [db]} [_event_id auto-open-props-resolved]]
   #_(println "entry-form-auto-open-db-key-not-found is called")
   ;; This will open the dialog 'auto-open-db-file-required-info-dialog' in 'entry_form_dialog.cljs'
   {:fx [[:dispatch [:generic-dialog-update-with-map
                     :auto-open-db-file-required-info-dialog
                     {:dialog-show true
                      ;; auto-open-props is a map from auto-open-props-resolved and we include the  password
                      ;; before passing to the dialog by using fn 'include-password'
                      ;; Note: 'db-key' field in auto-open-props is nil
                      ;; This is passed and used in event ':open-database/database-file-picked' handler 
                      ;; when user pick a file
                      :auto-open-props (include-password db auto-open-props-resolved)}]]]}))

(reg-event-fx
 :entry-form-auto-open-pick-key-file
 (fn [{:keys [_db]} [_event_id key-file-name]]
   #_(println "entry-form-auto-open-pick-key-file is called")
   ;; This will open the dialog 'auto-open-key-file-pick-required-info-dialog' in 'entry_form_dialog.cljs'
   {:fx [;; The event :generic-dialog-show-with-state of dialog with key  :auto-open-key-file-pick-required-info-dialog
         [:dispatch [:generic-dialog-show-with-state
                     :auto-open-key-file-pick-required-info-dialog
                     {:key-file-name key-file-name}]]]}))

(reg-event-fx
 :entry-form-auto-open-load-child-database
 (fn [{:keys [db]} [_event_id {:keys [] :as auto-open-props-resolved}]]
   (let [args (include-password db auto-open-props-resolved)]
     ;; The arg 'args' is a map from auto-open-props-resolved with password
     ;; The keys in this 'args' map are [db-key db-file-name password key-file-full-path key-file-name]
     ;; This ensures that we can launch "Open database" dialog and begins loading the db 
     ;; in the event ':open-database/database-file-picked-in-auto-open'
     {:fx [[:dispatch [:common/to-home-page]]
           [:dispatch [:open-database/database-file-picked-in-auto-open args]]]})))

(reg-event-fx
 :entry-form-auto-open-properties-resolve-error
 (fn [{:keys [_db]} [_event-id error]]
   {:fx [:dispatch [:common/error-box-show "Auto Open Error" error]]}))


;; Called from the auto-open-db-file-required-info-dialog
#_(reg-event-fx
   :entry-form-auto-open-show-select-storage
   (fn [{:keys [_db]} [_event_id auto-open-props]]
     (println "auto-open-props-resolved received is " auto-open-props)
     {:fx [[:dispatch [:open-database/auto-open-show-select-storage auto-open-props]]]}
     #_{:fx [[:dispatch [:common/to-home-page]]
             [:dispatch [:generic-dialog-show-with-state :start-page-storage-selection-dialog {:kw-browse-type const/BROWSE-TYPE-DB-OPEN}]]]}))

;;;;;;;;;;;;;;;;;;;;;; dialog ;;;;;;;;;;;

;; (defn entry-form-auto-open-key-file-required-dialog-close []
;;   (dispatch [:entry-form-auto-open-key-file-required-dialog-close]))

;; (defn entry-form-auto-open-key-file-required-dialog-ok []
;;   (dispatch [:entry-form-auto-open-key-file-required-dialog-ok]))

;; (defn entry-form-auto-open-key-file-required-dialog-data []
;;   (subscribe [:entry-form-auto-open-key-file-required-dialog-data]))

;; (reg-event-fx
;;  :entry-form-auto-open-key-file-required-dialog-init
;;  (fn [{:keys [db]} [_event-id key-file-name]]
;;    {:db (-> db (assoc-in-key-db [:auto-open-key-file-required-dialog]
;;                                 {:dialog-show true
;;                                  :key-file-name key-file-name}))}))

;; (reg-event-fx
;;  :entry-form-auto-open-key-file-required-dialog-close
;;  (fn [{:keys [db]} [_event-id]]
;;    {:db (-> db (assoc-in-key-db [:auto-open-key-file-required-dialog :dialog-show] false))}))

;; (reg-event-fx
;;  :entry-form-auto-open-key-file-required-dialog-ok
;;  (fn [{:keys [db]} [_event-id]]
;;    {:fx [[:dispatch [:key-file-form/show nil nil]]
;;          [:dispatch [:entry-form-auto-open-key-file-required-dialog-close]]]}))

;; (reg-sub
;;  :entry-form-auto-open-key-file-required-dialog-data
;;  (fn [db [_query-id]]
;;    (get-in-key-db db [:auto-open-key-file-required-dialog])))


;;  db file 
;; (reg-event-fx
;;  :entry-form-auto-open-db-file-required-dialog-init
;;  (fn [{:keys [db]} [_event-id db-file-name]]
;;    {:db (-> db (assoc-in-key-db [:auto-open-db-file-required-dialog]
;;                                 {:dialog-show true
;;                                  :db-file-name db-file-name}))}))

;; (reg-event-fx
;;  :entry-form-auto-open-db-file-required-dialog-close
;;  (fn [{:keys [db]} [_event-id]]
;;    {:db (-> db (assoc-in-key-db [:auto-open-db-file-required-dialog :dialog-show] false))}))

;; (reg-event-fx
;;  :entry-form-auto-open-db-file-required-dialog-ok
;;  (fn [{:keys [db]} [_event-id]]
;;    {:fx [#_[:dispatch [:open-database/database-file-picked-in-auto-open]]
;;          [:dispatch [:entry-form-auto-open-key-file-required-dialog-close]]]}))

;; (reg-sub
;;  :entry-form-auto-open-db-file-required-dialog-data
;;  (fn [db [_query-id]]
;;    (get-in-key-db db [:auto-open-db-file-required-dialog])))



(comment
  (in-ns 'onekeepass.mobile.events.entry-form-auto-open))

;; (println "In resolved auto-open-props " auto-open-props)
;; (println "In resolved auto-open-props-resolved " auto-open-props-resolved)
;; (println "first second third "  (str/blank? db-key) (and (not (str/blank? key-file-path)) (str/blank? key-file-full-path)) (not (nil? db-key)))


#_(reg-event-fx
   :entry-form-auto-open-load-child-database
   (fn [{:keys [db]} [_event_id {:keys [] :as auto-open-props-resolved}]]
     (let [args (include-password db auto-open-props-resolved)
           ;;  form-data (get-form-data db)
           ;;  parsed-fields (:parsed-fields form-data)
           ;;  {:keys [value]} (form-data-kv-data form-data PASSWORD)
           ;;  password (place-holder-resolved-value parsed-fields PASSWORD value)
           ;;  args (as-map [db-key db-file-name password key-file-full-path key-file-name]) 
           ]
       {:fx [[:dispatch [:common/to-home-page]]
             [:dispatch [:open-database/database-file-picked-in-auto-open args]]]})))

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
