(ns onekeepass.mobile.events.move-delete
  (:require
   [onekeepass.mobile.events.common :refer [active-db-key
                                            assoc-in-key-db
                                            get-in-key-db
                                            on-error]]
   [re-frame.core :refer [reg-event-db
                          reg-event-fx
                          reg-sub
                          dispatch
                          reg-fx
                          subscribe]]
   [onekeepass.mobile.background :as bg]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Entry delete ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;   

(reg-event-fx
 :move-delete/entry-delete-start
 (fn [{:keys [db]} [_event-id entry-id from-entry-form?]]
   {:db (-> db (assoc-in-key-db [:entry-delete :from-entry-form] from-entry-form?))
    :fx [[:dispatch [:common/message-modal-show nil 'deletingEntry]]
         [:bg-move-entry-to-recycle-bin [(active-db-key db) entry-id]]]}))

(defn- on-entry-delete [api-response]
  (when (not (on-error api-response (fn [e]
                                      (dispatch [:entry-delete-error e]))))
    (dispatch [:entry-delete-completed])))

(reg-fx
 :bg-move-entry-to-recycle-bin
 (fn [[db-key entry-id]]
   (bg/move-entry-to-recycle-bin db-key entry-id on-entry-delete)))

(reg-event-db
 :entry-delete-error
 (fn [db [_event-id error-text]]
   (-> db (assoc-in-key-db [:entry-delete :api-error-text] error-text)
       (assoc-in-key-db [:entry-delete :status] :completed))))

(reg-event-fx
 :entry-delete-completed
 (fn [{:keys [db]} [_event-id]]
   (let [from-entry-form? (get-in-key-db db [:entry-delete :from-entry-form])]
     {:db (-> db (assoc-in-key-db  [:entry-delete :status] :completed)
              (assoc-in-key-db  [:entry-delete :from-entry-form] false))
      ;; calls to refresh entry list and category  
      :fx [[:dispatch [:save/save-current-kdbx {:error-title "Save entry delete"
                                                :save-message "Delete and Saving...."}]]
           [:dispatch [:common/refresh-forms]]
           (when from-entry-form?
             ;; Need to close the entry-form as delete is called from the forms's menu
             [:dispatch [:common/previous-page]])]})))

;;;;;;;;;;;;;;;;;;;;;;;;; Entry delete End ;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;; Group delete ;;;;;;;;;;;;;;;;;;;

(reg-event-fx
 :move-delete/group-delete-start
 (fn [{:keys [db]} [_event-id group-id from-group-page]]
   ;; from-group-page is true if the group delete is called from 
   ;; the page where this group's subgroups and and entries are shown
   ;; Currently 'from-group-page' is not yet used and will be used once we add 'Delete' to header menu in a page 
   ;; showing the group's content
   {:db (-> db (assoc-in-key-db [:group-delete :from-group-page] from-group-page))
    :fx [[:dispatch [:common/message-modal-show nil 'deletingGroup]]
         [:bg-move-group-to-recycle-bin [(active-db-key db) group-id]]]}))

(defn- on-group-delete [api-response]
  (when-not (on-error api-response)
    (dispatch [:group-delete-completed])))

(reg-fx
 :bg-move-group-to-recycle-bin
 (fn [[db-key group-id]]
   (bg/move-group-to-recycle-bin db-key group-id on-group-delete)))

(reg-event-fx
 :group-delete-completed
 (fn [{:keys [db]} [_event-id]]
   (let [from-group-page (get-in-key-db db [:group-delete :from-group-page])]
     {:db (-> db (assoc-in-key-db  [:group-delete :from-group-page] false))
      :fx [[:dispatch [:save/save-current-kdbx {:error-title "Save group delete"
                                                :save-message "Delete and Saving...."
                                                :on-save-ok (fn []
                                                              (dispatch [:common/refresh-forms])
                                                              (dispatch [:common/message-snackbar-open 'groupOrCatDeleted])
                                                              (when from-group-page
                                                                ;; Need to go back to the previous page as group delete 
                                                                ;; is called from the group's header menu
                                                                (dispatch [:common/previous-page])))}]]]})))


;;;;;;;;;;;;;;;;;;;;;;; Put Back ;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn update-putback-dialog-parent-group [group-info]
  (dispatch [:putback-dialog-parent-group-update group-info]))

(defn hide-putback-dialog []
  (dispatch [:putback-dialog-hide]))

(defn open-putback-dialog [uuid]
  (dispatch [:putback-dialog-open uuid]))

(defn on-put-back-dialog-ok []
  (dispatch [:putback-dialog-ok]))

(defn putback-dialog-data []
  (subscribe [:putback-dialog-data]))

(defn groups-listing []
  (subscribe [:groups/listing]))

(def put-back-dialog-init-data {:dialog-show false
                                :title "Put back"
                                :parent-group-name nil
                                :parent-group-info nil
                                :uuid nil  ;;  uuid  of the entry that we want to put back
                                :error-fields {}  ;; key is the section-name  
                                })

(defn- init-put-back-dialog-data [db]
  (assoc-in-key-db db [:move-delete :putback-dialog-data] put-back-dialog-init-data))

(reg-event-db
 :putback-dialog-open
 (fn [db [_query-id uuid]]
   (-> db init-put-back-dialog-data
       (assoc-in-key-db [:move-delete :putback-dialog-data :dialog-show] true)
       (assoc-in-key-db [:move-delete :putback-dialog-data :uuid] uuid))))

(reg-event-db
 :putback-dialog-parent-group-update
 (fn [db [_event-id  group-info]]
   (-> db (assoc-in-key-db  [:move-delete :putback-dialog-data :parent-group-info] group-info)
       (assoc-in-key-db [:move-delete :putback-dialog-data :parent-group-name] (:name group-info)))))

(reg-event-db
 :putback-dialog-hide
 (fn [db [_event-id]]
   (-> db init-put-back-dialog-data)))

(reg-event-fx
 :putback-dialog-ok
 (fn [{:keys [db]} [_event-id]]
   (let [group-info (get-in-key-db db [:move-delete :putback-dialog-data :parent-group-info])
         uuid (get-in-key-db db [:move-delete :putback-dialog-data :uuid])
         error-fields (if (nil? group-info) {:parent-group-info "Valid parent group/category selection is required"} {})]
     (if-not (empty? error-fields)
       {:db (-> db (assoc-in-key-db  [:move-delete :putback-dialog-data :error-fields] error-fields))}
       {:fx [[:bg-move [(active-db-key db) uuid (:uuid group-info)]]]}))))

(defn- on-move-complete [api-response]
  (when (not (on-error api-response))
    (dispatch [:move-group-entry-completed])))

(reg-fx
 :bg-move
 (fn [[db-key id parent-group-uuid]]
   (bg/move-entry db-key id parent-group-uuid on-move-complete)))

(reg-event-fx
 :move-group-entry-completed
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db init-put-back-dialog-data)
    :fx [[:dispatch [:save/save-current-kdbx {:error-title "Put back and save"
                                              :save-message "Moved entry and Saving...."}]]
         [:dispatch [:common/refresh-forms]]]}))

(reg-sub
 :putback-dialog-data
 (fn [db _query-vec]
   (get-in-key-db db [:move-delete :putback-dialog-data])))

;;;;;;;;;;;;;;;;;;;;;;;; Delete permanently   ;;;;;;;;;;;;;;;;;;;;

(defn openn-delete-permanent-dialog [uuid]
  (dispatch [:delete-permanent-dialog-open uuid]))

(defn hide-delete-permanent-dialog []
  (dispatch [:delete-permanent-dialog-hide]))

(defn on-delete-permanent-dialog-ok []
  (dispatch [:delete-permanent-dialog-ok]))

(defn delete-permanent-dialog-data []
  (subscribe [:delete-permanent-dialog-data]))

(reg-event-db
 :delete-permanent-dialog-open
 (fn [db [_query-id uuid]]
   (-> db (assoc-in-key-db [:move-delete :delete-permanent-dialog-data]  {:dialog-show true :uuid uuid}))))

(reg-event-db
 :delete-permanent-dialog-hide
 (fn [db [_query-id]]
   (-> db (assoc-in-key-db [:move-delete :delete-permanent-dialog-data]  {:dialog-show false :uuid nil}))))

(reg-event-fx
 :delete-permanent-dialog-ok
 (fn [{:keys [db]} [_event-id]]
   {:fx [[:bg-permanent-delete [(active-db-key db) (get-in-key-db db [:move-delete :delete-permanent-dialog-data :uuid])]]]}))

(reg-event-fx
 :delete-permanent-completed
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db (assoc-in-key-db [:move-delete :delete-permanent-dialog-data]  {:dialog-show false :uuid nil}))
    :fx [[:dispatch [:save/save-current-kdbx {:error-title "Permanet delete"
                                              :save-message "Entry deleted permanently and Saving...."}]]
         [:dispatch [:common/refresh-forms]]]}))

(reg-fx
 :bg-permanent-delete
 (fn [[db-key id]]
   (bg/remove-entry-permanently db-key id (fn [api-response]
                                            (when-not (on-error api-response)
                                              (dispatch [:delete-permanent-completed]))))))

(reg-sub
 :delete-permanent-dialog-data
 (fn [db _query-vec]
   (get-in-key-db db [:move-delete :delete-permanent-dialog-data])))

;;;;;;;;;;;;;;;;;; Delete all entries permanently (Empty trash) ;;;;;;;;;;;;;;;;;;;

(reg-event-fx
 :move-delete/delete-all-entries-start
 (fn [{:keys [db]} [_event-id]]
   {:fx [[:dispatch [:common/message-modal-show nil 'deletingAllEntries]]
         [:bg-empty-trash [(active-db-key db)]]]}))

(defn- on-delete-all-entries [api-response]
  (when-not (on-error api-response #(dispatch [:common/default-error "Error in deleting all entries" %]))
    (dispatch [:delete-all-entries-completed])))

(reg-fx
 :bg-empty-trash
 (fn [[db-key]]
   (bg/empty-trash db-key on-delete-all-entries)))

(reg-event-fx
 :delete-all-entries-completed
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:dispatch [:save/save-current-kdbx {:error-title "Save after deleting all entries"
                                              :save-message "Deleted permanently and Saving"
                                              :on-save-ok (fn []
                                                            (dispatch [:common/refresh-forms])
                                                            (dispatch [:common/message-snackbar-open 'entriesDeletedPermanently])
                                                            (dispatch [:common/previous-page]))}]]]}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Uses generic dialogs based features. 

(defn move-entry-or-group
  "The arg id is either entry uuid or group uuid"
  [kind-kw id group-selection-info] 
  (dispatch [:move-entry-or-group kind-kw id group-selection-info]))

(reg-event-fx
 :move-entry-or-group
 (fn [{:keys [db]} [_event-id kind-kw id group-selection-info]]
   (let [error-fields (if (nil? group-selection-info) {:parent-group-selection-error "Valid parent group selection is required"} {})] 
     (if-not (empty? error-fields)
       {:fx [[:dispatch [:generic-dialog-update-with-map :move-group-or-entry-dialog {:error-fields error-fields}]]]}
       {:fx [[:bg-move-entry-or-group [(active-db-key db) kind-kw id (:uuid group-selection-info)]]]}))))

(defn- call-on-move-complete [kind-kw api-response]
  (when-not (on-error api-response)
    ;; Ensure that the dialog is closed
    (dispatch [:generic-dialog-close :move-group-or-entry-dialog])
    (dispatch [:common/message-snackbar-open (str (if (= kind-kw :group) "Group" "Entry") " is moved")])
    (dispatch [:common/refresh-forms])))

;; Called to move a group or an entry from one parent group to another parent group
(reg-fx
 :bg-move-entry-or-group
 (fn [[db-key kind-kw id parent-group-uuid]]
   (if (= kind-kw :group)
     (bg/move-group db-key id parent-group-uuid #(call-on-move-complete kind-kw %))
     (bg/move-entry db-key id parent-group-uuid #(call-on-move-complete kind-kw %)))))

