(ns onekeepass.mobile.events.open-database
  (:require
   [clojure.string :as str]
   [onekeepass.mobile.background :as bg]
   [onekeepass.mobile.constants :as const]
   [onekeepass.mobile.events.common :refer [biometric-enabled-to-open-db
                                            biometric-enabled-to-unlock-db 
                                            on-ok
                                            opened-db-keys
                                            is-db-locked]]
   [onekeepass.mobile.utils :as u]
   [re-frame.core :refer [dispatch reg-event-db reg-event-fx reg-fx reg-sub
                          subscribe]]))

(defn cancel-on-press
  "Called to close the dialog and resets all fields in ':open-database' "
  []
  (dispatch [:open-database-dialog-close]))

(defn open-database-on-press
  "Called to launch the platform specific file picker"
  []
  (dispatch [:pick-database-file]))

(defn set-opened-database-active 
  "Called when user selects a db name on the start page database list which is already opened and not locked
   It is assumed the db is opened but not locked
   "
  [full-file-name-uri]
  (dispatch [:common/set-active-db-key full-file-name-uri]))

(defn open-selected-database 
  "Called when user selects a db name on the start page database list"
  [file-name full-file-name-uri]
  (dispatch [:open-database/database-file-picked-1 {:file-name file-name :full-file-name-uri full-file-name-uri}]))

#_(defn open-selected-database
  "Called when user picks a db on the start page database list"
  [file-name full-file-name-uri already-opened?]
  (if already-opened?
    (dispatch [:common/set-active-db-key full-file-name-uri])
    (dispatch [:open-database/database-file-picked-1 {:file-name file-name :full-file-name-uri full-file-name-uri}])))

(defn database-field-update [kw-field-name value]
  (dispatch [:open-database-field-update kw-field-name value]))

(defn repick-confirm-close
  "User confirmed to repick the database file again. 
   Should this be called continue as use button label continue ?"
  []
  (dispatch [:repick-confirm-close]))

(defn repick-confirm-cancel []
  (dispatch [:repick-confirm-cancel]))

(defn repick-confirm-data []
  (subscribe [:repick-confirm-data]))

(defn open-database-read-db-file
  "Called when user clicks the continue button. By this time user would have picked a file to open in the 
  previous pick file call and provided credentials that are stored in a map in :open-database 
  "
  []
  (dispatch [:open-database-read-db-file]))

(defn show-key-file-form []
  ;; kw :open-database-key-file-selected is used in events in ns onekeepass.mobile.events.key-file-form
  ;; to send back the selected key file 
  (dispatch [:key-file-form/show :open-database-key-file-selected]))

(defn dialog-data []
  (subscribe [:open-database-dialog-data]))

(def blank-open-db  {:dialog-show false
                     :password-visible false
                     :error-fields {}
                     ;; database-file-name is just the 'file name' part derived from full 
                     ;; uri 'database-full-file-name' to show in the dialog
                     :database-file-name nil
                     :key-file-name-part nil

                     :status nil
                     ;; For read/load kdbx args
                     :database-full-file-name nil
                     :password nil
                     ;; This is the full key file path. See event :open-database-key-file-selected where it is set
                     :key-file-name nil
                     ;; This is set and used when user tries to open a child database and the db is not found in the 
                     ;; recently used list
                     :auto-open-props {}})

(defn- init-open-database-data
  "Initializes all open db related values in the incoming main app db 
  and returns the updated main app db
  "
  [db]
  (assoc-in db [:open-database] blank-open-db))

(defn- init-show-open-database-dialog
  "Initializes open database dialog data with given db information and also makes
   the dialog-show field to true. 
   Returns the updated 'app-db'  
   "
  [db  {:keys [file-name full-file-name-uri]}]
  (-> db init-open-database-data
      ;; database-file-name is just the 'file name' part derived from full uri 'database-full-file-name'
      ;; to show in the dialog
      (assoc-in [:open-database :database-file-name] file-name)
      (assoc-in [:open-database :database-full-file-name] full-file-name-uri)
      (assoc-in [:open-database :dialog-show] true)))

(defn- validate-required-fields
  [db]
  (let [error-fields {} #_(cond-> {}
                            (str/blank? (get-in db [:open-database :password]))
                            (assoc :password "A valid password is required"))]
    error-fields))

;; Shows the open db login dialog
(reg-event-db
 :open-database-dialog-show
 (fn [db [_event-id kdbx-file-info-m]]
   (init-show-open-database-dialog db kdbx-file-info-m)))

;; TODO: 
;; iOS specific
;; Need to delete bookmark file created while loading a kdbx file and then user 
;; cancels the login. We should delete only the bookmark file if it is not for
;; any existing db-key
(reg-event-db
 :open-database-dialog-hide
 (fn [db [_event-id]]
   ;; only :dialog-show is set to false leaving all fields with previous values
   ;; These prvious values are used when user repicks the database
   (assoc-in  db [:open-database :dialog-show] false)))

(reg-event-db
 :open-database-dialog-close
 (fn [db [_event-id]]
   (-> db init-open-database-data)
   #_(assoc-in  db [:open-database :dialog-show] false)))

;; An event to be called (from key file related page) after user selects a key file 
(reg-event-fx
 :open-database-key-file-selected
 (fn [{:keys [db]} [_event-id {:keys [file-name full-file-name] :as m}]]
   {:db (-> db (assoc-in [:open-database :key-file-name-part] file-name)
            (assoc-in [:open-database :key-file-name] full-file-name))}))

(reg-event-db
 :open-database-field-update
 (fn [db [_event-id kw-field-name value]] ;; kw-field-name is single kw or a vec of kws
   (-> db
       (assoc-in (into [:open-database]
                       (if (vector? kw-field-name)
                         kw-field-name
                         [kw-field-name])) value)
       ;; Hide any previous api-error-text
       #_(assoc-in [:open-database :api-error-text] nil))))

;; Called to launch the platform specific file picker
(reg-event-fx
 :pick-database-file
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:bg-pick-database-file]]}))

;; Calls the backend api to launch the platform specific file picker
(reg-fx
 :bg-pick-database-file
 (fn []
   (bg/pick-database-to-read-write
    (fn [api-response]
      (when-let [picked-response (on-ok
                                  api-response
                                  #(dispatch [:database-file-pick-error %]))]
        (dispatch [:open-database/database-file-picked picked-response]))))))

;; Called for repicking the database file when user presses the database file in the home page list
;; Similar to 'bg-pick-database-file'
(reg-fx
 :bg-repick-database-file
 (fn []
   (bg/pick-database-to-read-write
    (fn [api-response]
      (when-let [picked-response (on-ok
                                  api-response
                                  #(dispatch [:database-file-pick-error %]))]
        (dispatch [:database-file-repicked picked-response]))))))

;; A database file is already picked in the previous event (Storage selection dialog). 
;; This will make dialog open status true and login dialog is shown

;; This event is called from start page, remote page and native event handler

(reg-event-fx
 :open-database/database-file-picked
 (fn [{:keys [db]} [_event-id {:keys [file-name full-file-name-uri] :as kdbx-file-info-m}]]
   (let [;;found-in-recent-list (is-db-key-found-in-recently-used-dbs db full-file-name-uri)
         biometric-available (bg/is-biometric-available)
         biometric-enabled-db? (biometric-enabled-to-open-db db full-file-name-uri)
         {:keys [db-file-name] :as auto-open-props} (get-in db [:open-database :auto-open-props])]

     ;;(println ":open-database/database-file-picked is called found-in-recent-list biometric-available biometric-enabled-db? " found-in-recent-list biometric-available biometric-enabled-db?)
     (cond
       ;; User picked a file and passed that info in 'kdbx-file-info-m'
       ;; There is a possibility that user picked a db file that is already in the recent opened db list (same db-key), 
       ;; then we try to use the biometric auth if enabled ( the open db dialog is not shown)
       (and biometric-available biometric-enabled-db?)
       {:fx [[:bg-authenticate-with-biometric-before-db-open [kdbx-file-info-m]]]}

       ;; Next we check auto-open-props to ensure this is not for auto open
       ;; Or if it is from auto open but the user picked file-name does not match the one from auto open entry
       ;; In both the cases we open the 'open-database-dialog' prompting user to enter credentials
       (or (empty? auto-open-props) (not= db-file-name file-name))
       {:db (init-show-open-database-dialog db kdbx-file-info-m)}

       ;; Launched from Auto open 
       ;; Note: the db-key in 'auto-open-props' is nil and that is the reason we asked user to pick a file
       ;; and that is set to the picked full file url 'full-file-name-uri'
       :else
       {:fx [[:dispatch [:open-database/database-file-picked-in-auto-open
                         (assoc auto-open-props :db-key full-file-name-uri)]]]}))))

#_(reg-event-fx
   :open-database/database-file-picked
   (fn [{:keys [db]} [_event-id {:keys [file-name full-file-name-uri]}]]
     (let [{:keys [db-file-name] :as auto-open-props} (get-in db [:open-database :auto-open-props])]
     ;; auto-open-props is set when child database open is launched but the db file name is not found
     ;; in the recently used db list
       (if (or (empty? auto-open-props) (not= db-file-name file-name))
         {:db (-> db init-open-database-data
                ;; database-file-name is just the 'file name' part derived from full uri 'database-full-file-name'
                ;; to show in the dialog
                  (assoc-in [:open-database :database-file-name] file-name)
                  (assoc-in [:open-database :database-full-file-name] full-file-name-uri)
                  (assoc-in [:open-database :dialog-show] true))}
       ;; Call auto open 
         {:fx [[:dispatch [:open-database/database-file-picked-in-auto-open
                         ;; Note: the db-key in 'auto-open-props' is nil and that is the reason we asked user to pick a file
                         ;; and that is set to the picked full file url 'full-file-name-uri'
                           (assoc auto-open-props :db-key full-file-name-uri)]]]}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;  DB open using biometric  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Called when user picks a db on the start page database list
(reg-event-fx
 :open-database/database-file-picked-1
 (fn [{:keys [db]} [_event-id {:keys [_file-name full-file-name-uri] :as kdbx-file-info-m}]]
   (let [biometric-available (bg/is-biometric-available)
         biometric-enabled-db? (biometric-enabled-to-open-db db full-file-name-uri)]
     (if (and biometric-available biometric-enabled-db?)
       {;; Calls the biometric authentication to get stored crdentials
        :fx [[:bg-authenticate-with-biometric-before-db-open [kdbx-file-info-m]]]}
       ;; Calls the regular open db dialog
       {:db (init-show-open-database-dialog db kdbx-file-info-m)}))))

(reg-event-fx
 :open-database-biometric-auth-cancelled-or-failed
 (fn [{:keys [db]} [_event_id {:keys [_file-name _full-file-name-uri] :as kdbx-file-info-m}]]
   {:db  (init-show-open-database-dialog db kdbx-file-info-m)}))

(defn- handle-db-credentials-response
  "A dispatch fn handler that is called when the backend api 
   stored-db-credentials-on-biometric-authentication for db open returns"
  [kdbx-file-info-m api-response]
  (let [stored-credentials (on-ok api-response
                                  (fn [error]
                                    (println "The bg/stored-db-credentials-on-biometric-authentication call returned error " error)
                                   ;; When Backend api 'stored-db-credentials-on-biometric-authentication' results in error 
                                   ;; for whatever reason. Ideally should not happen!
                                    (dispatch [:open-database-dialog-show kdbx-file-info-m])))]
    (if (nil? stored-credentials)
      ;; Handles the situation the stored-credentials returned from backend api is None
      ;; This happens when user presses the db on db list first time after enabling Biometric in the settings 
      (dispatch [:open-database-db-open-with-credentials kdbx-file-info-m])
      ;; Found some stored-crdentials value
      (dispatch [:open-database-db-open-credentials-retrieved stored-credentials kdbx-file-info-m]))))

;; Call this when both flags 'biometric-available' and 'biometric-enabled-db?' are true
(reg-fx
 :bg-authenticate-with-biometric-before-db-open
 (fn [[{:keys [full-file-name-uri] :as kdbx-file-info-m}]]
   (let [;; Need to use 'partial' to create a backend call response handler 
         ;; that holds 'kdbx-file-info-m' for later use 
         cr-response-handler (partial handle-db-credentials-response kdbx-file-info-m)]

     (bg/authenticate-with-biometric
      (fn [api-response]
        (when-let [result (on-ok api-response
                                 (fn [error]
                                   ;; As a fallback if there is any error in using biometric call. Not expected
                                   (println "The bg/authenticate-with-biometric call returned error " error)
                                   (dispatch [:open-database-dialog-show kdbx-file-info-m])))]

          ;; The variable 'result' will have some valid value when biometric call works 
          (if (= result const/BIOMETRIC-AUTHENTICATION-SUCCESS)
            ;; Retrieve the auth info for furthur use
            (bg/stored-db-credentials-on-biometric-authentication full-file-name-uri cr-response-handler)
            ;; As biometric matching failed, we need to use credential based one
            (dispatch [:open-database-biometric-auth-cancelled-or-failed kdbx-file-info-m]))))))))

;; Called when biometric auth fails. 
;; This can happen when the user uses FaceId first time after enabling or 
;; stored crdentials are no more valid
(reg-event-fx
 :open-database-db-open-with-credentials
 (fn [{:keys [_db]} [_event-id kdbx-file-info-m]]
   {:fx [[:dispatch [:open-database-dialog-show kdbx-file-info-m]]
         [:dispatch [:common/error-box-show 'biometricDbOpenFirstTime 'biometricDbOpenFirstTime]]]}))

;; Called after getting the stored credentials ( a map from struct StoredCredential ) from secure enclave
(reg-event-fx
 :open-database-db-open-credentials-retrieved
 ;; The args are stored-credentials , kdbx-file-info-m
 (fn [{:keys [db]} [_event-id {:keys [password key-file-name]} {:keys [full-file-name-uri] :as kdbx-file-info-m}]]
   ;; Set the credentials fields in :open-database fields so that we can reuse the credentisals if repick is used
   ;; This repick may be asked if 'bg-load-kdbx' call response comes back with error 'FILE_NOT_FOUND' or 'PERMISSION_REQUIRED_TO_READ'
   ;; Particularly we see 'FILE_NOT_FOUND' error code when iCloud file sync is not yet happened 
   {:db (-> db
            (assoc-in [:open-database :password] password)
            (assoc-in [:open-database :key-file-name] key-file-name)
            (assoc-in [:open-database :database-full-file-name] full-file-name-uri)
            (assoc-in [:open-database  :dialog-show] false))
    :fx [[:dispatch [:common/message-modal-show nil 'loading]]
         ;; load-kdbx as we have credentials 
         [:bg-load-kdbx  [{:db-file-name full-file-name-uri
                           :password password
                           :key-file-name key-file-name
                           :biometric-auth-used true
                           :kdbx-file-info-m kdbx-file-info-m}]]]}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(reg-event-fx
 :database-file-repicked
 (fn [{:keys [db]} [_event-id {:keys [full-file-name-uri] :as picked-response}]]
   (let [{:keys [database-full-file-name password key-file-name]} (get db :open-database)]
     ;; There is a possibility that user might have picked up another database file instead of repicking the original database file
     (if (= database-full-file-name full-file-name-uri)
       ;; User picked up the same database file 
       {:db (-> db
                (assoc-in [:open-database :dialog-show] true)
                (assoc-in [:open-database :status] :in-progress))
        :fx [[:bg-load-kdbx [{:db-file-name database-full-file-name
                              :password password
                              :key-file-name key-file-name
                              :biometric-auth-used false}]]]}
       ;; User picked up a different database and it is then treated as similar to pressing 'Open databse' button
       ;; The Open database dialog is shown to enter new credentials
       ;; Do we need show some message about this to the user before proceeding this action?
       {:fx [[:dispatch [:open-database/database-file-picked picked-response]]]}))))

(reg-event-fx
 :database-file-pick-error
 (fn [{:keys [_db]} [_event-id error]]
   ;; If the user cancels any file selection, 
   ;; the RN response is a error due to the use of promise rejecton in Native Module. And we can ignore that error 
   {:fx [(when-not (= "DOCUMENT_PICKER_CANCELED" (:code error))
           [:dispatch [:common/error-box-show "File Pick Error" error]])]}))

;; TODO: Need to initiate loading progress indication
;; :open-database should have valid values by this time as user has picked a database and entered all valid 
;; credentials to open the db
(reg-event-fx
 :open-database-read-db-file
 (fn [{:keys [db]} [_event-id]]
   (let [error-fields (validate-required-fields db)
         errors-found (boolean (seq error-fields))
         {:keys [database-full-file-name password key-file-name]} (get-in db [:open-database])]
     (if errors-found
       {:db (assoc-in db [:open-database :error-fields] error-fields)}
       {:db (-> db (assoc-in [:open-database :status] :in-progress))
        :fx [[:bg-load-kdbx [{:db-file-name database-full-file-name
                              :password password
                              :key-file-name key-file-name
                              :biometric-auth-used false}]]]}))))

(reg-fx
 :bg-load-kdbx
 (fn [[{:keys [db-file-name password key-file-name biometric-auth-used kdbx-file-info-m]}]]
   ;; db-file-name is db-key
   ;; kdbx-file-info-m will have non nil value only for biometric credentials usage
   (bg/load-kdbx db-file-name password key-file-name biometric-auth-used
                 (fn [api-response]
                   (when-let [kdbx-loaded
                              (on-ok
                               api-response
                               (fn [error]
                                 ;; We use modal message after a biometric auth is used
                                 ;; The same thing is also done in 'common/kdbx-database-opened'
                                 (dispatch [:common/message-modal-hide])
                                 (dispatch [:open-database-read-kdbx-error error kdbx-file-info-m])))]
                     (dispatch [:open-database-db-opened kdbx-loaded]))))))

(reg-event-fx
 :open-database-read-kdbx-error
 (fn [{:keys [db]} [_event-id error kdbx-file-info-m]]
   {:db (-> db (assoc-in [:open-database :error-fields] {})
            (assoc-in [:open-database :status] :completed))

    ;; We get error code PERMISSION_REQUIRED_TO_READ or FILE_NOT_FOUND from middle layer readKdbx 

    ;; PERMISSION_REQUIRED_TO_READ may happen if the File Manager decides 
    ;; that the existing uri should be refreshed by asking user to pick the database again 

    ;; FILE_NOT_FOUND happens when the uri we have no more points to a valid file as that file 
    ;; might have been changed by other program.

    ;; In iOS, typically the error is "NSFileProviderErrorDomain Code=-1005 "The file doesn’t exist."

    ;; In iOS FILE_NOT_FOUND is triggered when the iCloud file url remains the same but the iCloud sync has not
    ;; yet completed. In that we ask the user to repick the same file
    :fx (cond

          (= (:code error) const/PERMISSION_REQUIRED_TO_READ)
          [[:dispatch [:repick-confirm-show const/PERMISSION_REQUIRED_TO_READ]]
           [:dispatch [:open-database-dialog-hide]]]

          (= (:code error) const/FILE_NOT_FOUND)
          [[:dispatch [:open-database-dialog-hide]]
           [:dispatch [:repick-confirm-show const/FILE_NOT_FOUND]]]

          (= error "BiometricCredentialsAuthenticationFailed")
          [[:dispatch [:open-database-db-open-with-credentials kdbx-file-info-m]]]
          #_[[:dispatch [:open-database/database-file-picked kdbx-file-info-m]]
             [:dispatch [:common/error-box-show "Database Open" "Please enter the credentials"]]]

          :else
          (let [b (str/starts-with? error "InvalidCredentials:")
                msg (if b
                      (-> error (str/split #"InvalidCredentials:") last str/trim)
                      error)]
            [[:dispatch [:common/error-box-show "Database Open Error" msg]]])

          ;;:else
          ;;[[:dispatch [:common/error-box-show "Database Open Error" error]]]
          )}))

(reg-event-fx
 :open-database-db-opened
 (fn [{:keys [db]} [_event-id kdbx-loaded]]
   {:db (-> db (assoc-in [:open-database :error-fields] {})
            (assoc-in [:open-database :status] :completed))
    :fx [[:dispatch [:open-database-dialog-close]]
         [:dispatch [:common/kdbx-database-opened kdbx-loaded]]]}))

(reg-event-db
 :repick-confirm-show
 (fn [db [_event-id reason-code]]
   (let [file-name (get-in db [:open-database :database-file-name])]
     (-> db
         (assoc-in [:open-database :repick-confirm :dialog-show] true)
         (assoc-in [:open-database :repick-confirm :file-name] file-name)
         (assoc-in [:open-database :repick-confirm :reason-code] reason-code)))))

(reg-event-fx
 :repick-confirm-close
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db
            (assoc-in [:open-database :repick-confirm]
                      {:dialog-show false :file-name nil}))
    :fx [[:bg-repick-database-file]]}))

(reg-event-db
 :repick-confirm-cancel
 (fn [db [_event-id]]
   (-> db
       (assoc-in [:open-database :repick-confirm :dialog-show] false)
       (assoc-in [:open-database :repick-confirm :file-name] nil)
       (assoc-in [:open-database :repick-confirm :reason-code] nil))))

(reg-sub
 :repick-confirm-data
 (fn [db]
   (get-in db [:open-database :repick-confirm])))

(reg-sub
 :open-database-dialog-data
 (fn [db _query-vec]
   (get-in db [:open-database])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  DB Unlock ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; All DB lock related calls are in common ns

(defn unlock-selected-db
  "Called to initiate the unlocking process of a selected database"
  [file-name full-file-name-uri]
  (dispatch [:open-database-unlock-kdbx {:file-name file-name :full-file-name-uri full-file-name-uri}]))

(defn authenticate-with-credential-to-unlock
  "Called after user enters required credentials in a dialog box and press continue button to unlock"
  []
  (dispatch [:authenticate-with-credential-to-unlock]))

;;;;; ;;;;; ;;;;; ;;;;; ;;;;; 

;; Deprecate as 'authenticate-biometric-confirm-dialog' is no more used ?
(defn authenticate-biometric-ok
  "User accepts to use biometric based authentication"
  []
  (dispatch [:open-database-authenticate-biometric-ok]))

;; Deprecate as 'authenticate-biometric-confirm-dialog' is no more used ?
(defn authenticate-biometric-cancel
  "User does not want to use biometric based authentication"
  []
  (dispatch [:open-database-authenticate-biometric-cancel]))

;; Deprecate as 'authenticate-biometric-confirm-dialog' is no more used ?
(defn authenticate-biometric-confirm-dialog-data
  "Need to get user confirmation to use the biometric authentication for quick unlock"
  []
  (subscribe [:open-database-authenticate-biometric-confirm-dialog-data]))
;;;;;;;;;; ;;;;; ;;;;; ;;;;;  

(reg-event-fx
 :open-database-unlock-kdbx
 ;; kdbx-file-info-m is map with keys [file-name full-file-name-uri key-file-name..]
 (fn [{:keys [db]} [_event-id {:keys [full-file-name-uri] :as kdbx-file-info-m}]]
   ;; Determine whether, we can do bio authentication or credential dialog auth or PIN based here (yet to add)
   (let [biometric-available (bg/is-biometric-available)
         biometric-enabled-db? (biometric-enabled-to-unlock-db db full-file-name-uri)]

     (if (and  biometric-available biometric-enabled-db?)
       {:fx [[:dispatch [:open-database-unlock-with-biometric-authentication kdbx-file-info-m]]]}
       {:fx [[:dispatch [:open-database-unlock-dialog-show kdbx-file-info-m]]]}))))


(reg-event-fx
 :open-database-unlock-with-biometric-authentication
 (fn [{:keys [db]} [_event-id kdbx-file-info-m]]
   {:fx [[:bg-authenticate-with-biometric [kdbx-file-info-m]]]}))

;; Confirm dialog
(reg-event-fx
 :open-database-unlock-dialog-show
 (fn [{:keys [db]} [_event-id {:keys [_file-name _full-file-name-uri] :as kdbx-file-info-m}]]
   {:db (init-show-open-database-dialog db kdbx-file-info-m)

    #_(-> db init-open-database-data
            ;; database-file-name is just the 'file name' part derived from full uri 'database-full-file-name'
            ;; to show in the dialog
          (assoc-in [:open-database :database-file-name] file-name)
          (assoc-in [:open-database :database-full-file-name] full-file-name-uri)
          (assoc-in [:open-database :dialog-show] true))}))

;; Somewhat similar to :open-database-read-db-file
;; Called after user enters the credentials to unlock db
;; :open-database-unlock-dialog-show -> :authenticate-with-credential-to-unlock (as user presses continue)
;; :open-database should have valid credentials
(reg-event-fx
 :authenticate-with-credential-to-unlock
 (fn [{:keys [db]} [_event-id]]
   ;; open-database should have valid values to unlock 
   (let [error-fields (validate-required-fields db)
         errors-found (boolean (seq error-fields))
         {:keys [database-full-file-name password key-file-name]} (:open-database db)]
     (if errors-found
       {:db (assoc-in db [:open-database :error-fields] error-fields)}
       {:fx [[:bg-unlock-kdbx [database-full-file-name password key-file-name]]]}))))


(defn- on-unlock-response [api-response]
  (when-let [kdbx-loaded (on-ok
                          api-response
                          #(dispatch [:common/error-box-show "Database Unlock Error" %]))]
    (dispatch [:unlock-kdbx-success kdbx-loaded])))

(reg-fx
 :bg-authenticate-with-biometric
 (fn [[{:keys [full-file-name-uri] :as kdbx-file-info-m}]]
   (bg/authenticate-with-biometric (fn [api-response]
                                     (when-let [result
                                                (on-ok api-response
                                                       #(dispatch [:common/error-box-show "Authentication Error" %]))]
                                       (if (= result const/BIOMETRIC-AUTHENTICATION-SUCCESS)
                                         (bg/unlock-kdbx-on-biometric-authentication
                                          full-file-name-uri
                                          on-unlock-response)
                                         ;; As biometric based failed, we need to use credential based one
                                         (dispatch [:open-database-unlock-dialog-show kdbx-file-info-m])))))))

(reg-fx
 :bg-unlock-kdbx
 (fn [[db-key password key-file-name]]
   (bg/unlock-kdbx db-key
                   password
                   key-file-name
                   on-unlock-response)))

;; TODO: 
;; The Quick unlock just gets the data from memory on successful
;; authentication using existing credential
;; So we need to make sure, the data are not stale by checking whether database 
;; has been changed externally and load accordingly as done for desktop
;; For now, at least user needs to be alerted that, the db is changed 
;; See desktop's event ':database-change-detected' in common ns
(reg-event-fx
 :unlock-kdbx-success
 (fn [{:keys [db]} [_event-id kdbx-loaded]]
   {:db (-> db (assoc-in [:open-database :error-fields] {})
            (assoc-in [:open-database :status] :completed))
    :fx [[:dispatch [:open-database-dialog-hide]]
         [:dispatch [:common/unlock-selected-db (:db-key kdbx-loaded)]]
         [:dispatch [:common/set-active-db-key (:db-key kdbx-loaded)]]
         [:dispatch [:app-settings/update-user-active-time (:db-key kdbx-loaded)]]
         [:dispatch [:common/message-snackbar-open 'databaseUnlocked]]]}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;   Auto open  ;;;;;;;;;;;;;;;;;;;;;;;;

(defn- init-auto-open
  "Initializes the :open-database for auto opening the db so that we can use in other events
   Returns the updated app-db
  "
  [db {:keys [db-key db-file-name password key-file-full-path key-file-name]}]
  (-> db init-open-database-data
      ;; database-file-name is just the 'file name' 
      ;; part derived from full uri 'database-full-file-name'to show in the dialog
      (assoc-in [:open-database :database-file-name] db-file-name)
      (assoc-in [:open-database :database-full-file-name] db-key)
      (assoc-in [:open-database :password] password)
      (assoc-in [:open-database :key-file-name-part] key-file-name)
      (assoc-in [:open-database :key-file-name] key-file-full-path)
      (assoc-in [:open-database :dialog-show] true)))

;; Called to open a database with credential data from an auto open entry 
;; This combines the actions of :open-database/database-file-picked and 
;; :open-database-read-db-file or authenticate-with-credential-to-unlock
(reg-event-fx
 :open-database/database-file-picked-in-auto-open
 ;; _db-file-name _password _key-file-full-path _key-file-name
 (fn [{:keys [db]} [_event-id {:keys [db-key] :as auto-open-data-m}]]

   (let [already-opened? (u/contains-val? (opened-db-keys db) db-key)
         locked? (is-db-locked db db-key)]
     (cond
       ;; We do not use biometric auth for unlock during auto open
       locked?
       {:db (init-auto-open db auto-open-data-m)
        :fx [[:dispatch [:authenticate-with-credential-to-unlock]]]}

       already-opened?
       {:fx [[:dispatch [:common/set-active-db-key db-key]]]}

       :else
       {:db (init-auto-open db auto-open-data-m)
        :fx [[:dispatch [:open-database-read-db-file]]]}))))

;; This event first navigates to the home page and then shows the dialog 'start-page-storage-selection-dialog'
;; Also auto-open-props is set here in [:open-database :auto-open-props]
;; so that open-database is opened with credentials to load the database when user picks a file
(reg-event-fx
 :open-database/auto-open-show-select-storage
 (fn [{:keys [db]} [_event_id auto-open-props]]
   {:db (-> db (assoc-in [:open-database :auto-open-props] auto-open-props))
    :fx [[:dispatch [:common/to-home-page]]
         ;; Shows the dialog 'start-page-storage-selection-dialog'
         [:dispatch [:generic-dialog-show-with-state
                     :start-page-storage-selection-dialog
                     {:kw-browse-type const/BROWSE-TYPE-DB-OPEN}]]]}))


(comment
  (in-ns 'onekeepass.mobile.events.open-database)
  (-> @re-frame.db/app-db :open-database)

  (def db-key (-> @re-frame.db/app-db :current-db-file-name))
  (-> @re-frame.db/app-db (get db-key) keys))



;; In 0.15.0, removed the use of this confirmation dialog for the biometric based db unlock
;; Called from event :open-database-unlock-kdbx
#_(reg-event-fx
   :open-database-authenticate-biometric-confirm
   (fn [{:keys [db]} [_event-id kdbx-file-info-m]]
     {:db (-> db
              (assoc-in [:open-database :authenticate-biometric-confirm :dialog-show] true)
              (assoc-in [:open-database :authenticate-biometric-confirm :data] kdbx-file-info-m))}))

;; In 0.15.0, removed the use of this confirmation dialog for the biometric based db unlock
;; Called from a confirm dialog
#_(reg-event-fx
   :open-database-authenticate-biometric-cancel
   (fn [{:keys [db]} [_event-id]]
     {:db (-> db
              (assoc-in [:open-database :authenticate-biometric-confirm :dialog-show] false))
      :fx [[:dispatch [:open-database-unlock-dialog-show
                       (get-in db [:open-database :authenticate-biometric-confirm :data])]]]}))

;; In 0.15.0, removed the use of this confirmation dialog for the biometric based db unlock
;; Called from a confirm dialog
#_(reg-event-fx
   :open-database-authenticate-biometric-ok
   (fn [{:keys [db]} [_event-id]]
   ;; Need to close the dialog!
     {:db (-> db (assoc-in [:open-database :authenticate-biometric-confirm :dialog-show] false))
      :fx [[:bg-authenticate-with-biometric [(get-in db [:open-database :authenticate-biometric-confirm :data])]]]}))

;; In 0.15.0, removed the use of this confirmation dialog for the db unlock before 
;; using biometric authentication
#_(reg-sub
   :open-database-authenticate-biometric-confirm-dialog-data
   (fn [db]
     (get-in db [:open-database :authenticate-biometric-confirm])))
