(ns onekeepass.ios.autofill.events.custom-icons
  "Read-only custom-icon support for the autofill extension. Lazily fetches
   PNG bytes (base64) for a given uuid and caches the resulting data-URL in
   app-db so subsequent renders are instant."
  (:require
   [onekeepass.ios.autofill.background-custom-icons :as bg-ci]
   [onekeepass.ios.autofill.events.common :refer [active-db-key on-ok]]
   [re-frame.core :refer [dispatch reg-event-fx reg-fx reg-sub subscribe]]))

;; Autofill loads one DB at a time, so data-urls live at the top of app-db
;; under [:custom-icons :data-urls uuid]. No per-DB scoping needed.

(defn icon-data-url
  "Returns a subscription holding the base64 data URL for `uuid`, fetching
   it lazily on first access."
  [uuid]
  (subscribe [:custom-icon-data-url uuid]))

(reg-sub
 :custom-icon-data-url
 (fn [db [_ uuid]]
   (get-in db [:custom-icons :data-urls uuid])))

(defn ensure-icon-data-url
  "Kick off a lazy fetch for `uuid`. Safe to call from a render fn — the
   fetch is a no-op if the bytes are already cached."
  [uuid]
  (when uuid
    (dispatch [:custom-icons-fetch-data-url uuid])))

(reg-event-fx
 :custom-icons-fetch-data-url
 (fn [{:keys [db]} [_ uuid]]
   (if (get-in db [:custom-icons :data-urls uuid])
     {}
     {:fx [[:bg-get-custom-icon-data [(active-db-key db) uuid]]]})))

(reg-event-fx
 :custom-icons-data-loaded
 (fn [{:keys [db]} [_ uuid data-url]]
   {:db (assoc-in db [:custom-icons :data-urls uuid] data-url)}))

(reg-fx
 :bg-get-custom-icon-data
 (fn [[db-key uuid]]
   (when (and db-key uuid)
     (bg-ci/get-custom-icon-data
      db-key uuid
      (fn [api-response]
        (when-let [{:keys [data]} (on-ok api-response)]
          (dispatch [:custom-icons-data-loaded
                     uuid
                     (str "data:image/png;base64," data)])))))))
