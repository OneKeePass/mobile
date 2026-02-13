(ns onekeepass.mobile.entry-category
  (:require
   [onekeepass.mobile.common-components  :refer [menu-action-factory]]
   [onekeepass.mobile.constants :as const :refer [AUTO_DB_OPEN_TYPE_NAME
                                                  BANK_ACCOUNT_TYPE_NAME
                                                  CAT_SECTION_TITLE
                                                  CATEGORY_ALL_ENTRIES
                                                  CATEGORY_DELETED_ENTRIES
                                                  CATEGORY_FAV_ENTRIES
                                                  CREDIT_DEBIT_CARD_TYPE_NAME
                                                  GROUP_SECTION_TITLE
                                                  ICON-CHECKBOX-BLANK-OUTLINE
                                                  ICON-CHECKBOX-OUTLINE
                                                  ICON-PLUS ICON-TAGS
                                                  LOGIN_TYPE_NAME
                                                  STANDARD_ENTRY_TYPES
                                                  TAG_SECTION_TITLE
                                                  TYPE_SECTION_TITLE
                                                  UUID_OF_ENTRY_TYPE_LOGIN
                                                  WIRELESS_ROUTER_TYPE_NAME]]
   [onekeepass.mobile.events.common :as cmn-events]
   [onekeepass.mobile.events.entry-category :as ecat-events]
   [onekeepass.mobile.icons-list :refer [icon-id->name]]
   [onekeepass.mobile.rn-components :as rnc :refer [cust-rnp-divider
                                                    dots-icon-name icon-color
                                                    on-primary-color
                                                    page-background-color
                                                    primary-container-color
                                                    rn-safe-area-view
                                                    rn-section-list rn-view
                                                    rnp-divider rnp-fab
                                                    rnp-icon-button
                                                    rnp-list-icon
                                                    rnp-list-item rnp-menu
                                                    rnp-menu-item rnp-text]]
   [onekeepass.mobile.translation :refer [lstr-cv lstr-entry-type-title lstr-l
                                          lstr-ml]]
   [onekeepass.mobile.utils :refer [contains-val? str->int]]
   [reagent.core :as r]))

(set! *warn-on-infer* true)

(def GENERAL_KEY "General")

(def group-by->section-titles {:type TYPE_SECTION_TITLE
                               :tag TAG_SECTION_TITLE
                               :group-category CAT_SECTION_TITLE
                               :group-tree GROUP_SECTION_TITLE})

;;;;;;;;;;;;;;;;;;;; Menus ;;;;;;;;;;;;;;;;;;

(def ^:private fab-action-menu-data (r/atom {:show false :x 0 :y 0}))

(defn hide-fab-action-menu []
  (swap! fab-action-menu-data assoc :show false))

(defn show-fab-action-menu [^js/PEvent event]
  (swap! fab-action-menu-data assoc :show true
         :x (-> event .-nativeEvent .-pageX) :y (-> event .-nativeEvent .-pageY)))

(def fab-menu-action (menu-action-factory hide-fab-action-menu))

(defn fab-action-menu [{:keys [show x y]} root-group]
  [rnp-menu {:visible show :key (str show) :onDismiss hide-fab-action-menu :anchor (clj->js {:x x :y y})}
   [rnp-menu-item {:title (lstr-ml "addEntry")
                   :disabled @(cmn-events/current-db-disable-edit)
                   :onPress (fab-menu-action ecat-events/add-new-entry (select-keys root-group [:name :uuid]) UUID_OF_ENTRY_TYPE_LOGIN)}]
   [rnp-menu-item {:title (lstr-ml "addCategory")
                   :disabled @(cmn-events/current-db-disable-edit)
                   :onPress (fab-menu-action ecat-events/initiate-new-blank-category-form (:uuid root-group))}]
   [rnp-menu-item {:title (lstr-ml "addGroup")
                   :disabled @(cmn-events/current-db-disable-edit)
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
  [rnp-menu {:visible show :key (str show) :onDismiss hide-category-long-press-menu :anchor (clj->js {:x x :y y})}
   (cond

     ;;  @(cmn-events/current-db-disable-edit)
     ;;  nil

     (= category-key TYPE_SECTION_TITLE)
     [rnp-menu-item {:title (lstr-ml "addEntry")
                     :disabled @(cmn-events/current-db-disable-edit)
                     :onPress (fn [] (ecat-events/add-new-entry nil (:entry-type-uuid category-detail)))}]

     (= category-key TAG_SECTION_TITLE)
     [rnp-menu-item {:title (lstr-ml "addEntry")
                     :disabled @(cmn-events/current-db-disable-edit)
                     :onPress (fn [] (ecat-events/add-new-entry nil UUID_OF_ENTRY_TYPE_LOGIN))}]

     (= category-key CAT_SECTION_TITLE)
     [:<>

      [rnp-menu-item {:title (lstr-ml "addEntry")
                      :disabled @(cmn-events/current-db-disable-edit)
                      :onPress (fn [] (ecat-events/add-new-entry {:name (:title category-detail) :uuid (:uuid category-detail)} UUID_OF_ENTRY_TYPE_LOGIN))}]
      [cust-rnp-divider]
      [rnp-menu-item {:title (lstr-ml "edit")
                      :disabled @(cmn-events/current-db-disable-edit)
                      :onPress (fn [] (ecat-events/find-category-by-id (:uuid category-detail)))}]]


     (= category-key GROUP_SECTION_TITLE)
     [:<>

      [rnp-menu-item {:title (lstr-ml "addEntry")
                      :disabled @(cmn-events/current-db-disable-edit)
                      :onPress (fn [] (ecat-events/add-new-entry {:name (:title category-detail) :uuid (:uuid category-detail)} UUID_OF_ENTRY_TYPE_LOGIN))}]
      [rnp-menu-item {:title (lstr-ml "addGroup")
                      :disabled @(cmn-events/current-db-disable-edit)
                      :onPress #(ecat-events/initiate-new-blank-group-form (:uuid category-detail))}]
      [cust-rnp-divider]
      [rnp-menu-item {:title (lstr-ml "edit")
                      :disabled @(cmn-events/current-db-disable-edit)
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
  [rnp-menu {:visible show :key (str show) :onDismiss hide-group-by-menu :anchor (clj->js {:x x :y y})}
   [rnp-menu-item {:title (lstr-ml "types")
                   :leadingIcon (if (= group-by :type) ICON-CHECKBOX-OUTLINE ICON-CHECKBOX-BLANK-OUTLINE)
                   :onPress #(change-entries-grouping-method :type)}]
   [rnp-menu-item {:title (lstr-ml "tags")
                   :leadingIcon (if (= group-by :tag) ICON-CHECKBOX-OUTLINE ICON-CHECKBOX-BLANK-OUTLINE)
                   :onPress #(change-entries-grouping-method :tag)}]
   [rnp-menu-item {:title (lstr-ml "categories")
                   :leadingIcon (if (= group-by :group-category) ICON-CHECKBOX-OUTLINE ICON-CHECKBOX-BLANK-OUTLINE)
                   :onPress #(change-entries-grouping-method :group-category)}]
   [rnp-menu-item {:title (lstr-ml "groups")
                   :leadingIcon (if (= group-by :group-tree) ICON-CHECKBOX-OUTLINE ICON-CHECKBOX-BLANK-OUTLINE)
                   :onPress #(change-entries-grouping-method :group-tree)}]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; check-all, heart-outline delete-outline, login, bank-outline,credit-card-outline, access-point,router-wireless
;; earth,airplane,dots-horizontal,dots-vertical

(def category-icons {;; General categories
                     CATEGORY_ALL_ENTRIES const/ICON-CHECK-ALL
                     CATEGORY_FAV_ENTRIES const/ICON-HEART-OUTLINE
                     CATEGORY_DELETED_ENTRIES const/ICON-TRASH-CAN-OUTLINE
                     ;; Following are hard coded entry type icon names
                     LOGIN_TYPE_NAME const/ICON-LOGIN
                     BANK_ACCOUNT_TYPE_NAME const/ICON-BANK-OUTLINE
                     WIRELESS_ROUTER_TYPE_NAME const/ICON-ROUTER-WIRELESS
                     CREDIT_DEBIT_CARD_TYPE_NAME const/ICON-CREDIT-CARD-OUTLINE
                     AUTO_DB_OPEN_TYPE_NAME const/ICON-LAUNCH})

(defn category-icon-name
  "Called to get icon name for General categories or Entry types category or Group as Category or Group "
  [{:keys [title icon-name icon-id uuid tag-id]}]
  (let [icon (get category-icons title)]
    (cond
      ;; General categories or Standard entry types only
      icon
      icon
      ;; Group tree root or Group category will have non nil uuid and valid icon-id int value
      (not (nil? uuid))
      (icon-id->name icon-id)

      (not (nil? tag-id))
      ICON-TAGS

      ;; custom entry type will have icon-name convertable to an int
      :else
      (let [cust-entry-type-icon-id (str->int icon-name)]
        (if cust-entry-type-icon-id
          (icon-id->name cust-entry-type-icon-id)
          (icon-id->name 0))))))

(defn translate-cat-title [category-key title display-title]
  (let [display-name (if (nil? display-title) title display-title)
        display-name (cond
                       (= category-key GENERAL_KEY)
                       (lstr-cv  display-name)

                       (and (= category-key TYPE_SECTION_TITLE) (contains-val?  STANDARD_ENTRY_TYPES display-name))
                       (lstr-entry-type-title display-name)

                       :else
                       display-name)]
    display-name))

(defn category-item
  "category-detail-m is a map representing struct 'CategoryDetail'
   category-key is one of key used in section data - General,Types,Tags,Categories, or Groups
  "
  [_category-detail-m category-key root-group]
  ;; should the following need to accept category-key for react comp?
  (fn [{:keys [title display-title entries-count groups-count] :as category-detail-m}]
    (let [display-name (translate-cat-title category-key title display-title)
          icon-name (category-icon-name category-detail-m)
          items-count (if (= category-key GROUP_SECTION_TITLE) (+ entries-count groups-count) entries-count)]
      [rnp-list-item {;;:style {:background-color @rnc/background-color}
                      :onPress (fn [_e]
                                 (ecat-events/load-selected-category-entry-items
                                  category-detail-m category-key))

                      :onLongPress  (fn [event]
                                      (if (= GENERAL_KEY category-key)
                                        (ecat-events/load-selected-category-entry-items
                                         category-detail-m
                                         category-key)
                                        (show-category-long-press-menu
                                         event
                                         {:category-key category-key
                                          :category-detail category-detail-m
                                          :root-group root-group})))
                      :title (r/as-element
                              [rnp-text {:variant "titleMedium"} display-name]) ;;:style {:color @rnc/on-background-color}

                      :left (fn [_props] (r/as-element
                                          [rnp-list-icon {:style {:height 20}
                                                          :icon icon-name
                                                          :color @icon-color}]))

                      :right (fn [_props] (r/as-element
                                           [rnp-text {:variant "titleMedium"} items-count]))}])))

;; title may be one of Types,Groups,Categories, Tags
(defn category-header [title group-by]
  [rn-view  {:style {:flexDirection "row"
                     :backgroundColor  @primary-container-color
                     :margin-top 5
                     :min-height 38}}
   [rnp-text {:style {:alignSelf "center" :width "85%" :padding-left 15} :variant "titleLarge"}
    ;; The translation used for menu labels are also used for this header  
    (lstr-ml title)]
   [rnp-icon-button {:icon dots-icon-name
                     :style {:height 38 :margin-right 0 :backgroundColor @on-primary-color}
                     :onPress #(show-group-by-menu % group-by)}]])

(defn categories-content []
  (let [general-categories @(ecat-events/general-categories)
        ;;group-by is kw and is one of :type, :group-tree, :group-category
        group-by @(ecat-events/entries-grouping-method)

        ;; Root group summary data map
        root-group @(ecat-events/root-group)

        ;; Convert the kw to use as :title in 'sections list' 
        section-title  (get group-by->section-titles group-by)

        section-data (cond
                       (= group-by :type)
                       @(ecat-events/type-categories)

                       (= group-by :tag)
                       @(ecat-events/tag-categories)

                       (= group-by :group-category)
                       @(ecat-events/group-categories)

                       (= group-by :group-tree)
                       (vector @(ecat-events/group-tree-root-summary)))

        sections  [{:title GENERAL_KEY
                    :key GENERAL_KEY ;; passed as category-key to category-item
                    :data (if (nil? general-categories) [] general-categories)}

                   {:title section-title
                    :key section-title ;; category-key
                    :data (if (nil? section-data) [] section-data)}]]
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
                               (when-not (= key GENERAL_KEY)
                                 (r/as-element [category-header title group-by]))))}]))


(defn- bottom-nav-bar
  "A functional reagent componnent that returns the custom bottom bar"
  []
  (fn []
    (let []

      [rn-view {:style {:width "100%"
                        ;; Need to use the same background-color as the entry list content to make it opaque
                        :background-color @page-background-color
                        :padding-left 25
                        :padding-right 25
                        :borderTopWidth 1
                        :borderTopColor  @rnc/outline-variant
                        :min-height 50

                        ;;:position "absolute"

                        ;; In adndroid when we use absolute position, this bottom bar hides
                        ;; the entries list content - particularly when the list has more entries
                        ;; and even using the scroll does not work and it scrolls behind this component 

                        ;; Not using absolute position works for both android in iOS

                        ;; After Android 'compileSdkVersion = 35 introduction, adding insets hides the entries list content
                        ;; Also see comments in js/components/KeyboardAvoidingDialog.js

                        ;; Instead of setting bottom value from inset, we are using a dummy view 'adjust-inset-view'

                        :bottom 0}}

       [rn-view {:flexDirection "row" :justifyContent "space-between"}
        [rn-view {:align-items "center"}
         [rnp-icon-button {:size 24
                           :icon const/ICON-HOUSE-VARIANT
                           :iconColor @rnc/on-error-container-color
                           :onPress (fn [e]
                                      #_(show-sort-menu e sort-criteria))}]
         [rnp-text {:style {:margin-top -5}
                    :text-align "center"}
          "Home"
          #_(lstr-l 'sort)]]

        [rn-view {:align-items "center"}
         [rnp-icon-button {:size 24
                           :icon const/ICON-PLUS
                           :iconColor @rnc/on-error-container-color
                           :disabled @(cmn-events/current-db-disable-edit)
                           :onPress (fn [e]
                                      #_(show-fab-action-menu e selected-category-key selected-category-detail))}]
         [rnp-text {:style {:margin-top -5}
                    :text-align "center"}
          (lstr-l 'add)]]]])))


(defn entry-category-content []
  [rn-safe-area-view {:style {:flex 1 :background-color @page-background-color}}
   [categories-content]
   [:f> bottom-nav-bar]
   [fab-action-menu @fab-action-menu-data @(ecat-events/root-group)]
   [category-long-press-menu @category-long-press-menu-data]
   [group-by-menu @group-by-menu-data]
   [rnp-fab {:style {:position "absolute" :margin 16 :right 0 :bottom (+ (rnc/get-inset-bottom) 100)}
             :disabled @(cmn-events/current-db-disable-edit)
             :icon ICON-PLUS :onPress (fn [e] (show-fab-action-menu e))}]])