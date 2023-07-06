(ns onekeepass.mobile.key-file-form
  (:require
   [reagent.core :as r]
   [onekeepass.mobile.background :refer [is-iOS]]
   [onekeepass.mobile.rn-components :refer [rn-keyboard-avoiding-view
                                            rn-scroll-view
                                            rn-view
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

;;;;;;;;;;;; Menus ;;;;;;;;;;;;;;;;;;;;;
(def ^:private list-menu-action-data (r/atom {:show false :x 0 :y 0
                                              :full-file-name nil
                                              :file-name nil}))

(defn hide-list-action-menu []
  (swap! list-menu-action-data assoc :show false))

(defn show-list-menu
  "Pops the menu popup for the selected row item"
  [^js/PEvent event full-file-name file-name]
  (swap! list-menu-action-data
         assoc :show true
         :full-file-name full-file-name
         :file-name file-name
         :x (-> event .-nativeEvent .-pageX) :y (-> event .-nativeEvent .-pageY)))

;;menu-action-factory returns a factory to use in 'onPress' 
(def list-menu-action-factory (menu-action-factory hide-list-action-menu))

(defn list-action-menu [{:keys [show x y full-file-name file-name]}]

  [rnp-menu {:visible show :onDismiss hide-list-action-menu :anchor (clj->js {:x x :y y})}
   [rnp-menu-item {:title "Select"
                   :onPress #() #_(list-menu-action-factory
                                   kf-events/key-file-selected full-file-name file-name)}]

   [rnp-menu-item {:title "Remove"
                   :onPress (list-menu-action-factory kf-events/delete-key-file file-name)}]


   [rnp-menu-item {:title "Save To"
                   :onPress #()}]])


(defn list-header [title]
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

(defn row-item [{:keys [full-file-name file-name]}]
  [rnp-list-item {:style {}
                  :onPress #()
                  :title (r/as-element
                          [rnp-text {:style {:color  primary-color}
                                     :variant "titleMedium"} file-name])

                  :right (fn [_props] (r/as-element
                                       [:<> ;; We can add another icon here if required
                                        [rnp-icon-button
                                         {:size 24
                                          :style {:margin -10}
                                          :icon dots-icon-name
                                          :onPress #(show-list-menu % full-file-name file-name)}]]))}])

(defn key-files-list-content []
  (fn [imported-key-files]
    (let [;;imported-key-files @(kf-events/imported-key-files)
          sections  [{:title (lstr "importedKeyFiles")
                      :key "Key Files"
                      ;; Recently used db info forms the data for this list
                      :data imported-key-files}]]
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
  [rn-view {:style {:height "100%" :padding 5}} ;;:backgroundColor "red"
   [rn-view {:style {:margin-top 20 :margin-bottom 20 :align-items "center"}}
    [rnp-button {:style {:width "50%"}
                ;;:labelStyle {:fontWeight "bold"}
                 :mode "contained"
                 :on-press kf-events/pick-key-file} "Browse"]
    [rnp-text {:style {:margin-top 10   :textAlign "justify"}}
     "You can pick any random file that does not change. A hash of the file's content is used as an additional key"]]
   [rnp-divider]
   [rn-view {:style {:margin-top 20 :margin-bottom 20 :align-items "center"}}
    [rnp-button {:style {:width "50%"}
                ;;:labelStyle {:fontWeight "bold"}
                 :mode "contained"
                 :on-press #()} "Generate"]
    [rnp-text {:style {:margin-top 10   :textAlign "justify"}}
     "OneKeePass can generate a random key and store it in a xml file to use as an additional key"]]
   [rnp-divider]
   [key-files-list-content @(kf-events/imported-key-files)]])

(defn content []
  [rn-safe-area-view {:style {:flex 1}}
   [main-content]
   [rnp-portal
    [list-action-menu @list-menu-action-data]]]

  ;; Using the following instead of the above gives this error:
  ;; VirtualizedLists should never be nested inside plain ScrollViews with the 
  ;; same orientation because it can break windowing and other functionality - use another 
  ;; VirtualizedList-backed container instead. 
  #_[rn-keyboard-avoiding-view {:style {:flex 1}
                                :behavior (if (is-iOS) "padding" nil)}
     [rn-scroll-view {:contentContainerStyle {:flexGrow 1}}
      [main-content]]])