(ns onekeepass.mobile.background
  "All backend api calls that are used across many events"
  (:require
   ["@react-native-clipboard/clipboard" :as rnc-clipboard]
   ["react-native-file-viewer" :as native-file-viewer]
   ["react-native-vision-camera" :as rn-vision-camera]
   [camel-snake-kebab.core :as csk]
   [camel-snake-kebab.extras :as cske]
   [onekeepass.mobile.background-common :as bg-cmn :refer [android-invoke-api
                                                           api-args->json
                                                           call-api-async
                                                           invoke-api
                                                           ios-autofill-invoke-api
                                                           is-rs-type]]
   [onekeepass.mobile.background-remote-server :as bg-rs]
   [onekeepass.mobile.utils :as u :refer [contains-val?]]
   [react-native :as rn]))

(set! *warn-on-infer* true)

;;;; Re exports

(def transform-api-response bg-cmn/transform-api-response)

(def transform-response-excluding-keys bg-cmn/transform-response-excluding-keys)

(def okp-db-service bg-cmn/okp-db-service)

(def okp-document-pick-service bg-cmn/okp-document-pick-service)

(def rn-boot-splash bg-cmn/rn-boot-splash)

(def okp-export bg-cmn/okp-export)

(def okp-events bg-cmn/okp-events)

;;;;;;

;; Use .getString and .setString of native clipboard. 
;; The .getString call returns a Promise
(def clipboard (.-default ^js/ClipboardDefault rnc-clipboard))

(defn write-string-to-clipboard [s]
  (.setString clipboard s))

;; Use (.open file-viewer "full file path")
(def file-viewer ^js/FileViewer (.-default ^js/NFileViewer native-file-viewer))

;; This is React Native standard Linking component which is a native module
;; See https://reactnative.dev/docs/0.72/linking
;; We can use '(js/Object.entries rn-native-linking)' to see all methods and props
(def rn-native-linking ^js/RNLinking rn/Linking)

;; TODO: Need to replace all places where DOCUMENT_PICKER_CANCELED checks are used with this fn
(defn document-pick-cancelled [error-m]
  (= "DOCUMENT_PICKER_CANCELED" (:code error-m)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-constants []
    (.getConstants okp-db-service))

(defn is-biometric-available []
  ;; Valid values expected for BiometricAvailable are "true" or "false" for both iOS and Android
  (=  (-> okp-db-service .getConstants .-BiometricAvailable) "true"))

(defn open-https-url
  "Called to open a valid web url using the built-in browser
   It is assumed the arg 'https-url' is a valid https url and no validation is done here for this
   "
  [https-url dispatch-fn]
  ;; .openURL returns a promise and it is resolved in call-api-async
  (call-api-async
   (fn [] (.openURL rn-native-linking https-url)) dispatch-fn :no-response-conversion true))

(defn open-mobile-settings [dispatch-fn]
  (call-api-async
   (fn [] (.openSettings rn-native-linking)) dispatch-fn :no-response-conversion true))

(defn view-file
  "Called to view any file using the native file viewer"
  [full-file-name dispatch-fn]
  (call-api-async (fn [] (.open file-viewer full-file-name)) dispatch-fn))

(defn hide-splash
  "Calls the native module RNBootSplash's hide method directly
   We are not using the JS api from RNBootSplash
   "
  [dispatch-fn]
  (call-api-async (fn [] (.hide rn-boot-splash true)) dispatch-fn))

(defn authenticate-with-biometric
  "Called to authenticate the previously locked database. 
  There is no db specific biometric authentication settings or control. If the device supports
  the biometric, then that feature is used for any locked database to unlock
  "
  [dispatch-fn]
  (call-api-async (fn [] (.authenticateWithBiometric okp-db-service)) dispatch-fn))

;; Android: 
;; The call sequence - pickKdbxFileToCreate, .createKdbx - to create
;; a new database works (works only with GDrive, DropBox, Local)
;; It apperars 'ACTION_CREATE_DOCUMENT' based intent works only with GDrive,DropBox, Local only
;; See https://stackoverflow.com/questions/64730497/missing-document-providers-using-intent-actioncreatedocument 
;; May need to use ACTION_SEND to save the newly created database file first and followed by ACTION_OPEN_DOCUMENT
;; to open the db
;; 
;; iOS
;; The call sequence works for Local,iCloud,DropBox. In case of OneDrive, the file is 
;; created with 0 bytes and shows the entry category screen. If we make some changes and saved the db, then 
;; db size reflects that 
;; But this sequence does not work for GDrive
;; So instead, in case of iOS, we are using the 'pick-and-save-new-kdbxFile' fn and not this one
(defn pick-document-to-create
  "Starts a document picker view so that user can select a location to create the db file
  kdbx-file-name is the database file name. It is not full path. On users pick, the complete url
  is passed to dispatch-fn in a map with keys [file-name full-file-name-uri] as ':ok' value. 
  "
  [kdbx-file-name dispatch-fn]
  ;;(println "pick-document-to-create called for " kdbx-file-name)
  (call-api-async (fn [] (.pickKdbxFileToCreate okp-document-pick-service kdbx-file-name))
                  dispatch-fn
                  :error-transform true))

;; This is used specifically in iOS. Here the call sequence - pickAndSaveNewKdbxFile,readKdbx - is used
;; This works for Local,iCloud. But in the cases of GDrive, OneDrive, the new database files are created
;; But we get 'COORDINATOR_CALL_FAILED' error 'Couldn’t communicate with a helper application'
;
; However the kdbx is file is created successfully and we can open the database (by picking the db again from File App ?)
;;
;; May need to explore the use of UIActivityViewController based export support. Using this the newly crated temp db file
;; may be saved to another app and then open from that. Someting similar to 'ACTION_SEND'
(defn pick-and-save-new-kdbxFile
  "Called to show a document pick view"
  [file-name new-db dispatch-fn]
  (call-api-async (fn [] (.pickAndSaveNewKdbxFile okp-document-pick-service file-name (api-args->json {:new-db new-db})))
                  ;; This dipatch function receives a map with keys [file-name full-file-name-uri] as ':ok' value
                  dispatch-fn
                  :error-transform true))

;; This works for both iOS and Android
(defn pick-database-to-read-write
  "Called to pick a kdbx file using platform specific File Manager. The 'dispatch-fn' called with a full uri
   of a file picked and the file is read and loaded in the subsequent call
   "
  [dispatch-fn]
  (call-api-async (fn []
                    (.pickKdbxFileToOpen okp-document-pick-service))
                  dispatch-fn
                  :error-transform true))

;; Both pick-key-file-to-copy and pick-upload-attachment uses the same 
;; RN backend api 'pickKeyFileToCopy'
;; TODO: Rename pickKeyFileToCopy -> pickFileToCopy ??

;; This works for both iOS and Android
(defn pick-key-file-to-copy
  "Called to pick any file using platform specific File Manager. The 'dispatch-fn' called with a full uri
   of a file picked and the file is read and loaded in the subsequent call
   "
  [dispatch-fn]
  (call-api-async (fn []
                    ;; This call is followed by copy-key-file 
                    (.pickKeyFileToCopy okp-document-pick-service))
                  dispatch-fn
                  :error-transform true))

;; This works for both iOS and Android
(defn pick-key-file-to-save
  "Called to save the selected key file to a user chosen location
   The arg 'full-file-name' is the fey file absolute path (locally inside the app stored file path)
   The arg 'file-name' is the just key file name part
   "
  [full-file-name file-name dispatch-fn]
  (call-api-async (fn [] (.pickKeyFileToSave okp-document-pick-service full-file-name file-name))
                  dispatch-fn :error-transform true))

;; This works for both iOS and Android
(defn pick-upload-attachment
  "Called to pick any file using platform specific File Manager. The 'dispatch-fn' called with a full uri
   of a file picked and the file is read and loaded in the subsequent call in 'upload-attachment'
   "
  [dispatch-fn]
  (call-api-async (fn []
                    ;; For now we are using the same RN function that is used for key file pickup
                    ;; This call is followed by upload-attachment from DbService RN module
                    (.pickKeyFileToCopy okp-document-pick-service))
                  dispatch-fn
                  :error-transform true))

;; This works for both iOS and Android 
(defn pick-attachment-file-to-save
  "Called to save the selected key file to a user chosen location
     The arg 'full-file-name' is the fey file absolute path (locally inside the app's temp stored file path)
     The arg 'attachment-name' is the just attachment name part
     "
  [full-file-name attachment-name dispatch-fn]
  (call-api-async (fn [] (.pickAttachmentFileToSave
                          okp-document-pick-service full-file-name attachment-name))
                  dispatch-fn :error-transform true))

;; This works for both iOS and Android
;; TODO: Need to rename '.pickKeyFileToCopy' to '.pickFileToCopy' 
(defn pick-file
  "Called to pick any file using platform specific File Manager. The 'dispatch-fn' called with a full uri
   of a file picked and that file path is used to read and/or copy in the subsequent call
   "
  [dispatch-fn]
  (call-api-async (fn []
                    ;; Typically this call needs to be followed by a read and/or copy call 
                    (.pickKeyFileToCopy okp-document-pick-service))
                  dispatch-fn
                  :error-transform true))


;;;;;;;;

(defn ios-pick-on-save-error-save-as
  "Called to present os specific view for the user to save the copied kdbx file"
  [kdbx-file-name db-key dispatch-fn]
  (call-api-async (fn [] (.pickOnSaveErrorSaveAs
                          okp-document-pick-service
                          kdbx-file-name db-key))
                  dispatch-fn
                  :error-transform true))

(defn ios-complete-save-as-on-error [db-key new-db-key file-name dispatch-fn]
  (call-api-async (fn [] (.completeSaveAsOnError
                          okp-db-service
                          (api-args->json {:db-key db-key :new-db-key new-db-key :file-name file-name})))
                  dispatch-fn))

;;;;;;   For autofill and ios app group related calls

(defn ios-copy-files-to-group [db-key dispatch-fn]
  (ios-autofill-invoke-api "copy_files_to_app_group" {:db-key db-key} dispatch-fn))

(defn ios-delete-copied-autofill-details [db-key dispatch-fn]
  (ios-autofill-invoke-api "delete_copied_autofill_details" {:db-key db-key} dispatch-fn))

(defn ios-query-autofill-db-info [db-key dispatch-fn]
  (ios-autofill-invoke-api "query_autofill_db_info" {:db-key db-key} dispatch-fn))

(defn ios-copy-to-clipboard
  "Called to copy a selected field value to clipboard
   The arg field-info is a map that statifies the enum member 
   ClipboardCopyArg {field_name,field_value,protected,cleanup_after}
   "
  [field-info dispatch-fn]
  (ios-autofill-invoke-api "clipboard_copy" field-info dispatch-fn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Android specific ;;;;;;;;;;;;;;;;;;;;;;

(defn android-pick-on-save-error-save-as [kdbx-file-name dispatch-fn]
  (pick-document-to-create kdbx-file-name dispatch-fn))

(defn android-complete-save-as-on-error [db-key new-db-key file-name dispatch-fn]
  (call-api-async (fn [] (.completeSaveAsOnError okp-db-service db-key new-db-key file-name)) dispatch-fn :error-transform true))

#_(defn android-copy-to-clipboard
    "Called to copy a selected field value to clipboard
   The arg field-info is a map that statifies the enum member 
   ClipboardCopyArg {field_name,field_value,protected,cleanup_after}
   "
    [field-info dispatch-fn]
    (android-invoke-api "clipboard_copy" field-info dispatch-fn))

(defn android-autofill-filtered-entries
  "Gets one or more entries based on the search term derived from autofill requesting app domain"
  [db-key dispatch-fn]
  (android-invoke-api "autofill_filtered_entries" {:db-key db-key} dispatch-fn))

(defn android-complete-login-autofill
  "This will send the login credentials to the calling app when user presses Autofill action"
  [username password dispatch-fn]
  (android-invoke-api "complete_autofill" {:type "Login" :username username :password password} dispatch-fn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn copy-key-file
  "After user picks up a file to use as Key File, this api is called to copy to a dir inside the app"
  [full-file-name dispatch-fn]
  (call-api-async (fn [] (.copyKeyFile okp-db-service full-file-name)) dispatch-fn :error-transform true))

(defn upload-attachment
  "After user picks up a file to use as Key File, this api is called to copy to a dir inside the app"
  [db-key full-file-name dispatch-fn]
  (call-api-async (fn [] (.uploadAttachment
                          okp-db-service full-file-name
                          (api-args->json {:db_key db-key}
                                          ;; Note the use of :db_key instead of :db-key
                                          ;; and we are using convert-request false 
                                          :convert-request false)))
                  dispatch-fn :error-transform true))


(defn handle-picked-file
  "After user picks up a file, this api is called to read and/or copy to a dir inside the app
  picked-file-handler is map that corresponds to the enum 'PickedFileHandler' (ffi layer)
  e.g {:handler \"SftpPrivateKeyFile\"}
  "
  [full-file-name picked-file-handler dispatch-fn]
  (call-api-async (fn [] (.handlePickedFile okp-db-service full-file-name
                                            (api-args->json
                                             {:picked-file-handler picked-file-handler}
                                             ;; Recursively converts all keys to 'snake_case'
                                             :convert-request true)))
                  dispatch-fn :error-transform true))

(defn create-kdbx
  "Called with full file name uri that was picked by the user through the document picker 
   and new db related info in a map
   Used only in case of Android. See comments in pick-document-to-create and pick-and-save-new-kdbxFile
   "
  [full-file-name new-db dispatch-fn]
  (if-not (is-rs-type full-file-name)
    (call-api-async (fn []
                      (.createKdbx
                       okp-db-service
                       full-file-name
                       (api-args->json {:new-db new-db})))
                    dispatch-fn :error-transform true)

    (bg-rs/create-kdbx new-db dispatch-fn)))

;; Used to get any uri that may be avaiable to open when user starts our app by pressing 
;; a db file with extension .kdbx. 
;; TODO: We may need to rename xxxxxOnCreate to something relevant action here  
(defn kdbx-uri-to-open-on-create [dispatch-fn]
  (call-api-async #(.kdbxUriToOpenOnCreate okp-db-service) dispatch-fn))

(defn export-kdbx
  "Called with full file name uri that was picked by the user through the document picker 
   and new db related info in a map
   "
  [full-file-name  dispatch-fn]
  (call-api-async (fn [] (.exportKdbx okp-export full-file-name))
                  dispatch-fn :error-transform false))

(defn load-kdbx [db-file-name password key-file-name biometric-auth-used dispatch-fn]
  (if-not (is-rs-type db-file-name)
    (call-api-async (fn []
                      (.readKdbx okp-db-service
                                 db-file-name
                                 (api-args->json {:db-file-name db-file-name
                                                  :password password
                                                  :key-file-name key-file-name
                                                  :biometric-auth-used biometric-auth-used}
                                                 :convert-request true)))
                    dispatch-fn :error-transform true)
    (bg-rs/read-kdbx db-file-name password key-file-name biometric-auth-used dispatch-fn)))


(defn read-latest-backup-kdbx [db-file-name password key-file-name biometric-auth-used dispatch-fn]
  (invoke-api "read_latest_backup" {:db-file-name db-file-name
                                    :password password
                                    :key_file_name key-file-name
                                    :biometric-auth-used biometric-auth-used} dispatch-fn))

(defn save-kdbx [full-file-name overwrite dispatch-fn]
  (if-not (is-rs-type full-file-name)
    ;; By default, we pass 'false' for the overwrite arg
    (call-api-async (fn [] (.saveKdbx okp-db-service full-file-name overwrite)) dispatch-fn :error-transform true)
    (bg-rs/save-kdbx full-file-name overwrite dispatch-fn)))

;; Deprecate
#_(defn categories-to-show [db-key dispatch-fn]
    (invoke-api "categories_to_show" {:db-key db-key} dispatch-fn))

;; Works for both iOS and Android
(defn copy-to-clipboard
  "Called to copy a selected field value to clipboard
   The arg field-info is a map that statifies the enum member 
   ClipboardCopyArg {field_name,field_value,protected,cleanup_after}

   IMPORTANT:The field 'cleanup_after' has clipboard timeout in seconds and 0 sec menas no timeout
   "
  [field-info dispatch-fn]
  (invoke-api "clipboard_copy_string" field-info dispatch-fn))

(defn combined-category-details
  [db-key grouping-kind dispatch-fn]
  (invoke-api "combined_category_details" {:db-key db-key
                                           :grouping-kind grouping-kind} dispatch-fn))

(defn transform-request-entry-category
  "Called to convert entry-category to the deserilaizable format expected as in EnteryCategory"
  [entry-category]
  (if (map? entry-category)
    (->> entry-category (cske/transform-keys csk/->camelCaseString))
    (csk/->camelCaseString entry-category)))

(defn entry-summary-data
  "Gets the list of entry summary data for a given entry category as defined in EnteryCategory in 'db_service.rs'. 
  The args the db-key and the entry-category 
  (AllEntries, Favorites,Deleted, {:group \"some valid group uuid\"}  {:entrytype \"Login\"}).
  The entry-category should have a value that can be converted to the enum  EnteryCategory
  The serilaization expect the enum name as camelCase 
  For now the valid enums are 
  `allEntries`, `favourites`, `deleted`, `{group \"valid uuid\"}`, `{entryTypeUuid \"valid uuid\"}`
  and we need convert the incoming entry-category to this enum
  "
  [db-key entry-category dispatch-fn]
  ;; Need to enusre that entry-category is not nil
  ;; Otherwise, csk/->camelCaseString will fail with 'ERROR  Error: Doesn't support name:'
  ;; Note the use of db_key,entry_category as keys in the args map passed to api 
  ;; As we have done all api args conversion outside here, we need to disable further conversion 
  ;; with convert-request false
  (invoke-api "entry_summary_data"
              {:db_key db-key
               :entry_category (transform-request-entry-category entry-category)}
              dispatch-fn
              :convert-request false))

(defn close-kdbx [db-key dispatch-fn]
  (invoke-api "close_kdbx" {:db-key db-key} dispatch-fn))

(defn save-conflict-resolution-cancel [db-key dispatch-fn]
  (invoke-api "save_conflict_resolution_cancel" {:db-key db-key} dispatch-fn))

(defn unlock-kdbx
  "Calls the API to unlock the previously opened db file.
   Calls the dispatch-fn with the received map of type 'KdbxLoaded' 
  "
  [db-key password key-file-name dispatch-fn]
  (invoke-api "unlock_kdbx" {:db-file-name db-key
                             :password password
                             :key-file-name key-file-name
                             :biometric-auth-used false} dispatch-fn))

;; See authenticate-with-biometric api above

(defn unlock-kdbx-on-biometric-authentication [db-key dispatch-fn]
  (invoke-api "unlock_kdbx_on_biometric_authentication" {:db-key db-key} dispatch-fn))

(defn stored-db-credentials-on-biometric-authentication [db-key dispatch-fn]
  (invoke-api "stored_db_credentials" {:db-key db-key} dispatch-fn))

(defn remove-from-recently-used
  "Removes recently used file info for the passed db-key, removes all files that were created for this db 
   and the database is closed automatically in the backend"
  [db-key dispatch-fn]
  (invoke-api "remove_from_recently_used" {:db-key db-key} dispatch-fn))

(defn new-blank-group [dispatch-fn]
  (invoke-api "new_blank_group" {:mark-as-category true} dispatch-fn))

(defn insert-group [db-key group dispatch-fn]
  (let [args  {:db-key db-key :group group}]
    (invoke-api "insert_group" args dispatch-fn)))

(defn update-group [db-key group dispatch-fn]
  (let [args  {:db-key db-key :group group}]
    (invoke-api "update_group" args dispatch-fn)))

(defn transform-response-groups-summary [response]
  (let [keys_exclude  (-> response (get "ok") (get "groups") keys vec)
        t-fn (fn [k]
               (if (contains-val? keys_exclude k)
                 k
                 (csk/->kebab-case-keyword k)))]
    (cske/transform-keys t-fn response)))

(defn groups-summary-data [db-key dispatch-fn]
  (invoke-api  "groups_summary_data" {:db-key db-key} dispatch-fn :convert-response-fn transform-response-groups-summary))

#_(defn- transform-response-entry-keys
    "All keys in the incoming raw entry map from backend will be transformed
  using custom key tramsformer
   "
    [response]
    (let [entry (-> response (get "ok"))
          keys-exclude (-> entry (get "section_fields") keys vec)
          keys-exclude (into keys-exclude (->  entry (get "parsed_fields") keys vec))
          t-fn (fn [k]
                 (if (contains-val? keys-exclude k)
                   k
                   (csk/->kebab-case-keyword k)))]
      (cske/transform-keys t-fn response)))

(declare transform-response-entry-form-data)

(defn new-entry-form-data [db-key entry-type-uuid dispatch-fn]
  (invoke-api "new_entry_form_data" {:db-key db-key
                                     :entry-type-uuid entry-type-uuid
                                     :parent-group-uuid nil} dispatch-fn :convert-response-fn transform-response-entry-form-data))


(defn- transform-resquest-entry-form-data
  "All keys in the incoming entry form data map from UI will be transformed using a custom key transformer
   "
  [entry-form-data]
  (let [keys_exclude (->  entry-form-data :section-fields keys vec)
        ;; _ (println "keys_exclude are " keys_exclude)
        t-fn (fn [k]
               (if (contains-val? keys_exclude k)
                 k
                 (csk/->snake_case k)))]
    (cske/transform-keys t-fn entry-form-data)))

(defn insert-entry [db-key entry-form-data dispatch-fn]
  (invoke-api "insert_entry_from_form_data"
              {:db_key db-key
               :form_data (transform-resquest-entry-form-data entry-form-data)} dispatch-fn :convert-request false))

(defn update-entry [db-key entry-form-data dispatch-fn]
  (invoke-api "update_entry_from_form_data"
              {:db_key db-key
               :form_data (transform-resquest-entry-form-data entry-form-data)} dispatch-fn :convert-request false))

(defn- transform-response-entry-form-data
  "
  The response is a map with keys 'ok' and 'error'
  All keys in the incoming raw EntryFormData map (found if there is no 'error' ) from backend will be transformed
  using a custom key tramsformer "
  [response]
  (let [;; Get the entry data from "ok" key of the response
        entry-form-data (get response "ok")
        keys-exclude (->  entry-form-data (get "section_fields") keys vec)
        keys-exclude (into keys-exclude (->  entry-form-data (get "parsed_fields") keys vec))
        t-fn (fn [k]
               (if (contains-val? keys-exclude k)
                 k
                 (csk/->kebab-case-keyword k)))]
    (cske/transform-keys t-fn response)))

(defn find-entry-by-id [db-key entry-uuid dispatch-fn]
  (invoke-api "get_entry_form_data_by_id" {:db_key db-key :uuid entry-uuid} dispatch-fn :convert-response-fn transform-response-entry-form-data))

(defn find-group-by-id [db-key group-uuid dispatch-fn]
  (invoke-api "get_group_by_id" {:db-key db-key :uuid group-uuid} dispatch-fn))

(defn entry-type-headers
  "Gets all entry types header information that are available. 
   Returns a map that has standard and custom entry type names separately. 
   See EntryTypeHeasders struct
  "
  [db-key dispatch-fn]
  (invoke-api "entry_type_headers" {:db-key db-key} dispatch-fn))

(defn history-entries-summary [db-key entry-uuid dispatch-fn]
  (invoke-api "history_entries_summary" {:db-key db-key :uuid entry-uuid} dispatch-fn))

(defn history-entry-by-index [db-key entry-uuid index dispatch-fn]
  (invoke-api "history_entry_by_index" {:db-key db-key :uuid entry-uuid :index index}
              dispatch-fn :convert-response-fn transform-response-entry-form-data))

(defn delete-history-entries [db-key entry-uuid dispatch-fn]
  (invoke-api "delete_history_entries" {:db-key db-key :uuid entry-uuid} dispatch-fn))

(defn delete-history-entry-by-index [db-key entry-uuid index dispatch-fn]
  (invoke-api "delete_history_entry_by_index" {:db-key db-key :uuid entry-uuid :index index} dispatch-fn))

(defn move-entry-to-recycle-bin [db-key entry-uuid dispatch-fn]
  (invoke-api "move_entry_to_recycle_bin" {:db-key db-key :uuid entry-uuid} dispatch-fn))

(defn move-entry
  [db-key entry-uuid new-parent-id dispatch-fn]
  (invoke-api "move_entry" {:db-key db-key :uuid entry-uuid :new-parent-id new-parent-id} dispatch-fn))

(defn move-group
  [db-key group-uuid new-parent-id dispatch-fn]
  (invoke-api "move_group" {:db-key db-key :uuid group-uuid :new-parent-id new-parent-id} dispatch-fn))

(defn move-group-to-recycle-bin [db-key group-uuid dispatch-fn]
  (invoke-api "move_group_to_recycle_bin" {:db-key db-key :uuid group-uuid} dispatch-fn))

;; Not used for now; Kept for later use
#_(defn move-group
    [db-key group-uuid new-parent-id dispatch-fn]
    (invoke-api "move_group" {:db-key db-key :uuid group-uuid :new-parent-id new-parent-id} dispatch-fn))

(defn remove-entry-permanently [db-key entry-uuid dispatch-fn]
  (invoke-api "remove_entry_permanently" {:db-key db-key :uuid entry-uuid} dispatch-fn))

;; Not used for now; Kept for later use
#_(defn remove-group-permanently [db-key group-uuid dispatch-fn]
    (invoke-api "remove_group_permanently" {:db-key db-key :uuid group-uuid} dispatch-fn))

(defn empty-trash [db-key dispatch-fn]
  (invoke-api "empty_trash" {:db-key db-key} dispatch-fn))


(defn get-db-settings [db-key dispatch-fn]
  (invoke-api "get_db_settings" {:db-key db-key} dispatch-fn))


(defn set-db-settings [db-key db-settings dispatch-fn]
  (invoke-api "set_db_settings" {:db_key db-key :db_settings db-settings} dispatch-fn))

(defn search-term
  [db-key term dispatch-fn]
  (invoke-api "search_term" {:db-key db-key :term term} dispatch-fn))

(defn analyzed-password [password-options dispatch-fn]
  (invoke-api "analyzed_password" {:password-options password-options} dispatch-fn))

(defn generate-password-phrase [pass-phrase-options dispatch-fn]
  (invoke-api "generate_password_phrase" {:pass-phrase-options pass-phrase-options} dispatch-fn))

;; Not used for now; Kept for later use
#_(defn recently-used-dbs-info [dispatch-fn]
    (invoke-api "recently_used_dbs_info" {} dispatch-fn))

(defn app-preference [dispatch-fn]
  (invoke-api "app_preference" {} dispatch-fn))

(defn get-file-info [full-file-name-uri dispatch-fn]
  (invoke-api "get_file_info" {:db-key full-file-name-uri} dispatch-fn))

(defn prepare-export-kdbx-data [full-file-name-uri dispatch-fn]
  (invoke-api "prepare_export_kdbx_data" {:db-key full-file-name-uri} dispatch-fn))

(defn collect-entry-group-tags [db-key dispatch-fn]
  (invoke-api  "collect_entry_group_tags" {:db-key db-key} dispatch-fn))

(defn list-key-files
  "Gets all previously copied key files"
  [dispatch-fn]
  (invoke-api "list_key_files" {} dispatch-fn))

(defn delete-key-file
  "Deletes any specfic key file"
  [file-name dispatch-fn]
  ;; This api call make use of 'CommandArg::GenericArg' and accordingly we need to ensure
  ;; we pass the expected arg name 'file_name' with non null value
  ;; The Arg map to this api is not transformed automaticlly as done typically.
  ;; Note the use of snakecase convention for 'key_vals' and 'file_name' as expected by serde conversion
  (invoke-api "delete_key_file"  {:key_vals {"file_name" file-name}} dispatch-fn :convert-request false))

(defn generate-key-file
  "Arg file-name is just the key file name part with .keyx suffix"
  [file-name dispatch-fn]
  (invoke-api "generate_key_file"  {:key_vals {"file_name" file-name}} dispatch-fn :convert-request false))

(defn save-attachment-to-view
  "Called to save an entry's attachment to a temp file"
  [db-key name data-hash-str dispatch-fn]
  (invoke-api "save_attachment_as_temp_file" {:db-key db-key :name name :data-hash-str data-hash-str} dispatch-fn :no-response-conversion true))

(defn update-db-session-timeout [db-session-timeout dispatch-fn]
  (invoke-api "update_session_timeout" {:timeout_type 1, :db-session-timeout db-session-timeout} dispatch-fn))

(defn update-clipboard-timeout [clipboard-timeout dispatch-fn]
  (invoke-api "update_session_timeout" {:timeout_type 1,:clipboard-timeout clipboard-timeout} dispatch-fn))

(defn update-preference [preference-data dispatch-fn]
  (invoke-api "update_preference" {:preference-data preference-data} dispatch-fn))

(defn set-db-open-biometric [db-key db-open-enabled dispatch-fn]
  (invoke-api "set_db_open_biometric" {:db-key db-key :db-open-enabled db-open-enabled} dispatch-fn))

(defn load-language-translations [language-ids dispatch-fn]
  (invoke-api "load_language_translations" {:language-ids language-ids} dispatch-fn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  Auto open ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn resolve-auto-open-properties
  "Called to resolve the auto open properties before opening the child database
   The arg auto-open-properties is a map from struct AutoOpenProperties

   The reolved props is returned as a map (struct AutoOpenPropertiesResolved) in the dispatch-fn 
  "
  [auto-open-properties dispatch-fn]
  (invoke-api "resolve_auto_open_properties" {:auto-open-properties auto-open-properties} dispatch-fn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  App lock    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn pin-entered [pin dispatch-fn]
  (invoke-api "pin_entered" {:pin pin} dispatch-fn))

(defn pin-verify [pin dispatch-fn]
  (invoke-api "pin_verify" {:pin pin} dispatch-fn))

(defn pin-removed [dispatch-fn]
  (invoke-api "pin_removed" {} dispatch-fn))

(defn app-reset [dispatch-fn]
  (invoke-api "app_reset" {} dispatch-fn))

;;;;;;;;;;;;;;;;;;;;;;;;; OTP, Timer etc  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-timeout [period-in-milli-seconds timer-id dispatch-fn]
  (invoke-api "set_timeout" {:period-in-milli-seconds period-in-milli-seconds :timer-id timer-id} dispatch-fn))

(defn start-polling-entry-otp-fields [db-key entry-uuid otp-fields dispatch-fn]
  ;; otp-fields is a map where keys are otp field names and values are a map with keys [ttl period]
  (let [exclude-keys (-> otp-fields keys vec)]
    ;; exclude-keys is vec of all otp field names and we use exclude-keys from not transforming these keys
    (invoke-api "start_polling_entry_otp_fields"
                {:db-key db-key
                 :entry-uuid entry-uuid
                 :otp-fields {:token-ttls otp-fields}}
                dispatch-fn
                ;; keys in the passed args map are transformed except those in this exclude vec
                :args-keys-excluded exclude-keys)))

(defn stop-polling-all-entries-otp-fields [_db-key dispatch-fn]
  #_(invoke-api "stop_polling_all_entries_otp_fields" {:db-key db-key} dispatch-fn)
  (invoke-api "stop_polling_all_entries_otp_fields" {} dispatch-fn))

(defn form-otp-url
  "The arg 'otp-settings' is map with secret-or-url,  eg {:secret-or-url \"base32secret3232\"}"
  [otp-settings dispatch-fn]
  (invoke-api "form_otp_url" {:otp-settings otp-settings} dispatch-fn :convert-request true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  Camera static methods calls ;;;;;;;;;;;

;; Reagent component 'camera' from 'react-native-vision-camera - VisionCamera' is defined in 'rn-components.cljs'

;; Need to use (js/Object.getOwnPropertyNames camera-obj) in repl to find all static methods 
;; defined in 'Camera'
(def camera-obj (.-Camera ^js/RNVisionCamera rn-vision-camera))

(defn camera-permission-status
  "
  Returns one of 'granted' | 'not-determined' | 'denied' | 'restricted'

  not-determined: Your app has not yet requested permission from the user. Continue by calling the request functions.

  denied: Your app has already requested permissions from the user, but was explicitly denied. 
  You cannot use the request functions again, 
  but you can use the Linking API to redirect the user to the Settings App where he can manually grant the permission.

  restricted: Your app cannot use the Camera or Microphone because that 
  functionality has been restricted, possibly due to active restrictions such as parental controls being in place
  "
  []
  (.getCameraPermissionStatus camera-obj))

;; See https://react-native-vision-camera.com/docs/api/classes/Camera#requestcamerapermission
;; https://react-native-vision-camera.com/docs/guides#requesting-permissions
(defn request-camera-permission
  "The static method .requestCameraPermission returns a promise and it is resolved in call-api-async
  
  By using :no-response-conversion, the resolved value is returned as {:ok resolved-value}
  The resolved value is either 'granted' or 'denied' 
  
  granted: The app is authorized to use said permission. Continue with using the <Camera> view.

  denied: The user explicitly denied the permission request alert. 
  You cannot use the request functions again, but you can use 
  the Linking API to redirect the user to the Settings App where he can manually grant the permission.

  restricted: The app cannot use the Camera or Microphone because that functionality has been restricted, 
  possibly due to active restrictions such as parental controls being in place
  "
  [dispatch-fn]
  (call-api-async (fn [] (.requestCameraPermission camera-obj)) dispatch-fn :no-response-conversion true))

(defn available-cameras
  "Returns a list of all available camera devices on the current phone"
  []
  (-> (.getAvailableCameraDevices camera-obj) js->clj))

;; Used for Android FOSS release only
;; 'CameraView' is the native module name that was taken from the package's platform specifics code 
;; Android https://github.com/mrousavy/react-native-vision-camera/blob/v3.9.0/package/android/src/main/java/com/mrousavy/camera/CameraViewManager.kt
;; iOS https://github.com/mrousavy/react-native-vision-camera/blob/147aff8683b6500ede825c4c06d27110af7a0654/package/ios/CameraViewManager.m#L14
(def camera-view-nm (.-CameraView ^js/RnCameraView rn/NativeModules))

(defn is-rn-native-camera-vison-disabled
  "Checks whether the camera vision's native modules are present or not
  As native-camera-vison package uses some property components (see below), we need to exclude 
  the use and inclusion of this package (see react-native.config.js) for APK release meant to be fully FOSS 
  https://developers.google.com/ml-kit/
  https://firebase.google.com/
  https://developers.google.com/android/reference/com/google/android/gms/package-summary
   "
  []
  ;;camera-view-nm should be non nil value for iOS and Android play store release
  (nil? camera-view-nm))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Native Events ;;;;;;;;;;;;;;;;;;;;;;;;;
;; While compiling with advanced option, if we use '(u/is-iOS)' to check platform
;; in any place, the call gets optimized with false constant value resulting all calls that 
;; use such check to use platform specific code failed   

;; For example,  the creation of 'OkpEvents' did not happen for '(util/is-iOS)'
;; This in turn leading to error in case of iOS
;; Invariant Violation: `new NativeEventEmitter()` requires a non-null argument
;; The following dummy function definition ensures that, type hint ^js/OkpEvents work
;; and also  (rn/NativeEventEmitter. okp-events) is called for iOS 

;; A dummy function definition for type inference of ^js/OkpEvents to work
(defn check-okp-event []
  (try
    (.getConstants okp-events)
    (catch js/Object e
      (js/console.log e))))

;; See comments above
;; We need to use this instead of Platform.OS
(defn platform-os []
  (if (not (nil? okp-events))
    "ios"
    "android"))

;; See comments above
(defn is-iOS []
  (= (platform-os) "ios"))

;; See comments above
(defn is-Android []
  (= (platform-os) "android"))

(defn get-emitter []
  (if (not (nil? okp-events))
    (rn/NativeEventEmitter. okp-events)
    (rn/NativeEventEmitter.)))

(def event-emitter ^js/NativeEventEmitter (get-emitter))

;; See the comments above why this is not used even though this works when we compile with simple option
#_(def event-emitter ^js/NativeEventEmitter (if (u/is-iOS)
                                              (rn/NativeEventEmitter. okp-events)
                                              (rn/NativeEventEmitter.)))

(def ^:private native-event-listeners
  "This is a map where keys are the event name (kw) and values the native listeners" (atom {}))

(defn register-event-listener
  "Called to register an event handler to listen for a named event message emitted in the backend service 
   event-name is the event name kw 
   event-handler-fn is the handler function
   "
  ([caller-name event-name event-handler-fn]
   ;;(println (js/Date.) "caller-name event-name event-handler-fn " caller-name event-name event-handler-fn)
   (let [el (get @native-event-listeners [caller-name event-name])]
     ;;Register the event handler function only if the '[caller-name event-name]' is not already registered
     (when (nil? el)
       (try
         (let [subscription (.addListener event-emitter event-name event-handler-fn)]
           (swap! native-event-listeners assoc [caller-name event-name] subscription))
         (catch js/Error err
           (do
             (println "error is " (ex-cause err))
             (js/console.log (ex-cause err)))))
       ;;To log the following messsage use 'if' instead of 'when' form
       #_(println "Tauri event listener for " event-name " is already registered"))))
  ([event-name event-handler-fn]
   (register-event-listener :common event-name event-handler-fn)))

;; Not used for now; Kept for later use
#_(defn unregister-event-listener
    "Unregisters the previously registered event handler"
    ([caller-name event-name]
     (let [subscription (get @native-event-listeners [caller-name event-name])]
       (if (nil? subscription)
         (println "No existing listener found for the event name " event-name " and unlisten is not called")
         (do
           (.remove subscription)
           (swap! native-event-listeners assoc [caller-name event-name] nil)))))
    ([event-name]
     (unregister-event-listener :common event-name)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  (require '[cljs.pprint]) ;;https://cljs.github.io/api/cljs.pprint/
  (cljs.pprint/pprint someobject)
  ;; daf114d0-a518-4e13-b75b-fbe893e69a9d 8bd81fe1-f786-46c3-b0e4-d215f8247a10

  (in-ns 'onekeepass.mobile.background)

  (.getConstants okp-db-service)

  (android-invoke-api "test_call" {} #(println %))

  (re-frame.core/dispatch [:common/update-page-info {:page :home :title "Welcome"}])

  (defn test-call [dispatch-fn]
    (call-api-async #(.kdbxUriToOpenOnCreate okp-db-service) dispatch-fn
                    :no-response-conversion true :error-transform false))

  (def db-key (-> @re-frame.db/app-db :current-db-file-name))

  (load-language-translations ["en"] #(println %))

  ;; Use this in repl before doing the refresh in metro dev server, particularly when async services
  ;; are sending events to the front end via rust middle layer -see 'init_async_listeners'
  ;; This ensures no active messages are from backend async loops
  ;; Otherwise, we may see error "*** Assertion failure in -[RCTEventEmitter sendEventWithName:body:]()," in xcode logs
  ;; In case of Android, this is not an issue
  (invoke-api  "shutdown_async_services" {} #(println %))

  (invoke-api "get_file_info" {:db-key db-key} #(println %))

  (invoke-api  "all_kdbx_cache_keys" {} #(println %))

  (invoke-api "clean_export_data_dir" {} #(println %))

  ;;(invoke-api  "list_backup_files" {} #(println %))

  (invoke-api  "list_bookmark_files" {} #(println %)))