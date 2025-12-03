(ns onekeepass.ios.autofill.utils
  (:require
   [clojure.string :as str]))

(set! *warn-on-infer* true)


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
  [data]
  (if (int? data)
    data
    (if (and (string? data) (re-matches #"\d+" data))  ;;(re-matches #"\d+"  data) will return nil when data includes space or any non numeric char
      (js/parseInt data)
      nil)))

;; Based on some examples in https://stackoverflow.com/questions/32467299/clojurescript-convert-arbitrary-javascript-object-to-clojure-script-map
;; Somewhat old, but the solution used here works
#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn jsx->clj
  "Converts objects of type '#object[Error Error: Document picker was cancelled..]' to 
  {:nativeStackAndroid [], :code \"DOCUMENT_PICKER_CANCELED\"..}
  "
  [obj]
  (js->clj (-> obj js/JSON.stringify js/JSON.parse) :keywordize-keys true))

(defn find-match
  "Finds a map that has a matching value for a given keyword key
   The arg 'seq-of-maps' is a vec of map elements. 
   The first match is returned ignoring any other subsequent ones
   "
  [seq-of-maps kw-key value]
  (first
   (filter
    (fn [m]
      (= value (kw-key m)))
    seq-of-maps)))

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
  (in-ns 'onekeepass.ios.autofill.utils))
