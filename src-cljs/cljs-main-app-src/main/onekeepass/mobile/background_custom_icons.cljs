(ns onekeepass.mobile.background-custom-icons
  "Native FFI wrappers for per-database custom icons — listing, fetching
   base64-encoded PNG bytes, adding from URL (favicon) / picked file,
   removing, and assigning to entries / groups."
  (:require
   [onekeepass.mobile.background-common :as bg-cmn :refer [api-args->json
                                                           call-api-async
                                                           invoke-api
                                                           okp-db-service]]))

(set! *warn-on-infer* true)

(defn list-custom-icons [db-key dispatch-fn]
  (invoke-api "list_custom_icons" {:db-key db-key} dispatch-fn))

(defn get-custom-icon-data
  "Returns {:uuid :name :data} — `data` is the base64-encoded PNG bytes
   which the cljs caller wraps in a `data:image/png;base64,…` URL.
   Field name is `data` (not `data_b64`) to avoid camel-snake-kebab
   splitting the digit boundary on the cljs side."
  [db-key custom-icon-uuid dispatch-fn]
  (invoke-api "get_custom_icon_data"
              {:db-key db-key :uuid custom-icon-uuid}
              dispatch-fn))

(defn add-custom-icon-from-url [db-key url dispatch-fn]
  (invoke-api "add_custom_icon_from_url" {:db-key db-key :url url} dispatch-fn))

;; The user picks an image file via the platform document picker first,
;; we get back a full-file-name URI (iOS bookmarked), then call this to
;; read the bytes server-side, normalize to PNG and add to the DB.
(defn add-custom-icon-from-file [db-key full-file-name dispatch-fn]
  (call-api-async (fn []
                    (.addCustomIconFromFile
                     okp-db-service full-file-name
                     (api-args->json {:db_key db-key}
                                     :convert-request false)))
                  dispatch-fn :error-transform true))

(defn remove-custom-icon [db-key custom-icon-uuid dispatch-fn]
  (invoke-api "remove_custom_icon"
              {:db-key db-key :uuid custom-icon-uuid}
              dispatch-fn))

;; Pass empty string for custom-icon-uuid to clear the assignment.
(defn set-entry-custom-icon [db-key entry-uuid custom-icon-uuid dispatch-fn]
  (invoke-api "set_entry_custom_icon"
              {:db-key db-key
               :entry-uuid entry-uuid
               :custom-icon-uuid (or custom-icon-uuid "")}
              dispatch-fn))

(defn set-group-custom-icon [db-key group-uuid custom-icon-uuid dispatch-fn]
  (invoke-api "set_group_custom_icon"
              {:db-key db-key
               :group-uuid group-uuid
               :custom-icon-uuid (or custom-icon-uuid "")}
              dispatch-fn))
