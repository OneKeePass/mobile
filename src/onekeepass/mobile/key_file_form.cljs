(ns onekeepass.mobile.key-file-form
  (:require
   [reagent.core :as r] 
   [onekeepass.mobile.rn-components :as rnc :refer [cust-dialog
                                            rnp-dialog-title
                                            rnp-dialog-content
                                            rnp-dialog-actions
                                            rn-view
                                            rnp-text-input
                                            rnp-button
                                            rnp-divider
                                            rnp-text
                                            primary-container-color
                                            primary-color
                                            dots-icon-name
                                            rn-section-list
                                            rnp-list-item
                                            rnp-icon-button
                                            rnp-menu
                                            rnp-menu-item
                                            rnp-portal
                                            lstr
                                            rn-safe-area-view]]
   [onekeepass.mobile.common-components  :refer [menu-action-factory]]
   [onekeepass.mobile.events.key-file-form :as kf-events]))

;;;;;;;;;;;;;;;;;;;;;;;;;;; Key file name dialog ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn key-file-name-dialog
  [{:keys [dialog-show file-name]}]
  [cust-dialog {:style {} :dismissable false :visible dialog-show :onDismiss #()}
   [rnp-dialog-title (lstr "keyFileName")]
   [rnp-dialog-content
    [rn-view {:style {:flexDirection "column"  :justify-content "center"}}
     [rnp-text-input {:label (lstr "name")
                      :defaultValue file-name
                      :autoComplete "off"
                      :autoCapitalize "none"
                      :placeholder "e.g mykey.keyx"
                      :onChangeText #(kf-events/generate-file-name-dialog-update %)}]]]

   [rnp-dialog-actions
    [rnp-button {:mode "text"
                 :onPress #(kf-events/generate-file-name-dialog-hide)}
     (lstr "button.labels.cancel")]
    [rnp-button {:mode "text"
                 :onPress #(kf-events/generate-key-file file-name)}
     (lstr "button.labels.ok")]]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Menus ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private list-menu-action-data (r/atom {:show false :x 0 :y 0
                                              :key-file-info nil}))

(defn hide-list-action-menu []
  (swap! list-menu-action-data assoc :show false))

(defn show-list-menu
  "Pops the menu popup for the selected row item"
  [^js/PEvent event key-file-info]
  (swap! list-menu-action-data
         assoc :show true
         :key-file-info key-file-info
         :x (-> event .-nativeEvent .-pageX) :y (-> event .-nativeEvent .-pageY)))

;;menu-action-factory returns a factory to use in 'onPress' 
(def list-menu-action-factory (menu-action-factory hide-list-action-menu))

(defn list-action-menu [{:keys [show x y key-file-info]}]
  [rnp-menu {:visible show :onDismiss hide-list-action-menu :anchor (clj->js {:x x :y y})}
   [rnp-menu-item {:title (lstr "menu.labels.select")
                   :onPress (list-menu-action-factory
                             kf-events/set-selected-key-file-info key-file-info)}]

   [rnp-menu-item {:title (lstr "menu.labels.remove")
                   :onPress (list-menu-action-factory
                             kf-events/delete-key-file (:file-name key-file-info))}]

   [rnp-menu-item {:title (lstr "menu.labels.saveAs")
                   :onPress (list-menu-action-factory
                             kf-events/key-file-save-as key-file-info)}]])

(defn list-header [title]
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

(defn row-item [{:keys [file-name] :as key-file-info}]
  [rnp-list-item {:style {}
                  :onPress #(kf-events/set-selected-key-file-info key-file-info)
                  :title (r/as-element
                          [rnp-text {:style {:color  @primary-color}
                                     :variant "titleMedium"} file-name])

                  :right (fn [_props] (r/as-element
                                       [:<> ;; We can add another icon here if required
                                        [rnp-icon-button
                                         {:size 24
                                          :style {:margin -10}
                                          :icon dots-icon-name
                                          :onPress #(show-list-menu % key-file-info)}]]))}])

(defn key-files-list-content []
  (fn [imported-key-files]
    (let [sections  [{:title (lstr "importedKeyFiles")
                      :key "Key Files"
                      ;; imported-key-files is a list of map formed from struct 'KeyFileInfo' 
                      ;; forms the data for this list
                      :data (if-not (nil? imported-key-files) imported-key-files [])}]]
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
                                 (r/as-element [list-header title])))}])))

(defn main-content []
  (let [show-generate-option? @(kf-events/show-generate-option)]
    [rn-view {:style {:height "100%" :padding 5}} ;;:backgroundColor "red"
     [rn-view {:style {:margin-top 20 :margin-bottom 20 :align-items "center"}}
      [rnp-button {:style {:width "50%"}
                ;;:labelStyle {:fontWeight "bold"}
                   :mode "contained"
                   :on-press kf-events/pick-key-file} (lstr "button.labels.browse")]
      [rnp-text {:style {:margin-top 10   :textAlign "justify"}}
       "You can pick any random file that does not change. A hash of the file's content is used as an additional key"]]
     [rnp-divider]

     (when show-generate-option?
       [rn-view {:style {:margin-top 20 :margin-bottom 20 :align-items "center"}}
        [rnp-button {:style {:width "50%"}
                ;;:labelStyle {:fontWeight "bold"}
                     :mode "contained"
                     :on-press #(kf-events/generate-file-name-dialog-show)} (lstr "button.labels.generate")]
        [rnp-text {:style {:margin-top 10   :textAlign "justify"}}
         "OneKeePass can generate a random key and store it in a xml file to use as an additional key"]])

     [rnp-divider]
     [rn-view {:style {:padding 5 :align-items "center"}} ;;:height "100%" 
      [rnp-text {:style {:margin-top 2   :textAlign "justify"}} "Or"]
      [rnp-text {:style {:margin-top 2   :textAlign "justify"}} "Select from the imported key files"]
      #_[key-files-list-content @(kf-events/imported-key-files)]]

     [key-files-list-content @(kf-events/imported-key-files)]]))

(defn content []
  [rn-safe-area-view {:style {:flex 1 :background-color @rnc/page-background-color}}
   [main-content]
   [rnp-portal
    [list-action-menu @list-menu-action-data]
    [key-file-name-dialog @(kf-events/generate-file-name-dialog-data)]]]

  ;; Using the following instead of the above gives this error:
  ;; VirtualizedLists should never be nested inside plain ScrollViews with the 
  ;; same orientation because it can break windowing and other functionality - use another 
  ;; VirtualizedList-backed container instead. 
  #_[rn-keyboard-avoiding-view {:style {:flex 1}
                                :behavior (if (is-iOS) "padding" nil)}
     [rn-scroll-view {:contentContainerStyle {:flexGrow 1}}
      [main-content]]])