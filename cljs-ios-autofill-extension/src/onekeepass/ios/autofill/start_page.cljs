(ns onekeepass.ios.autofill.start-page
  (:require [onekeepass.ios.autofill.constants :as const :refer [FLEX-DIR-ROW]]
            [onekeepass.ios.autofill.entry-list :as entry-list]
            [onekeepass.ios.autofill.entry-form :as entry-form]
            [onekeepass.ios.autofill.events.common :as cmn-events]
            [onekeepass.ios.autofill.events.entry-form :as form-events]
            [onekeepass.ios.autofill.common-components :refer [message-dialog]]
            [onekeepass.ios.autofill.rn-components :as rnc
             :refer [appbar-text-color dots-icon-name page-title-text-variant
                     primary-color primary-container-color rn-keyboard
                     rn-safe-area-view rn-section-list rn-view rnp-button
                     rnp-divider rnp-icon-button rnp-list-icon rnp-list-item rnp-searchbar rnp-portal
                     rnp-text rnp-text-input rnp-text-input-icon]]
            [reagent.core :as r]))


(defn open-db-page [{:keys [database-file-name database-full-file-name
                            password
                            password-visible
                            key-file-name-part]}]
  [rn-view {:style {:flex 1
                    :flexDirection "column"
                    :width "90%"
                    ;;:justify-content "center"
                    :margin-top "10%"}}
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

   [rn-view {:flexDirection FLEX-DIR-ROW :style {:margin-top 20 :justify-content "center"}}
    [rnp-button {:mode "text"
                 :onPress  cmn-events/cancel-login}
     "Back"]
    [rnp-button {:mode "text"
                 :onPress (fn []
                            (.dismiss rn-keyboard)
                            ;;^js/RNKeyboard (.dismiss rn-keyboard)
                            (cmn-events/open-database-read-db-file))}
     "Continue"]]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn show-all-entries []
  [rn-view {:style {:flex 1 :width "100%"}}
   [entry-list/content]])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn show-form []
  [rn-view {:style {:flex 1 :width "100%"}}
   [entry-form/content]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
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
   [rn-view {:style {:flex 1}}
    [databases-list-content]]])

(defn login-page []
  [open-db-page @(cmn-events/login-page-data)])

(defn top-bar-left-action [page]
  (if (= page cmn-events/ENTRY_FORM_PAGE_ID)
    {:action form-events/cancel-entry-form
     :label "Back"
     :title "Entry details"}
    {:action cmn-events/cancel-extension
     :label "Cancel"
     :title "AutoFill Password"}))

(defn top-bar [page]
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

(defn searchbar []
  (let [term @(cmn-events/search-term)]
    [rn-view {:margin-top 10}
     [rnp-searchbar {;; clearIcon mostly visible when value has some vlaue
                     :clearIcon "close"
                     :style {:margin-left 1
                             :margin-right 1
                             :borderWidth 0}
                     :placeholder "Search"
                     :onChangeText (fn [v]
                                     (cmn-events/search-term-update v))
                     :value term}]]))

(defn open-page-content []
  [rn-safe-area-view {:style {:flex 1 :background-color @rnc/page-background-color}}
   (let [{:keys [page]} @(cmn-events/page-info)]
     [rn-view {:style {:flex 1 :width "100%"}}
      [top-bar page]
      (when (= cmn-events/ENTRY_LIST_PAGE_ID page)
        [searchbar])

      [rn-view {:style {:justify-content "center"
                        :align-items "center"
                        :flex 1}}
       (condp = page
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
    [message-dialog @(cmn-events/message-dialog-data)]]])

