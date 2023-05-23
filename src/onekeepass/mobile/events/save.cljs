(ns onekeepass.mobile.events.save
  (:require
   [onekeepass.mobile.events.common :refer [active-db-key
                                            current-database-file-name
                                            assoc-in-key-db
                                            get-in-key-db
                                            on-ok
                                            on-error]]
   [re-frame.core :refer [reg-event-db
                          reg-event-fx
                          reg-sub
                          dispatch
                          reg-fx
                          subscribe]]
   [onekeepass.mobile.background :as bg]))

(defn save-as-on-error []
  (if (bg/is-iOS)
    (dispatch [:ios-save-as-on-error])
    (dispatch [:android-save-as-on-error])))

(defn overwrite-on-save-error []
  (dispatch [:overwrite-on-save-error]))

(defn discard-on-save-error []
  (dispatch [:discard-on-save-error]))

(defn handle-save-error
  "Save time error requires some detailed error handling and we parse the returned
  error and take appropriate action
  The arg error may be a string or a map with keys: code,message
  "
  [error error-title]
  (cond
    (= error "DbFileContentChangeDetected")
    (dispatch [:save-error-modal-show {:error-type :content-change-detected
                                       :error-title error-title}]) ;;title error-type message

    ;; This happens when the file is removed or cloud service changes the reference after 
    ;; syncing from remote source. This invalidates the reference help by okp
    (= "FILE_NOT_FOUND" (:code error))
    (dispatch [:save-error-modal-show {:error-type :file-not-found
                                       :message (:message error)
                                       :error-title error-title}])

    ;; Any error or exception that might have happend while saving
    (= "SAVE_CALL_FAILED" (:code error))
    (dispatch [:save-error-modal-show {:error-type :save-call-failled
                                       :message (:message error)
                                       :error-title error-title}])

    ;; This is iOS specific errors. Need to find a way how to test this
    (or (= "COORDINATOR_CALL_FAILED" (:code error))
        (= "BOOK_MARK_STALE" (:code error))
        (= "BOOK_MARK_NOT_FOUND" (:code error)))
    (dispatch [:save-error-modal-show {:error-type :ios-bookmark-error
                                       :message (:message error)
                                       :error-title error-title}])
    ;; This happened in iOS simulator when the database file name was changed after
    ;; that was loaded and edited and tried to save. Not sure whether this will happen in device and 
    ;; in case of android
    ;; It seems the bookmark resolve gets the renamed uri whereas we continue to hold to the old uri (db-key)
    ;; TODO: Needs to find a way to change the db-key from old to the new one from the bookmarks  
    ;; resolution and continue to proceed saving
    (and (bg/is-iOS) (= error "DbKeyNotFound"))
    (dispatch [:common/default-error "Invalid reference" "It appears the database file might have been moved or renamed. Please remove the database and reopen the moved or renamed file"])
    #_(dispatch [:save-error-modal-show {:error-type :unnown-error
                                         :message "Internal error"
                                         :error-title error-title}])

    :else
    (dispatch [:save-error-modal-show {:error-type :unnown-error
                                       :message (:message error)
                                       :error-title error-title}])))

(defn save-api-response-handler
  [{:keys [error-title on-save-ok on-save-error]} api-response]
  ;; (println "api-response " api-response)
  ;; api-response :ok value is a map corresponding to struct KdbxSaved
  ;; Here we are checking only the :error key and :ok value is ignored
  (when-not (on-error api-response
                      (fn [error]
                        (handle-save-error error error-title)
                         ;; on-save-error is not yet used
                         ;; Need to review its use and remove this
                        (when on-save-error (on-save-error error))))
    (dispatch [:common/message-modal-hide])
    (when on-save-ok (on-save-ok))))

(reg-event-fx
 :save/save-current-kdbx
 (fn [{:keys [db]} [_event-id {:keys [save-message] :as m-data}]]
   (let [handler-fn (partial save-api-response-handler m-data)]
     ;; We need to hold on to the 'handler-fn' in :save-api-response-handler
     ;; as we may need to use when we need to call overwrite after 'Save error' resolution by user
     ;; See event ':overwrite-on-save-error' how this handler-fn is used
     {:db (-> db (assoc-in-key-db  [:save-api-response-handler] handler-fn))
      :fx [[:dispatch [:common/message-modal-show nil (if-not (nil? save-message) save-message "Saving ...")]]
           [:bg-save-kdbx [(active-db-key db) false handler-fn]]]})))

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
   {:fx [[:bg-ios-save-as-on-error [(current-database-file-name db)(active-db-key db)]]]}))

(defn- save-as-api-response-handler [api-reponse]
  (when-let [result (on-ok api-reponse #(dispatch [:pick-save-as-on-error-not-completed %]))]
    (dispatch [:ios-pick-save-as-on-error-completed result])))

(reg-fx
 :bg-ios-save-as-on-error
 (fn [[ kdbx-file-name db-key]]
   (bg/ios-pick-on-save-error-save-as kdbx-file-name db-key save-as-api-response-handler)))

(reg-event-fx
 :ios-pick-save-as-on-error-completed
 (fn [{:keys [db]} [_event-id {:keys [file-name full-file-name-uri]}]]
   ;; kdbx-info is a map  with keys :file-name,:full-file-name-uri
   ;;(println "kdbx-info is " kdbx-info)
   {:fx [[:bg-ios-complete-save-as-on-error [(active-db-key db) full-file-name-uri]]]}))

(reg-fx
 :bg-ios-complete-save-as-on-error
 (fn [[db-key new-db-key]]
   ;;(println "db-key new-db-key are " db-key new-db-key)
   (bg/ios-complete-save-as-on-error db-key new-db-key (fn [api-reponse]
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
          [[:dispatch [:common/error-box-show "Save as Error" error]]])}))

;; Used for both  iOS and Abdroid
(reg-event-fx
 :save-as-on-error-finished
 (fn [{:keys [_db]} [_event-id kdbx-loaded]]
   {:fx [[:dispatch [:save-error-modal-hide]]
         [:dispatch [:common/kdbx-database-opened kdbx-loaded]]
         [:dispatch [:common/message-box-show "Save completed" "The newly saved database is loaded now"]]]}))

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
   {:fx [[:bg-android-complete-save-as-on-error [(active-db-key db) full-file-name-uri]]]}))

(reg-fx
 :bg-android-complete-save-as-on-error
 (fn [[db-key new-db-key]]
   ;;(println "db-key new-db-key are " db-key new-db-key)
   (bg/android-complete-save-as-on-error db-key new-db-key (fn [api-reponse]
                                                             (when-let [kdbx-loaded (on-ok api-reponse)]
                                                               (dispatch [:save-as-on-error-finished kdbx-loaded]))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Overwrite
(reg-event-fx
 :overwrite-on-save-error
 (fn [{:keys [db]} [_event-id]]
   ;; (println "overwrite-on-save-error is called ...fn is " (get-in-key-db db [:save-api-response-handler]))
   {:fx [[:dispatch [:common/message-modal-show nil  "Overwriting the database ..."]]
         [:dispatch [:save-error-modal-hide]]
         [:bg-save-kdbx [(active-db-key db) true (get-in-key-db db [:save-api-response-handler])]]
         #_[:bg-overwrite-kdbx [(active-db-key db)]]]}))


;;;; Discard and close
(reg-event-fx
 :discard-on-save-error
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:dispatch [:save-error-modal-hide]]
         [:dispatch [:common/close-current-kdbx-db]]]}))

;;;;;;;;;;;;;;;;;;;;;;;;;;; Save error modal ;;;;;;;;;;;;;;;;

(defn save-error-modal-hide []
  (dispatch [:save-error-modal-hide]))

(defn save-error-modal-data []
  (subscribe [:save-error-modal]))

(reg-event-fx
 :save-error-modal-show
 (fn [{:keys [db]} [_event-id {:keys [title error-type message]
                               :or {title "Save Error"}}]]
   (let [file-name (current-database-file-name db)]
     {:db (-> db
              (assoc-in [:save-error-modal :dialog-show] true)
              (assoc-in [:save-error-modal :title] title)
              (assoc-in [:save-error-modal :error-type] error-type)
              (assoc-in [:save-error-modal :error-message] message)
              (assoc-in [:save-error-modal :file-name] file-name))

      :fx [[:dispatch [:common/message-modal-hide]]]})))

(reg-event-db
 :save-error-modal-hide
 (fn [db [_event-id]]
   (-> db (assoc-in [:save-error-modal] {})
       (assoc-in [:save-error-modal :dialog-show] false))))

(reg-sub
 :save-error-modal
 (fn [db _query-vec]
   (-> db :save-error-modal)))