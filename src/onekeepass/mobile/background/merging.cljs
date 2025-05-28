(ns onekeepass.mobile.background.merging
  (:require
   [onekeepass.mobile.background-common :refer [invoke-api]]))

(defn merge-databases
  "Calls the API to merge two databases.
   Calls the dispatch-fn with the result or error 
  "
  [target-db-key source-db-key  dispatch-fn]
  (invoke-api "merge_databases"
              {:target-db-key target-db-key
               :source-db-key source-db-key} dispatch-fn))