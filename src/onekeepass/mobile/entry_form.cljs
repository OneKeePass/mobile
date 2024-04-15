(ns
 onekeepass.mobile.entry-form
  (:require [clojure.string :as str]
            [onekeepass.mobile.background :refer [is-Android is-iOS]]
            [onekeepass.mobile.common-components :as cc :refer [select-field
                                                                select-tags-dialog]]
            [onekeepass.mobile.constants :as const :refer [ADDITIONAL_ONE_TIME_PASSWORDS
                                                           ONE_TIME_PASSWORD_TYPE]]
            [onekeepass.mobile.date-utils :refer [utc-str-to-local-datetime-str]]
            [onekeepass.mobile.entry-form-dialogs :refer [add-modify-section-field-dialog
                                                          add-modify-section-name-dialog
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
            [onekeepass.mobile.entry-form-fields :refer [otp-field text-field]]
            [onekeepass.mobile.entry-form-menus :refer [attachment-long-press-menu
                                                        attachment-long-press-menu-data
                                                        custom-field-menu
                                                        custom-field-menu-data
                                                        section-menu
                                                        section-menu-dialog-data
                                                        section-menu-dialog-show
                                                        show-attachment-long-press-menu]]
            [onekeepass.mobile.events.common :as cmn-events]
            [onekeepass.mobile.events.dialogs :as dlg-events]
            [onekeepass.mobile.events.entry-form :as form-events]
            [onekeepass.mobile.icons-list :as icons-list]
            [onekeepass.mobile.rn-components
             :as rnc
             :refer [appbar-text-color dots-icon-name icon-color lstr
                     on-primary-color page-background-color
                     page-title-text-variant primary-container-color
                     rn-keyboard rn-keyboard-avoiding-view rn-scroll-view
                     rn-section-list rn-view rnp-button rnp-chip rnp-divider
                     rnp-helper-text rnp-icon-button rnp-list-icon
                     rnp-list-item rnp-portal rnp-text rnp-text-input
                     rnp-text-input-icon]]
            [onekeepass.mobile.utils :as u]
            [reagent.core :as r]))

;;(set! *warn-on-infer* true)

(def box-style-1 {:flexDirection "column"
                  :padding-right 5
                  :padding-left 5
                  :margin-bottom 5
                  :borderWidth .20
                  :borderRadius 4})

(def box-style-2 (merge box-style-1 {:padding-bottom 10 :padding-top 5}))

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
        (lstr "button.labels.cancel")]
       [rnp-text {:style {:color @appbar-text-color
                          :max-width 100
                          :margin-right 20 :margin-left 20}
                  :ellipsizeMode "tail"
                  :numberOfLines 1
                  :variant page-title-text-variant}
        (lstr "page.titles.entry")]
       [rnp-button {:style {}
                    :textColor @appbar-text-color
                    :disabled (not @(form-events/form-modified))
                    :mode "text"
                    :onPress (fn []
                               (.dismiss rn-keyboard)
                               (form-events/entry-save))}
        (lstr "button.labels.save")]]

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
        (lstr "button.labels.close")]
       [rnp-text {:style {:color @on-primary-color
                          :max-width "75%"
                          :margin-right 20 :margin-left 20}
                  :ellipsizeMode "tail"
                  :numberOfLines 1
                  :variant page-title-text-variant}
        (if is-history-entry (lstr "page.titles.historyEntry")
            (lstr "page.titles.entry"))]
       [rnp-button {:style {}
                    :textColor @on-primary-color
                    :disabled (or @(form-events/deleted-category-showing)
                                  is-history-entry)
                    :mode "text" :onPress form-events/edit-mode-on-press}
        (lstr "button.labels.edit")]])))

(declare clear-notes)

(defn entry-type-selection []
  (let [entry-types (clj->js
                     (mapv (fn [{:keys [name uuid]}]
                             {:key uuid :label name}) @(cmn-events/all-entry-type-headers)))
        entry-type-name-selection (form-events/entry-form-field :entry-type-name-selection)]
    [select-field {:text-label (str "Entry type" "*")
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
       [select-field {:text-label (str "Group/Category" "*")
                      :options names
                      :value group-selected-name
                      :on-change on-change}]
       [rnp-helper-text {:type "error" :visible true} error-text]]

      [select-field {:text-label (str "Group/Category" "*")
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
  "A callback function that is called from :common/icon-selected event handler when the user selects a new icon"
  [_icon-name icon-id]
  (form-events/edit-mode-on-press)
  (form-events/entry-form-data-update-field-value :icon-id icon-id))

(defn android-title-text-input [title icon-name]
  [rnp-text-input {:style {:width "100%"}
                   :label (str "Title" "*")
                   :autoCapitalize "none"
                   :defaultValue title
                   :ref (fn [^js/Ref ref]
                               ;; Keys found in ref for textinput
                               ;; are #js ["focus" "clear" "setNativeProps" "isFocused" "blur" "forceFocus"]
                               ;; Need to call clear directly as the previous value is not getting cleared 
                               ;; when there is a change in entry type selection name
                          (when (and (not (nil? ref)) (str/blank? title)) (.clear ref)))
                   :onChangeText #(form-events/entry-form-data-update-field-value :title %)
                   :right (r/as-element
                           [rnp-text-input-icon {:iconColor @icon-color
                                                 :icon icon-name
                                                 :onPress #(cmn-events/show-icons-to-select on-entry-icon-selection)}])}])

(defn ios-title-text-input [title icon-name]
  [rnp-text-input {:style {:width "100%"}
                   :label (str "Title" "*")
                   :autoCapitalize "none"
                   :value title
                   :onChangeText #(form-events/entry-form-data-update-field-value :title %)
                   :right (r/as-element
                           [rnp-text-input-icon {:iconColor @icon-color
                                                 :icon icon-name
                                                 :onPress #(cmn-events/show-icons-to-select on-entry-icon-selection)}])}])

(defn title-with-icon []
  (let [{:keys [title icon-id]} @(form-events/entry-form-data-fields [:title :icon-id])
        icon-name (icons-list/icon-id->name icon-id)
        edit @(form-events/form-edit-mode)
        error-fields @(form-events/entry-form-field :error-fields)]
    (if edit
      [rn-view {:style {:margin-top 2 :margin-bottom 2}}
       (if (is-iOS)
         [ios-title-text-input title icon-name]
         [android-title-text-input title icon-name])
       (when (contains? error-fields :title)
         [rnp-helper-text {:type "error" :visible (contains? error-fields :title)}
          (:title error-fields)])]
      [rn-view {:style {:flexDirection "row" :justify-content "center" :alignItems "center"}}
       [rnp-list-icon {:style {} :icon icon-name :color @icon-color}]
       [rn-view {:style {:width 10}}] ;; gap
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

(def notes-ref (atom nil))

(defn clear-notes []
  (when @notes-ref (.clear ^js/Ref  @notes-ref)))

(defn notes [edit]
  (let [value @(form-events/entry-form-data-fields :notes)]
    (when (or edit (not (str/blank? value)))
      [rn-view {:style {:padding-right 5 :padding-left 5 :borderWidth .20 :borderRadius 4}}
       [rnp-text-input {:style {:width "100%"} :multiline true :label (lstr "notes")
                        :defaultValue value
                        :ref (fn [^js/Ref ref]
                               (reset! notes-ref ref)
                               (when (and (is-Android) (not (nil? ref)) (str/blank? value)) (.clear ref)))
                        :showSoftInputOnFocus edit
                        :onChangeText (when edit #(form-events/entry-form-data-update-field-value :notes %))}]])))

(defn section-header [section-name]
  (let [edit @(form-events/entry-form-field :edit)
        standard-sections @(form-events/entry-form-data-fields :standard-section-names)]
    [rn-view {:style {:flexDirection "row"
                      :backgroundColor  @primary-container-color
                      :margin-top 5
                      :min-height 35}}
     [rnp-text {:style {:alignSelf "center" :width "85%" :padding-left 15} :variant "titleMedium"} section-name]
     (when edit
       ;;
       (if (not= section-name ADDITIONAL_ONE_TIME_PASSWORDS)
         [rnp-icon-button {:icon dots-icon-name :style {:height 35
                                                        :margin-right 0
                                                        :backgroundColor @on-primary-color}
                           :onPress (fn [^js/PEvent event]
                                      (section-menu-dialog-show {:section-name section-name
                                                                 :is-standard-section (u/contains-val? standard-sections section-name)
                                                                 :event event}))}]

         [rnp-icon-button {:icon const/ICON-PLUS
                           :style {:height 35 :margin-right 0 :backgroundColor @on-primary-color}
                           :onPress (fn [] (form-events/show-form-fields-validation-error-or-call
                                            #(setup-otp-action-dialog-show section-name nil false)))}]))]))

(defn section-content [edit section-name section-data]
  (let [errors @(form-events/entry-form-field :error-fields)]
    ;; Show a section in edit mode irrespective of its contents; 
    ;; In non edit mode a section is shown only 
    ;; if it has some fields with non blank value. It is assumed the 'required' 
    ;; fileds will have some valid values
    (when (or edit (boolean (seq (filter (fn [kv] (not (str/blank? (:value kv)))) section-data))))
      [rn-view {:style {:flexDirection "column"}}
       [section-header section-name]
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

                  :else
                  ^{:key key} [text-field (assoc kv
                                                 :required false ;; make all fields as optional 
                                                 :section-name section-name
                                                 :edit edit
                                                 :error-text (get errors key)
                                                 :on-change-text #(form-events/update-section-value-on-change
                                                                   section-name key %)
                                                 :password-score password-score
                                                 :visible @(form-events/visible? key))]))))])))

(defn all-sections-content []
  (let [{:keys [edit showing]
         {:keys [section-names section-fields]} :data} @(form-events/entry-form)
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
    [rn-view {:style box-style-2}
     (doall
      (for [section-name section-names]
        ^{:key section-name} [section-content edit section-name (get section-fields section-name)]))]))

(defn add-section-btn []
  [rn-view {:style {:padding-top 5 :padding-bottom 5}  :justify-content "center"}
   [rnp-button {:mode "contained" :onPress #(form-events/open-section-name-dialog)} "Additional section and custom fields"]])

(defn tags [edit]
  (let [entry-tags @(form-events/entry-form-data-fields :tags)
        tags-availble (boolean (seq entry-tags))]
    ;;(println "entry-tags empty-tags " entry-tags tags-availble edit)
    (when (or edit tags-availble)
      [rn-view {:style {:flexDirection "column" :justify-content "center"  :min-height 50  :margin-top 5 :padding 5 :borderWidth .20 :borderRadius 4}}
       [rn-view {:style {:flexDirection "row" :backgroundColor  @primary-container-color :min-height 25}}
        [rnp-text {:style {:alignSelf "center" :width "85%" :padding-left 15} :variant "titleMedium"} "Tags"]
        (when edit
          [rnp-icon-button {:icon const/ICON-PLUS :style {:height 35 :margin-right 0 :backgroundColor @on-primary-color}
                            :onPress (fn [] (cmn-events/tags-dialog-init-selected-tags entry-tags))}])]
       [rn-view {:style {:flexDirection "column" :padding-top 10}}
        [rn-view {:style {:flexDirection "row" :flexWrap "wrap"}}
         (doall
          (for [tag  entry-tags]
            ^{:key tag} [rnp-chip {:style {:margin 5}
                                   :onClose (when edit
                                              (fn []
                                                (form-events/entry-form-data-update-field-value
                                                 :tags (filterv #(not= tag %) entry-tags))))} tag]))]]])))

(defn uuid-times-content []
  (let [{:keys [uuid last-modification-time creation-time]} @(form-events/entry-form-data-fields
                                                              [:uuid :last-modification-time :creation-time])]
    [rn-view {:style {:margin-top 20}}
     [rn-view {:style {:justify-content "space-between"} :flexDirection "row"}
      [rnp-text "Uuid"]
      [rnp-text uuid]]
     [rn-view {:style {:height 15}}]
     [rn-view {:style {:justify-content "space-between"} :flexDirection "row"}
      [rnp-text "Creation Time"]
      [rnp-text (utc-str-to-local-datetime-str creation-time)]]
     [rn-view {:style {:height 10}}]
     [rn-view {:style {:justify-content "space-between"} :flexDirection "row"}
      [rnp-text "Last Modification Time"]
      [rnp-text (utc-str-to-local-datetime-str last-modification-time)]]]))

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
  [rn-view {:style {:flexDirection "row"
                    :backgroundColor  @primary-container-color
                    :margin-top 5
                    :min-height 35}}
   [rnp-text {:style {:alignSelf "center" :width "85%" :padding-left 15} :variant "titleMedium"} "Attachment"]
   (when edit
     [rnp-icon-button {:icon const/ICON-PLUS :style {:height 35
                                                     :margin-right 0
                                                     :backgroundColor @on-primary-color}
                       :onPress (fn [^js/PEvent _event]
                                  (form-events/upload-attachment)
                                  ;; Instead of the above action, use menu pop ups if we require more that upload action
                                  #_(show-attachment-menu event))}])])

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
      [rn-view {:style (merge box-style-1 {:margin-top 5 :min-height 60})}
       [attachment-content-header edit]

           ;; We may see the warning/error in the console: 
           ;; VirtualizedLists should never be nested inside plain ScrollViews with the same orientation because 
           ;; it can break windowing and other functionality - use another VirtualizedList-backed container instead
           ;; :scrollEnabled false (from RN 0.71)  removes that error
           ;; See https://stackoverflow.com/questions/58243680/react-native-another-virtualizedlist-backed-container
           ;; https://stackoverflow.com/questions/67623952/error-virtualizedlists-should-never-be-nested-inside-plain-scrollviews-with-th

       [rn-section-list {:scrollEnabled false
                         :sections (clj->js sections)
                         :renderItem (fn [props]
                                       (let [props (js->clj props :keywordize-keys true)]
                                         (r/as-element [attachment-row-item (-> props :item) edit])))
                         :ItemSeparatorComponent (fn [_p] (r/as-element [rnp-divider]))
                         :stickySectionHeadersEnabled false
                         :renderSectionHeader nil}]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn main-content []
  (let [edit @(form-events/form-edit-mode)]
    [rn-view {:style {:flexDirection "column" :justify-content "center" :padding 5}}
     [entry-type-selection-box]
     [title-group-selection-box]
     [:f> all-sections-content]
     (when edit [add-section-btn])
     [notes edit]
     ;; Tags
     [tags edit]

     ;; Attachments panel will come here 
     [attachment-content]

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
       [{:label (lstr "button.labels.yes")
         :on-press #(form-events/field-delete-confirm true)}
        {:label (lstr "button.labels.no")
         :on-press #(form-events/field-delete-confirm false)}]]
      [history-entry-delete-dialog]
      [history-entry-restore-dialog]
      [confirm-delete-otp-field-dialog]
      [setup-otp-action-dialog]
      [otp-settings-dialog @(dlg-events/otp-settings-dialog-data)]
      (:dialog delete-attachment-dialog-info)
      [rename-attachment-name-dialog @rename-attachment-name-dialog-data]
      [cc/entry-delete-confirm-dialog form-events/delete-entry]]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  

(defn content []
  [rn-keyboard-avoiding-view {:style {:flex 1}
                              :behavior (if (is-iOS) "padding" nil)}
   [rn-scroll-view {:contentContainerStyle {:flexGrow 1 :background-color @page-background-color}}
    [main-content]]])

(comment
  (in-ns 'onekeepass.mobile.entry-form))
