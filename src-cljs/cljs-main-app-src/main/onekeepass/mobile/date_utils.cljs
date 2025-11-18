(ns onekeepass.mobile.date-utils
  (:require 
   [onekeepass.mobile.rn-components :as rnc]
   [clojure.string :as str]))

(defn- from-UTC-str-to-UTC
  "Converts a datetime str of format 'yyyy-MM-dd'T'HH:mm:ss' to UTC datetime
  The datetime-str may have suffix 'Z'. In that case the string is already a UTC string

  Returns Date object which is in UTC
  "
  [datetime-str]
  (let [s (if (str/ends-with? datetime-str "Z")
            datetime-str (str datetime-str "Z"))]
    (js/Date. s)))

#_(defn to-UTC-ISO-string
  "This returns a string in simplified extended ISO format (ISO 8601) using 
  native javascript method on Date The timezone is always zero UTC offset, as denoted by the suffix 'Z'
  See https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/toISOString
  "
  [^js/Date date]
  (.toISOString date))

;; See https://date-fns.org/v2.29.2/docs/format and https://date-fns.org/v2.29.2/docs/parse
;; for the format supported by date-fns and in turn date-fns-utils

(defn utc-to-local-datetime-str
  "Incoming Date represents a UTC datetime in milli seconds and returns formatted string in local TZ"
  ([^js/Date date format-str]
   ;; .formatByString expects the date as a UTC object
   #_(.formatByString rnc/date-fns-utils date format-str)
   (try
     (.formatByString rnc/date-fns-utils date format-str)
     (catch js/Error err
       (js/console.log (ex-cause err))
       nil)))
  ([^js/Date date]
   (utc-to-local-datetime-str date "yyyy-MM-dd'T'HH:mm:ss")))

(defn utc-str-to-local-datetime-str
  "Coverts a datetime str which is in Utc TZ to a datetime str that is in Local TZ
   The incoming datetime str should be in a format like '2022-09-09T17:42:35'
   "
  [utc-datetime-str]
  ;; First we need to get a Utc date object from utc str and convert that to Local str
  (utc-to-local-datetime-str (from-UTC-str-to-UTC utc-datetime-str) "yyyy-MM-dd hh:mm:ss a"))


(comment
  (in-ns 'onekeepass.mobile.date-utils)
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
  )
