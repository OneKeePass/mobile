(ns onekeepass.mobile.entry-category
  (:require
   [reagent.core :as r]
   [onekeepass.mobile.rn-components :as rnc :refer [lstr
                                                    dots-icon-name
                                                    
                                                    icon-color
                                                    primary-container-color
                                                    on-primary-color
                                                    page-background-color
                                                    
                                                    rnp-fab
                                                    rnp-menu
                                                    rnp-menu-item
                                                    rn-view
                                                    rn-safe-area-view
                                                    rn-section-list
                                                    rnp-list-item
                                                    rnp-divider
                                                    cust-rnp-divider
                                                    rnp-list-icon
                                                    rnp-icon-button
                                                    rnp-text]]
   [onekeepass.mobile.icons-list :refer [icon-id->name]]
   [onekeepass.mobile.utils :refer [str->int]]
   [onekeepass.mobile.common-components  :refer [menu-action-factory]]
   [onekeepass.mobile.events.entry-category :as ecat-events]
   [onekeepass.mobile.constants :as const]))

(set! *warn-on-infer* true)

(def group-by->section-titles {:type "Types"
                               :group-category "Categories"
                               :group-tree "Groups"})

;;;;;;;;;;;;;;;;;;;; Menus ;;;;;;;;;;;;;;;;;;

(def ^:private fab-action-menu-data (r/atom {:show false :x 0 :y 0}))

(defn hide-fab-action-menu []
  (swap! fab-action-menu-data assoc :show false))

(defn show-fab-action-menu [^js/PEvent event]
  (swap! fab-action-menu-data assoc :show true
         :x (-> event .-nativeEvent .-pageX) :y (-> event .-nativeEvent .-pageY)))

(def fab-menu-action (menu-action-factory hide-fab-action-menu))

(defn fab-action-menu [{:keys [show x y]} root-group]
  [rnp-menu {:visible show :onDismiss hide-fab-action-menu :anchor (clj->js {:x x :y y})} 
   [rnp-menu-item {:title (lstr "menu.labels.addEntry")
                   :onPress (fab-menu-action ecat-events/add-new-entry (select-keys root-group [:name :uuid]) const/UUID_OF_ENTRY_TYPE_LOGIN)}]
   [rnp-menu-item {:title (lstr "menu.labels.addCategory")
                   :onPress (fab-menu-action ecat-events/initiate-new-blank-category-form (:uuid root-group))}]
   [rnp-menu-item {:title (lstr "menu.labels.addGroup")
                   :onPress (fab-menu-action ecat-events/initiate-new-blank-group-form (:uuid root-group))}]])

;;
(def ^:private category-long-press-menu-data (r/atom {:show false :x 0 :y 0}))

(defn hide-category-long-press-menu []
  (swap! category-long-press-menu-data assoc :show false))

(defn show-category-long-press-menu [^js/PEvent event {:keys [category-key category-detail  root-group]}]
  (swap! category-long-press-menu-data assoc
         :show true
         :x (-> event .-nativeEvent .-pageX) :y (-> event .-nativeEvent .-pageY)
         :category-key category-key
         :category-detail category-detail
         :root-group root-group))


(defn category-long-press-menu [{:keys [show x y category-detail category-key]}]
  [rnp-menu {:visible show :onDismiss hide-category-long-press-menu :anchor (clj->js {:x x :y y})}
   (cond
     (= category-key "Types")
     [rnp-menu-item {:title (lstr "menu.labels.addEntry")
                     :onPress (fn [] (ecat-events/add-new-entry nil (:entry-type-uuid category-detail)))}]

     (= category-key "Categories")
     [:<>

      [rnp-menu-item {:title (lstr "menu.labels.addEntry")
                      :onPress (fn [] (ecat-events/add-new-entry {:name (:title category-detail) :uuid (:uuid category-detail)} const/UUID_OF_ENTRY_TYPE_LOGIN))}]
      [cust-rnp-divider]
      [rnp-menu-item {:title (lstr "menu.labels.edit")
                      :onPress (fn [] (ecat-events/find-category-by-id (:uuid category-detail)))}]]


     (= category-key "Groups")
     [:<>

      [rnp-menu-item {:title (lstr "menu.labels.addEntry")
                      :onPress (fn [] (ecat-events/add-new-entry {:name (:title category-detail) :uuid (:uuid category-detail)} const/UUID_OF_ENTRY_TYPE_LOGIN))}]
      [rnp-menu-item {:title (lstr "menu.labels.addGroup")
                      :onPress #(ecat-events/initiate-new-blank-group-form (:uuid category-detail))}]
      [cust-rnp-divider]
      [rnp-menu-item {:title (lstr "menu.labels.edit")
                      :onPress (fn [] (ecat-events/find-group-by-id (:uuid category-detail)))}]]

     :else
     nil)])

;;
(def ^:private group-by-menu-data (r/atom {:show false :x 0 :y 0 :group-by :type}))

(defn hide-group-by-menu []
  (swap! group-by-menu-data assoc :show false))

(defn show-group-by-menu [^js/PEvent event group-by]
  (swap! group-by-menu-data assoc
         :show true
         :group-by group-by
         :x (-> event .-nativeEvent .-pageX) :y (-> event .-nativeEvent .-pageY)))

(defn change-entries-grouping-method [kind]
  (ecat-events/change-entries-grouping-method kind)
  (hide-group-by-menu))

(defn group-by-menu [{:keys [show group-by x y]}]
  [rnp-menu {:visible show :onDismiss hide-group-by-menu :anchor (clj->js {:x x :y y})}
   [rnp-menu-item {:title (lstr "menu.labels.types")
                   :leadingIcon (if (= group-by :type) "checkbox-outline" "checkbox-blank-outline")
                   :onPress #(change-entries-grouping-method :type)}]
   [rnp-menu-item {:title (lstr "menu.labels.categories")
                   :leadingIcon (if (= group-by :group-category) "checkbox-outline" "checkbox-blank-outline")
                   :onPress #(change-entries-grouping-method :group-category)}]
   [rnp-menu-item {:title (lstr "menu.labels.groups")
                   :leadingIcon (if (= group-by :group-tree) "checkbox-outline" "checkbox-blank-outline")
                   :onPress #(change-entries-grouping-method :group-tree)}]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; check-all, heart-outline delete-outline, login, bank-outline,credit-card-outline, access-point,router-wireless
;; earth,airplane,dots-horizontal,dots-vertical

;; TODO: Move Icon names to 'onekeepass.mobile.constants'
(def category-icons {;; General categories
                     "AllEntries" "check-all"
                     "Favorites" "heart-outline"
                     "Deleted" "trash-can-outline"
                     ;; Following are hard coded entry type icon names
                     "Login" "login"
                     "Bank Account" "bank-outline"
                     "Wireless Router" "router-wireless"
                     "Credit/Debit Card" "credit-card-outline"})

(defn category-icon-name 
  "Called to get icon name for General categories or Entry types category or Group as Category or Group "
  [{:keys [title icon-name icon-id uuid] } ] 
  (let [icon (get category-icons title)]
    (cond 
      ;; General categories or Standard entry types only
      icon 
      icon 
      ;; Group tree root or Group category will have non nil uuid and valid icon-id int value
      (not (nil? uuid))
      (icon-id->name icon-id) 
      ;; custom entry type will have icon-name convertable to an int
      :else 
      (let [cust-entry-type-icon-id (str->int icon-name)]
        (if cust-entry-type-icon-id
          (icon-id->name cust-entry-type-icon-id)
          (icon-id->name 0)
          )))))

(defn category-item
  "category-detail-m is a map representing struct 'CategoryDetail'
   category-key is one of key used in section data - Types,Categories, or Groups
  "
  [_category-detail-m category-key root-group]
  ;; should the following need to accept category-key for react comp?
  (fn [{:keys [title display-title entries-count groups-count] :as category-detail-m}]
    (let [display-name (if (nil? display-title) title display-title)
          icon-name (category-icon-name category-detail-m)
          items-count (if (= category-key "Groups") (+ entries-count groups-count) entries-count)]
      [rnp-list-item {;;:style {:background-color @rnc/background-color}
                      :onPress (fn [_e]
                                 (ecat-events/load-selected-category-entry-items
                                  category-detail-m category-key))
                      
                      :onLongPress  (fn [event]
                                      (if (= "General" category-key)
                                        (ecat-events/load-selected-category-entry-items
                                         category-detail-m
                                         category-key)
                                        (show-category-long-press-menu
                                         event
                                         {:category-key category-key
                                          :category-detail category-detail-m
                                          :root-group root-group})))
                      :title (r/as-element
                              [rnp-text {  :variant "titleMedium"} display-name]) ;;:style {:color @rnc/on-background-color}
                      
                      :left (fn [_props] (r/as-element
                                          [rnp-list-icon {:style {:height 20}
                                                          :icon icon-name
                                                          :color @icon-color}]))
                      
                      :right (fn [_props] (r/as-element
                                           [rnp-text {:variant "titleMedium"} items-count]))}])))

(defn category-header [title group-by]
  [rn-view  {:style {:flexDirection "row"
                     :backgroundColor  @primary-container-color
                     :margin-top 5
                     :min-height 38}}
   [rnp-text {:style {:alignSelf "center" :width "85%" :padding-left 15} :variant "titleLarge"} title]
   [rnp-icon-button {:icon dots-icon-name
                     :style {:height 38 :margin-right 0 :backgroundColor @on-primary-color}
                     :onPress #(show-group-by-menu % group-by)}]])

(defn categories-content []
  (let [general-categories @(ecat-events/general-categories)
        ;;group-by is kw and is one of :type, :group-tree, :group-category
        group-by @(ecat-events/entries-grouping-method)
        
        root-group @(ecat-events/root-group)
        ;; Convert the kw to UI section title
        section-title  (get group-by->section-titles group-by)
        
        section-data (cond
                       (= group-by :type)
                       @(ecat-events/type-categories)

                       (= group-by :group-category)
                       @(ecat-events/group-categories)

                       (= group-by :group-tree)
                       (vector @(ecat-events/group-tree-root-summary)))
        
        sections  [{:title "General"
                    :key "General"
                    :data (if (nil? general-categories) [] general-categories)}

                   {:title section-title
                    :key section-title
                    :data (if (nil? section-data) [] section-data) }]]
    [rn-section-list
     {:style {} ;;:background-color @rnc/background-color
      :sections (clj->js sections)
      :renderItem (fn [props] ;; keys are (:item :index :section :separators)
                    (let [props (js->clj props :keywordize-keys true)]
                      (r/as-element [category-item (-> props :item) (-> props :section :key) root-group])))
      :ItemSeparatorComponent (fn [_p]
                                (r/as-element [rnp-divider]))
      :renderSectionHeader (fn [props] ;; key is :section
                             (let [props (js->clj props :keywordize-keys true)
                                   {:keys [title key]} (-> props :section)]
                               (when-not (= key "General")
                                 (r/as-element [category-header title group-by]))))}]))

(defn entry-category-content []
  [rn-safe-area-view {:style {:flex 1 :background-color @page-background-color}}
   [categories-content]
   [fab-action-menu @fab-action-menu-data @(ecat-events/root-group)]
   [category-long-press-menu @category-long-press-menu-data]
   [group-by-menu @group-by-menu-data]
   [rnp-fab {:style {:position "absolute" :margin 16 :right 0 :bottom 0} 
             :icon "plus" :onPress (fn [e] (show-fab-action-menu e))}]])