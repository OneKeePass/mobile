(ns onekeepass.mobile.events.save
  (:require 
   [onekeepass.mobile.events.common :refer [active-db-key
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


(defn handle-save-error
  "Save time error requires some detailed error handling and we parse the returned
  error and take appropriate action
  The arg error may be a string or a map with keys: code,message
  "
  [error error-title]
  (println "Called handle-save-error " error error-title)
  (cond
    (= error "DbFileContentChangeDetected")
    (dispatch [:save-error-modal-show {:error-type :content-change-detected :error-title error-title}]) ;;title error-type message

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

    :else
    (dispatch [:save-error-modal-show {:error-type :unnown-error
                                       :message (:message error)
                                       :error-title error-title}])))

;; Useful when we want to use (dispatch [...]) instead of using the following reg-fx
(reg-event-fx
 :save/send-error
 (fn [{:keys [_db]} [_event-id error]]
   {:fx [[:dispatch [:common/message-modal-hide]]
         ;; Need to route through the common save error handler
         [:save/handle-error [error nil]]]}))

(reg-fx
 :save/handle-error
 (fn [[error error-title]]
   (handle-save-error error error-title)))

(reg-event-fx
 :save/save-current-kdbx
 (fn [{:keys [db]} [_event-id {:keys [error-title save-message on-save-ok on-save-error]}]]
   {:fx [[:dispatch [:common/message-modal-show nil (if-not (nil? save-message) save-message "Saving ...")]]
         [:save/bg-save-kdbx [(active-db-key db)
                                (fn [api-response]
                                  (when-not (on-error api-response
                                                      (fn [error] 
                                                        (handle-save-error error error-title)
                                                        ;; on-save-error is not yet used
                                                        ;; Need to review its use and remove this
                                                        (when on-save-error (on-save-error error))))
                                    (dispatch [:common/message-modal-hide])
                                    (when on-save-ok (on-save-ok))))]]]}))

;; Calls the background save kdbx api
(reg-fx
 :save/bg-save-kdbx
 (fn [[db-key dispatch-fn]]
   ;;(println "In common/bg-save-kdbx db-key dispatch-fn " db-key dispatch-fn)
   ;; db-key is the full file name 
   (bg/save-kdbx db-key dispatch-fn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;; Save error modal ;;;;;;;;;;;;;;;;

(defn save-error-modal-hide []
  (dispatch [:save-error-modal-hide]))

(defn save-error-modal-data []
  (subscribe [:save-error-modal]))

(reg-event-fx
 :save-error-modal-show
 (fn [{:keys [db]} [_event-id {:keys [title error-type message]
                               :or {title "Save Error"}}]]
   {:db (-> db
            (assoc-in [:save-error-modal :dialog-show] true)
            (assoc-in [:save-error-modal :title] title)
            (assoc-in [:save-error-modal :error-type] error-type)
            (assoc-in [:save-error-modal :error-message] message))
    :fx [[:dispatch [:common/message-modal-hide]]]}))

(reg-event-db
 :save-error-modal-hide
 (fn [db [_event-id]]
   (-> db (assoc-in [:save-error-modal] {})
       (assoc-in [:save-error-modal :dialog-show] false))))

(reg-sub
 :save-error-modal
 (fn [db _query-vec]
   (-> db :save-error-modal)))