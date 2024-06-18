(ns onekeepass.ios.autofill.start-page
  (:require [onekeepass.ios.autofill.constants :as const]
            [onekeepass.ios.autofill.events.common :as cmn-events]
            [onekeepass.ios.autofill.rn-components :as rnc
             :refer [dots-icon-name primary-color primary-container-color
                     rn-safe-area-view rn-section-list rn-view rnp-button
                     rnp-divider rnp-icon-button rnp-list-icon rnp-list-item
                     rnp-text-input-icon
                     rn-keyboard
                     rnp-text rnp-text-input]]
            [reagent.core :as r]))


(defn open-db-page [{:keys [database-file-name database-full-file-name
                            password
                            password-visible
                            key-file-name-part]}]
  [rn-view {:style {:flex 1 
                    :flexDirection "column"  
                    :width "90%" 
                    ;;:justify-content "center"
                    :margin-top "10%"
                    }}
   [rnp-text-input {:label "Database File"
                    :value database-file-name
                    :editable false
                    :onChangeText #()}]
   [rnp-text-input {:style {:margin-top 10}
                    :label "Master Password"
                           ;;:value password
                    :defaultValue password
                    :autoComplete "off"
                    :autoCapitalize "none"
                    :autoCorrect false
                    :secureTextEntry (not password-visible)
                    :right (r/as-element
                            [rnp-text-input-icon
                             {:icon (if password-visible "eye" "eye-off")
                              :onPress #(cmn-events/database-field-update :password-visible (not password-visible))}])
                    :onChangeText (fn [v]
                                           ;; After entering some charaters and delete is used to remove those charaters
                                           ;; password will have a string value "" resulting in a non visible password. Need to use nil instead
                                    (cmn-events/database-field-update :password (if (empty? v) nil v)))}]

   [rn-view
    [rnp-button {:mode "text"
                 :onPress  cmn-events/cancel-login}
     "Cancel"]
    [rnp-button {:mode "text"
                 :onPress (fn []
                            (.dismiss rn-keyboard)
                            ;;^js/RNKeyboard (.dismiss rn-keyboard)
                            (cmn-events/open-database-read-db-file))
                 #_(fn [] ^js/RNKeyboard (.dismiss rn-keyboard)
                     (if locked?
                       (opndb-events/authenticate-with-credential)
                       (opndb-events/open-database-read-db-file)))}
     "Continue"]]])




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn show-all-entries []
  (let [entries @(cmn-events/selected-entry-items)]
    [rn-view {}
     [rnp-text {:style {:alignSelf "center"
                        :width "85%"
                        :text-align "center"
                        :padding-left 0} :variant "titleLarge"} "Entries loaded"]

     [rnp-button {:mode "text"
                  :on-press cmn-events/to-home} "Home"]]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
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

(defn icon-name-color [found locked]
  (cond
    locked
    [const/ICON-LOCKED-DATABASE  @primary-color]  ;;"#477956" green tint

    found
    [const/ICON-DATABASE-EYE @rnc/tertiary-color]

    :else
    [const/ICON-DATABASE-OUTLINE @rnc/secondary-color]))

(defn row-item []
  (fn [{:keys [db-file-path file-name]}]
    (let [found true
          locked? false
          [icon-name color] (icon-name-color found locked?)]
      [rnp-list-item {:style {}
                      :onPress #(cmn-events/show-login file-name db-file-path)
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
                                              :onPress  #()}]]))}])))

(defn databases-list-content []
  (fn []
    (let [db-infos @(cmn-events/autofill-db-files-info)
          sections  [{:title "Databases" #_(lstr-l "databases")
                      :key "Databases"
                      ;; Recently used db info forms the data for this list
                      :data db-infos}]]
      [rn-section-list
       {:style {}
        :sections (clj->js sections)
        :renderItem  (fn [props] ;; keys are (:item :index :section :separators)
                       (let [props (js->clj props :keywordize-keys true)]
                         (r/as-element [row-item (-> props :item)])))
        :ItemSeparatorComponent (fn [_p]
                                  (r/as-element [rnp-divider]))
        :renderSectionHeader (fn [props] ;; key is :section
                               (let [props (js->clj props :keywordize-keys true)
                                     {:keys [title]} (-> props :section)]
                                 (r/as-element [databases-list-header title])))}])))

(defn home-page []
  [rn-view {:style {:flex 1 :width "100%"}}
   [rn-view {:style {:flex .5}}
    [databases-list-content]
    ]
   
   [rn-view {:style {:flex .5}}
    [rnp-button {:mode "text"
                 :on-press cmn-events/cancel-extension} "Cancel"]]])

(defn login-page []
  [open-db-page @(cmn-events/login-page-data)])



(defn open-page-content []
  [rn-safe-area-view {:style {:flex 1 :background-color @rnc/page-background-color}}
   [rn-view {:style {:flex 1 :justify-content "center" :align-items "center" }} ;;:margin-top "10%"

    (let [{:keys [page]} @(cmn-events/page-info)]

      (condp = page
        cmn-events/HOME_PAGE_ID
        [home-page]

        cmn-events/LOGIN_PAGE_ID
        [login-page]

        cmn-events/ENTRY_LIST_PAGE_ID
        [show-all-entries]
        
        :else
        [home-page]))

    #_[rn-view {:style {:flex .8 :width "100%"}}
       [databases-list-content]
       [rn-view
        [rnp-button {:mode "text"
                     :on-press cmn-events/cancel-extension} "Cancel"]]]]])

