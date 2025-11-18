(ns onekeepass.mobile.events.settings
  (:require [clojure.string :as str]
            [onekeepass.mobile.background :as bg]
            [onekeepass.mobile.events.common :refer [active-db-key
                                                     assoc-in-key-db
                                                     get-in-key-db on-error
                                                     on-ok]]
            [onekeepass.mobile.translation :refer [lstr-mt]]
            [onekeepass.mobile.utils  :refer [str->int]]
            [re-frame.core :refer [dispatch reg-event-db reg-event-fx reg-fx
                                   reg-sub subscribe]]))

(defn load-db-settings []
  (dispatch [:db-settings-read]))

(defn load-db-settings-with-active-db
  "Called from the home page to load the settings for the selected db
   The arg 'full-file-name-uri' is any one of the db that is already opened 
  "
  [opened-db? full-file-name-uri]
  (when opened-db?
    ;; First we need to reload the db to make sure this db is current
    ;; Then the settings are read on the assumption is that, these two
    ;; events happen in sequence
    ;; TODO: We need to ensure the db is made active first and then reading of 
    ;;  settings done. We can do by sending additional arg for event ":common/set-active-db-key"
    (dispatch [:common/set-active-db-key full-file-name-uri])
    (dispatch [:db-settings-read])))

(defn cancel-db-settings-form []
  (dispatch [:db-settings-cancel]))

(defn select-db-settings-panel [panel-id]
  (dispatch [:db-settings-panel-select panel-id]))

(defn db-settings-data-field-update
  "kw-field-name is single kw or a vec of kws that are found in :db-settings :data"
  [kw-field-name value]
  (dispatch [:db-settings-data-field-update kw-field-name value]))

(defn db-settings-field-update
  "kw-field-name is single kw or a vec of kws that are found in :db-settings"
  [kw-field-name value]
  (dispatch [:db-settings-field-update kw-field-name value]))

(defn db-settings-kdf-algorithm-select 
  "Called to update the kdf algorithm selection"
  [kdf-selection]
  (dispatch [:db-settings-kdf-algorithm-select kdf-selection]))

(defn db-settings-password-updated [pwd]
  (dispatch [:db-settings-password-updated pwd]))

(defn db-settings-password-removed []
  (dispatch [:db-settings-password-removed]))

(defn db-settings-password-added []
  (dispatch [:db-settings-password-added]))

(defn save-db-settings []
  (dispatch [:db-settings-write]))

(defn show-key-file-form []
  ;; kw :settings-key-file-selected is used in events in ns onekeepass.mobile.events.key-file-form
  ;; to send back the selected key file 
  (dispatch [:key-file-form/show :settings-key-file-selected true]))

(defn clear-key-file-field []
  (dispatch [:settings-key-file-selected {:file-name nil :full-file-name nil}]))

(defn db-settings-data []
  (subscribe [:db-settings-data]))

(defn db-settings-main
  "Returns the whole db-settings including data"
  []
  (subscribe [:db-settings-main]))

#_(defn db-settings-panel-id []
    (subscribe [:db-settings-panel-id]))

(defn master-password-visible? []
  (subscribe [:master-password-visible]))

(defn db-settings-validation-errors []
  (subscribe [:db-settings-validation-errors]))

(defn db-settings-modified []
  (subscribe [:db-settings-modified]))

(defn- validate-security-fields
  "Validates panel specific fields and returns errors map if any"
  [app-db]
  (let [{:keys [iterations memory parallelism]} (get-in-key-db app-db [:db-settings :data :kdf])
        [iterations memory parallelism] (mapv str->int [iterations memory parallelism])
        errors (if (or (nil? iterations) (< iterations 5) (> iterations 100))
                 {:iterations (lstr-mt 'dbSettings 'iterations)} {})
        errors (merge errors
                      (if (or (nil? memory) (< memory 1) (> memory 1000))
                        {:memory (lstr-mt 'dbSettings 'memory)} {}))
        errors (merge errors
                      (if (or (nil? parallelism) (< parallelism 1) (> parallelism 100))
                        {:parallelism (lstr-mt 'dbSettings 'parallelism)} {}))]

    errors))

(defn- validate-credential-fields
  "Validates password and key file fields and returns errors map"
  [app-db]
  (let [{:keys [password-used key-file-used]} (get-in-key-db app-db [:db-settings :data])
        errors (if (and (not password-used) (not key-file-used))
                 {:in-sufficient-credentials (lstr-mt 'dbSettings 'inSufficientCredentials)} {})]
    errors))

(defn- validate-required-fields
  [db panel]
  (cond
    (= panel :settings-general)
    (when (str/blank? (get-in-key-db db [:db-settings :data :meta :database-name]))
      {:database-name (lstr-mt 'dbSettings 'databaseName)})

    (= panel :settings-credentials)
    (validate-credential-fields db)

    (= panel :settings-security)
    (validate-security-fields db)))

(reg-event-fx
 :db-settings-read
 (fn [{:keys [db]} [_event-id]]
   {:fx [[:bg-get-db-settings [(active-db-key db)]]]}))

(reg-fx
 :bg-get-db-settings
 ;; fn in 'reg-fx' accepts single argument - vector arg typically 
 ;; used so that we can pass more than one input
 (fn [[db-key]]
   (bg/get-db-settings db-key (fn [api-response]
                                (when-let [settings (on-ok api-response)]
                                  (dispatch [:db-settings-read-completed settings]))))))

;; settings is a map formed from DbSettings struct
;; Note the special response transformer in background.cljs
(reg-event-fx
 :db-settings-read-completed
 (fn [{:keys [db]} [_event-id {:keys [_key-file-name] :as settings}]]
   ;; Need to convert memory value from bytes to MB. And we need to convert back bytes before saving back
   ;; See event ':bg-set-db-settings' 
   (let [data (-> settings
                  (update-in [:kdf :memory] #(Math/floor (/ % 1048576))))]
     {:db (-> db (assoc-in-key-db  [:db-settings :data] data)
              (assoc-in-key-db  [:db-settings :undo-data] data)
              (assoc-in-key-db [:db-settings :password-visible] false)
              (assoc-in-key-db [:db-settings :password-use-removed] false)
              (assoc-in-key-db [:db-settings :password-use-added] false)
              (assoc-in-key-db  [:db-settings :errors] nil)
              (assoc-in-key-db [:db-settings :status] :completed))

      :fx [[:dispatch [:common/next-page :settings "settings"]]]})))

;; Valid values for panel-id are :settings-general, :settings-credentials, :settings-encryption, :settings-kdf
(reg-event-fx
 :db-settings-panel-select
 (fn [{:keys [db]} [_event-id panel-id]]
   {:db (-> db (assoc-in-key-db [:db-settings :panel-id] panel-id))
    ;; The title SettingsPanel will be replaced in view - see app-title fn
    :fx [[:dispatch [:common/next-page panel-id "SettingsPanel"]]]}))

(reg-event-fx
 :db-settings-cancel
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db
            (assoc-in-key-db  [:db-settings :data] (get-in-key-db db [:db-settings :undo-data]))
            (assoc-in-key-db [:db-settings :errors] nil)
            (assoc-in-key-db [:db-settings :password-visible] false)
            (assoc-in-key-db [:db-settings :password-use-removed] false)
            (assoc-in-key-db [:db-settings :password-use-added] false))
    :fx [[:dispatch [:common/previous-page]]
         ;; For now whether the db name was changed or not, we call the db name update in the opened db list to undo
         [:dispatch [:common/database-name-update (get-in-key-db db [:db-settings :undo-data :meta :database-name])]]]}))

(reg-event-fx
 :db-settings-password-removed
 (fn [{:keys [db]} [_event-id]]
   (let [db (-> db  (assoc-in-key-db [:db-settings :data :password-changed] true)
                (assoc-in-key-db  [:db-settings :data :password-used] false)
                (assoc-in-key-db [:db-settings :password-use-removed] true))
         errors (validate-credential-fields db)
         db (-> db (assoc-in-key-db [:db-settings :errors] errors))]
     {:db db})))

(reg-event-fx
 :db-settings-password-added
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db
            (assoc-in-key-db [:db-settings :password-use-added] true)
            (assoc-in-key-db [:db-settings :password-use-removed] false))}))

(reg-event-fx
 :db-settings-password-updated
 (fn [{:keys [db]} [_event-id pwd]]
   (let [{:keys [password-used password-changed]} (get-in-key-db db [:db-settings :undo-data])
         ;; By using empty? fn, we can allow nnly space charaters as password
         db (if (empty? pwd)
              (-> db
                  (assoc-in-key-db [:db-settings :data :password] nil)
                  (assoc-in-key-db [:db-settings :data :password-used] password-used)
                  (assoc-in-key-db [:db-settings :data :password-changed] password-changed))
              (-> db
                  (assoc-in-key-db [:db-settings :data :password] pwd)
                  (assoc-in-key-db [:db-settings :data :password-used] true)
                  (assoc-in-key-db [:db-settings :data :password-changed] true)))
         errors (validate-credential-fields db)
         db (-> db (assoc-in-key-db [:db-settings :errors] errors))]
     {:db db})))

;; This event called (from key file related page) after user selects a key file 
;; Also used when user removes the use of key file as master key with nil values for file-name and full-file-name
(reg-event-fx
 :settings-key-file-selected
 (fn [{:keys [db]} [_event-id {:keys [file-name full-file-name]}]]
   (let [{:keys [key-file-name-part key-file-name]}  (get-in-key-db db [:db-settings :undo-data])
         changed (if (and (= key-file-name full-file-name) (= key-file-name-part file-name)) false true)
         used (if (nil? file-name) false true)

         db (-> db
                (assoc-in-key-db [:db-settings :data :key-file-used] used)
                (assoc-in-key-db [:db-settings :data :key-file-changed] changed)
                (assoc-in-key-db [:db-settings :data :key-file-name-part] file-name)
                (assoc-in-key-db [:db-settings :data :key-file-name] full-file-name))
         errors (validate-credential-fields db)
         db (-> db (assoc-in-key-db [:db-settings :errors] errors))]
     {:db db})))

;; A common event that handles most of the update of fields in the map found in ':db-settings :data'
(reg-event-fx
 :db-settings-data-field-update
 (fn [{:keys [db]} [_event-id kw-field-name value]] ;; kw-field-name is single kw or a vec of kws  
   (let [ks (into [:db-settings :data] (if (vector? kw-field-name)
                                         kw-field-name
                                         [kw-field-name]))
         db (assoc-in-key-db db ks value)
         errors (validate-required-fields db (get-in-key-db db [:db-settings :panel-id]))
         ;; Need to update the db list only if the db name is modified 
         new-db-name (when (= [:meta :database-name] kw-field-name) value)]
     {:db (-> db (assoc-in-key-db [:db-settings :errors] errors))
      :fx [(when new-db-name [:dispatch [:common/database-name-update new-db-name]])]})))

(reg-event-db
 :db-settings-kdf-algorithm-select
 (fn [db [_event-id kdf-selection]]
   ;; Fields algorithm and variant need to be set to these values so that 
   ;; kdf map is serialized to enum KdfAlgorithm::Argon2d or  KdfAlgorithm::Argon2id
   ;; Also see events in new-database.cljs
   (-> db (assoc-in-key-db [:db-settings :data :kdf :algorithm] kdf-selection)
       ;; Need to set variant int value based on the argon algorithm selected
       (assoc-in-key-db [:db-settings :data :kdf :variant] (if (= kdf-selection "Argon2d") 0 2)))))

;; Event to update any field at the top level of :db-settings other than :data field
(reg-event-db
 :db-settings-field-update
 (fn [db [_event-id kw-field-name value]] ;; kw-field-name is single kw or a vec of kws  
   (let [ks (into [:db-settings] (if (vector? kw-field-name)
                                   kw-field-name
                                   [kw-field-name]))
         db (assoc-in-key-db db ks value)]
     db)))

(reg-event-fx
 :db-settings-write
 (fn [{:keys [db]} [_event-id]]
   (let [errors (validate-required-fields db (get-in-key-db db [:db-settings :panel-id]))]
     (if-not (empty? errors)
       {:fx [[:dispatch [:common/error-box-show "Database Settings" "Errors need to be corrected before saving"]]]}
       {:fx [[:bg-set-db-settings [(active-db-key db) (get-in-key-db db [:db-settings :data])]]]}))))

(reg-fx
 :bg-set-db-settings
 (fn [[db-key settings]] ;; settings is [:db-settings :data]
   ;; Need to do some str to int and blank str handling
   (let [settings  (-> settings
                       (update-in [:kdf :iterations] str->int)
                       (update-in [:kdf :parallelism] str->int)
                       (update-in [:kdf :memory] str->int)
                       (update-in [:kdf :memory] * 1048576)
                       (update-in [:password] #(if (str/blank? %) nil %))
                       (update-in [:key-file-name] #(if (str/blank? %) nil %)))]
     (bg/set-db-settings db-key settings (fn [api-response]
                                           (when-not (on-error api-response)
                                             (dispatch [:db-settings-write-completed])))))))

(reg-event-fx
 :db-settings-write-completed
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:dispatch [:save/save-current-kdbx
                     {:error-title "Settings Save Error"
                      :save-message "Saving database settings..."
                      :on-save-ok (fn []
                                    (dispatch [:db-settings-saved])
                                    #_(dispatch [:common/previous-page])
                                    #_(dispatch [:common/message-snackbar-open "Database Settings saved"]))}]]]}))

(reg-event-fx
 :db-settings-saved
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:dispatch [:db-settings-read]] ;; Need to reload the updated db-settings before navigating to previous page
         [:dispatch [:common/previous-page]]
         [:dispatch [:common/message-snackbar-open 'databaseSettingsSaved]]]}))

(reg-sub
 :master-password-visible
 (fn [db]
   (get-in-key-db db [:db-settings :password-visible])))

(reg-sub
 :db-settings-validation-errors
 (fn [db]
   (get-in-key-db db [:db-settings :errors])))

(reg-sub
 :db-settings-panel-id
 (fn [db] (get-in-key-db db [:db-settings :panel-id])))

(reg-sub
 :db-settings-data
 (fn [db]
   (get-in-key-db db [:db-settings :data])))

(reg-sub
 :db-settings-main
 (fn [db]
   (get-in-key-db db [:db-settings])))

;;Determines whether any data is changed
(reg-sub
 :db-settings-modified
 (fn [db _query-vec]
   (let [undo-data (get-in-key-db db [:db-settings :undo-data])
         data (get-in-key-db db [:db-settings :data])
         errors (get-in-key-db db [:db-settings :errors])]
     (if (and (seq undo-data) (not= undo-data data) (empty? errors))
       true
       false))))


(comment
  (in-ns 'onekeepass.mobile.events.settings)

  (def db-key (-> @re-frame.db/app-db :current-db-file-name))
  (-> @re-frame.db/app-db (get db-key) keys)

  (-> @re-frame.db/app-db (get db-key) :db-settings keys))