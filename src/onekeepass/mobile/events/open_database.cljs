(ns onekeepass.mobile.events.open-database
  (:require
   [clojure.string :as str]
   [onekeepass.mobile.background :as bg]
   [onekeepass.mobile.constants :as const]
   [onekeepass.mobile.events.common :refer [biometric-enabled-to-open-db
                                            biometric-enabled-to-unlock-db
                                            on-ok]]
   [re-frame.core :refer [dispatch reg-event-db reg-event-fx reg-fx reg-sub
                          subscribe]]))

(defn cancel-on-press []
  (dispatch [:open-database-dialog-hide]))

(defn open-database-on-press []
  (dispatch [:pick-database-file]))

(defn open-selected-database
  "Called when user picks a db on the start page database list"
  [file-name full-file-name-uri already-opened?]
  (if already-opened?
    (dispatch [:common/set-active-db-key full-file-name-uri])
    (dispatch [:open-database/database-file-picked-1 {:file-name file-name :full-file-name-uri full-file-name-uri}])))

(defn database-field-update [kw-field-name value]
  (dispatch [:open-database-field-update kw-field-name value]))

(defn repick-confirm-close []
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
                     :key-file-name nil})

(defn- init-open-database-data
  "Initializes all open db related values in the incoming main app db 
  and returns the updated main app db
  "
  [db]
  (assoc-in db [:open-database] blank-open-db))

(defn- validate-required-fields
  [db]
  (let [error-fields {} #_(cond-> {}
                            (str/blank? (get-in db [:open-database :password]))
                            (assoc :password "A valid password is required"))]
    error-fields))

;; TODO: 
;; iOS specific
;; Need to delete bookmark file created while loading a kdbx file and then user 
;; cancels the login. We should delete only the bookmark file if it is not for
;; any existing db-key
(reg-event-db
 :open-database-dialog-hide
 (fn [db [_event-id]]
   (assoc-in  db [:open-database :dialog-show] false)))

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

(reg-event-fx
 :pick-database-file
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:bg-pick-database-file]]}))

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

;; This will make dialog open status true
(reg-event-fx
 :open-database/database-file-picked
 (fn [{:keys [db]} [_event-id {:keys [file-name full-file-name-uri]}]]
   {:db (-> db init-open-database-data
            ;; database-file-name is just the 'file name' part derived from full uri 'database-full-file-name'
            ;; to show in the dialog
            (assoc-in [:open-database :database-file-name] file-name)
            (assoc-in [:open-database :database-full-file-name] full-file-name-uri)
            (assoc-in [:open-database :dialog-show] true))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;  DB open using biometric  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Called when user picks a db on the start page database list
(reg-event-fx
 :open-database/database-file-picked-1
 (fn [{:keys [db]} [_event-id {:keys [full-file-name-uri] :as kdbx-file-info-m}]]

   (let [biometric-available (bg/is-biometric-available)
         biometric-enabled-db? (biometric-enabled-to-open-db db full-file-name-uri)]
     (if (and biometric-available biometric-enabled-db?)
       {;; Calls the biometric authentication to get stored crdentials
        :fx [[:bg-authenticate-with-biometric-before-db-open [kdbx-file-info-m]]]}
       ;; Calls the regular open db dialog
       {:fx [[:dispatch [:open-database/database-file-picked kdbx-file-info-m]]]}))))

(defn- handle-db-credentials-response
  "A dispatch fn handler that is called when the backend api 
   stored-db-credentials-on-biometric-authentication for db open returns"
  [kdbx-file-info-m api-response]
  (let [stored-crdentials (on-ok api-response
                                 (fn [error]
                                   (println "The bg/stored-db-credentials-on-biometric-authentication call returned error " error)
                                   ;; When Backend api 'stored-db-credentials-on-biometric-authentication' results in error 
                                   ;; for whatever reason. Ideally should not happen!
                                   (dispatch [:open-database/database-file-picked kdbx-file-info-m])))]
    (if (nil? stored-crdentials)
      ;; Handles the situation the stored-crdentials returned from backend api is None
      ;; This happens when user presses the db on db list first time after enabling Biometric in the settings
      #_(dispatch [:open-database/database-file-picked kdbx-file-info-m])
      (dispatch [:open-database-db-open-with-credentials kdbx-file-info-m])
      ;; Found some stored-crdentials value
      (dispatch [:open-database-db-open-credentials-retrieved stored-crdentials kdbx-file-info-m]))))

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
                                   (dispatch [:open-database/database-file-picked kdbx-file-info-m])))]
          
             ;; The variable 'result' will have some valid when biometric call works 
          (if (= result const/BIOMETRIC-AUTHENTICATION-SUCCESS)
            (bg/stored-db-credentials-on-biometric-authentication full-file-name-uri cr-response-handler)
               ;; As biometric matching failed, we need to use credential based one
            (dispatch [:open-database/database-file-picked kdbx-file-info-m]))))))))

(reg-event-fx
 :open-database-db-open-with-credentials
 (fn [{:keys [_db]} [_event-id kdbx-file-info-m]]
   {:fx [[:dispatch [:open-database/database-file-picked kdbx-file-info-m]]
         [:dispatch [:common/error-box-show 'biometricDbOpenFirstTime 'biometricDbOpenFirstTime ]]]}))

;; Called after getting the stored credentials ( a map from struct StoredCredential ) from secure enclave
(reg-event-fx
 :open-database-db-open-credentials-retrieved
 (fn [{:keys [_db]} [_event-id {:keys [password key-file-name]} {:keys [full-file-name-uri] :as kdbx-file-info-m}]]
   ;; load-kdbx as we have credentials
   ;; Show dialog when db load fails with authentication fails for the user to enter credentials
   {:fx [[:dispatch [:common/message-modal-show nil 'loading]]
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
    :fx [[:dispatch [:open-database-dialog-hide]]
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

(defn authenticate-with-credential
  "Called after user enters required credentials in a dialog box and press continue button to unlock"
  []
  (dispatch [:authenticate-with-credential]))

(defn authenticate-biometric-ok
  "User accepts to use biometric based authentication"
  []
  (dispatch [:open-database-authenticate-biometric-ok]))

(defn authenticate-biometric-cancel
  "User does not want to use biometric based authentication"
  []
  (dispatch [:open-database-authenticate-biometric-cancel]))

(defn authenticate-biometric-confirm-dialog-data
  "Need to get user confirmation to use the biometric authentication for quick unlock"
  []
  (subscribe [:open-database-authenticate-biometric-confirm-dialog-data]))

(reg-event-fx
 :open-database-unlock-kdbx
 ;; kdbx-file-info-m is map with keys [file-name full-file-name-uri key-file-name..]
 (fn [{:keys [db]} [_event-id {:keys [full-file-name-uri] :as kdbx-file-info-m}]]
   ;; Determine whether, we can do bio authentication or credential dialog auth or PIN based here (yet to add)
   (let [biometric-available (bg/is-biometric-available)
         biometric-enabled-db? (biometric-enabled-to-unlock-db db full-file-name-uri)]
     
     #_(if biometric-available
            ;; Need to confirm from user and then use biometric to authenticate
       {:fx [[:dispatch [:open-database-authenticate-biometric-confirm kdbx-file-info-m]]]}
       {:fx [[:dispatch [:open-database-unlock-dialog-show kdbx-file-info-m]]]})
     
     (if (and  biometric-available biometric-enabled-db?)
       ;; We are not using any confirmation dialog before biometric use as done earlier
       {:db (-> db (assoc-in [:open-database :authenticate-biometric-confirm :data] kdbx-file-info-m))
        :fx [[:dispatch [:open-database-authenticate-biometric-ok]]]} 
       {:fx [[:dispatch [:open-database-unlock-dialog-show kdbx-file-info-m]]]}))))

;; Called from event :open-database-unlock-kdbx
(reg-event-fx
 :open-database-authenticate-biometric-confirm
 (fn [{:keys [db]} [_event-id kdbx-file-info-m]]
   {:db (-> db
            (assoc-in [:open-database :authenticate-biometric-confirm :dialog-show] true)
            (assoc-in [:open-database :authenticate-biometric-confirm :data] kdbx-file-info-m))}))

;; Called from a confirm dialog
(reg-event-fx
 :open-database-authenticate-biometric-cancel
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db
            (assoc-in [:open-database :authenticate-biometric-confirm :dialog-show] false))
    :fx [[:dispatch [:open-database-unlock-dialog-show
                     (get-in db [:open-database :authenticate-biometric-confirm :data])]]]}))

;; Called from a confirm dialog
(reg-event-fx
 :open-database-authenticate-biometric-ok
 (fn [{:keys [db]} [_event-id]]
   ;; Need to close the dialog!
   {:db (-> db (assoc-in [:open-database :authenticate-biometric-confirm :dialog-show] false))
    :fx [[:bg-authenticate-with-biometric [(get-in db [:open-database :authenticate-biometric-confirm :data])]]]}))

;; Confirm dialog
(reg-event-fx
 :open-database-unlock-dialog-show
 (fn [{:keys [db]} [_event-id {:keys [file-name full-file-name-uri]}]]
   {:db (-> db init-open-database-data
            ;; database-file-name is just the 'file name' part derived from full uri 'database-full-file-name'
            ;; to show in the dialog
            (assoc-in [:open-database :database-file-name] file-name)
            (assoc-in [:open-database :database-full-file-name] full-file-name-uri)
            (assoc-in [:open-database :dialog-show] true))}))

(reg-sub
 :open-database-authenticate-biometric-confirm-dialog-data
 (fn [db]
   (get-in db [:open-database :authenticate-biometric-confirm])))


;; Somewhat similar to :open-database-read-db-file
;; Called after user enters credentials to unlock db
;; :open-database-unlock-dialog-show -> :authenticate-with-credential (as user presses continue)
(reg-event-fx
 :authenticate-with-credential
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

(comment
  (in-ns 'onekeepass.mobile.events.open-database)
  (def db-key (-> @re-frame.db/app-db :current-db-file-name))
  (-> @re-frame.db/app-db (get db-key) keys))