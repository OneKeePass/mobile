(ns onekeepass.mobile.utils
  (:require
   [clojure.string :as str]))

(set! *warn-on-infer* true)

;; Platform.OS is enum ('android', 'ios') 
;; See https://reactnative.dev/docs/platform

;; In advanced mode compilation, this function call gets replaced with
;; boolean false value by the closure compiler as an optimization during the compile phase
;; The Platform.OS is set to proper value only during the runtime
;; (= Platform.OS "ios") is false during the compile phase - may be Platform.OS is set some other default value 
;; See onekeepass.mobile.background/platform-os for alternative implementation for advanced compilation to work
#_(defn is-iOS []
    (= Platform.OS "ios"))

#_(defn is-Android []
    (= Platform.OS "android"))

#_(defn append-path
    "Called to append the given name to the uri encoded path. 
  used mainly in iOS
  "
    [path name]
    (if (str/ends-with? path "/")
      (str path (js/encodeURI name))
      (str path "/" (js/encodeURI name))))

#_(defn to-file-path
    "In case ios, we need to get the file path from file URI so that we can use in rust ffi"
    [path-uri]
    (if (= Platform.OS "ios")
      (let [path (js/decodeURI path-uri)]
        (if (str/starts-with? path "file://")
          (-> path (str/split  #"file://") last)
          path))
      path-uri))

#_(defn extract-file-name
    "Given the full file url as returned by native layer, we get the file name
  In Android, the full url is in the format
  content://com.android.externalstorage.documents/document/primary%3ADocuments%2FTest2.kdbx
  decodeURIComponent of this url returns content://com.android.externalstorage.documents/document/primary:Documents/Test2.kdbx
  The file name 'Test2.kdbx' is returned
  "
    [full-url]
    (last (str/split (js/decodeURIComponent full-url)  #"[/:]")))

(def UUID-DEFAULT "00000000-0000-0000-0000-000000000000")

(defn uuid-nil-or-default? [uuid]
  (or (nil? uuid) (= uuid UUID-DEFAULT)))

(defn tags->vec
  "Converts the incoming tag values from a string to a vector of tags to use in UI.
  The arg 'tags' is a string made from all tags searated by ';'
  "
  [tags]
  (if (or (nil? tags) (empty? (str/trim tags))) [] (str/split tags #"[;,]")))

(defn vec->tags
  "Called to convert a vector of tag values to a string with ';' as speaprator between
  tag values. Needs to be called before calling backend API.
  Returns a string made from all tags searated by ';'
   "
  [tagsv]
  (if (empty? tagsv) "" (str/join ";" tagsv)))

(defn find-index
  "Finds the index of a value  in a vector
  Returns the index of item found or nil
  In case of duplicate values, the index of first instance returned 
  "
  [vec val]
  (reduce-kv
   (fn [_ k v]
     (when (= val v)
       (reduced k)))
   nil
   vec))

(defn contains-val?
  "A sequential search to find a member with an early exit"
  [coll val]
  (reduce #(if (= val %2) (reduced true) %1) false coll))

(defn str->int
  "Converts the incoming 'data' to an integer or returns nil"
  ([data]
   (if (int? data)
     data
     (if (and (string? data) (re-matches #"\d+" data))  ;;(re-matches #"\d+"  data) will return nil when data includes space or any non numeric char
       (js/parseInt data)
       nil)))
  ([data default]
   (let [v (str->int data)]
     (if-not (nil? v) v default))))

;; Based on some examples in https://stackoverflow.com/questions/32467299/clojurescript-convert-arbitrary-javascript-object-to-clojure-script-map
;; Somewhat old, but the solution used here works
#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn jsx->clj
  "Converts objects of type '#object[Error Error: Document picker was cancelled..]' to 
  {:nativeStackAndroid [], :code \"DOCUMENT_PICKER_CANCELED\"..}
  "
  [obj] 
  (js->clj (-> obj js/JSON.stringify js/JSON.parse) :keywordize-keys true))

(def KB 1024)

(def MB 1048576) ;;1,048,576

(def GB 1073741824) ;;1,073,741,824

;;1,099,511,627,776 Terabyte

(defn to-file-size-str [^js/Number number]
  (cond
    (< number KB)
    (str (.toFixed number 2) " B")

    (and (> number KB) (< number MB))
    (str (.toFixed (/ number KB) 2) " KiB")

    (and (> number MB) (< number GB))
    (str (.toFixed (/ number MB) 2) " MiB")

    :else
    (str (.toFixed (/ number GB) 2) " GiB")))

;; From an article https://dnaeon.github.io/recursively-merging-maps-in-clojure/
(defn deep-merge
  "Recursively merges maps."
  [& maps]
  (letfn [(m [& xs]
            (if (some #(and (map? %) (not (record? %))) xs)
              (apply merge-with m xs)
              (last xs)))]
    (reduce m maps)))

(defn deep-merge-with
  "Recursively merges maps. Applies function f when we have duplicate keys.
  The fn 'f' should take two args
  "
  [f & maps]
  (letfn [(m [& xs]
             ;;(println "xs is " xs)
            (if (some #(and (map? %) (not (record? %))) xs)
              (apply merge-with m xs)
              (apply f xs)))]
    (reduce m maps)))

(comment
  (in-ns 'onekeepass.mobile.utils)
  ;; daf114d0-a518-4e13-b75b-fbe893e69a9d 8bd81fe1-f786-46c3-b0e4-d215f8247a10
  ;; content://com.android.externalstorage.documents/document/primary%3ATest1.kdbx
  ;; 
  (.parse rnc/date-fns-utils "2022-09-09T17:42:35" "yyyy-MM-dd'T'HH:mm:ss")
  (.getTimezoneOffset (js/Date.)) ;; will give timezone offset from utc in minutes
  (utc-to-local-datetime-str (from-UTC-str-to-UTC "2023-01-03 00:38:46.164941"))

  (utc-str-to-local-datetime-str "2023-01-03 00:38:46.164941")
  ;; => "2023-01-02 04:38:46 PM"
  
  (utc-to-local-datetime-str 1676337120434 "LLL dd,yyyy hh:mm:ss aaa")
  ;; => "Feb 13,2023 05:12:00 pm"
  
  (str->int "000001")
  ;; => 1
  
  (str->int "23e3qwe")
  ;; => nil
  
  (str->int "d" 34)
  ;; => 34

  (def a {:data {:field1 3} :dialog-show false})
  (deep-merge a {:data {:field2 4}})
  ;; => {:data {:field1 3, :field2 4}, :dialog-show false}
  
  (deep-merge a {:data {:field1 4} :dialog-show true})
  ;; => {:data {:field1 4}, :dialog-show true}
  
  (deep-merge a {:data {:field1 4 :field2 22} :dialog-show true :error-text "sometext"})

  ;; => {:data {:field1 4, :field2 22}, :dialog-show true, :error-text "sometext"}
  
  (deep-merge {} nil) ;; => {}
  
  (deep-merge) or (deep-merge nil) ;; => nil
  
  (deep-merge-with first {:foo "foo" :bar {:baz "baz"}} {:foo "another-foo" :bar {:qux "qux"}})
  ;; => {:foo "f", :bar {:baz "baz", :qux "qux"}}
  ;; expected as per atricle {:foo "foo", :bar {:baz "baz", :qux "qux"}}
  ;; using first did not work 
  
  (deep-merge-with (fn [a b] a)  {:foo "foo" :bar {:baz "baz"}} {:foo "another-foo" :bar {:qux "qux"}})
  ;; => {:foo "foo", :bar {:baz "baz", :qux "qux"}}
  )