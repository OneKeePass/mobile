(ns onekeepass.mobile.background-common
  (:require
   [react-native :as rn]
   ["@react-native-clipboard/clipboard" :as rnc-clipboard]
   ["react-native-file-viewer" :as native-file-viewer]
   ["react-native-vision-camera" :as rn-vision-camera]
   [cljs.core.async :refer [go]]
   [cljs.core.async.interop :refer-macros [<p!]]
   [onekeepass.mobile.utils :as u :refer [contains-val?]]
   [camel-snake-kebab.extras :as cske]
   [camel-snake-kebab.core :as csk]))



(set! *warn-on-infer* true)


(def okp-db-service ^js/OkpDbService (.-OkpDbService rn/NativeModules))

(def okp-document-pick-service ^js/OkpDocumentPickerService (.-OkpDocumentPickerService rn/NativeModules))

(def rn-boot-splash ^js/RNBootSplash (.-RNBootSplash rn/NativeModules))

(def okp-export ^js/OkpExport (.-OkpExport rn/NativeModules))

(def okp-events ^js/OkpEvents (.-OkpEvents rn/NativeModules))



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


(defn api-args->json
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

;; We can store the last raw response here and can be used for debugging during
;; development time. Need to uncomment the '(reset!...) call in fn 'call-api-async' for this
(def test-raw-response-data (atom nil))

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

;;TODO: Combine android-invoke-api,ios-autofill-invoke-api and invoke-api
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


(defn ios-autofill-invoke-api
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


(defn android-invoke-api
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
  (call-api-async (fn [] (.androidInvokeCommand okp-db-service
                                                name
                                                (api-args->json api-args
                                                                :convert-request convert-request
                                                                :args-keys-excluded args-keys-excluded)))
                  dispatch-fn :convert-response convert-response :convert-response-fn convert-response-fn))




