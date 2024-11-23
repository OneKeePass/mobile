(ns onekeepass.mobile.appbar
  (:require [onekeepass.mobile.about :as about :refer [about-content
                                                       privacy-policy-content]]
            [onekeepass.mobile.app-settings :as app-settings]
            [onekeepass.mobile.autofill :as af-settings]
            [onekeepass.mobile.common-components :as cc :refer [menu-action-factory]]
            [onekeepass.mobile.constants  :refer [AUTOFILL_SETTINGS_PAGE_ID
                                                  CAMERA_SCANNER_PAGE_ID
                                                  RS_CONNECTION_CONFIG_PAGE_ID
                                                  RS_CONNECTIONS_LIST_PAGE_ID]]
            [onekeepass.mobile.entry-category :refer [entry-category-content]]
            [onekeepass.mobile.entry-form :as entry-form]
            [onekeepass.mobile.entry-history-list :as entry-history-list]
            [onekeepass.mobile.entry-list :as entry-list :refer [entry-list-content]]
            [onekeepass.mobile.rs-configs :as remote-storage]
            [onekeepass.mobile.rs-config-form :as rs-form]
            [onekeepass.mobile.events.app-settings :as as-events]
            [onekeepass.mobile.events.common :as cmn-events]
            [onekeepass.mobile.events.entry-form :as ef-events]
            [onekeepass.mobile.events.entry-list :as elist-events]
            [onekeepass.mobile.events.password-generator :as pg-events]
            [onekeepass.mobile.events.search :as search-events]
            [onekeepass.mobile.events.settings :as stgs-events]
            [onekeepass.mobile.group-form :as group-form]
            [onekeepass.mobile.icons-list :as icons-list]
            [onekeepass.mobile.key-file-form :as kf-form]
            [onekeepass.mobile.password-generator :as pg]
            [onekeepass.mobile.rn-components :as rnc :refer [background-color
                                                             cust-rnp-divider
                                                             dots-icon-name
                                                             on-primary-color
                                                             primary-color
                                                             rnp-appbar-action
                                                             rnp-appbar-back-action
                                                             rnp-appbar-content
                                                             rnp-appbar-header
                                                             rnp-menu
                                                             rnp-menu-item]]
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
    (= page :home)
    false

    (= page :entry-list)
    (do
      (elist-events/entry-list-back-action)
      true)

    (or
     (= page :entry-category)
     (= page :entry-form)
     (= page :entry-history-list)
     (= page :icons-list)
     (= page :search)
     (= page :password-generator)
     (= page :group-form)
     (= page :settings)
     (= page :app-settings)
     (= page AUTOFILL_SETTINGS_PAGE_ID)
     (= page :key-file-form)
     (= page CAMERA_SCANNER_PAGE_ID)
     (= page :about)
     (= page :privacy-policy) 
     (= page RS_CONNECTION_CONFIG_PAGE_ID)
     (= page RS_CONNECTIONS_LIST_PAGE_ID))
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
  [rnp-menu {:visible show
             :onDismiss header-menu-hide
             :anchor (clj->js {:x x :y y})}
   (cond
     (= page :home)
     [:<>
      [rnp-menu-item {:title (lstr-ml "appSettings")
                      :onPress (header-menu-action as-events/to-app-settings-page)}]
      #_[cust-rnp-divider]
      [rnp-menu-item {:title (lstr-ml "about")
                      :onPress (header-menu-action cmn-events/to-about-page)}]
      [rnp-menu-item {:title (lstr-ml "privacyPolicy")
                      :onPress (header-menu-action cmn-events/to-privacy-policy-page)}]]

     (and (= page :entry-list) @(elist-events/deleted-category-showing))
     (let [items @(elist-events/selected-entry-items)]
       ;; items is all entry summary items found under 'Deleted' category and disable this if it is empty
       [rnp-menu-item {:title (lstr-ml "deleteAll")
                       :disabled (empty? items)
                       :onPress (header-menu-action
                                 entry-list/show-delete-all-entries-permanent-confirm-dialog items)}])

     (or (= page :entry-category) (= page :entry-list))
     [:<>
      [rnp-menu-item {:title (lstr-ml "home")
                      :onPress (header-menu-action cmn-events/to-home-page)}]
      [rnp-menu-item {:title (lstr-ml "pwdGenerator")
                      :onPress (header-menu-action pg-events/generate-password)}]
      [cust-rnp-divider]
      [rnp-menu-item {:title  (lstr-ml "lockdb")
                      :onPress (header-menu-action cmn-events/lock-kdbx nil)}]
      [cust-rnp-divider]
      [rnp-menu-item {:title (lstr-ml "closedb")
                      :onPress (header-menu-action cmn-events/close-current-kdbx-db)}]
      [cust-rnp-divider]
      [rnp-menu-item {:title (lstr-ml "settings")
                      :onPress (header-menu-action stgs-events/load-db-settings)}]]

     (= page :entry-history-list)
     [:<>
      [rnp-menu-item {:title (lstr-ml "deleteAll")
                      :onPress (header-menu-action ef-events/show-history-entry-delete-all-confirm-dialog)}]]

     (and (= page :entry-form) @(ef-events/history-entry-form?))
     [:<>
      [rnp-menu-item {:title (lstr-ml "restore")
                      :onPress (header-menu-action ef-events/show-history-entry-restore-confirm-dialog)}]
      [rnp-menu-item {:title (lstr-ml "delete")
                      :onPress (header-menu-action ef-events/show-history-entry-delete-confirm-dialog)}]]

     (= page :entry-form)
     (let [fav @(ef-events/favorites?)
           entry-uuid @(ef-events/entry-form-uuid)]
       [:<>
        [rnp-menu-item {:title (lstr-ml "favorite") :trailingIcon (if fav "check" nil)
                        :onPress (header-menu-action ef-events/favorite-menu-checked (not fav))}]
        [rnp-menu-item {:title "History"
                        :disabled (not @(ef-events/history-available))
                        :onPress (header-menu-action ef-events/load-history-entries-summary entry-uuid)}]
        ;; [cust-rnp-divider]
        ;; [rnp-menu-item {:title "Password Generator" :onPress #()}]
        [cust-rnp-divider]
        [rnp-menu-item {:title (lstr-ml "delete")
                        :onPress (header-menu-action cc/show-entry-delete-confirm-dialog entry-uuid)}]]))])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#_(def appbar-content-style {:padding-top 0
                             :alignItems "center"
                           ;;:color background-color
                             :backgroundColor @primary-color})

(defn is-settings-page [page]
  (u/contains-val? [:settings-general :settings-credentials :settings-security :settings-encryption :settings-kdf] page))

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

                                 (= page :entry-form)
                                 (r/as-element [entry-form/appbar-title])

                                 (= page :group-form)
                                 (r/as-element [group-form/appbar-title title])

                                 (= page :password-generator)
                                 (r/as-element [pg/appbar-title])

                                 (is-settings-page page)
                                 (r/as-element [settings/appbar-title page])

                                 (= page RS_CONNECTIONS_LIST_PAGE_ID)
                                 (r/as-element [remote-storage/appbar-title])

                                 ;;TODO 
                                 ;; Need to add translation of titles for Entry types and General cat types
                                 ;; Something similar one used in entry category page
                                 (= page :entry-list)
                                 title

                                 ;; No translation of text
                                 (= page :entry-category)
                                 title

                                 ;; Title for all other pages 
                                 ;; Here title is the key to which pageTitles prefix will be 
                                 ;; added and value is got from i18n map
                                 (string? title)
                                 (lstr-pt title)

                                 :else
                                 "No Title")}]])

(defn- appbar-title [{:keys [page title]}]
  ;; title is required
  (cond
    (or (= page :home)
        (= page :about)
        (= page :privacy-policy)
        (= page :entry-history-list)
        (= page :search)
        (= page :icons-list)
        (= page :settings)
        (= page :app-settings)
        (= page AUTOFILL_SETTINGS_PAGE_ID)
        (= page :key-file-form)
        (= page CAMERA_SCANNER_PAGE_ID)
        (= page RS_CONNECTION_CONFIG_PAGE_ID)
        )
    [positioned-title :title title]

    (= page :entry-list)
    [positioned-title :page page :title @(elist-events/current-page-title)  :titleStyle {:max-width "50%"}] ;;

    (= page :entry-category)
    [positioned-title :page page :title @(cmn-events/current-database-name) :titleStyle {:max-width "50%"}]

    (= page :group-form)
    [positioned-title :page page :title title]

    ;; page id is required
    (or
     (= page :entry-form)
     (is-settings-page page)
     (= page :password-generator)
     (= page RS_CONNECTIONS_LIST_PAGE_ID))
    [positioned-title :page page]))

(defn appbar-header-content [page-info] 
  (let [{:keys [page]} page-info]

    (reset! current-page-info page-info)

    ;; AppbarHeader contains three components : BackAction, AppbarContent(which has title), AppbarAction menus
    [rnp-appbar-header {:style {:backgroundColor @primary-color}}

     ;; Component for the back icon with onpress event handlers
     (cond
       (= page :entry-list)
       [rnp-appbar-back-action {:style {}
                                :color @background-color
                                :onPress (fn [] (elist-events/entry-list-back-action))}]

       (or
        (= page :about)
        (= page :privacy-policy)
        (= page :qr-scanner)
        (= page :entry-history-list)
        (= page :icons-list)
        (= page :search)
        (= page :settings)
        (= page :app-settings)
        (= page AUTOFILL_SETTINGS_PAGE_ID)
        (= page CAMERA_SCANNER_PAGE_ID)
        (= page RS_CONNECTION_CONFIG_PAGE_ID)
        (= page :key-file-form))
       [rnp-appbar-back-action {:color @background-color
                                :onPress cmn-events/to-previous-page}])

   ;; Title component 
     (appbar-title page-info) ;; [appbar-titlet page-info] did not work. Why?
     
   ;; The right side action icons component (dots icon, search icon .. ) and are shown for certain pages only
     (when (or
            (= page :home)
            (= page :entry-category)
            (= page :entry-list)
            (and (= page :entry-form) (not @(ef-events/deleted-category-showing))) ;; Do not show in deleted entry form 
            (= page :entry-history-list))
       [:<>
        [header-menu @header-menu-data page-info]
        (when-not (or (= page :home) (= page :entry-form) (= page :entry-history-list))
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

    (= page :home)
    [open-page-content]

    (= page :entry-category)
    [entry-category-content]

    (= page :entry-list)
    [entry-list-content]

    (= page :entry-history-list)
    [entry-history-list/content]

    (= page :group-form)
    (group-form/content)

    (= page :entry-form)
    [entry-form/content]

    (= page :search)
    [search/content]

    (= page :password-generator)
    [pg/content]

    (= page :icons-list)
    [icons-list/content]

    (= page :settings)
    [settings/content]

    (= page :app-settings)
    [app-settings/content]

    (= page AUTOFILL_SETTINGS_PAGE_ID)
    [af-settings/content]

    (= page :key-file-form)
    (kf-form/content)

    (u/contains-val? [:settings-general :settings-credentials
                      :settings-security :settings-encryption :settings-kdf] page)
    (db-settings-form-content page)

    (= page :about)
    [about-content]

    (= page :privacy-policy)
    [privacy-policy-content]

    (= page CAMERA_SCANNER_PAGE_ID)
    (scan-otp-qr/content)

    (= page RS_CONNECTIONS_LIST_PAGE_ID)
    [remote-storage/remote-connections-list-page-content]
    
    (= page RS_CONNECTION_CONFIG_PAGE_ID)
    [rs-form/connection-config-form]

    ;; For now, this page is shown after loading the newly selected language translation
    ;; Other attempts to refresh the app settings page itself did not work
    (= page :blank)
    (app-settings/language-update-feedback)


    ;; (= page :qr-scanner)
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
    [rnc/rn-view (merge {:style {:flex 1}} pan-handlers-m)
     [appbar-header-content @(cmn-events/page-info)]
     [appbar-body-content @(cmn-events/page-info)]]))