(ns onekeepass.mobile.events.key-file-form
  (:require
   [clojure.string :as str]
   [re-frame.core :refer [reg-event-db
                          reg-event-fx 
                          reg-sub
                          dispatch
                          subscribe]]

   [onekeepass.mobile.events.common :refer [on-ok on-error]]
   [onekeepass.mobile.background :as bg]))

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
  "The key file from the private area of app is saved to a location picked by the user"
  [{:keys [full-file-name file-name]}]
  (bg/pick-key-file-to-save full-file-name file-name
                            (fn [api-response]
                              (when-not (on-error api-response #(dispatch [:key-file-save-error %]))
                                (dispatch [:common/message-snackbar-open "Key file saved"])))))

(defn delete-key-file
  "Deletes the key file from local copy. The arg 'file-name' is just name part and 
   it is not the full file path
  "
  [file-name]
  (bg/delete-key-file file-name (fn [api-response]
                                  (when-let [updated-key-files-list (on-ok api-response)]
                                    (dispatch [:key-files-loaded updated-key-files-list])
                                    (dispatch [:common/message-snackbar-open "Key file removed"])))))

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
                            (dispatch [:key-file-copied kf-info])
                            (dispatch [:key-file-form-generate-file-name-dialog false])
                            (dispatch [:common/message-snackbar-open "Key file generated"])))))

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
   ;; key-files-list is a list of maps 
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
         [:dispatch [:common/next-page :key-file-form "page.titles.keyFileForm"]]]}))

;; Any file picked by user is copied to app's private area in a backend api and then this event is called
(reg-event-fx
 :key-file-copied
 (fn [{:keys [db]} [_event-id kf-info]]
   (let [next-dipatch-kw (-> db (get-in [:key-file-form :dispatch-on-file-selection]))] 
     {:db (-> db (assoc-in [:key-file-form :selected-key-file-info] kf-info))
      :fx [[:dispatch [:load-imported-key-files]]
         ;; Navigate to the previous page after the user picked a file
           [:dispatch [:common/previous-page]]
           [:dispatch [:common/message-snackbar-open "Key file selected"]]
           (when next-dipatch-kw
             [:dispatch [next-dipatch-kw kf-info]])]})))

;; Called with the selected key file info as map and in turns navigates back to the previous 
;; and also passing the selected kf info
(reg-event-fx
 :set-selected-key-file-info
 (fn [{:keys [db]} [_event-id kf-info]]
   ;;kf-info is a map with file-name and full-file-name
   (let [next-dipatch-kw (-> db (get-in [:key-file-form :dispatch-on-file-selection]))]
     {:db (-> db (assoc-in [:key-file-form :selected-key-file-info] kf-info))
      :fx [(when next-dipatch-kw
             [:dispatch [next-dipatch-kw kf-info]])
           [:dispatch [:common/previous-page]]
           [:dispatch [:common/message-snackbar-open "Key file selected"]]]})))

;; Similar to :database-file-pick-error event in ns onekeepass.mobile.events.open-database
;; Need to make it share instead of copying as done here
(reg-event-fx
 :key-file-pick-error
 (fn [{:keys [_db]} [_event-id error]]
   ;; If the user cancels any file selection, 
   ;; the RN response is a error due to the use of promise rejecton in Native Module. And we can ignore that error 
   {:fx [(when-not (= "DOCUMENT_PICKER_CANCELED" (:code error))
           [:dispatch [:common/error-box-show "File Pick Error" error]])]}))

(reg-event-fx
 :key-file-save-error
 (fn [{:keys [_db]} [_event-id error]]
   ;; If the user cancels any file selection, 
   ;; the RN response is a error due to the use of promise rejecton in Native Module. And we can ignore that error 
   {:fx [(when-not (= "DOCUMENT_PICKER_CANCELED" (:code error))
           [:dispatch [:common/error-box-show "Key File Save Error" error]])]}))

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