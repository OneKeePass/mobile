(ns onekeepass.mobile.rs-config-form
  "Shows sftp or webdav a form when a new connection is created or updated"
  (:require [onekeepass.mobile.background :refer [is-iOS]]
            [onekeepass.mobile.common-components :as cc :refer [select-field]]
            [onekeepass.mobile.constants :refer [ICON-EYE ICON-EYE-OFF
                                                 ICON-FOLDER]]
            [onekeepass.mobile.events.remote-storage :as rs-events]
            [onekeepass.mobile.rn-components :as rnc :refer [modal-selector-colors
                                                             page-background-color
                                                             rn-keyboard-avoiding-view
                                                             rn-scroll-view
                                                             rn-view
                                                             rnp-button
                                                             rnp-helper-text
                                                             rnp-text
                                                             rnp-text-input
                                                             rnp-text-input-icon]]
            [onekeepass.mobile.translation :refer [lstr-bl lstr-cv lstr-l]]
            [onekeepass.mobile.utils :refer [str->int]]
            [reagent.core :as r]))

(def sftp-logon-types [{:key "password" :label "password"} {:key "privateKey" :label "privateKey"}])

(defn form-header [title]
  [rn-view  {:style {:flexDirection "row"
                     :width "100%"
                     :margin-top 0
                     :min-height 38}}
   [rnp-text {:style {:alignSelf "center"
                      ;;:width "85%"
                      :text-align "center"
                      :padding-left 5} :variant "titleSmall"} title]])

(def form-style {:padding-top 5 :padding-right 5 :padding-left 5 :padding-bottom 10
                 :margin-left 5 :margin-right 5 :margin-bottom 5
                 :borderWidth .20 :borderRadius 4})

(defn error-text [errors kw-key]
  (when-not (nil? (kw-key errors))
    [rnp-helper-text {:type "error" :visible true} (kw-key errors)]))

;; Note: We need to use :defaultValue instead of :value with rnp-text-input
;; See detail comments below and also in entry-form 

(defn password-field [password password-visible tr-label errors edit]
  [:<>
   [rnp-text-input {:style {}
                    :label tr-label
                    :editable edit
                    ;; Need to use defaultValue (iOS only) in addition to :value 
                    ;; to show the password value when only in view mode
                    ;; Not sure only for this happens for this. May be the way reagent component 
                    ;; 'password-field' is used ?
                    :defaultValue password

                    ;; In case of Android, when we use ':vaue' and when we try 
                    ;; insert a charater in a text, the cursor moves back and confusing
                    ;; Using only ':defaultValue' solves the issue
                    ;; Also using only ':defaultValue' for both Android and iOS
                    ;;:vaue password 
                    :secureTextEntry (not password-visible)
                    :right (r/as-element
                            [rnp-text-input-icon
                             {:icon  (if password-visible ICON-EYE ICON-EYE-OFF)
                              :onPress #(rs-events/remote-storage-connection-form-data-update
                                         :sftp
                                         :password-visible (not password-visible))}])
                    :onChangeText #(rs-events/remote-storage-connection-form-data-update :sftp :password %)}]
   [error-text errors :password]])

(defn sftp-connection-config-form []
  (let [kw-type :sftp
        {:keys [name
                host
                port
                user-name
                password
                private-key-file-name
                edit
                logon-type
                password-visible]} @(rs-events/remote-storage-connection-form-data kw-type)
        errors @(rs-events/remote-storage-connection-form-errors kw-type)]
    [rn-view {:flex 1 :backgroundColor @page-background-color}  ;;
     [form-header (if edit (lstr-l 'newConnection) (lstr-l 'viewConnection))]
     [rn-view {:style form-style}
      [rnp-text-input {:style {}
                       :label (lstr-l 'name)
                       :editable edit
                       ;; onChangeText should be a fn that accepts the changed text, when we use the prop :value 
                       ;; otherwise the label keeps on showing even if the value has non nil value. Then 
                       ;; we may use defaultValue prop which hides the label once data is entered
                       ;; Also see comment in mobile/src/onekeepass/mobile/settings.cljs 
                       ;; Except few places, generally prop defaultValue is used 
                       ;; See the use of defaultValue and comment there
                       
                       ;; In case of Android, when we use ':vaue' and when we try 
                       ;; insert a charater in a text, the cursor moves back and confusing
                       ;; Using only ':defaultValue' solves the issue
                       ;; Also using only ':defaultValue' for both Android and iOS 
                       ;; :value name
                       
                       :defaultValue name
                       :onChangeText #(rs-events/remote-storage-connection-form-data-update kw-type :name %)}]
      [error-text errors :name]
      [rnp-text-input {:style {}
                       :label (lstr-l 'host)
                       :editable edit
                       ;; See fn passed in onChangeText which uses 'host' field directly instead of using % in fn
                       ;;:value host
                       :defaultValue host
                       :onChangeText #(rs-events/remote-storage-connection-form-data-update kw-type :host %)}]
      [error-text errors :host]

      [rnp-text-input {:style {}
                       :label (lstr-l 'port)
                       :keyboardType "numeric"
                       :disabled (not edit)
                       :value (str port)
                       :onChangeText #(rs-events/remote-storage-connection-form-data-update kw-type :port (str->int %))}]
      [error-text errors :port]

      [rnp-text-input {:style {}
                       :label (lstr-l 'userName)
                       :editable edit
                       ;;:value user-name
                       :defaultValue user-name
                       :onChangeText #(rs-events/remote-storage-connection-form-data-update kw-type :user-name %)}]

      [error-text errors :user-name]]

     [rn-view {:style form-style}
      [select-field {:text-label (lstr-l 'selectSftpLogonType)
                     :options sftp-logon-types
                     :disabled (not edit)
                     :value (lstr-cv logon-type)
                     :label-extractor-fn cc/select-field-tr-key-label-extractor
                     :text-input-style {:background-color @(:background-color modal-selector-colors)}
                     :on-change (fn [option]
                                  (rs-events/remote-storage-connection-form-data-update kw-type :logon-type (.-key option)))}]]

     (if (= logon-type "password")
       [rn-view {:style form-style}
        [password-field password password-visible (lstr-l 'password) errors edit]]

       [rn-view {:style form-style}
        [rnp-text-input {:style {}
                         :editable false
                         :label (lstr-l 'privateKey)
                         :value private-key-file-name
                         :right (when edit
                                  (r/as-element [rnp-text-input-icon
                                                 {:icon ICON-FOLDER
                                                  :onPress #(rs-events/pick-private-key-file)}]))
                         :onPressIn (when edit
                                      #(rs-events/pick-private-key-file))}]

        [error-text errors :private-key-file-name]
        [password-field password password-visible (lstr-l 'privateKeyPassphrase) errors edit]])

     (when edit
       [rn-view {:style {:margin-top 20 :margin-bottom 20 :align-items "center"}}
        [rnp-button {:style {:width "50%"}
                            ;;:labelStyle {:fontWeight "bold"}
                     :mode "contained"
                     :on-press #(rs-events/remote-storage-new-config-connect-and-save kw-type)}
         (lstr-bl 'connectAndSave)]])]))

(defn webdav-connection-config-form []
  (let [kw-type :webdav
        {:keys [name
                root-url
                user-name
                password
                password-visible
                edit]} @(rs-events/remote-storage-connection-form-data kw-type)
        errors @(rs-events/remote-storage-connection-form-errors kw-type)]
    [rn-view {:flex 1 :backgroundColor @page-background-color}  ;;
     [form-header (if edit (lstr-l 'newConnection) (lstr-l 'viewConnection))]
     [rn-view {:style form-style}
      [rnp-text-input {:style {}
                       :editable edit
                       :autoCapitalize "none"
                       :autoCorrect false
                       :label (lstr-l 'name)
                       ;;:value name
                       :defaultValue name
                       :onChangeText #(rs-events/remote-storage-connection-form-data-update kw-type :name %)}]
      [error-text errors :name]

      [rnp-text-input {:style {}
                       :label (lstr-l 'rootUrl)
                       :editable edit
                       :placeholder "e.g https://www.mywebdav.com:8080"
                       :autoCapitalize "none"
                       :autoCorrect false
                       ;;:value root-url
                       :defaultValue root-url
                       :onChangeText #(rs-events/remote-storage-connection-form-data-update kw-type :root-url %)}]

      [error-text errors :root-url]

      [rnp-text-input {:style {}
                       :label (lstr-l 'userName)
                       :editable edit
                       :autoCapitalize "none"
                       :autoCorrect false
                       ;;:value user-name
                       :defaultValue user-name
                       :onChangeText #(rs-events/remote-storage-connection-form-data-update kw-type :user-name %)}]

      [error-text errors :user-name]

      [rnp-text-input {:style {}
                       :label (lstr-l 'password)
                       :editable edit
                       :autoCapitalize "none"
                       :autoCorrect false
                       ;;:value password
                       :defaultValue password
                       :secureTextEntry (not password-visible)
                       :right (r/as-element
                               [rnp-text-input-icon
                                {:icon  (if password-visible ICON-EYE ICON-EYE-OFF)
                                 :onPress #(rs-events/remote-storage-connection-form-data-update
                                            kw-type
                                            :password-visible (not password-visible))}])
                       :onChangeText #(rs-events/remote-storage-connection-form-data-update kw-type :password %)}]
      [error-text errors :password]]



     (when edit
       [rn-view {:style {:margin-top 20 :margin-bottom 20 :align-items "center"}}
        [rnp-button {:style {:width "50%"}
                                 ;;:labelStyle {:fontWeight "bold"}
                     :mode "contained"
                     :on-press #(rs-events/remote-storage-new-config-connect-and-save kw-type)}
         (lstr-bl 'connectAndSave)]])]))

(defn main-content []
  (let [kw-type @(rs-events/remote-storage-current-rs-type)]
    (if (= :sftp kw-type)
      [sftp-connection-config-form]
      [webdav-connection-config-form])))

(defn connection-config-form []
  [rn-keyboard-avoiding-view {:style {:flex 1}
                              :behavior (if (is-iOS) "padding" nil)}
   [rn-scroll-view {:contentContainerStyle {:flexGrow 1 :background-color @page-background-color}}
    [main-content]]])