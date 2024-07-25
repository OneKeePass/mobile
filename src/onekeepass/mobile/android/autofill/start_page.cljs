(ns
 onekeepass.mobile.android.autofill.start-page
  (:require [onekeepass.mobile.background :refer [is-iOS]]
            [onekeepass.mobile.common-components :as cc
             :refer [message-dialog message-snackbar]]
            [onekeepass.mobile.constants :as const]
            [onekeepass.mobile.events.common :as cmn-events]
            [onekeepass.mobile.events.open-database :as opndb-events]
            [onekeepass.mobile.android.autofill.events.common :as android-af-cmn-events]
            [onekeepass.mobile.rn-components
             :as rnc
             :refer [appbar-text-color cust-dialog dots-icon-name
                     page-title-text-variant primary-color
                     primary-container-color rn-keyboard rn-safe-area-view
                     rn-section-list rn-view rnp-button rnp-dialog-actions
                     rnp-dialog-content rnp-dialog-title rnp-divider
                     rnp-helper-text rnp-icon-button rnp-list-icon
                     rnp-list-item rnp-portal rnp-progress-bar rnp-text
                     rnp-text-input rnp-text-input-icon]]
            [onekeepass.mobile.translation :refer [lstr-bl lstr-dlg-text
                                                   lstr-dlg-title lstr-l
                                                   lstr-pt]]
            [onekeepass.mobile.utils :as u]
            [reagent.core :as r]))


(defn open-db-dialog  [{:keys [dialog-show
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
                                  :onPress #(android-af-cmn-events/database-field-update
                                             :password-visible (not password-visible))}])
                        :onChangeText (fn [v]
                                                 ;; After entering some charaters and delete is used to remove those charaters
                                                 ;; password will have a string value "" resulting in a non visible password. Need to use nil instead
                                        (android-af-cmn-events/database-field-update :password (if (empty? v) nil v)))}]
       (when (contains? error-fields :password)
         [rnp-helper-text {:type "error" :visible (contains? error-fields :password)}
          (:password error-fields)])

       [rnp-divider {:style {:margin-top 10 :margin-bottom 10 :backgroundColor "grey"}}]

       (if  key-file-name-part
         [rnp-text-input {:style {:margin-top 10}
                          :label (lstr-l 'keyFile)
                          :defaultValue key-file-name-part
                          :readOnly (if (is-iOS) true false)
                          :onPressIn #(android-af-cmn-events/show-key-file-form)
                          :onChangeText nil
                          :right (r/as-element [rnp-text-input-icon
                                                {:icon const/ICON-CLOSE
                                                 :onPress (fn []
                                                            (android-af-cmn-events/database-field-update :key-file-name-part nil)
                                                            (android-af-cmn-events/database-field-update :key-file-name nil))}])}]
         [rnp-text {:style {:margin-top 15
                            :textDecorationLine "underline"
                            :text-align "center"}
                    :onPress #(android-af-cmn-events/show-key-file-form)} (lstr-l 'keyFile)])]

      [rnp-progress-bar {:style {:margin-top 10} :visible in-progress? :indeterminate true}]]

     [rnp-dialog-actions
      [rnp-button {:mode "text" :disabled in-progress?
                   :onPress  android-af-cmn-events/cancel-on-press}
       (lstr-bl "cancel")]
      [rnp-button {:mode "text" :disabled in-progress?
                   :onPress (fn [] ^js/RNKeyboard (.dismiss rn-keyboard)
                              (println "will call (android-af-cmn-events/open-database-read-db-file)")
                              (android-af-cmn-events/open-database-read-db-file)
                              #_(if locked?
                                  (opndb-events/authenticate-with-credential)
                                  (android-af-cmn-events/open-database-read-db-file)))}
       (lstr-bl "continue")]]]))


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

(defn message-repick-database-file-dialog [{:keys [dialog-show file-name reason-code]}]
  (let [[title text] (if (= reason-code const/PERMISSION_REQUIRED_TO_READ)
                       [(str (lstr-dlg-title 'reopen) " " file-name),
                        (str (lstr-dlg-text 'reopenReadPermissionrequired) ". " (lstr-dlg-text 'reopenAgain {:file-name file-name}))]
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
  (android-af-cmn-events/open-selected-database file-name db-file-path found)
  #_(cond
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
                                              :onPress  (fn [e] #_(show-db-action-menu
                                                                   e file-name db-file-path found locked?))}]]))}])))

(defn databases-list-content []
  (fn [recent-uses]
    (let [opened-databases-files @(android-af-cmn-events/opened-database-file-names)
          sections  [{:title (lstr-l "databases")
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

(defn home-page []
  [rn-view {:style {:flex 1 :width "100%"}}
   [rn-view {:style {:flex 1 :justify-content "center" :align-items "center" :margin-top "10%"}}
    [rn-view {:style {:flex .1 :justify-content "center" :width "90%"}}
     [rnp-button {:mode "contained" :onPress #(android-af-cmn-events/open-database-on-press)}
      (lstr-bl "opendb")]]

    [rn-view {:style {:margin-top 20}}
     [rnc/rnp-divider]]

    [rn-view {:style {:flex 1 :width "100%"}} 
     [databases-list-content @(cmn-events/recently-used)]]]
   #_[rn-view {:style {:flex 1}}
      [databases-list-content @(cmn-events/recently-used)]]])

#_(defn home-page []
    [rnc/rn-keyboard-avoiding-view {:style {:flex 1}
                                    :behavior "height"}
     [rn-view {:style {:flex 1 :width "100%"}}
      [rn-view {:style {:flex 1 :justify-content "center" :align-items "center" :margin-top "10%"}}
       [rn-view {:style {:flex .1 :justify-content "center" :width "90%"}}
        [rnp-button {:mode "contained" :onPress #(opndb-events/open-database-on-press)}
         (lstr-bl "opendb")]]

       [rn-view {:style {:margin-top 20}}
        [rnc/rnp-divider]]

       [rn-view {:style {:flex 1 :width "100%"}}
        [databases-list-content @(cmn-events/recently-used)]]]
      #_[rn-view {:style {:flex 1}}
         [databases-list-content @(cmn-events/recently-used)]]]])

(defn open-page-content []
  [rn-safe-area-view {:style {:flex 1 :background-color @rnc/page-background-color}} 
   (let [{:keys [page]} {} #_@(cmn-events/page-info)]
     [rn-view {:style {:flex 1 :width "100%"}}
      #_[top-bar page]
      #_(when (= cmn-events/ENTRY_LIST_PAGE_ID page)
          [searchbar])

      [rn-view {:style {:justify-content "center"
                        :align-items "center"
                        :flex 1}}

       [home-page]

       #_(condp = page
           cmn-events/HOME_PAGE_ID
           [home-page]

           cmn-events/LOGIN_PAGE_ID
           [login-page]

           cmn-events/ENTRY_LIST_PAGE_ID
           [show-all-entries]

           cmn-events/ENTRY_FORM_PAGE_ID
           [show-form]

           :else
           [home-page])]])

   [rnp-portal
    [message-dialog @(cmn-events/message-dialog-data)]
    [open-db-dialog @(android-af-cmn-events/dialog-data)]
    #_[message-repick-database-file-dialog @(opndb-events/repick-confirm-data)]
    #_[authenticate-biometric-confirm-dialog @(opndb-events/authenticate-biometric-confirm-dialog-data)]
    [message-snackbar]]])


#_(defn open-page-content []
    (let [recent-uses @(cmn-events/recently-used)]
      [rn-safe-area-view {:style {:flex 1 :background-color  @rnc/page-background-color}}
       [rn-view {:style {:flex 1 :justify-content "center" :align-items "center" :margin-top "10%"}}
        [rn-view {:style {:flex .1 :justify-content "center" :width "90%"}}
         [rnp-button {:mode "contained" :onPress #(opndb-events/open-database-on-press)}
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
        #_[db-action-menu @db-action-menu-data]
      ;; Gets the precreated dialog reagent component
        #_(:dialog remove-confirm-dialog-info)
        #_[new-db-dialog @(ndb-events/dialog-data)]
        [open-db-dialog @(opndb-events/dialog-data)]
        #_[file-info-dialog @(cmn-events/file-info-dialog-data)]
        [message-repick-database-file-dialog @(opndb-events/repick-confirm-data)]
        [authenticate-biometric-confirm-dialog @(opndb-events/authenticate-biometric-confirm-dialog-data)]
        [message-dialog @(cmn-events/message-dialog-data)]]]))


#_(defn top-bar-left-action [page]
    {:action #()
     :label (lstr-bl 'cancel)
     :title (lstr-pt 'autoFillPassword)}
    #_(if (= page cmn-events/ENTRY_FORM_PAGE_ID)
        {:action form-events/cancel-entry-form
         :label (lstr-bl 'back)
         :title (lstr-pt 'entry)}
        {:action cmn-events/cancel-extension
         :label (lstr-bl 'cancel)
         :title (lstr-pt 'autoFillPassword)}))

#_(defn top-bar [page]
    (let [{:keys [action label title]} (top-bar-left-action page)]
      [rn-view {:style {:flex .1
                        :justify-content "center"
                        :align-items "center"
                        :background-color @primary-color}}

       [rn-view {:style {:flexDirection "row"
                         :alignItems "center"
                         :width "100%"
                         :justify-content "space-between"}}
        [rnp-button {:style {}
                     :textColor @appbar-text-color
                     :mode "text"
                     :onPress action} label]

        [rnp-text {:style {:color @appbar-text-color
                           :max-width 200
                             ;; :margin-right 20 
                             ;; :margin-left 20
                           }
                   :ellipsizeMode "tail"
                   :numberOfLines 1
                   :variant page-title-text-variant} title]
        [rnp-button {:style {}
                     :textColor @appbar-text-color
                     :mode "text"
                     :onPress #()} ""]]]))
