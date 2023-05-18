(ns onekeepass.mobile.common-components
  (:require [reagent.core :as r]
            [onekeepass.mobile.rn-components
             :as rnc
             :refer [rnms-modal-selector
                     lstr
                     tertiary-color
                     primary-color
                     rn-view
                     rn-scroll-view
                     rnp-button
                     rnp-chip
                     rnp-paragraph
                     rnp-divider
                     rnp-dialog
                     cust-dialog
                     rnp-dialog-icon
                     rnp-dialog-title
                     rnp-dialog-content
                     rnp-dialog-actions
                     rnp-modal
                     rnp-snackbar
                     rnp-text
                     rnp-text-input
                     rnp-text-input-icon]]
            [onekeepass.mobile.utils :as u]
            [onekeepass.mobile.events.save :as save-events]
            [onekeepass.mobile.events.common :as cmn-events]))

(set! *warn-on-infer* true)

(defn message-dialog [{:keys [dialog-show title category message]}]
  [rnp-dialog {:style {}
               :dismissable false
               :visible dialog-show
               :onDismiss #()}
   [rnp-dialog-icon {:icon (if (= category :error) "alert" "information")
                     :color (if (= category :error)
                              ^js/Error50Color (.-error50 rnc/md3-colors)
                              rnc/outline-color)}]
   [rnp-dialog-title {:style {:color ^js/Error20Color (.-error20 rnc/md3-colors)}} title]
   [rnp-dialog-content
    [rn-view {:style {:flexDirection "column" :justify-content "center"}}
     [rnp-paragraph message]]]
   [rnp-dialog-actions
    [rnp-button {:mode "text" :onPress cmn-events/close-message-dialog} "Close"]]])

(defn select-tags-dialog [{:keys [show all-tags new-tags-str selected-tags]}
                          selected-tags-receiver-fn]

  (let [sv-ref (atom nil)]
    [cust-dialog {:style {} :dismissable true :visible show :onDismiss #()}
     [rnp-dialog-title [rn-view {}
                        [rnp-text  {:variant "titleLarge"} "All Tags"]
                        [rnp-text {:style {:color tertiary-color}}
                         "Select or Deselect one or more tags"]]]
     [rnp-dialog-content
      [rn-view {:flexDirection "column"}
       [rn-view {:style {:min-height 100 :max-height 250} :flexDirection "column"}
        [rn-scroll-view {:style {:borderWidth .20 :borderRadius 4}
                         ;; Need to use flexGrow for the Scroll View to show its content
                         :contentContainerStyle {:flexGrow 1}
                         :ref (fn [ref] (reset! sv-ref ref))
                         :onContentSizeChange (fn [_h] (when-not (nil? @sv-ref)
                                                         (.scrollToEnd ^js/SV @sv-ref)))}
         [rn-view {:style {:flexDirection "row" :flexWrap "wrap"}}
          (doall
           (for [tag all-tags]
             ^{:key tag} [rnp-chip {:style {:margin 5}
                                    :selected (u/contains-val? selected-tags tag)
                                    :onPress #(cmn-events/tags-dialog-tag-selected tag)} tag]))]]]
       [rnp-divider {:style {:margin-top 10}}]
       [rn-view {:flexDirection "column"}
        [rnp-text-input  {:style {:width "100%"}
                          :label "Tags"
                          :placeholder "Comma separated tags"
                          :value new-tags-str
                          :onChangeText #(cmn-events/tags-dialog-update-new-tags-str %)
                          :right (r/as-element [rnp-text-input-icon {:icon "plus" :onPress cmn-events/tags-dialog-add-tags}])}]
        [rnp-text {:style {:color tertiary-color}}  "Press + to add tags"]]]]
     [rnp-dialog-actions
      [rnp-button {:mode "text" :onPress  (fn []
                                            ;; Send the current selected tags to the caller in entry form or group form
                                            (selected-tags-receiver-fn selected-tags)
                                            (cmn-events/tags-dialog-done))} "Close"]]]))

;;; Uses react-native-modal-selector based selector
;; Refer https://github.com/peacechen/react-native-modal-selector#props for all supported props
;; that can be used with 'rnms-modal-selector'

(defn select-field [{:keys [text-label options value on-change disabled] :or [disabled false]}]
  [rnms-modal-selector {;; data can also include additional custom keys which are passed to the onChange callback
                        ;; in addition to required ones - key, label
                        ;; For example uuid can also be passed
                        :data options
                        :initValue value
                        ;;:selectedKey (get options value)
                        :disabled disabled
                        :selectedItemTextStyle {:color primary-color}
                        :onChange on-change}
   [rnp-text-input {:style {:width "100%"} :editable false :label text-label :value value}]])

(defn confirm-dialog [{:keys [dialog-show title confirm-text actions]}]
  [cust-dialog {:style {} :dismissable true :visible dialog-show}
   [rnp-dialog-title {:ellipsizeMode "tail" :numberOfLines 1} title]
   [rnp-dialog-content
    [rnp-text confirm-text]]
   [rnp-dialog-actions
    (for [{:keys [label on-press]}  actions]
      ^{:key label} [rnp-button {:mode "text"
                                 :on-press on-press} label])]])

(defn message-snackbar
  ([{:keys [open message]}]
   [rnp-snackbar {:visible  open
                  :onDismiss cmn-events/close-message-snackbar
                  :action (fn [] (clj->js {:label "Undo" :onPress #()}))
                  :duration 7000
                  :style {} ;;:zIndex 10 this works in android and not in iOs
                  ;; zIndex in wrapperStyle makes the snackbar to appear on top fab in iOS. 
                  ;; Need to check on android
                  :wrapperStyle {:bottom 20 :zIndex 10}} (lstr message)])
  ([]
   [message-snackbar @(cmn-events/message-snackbar-data)]))

(defn message-modal [{:keys [dialog-show message]}]
  [rnp-modal {:visible dialog-show
              :dismissable false
              ;;:onDismiss #() 
              :contentContainerStyle {:backgroundColor "white" :padding 20}}
   [rn-view {:style {:height 100 :justify-content "center" :align-items "center"}}
    [rnp-text (lstr message)]]])

(defn save-error-modal [{:keys [dialog-show error-type error-message]}]
  [rnp-modal {:style {:margin-right 25 :margin-left 25  }
              :visible dialog-show
              :dismissable false
              :dismissableBackButton false
              ;;:onDismiss #() 
              :contentContainerStyle {:borderRadius 15 :height "60%" :backgroundColor "white" :padding 10}} ;;:padding 20
   [rn-scroll-view {:centerContent "true" :style {:backgroundColor "white"}}
    [rn-view {:style {:height "100%" :backgroundColor "white"}} ;;:align-items "center"
     [rn-view {:style {:flex .1  :justify-content "center" :align-items "center" }}
      [rnp-text {:style {:color tertiary-color} :variant "titleLarge"} "Database Save Error"]
      [rnp-text {:style {:color tertiary-color} :variant "titleSmall"} "Saving error"]
      ]
     
     #_[rnp-divider]
     [rn-view {:style {:flex .2  :min-height 50 :justify-content "center" :align-items "center"}}
      [rnp-text {:style {:textAlign "justify"}} "The database content has changed since you have loaded"]
      #_[rnp-text {:style {:margin-bottom 5}} "You may do:"]
      ]
     [rnp-divider]
     [rn-view {:style {:flex .7}} ;;:backgroundColor "yellow"
      [rn-view {:style {:margin-top 10 :margin-bottom 10 :align-items "center"}}
       [rnp-button {:style {:width "50%"} :labelStyle {:fontWeight "bold"}  :mode "text" :on-press #()} "Save as .."]
       [rnp-text {:style {:textAlign "justify"}} "You can save the database file with all your changes to another file and later manually resolve the conflicts"]]
      [rnp-divider]
      [rn-view {:style {:margin-top 10 :margin-bottom 10 :align-items "center"}}
       [rnp-button {:style {:width "70%"} :labelStyle {:fontWeight "bold"} :mode "text" :on-press #()} "Discard & Close database"]
       [rnp-text {:style {:textAlign "justify"}} "Ignore all changes made here"]
       ]

      [rnp-divider]
      [rn-view {:style {:margin-top 10 :margin-bottom 10 :align-items "center"}}
       [rnp-button {:style {:width "70%"} :labelStyle {:fontWeight "bold"}  :textColor "red" :mode "text" :on-press #()} "Overwrite"]
       [rnp-text {:style {:textAlign "justify"}} "This database will overwrite the target database with your changes"]
       ]

      [rnp-divider]
      [rn-view {:style {:margin-top 10 :margin-bottom 10 :align-items "center"}}
       [rnp-button {:style {:width "70%"} :labelStyle {:fontWeight "bold"} :mode "text" :on-press save-events/save-error-modal-hide} "Cancel"]
       [rnp-text {:style {:textAlign "justify"}} ""]
       ]]]]])

(defn menu-action-factory
  "Wraps the hide-menu-action and returns a factory which itself returns another factory
  This inner factory can be used in menu items' onPress call
 "
  [hide-menu-action]
  (fn [action & action-args]
    (fn [_e]
      (hide-menu-action)
      (apply action action-args))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def entry-delete-confirm-dialog-data (r/atom {:dialog-show false :entry-uuid nil}))

(defn show-entry-delete-confirm-dialog [entry-uuid]
  (swap! entry-delete-confirm-dialog-data assoc :dialog-show true :entry-uuid entry-uuid))

(defn entry-delete-confirm-dialog [call-on-ok-fn]
  (let [{:keys [dialog-show entry-uuid]} @entry-delete-confirm-dialog-data]
    [confirm-dialog {:dialog-show dialog-show
                     :title  (lstr "dialog.titles.deleteEntry")
                     :confirm-text (lstr "dialog.texts.deleteEntry")
                     :actions [{:label (lstr "button.labels.cancel")
                                :on-press (fn []
                                            (swap! entry-delete-confirm-dialog-data
                                                   assoc :dialog-show false))}
                               {:label (lstr "button.labels.continue")
                                :on-press (fn []
                                            (call-on-ok-fn entry-uuid)
                                            (swap! entry-delete-confirm-dialog-data
                                                   assoc :dialog-show false))}]}]))


(def group-delete-confirm-dialog-data (r/atom {:dialog-show false
                                               :group-uuid nil}))
(defn show-group-delete-confirm-dialog [group-uuid]
  (swap! group-delete-confirm-dialog-data assoc :dialog-show true :group-uuid group-uuid))

(defn group-delete-confirm-dialog [call-on-ok-fn]
  (let [{:keys [dialog-show group-uuid]} @group-delete-confirm-dialog-data]
    [confirm-dialog {:dialog-show dialog-show
                     :title  (lstr "dialog.titles.deleteGroup")
                     :confirm-text (lstr "dialog.texts.deleteGroup")
                     :actions [{:label (lstr "button.labels.cancel")
                                :on-press #(swap! group-delete-confirm-dialog-data
                                                  assoc :dialog-show false)}
                               {:label (lstr "button.labels.continue")
                                :on-press (fn []
                                            (call-on-ok-fn group-uuid)
                                            (swap! group-delete-confirm-dialog-data
                                                   assoc :dialog-show false))}]}]))

;; TODO: Replace the use of group-delete-confirm-dialog and entry-delete-confirm-dialog with this 

(defn confirm-dialog-with-ref
  "This creates an reagent component. The arg 'data-ref' is reagent.core/atom"
  [data-ref]
  (let [{:keys [dialog-show title confirm-text call-on-ok-fn]} @data-ref]
    [confirm-dialog {:dialog-show dialog-show
                     :title title
                     :confirm-text confirm-text
                     :actions [{:label (lstr "button.labels.cancel")
                                :on-press #(swap! data-ref assoc :dialog-show false)}
                               {:label (lstr "button.labels.continue")
                                :on-press (fn []
                                            (call-on-ok-fn @data-ref)
                                            (swap! data-ref assoc :dialog-show false))}]}]))


(defn confirm-dialog-factory
  "The arg 'data-ref' is reagent.core/atom and returns a map (keys are :dialog :show) containg a  
   reagent dialog component and a function to call to show this dialog"
  [data-ref]
  {:dialog [confirm-dialog-with-ref data-ref]
   :show #(swap! data-ref assoc :dialog-show true)})