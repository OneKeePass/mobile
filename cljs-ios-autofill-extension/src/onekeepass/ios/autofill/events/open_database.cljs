(ns onekeepass.ios.autofill.events.open-database
  "All events that are specific to open a selected database"
  (:require
   [onekeepass.ios.autofill.background :as bg]
   [onekeepass.ios.autofill.constants :as const :refer [LOGIN_PAGE_ID]]
   [onekeepass.ios.autofill.events.common :refer [database-preference-by-db-key
                                                   on-ok
                                                  org-db-file-path]]
   [re-frame.core :refer [dispatch reg-event-db reg-event-fx reg-fx reg-sub
                          subscribe]]))


(defn show-login [file-name full-file-name-uri]
  #_(dispatch [:open-database/database-file-picked {:file-name file-name :full-file-name-uri full-file-name-uri}])
  (dispatch [:open-database/database-file-picked-1 {:file-name file-name :full-file-name-uri full-file-name-uri}]))

(defn database-field-update [kw-field-name value]
  (dispatch [:open-database-field-update kw-field-name value]))

(defn open-database-read-db-file
  "Called when user clicks the continue button. 
   By this time user would have picked a file to open in the previous pick file call and entered the credentials
  "
  []
  (dispatch [:open-database-read-db-file]))

(defn cancel-login []
  (dispatch [:common/previous-page]))

(defn login-page-data []
  (subscribe [:open-database-login-data]))

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

;; Shows the login page
(reg-event-fx
 :open-database/database-file-picked
 (fn [{:keys [db]} [_event-id {:keys [file-name full-file-name-uri]}]]
   {:db (-> db init-open-database-data
            ;; database-file-name is just the 'file name' part derived from full uri 'database-full-file-name'
            ;; to show in the dialog
            (assoc-in [:open-database :database-file-name] file-name)
            (assoc-in [:open-database :database-full-file-name] full-file-name-uri)
            (assoc-in [:open-database :dialog-show] true))

    :fx [[:dispatch [:common/next-page LOGIN_PAGE_ID "Unlock Database"]]]}))


(reg-event-fx
 :open-database-read-db-file
 (fn [{:keys [db]} [_event-id]]
   (let [{:keys [database-full-file-name password key-file-name]} (get-in db [:open-database])]
     {:db (-> db (assoc-in [:open-database :status] :in-progress))
      ;; bg-all-entries-on-db-open is similar to bg-load-kdbx in the main app
      :fx [[:bg-all-entries-on-db-open [{:db-key database-full-file-name
                                         :password password
                                         :key-file-name key-file-name
                                         :biometric-auth-used false}]]]})))

(reg-event-fx
 :open-database-read-kdbx-error
 (fn [{:keys [_db]} [_event-id error kdbx-file-info-m]]
   (if (= error "BiometricCredentialsAuthenticationFailed") 
     {:fx [[:dispatch [:open-database-db-open-with-credentials kdbx-file-info-m]]]}

     {:fx [[:dispatch [:common/error-box-show "Database Open Error" error]]]})))

;; Backend call to get all entries on openning a databse
;; This is similar to load-kdbx call in the main app
(reg-fx
 :bg-all-entries-on-db-open
 (fn [[{:keys [db-key password key-file-name biometric-auth-used kdbx-file-info-m]}]]
   (bg/all-entries-on-db-open db-key password key-file-name biometric-auth-used
                              (fn [api-response]
                                (when-let [entry-summaries (on-ok api-response #(dispatch [:open-database-read-kdbx-error % kdbx-file-info-m]))]
                                  #_(dispatch [:entry-list/update-selected-entry-items db-key entry-summaries])
                                  (dispatch [:all-entries-loaded db-key entry-summaries]))))))

;; Called after retreiving all entries for the opened database
(reg-event-fx
 :all-entries-loaded
 (fn [{:keys [_db]} [_event-id db-key entry-summaries]]
   {:fx [[:dispatch [:entry-list/update-selected-entry-items db-key entry-summaries]]
         [:bg-credential-service-identifier-filtering [db-key]]]}))

;; Called to load any matching entries based on ios autofill credential identifiers 
;; This is called after loading all entries summary - see the above event
(reg-fx
 :bg-credential-service-identifier-filtering
 (fn [[db-key]]
   (bg/credential-service-identifier-filtering
    db-key
    (fn [api-response]
      (when-let [result (on-ok api-response)]
        (dispatch [:search-term-completed result]))))))

(reg-sub
 :open-database-login-data
 (fn [db _query-vec]
   (get-in db [:open-database])))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;  DB open using biometric  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Called when user picks a db on the start page database list 
;; As compared to main app event, here we need to use :org-db-file-path as db-key to 
;; get the stored crdentials if this db opening supports biometric 
(reg-event-fx
 :open-database/database-file-picked-1
 (fn [{:keys [db]} [_event-id {:keys [full-file-name-uri] :as kdbx-file-info-m}]]

   (let [biometric-available (bg/is-biometric-available)
         biometric-enabled-db?  (boolean (-> (database-preference-by-db-key db full-file-name-uri) :db-open-biometric-enabled))
         org-db-key (org-db-file-path db full-file-name-uri)
         ;; Need to add 'org-db-file-path' so that we can use it to get the stored credentials
         ;; as the credentials are stored with the original db-key in the main app
         kdbx-file-info-m (merge kdbx-file-info-m {:org-db-file-path org-db-key})]
     (if (and biometric-available biometric-enabled-db?)
       {;; Calls the biometric authentication to get stored crdentials
        :fx [[:bg-authenticate-with-biometric-before-db-open [kdbx-file-info-m]]]}
       ;; Calls the regular open db dialog
       {:fx [[:dispatch [:open-database/database-file-picked kdbx-file-info-m]]]}))))

(reg-event-fx
 :open-database-db-open-with-credentials
 (fn [{:keys [_db]} [_event-id kdbx-file-info-m]]
   {:fx [[:dispatch [:open-database/database-file-picked kdbx-file-info-m]]
         ;; TODO: 
         ;; Add a message saying password might have changed and need to do onetime login 
         ;; in the main app to store the changed credentials 
         #_[:dispatch [:common/error-box-show "Database Open" "Please enter the credentials"]]]}))

;; Called after getting the stored credentials ( a map from struct StoredCredential) from secure enclave
(reg-event-fx
 :open-database-db-open-credentials-retrieved
 (fn [{:keys [_db]} [_event-id {:keys [password key-file-name]} {:keys [full-file-name-uri] :as kdbx-file-info-m}]]
   ;; backend api 'all-entries-on-db-open' is called as we have credentials now 
   {:fx [#_[:dispatch [:common/message-modal-show nil 'loading]]
         [:bg-all-entries-on-db-open
          [{:db-key full-file-name-uri
            :password password
            :key-file-name key-file-name
            :biometric-auth-used true
            :kdbx-file-info-m kdbx-file-info-m}]]]}))


(defn- handle-db-credentials-response
  "A dispatch fn handler that is called when the backend api 
   stored-db-credentials-on-biometric-authentication for db open returns with credential info"
  [kdbx-file-info-m api-response]
  (let [stored-crdentials
        (on-ok api-response
               (fn [error]
                 (println "The bg/stored-db-credentials-on-biometric-authentication call returned error " error)
                  ;; When Backend api 'stored-db-credentials-on-biometric-authentication' results in error 
                  ;; for whatever reason. Ideally should not happen!
                 (dispatch [:open-database/database-file-picked kdbx-file-info-m])))]
    
    (if (nil? stored-crdentials)

      ;; Handles the situation when the stored-crdentials returned from backend api is 'None'
      ;; This happens when user presses the db on db list first time after enabling Biometric in the settings 
      ;; Note: User needs to login to the main app atleast once after enabling Biometric
      (dispatch [:open-database-db-open-with-credentials kdbx-file-info-m])

      ;; Found some stored-crdentials value
      (dispatch [:open-database-db-open-credentials-retrieved stored-crdentials kdbx-file-info-m]))))

;; Call this when both flags 'biometric-available' and 'biometric-enabled-db?' are true
(reg-fx
 :bg-authenticate-with-biometric-before-db-open
 (fn [[{:keys [org-db-file-path] :as kdbx-file-info-m}]] 
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
            ;; Face ID is successful, so we get the stored credentials
            (bg/stored-db-credentials-on-biometric-authentication org-db-file-path cr-response-handler)
            
            ;; As biometric matching failed, we need to use credential based one
            (dispatch [:open-database/database-file-picked kdbx-file-info-m]))))))))


(comment
  (in-ns 'onekeepass.ios.autofill.events.open-database)
  (-> @re-frame.db/app-db keys)

  (def db-key (-> @re-frame.db/app-db :autofill-db-files-info first :org-db-file-path)))