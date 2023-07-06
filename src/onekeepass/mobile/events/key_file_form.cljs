(ns onekeepass.mobile.events.key-file-form
  (:require
   [re-frame.core :refer [reg-event-db
                          reg-event-fx
                          reg-fx
                          reg-sub
                          dispatch
                          dispatch-sync
                          subscribe]]

   [onekeepass.mobile.events.common :refer [on-ok on-error]]

   [onekeepass.mobile.background :as bg]))

(defn load-key-files []
  (dispatch [:key-file-form/load-imported-key-files]))

(defn pick-key-file []
  (bg/pick-key-file-to-copy (fn [api-response]
                              (when-let [full-file-name
                                         (on-ok
                                          api-response
                                          #(dispatch [:key-file-pick-error %]))]
                                ;; User picked a key file and needs to be copied
                                (bg/copy-key-file
                                 full-file-name
                                 (fn [res]
                                   (when-let [kf-info (on-ok res)]
                                     (dispatch [:key-file-copied kf-info]))))))))

(defn delete-key-file
  "Deletes the key file from local copy. The arg 'file-name' is just name part and 
   it is not the full file path
  "
  [file-name]
  (bg/delete-key-file file-name (fn [api-response]
                                  (when-let [updated-key-files-list (on-ok api-response)]
                                    (dispatch [:key-files-loaded updated-key-files-list])))))

(defn imported-key-files []
  (subscribe [:imported-key-files]))

(reg-event-fx
 :key-file-form/load-imported-key-files
 (fn [{:keys [db]} [_event-id]]
   (bg/list-key-files (fn [api-response]
                        (when-let [key-files-list (on-ok api-response)]
                          (dispatch [:key-files-loaded key-files-list]))))
   {}))

(reg-event-db
 :key-files-loaded
 (fn [db [_event-id key-files-list]]
   ;; key-files-list is a list of maps 
   (assoc-in db [:key-file-form :key-files] key-files-list)))

;; We may need to split this event into two events so that we can load all imported key files and then 
;; navigate to that page
(reg-event-fx
 :key-file-form/show
 (fn [{:keys [db]} [_event-id dispatch-on-file-selection]]
   {:db (-> db (assoc-in [:key-file-form :dispatch-on-file-selection] dispatch-on-file-selection))
    :fx [[:dispatch [:key-file-form/load-imported-key-files]]
         [:dispatch [:common/next-page :key-file-form "page.titles.keyFileForm"]]]}))

(reg-event-fx
 :key-file-copied
 (fn [{:keys [db]} [_event-id kf-info]]
   (let [next-dipatch-kw (-> db (get-in [:key-file-form :dispatch-on-file-selection]))]
     (println "next-dipatch-kw is " next-dipatch-kw)
     {:db (-> db (assoc-in [:key-file-form :selected-key-file-info] kf-info))
      :fx [[:dispatch [:key-file-form/load-imported-key-files]]
         ;; Navigate to the previous page after the user picked a file
           [:dispatch [:common/previous-page]]
           (when next-dipatch-kw
             [:dispatch [next-dipatch-kw kf-info]])]})))

#_(reg-event-fx
   :key-file-form/set-selected-key-file-info
   (fn [{:keys [db]} [_event-id kf-info]]
   ;;kf-info is a map with file-name and full-file-name
     {:db (-> db (assoc-in [:key-file-form :selected-key-file-info] kf-info))}))

#_(reg-event-fx
   :remove-key-file
   (fn [{:keys [db]} [_event-id file-name]]))

;; Similar to :database-file-pick-error event in ns onekeepass.mobile.events.open-database
;; Need to make it share instead of copying as done here
(reg-event-fx
 :key-file-pick-error
 (fn [{:keys [_db]} [_event-id error]]
   ;; If the user cancels any file selection, 
   ;; the RN response is a error due to the use of promise rejecton in Native Module. And we can ignore that error 
   {:fx [(when-not (= "DOCUMENT_PICKER_CANCELED" (:code error))
           [:dispatch [:common/error-box-show "File Pick Error" error]])]}))

(reg-sub
 :imported-key-files
 (fn [db _query-vec]
   (get-in db [:key-file-form :key-files])))

#_(reg-sub
   :key-file-form/selected-key-file-info
   (fn [db]
     (-> db (get-in [:key-file-form :selected-key-file-info]))))


(comment
  (in-ns 'onekeepass.mobile.events.key-file-form)

  (def db-key (-> @re-frame.db/app-db :current-db-file-name))
  (-> @re-frame.db/app-db (get db-key) keys))