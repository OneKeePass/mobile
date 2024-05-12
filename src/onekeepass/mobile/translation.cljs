(ns onekeepass.mobile.translation
  (:require ["i18next" :as i18n]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [cljs.core.async :refer [go]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [onekeepass.mobile.background :as bg]
            [onekeepass.mobile.events.common :as cmn-events :refer [on-ok]]))

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
   (str txt-key)
   #_(if (string? txt-key) txt-key "")))

(defn lstr-cv
  "Converts the arg txt-key to cameCase and gets the translation text for that key"
  [txt-key]
  (lstr (convert  (str txt-key))))

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
  ;;  :common/error-box-show calls that use symbol for title are assumed to have translations 
  ;; If string value is used, that means these are yet to be translated 
  (if (symbol? txt-key)
    (lstr (str "errorDialog.titles." txt-key))
    txt-key))

(defn lstr-error-dlg-text
  "Adds prefix 'errorDialog.texts' to the key before getting the translation"
  [txt-key]
  (lstr (str "errorDialog.texts." txt-key)))

(defn lstr-sm
  "Adds prefix 'snackbar.messages' to the key before getting the translation"
  [txt-key]
  (lstr (str "snackbar.messages." txt-key)))

(defn lstr-ml
  "Adds 'menu.labels' prefix to the key and gets the translated text."
  [txt-key]
  (-> (str "menu.labels." (convert txt-key)) lstr))

(defn lstr-bl [txt-key]
  (lstr (str "button.labels." txt-key)))

(defn lstr-pt
  "Adds 'page.titles' prefix to the key and gets the translated text."
  [txt-key]
  (lstr (str "page.titles." txt-key)))

(defn lstr-entry-type-title
  "Adds 'entryType.titles' prefix to the key and gets the translated text."
  [txt-key]
  (lstr (str "entryTypeTitles." (convert txt-key))))

#_(defn lstr-l [txt-key]
    (lstr (str "labels." txt-key)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  

(defn parse-json [str-value]
  (try
    (.parse js/JSON str-value)
    (catch js/Error err
      (js/console.log (ex-cause err))
      #js {})))

(declare ^:private  setup-i18n-with-backend)

(declare ^:private create-back-end)

(defn- translations-loaded [api-response]
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


(defn load-language-translation
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
        (cmn-events/load-language-translation-completed))
      ;; Error should not happen as we have already loaded a valid translations data before calling init 
      ;; Still what to do if there is any error in initializing 'i18n'? 
      (catch js/Error err
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
  )