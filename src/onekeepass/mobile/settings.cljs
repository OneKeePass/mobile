(ns onekeepass.mobile.settings
  {:clj-kondo/config '{:linters {:unresolved-symbol {:level :off}}}}
  (:require
   [reagent.core :as r]
   [onekeepass.mobile.rn-components :as rnc :refer [lstr

                                                    appbar-text-color
                                                    page-background-color
                                                    inverse-onsurface-color

                                                    page-title-text-variant
                                                    rn-view
                                                    rn-safe-area-view
                                                    rn-section-list
                                                    rnp-text-input
                                                    rnp-helper-text
                                                    rnp-text-input-icon
                                                    rnp-icon-button
                                                    rnp-button
                                                    rnp-list-item
                                                    rnp-divider
                                                    rnp-list-icon
                                                    rnp-portal
                                                    rnp-text]]
   [clojure.string :as str]
   [onekeepass.mobile.background :refer [is-iOS]]
   [onekeepass.mobile.utils  :refer [str->int]]
   [onekeepass.mobile.common-components :as cc :refer [select-field confirm-dialog]]
   [onekeepass.mobile.events.settings :as stgs-events :refer [cancel-db-settings-form]]
   [onekeepass.mobile.events.password-generator :as pg-events]
   [onekeepass.mobile.constants :as const :refer [ICON-EYE ICON-EYE-OFF]]))

;;:settings-general :settings-credentials :settings-security 
(def ^:private desc-page-id {"General" :settings-general
                             "Credentials" :settings-credentials
                             "Security" :settings-security
                             "Encryption" :settings-encryption
                             "Key Derivation Function" :settings-kdf})

(def ^:private mp-confirm-dialog-data (r/atom false))

(defn master-password-change-confirm-dialog []
  [confirm-dialog {:dialog-show @mp-confirm-dialog-data
                   :title "Changing Master Password"
                   :confirm-text "You are changing the master password. Please keep it safe. Otherwise you will not be able to unlock the databse"
                   :actions [{:label (lstr "button.labels.yes")
                              :on-press (fn []
                                          (stgs-events/save-db-settings)
                                          (reset! mp-confirm-dialog-data false))}
                             {:label (lstr "button.labels.no")
                              :on-press (fn []
                                          (reset! mp-confirm-dialog-data false))}]}])

(defn appbar-title
  "Settings page specific title to display
  Returns a form-2 reagent componnent
   "
  []
  (fn [page-id]
    (let [modified? (not @(stgs-events/db-settings-modified))]
      [rn-view {:flexDirection "row"
                :style {:width "100%"
                        :alignItems "center"
                        :justify-content "space-between"}}
       [rnp-button {:style {}
                    :textColor @appbar-text-color
                    :mode "text"
                    :onPress cancel-db-settings-form} "Cancel"]
       [rnp-text {:style {:color @appbar-text-color
                          :max-width "60%"
                          :margin-right 0 :margin-left 0}
                  :ellipsizeMode "tail"
                  :numberOfLines 1
                  :variant page-title-text-variant} "Database Settings"]
       [rnp-button {:style {}
                    :textColor @appbar-text-color
                    :disabled modified?
                    :mode "text" :onPress (fn [_e]
                                            (cond
                                              (= page-id :settings-credentials)
                                              (reset! mp-confirm-dialog-data true)

                                              :else
                                              (stgs-events/save-db-settings)))} "Save"]])))

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

(defn general-content []
  (let [{:keys [database-name database-description]} (-> @(stgs-events/db-settings-data) :meta)
        error-text (:database-name @(stgs-events/db-settings-validation-errors))]
    [rn-view {:flex 1 :backgroundColor @page-background-color} ;;
     [form-header "Database Details"]
     [rn-view {:style form-style}
      [rnp-text-input {:label "Database Name"
                       ;;:value database-name
                       ;; Need to use defaultValue prop instead of value prop to handle the text caret cursor movement
                       ;; This is required mainly for android. Otherwise the cursor does not move after a letter is inserted 
                       :defaultValue database-name
                       :autoCapitalize "none"
                       :onChangeText #(stgs-events/db-settings-data-field-update [:meta :database-name] %)}]
      (when-not (nil? error-text) [rnp-helper-text {:type "error" :visible true} error-text])
      [rnp-text-input {:label "Database Description"
                       ;;:value database-description
                       :defaultValue database-description
                       :onChangeText #(stgs-events/db-settings-data-field-update [:meta :database-description] %)
                       :autoCapitalize "none"}]]]))

(defn credential-content []
  (let [password (-> @(stgs-events/db-settings-data) :password)
        password-visible @(stgs-events/master-password-visible?)
        password-changed #(stgs-events/db-settings-data-field-update :password (if (str/blank? %) nil %))
        key-file-name-part (-> @(stgs-events/db-settings-data) :key-file-name-part)]
    [rn-view {:style {:flex 1 :backgroundColor @page-background-color}} ;;
     [form-header "Credentials"]
     [rn-view {:style form-style}
      [rn-view {:style {:flexDirection "row"}}
       [rn-view {:style {:width "85%"}}
        [rnp-text-input {:style {}
                         :label "New Master Password"
                         :value password
                         :secureTextEntry (not password-visible)
                         :right (r/as-element [rnp-text-input-icon
                                               {:icon  (if password-visible ICON-EYE ICON-EYE-OFF)
                                                :onPress #(stgs-events/db-settings-field-update
                                                           :password-visible (not password-visible))}])
                         ;; on-change-text is a single argument function
                         :onChangeText password-changed #_#(stgs-events/db-settings-data-field-update :password %)}]]

       [rn-view {:style {:backgroundColor @page-background-color}}  ;;
        [rnp-icon-button {:style {}
                          :icon const/ICON-CACHED
                           ;; This function is called when the generated passed is selected in Generator page
                          :onPress #(pg-events/generate-password password-changed)}]]]

      (when key-file-name-part
        [rnp-text-input {:style {:margin-top 10}
                         :label "Key File"
                         :defaultValue key-file-name-part
                         :readOnly (if (is-iOS) true false)
                         :onPressIn #(stgs-events/show-key-file-form)
                         :onChangeText nil
                         :placeholder "Pick an optional key file"
                         :right (r/as-element [rnp-text-input-icon
                                               {:icon const/ICON-CLOSE
                                                :onPress (fn []
                                                           (stgs-events/db-settings-data-field-update :key-file-name-part nil)
                                                           (stgs-events/db-settings-data-field-update :key-file-name nil))}])}])
      [rnp-text {:style {:margin-top 15
                         :textDecorationLine "underline"
                         :text-align "center"}
                 :onPress #(stgs-events/show-key-file-form)} "Key File"]]

     [rnp-portal
      [master-password-change-confirm-dialog]]]))

(def encryption-algorithms [{:key "AES 256" :label "Aes256"} {:key "ChaCha20 256" :label "ChaCha20"}])

(defn security-content []
  (let [{:keys [cipher-id]
         {:keys [Argon2]} :kdf} @(stgs-events/db-settings-data)
        {:keys [memory iterations parallelism]} Argon2
        errors @(stgs-events/db-settings-validation-errors)]

    [rn-view {:flex 1 :backgroundColor @page-background-color}  ;;
     [form-header "Security"]
     [rn-view {:style form-style}
      [select-field {:text-label "Encription Algorithm"
                     :options encryption-algorithms
                     :value cipher-id
                     :on-change (fn [option]
                                  (stgs-events/db-settings-data-field-update :cipher-id (.-label option)))}]]
     [rn-view {:style form-style}
      [rnp-text-input {:style {}
                       :editable false
                       :label "Key Derivation Function" :value "Argon 2d"}]
      [rnp-text-input {:style {}
                       :label "Transform Rounds"
                       :keyboardType "numeric"
                       :value (str iterations)
                       :onChangeText #(stgs-events/db-settings-data-field-update [:kdf :Argon2 :iterations] (str->int %))}]
      (when-not (nil? (:iterations errors))
        [rnp-helper-text {:type "error" :visible true} (:iterations errors)])

      [rnp-text-input {:style {}
                       :label "Memory Usage"
                       :keyboardType "numeric"
                       :value (str memory)
                       :onChangeText #(stgs-events/db-settings-data-field-update [:kdf :Argon2 :memory] (str->int %))}]
      (when-not (nil? (:memory errors))
        [rnp-helper-text {:type "error" :visible true} (:memory errors)])

      [rnp-text-input {:style {}
                       :label "Parallelism"
                       :keyboardType "numeric"
                       :value (str parallelism)
                       :onChangeText #(stgs-events/db-settings-data-field-update [:kdf :Argon2 :parallelism] (str->int %))}]
      (when-not (nil? (:parallelism errors))
        [rnp-helper-text {:type "error" :visible true} (:parallelism errors)])]]))

(defn db-settings-form-content [page-id]
  (cond
    (= page-id :settings-general)
    [general-content]

    (= page-id :settings-credentials)
    [credential-content]

    (= page-id :settings-security)
    [security-content]))

(defn section-header [title]
  [rn-view  {:style {:flexDirection "row"
                     :width "100%"
                     :backgroundColor @inverse-onsurface-color
                     :margin-top 0
                     :min-height 38}}
   [rnp-text {:style {:textTransform "uppercase"
                      :alignSelf "center"
                      ;;:width "85%"
                      :text-align "center"
                      :padding-left 5} :variant "titleSmall"} title]])

(defn row-item [_m]
  (fn [{:keys [title]}]
    [rnp-list-item {:style {}
                    :onPress #(stgs-events/select-db-settings-panel (get desc-page-id title))
                    :title (r/as-element
                            [rnp-text {:style {}
                                       :variant "titleMedium"} title])
                    :right (fn [_props] (r/as-element [rnp-list-icon {:icon const/ICON-CHEVRON-RIGHT}]))}]))

(defn db-settings-list-content []
  (let [sections [{:title "Database Settings"
                   :key "DbSettings"
                   :data [{:title "General"}
                          {:title "Credentials"}
                          {:title "Security"}
                          #_{:title "Key Derivation Function"}]}]]
    [rn-section-list  {:style {}
                       :sections (clj->js sections)
                       :renderItem  (fn [props]
                                      ;; keys are (:item :index :section :separators)
                                      (let [props (js->clj props :keywordize-keys true)]
                                        (r/as-element [row-item (-> props :item)])))
                       :ItemSeparatorComponent (fn [_p]
                                                 (r/as-element [rnp-divider]))

                       :renderSectionHeader (fn [props]
                                              (let [props (js->clj props :keywordize-keys true)
                                                    {:keys [title]} (-> props :section)]
                                                (r/as-element [section-header title])))}]))

(defn main-content []
  [rn-view {:style {:flex 1}}
   [db-settings-list-content]])

(defn content []
  [rn-safe-area-view {:style {:flex 1 :backgroundColor @page-background-color}}
   [main-content]])