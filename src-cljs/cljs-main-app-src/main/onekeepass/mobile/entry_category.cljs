(ns onekeepass.mobile.entry-category
  (:require
   [onekeepass.mobile.background :as bg]
   [onekeepass.mobile.bottom-navigator :as bn]
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
   [onekeepass.mobile.events.custom-icons :as ci-events]
   [onekeepass.mobile.events.entry-category :as ecat-events]
   [onekeepass.mobile.events.entry-list :as elist-events]
   [onekeepass.mobile.events.search :as search-events]
   [onekeepass.mobile.entry-list :as entry-list]
   [onekeepass.mobile.grouped-list :as gl]
   [onekeepass.mobile.icons-list :refer [ENTRY-GROUP-LIST-ICON-SIZE
                                         icon-id->name]]
   [onekeepass.mobile.ios.passkey-pending :as passkey-pending]
   [onekeepass.mobile.rn-components :as rnc :refer [cust-rnp-divider
                                                    dots-icon-name icon-color
                                                    on-surface-variant
                                                    rn-image rn-safe-area-view
                                                    rn-section-list rn-view
                                                    rnp-fab
                                                    rnp-list-icon
                                                    rnp-list-item rnp-menu
                                                    rnp-menu-item rnp-text
                                                    row-highlight-style]]
   [onekeepass.mobile.translation :refer [lstr-cv lstr-entry-type-title
                                          lstr-l lstr-ml]]
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

;; Every action of this menu hides the menu first. Otherwise the menu and the highlight of
;; the row it belongs to are still there when the user comes back from the page the action
;; has taken them to
(def category-long-press-menu-action (menu-action-factory hide-category-long-press-menu))

(defn category-long-press-menu [{:keys [show x y category-detail category-key]}]
  [rnp-menu {:visible show :key (str show) :onDismiss hide-category-long-press-menu :anchor (clj->js {:x x :y y})}
   (cond

     ;;  @(cmn-events/current-db-disable-edit)
     ;;  nil

     (= category-key TYPE_SECTION_TITLE)
     [rnp-menu-item {:title (lstr-ml "addEntry")
                     :disabled @(cmn-events/current-db-disable-edit)
                     :onPress (category-long-press-menu-action
                               ecat-events/add-new-entry nil (:entry-type-uuid category-detail))}]

     (= category-key TAG_SECTION_TITLE)
     [rnp-menu-item {:title (lstr-ml "addEntry")
                     :disabled @(cmn-events/current-db-disable-edit)
                     :onPress (category-long-press-menu-action
                               ecat-events/add-new-entry nil UUID_OF_ENTRY_TYPE_LOGIN)}]

     (= category-key CAT_SECTION_TITLE)
     [:<>

      [rnp-menu-item {:title (lstr-ml "addEntry")
                      :disabled @(cmn-events/current-db-disable-edit)
                      :onPress (category-long-press-menu-action
                                ecat-events/add-new-entry
                                {:name (:title category-detail) :uuid (:uuid category-detail)}
                                UUID_OF_ENTRY_TYPE_LOGIN)}]
      [cust-rnp-divider]
      [rnp-menu-item {:title (lstr-ml "edit")
                      :disabled @(cmn-events/current-db-disable-edit)
                      :onPress (category-long-press-menu-action
                                ecat-events/find-category-by-id (:uuid category-detail))}]]


     (= category-key GROUP_SECTION_TITLE)
     [:<>

      [rnp-menu-item {:title (lstr-ml "addEntry")
                      :disabled @(cmn-events/current-db-disable-edit)
                      :onPress (category-long-press-menu-action
                                ecat-events/add-new-entry
                                {:name (:title category-detail) :uuid (:uuid category-detail)}
                                UUID_OF_ENTRY_TYPE_LOGIN)}]
      [rnp-menu-item {:title (lstr-ml "addGroup")
                      :disabled @(cmn-events/current-db-disable-edit)
                      :onPress (category-long-press-menu-action
                                ecat-events/initiate-new-blank-group-form (:uuid category-detail))}]
      [cust-rnp-divider]
      [rnp-menu-item {:title (lstr-ml "edit")
                      :disabled @(cmn-events/current-db-disable-edit)
                      :onPress (category-long-press-menu-action
                                ecat-events/find-group-by-id (:uuid category-detail))}]]

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
                     AUTO_DB_OPEN_TYPE_NAME const/ICON-LAUNCH
                     const/PASSPORT_TYPE_NAME const/ICON-PASSPORT
                     const/IDENTITY_TYPE_NAME const/ICON-CARD-ACCOUNT-DETAILS-OUTLINE
                     const/DRIVER_LICENSE_TYPE_NAME const/ICON-CAR-OUTLINE
                     const/SSH_KEY_TYPE_NAME const/ICON-KEY-VARIANT
                     const/REMOTE_CONNECTION_SFTP_TYPE_NAME const/ICON-FOLDER-NETWORK-OUTLINE
                     const/REMOTE_CONNECTION_WEBDAV_TYPE_NAME const/ICON-CLOUD-OUTLINE})

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

(defn- category-left-icon
  "Renders the category row's left icon. If the category has a custom
   icon assigned (group rows whose backing GroupSummary carries
   :custom-icon-uuid), show the image; otherwise fall back to the
   MaterialCommunityIcons glyph."
  [icon-name custom-icon-uuid]
  (when custom-icon-uuid (ci-events/ensure-icon-data-url custom-icon-uuid))
  (let [data-url (when custom-icon-uuid @(ci-events/icon-data-url custom-icon-uuid))]
    (if data-url
      [rn-view {:style {:margin-left 5 :align-self "center"
                        :width ENTRY-GROUP-LIST-ICON-SIZE
                        :height ENTRY-GROUP-LIST-ICON-SIZE}}
       [rn-image {:source (clj->js {:uri data-url})
                  :style {:width ENTRY-GROUP-LIST-ICON-SIZE
                          :height ENTRY-GROUP-LIST-ICON-SIZE}}]]
      [rnp-list-icon {:style {:margin-left 5
                              :align-self "center"
                              :width ENTRY-GROUP-LIST-ICON-SIZE
                              :height ENTRY-GROUP-LIST-ICON-SIZE}
                      :icon icon-name
                      :color @icon-color}])))

;; Keys of the sections the user has collapsed - presentation only state of this page
(def ^:private collapsed-sections (r/atom #{}))

(defn- open-category
  "Leaves this page to show the entries of the pressed category"
  [category-detail-m category-key]
  (ecat-events/load-selected-category-entry-items category-detail-m category-key))

(defn category-item
  "category-detail-m is a map representing struct 'CategoryDetail'
   category-key is one of key used in section data - General,Types,Tags,Categories, or Groups
  "
  [_category-detail-m _category-key _root-group]
  ;; The inner fn has to take all three args. A row rendered by the section list is reused
  ;; for another category once the grouping is switched, and closing over the args of the
  ;; very first render would leave such a reused row on the category key it started with
  (fn [{:keys [title display-title entries-count groups-count custom-icon-uuid] :as category-detail-m}
       category-key root-group]
    (let [display-name (translate-cat-title category-key title display-title)
          icon-name (category-icon-name category-detail-m)
          ;; Only a group of the group tree holds other groups. For every other category
          ;; kind the row stands for entries alone
          sub-groups-count (if (= category-key GROUP_SECTION_TITLE) groups-count 0)
          {menu-show :show
           menu-category-detail :category-detail
           menu-category-key :category-key} @category-long-press-menu-data]
      [rnp-list-item {:style (row-highlight-style (and menu-show
                                                       (= category-key menu-category-key)
                                                       (= category-detail-m menu-category-detail)))
                      :onPress (fn [_e]
                                 (open-category category-detail-m category-key))

                      :onLongPress  (fn [event]
                                      (if (= GENERAL_KEY category-key)
                                        (open-category category-detail-m category-key)
                                        (show-category-long-press-menu
                                         event
                                         {:category-key category-key
                                          :category-detail category-detail-m
                                          :root-group root-group})))
                      :title (r/as-element
                              [rnp-text {:variant "titleMedium"} display-name])

                      :description (gl/items-count-description entries-count sub-groups-count)
                      :descriptionStyle {:color @on-surface-variant}

                      :left (fn [_props] (r/as-element
                                          [category-left-icon icon-name custom-icon-uuid]))

                      :right (fn [_props] (r/as-element [gl/row-chevron]))}])))

(defn category-header
  "Header of one of the sections that make up the current grouping.

   'grouping-menu?' is set for the first such section only, so that the menu that switches
   the grouping has exactly one place to hang off whatever the grouping turns out to show"
  [{:keys [label section-key items-count grouping-menu?]} group-by]
  [gl/section-header {:label label
                      :items-count items-count
                      :collapsed? (gl/collapsed? collapsed-sections section-key)
                      :on-toggle #(gl/toggle-collapsed collapsed-sections section-key)
                      :trailing-icon (when grouping-menu? dots-icon-name)
                      :on-trailing-press #(show-group-by-menu % group-by)}])

(defn- grouping-section
  "One section of the current grouping, ready for the section list. Nothing is returned when
   the section has no matching row - a collapsed section still returns its header, only its
   rows are held back"
  [{:keys [section-key label rows]}]
  (when (seq rows)
    {:key section-key
     :header-label label
     :items-count (count rows)
     :data (gl/section-data collapsed-sections section-key rows)}))

(defn categories-content []
  (let [general-categories @(ecat-events/general-categories)
        ;;group-by is kw and is one of :type, :tag, :group-tree, :group-category
        group-by @(ecat-events/entries-grouping-method)
        group-tree? (= group-by :group-tree)

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

                       ;; The root group is not shown as a row the user has to open first.
                       ;; What it holds is listed on this page instead - its sub groups here
                       ;; and its own entries in a section of their own below
                       group-tree?
                       @(ecat-events/sub-groups-summary (:uuid root-group)))

        general-rows (if (nil? general-categories) [] general-categories)
        section-rows (if (nil? section-data) [] section-data)

        entry-rows (if group-tree?
                     (let [rows @(ecat-events/root-group-entry-items)]
                       (if (nil? rows) [] rows))
                     [])

        grouping-sections (keep identity
                                [(grouping-section {:section-key section-title
                                                    :label (lstr-ml section-title)
                                                    :rows section-rows})
                                 (when group-tree?
                                   (grouping-section {:section-key const/ENTRIES_SECTION_TITLE
                                                      :label (lstr-cv const/ENTRIES_SECTION_TITLE)
                                                      :rows entry-rows}))])

        ;; With nothing at all under the current grouping there would be no header to reach
        ;; the grouping menu from, leaving the user unable to switch to another grouping.
        ;; An empty header is kept for that case
        grouping-sections (if (and (empty? grouping-sections)
                                   ;; nil until the grouping the db was opened with is known
                                   (some? section-title))
                            [{:key section-title
                              :header-label (lstr-ml section-title)
                              :items-count 0
                              :data []}]
                            grouping-sections)

        ;; Only the first section of the grouping carries the grouping menu
        grouping-sections (map-indexed (fn [idx section]
                                         (assoc section :grouping-menu? (= idx 0)))
                                       grouping-sections)

        sections (cond-> []
                   (seq general-rows)
                   (conj {:key GENERAL_KEY ;; passed as category-key to category-item
                          :data general-rows})

                   :always
                   (into grouping-sections))]

    (if (empty? sections)
      [gl/no-match-view]
      [rn-section-list
       {:style {:flex 1}
        :contentContainerStyle {:padding-bottom gl/FAB-LIST-BOTTOM-CLEARANCE}
        :sections (clj->js sections)
        :stickySectionHeadersEnabled false
        :renderItem (fn [props] ;; keys are (:item :index :section :separators)
                      (let [{:keys [item index section]} (js->clj props :keywordize-keys true)]
                        (r/as-element
                         [gl/card-row (gl/row-position index (count (:data section)))
                          (if (= const/ENTRIES_SECTION_TITLE (:key section))
                            [entry-list/row-item item]
                            [category-item item (:key section) root-group])])))
        :ItemSeparatorComponent (fn [_p]
                                  (r/as-element [gl/card-row-separator]))
        :renderSectionHeader (fn [props] ;; key is :section
                               (let [props (js->clj props :keywordize-keys true)
                                     {:keys [key header-label items-count grouping-menu?]} (-> props :section)]
                                 (when-not (= key GENERAL_KEY)
                                   (r/as-element [category-header {:label header-label
                                                                   :section-key key
                                                                   :items-count items-count
                                                                   :grouping-menu? grouping-menu?}
                                                  group-by]))))}])))

(defn- show-fab []
  [rnp-fab {:style {:position "absolute" :margin 16 :right 0 :bottom (+ (rnc/get-inset-bottom) 100)}
            :disabled @(cmn-events/current-db-disable-edit)
            :icon ICON-PLUS :onPress (fn [e] (show-fab-action-menu e))}])

(defn- bottom-nav-bar []
  (fn []
    (let [sort-criteria @(elist-events/entry-list-sort-criteria)
          group-tree? (= @(ecat-events/entries-grouping-method) :group-tree)
          has-root-entries? (seq @(ecat-events/root-group-entry-items))
          disable-time-sort? (not (and group-tree? has-root-entries?))
          items [(bn/home-icon-action-item)
                 (bn/close-db-icon-action-item)
                 {:icon const/ICON-SORT
                  :label (lstr-l 'sort)
                  :action #(entry-list/show-sort-menu % sort-criteria disable-time-sort?)}
                 (bn/settings-icon-action-item)]]
      [bn/bottom-nav-bar-gen items])))

(defn entry-category-content []
  (let [term @(search-events/search-term)]
    [rn-safe-area-view (cond-> {:style (gl/page-style)}
                         (not (bg/is-iOS)) (assoc :edges #js ["right" "left"]))
     [gl/inline-search-bar {:term term
                            :on-change search-events/search-term-update}]
     (if (gl/searching? term)
       [entry-list/search-results-content true]
       [categories-content])
     [:f> bottom-nav-bar]
     [show-fab]

     [fab-action-menu @fab-action-menu-data @(ecat-events/root-group)]
     [category-long-press-menu @category-long-press-menu-data]
     [group-by-menu @group-by-menu-data]
     [entry-list/sort-menu]
     ;; Needed by the entry rows this page lists - the root group's own entries under
     ;; 'Groups', and the matches of whatever is typed into the search bar
     [entry-list/entry-row-menus-and-dialogs]
     (when (bg/is-iOS)
       [passkey-pending/ios-pending-passkey-notification-dialog])]))
