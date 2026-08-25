(ns
 onekeepass.mobile.entry-form
  (:require [clojure.string :as str]
            [onekeepass.mobile.background :refer [is-Android is-iOS]]
            [onekeepass.mobile.common-components :as cc :refer [select-field
                                                                select-tags-dialog]]
            [onekeepass.mobile.constants :as const :refer [ADDITIONAL_ONE_TIME_PASSWORDS
                                                           BOOL_TYPE
                                                           DATE_TYPE
                                                           IFDEVICE
                                                           ONE_TIME_PASSWORD_TYPE
                                                           PASSKEY_DETAILS
                                                           PASSWORD URL
                                                           USERNAME]]
            [onekeepass.mobile.date-utils :refer [utc-str-to-local-datetime-str]]
            [onekeepass.mobile.entry-form-dialogs :refer [add-modify-section-field-dialog
                                                          add-modify-section-name-dialog
                                                          auto-open-db-file-required-info-dialog
                                                          auto-open-key-file-pick-required-info-dialog
                                                          confirm-delete-otp-field-dialog
                                                          delete-attachment-dialog-info
                                                          delete-field-confirm-dialog
                                                          history-entry-delete-dialog
                                                          history-entry-restore-dialog
                                                          otp-settings-dialog
                                                          rename-attachment-name-dialog
                                                          rename-attachment-name-dialog-data
                                                          setup-otp-action-dialog
                                                          setup-otp-action-dialog-show]]
            [onekeepass.mobile.entry-form-fields :as ef-fields :refer [bool-field date-field
                                                                       otp-field text-field]]
            [onekeepass.mobile.entry-form-menus :refer [attachment-long-press-menu
                                                        attachment-long-press-menu-data
                                                        custom-field-menu
                                                        custom-field-menu-data
                                                        section-menu
                                                        section-menu-dialog-data
                                                        section-menu-dialog-show
                                                        show-attachment-long-press-menu]]
            [onekeepass.mobile.entry-list :as entry-list]
            [onekeepass.mobile.events.common :as cmn-events]
            [onekeepass.mobile.events.custom-icons :as ci-events]
            [onekeepass.mobile.events.dialogs :as dlg-events]
            [onekeepass.mobile.events.entry-form :as form-events :refer [place-holder-resolved-value]]
            [onekeepass.mobile.grouped-list :as gl]
            [onekeepass.mobile.icons-list :as icons-list]
            [onekeepass.mobile.rn-components
             :as rnc
             :refer [appbar-text-color dots-icon-name icon-color
                     no-assist-text-props no-autocorrect-text-props
                     on-primary-color page-background-color
                     page-title-text-variant primary-container-color rn-image
                     rn-keyboard rn-keyboard-avoiding-view rn-scroll-view
                     rn-section-list rn-view rnp-button rnp-chip rnp-divider
                     rnp-helper-text rnp-icon-button rnp-list-icon
                     rnp-list-item rnp-portal rnp-text rnp-text-input
                     rnp-text-input-icon]]
            [onekeepass.mobile.translation :refer [lstr-bl lstr-field-name
                                                   lstr-l lstr-pt
                                                   lstr-section-name]]
            [onekeepass.mobile.utils :as u]
            [reagent.core :as r]))

;;(set! *warn-on-infer* true)

(def box-style-1 {:flexDirection "column"
                  :padding-right 8
                  :padding-left 8
                  :margin-bottom 5
                  :borderWidth 0.20
                  :borderRadius 4})

(def box-style-2 (merge box-style-1 {:padding-bottom 10 :padding-top 5}))

;; Left below the last field of a read mode card. A field draws its own underline at its very
;; bottom and without this the line of the last one sits flush against the card's rounded edge
(def ^:private CARD-FIELDS-BOTTOM-PADDING 8)

(defn- fields-card-style
  "Style of the read mode card that a block of form fields is drawn on"
  []
  (merge (gl/card-block-style) {:padding-bottom CARD-FIELDS-BOTTOM-PADDING}))

;; The notes card is the one field card that is left unclipped. Ending an editing or saving
;; left the field showing neither its text nor its label, while the value was still there in
;; the form data - the notes came back only on opening the entry again, which mounts the field
;; afresh. The multiline input draws outside the box the card gives it after that switch and
;; the card's 'overflow hidden' was cutting all of it away
;;
;; Dropping the clipping is what makes the notes visible again. It is safe to drop here as the
;; bottom padding already keeps the field's underline off the card's rounded edge, which is
;; what the clipping is there for
(defn- notes-card-style
  []
  (merge (fields-card-style) {:overflow "visible"}))

(defn appbar-title
  "Entry form specific title to display"
  []
  (let [edit @(form-events/form-edit-mode)
        is-history-entry @(form-events/history-entry-form?)]
    (if edit
      [rn-view {:flexDirection "row"
                :style {:alignItems "center"
                        ;; :justify-content "space-between"  <- Using this hides the Edit or Save button below Action icon
                        ;; We need to use "center" whenever we have 'rnp-appbar-action' 
                        :justify-content "center"}}
       [rnp-button {:style {}
                    :textColor @appbar-text-color
                    :mode "text"
                    :onPress form-events/cancel-entry-form}
        (lstr-bl "cancel")]
       [rnp-text {:style {:color @appbar-text-color
                          :max-width 100
                          :margin-right 20 :margin-left 20}
                  :ellipsizeMode "tail"
                  :numberOfLines 1
                  :variant page-title-text-variant}
        (lstr-pt "entry")]
       [rnp-button {:style {}
                    :textColor @appbar-text-color
                    :disabled (or (not @(form-events/form-modified)) @(cmn-events/current-db-disable-edit))
                    :mode "text"
                    :onPress (fn []
                               (.dismiss rn-keyboard)
                               (form-events/entry-save))}
        (lstr-bl "save")]]

      [rn-view {:flexDirection "row"
                :style {:alignItems "center"
                        ;; See the above comment for using "center"
                        :justify-content "center"}}
       [rnp-button {:style {}
                    :textColor @on-primary-color
                    :mode "text"
                    :onPress (if is-history-entry
                               form-events/cancel-history-entry-form
                               form-events/cancel-entry-form)}
        (lstr-bl "close")]
       [rnp-text {:style {:color @on-primary-color
                          :max-width "75%"
                          :margin-right 20 :margin-left 20}
                  :ellipsizeMode "tail"
                  :numberOfLines 1
                  :variant page-title-text-variant}
        (if is-history-entry (lstr-pt "historyEntry")
            (lstr-pt "entry"))]
       [rnp-button {:style {}
                    :textColor @on-primary-color
                    :disabled (or @(form-events/deleted-category-showing)
                                  is-history-entry
                                  @(cmn-events/current-db-disable-edit))
                    :mode "text" :onPress form-events/edit-mode-on-press}
        (lstr-bl "edit")]])))

(declare clear-notes)

(defn entry-type-selection []
  (let [entry-types (clj->js
                     (->> @(cmn-events/all-entry-type-headers)
                          (mapv (fn [{:keys [name uuid]}]
                                  {:key uuid :label name}))))
        entry-type-name-selection (form-events/entry-form-field :entry-type-name-selection)]
    [select-field {:text-label (str (lstr-l 'entryType) "*")
                   :options entry-types
                   :value @entry-type-name-selection
                   ;;:init-value @entry-type-name-selection  
                   :on-change (fn [^js/SelOption option]
                                ;; option is the selected member from the entry-types list passed as :options
                                (clear-notes)
                                (form-events/on-entry-type-selection (.-key option)))}]))

(defn group-selection []
  (let [groups-listing (form-events/groups-listing)
        names (mapv (fn [m] {:key (:name m) :label (:name m)}) @groups-listing)
        group-selection-info (form-events/entry-form-field :group-selection-info)
        group-selected-name (:name @group-selection-info)
        edit @(form-events/form-edit-mode)
        error-fields @(form-events/entry-form-field :error-fields)
        error-text (:group-selection error-fields)
        on-change (fn [^js/SelOption option]
                    ;; option is the selected member from the names list passed as :options
                    (let [g (first (filter (fn [m] (= (:name m) (.-label option))) @groups-listing))]
                      (form-events/on-group-selection g)))]

    (if (and edit (not (nil? error-text)))
      [:<>
       [select-field {:text-label (str (lstr-l 'groupOrCategory) "*")
                      :options names
                      :value group-selected-name
                      :on-change on-change}]
       [rnp-helper-text {:type "error" :visible true} error-text]]

      [select-field {:text-label (str (lstr-l 'groupOrCategory) "*")
                     :options names
                     :value group-selected-name
                     :on-change on-change}])))

(declare title-with-icon)

(defn entry-type-selection-box []
  (let [is-new-entry @(form-events/new-entry-form?)]
    (when is-new-entry
      [rn-view {:style box-style-1}
       [entry-type-selection]])))

(defn title-group-selection-box []
  (let [edit @(form-events/form-edit-mode)
        is-new-entry @(form-events/new-entry-form?)]
    (if edit
      [rn-view {:style box-style-1}
       [title-with-icon]
       (when is-new-entry
         [:<>
          #_[entry-type-selection]
          [group-selection]])]
      [title-with-icon])))

(defn on-entry-icon-selection
  "A callback function that is called from :common/icon-selected event handler
   when the user selects a new icon. When `custom-icon-uuid` is non-nil the
   user picked a custom icon — clear the standard icon-id and set the uuid."
  ([_icon-name icon-id]
   (on-entry-icon-selection _icon-name icon-id nil))
  ([_icon-name icon-id custom-icon-uuid]
   (form-events/edit-mode-on-press)
   (if custom-icon-uuid
     (do
       (form-events/entry-form-data-update-field-value :icon-id 0)
       (form-events/entry-form-data-update-field-value :custom-icon-uuid custom-icon-uuid))
     (do
       (form-events/entry-form-data-update-field-value :icon-id icon-id)
       (form-events/entry-form-data-update-field-value :custom-icon-uuid nil)))))

(defn- launch-icon-picker [prefill-url]
  ;; Pre-seed the Add From URL dialog with the entry's URL value so the
  ;; common case of "fetch favicon for this entry's site" is one tap.
  (cmn-events/show-icons-to-select on-entry-icon-selection prefill-url))

(defn- title-input-right-icon
  "Right-side affordance for the title text input. When the entry has a
   custom icon, render its image; otherwise render the standard
   MaterialCommunityIcons glyph. Both tap to launch the icon picker.

   IMPORTANT: react-native-paper's `TextInput.right` only renders a
   `TextInput.Icon` node — wrapping anything else around it (e.g.
   rn-pressable + rn-image directly) is silently dropped. So in the
   custom-icon case we still return a TextInput.Icon and use its
   render-function form for `:icon` to inject our rn-image."
  [icon-name custom-data-url prefill-url]
  (if custom-data-url
    [rnp-text-input-icon
     {:icon (fn []
              (r/as-element
               [rn-image {:source (clj->js {:uri custom-data-url})
                          :style {:width icons-list/ENTRY-GROUP-FORM-ICON-SIZE
                                  :height icons-list/ENTRY-GROUP-FORM-ICON-SIZE}}]))
      :onPress #(launch-icon-picker prefill-url)}]
    [rnp-text-input-icon {:iconColor @icon-color
                          :size icons-list/ENTRY-GROUP-FORM-ICON-SIZE
                          :icon icon-name
                          :onPress #(launch-icon-picker prefill-url)}]))

(defn android-title-text-input [title icon-name custom-data-url prefill-url]
  [rnp-text-input (merge no-assist-text-props
                         {:style {:width "100%"}
                          :label (str (lstr-l 'title) "*")
                          :defaultValue title
                          :ref (fn [^js/Ref ref]
                                 ;; Keys found in ref for textinput
                                 ;; are #js ["focus" "clear" "setNativeProps" "isFocused" "blur" "forceFocus"]
                                 ;; Need to call clear directly as the previous value is not getting cleared
                                 ;; when there is a change in entry type selection name
                                 (when (and (not (nil? ref)) (str/blank? title)) (.clear ref)))
                          :onChangeText #(form-events/entry-form-data-update-field-value :title %)
                          :right (r/as-element
                                  ;;The title-input-right-icon is called directly before r/as-element. Otherwise the icon is not shown
                                  (title-input-right-icon icon-name custom-data-url prefill-url))})])

(defn ios-title-text-input [title icon-name custom-data-url prefill-url]
  [rnp-text-input (merge no-assist-text-props
                         {:style {:width "100%"}
                          :label (str (lstr-l 'title) "*")
                          :value title
                          :onChangeText #(form-events/entry-form-data-update-field-value :title %)
                          :right (r/as-element
                                  ;;The title-input-right-icon is called directly before r/as-element. Otherwise the icon is not shown
                                  (title-input-right-icon icon-name custom-data-url prefill-url))})])

(defn title-with-icon []
  (let [{:keys [title icon-id custom-icon-uuid]}
        @(form-events/entry-form-data-fields [:title :icon-id :custom-icon-uuid])
        icon-name (icons-list/icon-id->name icon-id)
        edit @(form-events/form-edit-mode)
        error-fields @(form-events/entry-form-field :error-fields)
        ;; Trigger lazy fetch of the icon bytes when a custom icon is set.
        _ (when custom-icon-uuid
            (ci-events/ensure-icon-data-url custom-icon-uuid))
        custom-data-url (when custom-icon-uuid
                          @(ci-events/icon-data-url custom-icon-uuid))
        prefill-url @(form-events/entry-form-section-field-value URL)]
    (if edit
      [rn-view {:style {:margin-top 2 :margin-bottom 2}}
       (if (is-iOS)
         [ios-title-text-input title icon-name custom-data-url prefill-url]
         [android-title-text-input title icon-name custom-data-url prefill-url])
       (when (contains? error-fields :title)
         [rnp-helper-text {:type "error" :visible (contains? error-fields :title)}
          (:title error-fields)])]
      [rn-view {:style {:flexDirection "row" :justify-content "center" :alignItems "center" :margin-top 8 :margin-bottom 8 }}
       (if custom-data-url
         [rn-image {:source (clj->js {:uri custom-data-url})
                    :style {:width icons-list/ENTRY-GROUP-LIST-ICON-SIZE
                            :height icons-list/ENTRY-GROUP-LIST-ICON-SIZE}}]
         [rnp-list-icon {:style {:width icons-list/ENTRY-GROUP-LIST-ICON-SIZE
                                  :height icons-list/ENTRY-GROUP-LIST-ICON-SIZE}
                         :icon icon-name
                         :color @icon-color}])
       [rn-view {:style {:width 10}}]
       [rnp-text {:variant "titleLarge"} title]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Notes - Multiline text input
;; Android issue - inputing slow and if any character changed in the middle of text 
;; the cursor moves to the left
;; Need to use defaultValue and clear fn for entry type selection changes 
;; See other method of handling this in case of title and other text field 
;; iOS issue (After 0.71.3 upgrade)
;; The value props does not work with multiline and need to use defaultValue 
;; Need to call explcitly clear-notes in entry-type-selection on-change event
;; Still Soft KB hides the notes field and we need to manually scroll to the field.

;; When using multiline text input (rnp component) in iOS, the label is not shown when we have some value in input
;; See https://github.com/callstack/react-native-paper/issues/4482

(def notes-ref (atom nil))

(defn clear-notes []
  (when @notes-ref (.clear ^js/Ref  @notes-ref)))

(defn notes [edit]
  (let [value @(form-events/entry-form-data-fields :notes)]
    (when (or edit (not (str/blank? value)))
      [rn-view {:style (if edit
                         {:padding-right 5 :padding-left 5 :borderWidth 0.20 :borderRadius 4}
                         (notes-card-style))}
       [rnp-text-input (merge no-autocorrect-text-props
                              {:style (merge {:width "100%"}
                                             (when-not edit
                                               {:backgroundColor (ef-fields/read-field-background)}))
                               :multiline true
                               :label  (lstr-l "notes")
                               ;; :label (r/as-element [rnp-text {:style {:color "red"}} "My Notes"])
                               :defaultValue value
                               :placeholder ""
                               ;; :mode "outlined"
                               :ref (fn [^js/Ref ref]
                                      (reset! notes-ref ref)
                                      (when (and (is-Android) (not (nil? ref)) (str/blank? value)) (.clear ref)))
                               :showSoftInputOnFocus edit
                               :onChangeText (when edit #(form-events/entry-form-data-update-field-value :notes %))})]])))

(defn section-header [section-name]
  (let [edit @(form-events/entry-form-field :edit)
        standard-sections @(form-events/entry-form-data-fields :standard-section-names)
        ;;_ (println "In section-header standard-section-names are " standard-sections)
        standard-section? (u/contains-val? standard-sections section-name)
        tr-section-name (if standard-section? (lstr-section-name section-name) section-name)]
    (if-not edit
      ;; In read mode each section is a card standing on the page ground, introduced by the same
      ;; quiet header the list pages use. Nothing here can be collapsed, so no chevron
      [gl/section-header {:label tr-section-name}]

      [rn-view {:style {:flexDirection "row"
                        :backgroundColor  @primary-container-color
                        :margin-top 5
                        :min-height 35}}
       [rnp-text {:style {:alignSelf "center" :width "85%" :padding-left 15} :variant "titleMedium"}
        tr-section-name]
       (if (not= section-name ADDITIONAL_ONE_TIME_PASSWORDS)
         [rnp-icon-button {:icon dots-icon-name :style {:height 35
                                                        :margin-right 0
                                                        :backgroundColor @on-primary-color}
                           :onPress (fn [^js/PEvent event]
                                      (section-menu-dialog-show {:section-name section-name
                                                                 :is-standard-section standard-section?
                                                                 :event event}))}]

         [rnp-icon-button {:icon const/ICON-PLUS
                           :style {:height 35 :margin-right 0 :backgroundColor @on-primary-color}
                           :onPress (fn [] (form-events/show-form-fields-validation-error-or-call
                                            #(setup-otp-action-dialog-show section-name nil false)))}])])))

(defn get-section-data
  "Called to set up any entry type specific data in kv
   Returns an vec of kvd map for a section
   "
  [entry-type-uuid section-name section-fields parsed-fields]
  (let [section-data (get section-fields section-name)

        adjusted-section-data (mapv
                               (fn [{:keys [key] :as m}]
                                 (assoc m :read-value (place-holder-resolved-value parsed-fields key)))
                               section-data)

        adjusted-section-data
        (cond
          (= entry-type-uuid const/UUID_OF_ENTRY_TYPE_AUTO_OPEN)
          (mapv
           (fn [{:keys [key] :as m}]
             ;; Note the use of lstr-field-name vs tr-entry-field-name-cv
             ;; lstr-field-name is fn and tr-entry-field-name-cv is a macro
             (cond
               (= key URL)
               ;; for now read-value is not used
               ;;:read-value (:url-field-value m)
               (assoc m :field-name (lstr-field-name "autoOpenKdbxFileOpen"))

               (= key USERNAME)
               (assoc m :field-name (lstr-field-name 'autoOpenKeyFile)
                      :read-value (place-holder-resolved-value parsed-fields key)) ;; :read-value (:key-file-path m)

               (= key PASSWORD)
               (assoc m  :read-value (place-holder-resolved-value parsed-fields key))

               (= key IFDEVICE)
               (assoc m :field-name (lstr-field-name "autoOpenIfDevice"))

               :else
               m))
           adjusted-section-data)

          (= entry-type-uuid const/UUID_OF_ENTRY_TYPE_REMOTE_CONNECTION_SFTP)
          ;; The Password field on a REMOTE_CONNECTION_SFTP entry is
          ;; dual-use: login password OR private-key passphrase. The kv key
          ;; stays "Password" so the resolver is unaffected; only the
          ;; display label changes so the user understands the dual use.
          (mapv
           (fn [{:keys [key] :as m}]
             (if (= key PASSWORD)
               (assoc m :field-name (lstr-field-name "sftpPasswordOrPassphrase"))
               m))
           adjusted-section-data)

          :else
          adjusted-section-data)]
    adjusted-section-data))

;; These standard sections are not shown in the edit mode when they do not have any value.
;; Instead the user shows such a section on demand - see 'on-demand-section-links'.
;; The values of these sections are set through a dialog (one time passwords) or by the
;; browser extension of the desktop app (passkey) and are not entered field by field
(def ^:private ON_DEMAND_SECTIONS #{ADDITIONAL_ONE_TIME_PASSWORDS PASSKEY_DETAILS})

(defn- section-has-values?
  "Returns true if any field of this section has a non blank value"
  [section-data]
  (boolean (seq (filter (fn [kv] (not (str/blank? (:value kv)))) section-data))))

(defn- section-hidden-on-demand?
  "Returns true if this section is to be hidden in the edit mode till the user asks for it.
   A section that has any value or has any field in error is always shown"
  [section-name section-data revealed-sections errors]
  (and (contains? ON_DEMAND_SECTIONS section-name)
       (not (u/contains-val? revealed-sections section-name))
       (not (section-has-values? section-data))
       (not (some (fn [{:keys [key]}] (contains? errors key)) section-data))))

(defn- otp-field-not-set-up? [{:keys [data-type value]}]
  (and (= data-type ONE_TIME_PASSWORD_TYPE) (str/blank? value)))

(defn- move-not-set-up-otp-fields-to-end
  "In the edit mode an otp field that is not yet set up is shown as a 'Set up One-Time Password'
   button and such a field is moved after all the other fields of this section. Once the otp is
   set up, the field is shown in its usual place. Only the display order is changed here and
   the order of the fields in the form data itself remains the same"
  [edit section-data]
  (if-not edit
    section-data
    (into (filterv (complement otp-field-not-set-up?) section-data)
          (filterv otp-field-not-set-up? section-data))))

(defn section-content [{:keys [edit section-name section-data]}]
  (let [errors @(form-events/entry-form-field :error-fields)
        revealed-sections @(form-events/revealed-sections)
        section-data (move-not-set-up-otp-fields-to-end edit section-data)]
    ;; Show a section in edit mode irrespective of its contents except for the 'on demand'
    ;; sections that are shown only when the user asks for them;
    ;; In non edit mode a section is shown only
    ;; if it has some fields with non blank value. It is assumed the 'required'
    ;; fileds will have some valid values
    (when (if edit
            (not (section-hidden-on-demand? section-name section-data revealed-sections errors))
            (section-has-values? section-data))
      [rn-view {:style {:flexDirection "column"}}
       [section-header section-name]
       ;; In read mode the fields of the section are held in a card of their own. In edit mode
       ;; they stay in the single bordered box the whole form is drawn in
       [rn-view {:style (if edit {:flexDirection "column"} (fields-card-style))}
        (doall
         (for [{:keys [key
                      value
                      data-type
                      standard-field
                      select-field-options
                      required
                      password-score] :as kv} section-data]
          ;; All fields of this section is shown in edit mode. In case of non edit mode, 
          ;; all required fields and other fields with values are shown
          (when (or edit (not (str/blank? value))) #_(or edit (or required (not (str/blank? value))))
                (cond
                  (not (nil? select-field-options))
                  ^{:key key} [select-field {:text-label key #_(if required (str key "*") key)
                                             :options  (mapv (fn [v] {:key v :label v}) select-field-options)
                                             :value value
                                             :disabled (not edit)
                                             :on-change #(form-events/update-section-value-on-change
                                                          section-name key (.-label ^js/SelOption %))}]

                  (= data-type ONE_TIME_PASSWORD_TYPE)
                  ^{:key key} [otp-field (assoc kv
                                                :edit edit
                                                :section-name section-name
                                                :standard-field standard-field)]

                  ;; Boolean field (e.g. allowUntrustedCert) shows a Switch in both edit
                  ;; and non-edit mode; in non-edit mode the Switch is shown but disabled.
                  (= data-type BOOL_TYPE)
                  ^{:key key} [bool-field (assoc kv
                                                 :edit edit
                                                 :section-name section-name
                                                 :on-change-text #(form-events/update-section-value-on-change
                                                                   section-name key %))]

                  ;; Date field (core FieldDataType::Date) in edit mode shows a date picker.
                  ;; In non-edit mode it falls through to the plain text-field.
                  (and edit (= data-type DATE_TYPE))
                  ^{:key key} [date-field (assoc kv
                                                 :edit edit
                                                 :section-name section-name
                                                 :on-change-text #(form-events/update-section-value-on-change
                                                                   section-name key %))]

                  :else
                  ^{:key key} [text-field (assoc kv
                                                 :required false ;; make all fields as optional 
                                                 :section-name section-name
                                                 :edit edit
                                                 :error-text (get errors key)
                                                 :on-change-text #(form-events/update-section-value-on-change
                                                                   section-name key %)
                                                 :password-score password-score
                                                 :visible @(form-events/visible? key))]))))]])))

(defn all-sections-content []
  (let [{:keys [edit showing]
         {:keys [entry-type-uuid section-names section-fields]} :data} @(form-events/entry-form)
        parsed-fields @(form-events/entry-form-data-fields :parsed-fields)
        in-deleted-category @(form-events/deleted-category-showing)]
    (rnc/react-use-effect
     (fn []
       ;; cleanup fn is returned which is called when this component unmounts or any passed dependencies are changed
       ;; (println "all-sections-content effect init - showing edit in-deleted-category: " showing edit in-deleted-category)
       (when (and (= showing :selected) (not edit) (not in-deleted-category))
         ;; (println "From effect init entry-form-otp-start-polling is called")
         (form-events/entry-form-otp-start-polling))

       (fn []
         ;; (println "all-sections-content effect cleanup - showing edit in-deleted-category: " showing edit in-deleted-category)
         ;; (println "From effect cleanup entry-form-otp-stop-polling is called")
         (form-events/entry-form-otp-stop-polling)))

     ;; Need to pass the list of all reactive values (dependencies) referenced inside of the setup code or empty list
     (clj->js [showing edit in-deleted-category]))

    ;; section-names is a list of section names
    ;; section-fields is a list of map - one map for each field in that section
    ;; In read mode there is no box around all the sections - each one is a card of its own
    [rn-view {:style (if edit box-style-2 {:flexDirection "column"})}
     ;; Banner explaining the dual-use Password field and the
     ;; attach-private-key flow for REMOTE_CONNECTION_SFTP entries.
     (when (and edit (= entry-type-uuid const/UUID_OF_ENTRY_TYPE_REMOTE_CONNECTION_SFTP))
       [rn-view {:style {:padding 10
                         :margin-bottom 5
                         :background-color @rnc/secondary-container-color
                         :borderRadius 4}}
        [rnp-text {:style {:font-size 12}}
         (lstr-l "sftpEntryAuthHint")]])
     (doall
      (for [section-name section-names]
        ^{:key section-name} [section-content {:edit edit
                                               :section-name section-name
                                               :section-data (get-section-data  entry-type-uuid section-name section-fields parsed-fields)}]))]))

;; All the footer links are text buttons that are stacked one below the other. The icon and the
;; label of each link are left aligned within the button so that the icons of all these links
;; line up in a single column - see 'footer-links-stack'
(defn- footer-link-button [{:keys [label on-press]}]
  [rnp-button {:style {:margin-top 2 :margin-bottom 2}
               :contentStyle {:justifyContent "flex-start"}
               :mode "text"
               :icon const/ICON-PLUS
               :onPress on-press}
   label])

(defn- footer-links-stack
  "Lays out the footer links as a block that is centered in the form. The links themselves are
   left aligned within that block so that all their icons line up one below the other"
  [& links]
  [rn-view {:style {:flexDirection "column" :alignItems "center"}}
   (into [rn-view {:style {:flexDirection "column" :alignItems "flex-start"}}] links)])


(defn- standard-otp-field-set?
  "Returns true if the standard otp field of this entry has a value. Adding an
   'Additional One-Time Passwords' section makes sense only after that"
  [section-fields]
  (boolean (some (fn [{:keys [key value]}]
                   (and (= key const/OTP) (not (str/blank? value))))
                 (-> section-fields vals flatten))))

(defn- on-demand-section-link-shown?
  "Returns true if a link is to be shown for this hidden 'on demand' section"
  [section-name section-fields revealed-sections errors]
  (and (section-hidden-on-demand? section-name (get section-fields section-name) revealed-sections errors)
       (or (not= section-name ADDITIONAL_ONE_TIME_PASSWORDS)
           (standard-otp-field-set? section-fields))))

(defn- on-demand-section-links
  "A link for each 'on demand' section of this entry that is hidden in the edit mode.
   Pressing a link shows that section so that the user can add values to it"
  []
  (let [{:keys [edit] {:keys [section-names section-fields]} :data} @(form-events/entry-form)
        errors @(form-events/entry-form-field :error-fields)
        revealed-sections @(form-events/revealed-sections)
        hidden-sections (when edit
                          (filterv
                           #(on-demand-section-link-shown? % section-fields revealed-sections errors)
                           section-names))]
    (when (seq hidden-sections)
      (into [:<>]
            (for [section-name hidden-sections]
              ^{:key section-name}
              [footer-link-button {:label (lstr-section-name section-name)
                                   :on-press #(form-events/section-reveal section-name)}])))))

(defn- add-section-link []
  [footer-link-button {:label (lstr-bl 'additionalSection)
                       :on-press #(form-events/open-section-name-dialog)}])

(defn form-footer-content
  "The footer of the entry form in the edit mode. The links to show an 'on demand' section and
   to add a new section are kept in a box similar to the other content boxes of the form.
   All these links are in a single stack so that they line up one below the other"
  []
  (let [edit @(form-events/form-edit-mode)]
    (when edit
      [rn-view {:style (merge box-style-1 {:margin-top 5 :padding 5})}
       [footer-links-stack
        [on-demand-section-links]
        [add-section-link]]])))

(defn tags [edit]
  (let [entry-tags @(form-events/entry-form-data-fields :tags)
        tags-availble (boolean (seq entry-tags))]

    (when (or edit tags-availble)
      (let [chips [rn-view {:style {:flexDirection "column" :padding-top 10}}
                   [rn-view {:style {:flexDirection "row" :flexWrap "wrap"}}
                    (doall
                     (for [tag  entry-tags]
                       ^{:key tag} [rnp-chip {:style {:margin 5}
                                              :onClose (when edit
                                                         (fn []
                                                           (form-events/entry-form-data-update-field-value
                                                            :tags (filterv #(not= tag %) entry-tags))))} tag]))]]]
        (if-not edit
          ;; The tags read as one more section of the form - a quiet header with the chips on a
          ;; card below it
          [:<>
           [gl/section-header {:label (lstr-section-name 'tags)}]
           [rn-view {:style (merge (gl/card-block-style) {:padding 5 :min-height 50})}
            chips]]

          [rn-view {:style {:flexDirection "column" :justify-content "center"
                            :min-height 50  :margin-top 5 :padding 5 :borderWidth 0.20 :borderRadius 4}}
           [rn-view {:style {:flexDirection "row" :backgroundColor  @primary-container-color :min-height 25}}
            [rnp-text {:style {:alignSelf "center" :width "85%" :padding-left 15} :variant "titleMedium"}
             (lstr-section-name 'tags)]

            [rnp-icon-button {:icon const/ICON-PLUS :style {:height 35 :margin-right 0 :backgroundColor @on-primary-color}
                              :onPress (fn [] (cmn-events/tags-dialog-init-selected-tags entry-tags))}]]
           chips])))))

(defn uuid-times-content []
  (let [{:keys [uuid last-modification-time creation-time]} @(form-events/entry-form-data-fields
                                                              [:uuid :last-modification-time :creation-time])]
    [rn-view {:style (merge (gl/card-block-style)
                            {:margin-top 9 :padding 12})}
     [rn-view {:style {:flexDirection "row" :justify-content "space-between"}}
      [rnp-text {:variant "bodySmall" :style {:color @rnc/on-surface-variant}} "Uuid"]
      ;; The card is narrower than the box this used to be drawn in, so the uuid is allowed to
      ;; wrap rather than run past the card's edge
      [rnp-text {:variant "bodySmall" :style {:flexShrink 1 :textAlign "right" :margin-left 10}} uuid]]
     [rn-view {:style {:height 15}}]
     [rn-view {:style {:flexDirection "row" :justify-content "space-between"}}
      [rnp-text {:variant "bodySmall" :style {:color @rnc/on-surface-variant}} "Creation Time"]
      [rnp-text {:variant "bodySmall"} (utc-str-to-local-datetime-str creation-time)]]
     [rn-view {:style {:height 10}}]
     [rn-view {:style {:flexDirection "row" :justify-content "space-between"}}
      [rnp-text {:variant "bodySmall" :style {:color @rnc/on-surface-variant}} "Last Modification Time"]
      [rnp-text {:variant "bodySmall"} (utc-str-to-local-datetime-str last-modification-time)]]]))

;;;;;;;;;;;;;;;;;;;;  Attachment ;;;;;;;;;;;;;;;;;;;;

(def attachment-icons {"pdf" const/ICON-PDF
                       "txt" const/ICON-FILE
                       "jpg" const/ICON-FILE-JPG
                       "gif" const/ICON-FILE-JPG
                       "png" const/ICON-FILE-PNG})

(defn attachment-icon [file-name]
  (let [name (-> file-name (str/split ".") last str/lower-case)]
    (get attachment-icons name const/ICON-FILE-QUESTION-OUTLINE)))

(defn attachment-content-header [edit]
  (if-not edit
    [gl/section-header {:label (lstr-section-name 'attachments)}]

    [rn-view {:style {:flexDirection "row"
                      :backgroundColor  @primary-container-color
                      :margin-top 5
                      :min-height 35}}
     [rnp-text {:style {:alignSelf "center" :width "85%" :padding-left 15} :variant "titleMedium"} (lstr-section-name 'attachments)]
     [rnp-icon-button {:icon const/ICON-PLUS :style {:height 35
                                                     :margin-right 0
                                                     :backgroundColor @on-primary-color}
                       :onPress (fn [^js/PEvent _event]
                                  (form-events/upload-attachment)
                                  ;; Instead of the above action, use menu pop ups if we require more that upload action
                                  #_(show-attachment-menu event))}]]))

(defn attachment-row-item [{:keys [key data-size data-hash]} edit]
  (let [att-icon (attachment-icon key)
        size-str (u/to-file-size-str data-size)]
    [rnp-list-item
     {:onPress #(form-events/view-attachment key data-hash)
      :onLongPress  (fn [e]
                      (show-attachment-long-press-menu e edit key data-hash))
      :title (r/as-element
              [rnp-text {:variant "titleSmall"} key])
      :description size-str
      :left (fn [_props] (r/as-element
                          [rnp-list-icon
                           {:style {:align-self "center"}
                            :icon att-icon
                            :color @rnc/tertiary-color}]))}]))

(defn attachment-content []
  (let [{:keys [edit]
         {:keys [binary-key-values]} :data} @(form-events/entry-form)
        sections [{:title "Attachments"
                   :key "Attachments"
                   :data binary-key-values}]]

    (when (or edit (boolean (seq binary-key-values)))
      [rn-view {:style (if edit
                         (merge box-style-1 {:margin-top 5 :min-height 60})
                         {:flexDirection "column"})}
       [attachment-content-header edit]

       ;; We may see the warning/error in the console: 
       ;; VirtualizedLists should never be nested inside plain ScrollViews with the same orientation because 
       ;; it can break windowing and other functionality - use another VirtualizedList-backed container instead
       ;; :scrollEnabled false (from RN 0.71)  removes that error
       ;; See https://stackoverflow.com/questions/58243680/react-native-another-virtualizedlist-backed-container
       ;; https://stackoverflow.com/questions/67623952/error-virtualizedlists-should-never-be-nested-inside-plain-scrollviews-with-th

       [rn-view {:style (when-not edit (gl/card-block-style))}
        [rn-section-list {:scrollEnabled false
                          :sections (clj->js sections)
                          :renderItem (fn [props]
                                        (let [props (js->clj props :keywordize-keys true)]
                                          (r/as-element [attachment-row-item (-> props :item) edit])))
                          :ItemSeparatorComponent (fn [_p] (r/as-element [rnp-divider]))
                          :stickySectionHeadersEnabled false
                          :renderSectionHeader nil}]]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn main-content []
  (let [edit @(form-events/form-edit-mode)]
    ;; In read mode the cards carry their own side margins, so the page adds none of its own
    [rn-view {:style {:flexDirection "column" :justify-content "center" :padding (if edit 5 0)}}
     [entry-type-selection-box]
     [title-group-selection-box]
     [:f> all-sections-content]
     [notes edit]
     ;; Tags
     [tags edit]

     ;; Attachments panel will come here
     [attachment-content]

     ;; The footer links to add a section or to show an 'on demand' section are kept at the end.
     ;; A section shown from these links continues to appear in its own place in 'all-sections-content'
     [form-footer-content]

     (when-not edit [uuid-times-content])

     ;; Setup the menus. This ensures these menu components are called only once to initiate
     [section-menu @section-menu-dialog-data]
     [custom-field-menu @custom-field-menu-data]
     [attachment-long-press-menu @attachment-long-press-menu-data]

     ;; All entry form related dialogs
     [rnp-portal
      [add-modify-section-name-dialog @(form-events/section-name-dialog-data)]
      [add-modify-section-field-dialog @(form-events/section-field-dialog-data)]
      [select-tags-dialog @(cmn-events/tags-dialog-data) #(form-events/entry-form-data-update-field-value :tags %)]
      [delete-field-confirm-dialog @(form-events/field-delete-dialog-data)
       [{:label "yes"
         :on-press #(form-events/field-delete-confirm true)}
        {:label "no"
         :on-press #(form-events/field-delete-confirm false)}]]
      [history-entry-delete-dialog]
      [history-entry-restore-dialog]
      [confirm-delete-otp-field-dialog]
      [setup-otp-action-dialog]
      [otp-settings-dialog @(dlg-events/otp-settings-dialog-data)]
      (:dialog delete-attachment-dialog-info)
      [rename-attachment-name-dialog @rename-attachment-name-dialog-data]

      ;; The arg 'form-events/delete-entry' is call-on-ok-fn which is called after user confirm
      [cc/entry-delete-confirm-dialog form-events/delete-entry]
      [auto-open-db-file-required-info-dialog]
      [auto-open-key-file-pick-required-info-dialog]
      
      ;; Note: 
      ;; We are refering this dialog from ns entry-list. 
      ;; We may need to move some common ns if there is any circular reference issue comes up
      [entry-list/move-group-or-entry-dialog]
      [entry-list/clone-entry-dialog]]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  

(defn content []
  (let [edit @(form-events/form-edit-mode)]
    [rn-keyboard-avoiding-view {:style {:flex 1}
                                ;; After Android 'compileSdkVersion = 35 introduction
                                ;; Also see comments in js/components/KeyboardAvoidingDialog.js
                                :behavior (if (is-iOS) "padding" "height")}
     ;; In read mode the form stands on the same ground the list pages use so that its cards
     ;; read as cards. In edit mode the fields are still drawn in one box on a plain page
     [rn-scroll-view {:contentContainerStyle {:flexGrow 1
                                              :background-color (if edit
                                                                  @page-background-color
                                                                  @rnc/grouped-list-ground-color)}}
      [main-content]]]))

(comment
  (in-ns 'onekeepass.mobile.entry-form))
