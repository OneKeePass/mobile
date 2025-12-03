(ns onekeepass.mobile.events.key-file-form
  (:require [clojure.string :as str]
            [onekeepass.mobile.constants :refer [KEY_FILE_FORM_PAGE_ID]]
            [onekeepass.mobile.background :as bg]
            [onekeepass.mobile.events.common :refer [on-error on-ok]] 
            [re-frame.core :refer [dispatch reg-event-db reg-event-fx reg-fx
                                   reg-sub subscribe]]))

(defn set-selected-key-file-info
  "Called with the selected key file info as map"
  [kf-info-m]
  (dispatch [:set-selected-key-file-info kf-info-m]))

(defn pick-key-file
  "Called when user clicks the browse button"
  []
  ;; We make first backend api call 'bg/pick-key-file-to-copy' followed 
  ;; by the second backend api call 'bg/copy-key-file' based on the response from first
  (bg/pick-key-file-to-copy (fn [api-response]
                              (when-let [full-file-name
                                         (on-ok
                                          api-response
                                          #(dispatch [:key-file-pick-error %]))]
                                ;; User picked a key file and needs to be copied
                                (bg/copy-key-file
                                 full-file-name
                                 (fn [res]
                                   (when-let [kf-info (on-ok
                                                       res
                                                       #(dispatch [:common/default-error "Key File Error" %]))]
                                     (dispatch [:key-file-copied kf-info]))))))))

(defn key-file-save-as
  "The key file from the private area of app is saved to a location picked by the user
   This is called from the menu shown in the key files listing. See also event ':bg-pick-key-file-to-save' 
   that is used when we want to save the generated key file
   "
  [{:keys [full-file-name file-name]}]
  (bg/pick-key-file-to-save full-file-name file-name
                            (fn [api-response] 
                              (when-not (on-error api-response #(dispatch [:key-file-save-error %])) 
                                (dispatch [:common/message-snackbar-open 'keyFileSaved])))))

(defn delete-key-file
  "Deletes the key file from local copy. The arg 'file-name' is just name part and 
   it is not the full file path
  "
  [file-name]
  (bg/delete-key-file file-name (fn [api-response]
                                  (when-let [updated-key-files-list (on-ok api-response)]
                                    (dispatch [:key-files-loaded updated-key-files-list])
                                    (dispatch [:common/message-snackbar-open 'keyFileRemoved])))))

;; Good clojure regex article https://ericnormand.me/mini-guide/clojure-regex
#_(defn- derive-key-file-name-part
    [file-name-part]
    (let [has-suffix (-> file-name-part str/lower-case (str/ends-with? ".kdbx"))
          prefix (if has-suffix
                   (first (str/split file-name-part #"(?i).kdbx"))
                   file-name-part)]
      (str prefix ".keyx")))

(defn- derive-key-file-name-part
  "Adds the suffix to the entered file name if required"
  [file-name-part]
  (let [has-suffix (-> file-name-part str/lower-case (str/ends-with? ".keyx"))]
    (if has-suffix
      file-name-part
      (str file-name-part ".keyx"))))

(defn generate-key-file
  "The arg file-name is the name entered by user and it may have the suffix '.keyx' or not"
  [file-name]
  (bg/generate-key-file (derive-key-file-name-part file-name)
                        (fn [api-response]
                          (when-let [kf-info (on-ok api-response #(dispatch [:key-file-generate-error %]))]
                            ;;(dispatch [:key-file-copied kf-info])
                            (dispatch [:key-file-generated kf-info])
                            (dispatch [:key-file-form-generate-file-name-dialog false])
                            (dispatch [:common/message-snackbar-open 'keyFileGenerated])))))

(defn imported-key-files []
  (subscribe [:imported-key-files]))

(defn show-generate-option []
  (subscribe [:show-generate-option]))

;; This event just calls the backend api
(reg-event-fx
 :load-imported-key-files
 (fn [{:keys [_db]} [_event-id]]
   (bg/list-key-files (fn [api-response]
                        (when-let [key-files-list (on-ok api-response)]
                          (dispatch [:key-files-loaded key-files-list]))))
   {}))

(reg-event-db
 :key-files-loaded
 (fn [db [_event-id key-files-list]]
   ;; key-files-list is a list of maps  -  Vec<KeyFileInfo>
   (assoc-in db [:key-file-form :key-files] key-files-list)))

(reg-event-fx
 :key-file-form/show
 (fn [{:keys [db]} [_event-id dispatch-on-file-selection show-generate-option]]
   ;; dispatch-on-file-selection determines the receiver of the selected key file in the form
   ;; show-generate-option determines whether to show 'Generate' button or not
   ;; The button is shown for the New database and in Settings
   {:db (-> db
            (assoc-in [:key-file-form :show-generate-option] show-generate-option)
            (assoc-in [:key-file-form :dispatch-on-file-selection] dispatch-on-file-selection))
    :fx [[:dispatch [:load-imported-key-files]]
         [:dispatch [:common/next-page KEY_FILE_FORM_PAGE_ID "keyFileForm"]]]}))

;; Any file picked by user is copied to app's private area in a backend api and then this event is called
(reg-event-fx
 :key-file-copied
 (fn [{:keys [db]} [_event-id kf-info]]
   (let [next-dipatch-kw (-> db (get-in [:key-file-form :dispatch-on-file-selection]))]
     {:fx [[:dispatch [:load-imported-key-files]]
         ;; Navigate to the previous page after the user picked a file
           [:dispatch [:common/previous-page]]
           [:dispatch [:common/message-snackbar-open 'keyFileSelected]]
           (when next-dipatch-kw
             [:dispatch [next-dipatch-kw kf-info]])]})))

(reg-event-fx
 :key-file-generated
 (fn [{:keys [_db]} [_event-id kf-info]]
   {:fx [[:bg-pick-key-file-to-save [kf-info]]]}))

;; Called to show document picker view to save a newly generated key file
(reg-fx
 :bg-pick-key-file-to-save
 (fn [[{:keys [full-file-name file-name] :as kf-info}]] 
   (bg/pick-key-file-to-save full-file-name file-name
                             (fn [api-response]
                               (when-not (on-error api-response #(dispatch [:key-file-save-error % kf-info]))
                                 (dispatch [:key-file-copied kf-info])
                                 (dispatch [:common/message-snackbar-open 'keyFileSaved]))))))

;; Called with the selected key file info as map and in turns navigates back to the previous 
;; and also passing the selected kf info
(reg-event-fx
 :set-selected-key-file-info
 (fn [{:keys [db]} [_event-id kf-info]]
   ;;kf-info is a map with file-name and full-file-name
   (let [next-dipatch-kw (-> db (get-in [:key-file-form :dispatch-on-file-selection]))]
     {:fx [(when next-dipatch-kw
             [:dispatch [next-dipatch-kw kf-info]])
           [:dispatch [:common/previous-page]]
           [:dispatch [:common/message-snackbar-open 'keyFileSelected ]]]})))

;; Similar to :database-file-pick-error event in ns onekeepass.mobile.events.open-database
;; Need to make it share instead of copying as done here
(reg-event-fx
 :key-file-pick-error
 (fn [{:keys [_db]} [_event-id error]]
   ;; If the user cancels any file selection, 
   ;; the RN response is a error due to the use of promise rejecton in Native Module. And we can ignore that error 
   {:fx [(when-not (= "DOCUMENT_PICKER_CANCELED" (:code error))
           [:dispatch [:common/error-box-show 'filePickError error]])]}))

(reg-event-fx
 :key-file-save-error
 (fn [{:keys [_db]} [_event-id error kf-info]]
   ;; If the user cancels any file selection, 
   ;; the RN response is a error due to the use of promise rejecton in Native Module. And we can ignore that error 
   
   ;; kf-info will have non nil value after a new key file is generated and 
   ;; pick-key-file-to-save is called to save after file generation
   ;; kf-info is nil when 'pick-key-file-to-save' is called from the menu of a key file in the 'imported key files' list
   (if kf-info
     (if (= "DOCUMENT_PICKER_CANCELED" (:code error))
       {:fx [[:dispatch [:load-imported-key-files]]
             [:dispatch [:common/error-box-show 'keyFileSavingCancelled 
                         (str "Recommended to save the generated key file to a secure place."
                              "If you use this file and the file is lost, you will not able to open the database without the key file" 
                              )]]
             ]}
       {:fx [[:dispatch [:common/error-box-show "Key File Save Error" error]]]})

     {:fx [(when-not (= "DOCUMENT_PICKER_CANCELED" (:code error))
             [:dispatch [:common/error-box-show "Key File Save Error" error]])]})))

(reg-event-fx
 :key-file-generate-error
 (fn [{:keys [_db]} [_event-id error]]
   {:fx [[:dispatch [:key-file-form-generate-file-name-dialog false]]
         [:dispatch [:common/error-box-show error]]]}))

(reg-sub
 :imported-key-files
 (fn [db _query-vec]
   (get-in db [:key-file-form :key-files])))

(reg-sub
 :show-generate-option
 (fn [db _query-vec]
   (get-in db [:key-file-form :show-generate-option])))

;;;;;;;;;;;;; Key file name dialog ;;;;;;;;

(defn generate-file-name-dialog-show []
  (dispatch [:key-file-form-generate-file-name-dialog true]))

(defn generate-file-name-dialog-hide []
  (dispatch [:key-file-form-generate-file-name-dialog false]))

(defn generate-file-name-dialog-data []
  (subscribe [:key-file-form-generate-file-name-dialog-data]))

(defn generate-file-name-dialog-update [file-name]
  (dispatch [:key-file-form-generate-file-name-update file-name]))

(reg-event-db
 :key-file-form-generate-file-name-dialog
 (fn [db [_event-id show?]]
   (assoc-in db [:key-file-form :file-name-dialog] {:dialog-show show?
                                                    :file-name nil})))

(reg-event-db
 :key-file-form-generate-file-name-update
 (fn [db [_event-id name]]
   (assoc-in db [:key-file-form :file-name-dialog :file-name] name)))

(reg-sub
 :key-file-form-generate-file-name-dialog-data
 (fn [db _query-vec]
   (get-in db [:key-file-form :file-name-dialog])))


(comment
  (in-ns 'onekeepass.mobile.events.key-file-form)

  (def db-key (-> @re-frame.db/app-db :current-db-file-name))
  (-> @re-frame.db/app-db (get db-key) keys))