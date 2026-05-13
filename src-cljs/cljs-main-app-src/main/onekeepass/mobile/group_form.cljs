(ns onekeepass.mobile.group-form
  (:require [onekeepass.mobile.background :refer [is-iOS]]
            [onekeepass.mobile.events.common :as cmn-events]
            [onekeepass.mobile.events.custom-icons :as ci-events]
            [onekeepass.mobile.events.groups :as gf-events :refer [update-group-form-data]]
            [onekeepass.mobile.icons-list :as icons-list]
            [onekeepass.mobile.rn-components
             :as rnc
             :refer [appbar-text-color icon-color page-background-color
                     page-title-text-variant rn-image rn-keyboard-avoiding-view
                     rn-scroll-view rn-view rnp-button rnp-checkbox
                     rnp-helper-text rnp-text rnp-text-input
                     rnp-text-input-icon rnp-touchable-ripple]]
            [onekeepass.mobile.translation :refer [lstr-bl lstr-l lstr-pt]]
            [reagent.core :as r]))

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
    (lstr-bl "cancel")]
   [rnp-text {:style {:color @appbar-text-color
                      :max-width 120
                      :margin-right 20 :margin-left 20}
              :ellipsizeMode "tail"
              :numberOfLines 1
              :variant page-title-text-variant} (lstr-pt title)]
   [rnp-button {:style {}
                :textColor @appbar-text-color
                :disabled (not @(gf-events/form-modified))
                :mode "text" :onPress gf-events/save-group-form}
    (lstr-bl "save")]])

(defn on-group-icon-selection
  ([_icon-name icon-id]
   (on-group-icon-selection _icon-name icon-id nil))
  ([_icon-name icon-id custom-icon-uuid]
   (if custom-icon-uuid
     (do
       (update-group-form-data :icon-id 0)
       (update-group-form-data :custom-icon-uuid custom-icon-uuid))
     (do
       (update-group-form-data :icon-id icon-id)
       (update-group-form-data :custom-icon-uuid nil)))))

(defn- launch-icon-picker []
  (cmn-events/show-icons-to-select on-group-icon-selection))

(defn- group-input-right-icon
  "Paper's TextInput.right only renders TextInput.Icon children; using
   the render-function form of :icon lets us draw the custom icon's
   data-URL image inside that slot while keeping the onPress wiring."
  [icon-name custom-data-url]
  ;; (println "group-input-right-icon is called with custom-data-url" custom-data-url)
  (if custom-data-url
    [rnp-text-input-icon
     {:icon (fn []
              (r/as-element
               [rn-image {:source {:uri custom-data-url}
                          :style {:width 24 :height 24}}]))
      :onPress launch-icon-picker}]
    [rnp-text-input-icon {:iconColor @icon-color
                          :icon icon-name
                          :onPress launch-icon-picker}]))

(defn main-content []
  (let [marked-as-category @(gf-events/group-form-data-fields :marked-category)
        icon-id @(gf-events/group-form-data-fields :icon-id)
        custom-icon-uuid @(gf-events/group-form-data-fields :custom-icon-uuid)
        icon-name (icons-list/icon-id->name icon-id)
        _ (when custom-icon-uuid (ci-events/ensure-icon-data-url custom-icon-uuid))
        custom-data-url (when custom-icon-uuid
                          @(ci-events/icon-data-url custom-icon-uuid))
        error-fields @(gf-events/group-form-field :error-fields)
        right-icon (r/as-element
                    ;;The group-input-right-icon is called directly before r/as-element. Otherwise the icon is not shown
                    (group-input-right-icon icon-name custom-data-url)
                    #_[group-input-right-icon icon-name custom-data-url])]
    #_(println "main-content is called with custom-data-url" custom-data-url)
    [rn-view {:style {:flexDirection "column" :justify-content "center" :padding 5}}

     [rnp-text-input {:style {:width "100%"}
                      :multiline true
                      :label (lstr-l "name")
                      :defaultValue @(gf-events/group-form-data-fields :name)
                      :onChangeText #(update-group-form-data :name %)
                      :right right-icon}]
     (when (contains? error-fields :name)
       [rnp-helper-text {:type "error" :visible (contains? error-fields :name)}
        (:name error-fields)])

     [rnp-text-input {:style {:width "100%"}
                      :multiline true :label
                      (lstr-l "notes")
                      :defaultValue @(gf-events/group-form-data-fields :notes)
                      :onChangeText #(update-group-form-data :notes %)}]

     (when (= :group @(gf-events/group-form-field :kind))
       [rnp-touchable-ripple {:style {:align-self "center" :margin-top 15  :width "45%"}
                              :onPress #(update-group-form-data
                                         :marked-category (not marked-as-category))}
        [rn-view {:flexDirection "row" :style {:alignItems "center" :justifyContent "space-between"}}
         [rnp-text (lstr-l "categoryMarked")]
         [rn-view {:pointerEvents "none"}
          [rnp-checkbox {:status (if marked-as-category "checked" "unchecked")}]]]])]))

(defn content []
  [rn-keyboard-avoiding-view {:style {:flex 1}
                              ;; After Android 'compileSdkVersion = 35 introduction
                              ;; Also see comments in js/components/KeyboardAvoidingDialog.js
                              :behavior "padding" #_(if (is-iOS) "padding" nil)}
   [rn-scroll-view {:contentContainerStyle {:flexGrow 1 :background-color @page-background-color}}
    [main-content]]])