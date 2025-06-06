(ns onekeepass.mobile.events.merging
  (:require [onekeepass.mobile.background.merging :as bg-merging]
            [onekeepass.mobile.constants :as const :refer [MERGE_DATABASE_PAGE_ID]]
            [onekeepass.mobile.events.common :refer [on-error
                                                     on-ok
                                                     opened-db-file-name
                                                     active-db-key
                                                     drop-rs-pages
                                                     opened-db-list]]
            [re-frame.core :refer [dispatch reg-event-fx reg-fx
                                   reg-sub subscribe]]))


(defn merging-databases-page []
  (dispatch [:merging-databases-page]))

(defn merge-database-back-action []
  ;; IMPORTANT: This flag should be reset ( i.e false) for normal open db dialog to work
  (dispatch [:open-database/new-merging-source-db-wanted false])
  (dispatch [:common/previous-page]))

(defn merging-databases-source-db-selected [source-db-key]
  (dispatch [:merging-databases-source-db-selected source-db-key]))

(defn merging-databases-open-new-db-source-start
  "Called to set this flag which determines which event is to be called after kdbx loading"
  []
  (dispatch [:merging-databases-open-new-db-source-start]))

(defn merging-databases-opened-db-list []
  (subscribe [:merging-databases-opened-db-list]))

(defn merging-target-db-file-name []
  (subscribe [:merging-target-db-file-name]))

;; Navigates to the page
(reg-event-fx
 :merging-databases-page
 (fn [{:keys [db]} [_event-id]]
   (let [db-key (active-db-key db)
         target-db-file-name (opened-db-file-name db db-key)]
     ;; The current database is the target database for merging
     {:db (-> db (assoc-in [:merging]  {:target-db-file-name target-db-file-name
                                        :target-db-key db-key}))
      :fx [[:dispatch [:common/next-page MERGE_DATABASE_PAGE_ID "mergeDatabases"]]]})))

;; This is called when user picked a opened database from the list as source db for merging
(reg-event-fx
 :merging-databases-source-db-selected
 (fn [{:keys [db]} [_event-id source-db-key]]
   (let [target-db-key  (get-in db [:merging :target-db-key])]
     {:db (-> db (assoc-in [:merging :source-db-key] source-db-key)
              (assoc-in [:merging :target-db-key] target-db-key))
      :fx [[:dispatch [:common/message-modal-show nil "Merging"]]
           [:bg-merge-databases [target-db-key source-db-key]]]})))

;; The event handler is called 
;; when user wants to open a new source database from which to merge instead of picking one from any opened databases
(reg-event-fx
 :merging-databases-open-new-db-source-start
 (fn [{:keys [_db]} [_event-id]]
   {:fx [;; Ensures that open db dialog 'continue' event handler calls this event based on this flag 
         [:dispatch [:open-database/new-merging-source-db-wanted true]]
         ;; Shows the storage selection option dialog
         [:dispatch [:generic-dialog-show-with-state
                     :start-page-storage-selection-dialog
                     {:kw-browse-type const/BROWSE-TYPE-DB-OPEN}]]]}))

;; Called from event handler ':open-database-db-opened' after user opens a new source kdbx
(reg-event-fx
 :merging-databases/kdbx-database-opened
 (fn [{:keys [db]} [_event-id kdbx-loaded]]
   (let [target-db-key  (get-in db [:merging :target-db-key]) #_(active-db-key db)
         source-db-key (:db-key kdbx-loaded)]
     {:db (-> db
              (assoc-in [:merging :source-db-key] source-db-key)
              (assoc-in [:merging :target-db-key] target-db-key)
              (assoc-in [:merging :kdbx-loaded] kdbx-loaded))
      :fx [[:dispatch [:common/message-modal-show nil "Merging"]]
           [:bg-merge-databases [target-db-key source-db-key]]]})))

(reg-fx
 :bg-merge-databases
 (fn [[target-db-key source-db-key]]
   (bg-merging/merge-databases target-db-key
                               source-db-key
                               (fn [api-response]
                                 (when-some [{:keys [merge-done] :as merge-result} (on-ok api-response)]
                                   ;; IMPORTANT: This flag should be reset for normal open db dialog to work. Otherwise merging handler is called which we do not want to happen
                                   (dispatch [:open-database/new-merging-source-db-wanted false])
                                   (if merge-done
                                     (dispatch [:merge-databases-save merge-result])
                                     (dispatch [:merge-databases-completed merge-result])))))))

;; Merged target database is saved. In mobile, we always save after any db content changes
(reg-event-fx
 :merge-databases-save
 (fn [{:keys [_db]} [_event-id merge-result]]
   {:fx [[:dispatch [:save/save-current-kdbx
                     {:error-title "Save after databases merging"
                      :save-message "Merging and saving..."
                      :merge-save-called true
                      :on-save-ok (fn []
                                    (dispatch [:merge-databases-completed merge-result]))}]]]}))

(reg-event-fx
 :merge-databases-completed
 (fn [{:keys [db]} [_event-id merge-result]]
   (let [kdbx-loaded (get-in db [:merging :kdbx-loaded])
         ;; There is a possibility that user might have opened the source db 
         ;; from Sftp or webdav location. In that case we need to skip all remote server pages
         ;; after the merge is completed and make sure that MERGE_DATABASE_PAGE_ID is top in the page stack
         db (drop-rs-pages db)]
     {:db db
      :fx [[:dispatch [:common/refresh-forms]]
           (when-not (nil? kdbx-loaded)
             ;; The opened db is closed as it is opened only as a merging source db
             [:common/bg-close-kdbx [(:db-key kdbx-loaded) #(on-error %)]]) 
           [:dispatch [:common/message-modal-hide]]
           [:dispatch [:generic-dialog-show-with-state :merge-result-dialog {:data merge-result}]]]})))

(reg-sub
 :merging-databases-opened-db-list
 (fn [db [_event-id]]
   (let [source-db-file-name (get-in db [:merging :target-db-file-name])
         dbs (-> db opened-db-list) 
         dbs (mapv (fn [m] (select-keys m [:db-key :database-name :file-name])) dbs)
         dbs (filterv (fn [{:keys [file-name]}] (not= file-name source-db-file-name)) dbs)]
     dbs)))

(reg-sub
 :merging-target-db-file-name
 (fn [db [_event-id]]
   (get-in db [:merging :target-db-file-name])))

(comment
  (in-ns 'onekeepass.mobile.events.merging))