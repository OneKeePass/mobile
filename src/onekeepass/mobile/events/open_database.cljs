(ns onekeepass.mobile.events.open-database
  (:require
   [clojure.string :as str]
   [onekeepass.mobile.events.common :refer [on-ok]]
   [re-frame.core :refer [reg-event-db
                          reg-event-fx
                          reg-sub
                          dispatch
                          reg-fx
                          subscribe]]
   [onekeepass.mobile.background :as bg]))


#_(defn open-database-dialog-show []
    (dispatch [:open-database-dialog-show]))

(defn cancel-on-press []
  (dispatch [:open-database-dialog-hide]))

(defn open-database-on-press []
  (dispatch [:pick-database-file]))

(defn open-selected-database [file-name full-file-name-uri already-opened?]
  (if already-opened?
    (dispatch [:common/set-active-db-key full-file-name-uri])
    (dispatch [:open-database/database-file-picked {:file-name file-name :full-file-name-uri full-file-name-uri}])))

(defn database-field-update [kw-field-name value]
  (dispatch [:open-database-field-update kw-field-name value]))

(defn repick-confirm-close []
  (dispatch [:repick-confirm-close]))

(defn repick-confirm-data []
  (subscribe [:repick-confirm-data]))

(defn open-database-read-db-file []
  (dispatch [:open-database-read-db-file]))

(defn dialog-data []
  (subscribe [:open-database-dialog-data]))

(def blank-open-db  {:dialog-show false
                     :password-visible false
                     :error-fields {}
                     :database-file-name nil

                     :status nil
                     ;; For read/load kdbx args
                     :database-full-file-name nil
                     :password nil
                     :key-file-name nil})

(defn- init-open-database-data
  "Initializes all open db related values in the incoming main app db 
  and returns the updated main app db
  "
  [db]
  (assoc-in db [:open-database] blank-open-db))

(defn- validate-required-fields
  [db]
  (let [error-fields (cond-> {}
                       (str/blank? (get-in db [:open-database :password]))
                       (assoc :password "A valid password is required"))]
    error-fields))

#_(reg-event-db
   :open-database-dialog-show
   (fn [db [_event-id]]
     (-> db init-open-database-data (assoc-in [:open-database :dialog-show] true))))

(reg-event-db
 :open-database-dialog-hide
 (fn [db [_event-id]]
   (assoc-in  db [:open-database :dialog-show] false)))

(reg-event-db
 :open-database-field-update
 (fn [db [_event-id kw-field-name value]] ;; kw-field-name is single kw or a vec of kws
   (-> db
       (assoc-in (into [:open-database]
                       (if (vector? kw-field-name)
                         kw-field-name
                         [kw-field-name])) value)
       ;; Hide any previous api-error-text
       #_(assoc-in [:open-database :api-error-text] nil))))

(reg-event-fx
 :pick-database-file
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:bg-pick-database-file]]}))

(reg-fx
 :bg-pick-database-file
 (fn []
   (bg/pick-document-to-read-write
    (fn [api-response]
      (when-let [picked-response (on-ok
                                  api-response
                                  #(dispatch [:database-file-pick-error %]))]
        (dispatch [:open-database/database-file-picked picked-response]))))))

;; This will make dialog open status true
(reg-event-fx
 :open-database/database-file-picked
 (fn [{:keys [db]} [_event-id {:keys [file-name full-file-name-uri]}]]
   {:db (-> db init-open-database-data
            ;;database-file-name to show in the dialog
            (assoc-in [:open-database :database-file-name] file-name)
            (assoc-in [:open-database :database-full-file-name] full-file-name-uri)
            (assoc-in [:open-database :dialog-show] true))}))

(reg-event-fx
 :database-file-pick-error
 (fn [{:keys [_db]} [_event-id error]]
   ;; If the user cancels any file selection, 
   ;; the RN response is a error due to the use of promise rejecton in Native Module. And we can ignore that error 
   {:fx [(when-not (= "DOCUMENT_PICKER_CANCELED" (:code error))
           [:dispatch [:common/error-box-show "File Pick Error" error]])]}))

;; TODO: Need to initiate loading progress indication
(reg-event-fx
 :open-database-read-db-file
 (fn [{:keys [db]} [_event-id]]
   (let [error-fields (validate-required-fields db)
         errors-found (boolean (seq error-fields))]
     (if errors-found
       {:db (assoc-in db [:open-database :error-fields] error-fields)}
       {:db (-> db (assoc-in [:open-database :status] :in-progress))
        :fx [[:bg-load-kdbx [(get-in db [:open-database :database-full-file-name])
                             (get-in db [:open-database :password])
                             (get-in db [:open-database :key-file-name])]]]}))))

(reg-fx
 :bg-load-kdbx
 (fn [[db-file-name password key-file-name]]
   (bg/load-kdbx db-file-name password key-file-name (fn [api-response]
                                                       (when-let [kdbx-loaded
                                                                  (on-ok
                                                                   api-response
                                                                   (fn [error]
                                                                     (dispatch [:open-database-read-kdbx-error error])))]
                                                         (dispatch [:open-database-db-opened kdbx-loaded]))))))

(reg-event-fx
 :open-database-read-kdbx-error
 (fn [{:keys [db]} [_event-id error]]
   {:db (-> db (assoc-in [:open-database :error-fields] {})
            (assoc-in [:open-database :status] :completed))
    :fx (cond (= (:code error) "PERMISSION_REQUIRED_TO_READ")
              [[:dispatch [:repick-confirm-show]]
               [:dispatch [:open-database-dialog-hide]]]
              (= (:code error) "FILE_NOT_FOUND")
              [[:dispatch [:open-database-dialog-hide]]
               [:dispatch [:common/error-box-show "Database Open Error" (str "The database is no longer in that location." " You may open another one")]]]
              :else
              [[:dispatch [:common/error-box-show "Database Open Error" error]]])}))

(reg-event-fx
 :open-database-db-opened
 (fn [{:keys [db]} [_event-id kdbx-loaded]]
   {:db (-> db (assoc-in [:open-database :error-fields] {})
            (assoc-in [:open-database :status] :completed))
    :fx [[:dispatch [:open-database-dialog-hide]]
         [:dispatch [:common/kdbx-database-opened kdbx-loaded]]]}))

(reg-event-db
 :repick-confirm-show
 (fn [db [_event-id]]
   (let [file-name (get-in db [:open-database :database-file-name])]
     (-> db
         (assoc-in [:open-database :repick-confirm :dialog-show] true)
         (assoc-in [:open-database :repick-confirm :file-name] file-name)))))

(reg-event-fx
 :repick-confirm-close
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db (assoc-in [:open-database :repick-confirm] {:dialog-show false :file-name nil}))
    :fx [[:dispatch [:pick-database-file]]]}))

(reg-sub
 :repick-confirm-data
 (fn [db]
   (get-in db [:open-database :repick-confirm])))

(reg-sub
 :open-database-dialog-data
 (fn [db _query-vec]
   (get-in db [:open-database])))

(comment
  (in-ns 'onekeepass.mobile.events.open-database))