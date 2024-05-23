(ns onekeepass.mobile.translation
  (:require ["i18next" :as i18n]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [cljs.core.async :refer [go]]
            [cljs.core.async.interop :refer-macros [<p!]] 
            [onekeepass.mobile.events.translation :as tr-events]))

(set! *warn-on-infer* true)

(def ^:private i18n-obj ^js/i18nObj i18n)

(def i18n-instance (atom nil))

(def trans-defaults {})

(defn lstr
  "Gets the translation text for the given key and applying the interpolation if passed
  Arg interpolation-args is a map that provides value for any variable names used in text
  "
  ([txt-key interpolation-args]
   (let [;; NOTE: transform-keys will be called recursively though here interpolation-args 
         ;; will not have any inner map
         args (when-not (empty? interpolation-args)
                (->> interpolation-args (cske/transform-keys csk/->camelCaseString) clj->js))
         txt-key (if (symbol? txt-key) (str txt-key) txt-key)]

     (if (not (nil? @i18n-instance))
       (.t ^js/i18nObj @i18n-instance txt-key args)
       (get trans-defaults txt-key txt-key))))
  ([txt-key]
   (lstr txt-key nil)))

(defn- convert
  "Converts the case of the key string to the camelCase key as used in translation.json 
  IMPORTANT: 
   camel-snake-kebab.core/->camelCase expects a non nil value;Otherwise an error 
   will be thrown resulting UI not showing!
  "
  [txt-key]
  (csk/->camelCase
   (str txt-key) ;; (str nil) => ""
   #_(if (string? txt-key) txt-key "")))

(defn lstr-l
  "Adds prefix 'commonTexts' to the key before getting the translation"
  ([txt-key interpolation-args]
   (-> (str "commonTexts." txt-key) (lstr interpolation-args))
   #_(-> (str  txt-key) (lstr interpolation-args)))
  ([txt-key]
   (lstr-l txt-key nil)))

(defn lstr-cv
  "Converts the arg txt-key to cameCase and gets the translation text for that key"
  [txt-key]
  (lstr-l (convert (str txt-key))))

(defn lstr-dlg-title
  "Adds prefix 'dialog.titles' to the key before getting the translation"
  ([txt-key interpolation-args]
   (-> (str "dialog.titles." (convert txt-key)) (lstr interpolation-args)))

  ([txt-key]
   (lstr-dlg-title txt-key nil)))

(defn lstr-dlg-text
  "Adds prefix 'dialog.texts' to the key before getting the translation"
  ([txt-key interpolation-args]
   (-> (str "dialog.texts." (convert  txt-key)) (lstr interpolation-args)))
  ([txt-key]
   (lstr-dlg-text txt-key nil)))

;;(re-find #"[A-Z]"  (first "sacnError")) => nil
;;(re-find #"[A-Z]"  (first "Key File Save Error")) => "K"
(defn lstr-error-dlg-title
  "Adds prefix 'errorDialog.titles' to the key before getting the translation"
  [txt-key]
  ;; :common/error-box-show calls that use symbol for title are assumed to have translations 
  ;; If string value is used, that means these are yet to be translated 
  (if (symbol? txt-key)
    (lstr (str "errorDialog.titles." txt-key))
    txt-key))

(defn lstr-error-dlg-text
  "Adds prefix 'errorDialog.texts' to the key before getting the translation"
  [txt-key]
  ;; :common/error-box-show calls that use symbol for message are assumed to have translations 
  ;; If string value is used, that means these are yet to be translated 
  (if (symbol? txt-key)
    (lstr (str "errorDialog.texts." txt-key))
    txt-key))

(defn lstr-msg-dlg-title
  "Adds prefix 'messageDialog.titles' to the key before getting the translation"
  [txt-key]
  ;; :common/message-box-show calls that use symbol for title are assumed to have translations 
  ;; If string value is used, that means these are yet to be translated 
  (if (symbol? txt-key)
    (lstr (str "messageDialog.titles." txt-key))
    txt-key))

(defn lstr-msg-dlg-text
  "Adds prefix 'messageDialog.texts' to the key before getting the translation"
  [txt-key]
  ;; :common/message-box-show calls that use symbol for message are assumed to have translations 
  ;; If string value is used, that means these are yet to be translated 
  (if (symbol? txt-key)
    (lstr (str "messageDialog.texts." txt-key))
    txt-key))

(defn lstr-modal-dlg-text
  "Adds prefix 'modalDialog.texts' to the key before getting the translation"
  [txt-key]
  ;;  :common/error-box-show calls that use symbol for message are assumed to have translations 
  ;; If string value is used, that means these are yet to be translated 
  (if (symbol? txt-key)
    (lstr (str "modalDialog.texts." txt-key))
    txt-key))

(defn lstr-sm
  "Adds prefix 'snackbarMessages' to the key before getting the translation
  The arg 'txt-key' are expected to be a symbol as passed in events call ':common/message-snackbar-open' 
   "
  [txt-key]
  ;; If string value is used, that means such texts are yet to be translated 
  (if (symbol? txt-key)
    (lstr (str "snackbarMessages." txt-key))
    txt-key))

(defn lstr-ml
  "Adds 'menuLabels' prefix to the key and gets the translated text."
  [txt-key]
  (-> (str "menuLabels." (convert txt-key)) lstr))

(defn lstr-mt
  "Adds 'messageTexts' prefix to the key and gets the translated text."

  ([view-name txt-key interpolation-args]
   (-> (str "messageTexts." view-name "." txt-key) (lstr interpolation-args)))
  ([view-name txt-key]
   (lstr-mt view-name txt-key nil)))

(defn lstr-bl
  "Adds 'buttonLabels' prefix to the key and gets the translated text."
  [txt-key]
  (lstr (str "buttonLabels." txt-key)))

(defn lstr-pt
  "Adds 'pageTitles' prefix to the key and gets the translated text."
  [txt-key]
  (lstr (str "pageTitles." txt-key)))

(defn lstr-entry-type-title
  "Adds 'entryTypeTitles' prefix to the key and gets the translated text."
  [txt-key]
  (lstr (str "entryTypeTitles." (convert txt-key))))

(defn lstr-section-name
  "Adds 'entrySectionNames' prefix to the key and gets the translated text of standard entry section names"
  [txt-key]
  (lstr (str "entrySectionNames." (convert txt-key))))

(defn lstr-field-name
  "Adds 'entryFieldNames' prefix to the key and gets the translated text of standard entry fields"
  [txt-key]
  (lstr (str "entryFieldNames." (convert txt-key))))


(tr-events/set-translator {:lstr-modal-dlg-text lstr-modal-dlg-text})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  

(defn parse-json [str-value]
  (try
    (.parse js/JSON str-value)
    (catch js/Error err
      (js/console.log (ex-cause err))
      #js {})))

(declare ^:private  setup-i18n-with-backend)

(declare ^:private create-back-end)

(defn- translations-loaded-callback [ok-response]
  ;; ok-response be nil on error. Then no translation will be available  
  (let [{:keys [_current-locale-language prefered-language translations]} ok-response
        ;; translations is a map where key is the language id and value is a json string and 
        ;; the json string needs to be parsed. After parsing the string in 'v', the type 
        ;; of the parsed value is a js object - #object[Object]
        parsed-translations (reduce (fn [m [k v]] (assoc m k (parse-json v)))  {} translations)]
    ;; (println "res is  " res)
    #_(println "current-locale-language prefered-language are " current-locale-language prefered-language)

    ;; Type of 'parsed-translations' is  cljs.core/PersistentArrayMap
    #_(println "Type of 'parsed-translations' is " (type parsed-translations))

    ;; Type of translations for en is  #object[Object]
    #_(println "Type of value for en key in 'parsed-translations' is " (type (:en parsed-translations)))

    (setup-i18n-with-backend prefered-language (create-back-end parsed-translations))))

(defn load-language-translation
  "Needs to be called on app loading in the very begining to load locale language and 'en' 
   tranalations json files found in app resource dir"
  ([]
   (tr-events/load-language-translation [] translations-loaded-callback))

  ([language-ids]
   ;; language-ids is a vec of two charater language ids
   ;; e.g ["en" "fr"]
   (tr-events/load-language-translation language-ids translations-loaded-callback)))

(defn reload-language-translation
  "Called after language selection is changed"
  []
  (tr-events/reload-language-data [] translations-loaded-callback))


#_(defn- translations-loaded [api-response]
  ;; on-ok may return nil on error. Then no translation will be available  
    (let [{:keys [_current-locale-language prefered-language translations] :as res} (on-ok api-response)
        ;; translations is a map where key is the language id and value is a json string and 
        ;; the json string needs to be parsed. After parsing the string in 'v', the type 
        ;; of the parsed value is a js object - #object[Object]
          parsed-translations (reduce (fn [m [k v]] (assoc m k (parse-json v)))  {} translations)]
    ;; (println "res is  " res)
      #_(println "current-locale-language prefered-language are " current-locale-language prefered-language)

    ;; Type of 'parsed-translations' is  cljs.core/PersistentArrayMap
      #_(println "Type of 'parsed-translations' is " (type parsed-translations))

    ;; Type of translations for en is  #object[Object]
      #_(println "Type of value for en key in 'parsed-translations' is " (type (:en parsed-translations)))

      (setup-i18n-with-backend prefered-language (create-back-end parsed-translations))))


#_(defn load-language-translation
    "Needs to be called on app loading in the very begining to load locale language and 'en' 
   tranalations json files found in app resource dir"
    ([]
     (bg/load-language-translations [] translations-loaded))

    ([language-ids]
   ;; language-ids is a vec of two charater language ids
   ;; e.g ["en" "fr"]
     (bg/load-language-translations language-ids translations-loaded)))

(defn- create-i18n-init
  "The init call on an instance of 'i18n' returns a promise and we need to r
   esolve here before using any fns from 'i18n'"
  [^js/i18nObj instance options]
  (go
    (try
      (let [_f (<p! (.init instance (clj->js options)))]
        (reset! i18n-instance instance)
        (js/console.log  "i18n init is done successfully")
        ;; Need to dispatch on successful loading of data
        #_(cmn-events/load-language-translation-completed)
        (tr-events/load-language-data-complete))

      ;; Error should not happen as we have already loaded a valid translations data before calling init 
      ;; Still what to do if there is any error in initializing 'i18n'? 
      (catch js/Error err
        ;; Because of error in laoding i18n data, all UI texts will not be correct 
        ;; even though we are calling this complete status setting.
        ;; If we do not call this status, the UI will be stuck in 'Please wait ..'
        (tr-events/load-language-data-complete)
        (js/console.log (ex-cause err))))))

;;Ref: https://www.i18next.com/misc/creating-own-plugins#backend

(defn- create-back-end [translations]
  {:type "backend"

   :init (fn [_services _backendOptions _i18nextOptions]
           #_(println "services:  " services)
           #_(println "backendOptions: "  backendOptions)
           #_(println "i18nextOptions: " i18nextOptions))

   ;; Typically read would have been called when we call use fn
   ;; The translations data for the main language and fallback language will be 
   ;; called through callback and i18n retains internally
   :read (fn [language _namespace callback]
           #_(println "language ids from translations map are " (keys translations))
           #_(println "create-back-end language namespace callback " language namespace callback)
           ;; language is a string type whereas the keys in translations map are keyword
           #_(println "data  is... " (clj->js (get translations (keyword language))))
           (callback nil (clj->js (get translations (keyword language)))))})

;; Android issue 
;; When we use ':compatibilityJSON "v4"' to support 'PluralRules', we see the error

;; i18next::pluralResolver: Your environment seems not to be Intl API compatible, 
;; use an Intl.PluralRules polyfill. Will fallback to the compatibilityJSON v3 format handling

;; Using suggested polyfill from 'https://github.com/eemeli/intl-pluralrules' did not work

;; Then used  'def jscFlavor = 'org.webkit:android-jsc-intl:+''   instead of 'def jscFlavor = 'org.webkit:android-jsc:+''
;; in mobile/android/app/build.gradle as per the following refrences
;; https://stackoverflow.com/questions/56943813/using-intl-properly-in-android-react-native-app
;; https://github.com/formatjs/formatjs/issues/1591
;; https://github.com/formatjs/formatjs/issues/1591

(defn- setup-i18n-with-backend [language back-end]
  (let [m  {:lng language
            :fallbackLng "en"
            :compatibilityJSON "v4"
            ;; set debug true to see some i18n package debug prints in console (xcode debug/log console) 
            :debug false}
        ^js/i18nObj instance (.createInstance i18n-obj)]
    (.use instance (clj->js back-end))
    (create-i18n-init instance m)))

(comment
  (in-ns 'onekeepass.mobile.translation)

  (lstr "page.titles.appSettings")
  ;; => "App Settings"

  (lstr "page.titles.nonExistenceKey")
  ;; => "page.titles.nonExistenceKey"

  ;; if we give only the key of a json map, then we get this error string
  (lstr "page.titles")
  ;; => "key 'page.titles (en)' returned an object instead of string."

  ;; To verify whether Intl obj is available
  ;; Some examples are in https://www.js-howto.com/a-comprehensive-guide-to-the-javascript-intl-api/
  ;; Also see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl

  (def a (.NumberFormat js/Intl "de-DE"))  ;; a is #object[NumberFormat [object Object]]

  (.format a 1234567.89) ;; => "1.234.567,89"
  )