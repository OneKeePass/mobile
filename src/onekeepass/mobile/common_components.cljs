(ns onekeepass.mobile.common-components
  (:require [onekeepass.mobile.constants :as const]
            [onekeepass.mobile.events.common :as cmn-events]
            [onekeepass.mobile.rn-components
             :as rnc
             :refer [cust-dialog message-modal-background-color
                     modal-selector-colors on-background-color rn-scroll-view
                     rn-view rnms-modal-selector rnp-button rnp-chip
                     rnp-dialog rnp-dialog-actions rnp-dialog-content
                     rnp-dialog-icon rnp-dialog-title rnp-divider rnp-modal rn-pressable
                     rnp-snackbar rnp-text rnp-text-input rnp-text-input-icon
                     tertiary-color]]
            [onekeepass.mobile.translation :refer [lstr-bl lstr-dlg-text
                                                   lstr-dlg-title
                                                   lstr-error-dlg-text
                                                   lstr-error-dlg-title lstr-l
                                                   lstr-modal-dlg-text
                                                   lstr-msg-dlg-text
                                                   lstr-msg-dlg-title lstr-sm]]
            [onekeepass.mobile.utils :as u]
            [reagent.core :as r]))

(set! *warn-on-infer* true)

;; TODO: Need to use 'generic-dialog-*' as done in onekeepass.mobile.events.dialogs
;; for all dialogs data provider and events

;; dialog data is under the key :message-box in db
(defn message-dialog
  "Called to show an error or a general message
  The value of key 'category' determines whether it is error or message
   "
  [{:keys [dialog-show title category message]}]
  (let [error? (= category :error)
        title-txt (if error? (lstr-error-dlg-title title) (lstr-msg-dlg-title title))
        msg-txt (if error? (lstr-error-dlg-text message) (lstr-msg-dlg-text message))]
    [rnp-dialog {:style {}
                 :dismissable false
                 :visible dialog-show
                 :onDismiss #()}
     [rnp-dialog-icon {:icon (if error? "alert" "information")
                       :color (if error?
                                @rnc/error-color
                                @rnc/outline-color)}]
     [rnp-dialog-title {:style {:color (if error?
                                         @rnc/error-color
                                         @rnc/tertiary-color)}} title-txt]
     [rnp-dialog-content
      [rn-view {:style {:flexDirection "column" :justify-content "center"}}
       [rnp-text msg-txt]]]
     [rnp-dialog-actions
      [rnp-button {:mode "text" :onPress cmn-events/close-message-dialog} (lstr-bl "close")]]]))

(defn select-tags-dialog [{:keys [show all-tags new-tags-str selected-tags]}
                          selected-tags-receiver-fn]

  (let [sv-ref (atom nil)]
    [cust-dialog {:style {} :dismissable true :visible show :onDismiss #()}
     [rnp-dialog-title [rn-view {}
                        [rnp-text  {:variant "titleLarge"} (lstr-dlg-title 'allTags)]
                        [rnp-text {:style {:color @tertiary-color}}
                         (lstr-dlg-text 'allTagsTxt)]]]
     [rnp-dialog-content
      [rn-view {:flexDirection "column"}
       [rn-view {:style {:min-height 100 :max-height 250} :flexDirection "column"}
        [rn-scroll-view {:style {:borderWidth .20 :borderRadius 4 :border-color @on-background-color}
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
                          :label (lstr-l 'tags)
                          :placeholder (lstr-dlg-text 'allTagsPh)
                          :value new-tags-str
                          :onChangeText #(cmn-events/tags-dialog-update-new-tags-str %)
                          :right (r/as-element [rnp-text-input-icon {:icon const/ICON-PLUS :onPress cmn-events/tags-dialog-add-tags}])}]
        [rnp-text {:style {:color @tertiary-color}} 
         (lstr-dlg-text 'allTagsAddHint)]]]]
     [rnp-dialog-actions
      [rnp-button {:mode "text" :onPress  (fn []
                                            ;; Send the current selected tags to the caller in entry form or group form
                                            (selected-tags-receiver-fn selected-tags)
                                            (cmn-events/tags-dialog-done))} (lstr-bl 'close)]]]))

;;; Uses react-native-modal-selector based selector
;; Refer https://github.com/peacechen/react-native-modal-selector#props for all supported props
;; that can be used with 'rnms-modal-selector'

(defn select-field [{:keys [text-label options value on-change disabled] :or [disabled false]}]
  [rnms-modal-selector {;; data can also include additional custom keys which are passed to the onChange callback
                        ;; in addition to required ones - key, label
                        ;; For example uuid can also be passed
                        ;;:optionStyle {:background-color "red"}
                        :optionContainerStyle {:background-color @(:background-color modal-selector-colors)}
                        :data options
                        :initValue value
                        ;;:selectedKey (get options value)
                        :disabled disabled
                        :selectedItemTextStyle {:color @(:selected-text-color modal-selector-colors) :fontWeight "bold"}
                        :onChange on-change}
   [rnp-text-input {:style {:width "100%"} :editable false :label text-label :value value}]])

;; Note:
;; As we wrap the rnms-modal-selector in Pressable component, all press events are handled by rn-pressable
;; and no event is passed to rnms-modal-selector
(defn select-field-view [{:keys [text-label options value on-change disabled pressable-on-press] :or [disabled false]}]
  [rn-pressable {:on-press (if-not (nil? pressable-on-press) pressable-on-press #()) #_#(println "Pressed value.. " value)}
     [rnms-modal-selector {;; data can also include additional custom keys which are passed to the onChange callback
                           ;; in addition to required ones - key, label
                           ;; For example uuid can also be passed
                           ;;:optionStyle {:background-color "red"}
                           :optionContainerStyle {:background-color @(:background-color modal-selector-colors)}
                           :data options
                           :initValue value
                           ;;:selectedKey (get options value)
                           :disabled disabled
                           ;;:supportedOrientations (clj->js ["portrait" ])
                           :selectedItemTextStyle {:color @(:selected-text-color modal-selector-colors) :fontWeight "bold"}
                           :onChange on-change}
      [rnp-text-input {:style {:width "100%"} :editable false :label text-label :value value}]]])


(defn confirm-dialog 
  "A Generic confirm dialog. It is expected all texts should have been translated by caller"
  [{:keys [dialog-show title confirm-text actions]}]
  [cust-dialog {:style {} :dismissable true :visible dialog-show}
   [rnp-dialog-title {:ellipsizeMode "tail" :numberOfLines 1} title]
   [rnp-dialog-content
    [rnp-text confirm-text]]
   [rnp-dialog-actions
    (for [{:keys [label on-press]}  actions]
      ^{:key label} [rnp-button {:mode "text"
                                 :on-press on-press} label])]])

(defn vertical-buttons [actions]
  [rn-view {:style {}}
   (for [{:keys [label disabled on-press]} actions]
     ^{:key label} [rnp-button {:mode "text"
                                ;;:labelStyle {:fontWeight 700}
                                :disabled (if (nil? disabled) false disabled)
                                :on-press on-press} (lstr-bl label)])])

(defn confirm-dialog-with-lstr
  "A Generic confirm dialog. It is expected that caller needs to pass 
   translation keys (either as symbol or string key) for the texts (title,confirm-text, labels) and not any texts"
  [{:keys [dialog-show
           title
           confirm-text
           show-action-as-vertical
           actions]}] 
  [cust-dialog {:style {} :dismissable true :visible dialog-show}
   [rnp-dialog-title {:ellipsizeMode "tail" :numberOfLines 1} (lstr-dlg-title title)]
   [rnp-dialog-content
    [rnp-text (lstr-dlg-text confirm-text)]]
   [rnp-dialog-actions {:style {:justify-content (if show-action-as-vertical "center" "flex-end")}} ;;
    (if-not show-action-as-vertical
      (for [{:keys [label disabled on-press]} actions]
        ^{:key label} [rnp-button {:mode "text"
                                   :disabled (if (nil? disabled) false disabled)
                                   :on-press on-press}
                       (lstr-bl label)])
      [vertical-buttons actions])]])

(defn message-snackbar
  "Called to show result of an action for sometime. 
   The caller needs to pass the translation key for message"
  ([{:keys [open message]}]
   [rnp-snackbar {:visible  open
                  :onDismiss cmn-events/close-message-snackbar
                  ;; label 'Close' is not seen
                  :action (fn [] (clj->js {:label "Close"})) ;;:onPress #()
                  :duration 4000
                  ;;:theme {:colors {:inverseOnSurface "red"}} ;; only inverseOnSurface works
                  :style {} ;;:zIndex 10 this works in android and not in iOs
                  ;; zIndex in wrapperStyle makes the snackbar to appear on top fab in iOS. 
                  ;; Need to check on android
                  :wrapperStyle {:bottom 20 :zIndex 10}} (lstr-sm message)])
  ([]
   [message-snackbar @(cmn-events/message-snackbar-data)]))

(defn message-modal 
  "Called to show the passed message (mostly translation key as symbol) temporarily while 
   background work is going on without any title"
  [{:keys [dialog-show message]}] 
  [rnp-modal {:visible dialog-show
              :dismissable false
              ;;:onDismiss #() 
              :contentContainerStyle {:backgroundColor @message-modal-background-color :padding 20}}
   [rn-view {:style {:height 100 :justify-content "center" :align-items "center"}}
    [rnp-text (lstr-modal-dlg-text message)]]])

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

;; TODO: Need to replace with generic dialog data handling methods as done in
;; onekeepass.mobile.events.dialogs

(def entry-delete-confirm-dialog-data (r/atom {:dialog-show false :entry-uuid nil}))

(defn show-entry-delete-confirm-dialog [entry-uuid]
  (swap! entry-delete-confirm-dialog-data assoc :dialog-show true :entry-uuid entry-uuid))

(defn entry-delete-confirm-dialog [call-on-ok-fn]
  (let [{:keys [dialog-show entry-uuid]} @entry-delete-confirm-dialog-data]
    ;; title, confirm-text, label etc are to be translated before calling 'confirm-dialog'
    [confirm-dialog {:dialog-show dialog-show
                     :title  (lstr-dlg-title "deleteEntry")
                     :confirm-text (lstr-dlg-text "deleteEntry")
                     :actions [{:label (lstr-bl "cancel")
                                :on-press (fn []
                                            (swap! entry-delete-confirm-dialog-data
                                                   assoc :dialog-show false))}
                               {:label (lstr-bl "continue")
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
                     :title  (lstr-dlg-title "deleteGroup")
                     :confirm-text (lstr-dlg-text "deleteGroup")
                     :actions [{:label (lstr-bl "cancel")
                                :on-press #(swap! group-delete-confirm-dialog-data
                                                  assoc :dialog-show false)}
                               {:label (lstr-bl "continue")
                                :on-press (fn []
                                            (call-on-ok-fn group-uuid)
                                            (swap! group-delete-confirm-dialog-data
                                                   assoc :dialog-show false))}]}]))

;; TODO: Need to replace with generic dialog data handling methods as done in
;; onekeepass.mobile.events.dialogs

(defn- confirm-dialog-with-ref
  "This creates an reagent component. The arg 'data-ref' is reagent.core/atom"
  [data-ref]
  (let [{:keys [dialog-show title confirm-text call-on-ok-fn]} @data-ref]
    ;; title confirm-text have translated texts and translation should be called for them here
    [confirm-dialog {:dialog-show dialog-show
                     :title title
                     :confirm-text confirm-text
                     :actions [{:label (lstr-bl "cancel")
                                :on-press #(swap! data-ref assoc :dialog-show false)}
                               {:label (lstr-bl "continue")
                                :on-press (fn []
                                            (call-on-ok-fn @data-ref)
                                            (swap! data-ref assoc :dialog-show false))}]}]))

;;; See remove-confirm-dialog-info , overwrite-confirm-dialog-info for example usage
;;; Important: we need to call (:dialog ..-info) inside rnp-portal

(defn confirm-dialog-factory
  "The arg 'data-ref' is reagent.core/atom and returns a map (keys are :dialog :show) containg a  
   reagent dialog component and a function to call to show this dialog"
  [data-ref]
  {:dialog [confirm-dialog-with-ref data-ref]
   :show #(swap! data-ref assoc :dialog-show true)})