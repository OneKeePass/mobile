(ns onekeepass.mobile.settings
  {:clj-kondo/config '{:linters {:unresolved-symbol {:level :off}}}}
  (:require
   [reagent.core :as r]
   [onekeepass.mobile.rn-components :as rnc :refer [appbar-text-color
                                                    page-background-color
                                                    inverse-onsurface-color
                                                    page-title-text-variant
                                                    no-assist-text-props
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
   [onekeepass.mobile.translation :refer [lstr-bl lstr-l lstr-mt lstr-pt]]
   [onekeepass.mobile.background :refer [is-iOS]]
   [onekeepass.mobile.utils  :refer [str->int]]
   [onekeepass.mobile.common-components :as cc :refer [select-field confirm-dialog settings-section-header]]
   [onekeepass.mobile.events.settings :as stgs-events :refer [cancel-db-settings-form]]
   [onekeepass.mobile.events.password-generator :as pg-events]
   [onekeepass.mobile.events.common :as cmn-events]
   [onekeepass.mobile.events.app-settings :as as-events]
   [onekeepass.mobile.events.app-database-settings :as ada-events]
   [onekeepass.mobile.events.autofill :as af-events]
   [onekeepass.mobile.manage-custom-icons :as manage-custom-icons]
   [onekeepass.mobile.constants :as const :refer [ICON-EYE ICON-EYE-OFF]]))

(def ^:private mp-confirm-dialog-data (r/atom false))

(defn master-password-change-confirm-dialog []
  [confirm-dialog {:dialog-show @mp-confirm-dialog-data
                   :title "Changing Master Password"
                   :confirm-text "You are changing the master password. Please keep it safe. Otherwise you will not be able to unlock the databse"
                   :actions [{:label (lstr-bl "yes")
                              :on-press (fn []
                                          (stgs-events/save-db-settings)
                                          (reset! mp-confirm-dialog-data false))}
                             {:label (lstr-bl "no")
                              :on-press (fn []
                                          (reset! mp-confirm-dialog-data false))}]}])

(defn appbar-title
  "Settings page specific title to display
  Returns a form-2 reagent componnent
   "
  []
  (fn [page-id]
    (let [not-modified? (not @(stgs-events/db-settings-modified))
          save-diabled (or not-modified? @(cmn-events/current-db-disable-edit))]
      [rn-view {:flexDirection "row"
                :style {:width "100%"
                        :alignItems "center"
                        :justify-content "space-between"}}
       [rnp-button {:style {}
                    :textColor @appbar-text-color
                    :mode "text"
                    :onPress cancel-db-settings-form} (lstr-bl "cancel")]
       [rnp-text {:style {:color @appbar-text-color
                          :max-width "60%"
                          :margin-right 0 :margin-left 0}
                  :ellipsizeMode "tail"
                  :numberOfLines 1
                  :variant page-title-text-variant} (lstr-pt 'databaseSettings)]
       [rnp-button {:style {}
                    :textColor @appbar-text-color
                    :disabled save-diabled
                    :mode "text" :onPress (fn [_e]
                                            (cond
                                              (= page-id const/SETTINGS_CREDENTIALS_PAGE_ID)
                                              (reset! mp-confirm-dialog-data true)

                                              :else
                                              (stgs-events/save-db-settings)))} (lstr-bl 'save)]])))

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
                 :borderWidth 0.20 :borderRadius 4})

(defn general-content []
  (let [{:keys [database-name database-description]} (-> @(stgs-events/db-settings-data) :meta)
        error-text (:database-name @(stgs-events/db-settings-validation-errors))]
    [rn-view {:flex 1 :backgroundColor @page-background-color} ;;
     [form-header (lstr-l 'databaseDetails)]
     [rn-view {:style form-style}
      [rnp-text-input (merge no-assist-text-props
                             {:label (lstr-l 'databaseName)
                              ;;:value database-name
                              ;; Need to use defaultValue prop instead of value prop to handle the text caret cursor movement
                              ;; This is required mainly for android. Otherwise the cursor does not move after a letter is inserted
                              :defaultValue database-name
                              :onChangeText #(stgs-events/db-settings-data-field-update [:meta :database-name] %)})]
      (when-not (nil? error-text) [rnp-helper-text {:type "error" :visible true} error-text])
      [rnp-text-input (merge no-assist-text-props
                             {:label (lstr-l 'databaseDesc)
                              ;;:value database-description
                              :defaultValue database-description
                              :onChangeText #(stgs-events/db-settings-data-field-update [:meta :database-description] %)})]]]))

(defn password-credential [{:keys [password-visible password-use-removed password-use-added]
                            {:keys [password
                                    password-used]} :data}]
  (let [update-password #(stgs-events/db-settings-password-updated %)]
    [rn-view {:style {:backgroundColor @page-background-color}}
     [form-header (lstr-l 'masterPassword)]
     [rn-view {:style form-style}
      (cond
        ;;
        (or password-use-added (and password-used (not password-use-removed)))
        [rn-view
         (when-not password-use-added
           [rn-view
            [rn-view {:style {:margin-top 20 :margin-bottom 20 :align-items "center"}}
             [rnp-button {:style {:width "50%"}
                          :mode "contained"
                          ;; Removing the password cannot be saved to a read only db
                          :disabled @(cmn-events/current-db-disable-edit)
                          :on-press stgs-events/db-settings-password-removed} (lstr-bl "removePassword")]]
            [rnp-divider {:bold true :style {:margin-top 5}}]])

         [rn-view {:style {:flexDirection "row"}}
          [rn-view {:style {:width "85%"}}
           [rnp-text-input (merge no-assist-text-props
                                  {:style {}
                                   :label (if password-use-added (lstr-bl "addPassword") (lstr-bl "changePassword"))
                                   :value password
                                   :secureTextEntry (not password-visible)
                                   :right (r/as-element [rnp-text-input-icon
                                                         {:icon  (if password-visible ICON-EYE ICON-EYE-OFF)
                                                          :onPress #(stgs-events/db-settings-field-update
                                                                     :password-visible (not password-visible))}])
                                   ;; on-change-text is a single argument function
                                   :onChangeText update-password})]]

          [rn-view {:style {:backgroundColor @page-background-color}}  ;;
           [rnp-icon-button {:style {}
                             :icon const/ICON-CACHED
                             ;; This function is called when the generated passed is selected in Generator page
                             :onPress #(pg-events/generate-password update-password)}]]]]

        ;;
        (not password-use-added)
        [rn-view {:style {:margin-top 20 :margin-bottom 20 :align-items "center"}}
         [rnp-button {:style {:width "50%"}
                      :mode "contained"
                      :disabled @(cmn-events/current-db-disable-edit)
                      :on-press stgs-events/db-settings-password-added}
          (lstr-bl 'addPassword)]

         (when password-use-removed
           [rnp-text {:style {:margin-top 10 :color @rnc/tertiary-color}}
            (lstr-mt 'dbSettings 'currrentPasswordRemoved)])])]]))

(defn key-file-credential [{:keys []
                            {:keys [key-file-name-part]} :data}]
  ;; A key file cannot be picked or cleared for a read only db as the change cannot be saved
  (let [read-only? @(cmn-events/current-db-disable-edit)]
    [rn-view {:style {:backgroundColor @page-background-color}} ;;
     [form-header (lstr-l 'keyFile)]
     [rn-view  {:style form-style}
      (when key-file-name-part
        [rnp-text-input {:style {:margin-top 10}
                         :label (lstr-l 'keyFile)
                         :defaultValue key-file-name-part
                         :readOnly (if (is-iOS) true false)
                         :disabled read-only?
                         :onPressIn #(stgs-events/show-key-file-form)
                         :onChangeText nil
                         :placeholder (lstr-mt 'dbSettings 'pickKeyFile)
                         :right (r/as-element [rnp-text-input-icon
                                               {:icon const/ICON-CLOSE
                                                :disabled read-only?
                                                :onPress (fn []
                                                           (stgs-events/clear-key-file-field))}])}])
      [rnp-text {:style (cond-> {:margin-top 15
                                 :textDecorationLine "underline"
                                 :text-align "center"}
                          read-only? (assoc :opacity 0.38))
                 :disabled read-only?
                 :onPress (when-not read-only? #(stgs-events/show-key-file-form))} (lstr-bl 'keyFile)]]]))

(defn credential-content []
  [rn-view {:style {:flex 1 :backgroundColor @page-background-color}}
   [password-credential @(stgs-events/db-settings-main)]
   [rn-view {:style {:margin-top 20}}] ;; a gap
   [key-file-credential @(stgs-events/db-settings-main)]

   (when-let [error-text (:in-sufficient-credentials @(stgs-events/db-settings-validation-errors))]
     [rn-view {:style {:margin-top 20}}
      [rnp-helper-text {:style {:fontWeight "bold"}  :type "error" :visible true}
       ;; Translatted error text
       error-text]])
   [rnp-portal
    [master-password-change-confirm-dialog]]])

(def encryption-algorithms [{:key "Aes256" :label "AES 256"} {:key "ChaCha20" :label "ChaCha20 256"}])

(def kdf-algorithms [{:key "Argon2d" :label  "Argon 2d (KDBX 4)"} {:key "Argon2id" :label "Argon 2id (KDBX 4)"}])

(defn security-content []
  (let [{:keys [cipher-id]
         {:keys [memory iterations parallelism algorithm]} :kdf} @(stgs-events/db-settings-data)
        errors @(stgs-events/db-settings-validation-errors)] 
    [rn-view {:flex 1 :backgroundColor @page-background-color}  ;;
     [form-header (lstr-l 'security)]
     [rn-view {:style form-style}
      [select-field {:text-label (lstr-l 'encriptionAlgorithm)
                     :options encryption-algorithms
                     :value (cc/find-matching-label encryption-algorithms cipher-id) 
                     :on-change (fn [option] 
                                  (stgs-events/db-settings-data-field-update :cipher-id (.-key option)))}]]
     [rn-view {:style form-style}
      [select-field {:text-label (lstr-l 'kdf)
                     :options kdf-algorithms
                     :value (cc/find-matching-label kdf-algorithms algorithm)  
                     :on-change (fn [option-selected]
                                  (stgs-events/db-settings-kdf-algorithm-select (.-key option-selected)))}]

      [rnp-text-input {:style {}
                       :label (lstr-l 'transformRounds)
                       :keyboardType "numeric"
                       :value (str iterations)
                       :onChangeText #(stgs-events/db-settings-data-field-update [:kdf :iterations] (str->int %))}]
      (when-not (nil? (:iterations errors))
        [rnp-helper-text {:type "error" :visible true} (:iterations errors)])

      [rnp-text-input {:style {}
                       :label (lstr-l 'memoryUsage)
                       :keyboardType "numeric"
                       :value (str memory)
                       :onChangeText #(stgs-events/db-settings-data-field-update [:kdf :memory] (str->int %))}]
      (when-not (nil? (:memory errors))
        [rnp-helper-text {:type "error" :visible true} (:memory errors)])

      [rnp-text-input {:style {}
                       :label (lstr-l 'parallelism)
                       :keyboardType "numeric"
                       :value (str parallelism)
                       :onChangeText #(stgs-events/db-settings-data-field-update [:kdf :parallelism] (str->int %))}]
      (when-not (nil? (:parallelism errors))
        [rnp-helper-text {:type "error" :visible true} (:parallelism errors)])]]))

(defn db-settings-form-content [page-id]
  (cond
    (= page-id const/SETTINGS_GENERAL_PAGE_ID)
    [general-content]

    (= page-id const/SETTINGS_CREDENTIALS_PAGE_ID)
    [credential-content]

    (= page-id const/SETTINGS_SECURITY_PAGE_ID)
    [security-content]))


(def ^:private ^:const SECTION-KEY-DB-SETTINGS "DbSettings")
(def ^:private ^:const SECTION-KEY-ADDITIONAL-DB-ACCESS "AdditionalDatabaseAcccess")
(def ^:private ^:const SECTION-KEY-APP-SETTINGS "AppSettings")
(def ^:private ^:const SECTION-KEY-AUTOFILL "AutofillSettings")

;; Manage Custom Icons is listed with the database settings as the icons are kept in the
;; database file. This page-id tells its row apart from the settings panels
(def ^:private ^:const CUSTOM-ICONS-PAGE-ID "CustomIcons")


(defn field-explain []
  [rn-view {:style {:margin-top 1 :margin-bottom 15} :flexDirection "row" :flexWrap "wrap"}
   [rnp-text {:style {:margin-left 15}}
    (lstr-mt 'settings 'additionalDbAccessExplain {:biometricType (if (is-iOS)
                                                                    (lstr-l 'faceId)
                                                                    (lstr-l 'faceUnlockFingerprint))
                                                   ;;:interpolation {:escapeValue false}
                                                   })]])

(defn row-item [_m]
  (fn [{:keys [title page-id]} section-key]
    ;; page-id comes back from React Native SectionList as a string after clj->js/js->clj.
    ;; title is a key to i18n map
    [rn-view {:style {}}
     [rnp-list-item {:style {}
                     :contentStyle {}
                     :onPress (fn []
                                (cond
                                  (= page-id CUSTOM-ICONS-PAGE-ID)
                                  (manage-custom-icons/open-page)

                                  (= section-key SECTION-KEY-DB-SETTINGS)
                                  (stgs-events/select-db-settings-panel (keyword page-id))

                                  (= section-key SECTION-KEY-ADDITIONAL-DB-ACCESS)
                                  (ada-events/to-additional-db-acess-settings-page)

                                  (and (= section-key SECTION-KEY-AUTOFILL) (is-iOS))
                                  (af-events/to-autofill-settings-page)

                                  (= section-key SECTION-KEY-APP-SETTINGS)
                                  (as-events/to-app-settings-page)))
                     :title (r/as-element
                             [rnp-text {:style {}
                                        :variant "titleMedium"} (lstr-l title)])
                     :right (fn [_props] (r/as-element [rnp-list-icon {:icon const/ICON-CHEVRON-RIGHT}]))}]


     (when (= section-key SECTION-KEY-ADDITIONAL-DB-ACCESS)
       [field-explain])]))


(defn sections-data []
  ;; The subtitles tell where each group is kept. Only what is kept in the database file is
  ;; affected when the database is read only
  [{:title "dbSettings"
    :subtitle "savedInDbFile"
    :key SECTION-KEY-DB-SETTINGS
    :data [{:title "general" :page-id const/SETTINGS_GENERAL_PAGE_ID}
           {:title "credentials" :page-id const/SETTINGS_CREDENTIALS_PAGE_ID}
           {:title "security" :page-id const/SETTINGS_SECURITY_PAGE_ID}
           {:title "manageCustomIcons" :page-id CUSTOM-ICONS-PAGE-ID}]}

   {:title "additionalDbAcccess"
    :subtitle "savedOnThisDevice"
    :key SECTION-KEY-ADDITIONAL-DB-ACCESS
    :data [{:title "enableDisable"}]}

   ;; For android this is nil and need to be filtered out
   (when (is-iOS)
     {:title "autofillSettings"
      :key SECTION-KEY-AUTOFILL
      :data [{:title "autofillSettings"}]})

   {:title "appSettings"
    :key SECTION-KEY-APP-SETTINGS
    :data [{:title "allAppSettings"}]}])

(defn settings-list-content []
  (let [sections (filterv (complement nil?) (sections-data))]
    [rn-section-list  {:style {}
                       :sections (clj->js sections)
                       :renderItem  (fn [props]
                                      ;; keys are (:item :index :section :separators)
                                      ;; (-> props :item) gives the map corresponding to each member of the vec in :data
                                      (let [props (js->clj props :keywordize-keys true)]
                                        (r/as-element [row-item (-> props :item) (-> props :section :key)])))
                       :ItemSeparatorComponent (fn [_p]
                                                 (r/as-element [rnp-divider]))

                       :renderSectionHeader (fn [props]
                                              (let [props (js->clj props :keywordize-keys true)
                                                    {:keys [title subtitle]} (-> props :section)]
                                                (r/as-element [settings-section-header title subtitle])))}]))

(defn main-content []
  [rn-view {:style {:flex 1}}

   ;; The list takes all the space left and the link keeps only its own height at the bottom,
   ;; so the list does not have to scroll when it would fit
   [rn-view {:style {:flex 1}}
    [settings-list-content]]

   [rn-view {:style {:padding-top 12 :padding-bottom 16}}
    [rnp-text {:style {:textDecorationLine "underline"
                       :text-align "center"}
               :variant "titleMedium"
               :onPress cmn-events/to-about-page} (lstr-l "appInfo")]]])

(defn content []
  [rn-safe-area-view {:style {:flex 1 :backgroundColor @page-background-color}}
   [main-content]])