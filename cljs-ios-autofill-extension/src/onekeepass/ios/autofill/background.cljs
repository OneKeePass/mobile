(ns onekeepass.ios.autofill.background
  "All backend api calls that are used across many events"
   (:require
    [react-native :as rn] 
    [onekeepass.ios.autofill.utils :as u :refer [contains-val?]]
    [cljs.core.async :refer [go]]
    [cljs.core.async.interop :refer-macros [<p!]] 
    [camel-snake-kebab.extras :as cske]
    [camel-snake-kebab.core :as csk]))


(def okp-db-service ^js/OkpDbService (.-OkpDbService rn/NativeModules))

(def autofill-events ^js/AutoFillEvents (.-AutoFillEvents rn/NativeModules))

(defn is-biometric-available []
  ;; Valid values expected for BiometricAvailable are "true" or "false" for both iOS and Android
  (= (-> okp-db-service .getConstants .-BiometricAvailable) "true"))

(defn- transform-resquest-args-excluding-keys
  "All keys in the incoming args map from UI will be transformed recursively except those that 
   match in the vec keys-exclude-v
   "
  [args keys-exclude-v]
  (let [t-fn (fn [k]
               (if (contains-val? keys-exclude-v k)
                 k
                 (csk/->snake_case k)))]
    (cske/transform-keys t-fn args)))

(defn- api-args->json
  "Converts the api-args map to a json string that can be deserilaized on the backend as 
  arguments for the invoked command fn
  "
  [api-args & {:keys [convert-request
                      ;; Keys in this vector are not converted to snake_case
                      ;; Typically string that are used as keys in a HashMap 
                      args-keys-excluded]
               :or {convert-request true
                    args-keys-excluded nil}}]
  (let [args-keys-excluded (when (vector? args-keys-excluded) args-keys-excluded)]
    (cond
      ;; args-keys-excluded supercedes convert-request
      (vector? args-keys-excluded)
      (->> (transform-resquest-args-excluding-keys api-args args-keys-excluded) clj->js (.stringify js/JSON))

      ;; changes all keys of args map to snake_case (e.g db-key -> db_key, ...)
      convert-request
      (->> api-args (cske/transform-keys csk/->snake_case) clj->js (.stringify js/JSON))

      ;; args-keys-excluded is nil and convert-request is false
      :else
      ;; Here the api-args map has all its keys of 'snake_case' form as they converted in the calling
      ;; fn itself
      (->>  api-args clj->js  (.stringify js/JSON)))))

(defn transform-response-excluding-keys
  "Called to transform the keys recursively in all maps found in the json response except those 
   keys returned by keys-excluded-fn. 
   Typically a partial fn is created with some 'keys-excluded-fn' as first arg and then the resulting fn
   is passed in 'convert-response-fn' to transform-api-response
   "
  [keys-excluded-fn response]
  (let [keys-exclude-vec  (keys-excluded-fn (get response "ok"))
        t-fn (fn [k]
               (if (contains-val? keys-exclude-vec k)
                 k
                 (csk/->kebab-case-keyword k)))]
    (cske/transform-keys t-fn response)))

(defn transform-api-response
  "Transforms the resolved value and returns a result map with key :ok or :error
  The first arg is the response returned by the api as a stringified json object
  and this string will be parsed to a map and then keys are transformed 
  The second args is a map with few options
  "
  [resolved {:keys [convert-response
                    convert-response-fn
                    no-response-conversion]
             :or {convert-response true
                  no-response-conversion false}}]
  ;; A typical resolved response is a json string of the 
  ;; form {"ok" {}, "error" nil} or {"ok" nil, "error" "someerror value"}
  ;; e.g {"ok" {"k1" "v1", "k2" "v1"}, "error" nil} 
  (cond
    no-response-conversion
    {:ok resolved}

    (not (nil? convert-response-fn))
    (->> resolved (.parse js/JSON) js->clj convert-response-fn) ;; custom transformer of response

    ;; All keys are transformed to keywords
    convert-response
    (->> resolved (.parse js/JSON) js->clj (cske/transform-keys csk/->kebab-case-keyword))

    :else
    (->> resolved (.parse js/JSON) js->clj)))

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
        ;; Uncomment for debugging raw response during dev time only  
        ;;(reset! test-raw-response-data r)
        (dispatch-fn deserialized-response))
      (catch js/Error err
        (do

          ;;(reset! test-raw-response-data err)

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
   The arg 'name' is the name of backend fn to call
   The args 'api-args' is map that provides arguments to the ffi fns (commands.rs) on the backend 
   The arg 'dispatch-fn' is called with the returned map (parsed from a json string)
   The final arg is an optional map 
  "
  [name api-args dispatch-fn &
   {:keys [convert-request args-keys-excluded convert-response convert-response-fn]
    :or {convert-request true
         args-keys-excluded nil
         convert-response true}}]
  (call-api-async (fn [] (.invokeCommand okp-db-service
                                         name
                                         (api-args->json api-args
                                                         :convert-request convert-request
                                                         :args-keys-excluded args-keys-excluded)))
                  dispatch-fn :convert-response convert-response :convert-response-fn convert-response-fn))

(defn autofill-invoke-api
  "Called to invoke commands from ffi
   The arg 'name' is the name of backend fn to call
   The args 'api-args' is map that provides arguments to the ffi fns (commands.rs) on the backend 
   The arg 'dispatch-fn' is called with the returned map (parsed from a json string)
   The final arg is an optional map 
  "
  [name api-args dispatch-fn &
   {:keys [convert-request args-keys-excluded convert-response convert-response-fn]
    :or {convert-request true
         args-keys-excluded nil
         convert-response true}}]
  (call-api-async (fn [] (.autoFillInvokeCommand okp-db-service
                                                 name
                                                 (api-args->json api-args
                                                                 :convert-request convert-request
                                                                 :args-keys-excluded args-keys-excluded)))
                  dispatch-fn :convert-response convert-response :convert-response-fn convert-response-fn))

(defn list-app-group-db-files [dispatch-fn]
  (autofill-invoke-api  "list_of_autofill_db_infos" {} dispatch-fn))

(defn list-key-files [dispatch-fn]
  (autofill-invoke-api "list_of_key_files" {} dispatch-fn))

#_(defn read-kdbx-from-app-group
  "Calls the API to read a kdbx file.
   Calls the dispatch-fn with the received map of type 'KdbxLoaded' 
  "
  [db-key password key-file-name dispatch-fn]
  (invoke-api "read_kdbx_from_app_group" {:db-file-name db-key
                             :password password
                             :key-file-name key-file-name} dispatch-fn))

(defn all-entries-on-db-open
  "Calls the API to read a kdbx file.
   Calls the dispatch-fn with the received map of type 'KdbxLoaded' 
  "
  [db-key password key-file-name dispatch-fn]
  (autofill-invoke-api "all_entries_on_db_open" {:db-file-name db-key
                                          :password password
                                          :key-file-name key-file-name} dispatch-fn))

(defn credential-service-identifier-filtering 
  "Prepares search term based on the domain or url passed by iOS on autofill launch and uses that term
  to search any matching entries.
  The 'dispatch-fn' is called with same result as the api call 'search_term' does
  "
  [db-key dispatch-fn]
  (autofill-invoke-api "credential_service_identifier_filtering" {:db-key db-key} dispatch-fn))

(defn copy-to-clipboard
  "Called to copy a selected field value to clipboard
   The arg field-info is a map that statifies the enum member 
   ClipboardCopyArg {field_name,field_value,protected,cleanup_after}
   "
  [field-info dispatch-fn]
  (autofill-invoke-api "clipboard_copy" field-info dispatch-fn))

(defn cancel-extension [dispatch-fn]
  (call-api-async (fn [] (.cancelExtension okp-db-service)) dispatch-fn))

(defn credentials-selected [user-name password dispatch-fn]
  (call-api-async (fn [] (.credentialSelected okp-db-service user-name password)) dispatch-fn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn search-term
  [db-key term dispatch-fn]
  (invoke-api "search_term" {:db-key db-key :term term} dispatch-fn))

(defn- transform-response-entry-form-data
  "
  The response is a map with keys 'ok' and 'error'
  All keys in the incoming raw EntryFormData map (found if there is no 'error' ) from backend will be transformed
  using a custom key transformer "
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

;;Note: load_language_translations uses data from AppState's default Preference struct and because of 
;; that the current locale language is used. Any ln overriden in the App's settings is not used
;; The same applies for the theme
(defn load-language-translations [language-ids dispatch-fn]
  (invoke-api "load_language_translations" {:language-ids language-ids} dispatch-fn))

;;;;;;;;;;;;;;; Native Events ;;;;;;;;;;;;;;;;;;

;; A dummy function definition for type inference of ^js/OkpEvents to work
(defn check-okp-event []
  (try
    (.getConstants autofill-events)
    (catch js/Object e
      (js/console.log e))))

(def event-emitter (rn/NativeEventEmitter. autofill-events))

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


(comment
  ;;(call-api-async (fn [] (.cancelExtension okp-db-service)) #(println %))
  (in-ns 'onekeepass.ios.autofill.background)
  
  (def db-key (-> @re-frame.db/app-db :current-db-file-name))
  ;; Use this to see :MainBundleDir, :LibraryDir, :DocumentDir etc
  (-> okp-db-service .getConstants .-MainBundleDir)
  )
