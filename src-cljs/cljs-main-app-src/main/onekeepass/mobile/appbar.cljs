(ns onekeepass.mobile.appbar
  (:require [onekeepass.mobile.about :as about :refer [about-content
                                                       privacy-policy-content]]
            [onekeepass.mobile.app-database-settings :as app-db-settings]
            [onekeepass.mobile.app-lock :as app-lock]
            [onekeepass.mobile.app-lock-settings :as app-lock-settings]
            [onekeepass.mobile.app-settings :as app-settings]
            [onekeepass.mobile.autofill :as af-settings]
            [onekeepass.mobile.common-components :as cc :refer [menu-action-factory]]
            [onekeepass.mobile.constants  :refer [ABOUT_PAGE_ID
                                                  ADDITIONAL_DATABASE_ACCESS_SETTINGS_PAGE_ID
                                                  APP_LOCK_SETTINGS_PAGE_ID
                                                  APP_SETTINGS_PAGE_ID
                                                  AUTOFILL_SETTINGS_PAGE_ID
                                                  BLANK_PAGE_ID
                                                  CAMERA_SCANNER_PAGE_ID
                                                  ENTRY_CATEGORY_PAGE_ID
                                                  ENTRY_FORM_PAGE_ID
                                                  ENTRY_HISTORY_LIST_PAGE_ID
                                                  ENTRY_LIST_PAGE_ID
                                                  GROUP_FORM_PAGE_ID
                                                  HOME_PAGE_ID
                                                  ICONS_LIST_PAGE_ID
                                                  KEY_FILE_FORM_PAGE_ID
                                                  MANAGE_CUSTOM_ICONS_PAGE_ID
                                                  MERGE_DATABASE_PAGE_ID
                                                  PASSWORD_GENERATOR_PAGE_ID
                                                  PASSKEY_PENDING_REVIEW_PAGE_ID
                                                  PRIVACY_POLICY_PAGE_ID
                                                  SEARCH_PAGE_ID
                                                  SETTINGS_CREDENTIALS_PAGE_ID
                                                  SETTINGS_ENCRYPTION_PAGE_ID
                                                  SETTINGS_GENERAL_PAGE_ID
                                                  SETTINGS_KDF_PAGE_ID
                                                  SETTINGS_PAGE_ID
                                                  SETTINGS_SECURITY_PAGE_ID
                                                  RS_CONNECTION_CONFIG_PAGE_ID
                                                  RS_CONNECTIONS_LIST_PAGE_ID
                                                  RS_FILES_FOLDERS_PAGE_ID]]
            [onekeepass.mobile.entry-category :refer [entry-category-content]]
            [onekeepass.mobile.entry-form :as entry-form]
            [onekeepass.mobile.entry-history-list :as entry-history-list]
            [onekeepass.mobile.entry-list :as entry-list :refer [entry-list-content]]
            [onekeepass.mobile.events.app-lock :as app-lock-events]
            [onekeepass.mobile.events.app-settings :as as-events]
            [onekeepass.mobile.events.common :as cmn-events]
            [onekeepass.mobile.events.entry-form :as ef-events]
            [onekeepass.mobile.events.entry-list :as elist-events]
            [onekeepass.mobile.events.external-db-change :as ext-change-events]
            [onekeepass.mobile.events.merging :as merging-events]
            [onekeepass.mobile.events.password-generator :as pg-events]
            [onekeepass.mobile.events.remote-storage :as rs-events]
            [onekeepass.mobile.events.search :as search-events]
            [onekeepass.mobile.events.settings :as stgs-events]
            [onekeepass.mobile.group-form :as group-form]
            [onekeepass.mobile.icons-list :as icons-list]
            [onekeepass.mobile.ios.passkey-pending :as passkey-pending]
            [onekeepass.mobile.key-file-form :as kf-form]
            [onekeepass.mobile.manage-custom-icons :as manage-custom-icons]
            [onekeepass.mobile.merging :as merging]
            [onekeepass.mobile.password-generator :as pg]
            [onekeepass.mobile.rn-components :as rnc :refer [background-color
                                                             cust-rnp-divider
                                                             dots-icon-name
                                                             on-primary-color
                                                             primary-color
                                                             rn-view
                                                             rnp-appbar-action
                                                             rnp-appbar-back-action
                                                             rnp-appbar-content
                                                             rnp-appbar-header
                                                             rnp-menu
                                                             rnp-menu-item]]
            [onekeepass.mobile.rs-config-form :as rs-form]
            [onekeepass.mobile.rs-configs :as rs-configs]
            [onekeepass.mobile.rs-files-folders :as rs-files-folders]
            [onekeepass.mobile.scan-otp-qr :as scan-otp-qr]
            [onekeepass.mobile.search :as search]
            [onekeepass.mobile.settings :as settings :refer [db-settings-form-content]]
            [onekeepass.mobile.start-page :refer [open-page-content]]
            [onekeepass.mobile.translation :refer [lstr-ml lstr-pt]]
            [onekeepass.mobile.utils :as u]
            [reagent.core :as r]))

(set! *warn-on-infer* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Used in Android mobiles
(defn back-action
  "Called when user presses the hardware or os provided back button 
   This is somewhat similar to the app back action - see inside appbar-header-content
   Returns 
      true - the app handles the back action and the event will not be bubbled up
      false - the system's default back action to be executed
   "
  [{:keys [page]}]
  (cond
    (= page HOME_PAGE_ID)
    false

    (= page ENTRY_LIST_PAGE_ID)
    (do
      (elist-events/entry-list-back-action)
      true)

    (= page RS_FILES_FOLDERS_PAGE_ID)
    (do (rs-events/remote-storage-listing-previous)
        true)

    (= page MERGE_DATABASE_PAGE_ID)
    (do (merging-events/merge-database-back-action)
        true)

    (or
     (= page ENTRY_CATEGORY_PAGE_ID)
     (= page ENTRY_FORM_PAGE_ID)
     (= page ENTRY_HISTORY_LIST_PAGE_ID)
     (= page ICONS_LIST_PAGE_ID)
     (= page SEARCH_PAGE_ID)
     (= page PASSWORD_GENERATOR_PAGE_ID)
     (= page GROUP_FORM_PAGE_ID)
     (= page SETTINGS_PAGE_ID)
     (= page APP_SETTINGS_PAGE_ID)
     (= page AUTOFILL_SETTINGS_PAGE_ID)
     (= page KEY_FILE_FORM_PAGE_ID)
     (= page CAMERA_SCANNER_PAGE_ID)
     (= page ABOUT_PAGE_ID)
     (= page PRIVACY_POLICY_PAGE_ID)
     (= page RS_CONNECTION_CONFIG_PAGE_ID)
     (= page RS_CONNECTIONS_LIST_PAGE_ID)
     (= page ADDITIONAL_DATABASE_ACCESS_SETTINGS_PAGE_ID)
     (= page APP_LOCK_SETTINGS_PAGE_ID)
     (= page MANAGE_CUSTOM_ICONS_PAGE_ID)
     (= page PASSKEY_PENDING_REVIEW_PAGE_ID))
    (do
      (cmn-events/to-previous-page)
      true)

    :else
    (do
      (println "Else page " page)
      false)))

;; holds additional copy of the current page
(def ^:private current-page-info (atom nil))

(defn hardware-back-pressed []
  (back-action @current-page-info))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; header menu ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private header-menu-data (r/atom  {:show false :x 0 :y 0}))

(defn- header-menu-show [^js/PEvent event]
  (swap! header-menu-data assoc :show true :x (-> event .-nativeEvent .-pageX) :y (-> event .-nativeEvent .-pageY)))

(defn header-menu-hide []
  (swap! header-menu-data assoc :show false))

(def header-menu-action (menu-action-factory header-menu-hide))

(defn header-menu [{:keys [show x y]} {:keys [page]}]

  ;; https://github.com/callstack/react-native-paper/issues/4807
  ;; Workaround to make menu popup work always by adding :key (str show)
  ;; See rn_components.cljs where rnp-menu is defined
  [rnp-menu {:visible show
             :key (str show)
             :onDismiss header-menu-hide
             :anchor (clj->js {:x x :y y})}
   (cond
     (= page HOME_PAGE_ID)
     [:<>
      [rnp-menu-item {:title (lstr-ml "appSettings")
                      :onPress (header-menu-action as-events/to-app-settings-page)}]
      #_[cust-rnp-divider]
      [rnp-menu-item {:title (lstr-ml "about")
                      :onPress (header-menu-action cmn-events/to-about-page)}]
      [rnp-menu-item {:title (lstr-ml "privacyPolicy")
                      :onPress (header-menu-action cmn-events/to-privacy-policy-page)}]]

     (and (= page ENTRY_LIST_PAGE_ID) @(elist-events/deleted-category-showing))
     (let [items @(elist-events/selected-entry-items)]
       ;; items is all entry summary items found under 'Deleted' category and disable this if it is empty
       [rnp-menu-item {:title (lstr-ml "deleteAll")
                       :disabled (empty? items)
                       :onPress (header-menu-action
                                 entry-list/show-delete-all-entries-permanent-confirm-dialog items)}])

     (or (= page ENTRY_CATEGORY_PAGE_ID) (= page ENTRY_LIST_PAGE_ID))
     [:<>
      [rnp-menu-item {:title (lstr-ml "home")
                      :onPress (header-menu-action cmn-events/to-home-page)}]
      [rnp-menu-item {:title (lstr-ml "pwdGenerator")
                      :onPress (header-menu-action pg-events/generate-password)}]
      [cust-rnp-divider]
      [rnp-menu-item {:title  (lstr-ml "lockdb")
                      :onPress (header-menu-action cmn-events/lock-kdbx nil)}]

      [rnp-menu-item {:title (lstr-ml "closedb")
                      :onPress (header-menu-action cmn-events/close-current-kdbx-db)}]
      [cust-rnp-divider]
      [rnp-menu-item {:title (lstr-ml "mergeDatabases")
                      :disabled  @(cmn-events/current-db-disable-edit)
                      :onPress (header-menu-action merging-events/merging-databases-page)}]
      [rnp-menu-item {:title (lstr-ml "checkRemoteChanges")
                      :disabled (not (cmn-events/remote-db-key? @(cmn-events/active-db-key)))
                      :onPress (header-menu-action ext-change-events/manual-check-remote-changes)}]
      [cust-rnp-divider]
      [rnp-menu-item {:title (lstr-ml "settings")
                      :onPress (header-menu-action stgs-events/load-db-settings)}]]

     (= page ENTRY_HISTORY_LIST_PAGE_ID)
     [:<>
      [rnp-menu-item {:title (lstr-ml "deleteAll")
                      :disabled  @(cmn-events/current-db-disable-edit)
                      :onPress (header-menu-action ef-events/show-history-entry-delete-all-confirm-dialog)}]]

     (and (= page ENTRY_FORM_PAGE_ID) @(ef-events/history-entry-form?))
     [:<>
      [rnp-menu-item {:title (lstr-ml "restore")
                      :disabled  @(cmn-events/current-db-disable-edit)
                      :onPress (header-menu-action ef-events/show-history-entry-restore-confirm-dialog)}]
      [rnp-menu-item {:title (lstr-ml "delete")
                      :disabled  @(cmn-events/current-db-disable-edit)
                      :onPress (header-menu-action ef-events/show-history-entry-delete-confirm-dialog)}]]

     (= page ENTRY_FORM_PAGE_ID)
     (let [fav @(ef-events/favorites?)
           entry-uuid @(ef-events/entry-form-uuid)
           parent-group-uuid @(ef-events/entry-form-parent-group-uuid)]
       [:<>
        [rnp-menu-item {:title (lstr-ml "favorite") :trailingIcon (if fav "check" nil)
                        :disabled (not @(ef-events/history-available))
                        :onPress (header-menu-action ef-events/favorite-menu-checked (not fav))}]
        [rnp-menu-item {:title (lstr-ml 'history)
                        :disabled (not @(ef-events/history-available))
                        :onPress (header-menu-action ef-events/load-history-entries-summary entry-uuid)}]
        ;; [cust-rnp-divider]
        ;; [rnp-menu-item {:title "Password Generator" :onPress #()}]
        [cust-rnp-divider]
        [rnp-menu-item {:title (lstr-ml "move")
                        :disabled  @(cmn-events/current-db-disable-edit)
                        :onPress (header-menu-action entry-list/move-entry-dialog-show-with-state entry-uuid parent-group-uuid)}]

        [rnp-menu-item {:title (lstr-ml "delete")
                        :disabled  @(cmn-events/current-db-disable-edit)
                        :onPress (header-menu-action cc/show-entry-delete-confirm-dialog entry-uuid)}]]))])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#_(def appbar-content-style {:padding-top 0
                             :alignItems "center"
                             ;;:color background-color
                             :backgroundColor @primary-color})

(defn is-settings-page [page]
  (u/contains-val? [SETTINGS_GENERAL_PAGE_ID SETTINGS_CREDENTIALS_PAGE_ID SETTINGS_SECURITY_PAGE_ID SETTINGS_ENCRYPTION_PAGE_ID SETTINGS_KDF_PAGE_ID] page))

;; If we have more than one appbar action icon on right side, the title text is not centered
;; One solution is to use appbar content with absolute position based on the discussion here
;;https://stackoverflow.com/questions/54120003/how-can-i-center-the-title-in-appbar-header-in-react-native-paper 

(defn positioned-title [& {:keys [title page style titleStyle]}]

  [:<>
   ;; Need a dummy content so that icons(from rnp-appbar-action) are placed on the right side 
   ;; We need to use the dummy one's zIndex = -1 so that any click action on the buttons used inside the custom title
   ;; are active. Otherwise this dummy content will be above the title buttons and clicking will not work 
   [rnp-appbar-content {:style {:zIndex -1}}]
   ;; Need to use max-width in titleStyle for the text to put ...
   [rnp-appbar-content {:style (merge {:marginLeft 0  :position "absolute", :left 0, :right 0, :zIndex -1} style)
                        :color @background-color
                        :titleStyle (merge {:align-self "center"} titleStyle)

                        ;; For some forms provides its own appbar title
                        :title (cond

                                 (= page ENTRY_FORM_PAGE_ID)
                                 (r/as-element [entry-form/appbar-title])

                                 (= page GROUP_FORM_PAGE_ID)
                                 (r/as-element [group-form/appbar-title title])

                                 (= page PASSWORD_GENERATOR_PAGE_ID)
                                 (r/as-element [pg/appbar-title])

                                 (is-settings-page page)
                                 (r/as-element [settings/appbar-title page])

                                 (= page RS_CONNECTIONS_LIST_PAGE_ID)
                                 (r/as-element [rs-configs/appbar-title])

                                 (= page PASSKEY_PENDING_REVIEW_PAGE_ID)
                                 (r/as-element [passkey-pending/appbar-title])

                                 ;;  (= page APP_LOCK_SETTINGS_PAGE_ID)
                                 ;;  (r/as-element [app-lock-settings/appbar-title])

                                 ;;TODO 
                                 ;; Need to add translation of titles for Entry types and General cat types
                                 ;; Something similar one used in entry category page
                                 (= page ENTRY_LIST_PAGE_ID)
                                 title

                                 ;; No translation of text
                                 (= page ENTRY_CATEGORY_PAGE_ID)
                                 title

                                 ;; Title for all other pages 
                                 ;; Here title is the key to which pageTitles prefix will be 
                                 ;; added and value is got from i18n map
                                 (string? title)
                                 (lstr-pt title)

                                 :else
                                 "No Title")}]])

(def page-id-based-title-providers [ENTRY_FORM_PAGE_ID
                                    PASSWORD_GENERATOR_PAGE_ID
                                    RS_CONNECTIONS_LIST_PAGE_ID
                                    PASSKEY_PENDING_REVIEW_PAGE_ID])

(def title-provider-pages [HOME_PAGE_ID
                           ABOUT_PAGE_ID
                           PRIVACY_POLICY_PAGE_ID
                           ENTRY_HISTORY_LIST_PAGE_ID
                           SEARCH_PAGE_ID
                           ICONS_LIST_PAGE_ID
                           SETTINGS_PAGE_ID
                           APP_SETTINGS_PAGE_ID
                           AUTOFILL_SETTINGS_PAGE_ID
                           KEY_FILE_FORM_PAGE_ID
                           CAMERA_SCANNER_PAGE_ID
                           ADDITIONAL_DATABASE_ACCESS_SETTINGS_PAGE_ID
                           APP_LOCK_SETTINGS_PAGE_ID
                           MANAGE_CUSTOM_ICONS_PAGE_ID
                           RS_CONNECTION_CONFIG_PAGE_ID
                           RS_FILES_FOLDERS_PAGE_ID
                           MERGE_DATABASE_PAGE_ID])

(defn- appbar-title [{:keys [page title]}]
  ;; title is required
  (cond
    (u/contains-val? title-provider-pages page)
    [positioned-title :title title]

    ;; Both page and title are required
    (= page ENTRY_LIST_PAGE_ID)
    [positioned-title :page page :title @(elist-events/current-page-title)  :titleStyle {:max-width "50%"}] ;;

    (= page ENTRY_CATEGORY_PAGE_ID)
    [positioned-title :page page :title @(cmn-events/current-database-name) :titleStyle {:max-width "50%"}]

    (= page GROUP_FORM_PAGE_ID)
    [positioned-title :page page :title title]

    ;; page id is required and title is provided by page specific app 'appbar-title' fn
    (or
     (u/contains-val? page-id-based-title-providers page)
     (is-settings-page page))
    [positioned-title :page page]))

;; All pages that has back action using default "<" button
(def back-button-pages [ABOUT_PAGE_ID
                        PRIVACY_POLICY_PAGE_ID
                        CAMERA_SCANNER_PAGE_ID
                        ENTRY_HISTORY_LIST_PAGE_ID
                        ICONS_LIST_PAGE_ID
                        SEARCH_PAGE_ID
                        SETTINGS_PAGE_ID
                        APP_SETTINGS_PAGE_ID
                        AUTOFILL_SETTINGS_PAGE_ID
                        ADDITIONAL_DATABASE_ACCESS_SETTINGS_PAGE_ID
                        APP_LOCK_SETTINGS_PAGE_ID
                        CAMERA_SCANNER_PAGE_ID
                        MANAGE_CUSTOM_ICONS_PAGE_ID
                        RS_CONNECTION_CONFIG_PAGE_ID
                        KEY_FILE_FORM_PAGE_ID
                        MERGE_DATABASE_PAGE_ID])

(defn appbar-header-content [page-info]
  (let [{:keys [page]} page-info]

    (reset! current-page-info page-info)

    ;; AppbarHeader contains three components : BackAction, AppbarContent(which has title), AppbarAction menus
    [rnp-appbar-header {:style {:backgroundColor @primary-color}}

     ;; Component for the back icon with onpress event handlers
     ;; Note: Some page provide its own back action handler
     (cond
       (= page ENTRY_LIST_PAGE_ID)
       [rnp-appbar-back-action {:style {}
                                :color @background-color
                                :onPress (fn [] (elist-events/entry-list-back-action))}]

       (= page RS_FILES_FOLDERS_PAGE_ID)
       [rnp-appbar-back-action {:color @background-color
                                :onPress rs-events/remote-storage-listing-previous}]

       (= page MERGE_DATABASE_PAGE_ID)
       [rnp-appbar-back-action {:color @background-color
                                :onPress merging-events/merge-database-back-action}]

       (u/contains-val? back-button-pages page)
       [rnp-appbar-back-action {:color @background-color
                                :onPress cmn-events/to-previous-page}])

     ;; Title component 
     (appbar-title page-info) ;; [appbar-title page-info] did not work. Why?

     ;; The right side action icons component (dots icon, search icon .. ) and are shown for certain pages only
     (when (or
            (= page HOME_PAGE_ID)
            (= page ENTRY_CATEGORY_PAGE_ID)
            (= page ENTRY_LIST_PAGE_ID)
            (and (= page ENTRY_FORM_PAGE_ID) (not @(ef-events/deleted-category-showing))) ;; Do not show in deleted entry form
            (= page ENTRY_HISTORY_LIST_PAGE_ID))
       [:<>
        [header-menu @header-menu-data page-info]
        (when-not (or (= page HOME_PAGE_ID) (= page ENTRY_FORM_PAGE_ID) (= page ENTRY_HISTORY_LIST_PAGE_ID))
          [rnp-appbar-action {:style {:backgroundColor @primary-color
                                      ;;:position "absolute" :right 50
                                      :margin-right -9}
                              :color @on-primary-color
                              :icon "magnify"
                              :onPress search-events/to-search-page}])
        [rnp-appbar-action {:style {:backgroundColor @primary-color
                                    ;;:position "absolute" :right 0 
                                    }
                            :color @on-primary-color
                            :icon dots-icon-name
                            :onPress #(header-menu-show %)}]])]))

(defn appbar-body-content
  "The page body content based on the page info set"
  [{:keys [page]}]
  (cond

    (= page HOME_PAGE_ID)
    [open-page-content]

    (= page ENTRY_CATEGORY_PAGE_ID)
    [entry-category-content]

    (= page ENTRY_LIST_PAGE_ID)
    [entry-list-content]

    (= page ENTRY_HISTORY_LIST_PAGE_ID)
    [entry-history-list/content]

    (= page GROUP_FORM_PAGE_ID)
    (group-form/content)

    (= page ENTRY_FORM_PAGE_ID)
    [entry-form/content]

    (= page SEARCH_PAGE_ID)
    [search/content]

    (= page PASSWORD_GENERATOR_PAGE_ID)
    [pg/content]

    (= page ICONS_LIST_PAGE_ID)
    [icons-list/content]

    (= page MANAGE_CUSTOM_ICONS_PAGE_ID)
    [manage-custom-icons/content]

    (= page SETTINGS_PAGE_ID)
    [settings/content]

    (= page APP_SETTINGS_PAGE_ID)
    [app-settings/content]

    (= page AUTOFILL_SETTINGS_PAGE_ID)
    [af-settings/content]

    (= page KEY_FILE_FORM_PAGE_ID)
    (kf-form/content)

    (u/contains-val? [SETTINGS_GENERAL_PAGE_ID SETTINGS_CREDENTIALS_PAGE_ID
                      SETTINGS_SECURITY_PAGE_ID SETTINGS_ENCRYPTION_PAGE_ID SETTINGS_KDF_PAGE_ID] page)
    (db-settings-form-content page)

    (= page ABOUT_PAGE_ID)
    [about-content]

    (= page PRIVACY_POLICY_PAGE_ID)
    [privacy-policy-content]

    (= page CAMERA_SCANNER_PAGE_ID)
    (scan-otp-qr/content)

    (= page RS_CONNECTIONS_LIST_PAGE_ID)
    [rs-configs/remote-connections-list-page-content]

    (= page RS_CONNECTION_CONFIG_PAGE_ID)
    [rs-form/connection-config-form]

    (= page RS_FILES_FOLDERS_PAGE_ID)
    [rs-files-folders/dir-entries-content]

    (= page ADDITIONAL_DATABASE_ACCESS_SETTINGS_PAGE_ID)
    [app-db-settings/content]

    (= page APP_LOCK_SETTINGS_PAGE_ID)
    [app-lock-settings/content]

    (= page MERGE_DATABASE_PAGE_ID)
    [merging/main-content]

    (= page PASSKEY_PENDING_REVIEW_PAGE_ID)
    [passkey-pending/content]

    ;; For now, this page is shown after loading the newly selected language translation
    ;; Other attempts to refresh the app settings page itself did not work
    (= page BLANK_PAGE_ID)
    (app-settings/language-update-feedback)

    ;; (= page CAMERA_SCANNER_PAGE_ID)
    ;; [totp/content]
    ))

(defn appbar-main-content
  "An App bar has both header and the body combined"
  []
  (let [handler-fns-m {:onStartShouldSetPanResponderCapture (fn []
                                                              (as-events/user-action-detected)
                                                              ;; Returns false so that the event is passed to other 
                                                              ;; listeners
                                                              false)}

        pan-handlers-m (-> (rnc/create-pan-responder handler-fns-m)
                           (js->clj :keywordize-keys true) :panHandlers)]
    [rn-view (merge {:style {:flex 1}} pan-handlers-m)
     (let [lock-state @(app-lock-events/app-lock-state)
           page-info @(cmn-events/page-info)]
       (if-not (= lock-state :locked)
         [rn-view {:style {:flex 1}}
          [appbar-header-content page-info]
          [appbar-body-content page-info]]
         [rn-view {:style {:flex 1}}
          [app-lock/content]
          #_[rnc/rnp-text "App locked"]]))]))