(ns onekeepass.mobile.events.entry-category
  "Entry category panel releated events"
  (:require
   [onekeepass.mobile.events.common :refer [assoc-in-key-db
                                            get-in-key-db
                                            active-db-key
                                            on-error
                                            default-entry-category
                                            on-ok]]
   [onekeepass.mobile.events.entry-list :refer [sort-containers-with-criteria
                                                sort-entries-with-criteria]]
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

(defn sub-groups-summary
  "Summaries of the groups directly under the given group"
  [group-uuid]
  (subscribe [:groups/subgroups-summary group-uuid]))

(defn root-group-entry-items
  "Entry summaries of the entries that sit in the root group itself"
  []
  (subscribe [:entry-category/root-group-entry-items]))

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
      :fx [[:bg-combined-category-details [(active-db-key db) group-by]]
           ;; Covers switching back to an already opened db, where the group tree data is
           ;; in place already and no ':groups-data-update' will follow
           [:dispatch [:entry-category/load-root-group-entry-items]]]})))

;;;;;;;;;;;;;;;;;;;;;;;;; Root group's own entries ;;;;;;;;;;;;;;;;;;;;;;;;;

;; When the entries are grouped as 'Groups', the root group is not shown as a row the user
;; has to open first. Its sub groups and its own entries are listed on the category page
;; itself. The sub groups are already part of the group tree data; its entries are not and
;; have to be asked for separately here

(reg-event-fx
 :entry-category/load-root-group-entry-items
 (fn [{:keys [db]} [_event-id]]
   (let [group-by (get-in-key-db db [:entry-category :entries-grouping-as])
         root-uuid (get-in-key-db db [:groups :data :root-uuid])]
     (if (and (= group-by :group-tree) (not (nil? root-uuid)))
       {:fx [[:bg-root-group-entry-summary [(active-db-key db) root-uuid]]]}
       ;; No other grouping shows these entries. The root uuid is nil until the group tree
       ;; data arrives, and this event is dispatched again once it does
       {:db (assoc-in-key-db db [:entry-category :root-group-entry-items] [])}))))

(reg-fx
 :bg-root-group-entry-summary
 (fn [[db-key root-uuid]]
   (bg/entry-summary-data db-key
                          {:group root-uuid}
                          (fn [api-response]
                            (when-let [result (on-ok api-response)]
                              (dispatch [:root-group-entry-items-loaded result]))))))

(reg-event-db
 :root-group-entry-items-loaded
 (fn [db [_event-id entry-summaries]]
   (assoc-in-key-db db [:entry-category :root-group-entry-items]
                    ;; Sorted the same way the entry list page sorts, so that the two agree
                    (sort-entries-with-criteria db entry-summaries))))

;; Dispatched whenever the shared sort criteria changes. General categories retain their
;; intentional product order; grouping containers and root entries are sorted independently.
(reg-event-db
 :entry-category/sort-category-page-items
 (fn [db [_event-id]]
   (-> db
       (assoc-in-key-db [:entry-category :root-group-entry-items]
                        (sort-entries-with-criteria
                         db (get-in-key-db db [:entry-category :root-group-entry-items])))
       (assoc-in-key-db [:entry-category :data :grouped-categories]
                        (sort-containers-with-criteria
                         db (get-in-key-db db [:entry-category :data :grouped-categories]))))))

(reg-sub
 :entry-category/root-group-entry-items
 (fn [db _query-vec]
   (get-in-key-db db [:entry-category :root-group-entry-items])))

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
         {:keys [grouped-categories]} entry-categories
         sorted-grouped-categories (sort-containers-with-criteria db grouped-categories)
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
         ;; Switching to or away from 'Groups' changes whether the root group's own
         ;; entries are listed on the category page
         [:dispatch [:entry-category/load-root-group-entry-items]]
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
