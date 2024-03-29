(ns
 onekeepass.mobile.entry-form-dialogs
  (:require [reagent.core :as r]
            [onekeepass.mobile.rn-components
             :as rnc
             :refer [lstr
                     icon-color
                     on-primary-color
                     primary-container-color
                     page-background-color
                     appbar-text-color
                     rn-keyboard
                     dots-icon-name
                     page-title-text-variant
                     rn-view
                     rn-scroll-view
                     rn-keyboard-avoiding-view
                     rnp-chip
                     rnp-checkbox
                     rnp-divider
                     rnp-menu
                     rnp-menu-item
                     rnp-text-input
                     rnp-text
                     rnp-touchable-ripple
                     rnp-helper-text
                     rnp-text-input-icon
                     rnp-icon-button
                     rnp-button
                     rnp-portal
                     cust-dialog
                     rnp-dialog-title
                     rnp-dialog-content
                     rnp-dialog-actions
                     rnp-list-item
                     rn-section-list
                     rnp-list-icon]]
            [onekeepass.mobile.common-components :as cc :refer [confirm-dialog
                                                                confirm-dialog-with-lstr
                                                                select-field
                                                                select-tags-dialog]]
            [onekeepass.mobile.constants :as const]
            [onekeepass.mobile.icons-list :as icons-list]
            [onekeepass.mobile.utils :as u]
            [onekeepass.mobile.date-utils :refer [utc-str-to-local-datetime-str]]
            [clojure.string :as str]
            [onekeepass.mobile.events.entry-form :as form-events]
            [onekeepass.mobile.events.password-generator :as pg-events]
            [onekeepass.mobile.background :refer [is-iOS is-Android]]
            [onekeepass.mobile.events.dialogs :as dlg-events]
            [onekeepass.mobile.events.common :as cmn-events]))

;;;;;;;;;;;;;
#_(defn confirm-delete-otp-field-dialog []
    [confirm-dialog (merge @(dlg-events/confirm-delete-otp-field-dialog-data)
                           {:title "Delete one time password?"
                            :confirm-text "Are you sure you want to delete this permanently?"
                            :actions [{:label (lstr "button.labels.yes")
                                       :on-press dlg-events/confirm-delete-otp-field-dialog-on-ok}
                                      {:label (lstr "button.labels.no")
                                       :on-press dlg-events/confirm-delete-otp-field-dialog-close}]})])
#_{:clj-kondo/ignore [:unresolved-var]}
(defn confirm-delete-otp-field-dialog []
  [confirm-dialog-with-lstr @(dlg-events/confirm-delete-otp-field-dialog-data)])

#_{:clj-kondo/ignore [:unresolved-var]}
(defn confirm-delete-otp-field-show [section-name key]
  (dlg-events/confirm-delete-otp-field-dialog-show-with-state {:title "Delete one time password?"
                                                       :confirm-text "Are you sure you want to delete this permanently?"
                                                       :call-on-ok-fn #(form-events/entry-form-delete-otp-field section-name key)
                                                       :actions [{:label "button.labels.yes"
                                                                  :on-press dlg-events/confirm-delete-otp-field-dialog-on-ok}
                                                                 {:label "button.labels.no"
                                                                  :on-press dlg-events/confirm-delete-otp-field-dialog-close}]}))

#_{:clj-kondo/ignore [:unresolved-var]}
(defn setup-otp-action-dialog []
  [confirm-dialog-with-lstr @(dlg-events/setup-otp-action-dialog-data)])

#_{:clj-kondo/ignore [:unresolved-var]}
(defn setup-otp-action-dialog-show [section-name]
  ;;#_{:clj-kondo/ignore [:unresolved-var]}
  (dlg-events/setup-otp-action-dialog-show-with-state {:title "Set up OTP"
                                                       :confirm-text "You can set up TOTP by scanning a QR code or manually entering the secret or an OTPAuth url"
                                                       :section-name section-name
                                                       :actions [{:label "button.labels.cancel"
                                                                  :on-press dlg-events/setup-otp-action-dialog-close}
                                                                 {:label "Scan QR Code"
                                                                  :on-press dlg-events/setup-otp-action-dialog-close}
                                                                 {:label "Enter Manually"
                                                                  :on-press dlg-events/setup-otp-action-dialog-close}]}))

(defn setup-otp-manual-dialog [{:keys [dialog-show
                                       standard-field
                                       section-name
                                       field-name
                                       secret-or-url
                                       error-fields
                                       api-error-text
                                       ]}]
  
  (let [error (boolean (seq error-fields))]
    [cust-dialog {:style {} :dismissable true :visible dialog-show :onDismiss #()}
     [rnp-dialog-title {:ellipsizeMode "tail" :numberOfLines 1} 
      "Scan QR Code"
      ]
     [rnp-dialog-content
      [rn-view {:flexDirection "column"}
       [rnp-text-input {:label "Secret code or Url"
                        :defaultValue section-name
                        :onChangeText #(form-events/section-name-dialog-update :section-name %)}]
       (when error
         [rnp-helper-text {:type "error" :visible error}
          (get error-fields :section-name)])]]
     
     ]
    )
  
  )

;;;;;;;;;;;;;;;
(defn add-modify-section-name-dialog [{:keys [dialog-show
                                              mode
                                              section-name
                                              error-fields] :as dialog-data}]
  (let [error (boolean (seq error-fields))]
    [cust-dialog {:style {} :dismissable true :visible dialog-show :onDismiss #()}
     [rnp-dialog-title {:ellipsizeMode "tail" :numberOfLines 1}
      (if (= mode :add) "New section name" "Modify section name")]
     [rnp-dialog-content
      [rn-view {:flexDirection "column"}
       [rnp-text-input {:label "Section name"
                        :defaultValue section-name
                        :onChangeText #(form-events/section-name-dialog-update :section-name %)}]
       (when error
         [rnp-helper-text {:type "error" :visible error}
          (get error-fields :section-name)])]]
     [rnp-dialog-actions]
     [rnp-dialog-actions
      [rnp-button {:mode "text"
                   :onPress #(form-events/section-name-dialog-update :dialog-show false)} (lstr "button.labels.cancel")]
      [rnp-button {:mode "text"
                   :onPress #(form-events/section-name-add-modify dialog-data)} (lstr "button.labels.ok")]]]))

(defn delete-field-confirm-dialog [{:keys [dialog-show]} actions]
  [cust-dialog {:style {} :dismissable true :visible dialog-show}
   [rnp-dialog-title {:ellipsizeMode "tail" :numberOfLines 1} "Delete field"]
   [rnp-dialog-content
    [rnp-text "Are you sure you want to delete this field permanently?"]]
   [rnp-dialog-actions
    (for [{:keys [label on-press]}  actions]
      ^{:key label} [rnp-button {:mode "text"
                                 :on-press on-press} label])]])

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
                                            (str "Add field in " section-name)
                                            (str "Modify field in " section-name))]
     [rnp-dialog-content
      [rn-view {:flexDirection "column"}

       [rnp-text-input {:label "Field name" :defaultValue field-name
                        :onChangeText #(form-events/section-field-dialog-update :field-name %)}]
       (when error
         [rnp-helper-text {:type "error" :visible error}
          (get error-fields field-name)])

       [rn-view {:flexDirection "row"}
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
      [rnp-button {:mode "text" :onPress #(form-events/close-section-field-dialog)} "Cancel"]
      [rnp-button {:mode "text" :onPress ok-fn} "Ok"]]]))

(defn history-entry-delete-dialog []
  (let [entry-uuid @(form-events/entry-form-uuid)
        index @(form-events/history-entry-selected-index)]
    [confirm-dialog {:dialog-show @(form-events/history-entry-delete-flag)
                     :title "Delete history entry"
                     :confirm-text "Are you sure you want to delete this history entry permanently?"
                     :actions [{:label (lstr "button.labels.yes")
                                :on-press #(form-events/delete-history-entry-by-index
                                            entry-uuid index)}
                               {:label (lstr "button.labels.no")
                                :on-press form-events/close-history-entry-delete-confirm-dialog}]}]))

(defn history-entry-restore-dialog []
  [confirm-dialog {:dialog-show @(form-events/history-entry-restore-flag)
                   :title "Restore from history entry"
                   :confirm-text "Are you sure you want to restore the entry from this history entry?"
                   :actions [{:label (lstr "button.labels.yes")
                              :on-press form-events/restore-entry-from-history}
                             {:label (lstr "button.labels.no")
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
    "Change name"]
   [rnp-dialog-content
    [rn-view {:flexDirection "column"}
     [rnp-text-input {:label "Name"
                      :defaultValue name
                      :onChangeText change-attachment-name}]
     (when error-text
       [rnp-helper-text {:type "error" :visible true}
        error-text])]]

   [rnp-dialog-actions
    [rnp-button {:mode "text"
                 :onPress close-rename-attachment-name-dialog} (lstr "button.labels.cancel")]
    [rnp-button {:mode "text"
                 :disabled (not (nil? error-text))
                 :onPress (fn []
                            (form-events/rename-attachment name data-hash)
                            (close-rename-attachment-name-dialog))} (lstr "button.labels.ok")]]])
