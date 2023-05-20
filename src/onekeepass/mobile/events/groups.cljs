(ns onekeepass.mobile.events.groups
  (:require
   [clojure.string :as str]
   [onekeepass.mobile.events.common :refer [active-db-key
                                            assoc-in-key-db
                                            get-in-key-db
                                            on-ok on-error]]
   [re-frame.core :refer [reg-event-db
                          reg-event-fx
                          reg-sub
                          dispatch
                          reg-fx
                          subscribe]]
   [onekeepass.mobile.utils :as u :refer [contains-val?]]
   [onekeepass.mobile.background :as bg :refer []]))

;;;;;;;;;;;;;;;;;;;;;;;;;;  Group form ;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn update-group-form-data [field-name-kw value]
  (dispatch [:update-group-form-data field-name-kw value]))

(defn cancel-group-form []
  (dispatch [:common/previous-page]))

(defn save-group-form []
  (dispatch [:save-group-form]))

(defn find-group-by-id [group-id kind]
  (dispatch [:group-form/find-group-by-id group-id kind]))

(defn group-form-field [field-kw]
  (subscribe [:group-form-field field-kw]))

(defn group-form-data-fields
  " 
  Called to get value of one more of form data fields. 
  The arg is a single field name or  fields in a vector of two more field 
  names (Keywords) like [:name :icon-id]
  Returns an atom which resolves to a single value  or a map when derefenced
  e.g {:name 'value'} or {:tags 'value} or {:name 'value' :icon-id 'value}
   "
  [fields]
  (subscribe [:group-form-data-fields fields]))

(defn form-modified
  "Checks whether any data changed or added"
  []
  (subscribe [:group-form-modified]))

;;Called when data is entered or updated for all fields except tags
(reg-event-db
 :update-group-form-data
 (fn [db [_event-id field-name-kw value]]
   (assoc-in-key-db db [:group-form :data field-name-kw] value)))

(reg-event-fx
 :group-form/find-group-by-id
 (fn [{:keys [db]} [_event-id group-id kind]] ;; kind can be :group or :category
   {:db (-> db (assoc-in-key-db [:group-form :kind] kind)
            (assoc-in-key-db [:group-form :new] false))
    :fx [[:bg-find-group-by-id [(active-db-key db) group-id]]]}))

(reg-fx
 :bg-find-group-by-id
 (fn [[db-key group-id]]
   (bg/find-group-by-id db-key group-id (fn [api-response]
                                          (when-let [group (on-ok api-response)]
                                            (dispatch [:group-found group]))))))

(reg-event-fx
 :group-found
 (fn [{:keys [db]} [_event-id group]]
   (let [kind (get-in-key-db db [:group-form :kind])]
     {:db (-> db (assoc-in-key-db [:group-form :data] group)
              (assoc-in-key-db [:group-form :error-fields] {})
              (assoc-in-key-db [:group-form :undo-data] group))
      :fx [[:dispatch [:common/next-page :group-form (if (= kind :group) "page.titles.group" "page.titles.category")]]]})))


(reg-event-fx
 :group-form/create-blank-group
 ;; kind is :group or :category
 (fn [{:keys [db]} [_event-id parent-group-uuid kind]]
   {:db (-> db (assoc-in-key-db [:group-form :kind] kind)
            (assoc-in-key-db [:group-form :new] true))
    :fx [[:bg-new-blank-group parent-group-uuid]]}))

(reg-fx
 :bg-new-blank-group
 (fn [parent-group-uuid]
   (bg/new-blank-group  (fn [api-reponse]
                          (when-let [group (on-ok api-reponse)]
                            (let [group-with-parent (assoc group :parent-group-uuid parent-group-uuid)]
                              (dispatch [:new-blank-group-created group-with-parent])))))))

(reg-event-fx
 :new-blank-group-created
 (fn [{:keys [db]} [_event-id blank-group]]
   (let [kind (get-in-key-db db [:group-form :kind])]
     {:db (-> db (assoc-in-key-db [:group-form :data] blank-group)
              (assoc-in-key-db [:group-form :undo-data] blank-group))
      ;; TODO: Use "page.titles.newGroup" "page.titles.newCategory" after finding a way to show longer texts
      ;; We may use the same technique as in Database settings page title ?
      :fx [[:dispatch [:common/next-page :group-form
                       (if (= kind :group) "page.titles.group" "page.titles.category")]]]})))

(reg-event-fx
 :save-group-form
 (fn [{:keys [db]} [_event-id]]
   (let [new (get-in-key-db db  [:group-form :new])
         group (get-in-key-db db  [:group-form :data])
         name (:name group)
         error-fields (if (str/blank? name) {:name "A valid group/category name is required"} {})]
     (if (empty? error-fields)
       {:db (-> db (assoc-in-key-db [:group-form :error-fields] error-fields))
        :fx [[:dispatch [:common/message-modal-show nil "Saving ..."]]
             (if new [:bg-insert-group [(active-db-key db) group]]
                 [:bg-update-group [(active-db-key db) group]])]}

       {:db (-> db (assoc-in-key-db [:group-form :error-fields] error-fields))}))))

(reg-fx
 :bg-insert-group
 (fn [[db-key group]]
   (bg/insert-group db-key group (fn [api-reponse]
                                   (when-not (on-error api-reponse #(dispatch [:insert-update-group-form-error %]))
                                     (dispatch [:insert-update-group-form-data-complete]))))))

(reg-event-fx
 :insert-update-group-form-error
 (fn [{:keys [_db]} [_event-id error]]
   {:fx [[:dispatch [:common/error-box-show "Insert or Update failed.." error]]]}))

(reg-event-fx
 :insert-update-group-form-data-complete
 (fn [{:keys [_db]} [_event-id]]
   ;; call Save db call here
   {:fx [[:dispatch [:save/save-current-kdbx
                     {:error-title "Group form save error"
                      :save-message "Saving group form..."
                      :on-save-ok (fn []
                                    (dispatch [:group-insert-update-save-complete]))}]]]}))

(reg-event-fx
 :group-insert-update-save-complete
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:dispatch [:common/message-modal-hide]]
         [:dispatch [:groups/load]]
         [:dispatch [:entry-category/load-categories-to-show]]
         [:dispatch [:common/previous-page]]]}))

(reg-fx
 :bg-update-group
 (fn [[db-key group]]
   (bg/update-group db-key group (fn [api-reponse]
                                   (when-not (on-error api-reponse #(dispatch [:insert-update-group-form-error %]))
                                     (dispatch [:insert-update-group-form-data-complete]))))))

(reg-sub
 :group-form
 (fn [db _query-vec]
   (get-in-key-db db [:group-form])))

(reg-sub
 :group-form-data
 (fn [db _query-vec]
   (get-in-key-db db [:group-form :data])))

(reg-sub
 :group-form-data-fields
 :<- [:group-form-data]
 (fn [data [_query-id fields]]
   (if-not (vector? fields)
     ;; fields is a single field name
     (get data fields)
     ;; a vector field names
     (select-keys data fields))))

(reg-sub
 :group-form-field
 :<- [:group-form]
 (fn [form [_query-id field-kw]]
   (get form field-kw)))

;;Determines whether any data is changed
(reg-sub
 :group-form-modified
 (fn [db _query-vec]
   (let [undo-data (get-in-key-db db [:group-form :undo-data])
         data (get-in-key-db db [:group-form :data])]
     (if (and (seq undo-data) (not= undo-data data))
       true
       false))))


;;;;;;;;;;;;;;;;;;;;;;;;;;  Group tree ;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn groups-data
  "Gets the group tree data"
  []
  (subscribe [:groups-data]))

#_(defn root-group-uuid []
    (subscribe [:group-data/root-group-uuid]))

;; This event handler loads the group summary data once only.  
#_(reg-event-fx
   :groups/load-once
   (fn [{:keys [db]} [_event-id]]
     (let [data (get-in-key-db db [:groups :data])]
       (if  (nil? data)
         {:fx [[:dispatch [:groups-data-update nil]]
               [:load-bg-groups-summary-data (active-db-key db)]]}
         {}))))

;; An event to reload group tree data whenever any group data update or insert is done
(reg-event-fx
 :groups/load
 (fn [{:keys [db]} [_event-id]]
   {:fx [[:dispatch [:groups-data-update nil]]
         [:load-bg-groups-summary-data (active-db-key db)]]}))

(reg-fx
 :load-bg-groups-summary-data
 (fn [db-key]
   (bg/groups-summary-data db-key (fn [api-reponse]
                                    (when-let [result (on-ok api-reponse)]
                                      (dispatch [:groups-data-update result]))))))

(reg-event-db
 :groups-data-update
 (fn [db [_event-id v]]
   (assoc-in-key-db db [:groups :data] v)))

(reg-sub
 :groups-data
 (fn [db _query-vec]
   (get-in-key-db db [:groups :data])))

;; Returns a map of root group summary data based on struct 'GroupSummary' 
(reg-sub
 :group-data/root-group
 :<- [:groups-data]
 (fn [{:keys [root-uuid groups]} _query-vec]
   (get groups root-uuid)))

(reg-sub
 :recycle-bin-group
 :<- [:groups-data]
 (fn [{:keys [recycle-bin-uuid groups]} _query-vec]
   (get groups recycle-bin-uuid)))

(reg-sub
 :group-data/is-recycle-bin-group
 :<- [:recycle-bin-group]
 (fn [{:keys [uuid]} [_event_id group-uuid]]
   (= uuid group-uuid)))

;; Used to list all groups in a selection list
;; Gets all active groups excluding all groups that are in recycle bin and the recycle bin
(reg-sub
 :groups/listing
 :<- [:groups-data]
 (fn [{:keys [groups recycle-bin-uuid deleted-group-uuids]} _query-vec]
   (let [recycle-groups (conj deleted-group-uuids recycle-bin-uuid)]
     (as-> (vals groups) coll
       ;; Only groups that are not deleted
       (filter #(not (contains-val? recycle-groups (get % :uuid))) coll)
       ;; Few fiedlds for each group
       (map (fn [v] {:name (get v :name)
                     :uuid (get v :uuid)
                     :icon-id 0}) (filter #(not (contains-val? recycle-groups %)) coll))
       ;; Sort by name - Note kw :name vs "name" used previously
       (sort-by (fn [v] (:name v)) coll)))))

#_(reg-sub
   :group-by-id
   :<- [:groups-data]
   (fn [{:keys [groups]} [_query-id by-group-uuid]]
     (get groups by-group-uuid)))

#_(reg-sub
   :groups/groups-tree-summary
   :<- [:groups-data]
   (fn [{:keys [groups recycle-bin-uuid deleted-group-uuids]} _query-vec]
     (let [recycle-groups (conj deleted-group-uuids recycle-bin-uuid)]
       (as-> (vals groups) coll
       ;; Only groups that are not deleted
         (filter #(not (contains-val? recycle-groups (get % :uuid))) coll)
       ;; Few fields for each group
         (map (fn [v] {:title (get v :name)
                       :uuid (get v :uuid)
                       :icon-id (:icon-id v)
                       :entries-count (-> v :entry-uuids count)})
              (filter #(not (contains-val? recycle-groups %)) coll))
       ;; Sort by name 
         (sort-by (fn [v] (:name v)) coll)
         (vec coll)))))

;; Called to show the root group in entry category
(reg-sub
 :groups/groups-tree-root-summary
 :<- [:groups-data]
 (fn [{:keys [root-uuid recycle-bin-uuid groups]} _query-vec]
   (let [root-group (get groups root-uuid)
         ;; Exclude recycle bin from root group's sub groups count
         children-group-uuids (filterv (fn [gid] (not= gid recycle-bin-uuid)) (:group-uuids root-group))]
     {:title (get root-group :name)
      :uuid (get root-group :uuid)
      :icon-id (:icon-id root-group)
      :entries-count (-> root-group :entry-uuids count)
      :groups-count (count children-group-uuids)})))

(reg-sub
 :groups/subgroups-summary
 :<- [:groups-data]
 (fn [{:keys [recycle-bin-uuid groups]}  [_query-id group-uuid]]
   ;; groups is a map where key is group uuid and value is group summary map {:name ".." :uuid ".." :icon-id 0 ..} 
   (let [{:keys [group-uuids]} (get groups group-uuid)
         ;; When the root's sub groups summary is called, the 'group-uuids' list will include the 'recycle-bin-uuid' 
         ;; Need to exlude the recycle bin group showing 
         children-group-uuids (filterv (fn [gid] (not= gid recycle-bin-uuid)) group-uuids)
         summaries (reduce (fn [acc id]
                             (let [{:keys [name uuid icon-id entry-uuids group-uuids]} (get groups id)]
                               (conj acc {:title name
                                          :uuid uuid
                                          :icon-id icon-id
                                          :groups-count (count group-uuids)
                                          :entries-count (count entry-uuids)}))) [] children-group-uuids)]
     summaries)))

(comment
  (in-ns 'onekeepass.mobile.events.groups)
  ;; Sometime subscrition changes are reflected after save. In that case the following clears old subs
  (re-frame.core/clear-subscription-cache!)
  (def db-key (-> @re-frame.db/app-db :current-db-file-name))
  (-> @re-frame.db/app-db (get db-key) keys))