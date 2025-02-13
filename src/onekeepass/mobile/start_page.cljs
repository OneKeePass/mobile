(ns
 onekeepass.mobile.start-page
  (:require [onekeepass.mobile.background :refer [is-iOS]]
            [onekeepass.mobile.common-components :as cc  :refer [menu-action-factory
                                                                 message-dialog confirm-dialog-with-lstr]]
            [onekeepass.mobile.constants :as const]
            [onekeepass.mobile.date-utils :refer [utc-to-local-datetime-str]]
            [onekeepass.mobile.events.common :as cmn-events]
            [onekeepass.mobile.events.exporting :as exp-events]
            [onekeepass.mobile.events.new-database :as ndb-events]
            [onekeepass.mobile.events.open-database :as opndb-events]
            [onekeepass.mobile.events.settings :as stgs-events]
            [onekeepass.mobile.events.dialogs :as dlg-events]
            [onekeepass.mobile.events.remote-storage :as rs-events :refer [BROWSE-TYPE-DB-NEW BROWSE-TYPE-DB-OPEN]]
            [onekeepass.mobile.rn-components
             :as rnc
             :refer [cust-dialog cust-rnp-divider divider-color-1
                     dots-icon-name primary-color primary-container-color
                     rn-keyboard rn-safe-area-view rn-section-list rn-view
                     rnp-button rnp-dialog-actions rnp-dialog-content
                     rnp-dialog-title rnp-divider rnp-helper-text
                     rnp-icon-button rnp-list-icon rnp-list-item rnp-menu
                     rnp-menu-item rnp-portal rnp-progress-bar rnp-text
                     rnp-text-input rnp-text-input-icon]]
            [onekeepass.mobile.translation :refer [lstr-bl lstr-l lstr-dlg-text
                                                   lstr-dlg-title lstr-ml]]
            [onekeepass.mobile.utils :as u]
            [reagent.core :as r]))

;;(set! *warn-on-infer* true)

;; For now we will use a simple dialog using generic confirm type dialog
;; Called to create a dialog and the dialog is shown if the 'show' is true in 
;; the dialog data
(defn start-page-storage-selection-dialog []
  [confirm-dialog-with-lstr @(dlg-events/start-page-storage-selection-dialog-data)])

;; 
(defn start-page-storage-selection-dialog-show
  "Called to show the dialog showing storage locations
   The arg 'kw-browse-type' determines we are showing the dialog during open database or new database time
   We pass additional new db data in the arg 'opts-m'  
   "
  [kw-browse-type & {:as opts-m}]
  ;; We pass translation keys for title, confirm-text and for button labels
  (dlg-events/start-page-storage-selection-dialog-show-with-state
   {:title "dbStorage"
    :confirm-text "dbStorage"
    :show-action-as-vertical true
    :actions [{:label "localDevice"
               :on-press (fn []
                           (if (= BROWSE-TYPE-DB-OPEN kw-browse-type)
                             (opndb-events/open-database-on-press)
                             (ndb-events/done-on-click))
                           (dlg-events/start-page-storage-selection-dialog-close))}
              {:label "sftp"
               :on-press (fn []
                           (rs-events/remote-storage-type-selected :sftp kw-browse-type opts-m)
                           (dlg-events/start-page-storage-selection-dialog-close))}
              {:label "webdav"
               :on-press (fn []
                           (rs-events/remote-storage-type-selected :webdav kw-browse-type opts-m)
                           (dlg-events/start-page-storage-selection-dialog-close))}
              {:label "cancel"
               :on-press dlg-events/start-page-storage-selection-dialog-close}]}))

;;;;;;;;;;;;;;

(defn new-db-dialog [{:keys [dialog-show
                             database-name
                             database-description
                             password
                             password-visible
                             key-file-name-part
                             error-fields
                             status]}]
  (let [in-progress? (= :in-progress status)]
    [cust-dialog
     {:style {} :dismissable false :visible dialog-show :onDismiss #()}
     [rnp-dialog-title (lstr-dlg-title "newDatabase")]
     [rnp-dialog-content
      [rn-view {:style {:flexDirection "column"  :justify-content "center"}}
       [rnp-text-input {:label (lstr-l "name")
                        ;;:value database-name
                        :defaultValue database-name
                        :autoCapitalize "none" ;; this starts with the lowercase keyboard 
                        :autoComplete "off"
                        :onChangeText #(ndb-events/database-field-update :database-name %)}]
       (when (contains? error-fields :database-name)
         [rnp-helper-text {:type "error" :visible true}
          (:database-name error-fields)])

       [rnp-text-input {:style {:margin-top 10}
                        :label (lstr-l "description")
                        ;;:value database-description
                        :defaultValue database-description
                        :autoComplete "off"
                        :onChangeText #(ndb-events/database-field-update :database-description %)}]

       [rnp-divider {:style {:margin-top 10 :margin-bottom 10 :backgroundColor "grey"}}]

       [rnp-text-input {:style {}
                        :label (lstr-l "masterPassword")
                        ;;:value password
                        :defaultValue password
                        :autoCapitalize "none"
                        :autoComplete "off"
                        :secureTextEntry (not password-visible)
                        :right (r/as-element
                                [rnp-text-input-icon
                                 {:icon (if password-visible "eye" "eye-off")
                                  :onPress #(ndb-events/database-field-update
                                             :password-visible (not password-visible))}])
                        :onChangeText (fn [v]
                                        ;; After entering some charaters and delete is used to remove those charaters
                                        ;; password will have a string value "" resulting in a non visible password. Need to use nil instead
                                        (ndb-events/database-field-update :password (if (empty? v) nil v)))}]
       (when (contains? error-fields :password)
         [rnp-helper-text {:type "error" :visible true}
          (:password error-fields)])

       [rnp-divider {:style {:margin-top 10 :margin-bottom 10 :backgroundColor "grey"}}]

       (if key-file-name-part
         [rnp-text-input {:style {:margin-top 10}
                          :label (lstr-l "keyFile")
                          :defaultValue key-file-name-part
                          :readOnly (if (is-iOS) true false)
                          :onPressIn #(ndb-events/show-key-file-form true)
                          :right (r/as-element [rnp-text-input-icon
                                                {:icon const/ICON-CLOSE
                                                 :onPress (fn []
                                                            (ndb-events/database-field-update :key-file-name-part nil)
                                                            (ndb-events/database-field-update :key-file-name nil))}])
                          :onChangeText nil}]
         [rnp-text {:style {:margin-top 15
                            :textDecorationLine "underline"
                            :text-align "center"}
                    :onPress #(ndb-events/show-key-file-form true)} (lstr-l "additionalProtection")])]

      [rnp-progress-bar {:style {:margin-top 10} :visible in-progress?
                         :indeterminate true}]]
     [rnp-dialog-actions
      [rnp-button {:mode "text" :disabled in-progress?
                   :onPress  ndb-events/cancel-on-click}
       (lstr-bl "cancel")]
      [rnp-button {:mode "text" :disabled in-progress?
                   :onPress (fn [] (start-page-storage-selection-dialog-show BROWSE-TYPE-DB-NEW :new-db-data {:database-name database-name}))}
       (lstr-bl "create")]]]))

;; open-db-dialog is called after user pick a database file open 
;; or the database is locked and user needs to use password and keyfile based authentication

(defn- open-db-dialog-1  [{:keys [dialog-show
                                  database-file-name
                                  database-full-file-name
                                  password
                                  password-visible
                                  key-file-name-part
                                  error-fields
                                  status]}]

  (let [locked? @(cmn-events/locked? database-full-file-name)
        in-progress? (= :in-progress status)
        dlg-title (if locked? (lstr-dlg-title "unlockDatabase") (lstr-dlg-title "openDatabase"))]
    [cust-dialog {:style {}
                  :visible dialog-show
                  :dismissable false
                   ;;:onDismiss opndb-events/cancel-on-press
                  }
     [rnp-dialog-title dlg-title]
     [rnp-dialog-content

      [rn-view {:style {:flexDirection "column"  :justify-content "center"}}
       [rnp-text-input {:label (lstr-l "databaseFile")
                        :value database-file-name
                        :editable false
                        :onChangeText #()}]
       [rnp-text-input {:style {:margin-top 10}
                        :label (lstr-l "masterPassword")
                        ;;:value password
                        :defaultValue password
                        :autoComplete "off"
                        :autoCapitalize "none"
                        :autoCorrect false
                        :secureTextEntry (not password-visible)
                        :right (r/as-element
                                [rnp-text-input-icon
                                 {:icon (if password-visible "eye" "eye-off")
                                  :onPress #(opndb-events/database-field-update
                                             :password-visible (not password-visible))}])
                        :onChangeText (fn [v]
                                        ;; After entering some charaters and delete is used to remove those charaters
                                        ;; password will have a string value "" resulting in a non visible password. Need to use nil instead
                                        (opndb-events/database-field-update :password (if (empty? v) nil v)))}]
       (when (contains? error-fields :password)
         [rnp-helper-text {:type "error" :visible (contains? error-fields :password)}
          (:password error-fields)])

       [rnp-divider {:style {:margin-top 10 :margin-bottom 10 :backgroundColor "grey"}}]

       (if  key-file-name-part
         [rnp-text-input {:style {:margin-top 10}
                          :label (lstr-l 'keyFile)
                          :defaultValue key-file-name-part
                          :readOnly (if (is-iOS) true false)
                          :onPressIn #(opndb-events/show-key-file-form)
                          :onChangeText nil
                          :right (r/as-element [rnp-text-input-icon
                                                {:icon const/ICON-CLOSE
                                                 :onPress (fn []
                                                            (opndb-events/database-field-update :key-file-name-part nil)
                                                            (opndb-events/database-field-update :key-file-name nil))}])}]
         [rnp-text {:style {:margin-top 15
                            :textDecorationLine "underline"
                            :text-align "center"}
                    :onPress #(opndb-events/show-key-file-form)} (lstr-l 'keyFile)])]

      [rnp-progress-bar {:style {:margin-top 10} :visible in-progress? :indeterminate true}]]

     [rnp-dialog-actions
      [rnp-button {:mode "text" :disabled in-progress?
                   :onPress  opndb-events/cancel-on-press}
       (lstr-bl "cancel")]
      [rnp-button {:mode "text" :disabled in-progress?
                   :onPress (fn [] ^js/RNKeyboard (.dismiss rn-keyboard)
                              (if locked?
                                (opndb-events/authenticate-with-credential-to-unlock)
                                (opndb-events/open-database-read-db-file)))}
       (lstr-bl "continue")]]]))

(defn open-db-dialog
  ([data]
   (open-db-dialog-1 data))
  ([]
   (open-db-dialog @(opndb-events/dialog-data))))

(defn file-info-dialog [{:keys [dialog-show file-size location last-modified] :as _data}]
  [cust-dialog {:style {}
                :visible dialog-show :onDismiss #(cmn-events/close-file-info-dialog)}
   [rnp-dialog-title "File Info"]
   [rnp-dialog-content
    [rn-view {:style {:flexDirection "column"  :justify-content "center"}}
     [rn-view {:style {:justify-content "space-between"} :flexDirection "row"}
      [rnp-text "File Size"]
      [rnp-text (str file-size " " "bytes")]]
     [rn-view {:style {:height 10}}]
     [rnp-divider]
     [rn-view {:style {:height 10}}]
     [rn-view {:style {:justify-content "space-between"} :flexDirection "row"}
      [rnp-text "Location"]
      [rnp-text location]]
     [rn-view {:style {:height 10}}]
     [rnp-divider]
     [rn-view {:style {:height 10}}]
     [rn-view {:style {:justify-content "space-between"} :flexDirection "row"}
      [rnp-text "Last Modified"]
      [rnp-text (utc-to-local-datetime-str last-modified "LLL dd,yyyy hh:mm:ss aaa")]]
     [rn-view {:style {:height 10}}]
     [rnp-divider]]]
   [rnp-dialog-actions
    [rnp-button {:mode "text" :onPress  #(cmn-events/close-file-info-dialog)} "Close"]]])

(defn databases-list-header [title]
  [rn-view  {:style {:flexDirection "row"
                     :width "100%"
                     :backgroundColor @primary-container-color
                     :justify-content "space-around"
                     :margin-top 5
                     :min-height 38}}
   [rnp-text {:style {:alignSelf "center"
                      :width "85%"
                      :text-align "center"
                      :padding-left 0} :variant "titleLarge"} title]])

;;;;;;;;;;;; Menus ;;;;;;;;;;;;;;;;;;;;;
(def ^:private db-action-menu-data (r/atom {:show false :x 0 :y 0
                                            :db-file-path nil
                                            :file-name nil
                                            :opened false
                                            :locked false}))

(defn hide-db-action-menu []
  (swap! db-action-menu-data assoc :show false))

(defn show-db-action-menu [^js/PEvent event file-name db-file-path opened? locked?]
  (swap! db-action-menu-data assoc :show true
         :db-file-path db-file-path
         :file-name file-name
         :opened opened?
         :locked locked?
         :x (-> event .-nativeEvent .-pageX) :y (-> event .-nativeEvent .-pageY)))

(def db-action-menu-action (menu-action-factory hide-db-action-menu))

(def remove-confirm-dialog-data (r/atom {:dialog-show false
                                         :title nil
                                         :confirm-text nil
                                         :call-on-ok-fn #(println %)}))

(def remove-confirm-dialog-info (cc/confirm-dialog-factory remove-confirm-dialog-data))

(def confirm-remove (:show remove-confirm-dialog-info))

(defn db-action-menu [{:keys [show x y file-name db-file-path opened locked]}]
  ;; db-file-path is the full-file-name-uri and used as db-key
  [rnp-menu {:visible show :onDismiss hide-db-action-menu :anchor (clj->js {:x x :y y})}
   [rnp-menu-item {:title (lstr-ml "settings")
                   :disabled (or (not opened) locked)
                   :onPress (db-action-menu-action
                             stgs-events/load-db-settings-with-active-db
                             opened db-file-path)}]

   [rnp-menu-item {:title (lstr-ml "fileinfo")
                   :onPress (db-action-menu-action
                             cmn-events/load-file-info
                             db-file-path)}]


   [rnp-menu-item {:title (lstr-ml "exportTo")
                   :onPress (db-action-menu-action
                             exp-events/prepare-export-kdbx-data
                             db-file-path)}]
   ;; Dividers used in menu has a preset background-color
   [cust-rnp-divider]
   (when opened
     (if locked
       [rnp-menu-item {:title  (lstr-ml "unlockdb")
                       :onPress (db-action-menu-action
                                 opndb-events/unlock-selected-db
                                 file-name
                                 db-file-path)}]
       [rnp-menu-item {:title (lstr-ml "lockdb")
                       :onPress (db-action-menu-action
                                 cmn-events/lock-kdbx
                                 db-file-path)}]))

   [rnp-menu-item {:title (lstr-ml "closedb")
                   :disabled (not opened)
                   :onPress (db-action-menu-action
                             cmn-events/close-kdbx
                             db-file-path)}]
   ;; Another way of setting the background-color of dividers in menu
   [rnp-divider {:style {:background-color @divider-color-1}}]
   [rnp-menu-item {:title (lstr-ml "remove")
                   :onPress (fn []
                              (hide-db-action-menu)
                              (swap! remove-confirm-dialog-data assoc
                                     :title (lstr-dlg-title 'removing {:dbFileName file-name})
                                     :confirm-text (lstr-dlg-text "remove")
                                     :call-on-ok-fn #(cmn-events/remove-from-recent-list
                                                      db-file-path))
                              (confirm-remove))}]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn message-repick-database-file-dialog [{:keys [dialog-show file-name reason-code]}]
  ;; reason-code may be 'PERMISSION_REQUIRED_TO_READ' or 'FILE_NOT_FOUND'
  ;; See dispatch event ':repick-confirm-show'
  (let [[title text] (if (= reason-code const/PERMISSION_REQUIRED_TO_READ)
                       [(str (lstr-dlg-title 'reopen) " " file-name),
                        (str (lstr-dlg-text 'reopenReadPermissionrequired) ". " (lstr-dlg-text 'reopenAgain {:file-name file-name}))]
                       ;; The else condition is :const/FILE_NOT_FOUND
                       ;; in iOS, seen this happening when we try to open (pressing on the home page dabase list) a database 
                       ;; file stored in iCloud and file is not synched to the mobile yet  
                       [(lstr-dlg-title 'reopen)
                        (str (lstr-dlg-text 'reopenNotOldRef) ". " (lstr-dlg-text 'reopenAgain {:file-name file-name}))])]
    [cc/confirm-dialog  {:dialog-show dialog-show
                         :title title
                         :confirm-text text
                         :actions [{:label (lstr-bl "cancel")
                                    :on-press #(opndb-events/repick-confirm-cancel)}
                                   {:label (lstr-bl "continue")
                                    :on-press (fn []
                                                (opndb-events/repick-confirm-close))}]}]))

(defn authenticate-biometric-confirm-dialog [{:keys [dialog-show]}]
  [cc/confirm-dialog  {:dialog-show dialog-show
                       :title (lstr-dlg-title  'biometricConfirm)
                       :confirm-text (if (is-iOS)
                                       (lstr-dlg-text 'biometricConfirmTxt1)
                                       (lstr-dlg-text 'biometricConfirmTxt2))
                       :actions [{:label "Cancel"
                                  :on-press (fn []
                                              (opndb-events/authenticate-biometric-cancel))}
                                 {:label "Continue"
                                  :on-press (fn []
                                              (opndb-events/authenticate-biometric-ok))}]}])

(defn icon-name-color [found locked]
  (cond
    locked
    [const/ICON-LOCKED-DATABASE  @primary-color]  ;;"#477956" green tint

    found
    [const/ICON-DATABASE-EYE @rnc/tertiary-color]

    :else
    [const/ICON-DATABASE-OUTLINE @rnc/secondary-color]))

(defn row-item-on-press [file-name db-file-path found locked]
  (cond
    locked
    (opndb-events/unlock-selected-db file-name db-file-path)

    :else
    (opndb-events/open-selected-database file-name db-file-path found)))

(defn row-item
  "The first arg is map from recently-used and the second arg is a vec of db keys of the opened databases 
   Returns a row item component"
  []
  (fn [{:keys [file-name db-file-path] :as _recently-used} opened-databases-files]
    (let [found (u/contains-val? opened-databases-files db-file-path)
          locked? @(cmn-events/locked? db-file-path)
          [icon-name color] (icon-name-color found locked?)]
      [rnp-list-item {:style {}
                      :onPress #(row-item-on-press file-name db-file-path found locked?)
                      :title (r/as-element
                              [rnp-text {:style {:color color #_(if found primary-color rnc/outline-color)}
                                         :variant (if found "titleMedium" "titleSmall")} file-name])
                      :left (fn [_props]
                              (r/as-element
                               [rnp-list-icon {:style {:height 24}
                                               :icon icon-name
                                               :color color}]))
                      :right (fn [_props] (r/as-element
                                           [:<> ;; We can add another icon here if required
                                            [rnp-icon-button
                                             {:size 24
                                              :style {:margin -10}
                                              :icon dots-icon-name
                                              :onPress  (fn [e] (show-db-action-menu
                                                                 e file-name db-file-path found locked?))}]]))}])))

(defn databases-list-content []
  (fn [recently-used]
    (let [opened-databases-files @(cmn-events/opened-database-file-names)
          sections  [{:title (lstr-l "databases")
                      :key "Databases"
                      ;; Recently used db info forms the data for this list
                      :data recently-used}]]
      [rn-section-list
       {:style {}
        :sections (clj->js sections)
        :renderItem  (fn [props] ;; keys are (:item :index :section :separators)
                       (let [props (js->clj props :keywordize-keys true)]
                         (r/as-element [row-item (-> props :item) opened-databases-files])))
        :ItemSeparatorComponent (fn [_p]
                                  (r/as-element [rnp-divider]))
        :renderSectionHeader (fn [props] ;; key is :section
                               (let [props (js->clj props :keywordize-keys true)
                                     {:keys [title]} (-> props :section)]
                                 (r/as-element [databases-list-header title])))}])))

(defn open-page-content []
  (let [recent-uses @(cmn-events/recently-used)]
    [rn-safe-area-view {:style {:flex 1 :background-color @rnc/page-background-color}}
     [rn-view {:style {:flex 1 :justify-content "center" :align-items "center" :margin-top "10%"}}
      [rn-view {:style {:flex .1 :justify-content "center" :width "90%"}}
       [rnp-button {:mode "contained" :onPress (fn [] (ndb-events/new-database-dialog-show))}
        (lstr-bl "newdb")]] ;;
      [rn-view {:style {:flex .1 :justify-content "center" :width "90%"}}
       [rnp-button {:mode "contained"
                    :onPress (fn [] (start-page-storage-selection-dialog-show BROWSE-TYPE-DB-OPEN))}
        (lstr-bl "opendb")]]

      [rn-view {:style {:margin-top 20}}
       [rnc/rnp-divider]]

      [rn-view {:style {:flex 1 :width "100%"}}
       [databases-list-content recent-uses]]]

     ;; This absolutely position view works in both android and iOS
     ;; And then may be used for bottom icons panel
     #_[rn-view {:style {:width "100%" :height 60 :backgroundColor "red" :position "absolute" :bottom 0}}
        [rnp-text {:variant "titleMedium"} "Some icons here"]]

     [rnp-portal
      [db-action-menu @db-action-menu-data]
      ;; Gets the precreated dialog reagent component
      (:dialog remove-confirm-dialog-info)
      [new-db-dialog @(ndb-events/dialog-data)]
      [open-db-dialog @(opndb-events/dialog-data)]
      [start-page-storage-selection-dialog]
      [file-info-dialog @(cmn-events/file-info-dialog-data)]
      [message-repick-database-file-dialog @(opndb-events/repick-confirm-data)]
      #_[authenticate-biometric-confirm-dialog @(opndb-events/authenticate-biometric-confirm-dialog-data)]
      [message-dialog @(cmn-events/message-dialog-data)]]]))