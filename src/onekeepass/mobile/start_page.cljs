(ns
 onekeepass.mobile.start-page
  (:require [reagent.core :as r]
            [onekeepass.mobile.rn-components
             :as rnc
             :refer [lstr
                     dots-icon-name
                     primary-color
                     primary-container-color
                     rn-keyboard
                     rn-view
                     rn-safe-area-view
                     rn-section-list
                     rnp-menu
                     rnp-menu-item
                     rnp-text
                     rnp-list-item
                     rnp-list-icon
                     rnp-icon-button
                     rnp-divider
                     rnp-text-input
                     rnp-helper-text
                     rnp-text-input-icon
                     rnp-portal
                     cust-dialog
                     rnp-dialog-title
                     rnp-dialog-content
                     rnp-dialog-actions
                     rnp-button
                     rnp-progress-bar]]
            [onekeepass.mobile.utils :as u]
            [onekeepass.mobile.constants :as const]
            [onekeepass.mobile.date-utils :refer [utc-to-local-datetime-str]]
            [onekeepass.mobile.common-components :as cc  :refer [menu-action-factory message-dialog]]
            [onekeepass.mobile.events.new-database :as ndb-events]
            [onekeepass.mobile.events.open-database :as opndb-events]
            [onekeepass.mobile.events.settings :as stgs-events]
            [onekeepass.mobile.events.common :as cmn-events]
            [onekeepass.mobile.events.exporting :as exp-events]))

;;(set! *warn-on-infer* true)

(defn new-db-dialog [{:keys [dialog-show
                             database-name
                             database-description
                             password
                             password-visible
                             _key-file-name
                             error-fields
                             status]}]
  (let [in-progress? (= :in-progress status)]
    [cust-dialog
     {:style {} :dismissable false :visible dialog-show :onDismiss #()}
     [rnp-dialog-title (lstr "dialog.titles.newDatabase")]
     [rnp-dialog-content
      [rn-view {:style {:flexDirection "column"  :justify-content "center"}}
       [rnp-text-input {:label (lstr "name")
                        ;;:value database-name
                        :defaultValue database-name
                        :autoComplete "off"
                        :onChangeText #(ndb-events/database-field-update :database-name %)}]
       (when (contains? error-fields :database-name)
         [rnp-helper-text {:type "error" :visible true}
          (:database-name error-fields)])

       [rnp-text-input {:style {:margin-top 10}
                        :label (lstr "description")
                        ;;:value database-description
                        :defaultValue database-description
                        :autoComplete "off"
                        :onChangeText #(ndb-events/database-field-update :database-description %)}]

       [rnp-divider {:style {:margin-top 10 :margin-bottom 10 :backgroundColor "grey"}}]

       [rnp-text-input {:style {}
                        :label (lstr "masterPassword")
                        ;;:value password
                        :defaultValue password
                        :autoComplete "off"
                        :secureTextEntry (not password-visible)
                        :right (r/as-element
                                [rnp-text-input-icon
                                 {:icon (if password-visible "eye" "eye-off")
                                  :onPress #(ndb-events/database-field-update
                                             :password-visible (not password-visible))}])
                        :onChangeText #(ndb-events/database-field-update :password %)}]
       (when (contains? error-fields :password)
         [rnp-helper-text {:type "error" :visible true}
          (:password error-fields)])

       ;; Key File use is not yet implemented
       #_[rnp-text-input {:style {:margin-top 10}
                          :label "Key File"
                          :value key-file-name
                          :placeholder "Optional key file"
                          :onChangeText #()}]]
      [rnp-progress-bar {:style {:margin-top 10} :visible in-progress?
                         :indeterminate true}]]
     [rnp-dialog-actions
      [rnp-button {:mode "text" :disabled in-progress?
                   :onPress  ndb-events/cancel-on-click}
       (lstr "button.labels.cancel")]
      [rnp-button {:mode "text" :disabled in-progress?
                   :onPress ndb-events/done-on-click}
       (lstr "button.labels.create")]]]))

;; open-db-dialog is called after user pick a database file open 
;; or the database is locked and user needs to use password and keyfile based authentication
(defn open-db-dialog
  ([{:keys [dialog-show
            database-file-name
            database-full-file-name
            password
            password-visible
            _key-file-name
            error-fields
            status]}]

   (let [locked? @(cmn-events/locked? database-full-file-name)
         in-progress? (= :in-progress status)
         dlg-title (if locked? (lstr "dialog.titles.unlockDatabase") (lstr "dialog.titles.openDatabase"))]
     [cust-dialog {:style {}
                   :visible dialog-show :onDismiss opndb-events/cancel-on-press}
      [rnp-dialog-title dlg-title]
      [rnp-dialog-content

       [rn-view {:style {:flexDirection "column"  :justify-content "center"}}
        [rnp-text-input {:label (lstr "databaseFile")
                         :value database-file-name
                         :editable false
                         :onChangeText #()}]
        [rnp-text-input {:style {:margin-top 10}
                         :label (lstr "masterPassword")
                        ;;:value password
                         :defaultValue password
                         :autoComplete "off"
                         :autoCorrect false
                         :secureTextEntry (not password-visible)
                         :right (r/as-element
                                 [rnp-text-input-icon
                                  {:icon (if password-visible "eye" "eye-off")
                                   :onPress #(opndb-events/database-field-update
                                              :password-visible (not password-visible))}])
                         :onChangeText #(opndb-events/database-field-update :password %)}]
        (when (contains? error-fields :password)
          [rnp-helper-text {:type "error" :visible (contains? error-fields :password)}
           (:password error-fields)])

        #_[rnp-text-input {:style {:margin-top 10}
                           :label "Key File"
                           :value key-file-name
                           :placeholder "Optional key file"
                           :right (r/as-element [rnp-text-input-icon {:icon "file"}])
                           :onChangeText #()}]]

       [rnp-progress-bar {:style {:margin-top 10} :visible in-progress? :indeterminate true}]]

      [rnp-dialog-actions
       [rnp-button {:mode "text" :disabled in-progress?
                    :onPress  opndb-events/cancel-on-press}
        (lstr "button.labels.cancel")]
       [rnp-button {:mode "text" :disabled in-progress?
                    :onPress (fn [] ^js/RNKeyboard (.dismiss rn-keyboard)
                               (if locked?
                                 (opndb-events/authenticate-with-credential)
                                 (opndb-events/open-database-read-db-file)))}
        (lstr "button.labels.continue")]]]))
  ([] [open-db-dialog @(opndb-events/dialog-data)]))

(defn file-info-dialog [{:keys [dialog-show file-size location last-modified]}]
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
                     :backgroundColor primary-container-color
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
   [rnp-menu-item {:title (lstr "menu.labels.settings")
                   :disabled (not opened)
                   :onPress (db-action-menu-action
                             stgs-events/load-db-settings-with-active-db
                             opened db-file-path)}]

   [rnp-menu-item {:title (lstr "menu.labels.fileinfo")
                   :onPress (db-action-menu-action
                             cmn-events/load-file-info
                             db-file-path)}]


   [rnp-menu-item {:title (lstr "menu.labels.exportTo")
                   :onPress (db-action-menu-action
                             exp-events/prepare-export-kdbx-data
                             db-file-path)}]
   [rnp-divider]
   (when opened
     (if locked
       [rnp-menu-item {:title  (lstr "menu.labels.unlockdb")
                       :onPress (db-action-menu-action
                                 opndb-events/unlock-selected-db
                                 file-name
                                 db-file-path)}]
       [rnp-menu-item {:title (lstr "menu.labels.lockdb")
                       :onPress (db-action-menu-action
                                 cmn-events/lock-kdbx
                                 db-file-path)}]))

   [rnp-menu-item {:title (lstr "menu.labels.closedb")
                   :disabled (not opened)
                   :onPress (db-action-menu-action
                             cmn-events/close-kdbx
                             db-file-path)}]
   [rnp-divider]
   [rnp-menu-item {:title (lstr "menu.labels.remove")
                   :onPress (fn []
                              (hide-db-action-menu)
                              (swap! remove-confirm-dialog-data assoc
                                     :title (str "Removing" " " file-name)
                                     :confirm-text (lstr "dialog.texts.remove")
                                     :call-on-ok-fn #(cmn-events/remove-from-recent-list
                                                      db-file-path))
                              (confirm-remove))}]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn message-repick-database-file-dialog [{:keys [dialog-show file-name]}]
  [cc/confirm-dialog  {:dialog-show dialog-show
                       :title "Pick database file"
                       :confirm-text (str "Please pick the database file " file-name)
                       :actions [{:label "Continue"
                                  :on-press (fn []
                                              (opndb-events/repick-confirm-close))}]}])

(defn icon-name-color [found locked]
  (cond
    locked
    [const/ICON-LOCKED-DATABASE  primary-color]  ;;"#477956" green tint

    found
    [const/ICON-DATABASE rnc/neutral-variant20-color]

    :else
    [const/ICON-DATABASE-OUTLINE rnc/neutral-variant60-color]))

(defn row-item-on-press [file-name db-file-path found locked]
  (cond
    locked
    (opndb-events/unlock-selected-db file-name db-file-path)

    :else
    (opndb-events/open-selected-database file-name db-file-path found)))

(defn row-item []
  (fn [{:keys [file-name db-file-path]} opened-databases-files]
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
  (fn [recent-uses]
    (let [opened-databases-files @(cmn-events/opened-database-file-names)
          sections  [{:title (lstr "databases")
                      :key "Databases"
                      ;; Recently used db info forms the data for this list
                      :data recent-uses}]]
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
    [rn-safe-area-view {:style {:flex 1}}
     [rn-view {:style {:flex 1 :justify-content "center" :align-items "center" :margin-top "10%"}}
      [rn-view {:style {:flex .1 :justify-content "center" :width "90%"}}
       [rnp-button {:mode "contained" :onPress ndb-events/new-database-dialog-show}
        (lstr "button.labels.newdb")]] ;;
      [rn-view {:style {:flex .1 :justify-content "center" :width "90%"}}
       [rnp-button {:mode "contained" :onPress #(opndb-events/open-database-on-press)}
        (lstr "button.labels.opendb")]]

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
      #_[open-db-dialog @(opndb-events/dialog-data)]
      [open-db-dialog]
      [file-info-dialog @(cmn-events/file-info-dialog-data)]
      [message-repick-database-file-dialog @(opndb-events/repick-confirm-data)]
      [message-dialog @(cmn-events/message-dialog-data)]]]))

;;;;;;;;;;;;;;;;;;;;;