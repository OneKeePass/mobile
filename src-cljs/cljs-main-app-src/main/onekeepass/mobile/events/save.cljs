(ns onekeepass.mobile.events.save
  (:require
   [clojure.string :as str]
   [onekeepass.mobile.background :as bg]
   [onekeepass.mobile.background-remote-server :as bg-rs]
   [onekeepass.mobile.constants :as const]
   [onekeepass.mobile.events.common :refer [active-db-key
                                            assoc-in-key-db
                                            current-database-file-name
                                            current-database-name
                                            current-db-disable-edit
                                            get-in-key-db on-error
                                            on-ok
                                            remote-db-key?]]
   [onekeepass.mobile.translation :refer [lstr-error-dlg-title lstr-error-dlg-text
                                          lstr-msg-dlg-title lstr-msg-dlg-text]]
   [re-frame.core :refer [dispatch reg-event-db reg-event-fx reg-fx
                          reg-sub subscribe]]))

(defn save-as-on-error []
  (if (bg/is-iOS)
    (dispatch [:ios-save-as-on-error])
    (dispatch [:android-save-as-on-error])))

(defn overwrite-on-save-error []
  (dispatch [:overwrite-on-save-error]))

(defn merge-on-save-error []
  (dispatch [:merge-on-save-error]))

(defn discard-on-save-error []
  (dispatch [:discard-on-save-error]))

(defn handle-save-error
  "Save time error requires some detailed error handling and we parse the returned
  error and take appropriate action
  The arg error may be a string or a map with keys: code,message
  "
  [{:keys [error error-title merge-save-called]}]
  ;;(println "Error " error error-title)
  (cond
    (= error "DbFileContentChangeDetected")
    (dispatch [:save-error-modal-show {:error-type :content-change-detected
                                       :error-title error-title
                                       :merge-save-called merge-save-called}]) ;;title error-type message

    (= error "NoRemoteStorageConnection")
    (dispatch [:save-error-modal-show {:error-type :no-remote-storage-connection
                                       :error-title error-title
                                       :merge-save-called merge-save-called}])

    ;; This happens when the file is removed or cloud service changes the reference after 
    ;; syncing from remote source. This invalidates the reference held by the app internally
    (= const/FILE_NOT_FOUND (:code error))
    (dispatch [:save-error-modal-show {:error-type :file-not-found
                                       :message (:message error)
                                       :error-title error-title
                                       :merge-save-called merge-save-called}])

    ;; Android only. The db file is opened through the 'Open with' action of another app
    ;; (OneDrive and the like) which grants read only access to the uri and saving back
    ;; to that app is not possible. 'Save as' is the way out for the user
    (= const/PERMISSION_REQUIRED_TO_WRITE (:code error))
    (dispatch [:save-error-modal-show {:error-type :permission-required-to-write
                                       :error-title error-title
                                       :merge-save-called merge-save-called}])

    ;; Any error or exception that might have happend while saving
    (= const/SAVE_CALL_FAILED (:code error))
    (dispatch [:save-error-modal-show {:error-type :save-call-failled
                                       :message (:message error)
                                       :error-title error-title
                                       :merge-save-called merge-save-called}])

    ;; This is iOS specific errors. Need to find a way how to test this
    (or (= const/COORDINATOR_CALL_FAILED (:code error))
        (= const/BOOK_MARK_STALE (:code error))
        (= const/BOOK_MARK_NOT_FOUND (:code error)))
    (dispatch [:save-error-modal-show {:error-type :ios-bookmark-error
                                       :message (:message error)
                                       :error-title error-title
                                       :merge-save-called merge-save-called}])
    ;; This happened in iOS simulator when the database file name was changed after
    ;; that was loaded and edited and tried to save. Not sure whether this will happen in device and 
    ;; in case of android
    ;; It seems the bookmark resolve gets the renamed uri whereas we continue to hold to the old uri (db-key)
    ;; TODO: Need to find a way to change the db-key from old to the new one from the bookmarks  
    ;; resolution and continue to proceed saving
    (and (bg/is-iOS) (= error "DbKeyNotFound"))
    (dispatch [:common/default-error (lstr-error-dlg-title 'invalidReference) (lstr-error-dlg-text 'dbMovedOrRenamed)])
    #_(dispatch [:save-error-modal-show {:error-type :unnown-error
                                         :message "Internal error"
                                         :error-title error-title}])

    ;; The 'error' is a map with keys code,message for all the native module rejections and
    ;; a string only for the errors coming from the core lib. The 'string?' check keeps a
    ;; map error from reaching 'starts-with?' which then fails with 'undefined is not a function'
    (and (string? error) (str/starts-with? error "UnRecoverableError"))
    (dispatch [:common/error-box-show "Error" error])

    :else
    (dispatch [:save-error-modal-show {:error-type :unnown-error
                                       :message (:message error)
                                       :error-title error-title
                                       :merge-save-called merge-save-called}])))

(defn save-api-response-handler
  [{:keys [error-title merge-save-called on-save-ok on-save-error]} api-response]
  ;; api-response :ok value is a map corresponding to struct KdbxSaved
  ;; Here we are checking only the :error key and :ok value is ignored
  (when-not (on-error api-response
                      (fn [error]
                        ;; When on-save-error is provided the caller takes full responsibility
                        ;; for surfacing the failure (see :save-error-merge-completed, which
                        ;; needs to show the merge-result-dialog before the save-error-modal).
                        (if on-save-error
                          (on-save-error error)
                          (handle-save-error {:error error
                                              :merge-save-called merge-save-called
                                              :error-title error-title}))))
    (dispatch [:common/message-modal-hide])
    (when on-save-ok (on-save-ok))))

;; Called from entry-form, group,settings page events to save any db specific changes
(reg-event-fx
 :save/save-current-kdbx
 (fn [{:keys [db]} [_event-id {:keys [save-message] :as m-data}]]
   ;;(println "In :save/save-current-kdbx")
   (let [save-enabled (not (current-db-disable-edit db))]
     ;;(println "In :save/save-current-kdbx save-enabled active-db " save-enabled (active-db-key db))
     (if save-enabled
       ;; m-data is a map with keys :save-message and :error-title
       (let [handler-fn (partial save-api-response-handler m-data)]
         ;; We need to hold on to the 'handler-fn' in :save-api-response-handler
         ;; as we may need to use when we need to call overwrite after 'Save error' resolution by user
         ;; See event ':overwrite-on-save-error' how this handler-fn is used
         {:db (-> db (assoc-in-key-db  [:save-api-response-handler] handler-fn))
          :fx [[:dispatch [:common/message-modal-show nil (if-not (nil? save-message) save-message 'saving)]]
               [:bg-save-kdbx [(active-db-key db) false handler-fn]]]})
       {:fx [[:dispatch [:common/message-modal-hide]]
             [:dispatch [:common/error-box-show (lstr-msg-dlg-title 'dbReadOnly)
                         (lstr-error-dlg-text 'editingDisabledReadOnly)]]]}))))

;; Calls the background save kdbx api
(reg-fx
 :bg-save-kdbx
 (fn [[db-key overwrite dispatch-fn]]
   ;; db-key is the full file name 
   (bg/save-kdbx db-key overwrite dispatch-fn)))

;;; Save as  ;;;
;; Sequences for save as  
;; :ios-save-as-on-error -> :bg-ios-save-as-on-error -> :ios-pick-save-as-on-error-completed
;; -> :bg-ios-complete-save-as-on-error -> :save-as-on-error-finished

(reg-event-fx
 :ios-save-as-on-error
 (fn [{:keys [db]} [_event-id]]
   {:fx [[:bg-ios-save-as-on-error [(current-database-file-name db) (active-db-key db)]]]}))

(defn- save-as-api-response-handler [api-reponse]
  (when-let [result (on-ok api-reponse #(dispatch [:pick-save-as-on-error-not-completed %]))]
    (dispatch [:ios-pick-save-as-on-error-completed result])))

(reg-fx
 :bg-ios-save-as-on-error
 (fn [[kdbx-file-name db-key]]
   (bg/ios-pick-on-save-error-save-as kdbx-file-name db-key save-as-api-response-handler)))

(reg-event-fx
 :ios-pick-save-as-on-error-completed
 (fn [{:keys [db]} [_event-id {:keys [file-name full-file-name-uri]}]]
   ;; kdbx-info is a map  with keys :file-name,:full-file-name-uri
   ;;(println "kdbx-info is " kdbx-info)
   {:fx [[:bg-ios-complete-save-as-on-error [(active-db-key db) full-file-name-uri file-name]]]}))

(reg-fx
 :bg-ios-complete-save-as-on-error
 (fn [[db-key new-db-key file-name]]
   ;; (println "db-key new-db-key file-name are " db-key new-db-key file-name)
   (bg/ios-complete-save-as-on-error db-key new-db-key file-name (fn [api-reponse]
                                                                   (when-let [kdbx-loaded (on-ok api-reponse)]
                                                                     (dispatch [:save-as-on-error-finished kdbx-loaded]))))))

;; Used for both  iOS and Abdroid
(reg-event-fx
 :pick-save-as-on-error-not-completed
 (fn [{:keys [_db]} [_event-id error]]
   ;; When user cancels the picking document, we receive the error with
   ;; DOCUMENT_PICKER_CANCELED and in that case, we will continue show the save error dialog 
   ;; Any other error will be shown in the error dialog
   {:fx (if (= "DOCUMENT_PICKER_CANCELED" (:code error))
          []
          [[:dispatch [:common/error-box-show (lstr-error-dlg-title 'saveAsError) error]]])}))

;; Used for both  iOS and Android
(reg-event-fx
 :save-as-on-error-finished
 (fn [{:keys [_db]} [_event-id kdbx-loaded]]
   {:fx [[:dispatch [:save-error-modal-hide]]
         [:dispatch [:common/kdbx-database-opened kdbx-loaded]]
         [:dispatch [:common/message-box-show (lstr-msg-dlg-title 'saveCompleted) (lstr-msg-dlg-text 'saveCompleted)]]]}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Android specfic Save as events ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(reg-event-fx
 :android-save-as-on-error
 (fn [{:keys [db]} [_event-id]]
   {:fx [[:bg-android-save-as-on-error [(current-database-file-name db)]]]}))

(reg-fx
 :bg-android-save-as-on-error
 (fn [[kdbx-file]]
   ;;(println "kdbx-file is " kdbx-file)
   (bg/android-pick-on-save-error-save-as kdbx-file (fn [api-reponse]
                                                      (when-let [result (on-ok
                                                                         api-reponse
                                                                         #(dispatch [:pick-save-as-on-error-not-completed %]))]
                                                        (dispatch [:android-pick-save-as-on-error-completed result]))))))

(reg-event-fx
 :android-pick-save-as-on-error-completed
 (fn [{:keys [db]} [_event-id {:keys [file-name full-file-name-uri]}]]
   {:fx [[:bg-android-complete-save-as-on-error [(active-db-key db) full-file-name-uri file-name]]]}))

(reg-fx
 :bg-android-complete-save-as-on-error
 (fn [[db-key new-db-key file-name]]
   ;;(println "db-key new-db-key are " db-key new-db-key)
   (bg/android-complete-save-as-on-error db-key new-db-key file-name (fn [api-reponse]
                                                                       (when-let [kdbx-loaded (on-ok api-reponse)]
                                                                         (dispatch [:save-as-on-error-finished kdbx-loaded]))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Overwrite
(reg-event-fx
 :overwrite-on-save-error
 (fn [{:keys [db]} [_event-id]]
   ;; (println "overwrite-on-save-error is called ...fn is " (get-in-key-db db [:save-api-response-handler]))
   ;; User explicitly resolved the conflict by overwriting — clear any
   ;; external-change Ignore snooze so future remote changes resurface.
   {:db (update db (active-db-key db) dissoc :external-change-ignored-mtime)
    :fx [[:dispatch [:common/message-modal-show nil 'overwritingDb]]
         [:dispatch [:save-error-modal-hide]]
         [:bg-save-kdbx [(active-db-key db) true (get-in-key-db db [:save-api-response-handler])]]
         #_[:bg-overwrite-kdbx [(active-db-key db)]]]}))


;;;; Discard and close
(reg-event-fx
 :discard-on-save-error
 (fn [{:keys [db]} [_event-id]]
   {:fx [[:dispatch [:save-error-modal-hide]]
         [:dispatch [:common/close-current-kdbx-db]]
         [:bg-save-conflict-resolution-cancel [(active-db-key db)]]]}))

;;;;;;;;;;;;;;;;;;;;;;;;;;; Save error modal ;;;;;;;;;;;;;;;;

#_(defn save-error-modal-hide []
    (dispatch [:save-error-modal-hide]))

(defn save-error-modal-cancel []
  (dispatch [:save-error-modal-cancel]))

(defn save-error-modal-data []
  (subscribe [:save-error-modal]))

(reg-event-fx
 :save-error-modal-show
 (fn [{:keys [db]} [_event-id {:keys [title error-type message merge-save-called]
                               :or {title "Save Error"}}]]
   (let [file-name (current-database-file-name db)]
     {:db (-> db
              (assoc-in [:save-error-modal :dialog-show] true)
              (assoc-in [:save-error-modal :title] title)
              (assoc-in [:save-error-modal :error-type] error-type)
              (assoc-in [:save-error-modal :error-message] message)
              (assoc-in [:save-error-modal :file-name] file-name)
              (assoc-in [:save-error-modal :remote-db?] (remote-db-key? (active-db-key db)))
              (assoc-in [:save-error-modal :merge-save-called] merge-save-called))

      :fx [[:dispatch [:common/message-modal-hide]]]})))

;;;;;;;;;;;;;;;;;;;;;;;;;;; Merge on save conflict ;;;;;;;;;;;;;;;;

(reg-event-fx
 :merge-on-save-error
 (fn [{:keys [db]} [_event-id]]
   ;; User explicitly resolved the conflict by merging — clear any
   ;; external-change Ignore snooze so future remote changes resurface.
   {:db (update db (active-db-key db) dissoc :external-change-ignored-mtime)
    :fx [[:dispatch [:save-error-modal-hide]]
         [:dispatch [:common/message-modal-show nil "Merging remote changes..."]]
         [:bg-save-error-merge-with-remote [(active-db-key db)]]]}))

(reg-fx
 :bg-save-error-merge-with-remote
 (fn [[db-key]]
   (bg-rs/merge-with-remote
    db-key
    (fn [api-response]
      (when-some [merge-result (on-ok api-response
                                      (fn [error]
                                        (dispatch [:save-error-merge-error error])))]
        (dispatch [:save-error-merge-completed merge-result]))))))

(reg-event-fx
 :save-error-merge-completed
 (fn [{:keys [_db]} [_event-id merge-result]]
   {:fx [[:dispatch [:save/save-current-kdbx
                     {:error-title "Save after remote changes merging"
                      :save-message "Merging and saving..."
                      :merge-save-called true
                      :on-save-ok (fn []
                                    (dispatch [:save-error-merge-save-completed merge-result]))
                      :on-save-error (fn [error]
                                       (dispatch [:save-error-merge-save-failed merge-result error]))}]]]}))

;; Auto-save after merge failed. Show the merge-result-dialog FIRST so the user
;; sees what was merged (the in-memory db has it, even though the upload failed),
;; and stash the save error to surface in the save-error-modal once the dialog
;; is closed.
(reg-event-fx
 :save-error-merge-save-failed
 (fn [{:keys [db]} [_event-id merge-result error]]
   {:db (assoc db :pending-merge-save-failure
               {:error error
                :error-title "Save after remote changes merging"})
    :fx [[:dispatch [:common/message-modal-hide]]
         [:dispatch [:generic-dialog-show-with-state
                     :merge-result-dialog
                     {:data merge-result
                      :stay-on-page? true
                      :show-save-error-after? true}]]]}))

(defn merge-result-dialog-close-and-show-save-error []
  (dispatch [:merge-result-dialog-close-and-show-save-error]))

(reg-event-fx
 :merge-result-dialog-close-and-show-save-error
 (fn [{:keys [db]} _]
   (let [{:keys [error error-title]} (:pending-merge-save-failure db)]
     {:db (dissoc db :pending-merge-save-failure)
      :fx [[:dispatch [:generic-dialog-close :merge-result-dialog]]
           [:run-handle-save-error {:error error
                                    :error-title error-title
                                    :merge-save-called true}]]})))

(reg-fx
 :run-handle-save-error
 (fn [m]
   (handle-save-error m)))

(reg-event-fx
 :save-error-merge-save-completed
 (fn [{:keys [db]} [_event-id merge-result]]
   {:fx [[:dispatch [:common/refresh-forms]]
         [:dispatch [:common/message-modal-hide]]
         [:dispatch [:common/next-page const/ENTRY_CATEGORY_PAGE_ID (current-database-name db)]]
         [:dispatch [:generic-dialog-show-with-state
                     :merge-result-dialog
                     {:data merge-result
                      :stay-on-page? true}]]]}))

(reg-event-fx
 :save-error-merge-error
 (fn [{:keys [db]} [_event-id error]]
   {:fx [[:dispatch [:common/message-modal-hide]]
         ;; Clean up the "backup on error" left from the initial failed save so
         ;; the user isn't stuck with leaked backup state when merge itself fails.
         [:bg-save-conflict-resolution-cancel [(active-db-key db)]]
         [:dispatch [:common/error-box-show 'mergeFailed error]]]}))

(reg-event-fx
 :save-error-modal-cancel
 (fn [{:keys [db]} [_event-id]]
   {:fx [;; Called to remove any backup files created during this save kdbx call. Needs removal as the save is cancelled
         [:bg-save-conflict-resolution-cancel [(active-db-key db)]]
         [:dispatch [:save-error-modal-hide]]]}))

(reg-fx
 :bg-save-conflict-resolution-cancel
 (fn [[db-key]]
   (bg/save-conflict-resolution-cancel db-key #())))

(reg-event-db
 :save-error-modal-hide
 (fn [db [_event-id]]
   (-> db (assoc-in [:save-error-modal] {})
       (assoc-in [:save-error-modal :dialog-show] false))))

(reg-sub
 :save-error-modal
 (fn [db _query-vec]
   (-> db :save-error-modal)))


(comment
  (in-ns 'onekeepass.mobile.events.save)
  (def db-key (-> @re-frame.db/app-db :current-db-file-name))
  (-> @re-frame.db/app-db (get db-key) keys))
