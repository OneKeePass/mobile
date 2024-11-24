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
            [onekeepass.mobile.translation :refer [lstr-bl lstr-cv lstr-l
                                                   lstr-mt]]
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

(defn password-field [password password-visible tr-label errors]
  [:<>
   [rnp-text-input {:style {}
                    :label tr-label
                    :vaue password
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
  (let [{:keys [name
                host
                port
                user-name
                logon-type
                password
                password-visible
                _private-key-full-file-name
                private-key-file-name]} @(rs-events/remote-storage-connection-form-data :sftp)
        errors @(rs-events/remote-storage-connection-form-errors :sftp)]

    [rn-view {:flex 1 :backgroundColor @page-background-color}  ;;
     [form-header (lstr-l 'newConnection)]
     [rn-view {:style form-style}
      [rnp-text-input {:style {}
                       :label (lstr-l 'name)

                       ;; onChangeText should be a fn that accepts the changed text, when we use the prop :value 
                       ;; otherwise the label keeps on showing even if the value has non nil value. Then 
                       ;; we may use defaultValue prop which hides the label once data is entered
                       ;; Also see comment in mobile/src/onekeepass/mobile/settings.cljs 
                       ;; Except few places, generally prop defaultValue is used 
                       ;; See the use of defaultValue and comment there
                       :value name
                       :onChangeText #(rs-events/remote-storage-connection-form-data-update :sftp :name %)}]
      [rnp-text-input {:style {}
                       :label (lstr-l 'host)
                       ;; See fn passed in onChangeText which uses 'host' field directly instead of using % in fn
                       :value host
                       :onChangeText #(rs-events/remote-storage-connection-form-data-update :sftp :host %)}]
      [error-text errors :host]

      [rnp-text-input {:style {}
                       :label (lstr-l 'port)
                       :keyboardType "numeric"
                       :value (str port)
                       :onChangeText #(rs-events/remote-storage-connection-form-data-update :sftp :port (str->int %))}]
      [error-text errors :port]

      [rnp-text-input {:style {}
                       :label (lstr-l 'userName)
                       :value user-name
                       :onChangeText #(rs-events/remote-storage-connection-form-data-update :sftp :user-name %)}]

      [error-text errors :user-name]]

     [rn-view {:style form-style}
      [select-field {:text-label "Select Logon Type"
                     :options sftp-logon-types
                     :value (lstr-cv logon-type)
                     :label-extractor-fn cc/select-field-tr-key-label-extractor
                     :text-input-style {:background-color @(:background-color modal-selector-colors)}
                     :on-change (fn [option]
                                  (rs-events/remote-storage-connection-form-data-update :sftp :logon-type (.-key option)))}]]

     (if (= logon-type "password")
       [rn-view {:style form-style}
        [password-field password password-visible (lstr-l 'password) errors]]

       [rn-view {:style form-style}
        [rnp-text-input {:style {}
                         :editable false
                         :label (lstr-l 'privateKey)
                         :value private-key-file-name
                         :right (r/as-element [rnp-text-input-icon
                                               {:icon ICON-FOLDER
                                                :onPress #(rs-events/pick-private-key-file)}])
                         :onPressIn #(rs-events/pick-private-key-file)}]

        [error-text errors :private-key-file-name]
        [password-field password password-visible "Private Key Passphrase" errors]])

     [rn-view {:style {:margin-top 20 :margin-bottom 20 :align-items "center"}}
      [rnp-button {:style {:width "50%"}
                     ;;:labelStyle {:fontWeight "bold"}
                   :mode "contained"
                   :on-press #(rs-events/remote-storage-new-config-connect-and-save :sftp)}
       "Connect & Save"]]]))

(defn webdav-connection-config-form []
  (let [{:keys [name
                root-url
                user-name
                password]} @(rs-events/remote-storage-connection-form-data :webdav)]

    [rn-view {:flex 1 :backgroundColor @page-background-color}  ;;
     [form-header (lstr-l 'connectionConfig)]
     [rn-view {:style form-style}
      [rnp-text-input {:style {}
                       :editable false
                       :label (lstr-l 'name)
                       :value name}]
      [rnp-text-input {:style {}
                       :label (lstr-l 'rootUrl)
                       :value root-url
                       :onChangeText #()}]
      #_(when-not (nil? (:iterations errors))
          [rnp-helper-text {:type "error" :visible true} (:iterations errors)])

      [rnp-text-input {:style {}
                       :label (lstr-l 'userName)
                       :value user-name
                       :onChangeText #()}]

      [rnp-text-input {:style {}
                       :label (lstr-l 'password)
                       :value password
                       :onChangeText #() #_#(stgs-events/db-settings-data-field-update [:kdf :Argon2 :memory] (str->int %))}]
      #_(when-not (nil? (:memory errors))
          [rnp-helper-text {:type "error" :visible true} (:memory errors)])

      #_(when-not (nil? (:parallelism errors))
          [rnp-helper-text {:type "error" :visible true} (:parallelism errors)])]]))

(defn main-content []
  (let [kw-type @(rs-events/remote-storage-current-form-type)]
    (if (= :sftp kw-type)
      [sftp-connection-config-form]
      [webdav-connection-config-form])))

(defn connection-config-form []
  [rn-keyboard-avoiding-view {:style {:flex 1}
                              :behavior (if (is-iOS) "padding" nil)}
   [rn-scroll-view {:contentContainerStyle {:flexGrow 1 :background-color @page-background-color}}
    [main-content]]])