(ns onekeepass.mobile.events.new-database
  (:require [clojure.string :as str]
            [onekeepass.mobile.background :as bg :refer [is-Android]]
            [onekeepass.mobile.events.common :refer [on-ok]]
            [onekeepass.mobile.translation :refer [lstr-mt]]
            [onekeepass.mobile.utils :as u :refer [str->int]]
            [re-frame.core :refer [dispatch reg-event-db reg-event-fx reg-fx
                                   reg-sub subscribe]]))


(defn new-database-dialog-show []
  (dispatch [:new-database-dialog-show]))

(defn cancel-on-click []
  (dispatch [:new-database-dialog-hide]))

;; Need to use some iOS specific call sequences to make sure kdbx file is created
;; Need to verify this - In case of OneDrive, GDrive there may see 'Coordinator error'. However the db file gets created and 
;; user can open back
(defn done-on-click []
  (if (is-Android)
    (dispatch [:new-database-create])
    (dispatch [:new-database-create-ios])))

(defn database-field-update [kw-field-name value]
  (dispatch [:new-database-field-update kw-field-name value]))

(defn show-key-file-form [show-generate-option?]
  ;; kw :new-database-key-file-selected is used in events in ns onekeepass.mobile.events.key-file-form
  ;; to send back the selected key file 
  (dispatch [:key-file-form/show :new-database-key-file-selected show-generate-option?]))

(defn new-database-validate-before-create-action 
  "This is called before showing the select storage options to ensure all required fields are entered
   If validation is successful, then the callback-fn is called 
   "
  [callback-fn]
  (dispatch [:new-database-validate-before-create-action callback-fn]))

(defn dialog-data []
  (subscribe [:new-database-dialog-data]))

(def newdb-fields [:database-name
                   :database-description
                   :password
                   :database-file-name
                   :file-name
                   :cipher-id
                   :kdf
                   :key-file-name])

(def blank-new-db  {;;All fields matching 'NewDatabase' struct
                    :database-name nil
                    :database-description nil
                    :password nil
                    :database-file-name nil
                    :cipher-id "Aes256"
                    :kdf {:Argon2  {:iterations 10 :memory 64 :parallelism 2}}
                    :key-file-name nil
                    :file-name nil

                    ;; Extra UI related fields
                    ;; These fields will be ignored by serde while doing json deserializing to NewDatabase struct
                    :key-file-name-part nil
                    :dialog-show false
                    :password-visible false
                    :password-confirm nil
                    :api-error-text nil
                    :db-file-file-exists false
                    :error-fields {} ;; a map e.g {:id1 "some error text" :id2 "some error text" }
                    })

(defn- init-new-database-data [app-db]
  (assoc app-db :new-database blank-new-db))

(defn- validate-required-fields
  [db kw-field]
  (let [{:keys [database-name password key-file-name]} (get-in db [:new-database])
        m1 (lstr-mt 'newDbForm 'validPasswordOrKeyFileRequired)
        m2 (lstr-mt 'newDbForm 'validPasswordRequired)

        error-fields (cond
                       (and (= kw-field :password) (empty? password) (empty? key-file-name))
                       (assoc {} :password m1)

                       (and (= kw-field :database-name) (str/blank? database-name))
                       (assoc {} :database-name m2)

                       (= kw-field :all)
                       (cond-> {}
                         (str/blank? database-name)
                         (assoc :database-name m2)

                         (and (empty? password) (empty? key-file-name))
                         (assoc :password m1))

                       :else
                       {})]
    error-fields))

(reg-event-fx
 :new-database-validate-before-create-action
 (fn [{:keys [db]} [_event-id callback-fn]]
   (let [error-fields (validate-required-fields db :all)
         errors-found (boolean (seq error-fields))]
     (if errors-found
       {:db (assoc-in db [:new-database :error-fields] error-fields)}
       (do
         ;; Side effect call
         (callback-fn)
         {})))))

(reg-event-fx
 :new-database-dialog-show
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db init-new-database-data (assoc-in [:new-database :dialog-show] true))}))

(reg-event-db
 :new-database-dialog-hide
 (fn [db [_event-id]]
   (assoc-in  db [:new-database :dialog-show] false)))

;; An event to be called (from key file related page) after user selects a key file 
(reg-event-fx
 :new-database-key-file-selected
 (fn [{:keys [db]} [_event-id {:keys [file-name full-file-name] :as m}]]
   (let [db (-> db (assoc-in [:new-database :key-file-name-part] file-name)
                (assoc-in [:new-database :key-file-name] full-file-name))
         error-fields (validate-required-fields db :password)
         db (assoc-in db [:new-database :error-fields] error-fields)]
     {:db db})))

(reg-event-db
 :new-database-field-update
 ;; kw-field-name is single kw or a vec of kws
 (fn [db [_event-id kw-field-name value]]
   (let [db (-> db
                (assoc-in (into [:new-database]
                                (if (vector? kw-field-name)
                                  kw-field-name
                                  [kw-field-name])) value)
                        ;; Hide any previous api-error-text
                (assoc-in [:new-database :api-error-text] nil))
         error-fields (validate-required-fields db kw-field-name)
         db (-> db (assoc-in [:new-database :error-fields] error-fields))]
     db)))

;;;;;;;;;;;;;;;;;;;;;  Used only for Android ;;;;;;;;;;;;;;;;
;; In iOS we need to have separate set of steps this to work 
;; See comments in pick-document-to-create and pick-and-save-new-kdbxFile

;; Android :new-database-create -> ... :bg-create-kdbx
;; iOS     :new-database-create-ios -> ... :bg-load-new-kdbx

(reg-event-fx
 :new-database-create
 (fn [{:keys [db]} [_event-id]]
   (let [error-fields (validate-required-fields db :all)
         errors-found (boolean (seq error-fields))]
     (if errors-found
       {:db (assoc-in db [:new-database :error-fields] error-fields)}
       {:db (-> db (assoc-in [:new-database :status] :in-progress))
        :fx [[:bg-pick-document-to-create (str (get-in db [:new-database :database-name]) ".kdbx")]]}))))

(reg-fx
 :bg-pick-document-to-create
 (fn [kdbx-file-name]
   (bg/pick-document-to-create kdbx-file-name (fn [api-response]
                                                ;;(println "pick-document-to-create api-response.. " api-response)
                                                (when-let [picked (on-ok
                                                                   api-response
                                                                   #(dispatch [:new-database-dialog-hide]))]
                                                  ;; picked is a map with keys :file-name :full-file-name-uri
                                                  ;; see the use of DbServiceAPI.formJsonWithFileName
                                                  (dispatch [:new-database/document-to-create-picked picked]))))))

;; Used for android and for creating a new db in a remote storage location 
;; See :document-to-create-picked-ios for iOS
(reg-event-fx
 :new-database/document-to-create-picked
 (fn [{:keys [db]} [_event-id {:keys [file-name full-file-name-uri]}]]
   (let [db (-> db (assoc-in [:new-database :database-file-name] full-file-name-uri)
                (assoc-in [:new-database :file-name] file-name))]
     {:db db
      :fx [[:dispatch [:new-database-field-update :status :in-progress]]
           [:bg-create-kdbx [full-file-name-uri  (:new-database db)]]]})))

;; Used for ios and android 
(reg-event-fx
 :new-database-created
 (fn [{:keys [db]} [_event-id kdbx-loaded]]
   {:db (-> db (assoc-in [:new-database :api-error-text] nil)
            (assoc-in [:new-database :status] :completed))
    :fx [[:dispatch [:new-database-dialog-hide]]
         [:dispatch [:common/kdbx-database-opened kdbx-loaded]]]}))


(reg-event-fx
 :new-database-create-kdbx-error
 (fn [{:keys [db]} [_event-id error]]
   {:db (-> db (assoc-in [:new-database :status] :completed)
            (assoc-in  [:new-database :api-error-text] error))
    :fx (if (= "DOCUMENT_PICKER_CANCELED" (:code error))
          [[:dispatch [:new-database-dialog-hide]]]
          [[:dispatch [:new-database-dialog-hide]]
           [:dispatch [:common/error-box-show 'filePickError error]]])}))

(defn- on-database-creation-completed
  [api-response]
  (when-let [kdbx-loaded (on-ok
                          api-response
                          (fn [error]
                            (dispatch [:new-database-create-kdbx-error error])))]
    (dispatch [:new-database-created kdbx-loaded])))

;; Used for android and for creating a new db in a remote storage location 
;; See :document-to-create-picked-ios for iOS
(reg-fx
 :bg-create-kdbx
 (fn [[full-file-name new-db]]
   (bg/create-kdbx full-file-name (-> new-db
                                      (update-in [:kdf :Argon2 :iterations] str->int)
                                      (update-in [:kdf :Argon2 :parallelism] str->int)
                                      (update-in [:kdf :Argon2 :memory] str->int)
                                      ;; Need to make sure memory value is in MB 
                                      (update-in [:kdf :Argon2 :memory] * 1048576)
                                      (select-keys newdb-fields)) on-database-creation-completed)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; iOS specific ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; 

;; We need to use iOS specific calls - create a temp db file, copy that file using user selection
;; and then read that newly created file to load the db

;; Because this call intiates iOS UIDocumentPickerViewController in 'bg-pick-and-save-new-kdbxFile'
;; though the status is changed to :in-progress, the UI does not reflect. It changes only after user
;; completes the action from DocumentPickerView 
;; One solution may be instead of creating the temp new database file in 'pick-and-save-new-kdbxFile' 
;; we can create the temp file in a separate api call returning the temp url and then call
;; 'pick-and-save-new-kdbxFile' with that call
(reg-event-fx
 :new-database-create-ios
 (fn [{:keys [db]} [_event-id]]
   (let [error-fields (validate-required-fields db :all)
         errors-found (boolean (seq error-fields))
         db (assoc-in db [:new-database :database-file-name] "TEMP")]
     (if errors-found
       {:db (assoc-in db [:new-database :error-fields] error-fields)}
       {:db (-> db (assoc-in [:new-database :status] :in-progress)
                (assoc-in [:new-database :error-fields] nil))
        :fx [[:bg-pick-and-save-new-kdbxFile [(str (get-in db [:new-database :database-name]) ".kdbx")
                                              (:new-database db)]]]}))))

(defn- on-database-new-database-save-completed
  [api-response]
  (when-let [result (on-ok
                     api-response
                     (fn [error]
                       (dispatch [:new-database-create-kdbx-error error])))]
    (dispatch [:common/message-modal-hide])
    (dispatch [:document-to-create-picked-ios result])))

(reg-fx
 :bg-pick-and-save-new-kdbxFile
 (fn [[file-name new-db]]
   #_(let [sf (select-keys new-db newdb-fields)]
       (println "(select-keys newdb-fields):  " sf)
       (println "Password type and count is " (type (:password sf)) ", " (count (:password sf))))
   (bg/pick-and-save-new-kdbxFile file-name
                                  (-> new-db
                                      (update-in [:kdf :Argon2 :iterations] str->int)
                                      (update-in [:kdf :Argon2 :parallelism] str->int)
                                      (update-in [:kdf :Argon2 :memory] str->int)
                                      ;; Need to make sure memory value is in MB 
                                      (update-in [:kdf :Argon2 :memory] * 1048576)
                                      (select-keys newdb-fields)) on-database-new-database-save-completed)))

(reg-event-fx
 :document-to-create-picked-ios
 (fn [{:keys [db]} [_event-id {:keys [_file-name full-file-name-uri]}]]
   (let [db (-> db (assoc-in [:new-database :database-file-name] full-file-name-uri))]
     {:db db
      :fx [[:dispatch [:new-database-field-update :status :in-progress]]
           [:bg-load-new-kdbx [full-file-name-uri ;; will be the db-key
                               (get-in db [:new-database :password])
                               (get-in db [:new-database :key-file-name])]]]})))

(reg-fx
 :bg-load-new-kdbx
 (fn [[db-file-name password key-file-name]]
   (bg/load-kdbx db-file-name password key-file-name false on-database-creation-completed)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(reg-sub
 :new-database-dialog-data
 (fn [db _query-vec]
   (get-in db [:new-database])))

(comment
  (in-ns 'onekeepass.mobile.events.new-database))