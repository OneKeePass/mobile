(ns onekeepass.mobile.events.settings
  (:require
   [re-frame.core :refer [reg-event-db reg-event-fx reg-fx reg-sub dispatch subscribe]]
   [onekeepass.mobile.events.common :refer [on-error
                                            on-ok
                                            assoc-in-key-db
                                            get-in-key-db
                                            active-db-key]]
   [clojure.string :as str]
   [onekeepass.mobile.utils  :refer [str->int]]
   [onekeepass.mobile.background :as bg]))

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

(defn save-db-settings []
  (dispatch [:db-settings-write]))

(defn db-settings-data []
  (subscribe [:db-settings-data]))

#_(defn db-settings-panel-id []
  (subscribe [:db-settings-panel-id]))

(defn master-password-visible? []
  (subscribe [:master-password-visible]))

(defn db-settings-validation-errors []
  (subscribe [:db-settings-validation-errors]))

(defn db-settings-modified []
  (subscribe [:db-settings-modified]))

(defn- validate-security-fields
  [app-db]
  (let [{:keys [iterations memory parallelism]} (get-in-key-db app-db [:db-settings :data :kdf :Argon2])
        [iterations memory parallelism] (mapv str->int [iterations memory parallelism])
        errors (if (or (nil? iterations) (or (< iterations 5) (> iterations 100)))
                 {:iterations "Valid values should be in the range 5 - 100"} {})
        errors (merge errors
                      (if (or (nil? memory) (or (< memory 1) (> memory 1000)))
                        {:memory "Valid values should be in the range 1 - 1000"} {}))
        errors (merge errors
                      (if (or (nil? parallelism) (or (< parallelism 1) (> parallelism 100)))
                        {:parallelism "Valid values should be in the range 1 - 100"} {}))]

    errors))

(defn- validate-required-fields
  [db panel] 
  (cond
    (= panel :settings-general)
    (when (str/blank? (get-in-key-db db [:db-settings :data :meta :database-name]))
      {:database-name "A valid database name is required"})

    ;; (= panel :settings-credentials)
    ;; (let [p (get-in-key-db db [:db-settings :data :password])
    ;;       cp (get-in-key-db db [:db-settings :password-confirm])
    ;;       visible  (get-in-key-db db [:db-settings :password-visible])]
    ;;   ;;(println "p cp visible " p cp visible)
    ;;   ;;(println "(not (str/blank? p))" (not (str/blank? p)) " (not visible)"  (not visible) " (not= p cp)" (not= p cp))
    ;;   (cond
    ;;     (and (not (str/blank? p)) (not visible) (not= p cp))
    ;;     {:password-confirm "Password and Confirm password are not matching"}

    ;;     (and (str/blank? p) (not (str/blank? cp)) (not visible))
    ;;     {:password-confirm "Password and Confirm password are not matching"}))

    (= panel :settings-security)
    (validate-security-fields db)

    ;;(= panel :file-info)
    ))

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
                  (update-in [:kdf :Argon2 :memory] #(Math/floor (/ % 1048576))))] 
     {:db (-> db (assoc-in-key-db  [:db-settings :data] data)
              (assoc-in-key-db  [:db-settings :undo-data] data)
              (assoc-in-key-db [:db-settings :password-visible] false)
              (assoc-in-key-db  [:db-settings :errors] nil)
              (assoc-in-key-db [:db-settings :status] :completed))

      :fx [[:dispatch [:common/next-page :settings "page.titles.settings"]]]})))

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
            (assoc-in-key-db [:db-settings :errors] nil))
    :fx [[:dispatch [:common/previous-page]]
         ;; For now whether the db name was changed or not, we call the db name update in the opened db list to undo
         [:dispatch [:common/database-name-update (get-in-key-db db [:db-settings :undo-data :meta :database-name])]]]}))

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
                       (update-in [:kdf :Argon2 :iterations] str->int)
                       (update-in [:kdf :Argon2 :parallelism] str->int)
                       (update-in [:kdf :Argon2 :memory] str->int)
                       (update-in [:kdf :Argon2 :memory] * 1048576)
                       (update-in [:password] #(if (str/blank? %) nil %))
                       (update-in [:key-file-name] #(if (str/blank? %) nil %)))]
     (bg/set-db-settings db-key settings (fn [api-response] 
                                           (when-not (on-error api-response)
                                             (dispatch [:db-settings-write-completed])))))))
(reg-event-fx
 :db-settings-write-completed
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:dispatch [:common/save-current-kdbx 
                     {:error-title "Settings Save Error"
                      :save-message "Saving database settings..."
                      :on-save-ok (fn []
                                    (dispatch [:common/previous-page])
                                    (dispatch [:common/message-snackbar-open "Database Settings saved"]))}]]]}))

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
  (-> @re-frame.db/app-db (get db-key) keys))