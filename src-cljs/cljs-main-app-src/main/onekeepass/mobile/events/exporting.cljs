(ns onekeepass.mobile.events.exporting
  (:require
   [onekeepass.mobile.events.common :refer [on-ok on-error]]
   [onekeepass.mobile.translation :refer [lstr-error-dlg-title]]
   [re-frame.core :refer [reg-event-fx
                          dispatch
                          reg-fx]]
   [onekeepass.mobile.background-remote-server :as bg-rs]
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
                       ;; (println "exported-data-full-file-name is " exported-data-full-file-name)
                       ;; TODO: Need to delete the temporarily created data file from "export_data" folder
                       ;; on device. For now whole folder content is deleted while preparing export data
                       (dispatch [:common/message-snackbar-open  'actionCompleted]))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;   Save as   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; 'Save As' writes a copy of a database to a location selected by the user - either
;; through the device's document picker or to a remote storage folder. The database
;; that is open stays as it is. The copy is not opened, is not added to the recently
;; used list and no backup or checksum is tracked for it

;; The copy is prepared only after the target is known. Otherwise cancelling the
;; selection would leave the database content behind in the export data dir

(defn save-as-to-device-start
  "Called when the user picks the device file picker as the target of 'Save As'"
  [{:keys [db-key file-name]}]
  (dispatch [:exporting-save-as-prepare-data db-key {:target :device :file-name file-name}]))

;; Called after the user has selected a remote storage folder and confirmed the file name
;; The arg 'new-db-key' is the formed key of the copy that is going to be created
(reg-event-fx
 :exporting/save-as-to-remote-start
 (fn [{:keys [_db]} [_event-id db-key new-db-key]]
   {:fx [[:dispatch [:exporting-save-as-prepare-data db-key {:target :remote
                                                             :new-db-key new-db-key}]]]}))

(reg-event-fx
 :exporting-save-as-prepare-data
 (fn [{:keys [_db]} [_event-id db-key target-info]]
   {:fx [[:dispatch [:common/message-modal-show nil 'preparingExportData]]
         [:bg-prepare-save-as-data [db-key target-info]]]}))

(reg-fx
 :bg-prepare-save-as-data
 (fn [[db-key target-info]]
   (bg/prepare-export-kdbx-data db-key
                                (fn [api-response]
                                  (when-let [result (on-ok
                                                     api-response
                                                     #(dispatch [:exporting-save-as-error %]))]
                                    (dispatch [:exporting-save-as-data-prepared result target-info]))))))

(reg-event-fx
 :exporting-save-as-data-prepared
 (fn [{:keys [_db]} [_event-id
                     {:keys [exported-data-full-file-name]}
                     {:keys [target file-name new-db-key]}]]
   (if (= target :device)
     ;; The modal is hidden before the device's document picker is presented
     {:fx [[:dispatch [:common/message-modal-hide]]
           [:bg-pick-kdbx-file-to-save [exported-data-full-file-name file-name]]]}
     {:fx [[:dispatch [:common/message-modal-show nil 'saving]]
           [:bg-rs-save-as-kdbx [new-db-key exported-data-full-file-name]]]})))

(reg-fx
 :bg-pick-kdbx-file-to-save
 (fn [[exported-data-full-file-name file-name]]
   ;; The copying of the prepared file to the location picked by the user is done
   ;; by the device itself and this call resolves only after that is completed
   (bg/pick-kdbx-file-to-save exported-data-full-file-name file-name
                              (fn [api-response]
                                (when-not (on-error
                                           api-response
                                           #(dispatch [:exporting-save-as-pick-not-completed %]))
                                  (dispatch [:exporting-save-as-completed]))))))

(reg-fx
 :bg-rs-save-as-kdbx
 (fn [[new-db-key exported-data-full-file-name]]
   (bg-rs/save-as-kdbx new-db-key exported-data-full-file-name
                       (fn [api-response]
                         (when-not (on-error
                                    api-response
                                    #(dispatch [:exporting-save-as-error %]))
                           (dispatch [:exporting-save-as-completed]))))))

(reg-event-fx
 :exporting-save-as-completed
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:dispatch [:common/message-modal-hide]]
         [:bg-clean-export-data-dir]
         [:dispatch [:common/message-snackbar-open 'actionCompleted]]]}))

;; The user cancelling the document picker is not an error and no message is shown
(reg-event-fx
 :exporting-save-as-pick-not-completed
 (fn [{:keys [_db]} [_event-id error]]
   (if (= "DOCUMENT_PICKER_CANCELED" (:code error))
     {:fx [[:bg-clean-export-data-dir]]}
     {:fx [[:dispatch [:exporting-save-as-error error]]]})))

(reg-event-fx
 :exporting-save-as-error
 (fn [{:keys [_db]} [_event-id error]]
   {:fx [[:dispatch [:common/message-modal-hide]]
         [:bg-clean-export-data-dir]
         [:dispatch [:common/error-box-show (lstr-error-dlg-title 'saveAsError) error]]]}))

;; The prepared copy holds the database content and is removed on all the end paths
(reg-fx
 :bg-clean-export-data-dir
 (fn [_no-args]
   (bg/clean-export-data-dir (fn [api-response]
                               (on-error api-response
                                         #(js/console.warn "Cleaning the export data dir failed " %))))))