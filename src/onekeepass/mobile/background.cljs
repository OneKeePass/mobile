(ns onekeepass.mobile.background
  (:require
   [react-native :as rn]
   ["@react-native-clipboard/clipboard" :as rnc-clipboard]
   [cljs.core.async :refer [go]]
   [cljs.core.async.interop :refer-macros [<p!]]
   [onekeepass.mobile.utils :as u :refer [contains-val?]]
   [camel-snake-kebab.extras :as cske]
   [camel-snake-kebab.core :as csk]))

(set! *warn-on-infer* true)

;; Use .getString and .setString of native clipboard. 
;; The .getString call returns a Promise
(def clipboard (.-default ^js/ClipboardDefault rnc-clipboard))

(defn write-string-to-clipboard [s]
  (.setString clipboard s))

;; Not ussed for now
#_(defn read-string-from-clipboard [callback-fn]
    (go (try
          (let [s (<p! (.getString clipboard))]
            (callback-fn s))
          (catch js/Error err
            (js/console.log (ex-cause err))
            (callback-fn nil)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def okp-db-service ^js/OkpDbService (.-OkpDbService rn/NativeModules))

(def okp-document-pick-service ^js/OkpDocumentPickerService (.-OkpDocumentPickerService rn/NativeModules))

(def rn-boot-splash ^js/RNBootSplash (.-RNBootSplash rn/NativeModules))

(def okp-export ^js/OkpExport (.-OkpExport rn/NativeModules))

(def okp-events ^js/OkpEvents (.-OkpEvents rn/NativeModules))

#_(defn get-constants []
    (.getConstants okp-db-service))

(defn is-biometric-available []
  ;; Valid values expected for BiometricAvailable are "true" or "false" for both iOS and Android
  (= (-> okp-db-service .getConstants .-BiometricAvailable) "true"))

(defn- api-args->json
  [api-args convert-request]
  (if convert-request
    ;; changes all keys of args map to snake_case (e.g db-key -> db_key, ...)
    (->> api-args (cske/transform-keys csk/->snake_case) clj->js (.stringify js/JSON))
    ;; Here the api-args map has all its keys of 'snake_case' form as they converted in the calling
    ;; fn itself
    (->>  api-args clj->js  (.stringify js/JSON))))

(defn transform-api-response
  "Transforms the resolved value and returns a result map with key :ok or :error"
  [resolved {:keys [convert-response
                    convert-response-fn
                    no-response-conversion]
             :or {convert-response true
                  no-response-conversion false}}]
  ;; A typical resolved response is of form {"ok" {}, "error" nil} or {"ok" nil, "error" "someerror value"}
  ;; e.g {"ok" {"k1" "v1", "k2" "v1"}, "error" nil} 
  ;; May need to allow the caller to do the transformation as done in desktop application
  ;; The transformation fn need to extract the successful value from "ok" and do the custom conversion
  (cond
    no-response-conversion
    {:ok resolved}

    (not (nil? convert-response-fn))
    (->> resolved (.parse js/JSON) js->clj convert-response-fn) ;; custom transformer of response

    convert-response
    (->> resolved (.parse js/JSON) js->clj (cske/transform-keys csk/->kebab-case-keyword))

    :else
    (->> resolved (.parse js/JSON) js->clj)))

(def test-data (atom nil))

(defn call-api-async
  "Calls the backend APIs asynchronously
   aync-fn returns a Promise
   dispatch-fn is called when a promise is resolve or rejected 
  "
  [aync-fn dispatch-fn &
   {:keys [error-transform]
    :or {error-transform false}
    :as opts}]
  (go
    (try
      (let [r (<p! (aync-fn))
            deserialized-response (transform-api-response r opts)]
        ;;(reset! test-data r)
        (dispatch-fn deserialized-response))
      (catch js/Error err
        (do

          (reset! test-data err)

          ;; (println "type of err is " (type err))
          ;; (println "type of (ex-cause err) is " (type (ex-cause err))) 
          ;; (println "(ex-data err) is " (ex-data err))
          ;; (println (js/Object.keys (ex-cause err)))

          ;; (type err) is #object[cljs$core$ExceptionInfo], an instance of ExceptionInfo 
          ;; (type (ex-cause err)) is #object[Error], an instance of js/Error

          ;; The RN err object keys are (in both iOs and Android) - can be seen using '(js/Object.keys (ex-cause err)'
          ;; #js [message data cause name description number fileName lineNumber columnNumber stack]
          ;; (ex-cause err) keys are #js [code message domain userInfo nativeStackIOS]

          ;;Call the dispatch-fn with any error returned by the back end API
          (dispatch-fn {:error (cond
                                 (nil? (ex-cause err))
                                 (if (not (nil? (.-message err)))
                                   (.-message err)
                                   err)

                                 ;; When we reject the promise in native module, we get a detailed
                                 ;; object as described above comments
                                 ;; iOS error has :domain :userInfo keys 
                                 ;; Android not sure on that 
                                 error-transform
                                 (-> err ex-cause u/jsx->clj (select-keys [:code :message]))

                                 :else
                                 (ex-cause err))})
          (js/console.log (ex-cause err)))))))

(defn invoke-api
  "Called to invoke commands from ffi
  "
  [name api-args dispatch-fn &
   {:keys [convert-request convert-response convert-response-fn]
    :or {convert-request true convert-response true}}]
  (call-api-async (fn [] (.invokeCommand okp-db-service name (api-args->json api-args convert-request)))
                  dispatch-fn :convert-response convert-response :convert-response-fn convert-response-fn))

(defn hide-splash
  "Calls the native module RNBootSplash's hide method directly
   We are not using the JS api from RNBootSplash
   "
  [dispatch-fn]
  (call-api-async (fn [] (.hide rn-boot-splash 200)) dispatch-fn))

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
;; In case of iOS, we are using the 'pick-and-save-new-kdbxFile' fn
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

(defn- request-argon2key-transformer
  "A custom transformer that transforms a map that has ':Argon2' key "
  [new-db]
  (let [t (fn [k] (if (= k :Argon2)
                    ;;retains the key as :Argon2 instead of :argon-2
                    :Argon2
                    (csk/->snake_case k)))]
    (cske/transform-keys t new-db)))

;; This is used specifically in iOS. Here the call sequence - pickAndSaveNewKdbxFile,readKdbx - is used
;; This works for Local,iCloud. But the cases of GDrive, OneDrive, the new database files are created
;; But we get 'COORDINATOR_CALL_FAILED' error 'Couldn’t communicate with a helper application'
;; As the kdbx is file is created succesully, we can open the database
;;
;; May need to explore the use of UIActivityViewController based export support. Using this the newly crated temp db file
;; may be saved to another app and then open from that. Someting similar to 'ACTION_SEND'
(defn pick-and-save-new-kdbxFile 
  "Called to show a document pick view"
  [file-name new-db dispatch-fn]
  (call-api-async (fn [] (.pickAndSaveNewKdbxFile
                          okp-document-pick-service file-name
                          ;; Explicit conversion of the api args to json here
                          (api-args->json {:new_db (request-argon2key-transformer new-db)} false)))
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

;; This works for both iOS and Android
(defn pick-key-file-to-copy
  "Called to pick a kdbx file using platform specific File Manager. The 'dispatch-fn' called with a full uri
   of a file picked and the file is read and loaded in the subsequent call
   "
  [dispatch-fn]
  (call-api-async (fn []
                    (.pickKeyFileToCopy okp-document-pick-service))
                  dispatch-fn
                  :error-transform true))

;; This works for both iOS and Android
(defn pick-key-file-to-save 
  "Called to save the selected key file to a user chosen location
   The arg 'full-file-name' is the fey file absolute path
   The arg 'file-name' is the just key file name part
   "
  [full-file-name file-name dispatch-fn] 
  (call-api-async (fn [] (.pickKeyFileToSave okp-document-pick-service full-file-name file-name)) 
                  dispatch-fn :error-transform true))

;;;;;;;;

(defn ios-pick-on-save-error-save-as
  "Called to present os specific view for the user to save the copied kdbx file"
  [kdbx-file-name db-key dispatch-fn]
  (call-api-async (fn [] (.pickOnSaveErrorSaveAs 
                          okp-document-pick-service 
                          kdbx-file-name db-key)) 
                  dispatch-fn 
                  :error-transform true))

(defn ios-complete-save-as-on-error [db-key new-db-key dispatch-fn]
  (call-api-async (fn [] (.completeSaveAsOnError 
                          okp-db-service
                          (api-args->json {:db-key db-key :new-db-key new-db-key} true))) 
                  dispatch-fn))

;; 
(defn android-pick-on-save-error-save-as [kdbx-file-name dispatch-fn] 
  (pick-document-to-create kdbx-file-name dispatch-fn))

(defn android-complete-save-as-on-error [db-key new-db-key dispatch-fn]
  (call-api-async (fn [] (.completeSaveAsOnError okp-db-service db-key new-db-key)) dispatch-fn :error-transform true))

;;;;;;

(defn copy-key-file 
  "After user picks up a file to use as Key File, this api is called to copy to a dir inside the app"
  [full-file-name dispatch-fn]
  (call-api-async (fn [] (.copyKeyFile okp-db-service full-file-name)) dispatch-fn :error-transform true))

(defn create-kdbx
  "Called with full file name uri that was picked by the user through the document picker 
   and new db related info in a map
   Used only in case of Android. See comments in pick-document-to-create and pick-and-save-new-kdbxFile
   "
  [full-file-name new-db dispatch-fn]
  (call-api-async (fn [] (.createKdbx okp-db-service full-file-name
                                       ;; Note the use of snake_case for all keys and false as convert-request value
                                      (api-args->json {:new_db (request-argon2key-transformer new-db)} false)))
                  dispatch-fn :error-transform true))

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

(defn load-kdbx [db-file-name password key-file-name dispatch-fn]
  (call-api-async (fn []
                    (.readKdbx okp-db-service
                               db-file-name
                               (api-args->json {:db-file-name db-file-name :password password :key_file_name key-file-name} true)))
                  dispatch-fn :error-transform true))

(defn save-kdbx [full-file-name overwrite dispatch-fn]
  ;; By default, we pass 'false' for the overwrite arg
  (call-api-async (fn [] (.saveKdbx okp-db-service full-file-name overwrite)) dispatch-fn :error-transform true))

(defn categories-to-show [db-key dispatch-fn]
  (invoke-api "categories_to_show" {:db-key db-key} dispatch-fn))

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

(defn unlock-kdbx
  "Calls the API to unlock the previously opened db file.
   Calls the dispatch-fn with the received map of type 'KdbxLoaded' 
  "
  [db-key password key-file-name dispatch-fn]
  (invoke-api "unlock_kdbx" {:db-file-name db-key
                             :password password
                             :key-file-name key-file-name} dispatch-fn))

;; See authenticate-with-biometric api above

(defn unlock-kdbx-on-biometric-authentication [db-key dispatch-fn]
  (invoke-api "unlock_kdbx_on_biometric_authentication" {:db-key db-key} dispatch-fn))

(defn remove-from-recently-used
  "Removes recently used file info for the passed db-key and the database id also 
   closed automatically in the backend"
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

(defn- transform-response-entry-keys
  "All keys in the incoming raw entry map from backend will be transformed
  using custom key tramsformer
   "
  [response]
  (let [keys_exclude (-> response (get "ok") (get "section_fields") keys vec)
        t-fn (fn [k]
               (if (contains-val? keys_exclude k)
                 k
                 (csk/->kebab-case-keyword k)))]
    (cske/transform-keys t-fn response)))

(defn new-entry-form-data [db-key entry-type-uuid dispatch-fn]
  (invoke-api "new_entry_form_data" {:db-key db-key
                                     :entry-type-uuid entry-type-uuid
                                     :parent-group-uuid nil} dispatch-fn :convert-response-fn transform-response-entry-keys))


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
        keys_exclude (->  entry-form-data (get "section_fields") keys vec)
        t-fn (fn [k]
               (if (contains-val? keys_exclude k)
                 k
                 (csk/->kebab-case-keyword k)))]
    (cske/transform-keys t-fn response)))

(defn find-entry-by-id [db-key entry-uuid dispatch-fn]
  (invoke-api "get_entry_form_data_by_id" {:db_key db-key :uuid entry-uuid} dispatch-fn :convert-response-fn transform-response-entry-form-data))

(defn find-group-by-id [db-key group-uuid dispatch-fn]
  (invoke-api "get_group_by_id" {:db-key db-key :uuid group-uuid} dispatch-fn))

(defn entry-type-headers
  "Gets all entry types header information that are avaiable. 
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

(defn- handle-argon2-renaming
  "A custom transform fuction to make sure Argon2 is converted to :Argon2 not converted to :argon-2 so that 
  we can keep same as generated by serde 
  "
  [data]
  (let [t (fn [k] (if (= k "Argon2")
                    :Argon2
                    (csk/->kebab-case-keyword k)))]
    ;; transform-keys walks through the keys of a map using the transforming fn passed as first arg 
    ;; see https://github.com/clj-commons/camel-snake-kebab/blob/version-0.4.3/src/camel_snake_kebab/extras.cljc
    (cske/transform-keys t data)))

(defn get-db-settings [db-key dispatch-fn]
  ;; Note: With regard to reading and writing of DbSettings, we use custom conversion from json to clojure data and back
  ;; It is possible to use just custom converter only in 'set-db-settings' by using the default response converted :argon-2 in clojure code
  ;; Then convert only :argon-2 to :Argon2 before calling 'set-db-settings'
  (invoke-api "get_db_settings" {:db-key db-key} dispatch-fn :convert-response-fn handle-argon2-renaming))

(defn set-db-settings [db-key db-settings dispatch-fn]
  ;; We keep Argon2 as expected by tauri api serde conversion for DbSettings struct (serde expects 'snake_case' fields of DbSettings)
  ;; The default csk/->snake_case converts Argon2 to argon_2 
  (let [t (fn [k] (if (= k :Argon2) k (csk/->snake_case k)))
        converted  (cske/transform-keys t db-settings)]
    (invoke-api "set_db_settings" {:db_key db-key :db_settings converted} dispatch-fn :convert-request false)))

(defn search-term
  [db-key term dispatch-fn]
  (invoke-api "search_term" {:db-key db-key :term term} dispatch-fn))

(defn analyzed-password [password-options dispatch-fn]
  (invoke-api "analyzed_password" {:password-options password-options} dispatch-fn))

;; Not used for now; Kept for later use
#_(defn recently-used-dbs-info [dispatch-fn]
    (invoke-api "recently_used_dbs_info" {} dispatch-fn))

(defn app_preference [dispatch-fn]
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  (require '[cljs.pprint]) ;;https://cljs.github.io/api/cljs.pprint/
  (cljs.pprint/pprint someobject)
  ;; daf114d0-a518-4e13-b75b-fbe893e69a9d 8bd81fe1-f786-46c3-b0e4-d215f8247a10

  (in-ns 'onekeepass.mobile.background)

  (re-frame.core/dispatch [:common/update-page-info {:page :home :title "Welcome"}])

  (defn test-call [dispatch-fn]
    (call-api-async #(.kdbxUriToOpenOnCreate okp-db-service) dispatch-fn
                    :no-response-conversion true :error-transform false))

  (def db-key (-> @re-frame.db/app-db :current-db-file-name))

  (invoke-api "get_file_info" {:db-key db-key} #(println %))

  (invoke-api  "all_kdbx_cache_keys" {} #(println %))

  (invoke-api "clean_export_data_dir" {} #(println %))

  (invoke-api  "list_backup_files" {} #(println %))

  (invoke-api  "list_bookmark_files" {} #(println %)))