(ns onekeepass.mobile.events.merging
  (:require [onekeepass.mobile.background.merging :as bg-merging]
            [onekeepass.mobile.constants :as const]
            [onekeepass.mobile.constants :refer [MERGE_DATABASE_PAGE_ID]]
            [onekeepass.mobile.events.common :refer [on-error
                                                     on-ok
                                                     opened-db-file-name
                                                     active-db-key
                                                     opened-db-list]]
            [re-frame.core :refer [dispatch reg-event-fx reg-fx
                                   reg-sub subscribe]]))


(defn merging-databases-page []
  (dispatch [:merging-databases-page]))

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
   {:fx [[;; determines which event is to be called after kdbx loading
          ;; See 
          :dispatch [:open-database/new-merging-source-db-wanted true]]
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
                                 (when-some [merge-result (on-ok api-response)]
                                   (dispatch [:merge-databases-save merge-result]))))))

;; Merged target database is saved. In mobile, we always save after any db content changes
(reg-event-fx
 :merge-databases-save
 (fn [{:keys [_db]} [_event-id merge-result]]
   {:fx [[:dispatch [:save/save-current-kdbx 
                     {:error-title "Save after databases merging"
                      :save-message "Merging and saving..."
                      :on-save-ok (fn []
                                    (dispatch [:merge-databases-completed merge-result]))}]]]}))

(reg-event-fx
 :merge-databases-completed
 (fn [{:keys [db]} [_event-id merge-result]] 
   (let [kdbx-loaded (get-in db [:merging :kdbx-loaded])]
     {:fx [[:dispatch [:common/refresh-forms]]
           (when-not (nil? kdbx-loaded)
             [:common/bg-close-kdbx [(:db-key kdbx-loaded) #(on-error %)]])
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