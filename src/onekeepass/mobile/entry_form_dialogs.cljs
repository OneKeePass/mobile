
(ns
 onekeepass.mobile.entry-form-dialogs
  (:require-macros [onekeepass.mobile.okp-macros
                    :refer  [as-map]])
  (:require [clojure.string :as str]
            [onekeepass.mobile.common-components :as cc :refer [confirm-dialog
                                                                confirm-dialog-with-lstr]]
            [onekeepass.mobile.events.dialogs :as dlg-events]
            [onekeepass.mobile.events.entry-form :as form-events]
            [onekeepass.mobile.events.scan-otp-qr :as scan-qr-events]
            [onekeepass.mobile.rn-components
             :as rnc
             :refer [cust-dialog rn-view rnp-button rnp-checkbox
                     rnp-dialog-actions rnp-dialog-content rnp-dialog-title
                     rnp-helper-text rnp-text rnp-text-input
                     rnp-touchable-ripple]]
            [onekeepass.mobile.translation :refer [lstr-bl lstr-dlg-text
                                                   lstr-dlg-title lstr-l]]
            [reagent.core :as r]
            [onekeepass.mobile.background :as bg]))

;;;;;;;;;;;;;

(defn confirm-delete-otp-field-dialog []
  [confirm-dialog-with-lstr @(dlg-events/confirm-delete-otp-field-dialog-data)])


(defn confirm-delete-otp-field-show [section-name key]
  ;; We pass translation keys for title, confirm-text and for button labels as expected
  ;; by confirm-dialog-with-lstr
  (dlg-events/confirm-delete-otp-field-dialog-show-with-state
   {:title "deleteOtp"
    :confirm-text "deleteOtp"
    :call-on-ok-fn #(form-events/entry-form-delete-otp-field section-name key)
    :actions [{:label "yes"
               :on-press dlg-events/confirm-delete-otp-field-dialog-on-ok}
              {:label "no"
               :on-press dlg-events/confirm-delete-otp-field-dialog-close}]}))

;; Called to create a dialog and the dialog is shown if the 'show' is true in 
;; the dialog data
(defn setup-otp-action-dialog []
  [confirm-dialog-with-lstr @(dlg-events/setup-otp-action-dialog-data)])

(declare otp-settings-dialog-show)

(defn scan-qr-action [{:keys [section-name field-name standard-field] :as otp-field-m}]
  (if standard-field
    (scan-qr-events/initiate-scan-qr otp-field-m)
    (otp-settings-dialog-show section-name field-name standard-field :scan-qr)))

;; Called to show the dialog created in setup-otp-action-dialog fn above
(defn setup-otp-action-dialog-show [section-name field-name standard-field]
  ;; We pass translation keys for title, confirm-text and for button labels
  (dlg-events/setup-otp-action-dialog-show-with-state
   {:title "setupOtp"
    :confirm-text "setupOtp"
    :section-name section-name
    :show-action-as-vertical true
    :actions [{:label "scanQRcode"
               :disabled (bg/is-rn-native-camera-vison-disabled)
               :on-press (fn []
                           (scan-qr-action (as-map [section-name field-name standard-field]))
                           (dlg-events/setup-otp-action-dialog-close))}
              {:label "enterManually"
               :on-press (fn []
                           (otp-settings-dialog-show section-name field-name standard-field :manual)
                           (dlg-events/setup-otp-action-dialog-close))}
              {:label "cancel"
               :on-press dlg-events/setup-otp-action-dialog-close}]}))

(defn otp-settings-dialog
  "Shown when user wants to enter secrect code or scan QR
   In case of 'Scan QR' option, this dialog is shown only for the user to enter field-name when user
   wants to add additional otp field
  "
  [{:keys [dialog-show
           standard-field
           _section-name
           field-name
           code-entry-type
           secret-or-url
           error-fields
           _api-error-text]}]

  (let [error (boolean (seq error-fields))]
    [cust-dialog {:style {} :dismissable true :visible dialog-show :onDismiss #()}
     
     [rnp-dialog-title {:ellipsizeMode "tail" :numberOfLines 1}
      (if (=  code-entry-type :scan-qr) 
        (lstr-l "scanQRcode") 
        (lstr-l "enterCode"))]
     
     [rnp-dialog-content
      [rn-view {:flexDirection "column"}
       (when-not standard-field
         [:<> ;; Needs to :<> so that a combine comp is returned the condition evaluates
          [rnp-text-input {:label (lstr-l "fieldName")
                           :autoCapitalize "none"
                           :defaultValue field-name
                           :onChangeText #(dlg-events/otp-settings-dialog-update [:field-name %])}]
          (when error
            [rnp-helper-text {:type "error" :visible error}
             (get error-fields :field-name)])

          [rn-view {:style {:height 10}}]])

       (when  (= code-entry-type :manual)
         [:<>
          [rnp-text-input {:label (lstr-l "secretCodeOrUrl")
                           :autoCapitalize "none"
                           :defaultValue secret-or-url
                           :onChangeText #(dlg-events/otp-settings-dialog-update [:secret-or-url %])}]
          (when error
            [rnp-helper-text {:type "error" :visible error}
             (get error-fields :secret-or-url)])])]]

     [rnp-dialog-actions
      [rnp-button {:mode "text"
                   :onPress dlg-events/otp-settings-dialog-close} (lstr-bl "cancel")]
      [rnp-button {:mode "text"
                   :onPress dlg-events/otp-settings-dialog-complete-ok} (lstr-bl "ok")]]]))

(defn otp-settings-dialog-show
  "Called to show the otp settings dialog with initial values
   This dialog is shown when user wants to enter the code manually. 
   This is also called before scanning code if the otp is an additional custom field 
   code-entry-type is one of :scan-qr or :manual
   "
  [section-name field-name standard-field code-entry-type]
  (dlg-events/otp-settings-dialog-show-with-state (as-map [section-name field-name standard-field code-entry-type])))

;;;;;;;;;;;;;;;
(defn add-modify-section-name-dialog [{:keys [dialog-show
                                              mode
                                              section-name
                                              error-fields] :as dialog-data}]
  (let [error (boolean (seq error-fields))]
    [cust-dialog {:style {} :dismissable true :visible dialog-show :onDismiss #()}
     [rnp-dialog-title {:ellipsizeMode "tail" :numberOfLines 1}
      (if (= mode :add) (lstr-dlg-title 'newSectionName) (lstr-dlg-title 'modifySectionName))]
     [rnp-dialog-content
      [rn-view {:flexDirection "column"}
       [rnp-text-input {:label (lstr-l 'sectionName)
                        :defaultValue section-name
                        :onChangeText #(form-events/section-name-dialog-update :section-name %)}]
       (when error
         [rnp-helper-text {:type "error" :visible error}
          (get error-fields :section-name)])]]
     [rnp-dialog-actions]
     [rnp-dialog-actions
      [rnp-button {:mode "text"
                   :onPress #(form-events/section-name-dialog-update :dialog-show false)} (lstr-bl "cancel")]
      [rnp-button {:mode "text"
                   :onPress #(form-events/section-name-add-modify dialog-data)} (lstr-bl "ok")]]]))

(defn delete-field-confirm-dialog [{:keys [dialog-show]} actions]
  [cust-dialog {:style {} :dismissable true :visible dialog-show}
   [rnp-dialog-title {:ellipsizeMode "tail" :numberOfLines 1}
    (lstr-dlg-title 'deleteField)]

   [rnp-dialog-content
    [rnp-text
     (lstr-dlg-text 'deleteField)]]

   [rnp-dialog-actions
    (for [{:keys [label on-press]} actions]
      ^{:key label} [rnp-button {:mode "text"
                                 :on-press on-press} (lstr-bl label)])]])

(defn add-modify-section-field-dialog [{:keys [dialog-show
                                               section-name
                                               field-name
                                               protected
                                               required
                                               _data-type
                                               mode
                                               error-fields]
                                        :as m}]

  (let [error (boolean (seq error-fields))
        ok-fn (fn [_e]
                (if (= mode :add)
                  (form-events/section-field-add
                   (select-keys m [:field-name :protected :required :section-name :data-type]))
                  (form-events/section-field-modify
                   (select-keys m [:field-name :current-field-name :data-type :protected :required :section-name]))))]

    [cust-dialog {:style {} :dismissable true :visible dialog-show
                  :onDismiss #(form-events/close-section-field-dialog)}
     [rnp-dialog-title {:ellipsizeMode "tail"
                        :numberOfLines 1} (if (= mode :add)
                                            (lstr-dlg-title 'addSectionField {:section-name section-name})
                                            (lstr-dlg-title 'modifySectionField {:section-name section-name}))]
     [rnp-dialog-content
      [rn-view {:flexDirection "column"}

       [rnp-text-input {:label (lstr-l 'fieldName)
                        :defaultValue field-name
                        :onChangeText #(form-events/section-field-dialog-update :field-name %)}]
       (when error
         [rnp-helper-text {:type "error" :visible error}
          (get error-fields field-name)])

       [rn-view {:flexDirection "column" :style {}}
        [rn-view {:style {:height 15}}]
        [rnp-touchable-ripple {:style {}
                               :onPress #(form-events/section-field-dialog-update :protected (not protected))}
         [rn-view {:flexDirection "row"
                   :style {:alignItems "center" :justifyContent "center"}}
          [rnp-text {:style {}} (lstr-l 'protected)]
          [rn-view {:pointerEvents "none"}
           [rnp-checkbox {:status (if protected "checked" "unchecked")}]]]]]

       #_[rn-view {:flexDirection "row"}
          [rnp-touchable-ripple {:style {:width "45%"}
                                 :onPress #(form-events/section-field-dialog-update :protected (not protected))}
           [rn-view {:flexDirection "row" :style {:alignItems "center" :justifyContent "space-between"}}
            [rnp-text "Protected"]
            [rn-view {:pointerEvents "none"}
             [rnp-checkbox {:status (if protected "checked" "unchecked")}]]]]
          [rn-view {:style {:width "10%"}}]
          [rnp-touchable-ripple {:style {:width "45%"}
                                 :onPress #(form-events/section-field-dialog-update :required (not required))}
           [rn-view {:flexDirection "row" :style {:alignItems "center" :justifyContent "space-between"}}
            [rnp-text "Required"]
            [rn-view {:pointerEvents "none"}
             [rnp-checkbox {:status (if required "checked" "unchecked")}]]]]]]]

     [rnp-dialog-actions
      [rnp-button {:mode "text" :onPress #(form-events/close-section-field-dialog)} (lstr-bl "cancel")]
      [rnp-button {:mode "text" :onPress ok-fn} (lstr-bl "ok")]]]))

(defn history-entry-delete-dialog []
  (let [entry-uuid @(form-events/entry-form-uuid)
        index @(form-events/history-entry-selected-index)]
    [confirm-dialog {:dialog-show @(form-events/history-entry-delete-flag)
                     :title (lstr-dlg-title 'deleteHistoryEntry)
                     :confirm-text (lstr-dlg-text 'deleteHistoryEntry)
                     :actions [{:label (lstr-bl "yes")
                                :on-press #(form-events/delete-history-entry-by-index
                                            entry-uuid index)}
                               {:label (lstr-bl "no")
                                :on-press form-events/close-history-entry-delete-confirm-dialog}]}]))

(defn history-entry-restore-dialog []
  [confirm-dialog {:dialog-show @(form-events/history-entry-restore-flag)
                   :title (lstr-dlg-title 'restoreHistoryEntry)
                   :confirm-text (lstr-dlg-text 'restoreHistoryEntry)
                   :actions [{:label (lstr-bl "yes")
                              :on-press form-events/restore-entry-from-history}
                             {:label (lstr-bl "no")
                              :on-press form-events/close-history-entry-restore-confirm-dialog}]}])

(def delete-attachment-confirm-dialog-data (r/atom {:dialog-show false
                                                    :title nil
                                                    :confirm-text nil
                                                    :call-on-ok-fn #(println %)}))

(def delete-attachment-dialog-info (cc/confirm-dialog-factory delete-attachment-confirm-dialog-data))

(defn show-delete-attachment-dialog [title confirm-text call-on-ok-fn]
  (swap! delete-attachment-confirm-dialog-data assoc
         :title title :confirm-text confirm-text :call-on-ok-fn call-on-ok-fn)
  ((:show delete-attachment-dialog-info)))

;; rename dialog
(def rename-attachment-name-dialog-data (r/atom {:dialog-show false
                                                 :name nil
                                                 :data-hash nil
                                                 :error-text nil}))

(defn show-rename-attachment-name-dialog [name data-hash]
  (swap! rename-attachment-name-dialog-data assoc :dialog-show true :name name :data-hash data-hash))

(defn close-rename-attachment-name-dialog []
  (reset! rename-attachment-name-dialog-data {}))

(defn change-attachment-name [value]
  (swap! rename-attachment-name-dialog-data assoc :name value)
  (if  (str/blank? value)
    (swap! rename-attachment-name-dialog-data assoc :error-text "Valid name is required")
    (swap! rename-attachment-name-dialog-data assoc :error-text nil)))

(defn rename-attachment-name-dialog [{:keys [dialog-show
                                             name
                                             data-hash
                                             error-text]}]
  [cust-dialog {:style {} :dismissable true :visible dialog-show :onDismiss #()}
   [rnp-dialog-title {:ellipsizeMode "tail" :numberOfLines 1}
    (lstr-dlg-title 'changeAttachmentName)]
   [rnp-dialog-content
    [rn-view {:flexDirection "column"}
     [rnp-text-input {:label (lstr-l 'name)
                      :defaultValue name
                      :onChangeText change-attachment-name}]
     (when error-text
       [rnp-helper-text {:type "error" :visible true}
        error-text])]]

   [rnp-dialog-actions
    [rnp-button {:mode "text"
                 :onPress close-rename-attachment-name-dialog} (lstr-bl "cancel")]
    [rnp-button {:mode "text"
                 :disabled (not (nil? error-text))
                 :onPress (fn []
                            (form-events/rename-attachment name data-hash)
                            (close-rename-attachment-name-dialog))} (lstr-bl "ok")]]])
