(ns onekeepass.ios.autofill.common-components
  (:require [onekeepass.ios.autofill.rn-components :as rnc 
             :refer [cust-dialog message-modal-background-color
                     modal-selector-colors on-background-color rn-scroll-view
                     rn-view rnms-modal-selector rnp-button rnp-chip
                     rnp-dialog rnp-dialog-actions rnp-dialog-content
                     rnp-dialog-icon rnp-dialog-title rnp-divider rnp-modal
                     rnp-snackbar rnp-text rnp-text-input rnp-text-input-icon
                     tertiary-color]]
            
            [onekeepass.ios.autofill.events.common :as cmn-events]
            ))


(defn message-dialog
  "Called to show an error or a general message
    The value of key 'category' determines whether it is error or message
     "
  [{:keys [dialog-show title category message]}] 
  (let [error? (= category :error)
        title-txt title #_(if error? (lstr-error-dlg-title title) (lstr-msg-dlg-title title))
        msg-txt message #_(if error? (lstr-error-dlg-text message) (lstr-msg-dlg-text message))]
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
      [rnp-button {:mode "text" :onPress cmn-events/close-message-dialog} "Close" #_(lstr-bl "close")]]]))


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



(defn menu-action-factory
  "Wraps the hide-menu-action and returns a factory which itself returns another factory
  This inner factory can be used in menu items' onPress call
 "
  [hide-menu-action]
  (fn [action & action-args]
    (fn [_e]
      (hide-menu-action)
      (apply action action-args))))