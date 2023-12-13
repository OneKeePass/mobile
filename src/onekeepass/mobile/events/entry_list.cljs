(ns onekeepass.mobile.events.entry-list
  (:require 
   [onekeepass.mobile.events.common :refer [assoc-in-key-db
                                            get-in-key-db
                                            active-db-key 
                                            on-ok]]
   [re-frame.core :refer [reg-event-db
                          reg-event-fx
                          reg-sub
                          dispatch
                          reg-fx
                          subscribe]]
   [onekeepass.mobile.constants :as const]
   [onekeepass.mobile.background :as bg :refer [entry-summary-data]]
   [onekeepass.mobile.utils :as u]))

(defn entry-list-back-action []
  (dispatch [:entry-list-back-action]))

(defn find-entry-by-id [entry-uuid]
  (dispatch [:entry-form/find-entry-by-id entry-uuid]))

(defn find-group-by-id [group-id]
  (dispatch [:group-form/find-group-by-id group-id :group]))

(defn add-entry 
  "Called to add a new entry based on the entry type or group selected"
  []
  (dispatch [:entry-list-add-new-entry]))

(defn add-entry-in-selected-group [{:keys [uuid title]}]
  (dispatch [:entry-form/add-new-entry {:uuid uuid :name title} const/UUID_OF_ENTRY_TYPE_LOGIN]))

(defn add-group [parent-group-uuid]
  (dispatch [:group-form/create-blank-group parent-group-uuid :group]))

(defn delete-entry [entry-id]
  (dispatch [:move-delete/entry-delete-start entry-id]))

(defn delete-group [group-id]
  (dispatch [:move-delete/group-delete-start group-id]))

(defn delete-all-entries-permanently []
  (dispatch [:move-delete/delete-all-entries-start]))

(defn entry-list-sort-key-changed [key-name]
  (dispatch [:entry-list-sort-key-changed key-name]))

(defn entry-list-sort-direction-changed [direction]
  (dispatch [:entry-list-sort-direction-changed direction]))

(defn selected-entry-items []
  (subscribe [:selected-entry-items]))

(defn show-subgroups []
  (subscribe [:show-subgroups]))

(defn selected-category-detail []
  (subscribe [:selected-category-detail]))

(defn selected-category-key []
  (subscribe [:selected-category-key]))

(defn deleted-category-showing []
  (subscribe [:entry-list/deleted-category-showing]))

(defn subgroups-summary [group-uuid]
  (subscribe [:groups/subgroups-summary group-uuid]))

(defn is-recycle-bin-group [group-uuid]
  (subscribe [:group-data/is-recycle-bin-group group-uuid]))

(defn current-page-title []
  (subscribe [:current-page-title]))

(defn entry-list-sort-criteria []
  (subscribe [:entry-list-sort-criteria]))

;;;;;;;;;;;;;;;;;;;;;;; Entry summary lists sorting support functions  ;;;;;;;;;;;;;;;;;;;;;;;;;;

(def sort-default-key-name const/TITLE)

(def sort-default-direction const/ASCENDING)

(defn list-sort-criteria
  ([db]
   (let [{:keys [key-name direction] :as el-sort} (get-in-key-db db [:entry-list :sort])]
     (if (nil? key-name)
       {:key-name sort-default-key-name
        :direction (if (nil? direction) sort-default-direction direction)}
       el-sort))))

(defn sort-entries [{:keys [key-name direction]} entries]
  (sort-by

   ;; This is the key fn that provides keys for the comparion
   (fn [{:keys [title modified-time created-time]}]
     (cond
       (= key-name const/TITLE)
       title

       (= key-name const/MODIFIED_TIME)
       modified-time

       (= key-name const/CREATED_TIME)
       created-time

       :else
       title))

   ;; This is comparater for the keys
   (fn [v1 v2] (if (= direction const/ASCENDING)
                 (compare v1 v2)
                 (compare v2 v1)))
   entries))

(defn sort-entries-with-criteria
  "Sorts the entry list based on the currrent sort criteria"
  [db entries]
  (let [sort-criteria (list-sort-criteria db)]
    (sort-entries sort-criteria entries)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- to-category-source
  "Returns value matching EntryCategory enum based on the current selected-category-key"
  [selected-category-key {:keys [title uuid entry-type-uuid]}]
  (condp = selected-category-key
    const/GEN_SECTION_TITLE title
    const/TAG_SECTION_TITLE {:tag title}
    const/TYPE_SECTION_TITLE {:entry-type-uuid entry-type-uuid}
    const/CAT_SECTION_TITLE {:group uuid}
    const/GROUP_SECTION_TITLE {:group uuid}))

(reg-event-fx
 :entry-list-back-action
 (fn [{:keys [db]} [_event-id]] 
   (let [back-action-stack (get-in-key-db db [:entry-list :back-action-stack])
         selected-category-key (get-in-key-db db [:entry-list :selected-category-key])
         ;; Remove the current 'category-detail' map which is on the top of the list back-action-stack that was put earlier
         ;; Except for "Groups", the rest-stack will be empty. In case of "Groups" section
         ;; the rest-stack will have that many 'category-detail' depending on the sub groups navigation
         rest-stack (rest back-action-stack)
         previous-category-detail (first rest-stack) 
         entry-category (to-category-source selected-category-key previous-category-detail)
         ]
     {:db (-> db (assoc-in-key-db  [:entry-list :back-action-stack] rest-stack)
              (assoc-in-key-db  [:entry-list :category-detail] previous-category-detail)
              (assoc-in-key-db [:entry-list :category-source] entry-category))
      :fx [(if-not (empty? rest-stack)
             [:dispatch [:entry-list/reload-selected-entry-items]]
             [:dispatch [:common/previous-page]])]
      ;; :fx [(when-not (empty? rest-stack)
      ;;        [:dispatch [:entry-list/reload-selected-entry-items]])
      ;;      ;; Any relavant entry-items will be loaded in the above dispatch 
      ;;      [:dispatch [:common/update-page-info-to-previous]]]
      })))

;; Called to get all entry summary items for a selected category in entry category view.
;; This also sets the category source. The valid values 
;; are AllEntries or Deleted or Favorites or a map for group or type or tag category.
;; These match the EntryCategory enum in backend service
(reg-event-fx
 :entry-list/load-entry-items
 (fn [{:keys [db]} [_event-id {:keys [uuid] :as category-detail} selected-category-key]]
   ;; selected-category-key is one of Types, Tags, Groups or Categories 
   (let [;; entry-category is convertable to EntryCategory enum in the rust side
         entry-category (to-category-source selected-category-key category-detail)
         ;; back-action-stack is a list (not vector)
         back-action-stack (get-in-key-db db [:entry-list :back-action-stack])
         ;; If the first member 'category-detail' of back-action-stack is the same as the incoming one, it is not added to the stack
         ;; Otherwise this category-detail added to the front of the list
         back-action-stack (if (= uuid (:uuid (first back-action-stack))) back-action-stack (conj back-action-stack category-detail))]
     {:db (-> db
              (assoc-in-key-db  [:entry-list :back-action-stack] back-action-stack)
              (assoc-in-key-db  [:entry-list :category-source] entry-category)
              (assoc-in-key-db  [:entry-list :category-detail] category-detail)
              (assoc-in-key-db  [:entry-list :selected-category-key] selected-category-key))
      :fx [[:dispatch [:entry-list/update-selected-entry-id nil]]
           [:load-bg-entry-summary-data [(active-db-key db) category-detail entry-category false]]]})))

;; Need to reload entry summaries after updating an entry or 
;; when navigating to previous list - particularly while navigating 'Groups' tree
(reg-event-fx
 :entry-list/reload-selected-entry-items
 (fn [{:keys [db]} [_event-id]]
   (let [category-detail (get-in-key-db db [:entry-list :category-detail])
         category (get-in-key-db db [:entry-list :category-source])]
     ;; :category-detail may be nil if entry list is not yet shown or :entry-list-back-action event happened 
     ;; IMPORATNT: category-detail need to be a valid enum value and not nil. 
     ;; Otherwise sometimes RN panic error ' ERROR  Error: Doesn't support name:' happens in 'csk/->camelCaseString' call 
     ;; in the background.cljs
     {:fx [(when category-detail [:load-bg-entry-summary-data [(active-db-key db) category-detail category true]])]})))

(reg-fx
 :load-bg-entry-summary-data
 ;; reg-fx accepts only single argument. So the calleer needs to use map or vector to pass multiple values
 (fn [[db-key category-detail category reloaded?]] 
   (entry-summary-data db-key category
                          (fn [api-response]
                            ;;(println "ENTRYLIST: In entry-summary-data callback with api-response " api-response)
                            (when-let [result (on-ok api-response)]
                              (dispatch [:entry-list-load-complete result category-detail reloaded?]))))))


;; When a list of all entry summary data is loaded successfully, this is called 
(reg-event-fx
 :entry-list-load-complete
 (fn [{:keys [db]} [_event-id result {:keys [title display-title] :as _category-detail} reloaded?]]
   (let [page-title (if (nil? display-title) title display-title)] 
     {:db db
      :fx [[:dispatch [:update-selected-entry-items result]] 
           (when-not reloaded?
             [:dispatch [:common/next-page :entry-list page-title]])]})))

(reg-event-fx
 :entry-list-sort-key-changed
 (fn [{:keys [db]} [_event-id key-name]]
   {:db (assoc-in-key-db db [:entry-list :sort :key-name] key-name)
    :fx [[:dispatch [:sort-entry-items]]]}))

(reg-event-fx
 :entry-list-sort-direction-changed
 (fn [{:keys [db]} [_event-id direction]]
   {:db (assoc-in-key-db db [:entry-list :sort :direction] direction)
    :fx [[:dispatch [:sort-entry-items]]]}))

(reg-event-fx
 :sort-entry-items
 (fn [{:keys [db]} [_event-id]]
   (let [entries (get-in-key-db db [:entry-list :selected-entry-items])]
     {:db (assoc-in-key-db db [:entry-list :selected-entry-items] (sort-entries-with-criteria db entries))})))

;; list of entry items returned by backend api when a category selected
;; or entry items returned in a search result - Work is yet to be done
(reg-event-db
 :update-selected-entry-items
 (fn [db [_event-id  entry-summaries]]
   (assoc-in-key-db db [:entry-list :selected-entry-items] (sort-entries-with-criteria db entry-summaries))))

;; Sets the category-source that is selected in the category view
#_(reg-event-db
 :update-category-source
 (fn [db [_event-id  source]]
   (assoc-in-key-db db [:entry-list :category-source] source)))

(reg-event-db
 :entry-list/update-selected-entry-id
 (fn [db [_event-id  entry-id]]
   (assoc-in-key-db db [:entry-list :selected-entry-id] entry-id)))

;; Called to add a new entry based on Type or Group selected in entry category page
(reg-event-fx
 :entry-list-add-new-entry
 (fn [{:keys [db]} [_event-id]]
   (let [selected-category-key (get-in-key-db db [:entry-list :selected-category-key])
         {:keys [uuid title entry-type-uuid]} (get-in-key-db db [:entry-list :category-detail])
         [group-info entry-type-uuid] (cond
                                        (u/contains-val? [const/GROUP_SECTION_TITLE const/CAT_SECTION_TITLE]  
                                                         selected-category-key)
                                        [{:uuid uuid :name title} const/UUID_OF_ENTRY_TYPE_LOGIN]

                                        (= const/TYPE_SECTION_TITLE selected-category-key)
                                        [nil entry-type-uuid]

                                        :else
                                        [nil const/UUID_OF_ENTRY_TYPE_LOGIN])] 
     (if-not (= [group-info entry-type-uuid] [nil nil])
       {:fx [[:dispatch [:entry-form/add-new-entry group-info entry-type-uuid]]]}
       {}))))

;; The title of entry list page as user navigates from group to its entries or sub groups
;; We get the entry list page title dynamically as we need to support multiple 
;; entry list pages particularly when category type is 'group' or 'category'
(reg-sub
 :current-page-title
 (fn [db]
   (let [category-detail (get-in-key-db  db  [:entry-list :category-detail]) 
         page-title (-> (get-in-key-db db [:entry-list :back-action-stack]) first :title)]
     ;; When the selected category is 'General', the back-action-stack will be empty as only that page is shown
     (if-not (nil? page-title) page-title (:title category-detail)))))

(reg-sub
 :selected-entry-items
 (fn [db _query-vec]
   (get-in-key-db db [:entry-list :selected-entry-items])))

(reg-sub
 :selected-entry-id
 (fn [db _query-vec]
   (get-in-key-db db [:entry-list :selected-entry-id])))

;; Gets the source based on the category view selection in the previous entry category screen
;; It is a map representing the enum EntryCategory
;; eg  "AllEntries" or "Deleted" or "Favorites" or {:group "9e0618bd-b7f4-4876-af42-43180d07b1bb"}  or {:entrytype "Login"}
(reg-sub
 :category-source
 (fn [db _query-vec]
   (get-in-key-db db [:entry-list :category-source])))

;; Checkcs whether the group-by is done by 'Groups' 
(reg-sub
 :show-subgroups
 (fn [db _query-vec]
   (= const/GROUP_SECTION_TITLE (get-in-key-db db [:entry-list :selected-category-key]))))

(reg-sub
 :selected-category-detail
 (fn [db _query-vec]
   (get-in-key-db db [:entry-list :category-detail])))

;; selected-category-key is one of General, Types, Categories, Groups
(reg-sub
 :selected-category-key
 (fn [db _query-vec]
   (get-in-key-db db [:entry-list :selected-category-key])))

(reg-sub
 :selected-entry-type
 :<- [:category-source]
 (fn [{:keys [entrytype]} _query-vec]
   entrytype))

;; The title of any current selected Category ( "Deleted" "AllEntries" etc or Group name in category view)
(reg-sub
 :selected-category-title
 :<- [:selected-category-detail]
 (fn [category-detail _query-vec]
   (:title category-detail)))

;; Is the category selected is deleted one?
(reg-sub
 :entry-list/deleted-category-showing
 :<- [:selected-category-title]
 (fn [cat-title _query-vec]
   (= cat-title "Deleted")))


(reg-sub
 :entry-list-sort-criteria
 (fn [db _query-vec]
   (list-sort-criteria db)))

(comment
  (in-ns 'onekeepass.mobile.events.entry-list)

  (def db-key (-> @re-frame.db/app-db :current-db-file-name))
  (-> @re-frame.db/app-db (get db-key) keys))