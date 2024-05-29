(ns onekeepass.mobile.events.exporting
  (:require
   [onekeepass.mobile.events.common :refer [on-ok on-error]]
   [re-frame.core :refer [reg-event-fx
                          dispatch
                          reg-fx]]
   [onekeepass.mobile.background :as bg]))


(defn prepare-export-kdbx-data [full-file-name-uri]
  (dispatch [:start-export-kdbx-data-preparation full-file-name-uri]))

(reg-event-fx
 :start-export-kdbx-data-preparation
 (fn [{:keys [_db]} [_event-id full-file-name-uri]]
   {:fx  [[:dispatch [:common/message-modal-show nil 'preparingExportData]]
          [:bg-prepare-export-kdbx-data [full-file-name-uri]]]}))

(reg-fx
 :bg-prepare-export-kdbx-data
 (fn [[full-file-name-uri]]
   (bg/prepare-export-kdbx-data full-file-name-uri
                                (fn [api-response]
                                  (when-let [result (on-ok api-response)]
                                    (dispatch [:export-kdbx-data-preparation-done result]))))))

(reg-event-fx
 :export-kdbx-data-preparation-done
 (fn [{:keys [_db]} [_event-id {:keys [exported-data-full-file-name]}]]
   {:fx [[:dispatch [:common/message-modal-hide]]
         [:bg-exportkdbx-data [exported-data-full-file-name]]]}))

(reg-fx
 :bg-exportkdbx-data
 (fn [[exported-data-full-file-name]]
   (bg/export-kdbx exported-data-full-file-name
                   (fn [api-response]
                     (when-not (on-error api-response)
                       (dispatch [:common/message-snackbar-open  'actionCompleted]))))))