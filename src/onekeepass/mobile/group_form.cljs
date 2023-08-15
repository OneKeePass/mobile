(ns onekeepass.mobile.group-form
  (:require [reagent.core :as r]
            [onekeepass.mobile.rn-components
             :as rnc
             :refer [lstr
                     icon-color
                     appbar-text-color
                     page-background-color
                     
                     page-title-text-variant
                     rn-view
                     rn-scroll-view
                     rn-keyboard-avoiding-view
                     rnp-checkbox
                     rnp-text-input
                     rnp-helper-text
                     rnp-text
                     rnp-touchable-ripple
                     rnp-text-input-icon
                     rnp-button]] 
            [onekeepass.mobile.background :refer [is-iOS]]
            [onekeepass.mobile.icons-list :as icons-list]
            [onekeepass.mobile.events.common :as cmn-events]
            [onekeepass.mobile.events.groups :as gf-events :refer [update-group-form-data]]))

(set! *warn-on-infer* true)

(defn appbar-title
  "Group form specific title to display"
  [title]
  [rn-view {:flexDirection "row"
            :style {:alignItems "center"
                    :justify-content "space-between"}}
   [rnp-button {:style {}
                :textColor @appbar-text-color
                :mode "text"
                :onPress gf-events/cancel-group-form}
    (lstr "button.labels.cancel")]
   [rnp-text {:style {:color @appbar-text-color
                      :max-width 120
                      :margin-right 20 :margin-left 20}
              :ellipsizeMode "tail"
              :numberOfLines 1
              :variant page-title-text-variant} (lstr title)]
   [rnp-button {:style {}
                :textColor @appbar-text-color
                :disabled (not @(gf-events/form-modified))
                :mode "text" :onPress gf-events/save-group-form}
    (lstr "button.labels.save")]])

(defn on-group-icon-selection [_icon-name icon-id]
  (update-group-form-data :icon-id icon-id))

(defn main-content []
  (let [marked-as-category @(gf-events/group-form-data-fields :marked-category)
        icon-name (icons-list/icon-id->name @(gf-events/group-form-data-fields :icon-id))
        error-fields @(gf-events/group-form-field :error-fields)]
    [rn-view {:style {:flexDirection "column" :justify-content "center" :padding 5}}

     [rnp-text-input {:style {:width "100%"} :multiline true :label (lstr "name")
                      :defaultValue @(gf-events/group-form-data-fields :name)
                      :onChangeText #(update-group-form-data :name %)
                      :right (r/as-element 
                              [rnp-text-input-icon 
                               {:iconColor @icon-color
                                :icon icon-name
                                :onPress #(cmn-events/show-icons-to-select 
                                           on-group-icon-selection)}])}]
     (when (contains? error-fields :name)
       [rnp-helper-text {:type "error" :visible (contains? error-fields :name)}
        (:name error-fields)])

     [rnp-text-input {:style {:width "100%"} :multiline true :label (lstr "notes")
                      :defaultValue @(gf-events/group-form-data-fields :notes)
                      :onChangeText #(update-group-form-data :notes %)}]

     (when (= :group @(gf-events/group-form-field :kind))
       [rnp-touchable-ripple {:style {:align-self "center" :margin-top 15  :width "45%"} 
                              :onPress #(update-group-form-data 
                                         :marked-category (not marked-as-category))}
        [rn-view {:flexDirection "row" :style {:alignItems "center" :justifyContent "space-between"}}
         [rnp-text (lstr "categoryMarked")]
         [rn-view {:pointerEvents "none"}
          [rnp-checkbox {:status (if marked-as-category "checked" "unchecked")}]]]])]))

(defn content []
  [rn-keyboard-avoiding-view {:style {:flex 1 } 
                              :behavior (if (is-iOS) "padding" nil)}
   [rn-scroll-view {:contentContainerStyle {:flexGrow 1 :background-color @page-background-color}}
    [main-content]]])