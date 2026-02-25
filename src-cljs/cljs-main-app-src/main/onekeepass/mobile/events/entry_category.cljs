(ns onekeepass.mobile.events.entry-category
  "Entry category panel releated events"
  (:require
   [onekeepass.mobile.events.common :refer [assoc-in-key-db
                                            get-in-key-db
                                            active-db-key
                                            on-error
                                            default-entry-category
                                            on-ok]]
   [re-frame.core :refer [reg-event-db
                          reg-event-fx
                          reg-sub
                          dispatch
                          reg-fx
                          subscribe]]
   [onekeepass.mobile.background :as bg]
   [onekeepass.mobile.constants :as const :refer [GROUPING_LABEL_TYPES GROUPING_LABEL_TAGS
                                                  GROUPING_LABEL_CATEGORIES GROUPING_LABEL_GROUPS]]))

(defn change-entries-grouping-method [kind]
  ;; kind is :type, :group-tree, :group-category
  (dispatch [:change-entries-grouping-method kind]))

(defn initiate-new-blank-group-form [parent-group-uuid]
  (dispatch [:group-form/create-blank-group parent-group-uuid :group]))

(defn initiate-new-blank-category-form [parent-group-uuid]
  (dispatch [:group-form/create-blank-group parent-group-uuid :category]))

(defn add-new-entry
  "The args passed are the group-info which is a map with keys :name, :uuid and 
   entry-type-name is the name from the entry types
  "
  ([group-info entry-type-uuid]
   (dispatch [:entry-form/add-new-entry group-info entry-type-uuid]))
  ([]
   (add-new-entry  nil const/UUID_OF_ENTRY_TYPE_LOGIN)))

(defn load-selected-category-entry-items
  "Called to load entry summary list for certain category sections"
  [category-detail-m category-section-title] 
  (dispatch [:load-selected-category-entry-items category-detail-m category-section-title]))

(defn find-group-by-id
  "Finds a group. The group might have been marked as category or not"
  [group-id]
  (dispatch [:group-form/find-group-by-id group-id :group]))

(defn find-category-by-id
  "Finds a group which is also marked as category"
  [group-id]
  (dispatch [:group-form/find-group-by-id group-id :category]))

(defn entries-grouping-method
  " Returns one of :type, :group-tree, :group-category 
    See  [:entry-category :entries-grouping-as] 
   "
  []
  (subscribe [:entries-grouping-method]))

(defn general-categories
  "Entry categorys such as AllEntries or Favorites or Deleted"
  []
  (subscribe [:general-categories]))

(defn type-categories
  "Entry type based entry category"
  []
  (subscribe [:type-categories]))

(defn tag-categories
  "Entry type based entry category"
  []
  (subscribe [:tag-categories]))

(defn group-categories
  "All group category"
  []
  (subscribe [:group-categories]))

(defn group-tree-root-summary
  "Gets the root group"
  []
  (subscribe [:groups/groups-tree-root-summary]))

(defn root-group []
  (subscribe [:group-data/root-group]))

(defn sort-by-tag-name [grouped-categories]
  ;; First fn provides the comparision key
  ;; Second fn provides the comparator that uses the keys
  (sort-by (fn [m] (:title m)) (fn [v1 v2] (compare v1 v2)) grouped-categories))

;; TODO: May need to use combine the use of string labels, grouping kw and enum variants. How ?

(defn- groupings-label->groupings-kind-kw
  "Converts the string value of grouping label to an appropriate keyword"
  [start-view-to-show]
  (cond
    (= start-view-to-show GROUPING_LABEL_TYPES)
    :type

    (= start-view-to-show GROUPING_LABEL_CATEGORIES)
    :group-category

    (= start-view-to-show GROUPING_LABEL_GROUPS)
    :group-tree

    (= start-view-to-show GROUPING_LABEL_TAGS)
    :tag

    :else
    :type))

(defn- grouping-kind->pref-entry-category-groupings
  "Converts the show-as kw to a string that is used in app preference settings"
  [kw-kind]
  (cond
    (= kw-kind :type)
    GROUPING_LABEL_TYPES

    (= kw-kind :group-tree)
    GROUPING_LABEL_GROUPS

    (= kw-kind :tag)
    GROUPING_LABEL_TAGS

    (= kw-kind :group-category)
    GROUPING_LABEL_CATEGORIES))

(defn- show-as->grouping-kind
  "Converts the group-by kw to a string that is convertable to enum EntryCategoryGrouping"
  [group-by]
  (cond
    (= group-by :type)
    "AsTypes"

    (= group-by :tag)
    "AsTags"

    (= group-by :group-category)
    "AsGroupCategories"

    (= group-by :group-tree)
    "AsGroupCategories"))

;; category-detail is a map representing struct 'CategoryDetail' and 
;; category-section-title is one of Types or Tags or Categories or Groups
(reg-event-fx
 :load-selected-category-entry-items
 (fn [{:keys [_db]} [_event-id category-detail category-section-title]]
   ;; Delegates loading of list of entries for a selected category to the next page entry-list 
   {:fx [[:dispatch [:entry-list/load-entry-items category-detail category-section-title]]]}))

;; Called to load all available entry categories. 
;; The categories are general categories, type categories and group categories
;; See EntryCategoryInfo struct

(reg-event-fx
 :entry-category/load-categories-to-show
 (fn [{:keys [db]} [_event-id]]
   (let [group-by (get-in-key-db db [:entry-category :entries-grouping-as])
         ;; Need to set the grouping kind kw from the app-preference if required
         db (if (nil? group-by)
              (-> db (assoc-in-key-db
                      [:entry-category :entries-grouping-as]
                      (groupings-label->groupings-kind-kw (default-entry-category db))))
              db)
         ;; Ensure that we use any updated group-by value
         group-by (get-in-key-db db [:entry-category :entries-grouping-as])]

     {:db db
      :fx [[:bg-combined-category-details [(active-db-key db) group-by]]]})))

#_(reg-event-fx
   :entry-category/load-categories-to-show
   (fn [{:keys [db]} [_event-id]]
     (let [group-by (get-in-key-db db [:entry-category :entries-grouping-as])
           group-by (if (nil? group-by) :type group-by)]
       {:fx [[:bg-combined-category-details [(active-db-key db) group-by]]]})))

(reg-fx
 :bg-combined-category-details
 (fn [[db-key group-by]]
   (bg/combined-category-details db-key
                                 (show-as->grouping-kind group-by)
                                 (fn [api-response]
                                   (when-let [categories (on-ok api-response)]
                                     (dispatch [:categories-to-show-loaded categories]))))))

(reg-event-db
 :categories-to-show-loaded
 (fn [db [_event-id entry-categories]]
   ;; entry-categories is a map from struct EntryCategories
   (let [kind (get-in-key-db db [:entry-category :entries-grouping-as])
         kind (if (nil? kind) :type kind)
         {:keys [grouping-kind grouped-categories]} entry-categories
         sorted-grouped-categories (if (= grouping-kind "AsTags")
                                     (sort-by-tag-name grouped-categories)
                                     grouped-categories)
         entry-categories (assoc entry-categories :grouped-categories sorted-grouped-categories)]
     (-> db (assoc-in-key-db [:entry-category :data] entry-categories)
           ;; entries-grouping-as is one of :type, :tag,:group-tree, :group-category 
         (assoc-in-key-db [:entry-category :entries-grouping-as] kind)))))

;; Called to switch one of categories from Entry types or Group Marked as Categories or Just group tree
(reg-event-fx
 :change-entries-grouping-method
 (fn [{:keys [db]} [_event-id kind]]
   {:db (assoc-in-key-db db [:entry-category :entries-grouping-as] kind)
    :fx [[:bg-combined-category-details [(active-db-key db) kind]]
         ;; Reusing this event from app-settings to update the ":default-entry-category-groupings" in the backend  
         [:dispatch [:app-settings/app-preference-update-data
                     :default-entry-category-groupings (grouping-kind->pref-entry-category-groupings kind) nil]]]}))

;; entries-grouping-as is one of :type, :group-tree, :group-category
(reg-sub
 :entries-grouping-method
 (fn [db _query-vec]
   (get-in-key-db db [:entry-category :entries-grouping-as])))

(reg-sub
 :entry-category-data
 (fn [db _query-vec]
   (get-in-key-db db [:entry-category :data])))

(reg-sub
 :type-categories
 :<- [:entry-category-data]
 (fn [data _query-vec]
   (:grouped-categories data)))

(reg-sub
 :tag-categories
 :<- [:entry-category-data]
 (fn [data _query-vec]
   (:grouped-categories data)))

(reg-sub
 :group-categories
 :<- [:entry-category-data]
 (fn [data _query-vec]
   (:grouped-categories data)
   ;; category-detail map has group-uuid for :group-category (i.e "AsGroupCategories" grouping-kind) grouped data 
   ;; we duplicate the 'group-uuid' as for a key :uuid to be consistent with :group-tree 
   ;; Group uuid is used to load entries and groups belonging to a ground in entry list
   ;; See ':entry-list/load-entry-items' how group's uuid is used
   (mapv (fn [{:keys [group-uuid] :as m}]
           (assoc m :uuid group-uuid)) (:grouped-categories data))))

(reg-sub
 :general-categories
 :<- [:entry-category-data]
 (fn [data _query-vec]
   (:general-categories data)))

(comment
  (in-ns 'onekeepass.mobile.events.entry-category)
  (def db-key (-> @re-frame.db/app-db :current-db-file-name))
  (-> @re-frame.db/app-db (get db-key) keys)
  (-> @re-frame.db/app-db (get db-key) :entry-category :data keys);; =>  showing-groups-as  entries-grouping-as :type :group-tree :group-category
  )