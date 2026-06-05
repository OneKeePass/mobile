(ns onekeepass.ios.autofill.background-custom-icons
  "Read-only FFI wrappers for custom icons inside the iOS autofill
   extension. Only listing + byte fetch are exposed here — add / remove
   / assign live on the main app's invoke surface."
  (:require
   [onekeepass.ios.autofill.background :refer [autofill-invoke-api]]))

(set! *warn-on-infer* true)

(defn list-custom-icons [db-key dispatch-fn]
  (autofill-invoke-api "list_custom_icons" {:db-key db-key} dispatch-fn))

(defn get-custom-icon-data
  "Returns {:uuid :name :data} where `data` is base64-encoded PNG bytes.
   Field name is `data` (not `data_b64`) to avoid camel-snake-kebab
   splitting the digit boundary on the cljs side."
  [db-key custom-icon-uuid dispatch-fn]
  (autofill-invoke-api "get_custom_icon_data"
                       {:db-key db-key :uuid custom-icon-uuid}
                       dispatch-fn))
