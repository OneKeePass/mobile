(ns
 onekeepass.mobile.entry-form-fields

  (:require [clojure.string :as str]
            [onekeepass.mobile.background :refer [is-Android is-iOS]]
            [onekeepass.mobile.common-components :as cc]
            [onekeepass.mobile.constants :as const :refer [OTP PASSWORD URL]]
            [onekeepass.mobile.entry-form-dialogs :as ef-dlg  :refer [setup-otp-action-dialog-show]]
            [onekeepass.mobile.events.entry-form-auto-open :as ef-ao]
            [onekeepass.mobile.entry-form-menus :refer [custom-field-menu-show]]
            [onekeepass.mobile.events.common :as cmn-events]
            [onekeepass.mobile.events.entry-form :as form-events]
            [onekeepass.mobile.events.password-generator :as pg-events]
            [onekeepass.mobile.events.remote-storage :as rs-events]
            [onekeepass.mobile.otp-badge :refer [formatted-token]]
            [onekeepass.mobile.rn-components
             :as rnc :refer [animated-circular-progress dots-icon-name
                             no-assist-text-props
                             page-background-color rn-keyboard rn-text-input
                             rn-view rnp-button
                             rnp-helper-text rnp-icon-button rnp-switch rnp-text
                             rnp-text-input rnp-text-input-icon]]
            [onekeepass.mobile.translation :refer [lstr-bl lstr-field-name
                                                   lstr-l]]
            [reagent.core :as r]))

(defn to-field-label [{:keys [standard-field key field-name]}]
  (cond
    ;; It is assumed translation is done already
    (not (nil? field-name))
    field-name

    standard-field
    (lstr-field-name key)

    ;; It is assumed translation is done already
    :else
    key))

(defn to-field-value [{:keys [value read-value edit]}]
  (cond
    edit
    value

    (and (not edit) (not (nil? read-value)))
    read-value

    :else
    value))

(defn read-field-background
  "In read mode the fields of a section sit on a card of the grouped list kind, so they have to
   carry that card's color, which differs from the page background in the dark theme"
  []
  @rnc/grouped-list-card-color)

;; The read mode field whose copy icon was last pressed, or nil. It is drawn with the underline and
;; the label of a focused input so that the form shows which field was copied
(def ^:private pressed-read-field (r/atom nil))

(defn- pressed-read-field? [key]
  (= key @pressed-read-field))

;; Height of the line drawn under a pressed read mode field. The same as the underline a focused
;; paper flat input draws. It is laid over the resting line rather than replacing it, so that the
;; field does not shift as the line thickens
(def ^:private read-field-active-underline-height 2)

(defn- read-field-active-underline
  "The thicker colored line a read mode field carries while it is the pressed one"
  [key]
  (when (pressed-read-field? key)
    [rn-view {:style {:position "absolute"
                      :left 0 :right 0 :bottom 0
                      :height read-field-active-underline-height
                      :background-color @rnc/primary-color}}]))

(defn- read-field-label-color [key]
  (if (pressed-read-field? key) @rnc/primary-color @rnc/outline-color))

(defn- read-field-on-press
  "Pressing the copy icon of a read mode field copies its full value to the clipboard and leaves
   that field looking like the focused one.

   Dropping the keyboard first blurs whichever text input of the form holds the focus - pressing
   a view of our own does not take the focus off a native input on either platform - so that only
   one field of the form is ever drawn as focused"
  [{:keys [key protected] :as kv}]
  (fn []
    ;; The backend requires a boolean, and the notes field for one is not given a :protected value
    (cmn-events/write-string-to-clipboard {:field-name key
                                           :protected (boolean protected)
                                           :value (to-field-value kv)})
    (.dismiss rn-keyboard)
    (reset! pressed-read-field key)))

(defn- clear-pressed-read-field
  "Takes the mark off the given field if it is the one carrying it"
  [key]
  (swap! pressed-read-field (fn [pressed] (when-not (= pressed key) pressed))))

(defn- visibility-toggle-on-press
  "Shows or masks the value of a protected field.

   In edit mode a multi line field is remounted by this change - see 'mask-remount-key'. Android
   then hands the focus that input held to another text input on the page, and that one draws
   its underline and its label in the active color as though the user had tapped it. iOS does
   not move focus in that way

   Dropping whatever holds the focus once the change has been made leaves the other fields
   looking as they did. 'Keyboard.dismiss' is what does it - it blurs the currently focused
   input. The wait is for React to have taken the input out of the tree first, since it is
   only then that Android hands the focus on"
  [key]
  (fn []
    (form-events/entry-form-field-visibility-toggle key)
    ;; The mark shows the field that was copied, which is not what pressing the eye icon does
    (clear-pressed-read-field key)
    (when (is-Android)
      (js/setTimeout (fn [] (.dismiss rn-keyboard)) 0))))

(def ^:private field-focused (r/atom {:key nil :focused false}))

(defn field-focus-action [key flag]
  (swap! field-focused assoc :field-name key :focused flag))

;; Some field values carry embedded new lines - the passkey private key is stored as a pem with
;; LF line endings and an ssh key is the same. A text input shows such a value on a single line
;; and the rest of it can only be reached by scrolling, so it is shown here in an input that
;; grows to fit all of its lines. The growth is capped so that a long key scrolls within the
;; field instead of taking over the whole form
(def ^:private multi-line-field-max-height 240)

;; A masked value cannot grow - secureTextEntry does not work along with the multiline prop. Its
;; new lines are still laid out as line breaks though and, as a text input does not clip by
;; default, the masking dots of a long value spill out of the field and paint over the rest of
;; the form. Such a field is held to the height of a single line field with its overflow hidden
;; so that only the first line of dots is seen and the value is clipped rather than altered to
;; fit
;;
;; Both platforms leave the paper flat input at its own height, which is 56 for the material 3
;; theme the app uses
(def ^:private masked-field-max-height 56)

(defn- multi-line-value? [v]
  (and (string? v) (str/includes? v "\n")))

;; The named fields are treated as multi line even when the value they hold right now has no new
;; line in it - see 'const/MULTI_LINE_FIELD_NAMES'
;;
;; For a key field that is what lets a multi line value be entered in the first place. Such a
;; field is revealed when the editing starts so that it is laid out as a multi line input and a
;; pasted key keeps its line breaks
;;
;; The additional urls are named there for a different reason - the value is a single line of
;; urls separated by a space, and one long url or several of them are cut off at the field's
;; one line unless the field is allowed to grow and wrap
(defn- multi-line-field? [key val]
  (or (contains? const/MULTI_LINE_FIELD_NAMES key)
      (multi-line-value? val)))

;; Revealing a multi line field turns the multiline prop on and masking it again turns it off.
;; Both platforms rebuild the native text input when that prop changes - iOS swaps the backing
;; text view for the other kind and android rebuilds the input type - and the input loses focus
;; while it happens, which makes the paper label animate away and back. Remounting the input
;; instead makes the change a plain swap with no animation, and the value is not lost as it is
;; read back from the form data by the fresh input.
;;
;; Only a field that is multi line can flip that prop, so this stays the same for every other
;; field and the reveal toggle of an ordinary protected field like the password is untouched
(defn- mask-remount-key [multi-line? masked?]
  (and multi-line? masked?))

;; Android text input component issue - inputing slow and if any character changed in the middle of text 
;; the cursor moves to the left
;; Solution:
;;   Need to use defaultValue. Also we need to use ref clear fn so that any previous default value shown is cleared
;;   when there is a entry type selection change. 
;; If we do not call explicit call to clear using ref, then following is noticed 
;; If user enters some text and when entry type is changed, we expect all fields 
;; should be blank. But because of the defaultValue prop is used, the old texts entered keeps showing 
;; though in the backend db it is blank or nil
(defn android-form-text-input [{:keys [key
                                       value

                                       protected
                                       visible
                                       on-change-text
                                       icon-space-required] :as kv}]
  (let [label (to-field-label kv)
        val (to-field-value kv)
        masked? (and protected (not visible))
        multi-line? (multi-line-field? key val)
        ;; A masked value is left as a single line as secureTextEntry does not work with multiline.
        ;; Revealing such a field is what turns it into a multi line input - in edit mode that is
        ;; also the only way a key with its line breaks can be typed or pasted into it.
        grow? (and multi-line? (not masked?))]

    ;; (println "Lable is " label)
    ;; We need to use this ^:key so that text-input field is unique
    ^{:key (str key protected (mask-remount-key multi-line? masked?))} [rnp-text-input (merge
                                                      no-assist-text-props
                                                      {:label label
                                                      :defaultValue val
                                                      ;; :value val
                                                      :ref (fn [^js/Ref ref]
                                                             (when (and (not (nil? ref)) (str/blank? value)) (.clear ref)))
                                                      :keyboardType "default" #_(if-not protected "email-address" "default")
                                                      :style (merge {:width (if icon-space-required  "90%" "100%")}
                                                                    (cond
                                                                      grow?
                                                                      {:max-height multi-line-field-max-height}

                                                                      ;; Only the first line of a masked multi line
                                                                      ;; value is seen and the rest is clipped
                                                                      (and masked? (multi-line-value? val))
                                                                      {:max-height masked-field-max-height :overflow "hidden"}))
                                                      ;; If multiline true, then secureTextEntry is not working
                                                      :multiline (or grow? (not protected))
                                                      :onFocus #(field-focus-action key true)
                                                      :onBlur #(field-focus-action key false)
                                                      :onChangeText on-change-text
                                                      :secureTextEntry masked?
                                                      ;; It looks like we can have only one icon
                                                      :right (when protected
                                                               (if visible
                                                                 (r/as-element [rnp-text-input-icon
                                                                                {:icon "eye"
                                                                                 :onPress (visibility-toggle-on-press key)}])
                                                                 (r/as-element [rnp-text-input-icon
                                                                                {:icon "eye-off"
                                                                                 :onPress (visibility-toggle-on-press key)}])))}
                                                      ;; 'numberOfLines' caps how many lines the Android input shows.
                                                      ;; It is left out for a value that has to grow to fit all its lines
                                                      (when-not grow? {:numberOfLines 1})
)]))


;; There are somes issues seen while using multiline in iOS and added comments below on these observations

;; With "react-native-paper" 5.12.3, a multiline text input in iOS did not show its label when the
;; input had some value - see https://github.com/callstack/react-native-paper/issues/4482
;; From 5.15.3 paper draws a patch behind the label of a non Android multiline input and the label
;; stays visible, so :multiline can be used here for the values that need more than one line

;; In iOS, we do not see the same issue as seen with the use of text input in android 
;; However while using :multiline prop, 
;; the editing was not working properly -not showing field value while changing to read from edit
;; See the comments of using :defaultValue

(defn ios-form-text-input [{:keys [key
                                   value
                                   protected
                                   visible
                                   on-change-text
                                   icon-space-required] :as kv}]
  (let [label (to-field-label kv)
        val (to-field-value kv)
        masked? (and protected (not visible))
        multi-line? (multi-line-field? key val)
        ;; A masked value is left as a single line as secureTextEntry does not work with multiline.
        ;; Revealing such a field is what turns it into a multi line input - in edit mode that is
        ;; also the only way a key with its line breaks can be typed or pasted into it.
        grow? (and multi-line? (not masked?))]
    ;; iOS does not otherwise need a key - unlike android this input is a controlled one and its
    ;; ':value' cannot go stale. It is used only to remount on a mask change - see the comments
    ;; of 'mask-remount-key'
    ^{:key (str key (mask-remount-key multi-line? masked?))}
    [rnp-text-input (merge
                     no-assist-text-props
                     {:label label
                     ;; In 0.16.0, while using multiline = true, edit did not work with :value val and need to use :defaultValue
                     :value val
                     ;;:defaultValue val

                     ;; Using the 'keyboardType email-address' will not show ":" in the keyboard (0.16.0)
                     ;; :keyboardType "email-address"

                     ;; :contextMenuHidden true

                     :selectTextOnFocus false

                       ;; Sometime in iOS when a text input has its secureTextEntry with true value
                       ;; Strong Password prompt comes up and hides the actual input box preventing any entry
                       ;; Particularly it happened with Simulator. For now, we can disable the Password AutoFill feature
                       ;; in the Simulator Phone's Settings or setting the textContentType as shown below also works
                       ;; On device, this behaviour is not seen
                       ;; :textContentType (if (or (not protected) visible) nil "newPassword")
                     :style (merge {:width (if icon-space-required  "90%" "100%")}
                                   (cond
                                     grow?
                                     {:max-height multi-line-field-max-height}

                                     ;; Only the first line of a masked multi line value is seen
                                     ;; and the rest is clipped
                                     (and masked? (multi-line-value? val))
                                     {:max-height masked-field-max-height :overflow "hidden"}))

                     ;; Sometimes when kdbx url is long, the full text is not shown
                     ;; When the field is focused, we are able to see full text by scrolling
                     ;; :autoFocus non-edit-kdbx-url

                    ;; multiline cannot be used for a masked value as secureTextEntry does not
                    ;; work along with it. 'grow?' already excludes a masked field
                     :multiline grow?

                    ;;  :ref (fn [^js/Ref ref]
                    ;;         (when-not (nil? ref)
                    ;;           (when-not non-edit-kdbx-url
                    ;;             (.blur ref))))

                     :onFocus #(field-focus-action key true)
                     :onBlur #(field-focus-action key false)
                     :onChangeText on-change-text
                     :secureTextEntry masked?
                     ;; It looks like we can have only one icon
                     :right (when protected
                              (if visible
                                (r/as-element [rnp-text-input-icon
                                               {:icon "eye"
                                                :onPress (visibility-toggle-on-press key)}])
                                (r/as-element [rnp-text-input-icon
                                               {:icon "eye-off"
                                                :onPress (visibility-toggle-on-press key)}])))}
)]))

;; Read mode monospace + per character coloring is applied only to the protected fields whose
;; key is in this set. A password and the passkey secret fields are random looking strings and
;; benefit from this. Other protected fields (e.g a free text security answer) are left as plain
;; text. Add a field key here to opt it in
(def ^:private colorable-protected-field-names
  #{PASSWORD
    const/KPEX_PASSKEY_USER_HANDLE
    const/KPEX_PASSKEY_CREDENTIAL_ID})

;; A masked value is always shown with this many dots so that its length is not given away
(def ^:private masked-read-value "••••••••")

;; Two icons can sit side by side at the end of a field, so the outer margins are dropped to leave
;; more of the row to the value
(def ^:private read-field-icon-style {:margin-left 0 :margin-right 0})

(defn- read-field-row
  "Lays out a read mode field the way the flat text input of the edit mode is - the label on top,
   the value below it and a thin underline - with a copy icon at the end of the row

   Only the icon copies. Touching the value does not, so that a scroll or the system swipe to
   switch apps that starts on a field cannot replace what the user just copied, and so that a
   long press is left free for selecting a part of the value

   Args:
     kv           the field's map - :key and the value are what gets copied. Its label comes from
                  'to-field-label'
     value-view   the component showing the value
     action-view  optional component placed just before the copy icon
  "
  [{:keys [key]} _value-view _action-view]
  (r/create-class
   {:display-name "read-field-row"

    ;; The mark is held per field name, and so would otherwise show up on the same field of the
    ;; next entry opened or when coming back from the edit mode
    :component-will-unmount
    (fn [] (clear-pressed-read-field key))

    :reagent-render
    (fn [{:keys [key] :as kv} value-view action-view]
      [rn-view {:style {:width "100%"
                        :flexDirection "row"
                        :align-items "center"
                        :min-height 60
                        :border-bottom-width 0.5
                        :border-bottom-color @rnc/outline-color
                        :backgroundColor (read-field-background)}}
       [rn-view {:style {:flex 1
                         :padding-left 16
                         :padding-top 8
                         :padding-bottom 8}}
        [rnp-text {:variant "bodySmall" :style {:color (read-field-label-color key)}}
         (to-field-label kv)]
        value-view]
       action-view
       [rnp-icon-button {:style read-field-icon-style
                         :icon "content-copy"
                         :onPress (read-field-on-press kv)}]
       [read-field-active-underline key]])}))

(defn- selectable-read-value
  "Shows a plain text value so that a part of it can be selected and copied with the system menu.

   Neither component does this on both platforms. On iOS a selectable 'Text' can only copy its
   whole text. On Android a text input with 'editable' false is disabled and cannot be selected.
   iOS keeps selection on a non editable input only when it is multiline, as the single line one
   is disabled just as Android's is. The menu of either one offers no cut or paste

   'textBreakStrategy' is explained in 'read-field-value'"
  [val]
  (if (is-iOS)
    ;; A bare input without the padding it has by default, so that its text lines up with the
    ;; label above it. It does not scroll on its own and the page scrolls instead
    [rn-text-input {:value val
                    :editable false
                    :multiline true
                    :scrollEnabled false
                    :style {:padding 0
                            :paddingTop 0
                            :fontSize 14
                            :color @rnc/on-background-color}}]
    [rnp-text {:selectable true :textBreakStrategy "simple"} val]))

(defn- read-field-value
  "The value of a read mode field - dots when it is masked, colored characters for a revealed
   password like value, monospaced text for a key and otherwise selectable plain text"
  [{:keys [key protected visible] :as kv}]
  (let [val (to-field-value kv)]
    (cond
      (and protected (not visible))
      [rnp-text {:style {:fontSize 18 :letterSpacing 2}} masked-read-value]

      (and protected (contains? colorable-protected-field-names key))
      [cc/colored-password val true {:style {:fontSize 18 :letterSpacing 0.5}}]

      ;; A key is monospaced and a bit smaller as a pem or an openssh text reads better that way
      ;; and more of it fits on a line
      ;;
      ;; The whole value is shown - the form is inside a scroll view and so a long value is read
      ;; by scrolling the page rather than scrolling within the field
      ;; 'textBreakStrategy' is an android only prop and iOS ignores it. Android breaks the lines
      ;; of a text with the high quality strategy by default, which ends a line well before the
      ;; width it has for a value that is one long run of letters and digits like a pem body. The
      ;; simple strategy fills each line as far as it goes, which is what iOS does
      (contains? const/KEY_FIELD_NAMES key)
      [rnp-text {:textBreakStrategy "simple"
                 :style {:fontFamily rnc/monospace-font-family :fontSize 14}} val]

      :else
      [selectable-read-value val])))

(defn read-field
  "Shows a field of the entry form in read mode. See 'read-field-row'. A protected field also
   gets the eye icon that reveals or masks its value and :action-view, when given, is an icon
   placed next to the copy icon"
  [{:keys [key protected visible action-view] :as kv}]
  [read-field-row kv
   [read-field-value kv]
   [:<>
    (when protected
      [rnp-icon-button {:style read-field-icon-style
                        :icon (if visible "eye" "eye-off")
                        :onPress (visibility-toggle-on-press key)}])
    action-view]])

(defn- read-field-launch-icon [on-press]
  [rnp-icon-button {:style read-field-icon-style
                    :icon const/ICON-LAUNCH
                    :onPress on-press}])

(defn text-field
  "Called to show form fields"
  [{:keys [key
           value
           _field-name
           protected
           standard-field
           required
           data-type
           edit
           on-change-text
           error-text
           helper-text
           section-name]
    :or {edit false
         protected false
         on-change-text #(println (str "No on change text handler yet registered for " key))
         required false}
    :as kvm}]
  (let [cust-color (if edit @page-background-color (read-field-background))
        is-password-edit? (and edit (= key PASSWORD))
        entry-type-uuid @(form-events/entry-form-data-fields :entry-type-uuid)
        entry-uuid @(form-events/entry-form-uuid)
        ;; Read-mode launch of the remote Storage Browser from the connection
        ;; field of an SFTP/WebDAV connection entry: Host for SFTP, URL for WebDAV.
        rs-conn-launch? (and (not edit)
                             (or (and (= entry-type-uuid const/UUID_OF_ENTRY_TYPE_REMOTE_CONNECTION_SFTP)
                                      (= key const/HOST))
                                 (and (= entry-type-uuid const/UUID_OF_ENTRY_TYPE_REMOTE_CONNECTION_WEBDAV)
                                      (= key URL))))
        ;; kdbx:// or https:// or http. For a WebDAV connection entry the URL
        ;; field shows the storage-launch icon instead of the open-url icon.
        non-edit-kdbx-url (and (not edit) (= key URL) (not rs-conn-launch?))
        custom-field-edit-focused? (if (is-iOS)
                                     (and
                                      edit
                                      (= (:field-name @field-focused) key)
                                      ;; Sometimes pressing on the custom field menu icon is not working
                                      ;; It starts working when some input action is done in the custom field
                                      ;; and now if press on the menu icon, it works. By removing focused check as below
                                      ;; the pressing on custom icon works. But first time when press Soft KB hides and then
                                      ;; again we need to press for menu popup
                                      ;;(:focused @field-focused)   <- See above comments 
                                      (not standard-field))
                                     ;; In Android, we cannot use :focused as onBlur sets false and dot-icon is hidden
                                     (and
                                      edit
                                      (= (:field-name @field-focused) key)
                                      (not standard-field)))
        icon-space-required (or is-password-edit? custom-field-edit-focused?)
        read-action-view (cond
                           non-edit-kdbx-url
                           [read-field-launch-icon #(ef-ao/entry-form-open-url value)]

                           ;; Launch the remote Storage Browser using this connection entry
                           rs-conn-launch?
                           [read-field-launch-icon #(rs-events/open-entry-remote entry-type-uuid entry-uuid)])]
    [rn-view {:flexDirection "column"}
     ;; text input field and optional icon button in the same row
     [rn-view {:flexDirection "row" :style {:flex 1}}
      ;; First platform specific text input field
      (cond
        (not edit)
        [read-field (assoc kvm :action-view read-action-view)]

        (is-iOS)
        [ios-form-text-input (assoc kvm :icon-space-required icon-space-required)]

        :else
        [android-form-text-input (assoc kvm :icon-space-required icon-space-required)])
      ;; An icon to launch PasswordGenerator
      (when is-password-edit?
        [rn-view {:style {:margin-left -5 :backgroundColor cust-color}}
         [rnp-icon-button {:style {:margin-right 0}
                           :icon "cached"
                           ;; on-change-text is a single argument function
                           ;; This function is called when the generated password is selected in Generator page
                           :onPress #(pg-events/generate-password on-change-text)}]])

      ;; An icon to show custom field edit menus
      ;; In Android, when we press this dot-icon, the custom field first loses focus and the keyborad dimiss takes place 
      ;; and then menu pops up only when we press second time the dot-icon
      (when custom-field-edit-focused?
        [rn-view {:style {:margin-left -5 :backgroundColor cust-color}}
         [rnp-icon-button {:style {:margin-right 0}
                           :icon dots-icon-name
                           :onPress #(custom-field-menu-show % section-name key protected required data-type)}]])]

     ;; Any error text below the field, otherwise the field's helper text (e.g. CVC, Additional URLs)
     (cond
       (and edit (not (nil? error-text)))
       [rnp-helper-text {:type "error" :visible true} error-text]

       (and edit (not (str/blank? helper-text)))
       [rnp-helper-text {:type "info" :visible true} helper-text])]))

(defn bool-field
  "Renders a boolean field (core FieldDataType::Bool, e.g. allowUntrustedCert) as a labeled
   Switch row in both edit and non-edit (read) mode. In read mode the Switch is shown but
   disabled (not editable). The value is stored as a string; the core treats true/1/yes
   (case-insensitive) as true, so we write back \"true\"/\"false\"."
  [{:keys [key value edit on-change-text section-name standard-field protected required data-type] :as kv}]
  (let [label (to-field-label kv)
        checked? (contains? #{"true" "1" "yes"}
                            (-> (str value) str/trim str/lower-case))
        ;; The '...' menu (and hence the label-on-top layout) is only for custom fields in
        ;; edit mode. Standard/predefined fields and read mode keep the switch on the right.
        show-menu? (and edit (not standard-field))
        switch-el [rnp-switch {:value checked?
                               :disabled (not edit)
                               :onValueChange (fn []
                                                (when (and edit on-change-text)
                                                  (on-change-text (if checked? "false" "true"))))}]]
    (if show-menu?
      ;; Custom field, edit mode: laid out like the other custom fields (date/text) - a small
      ;; caption label on top with the Switch below it and the '...' menu on the right.
      [rn-view {:style {:flexDirection "row" :align-items "center"
                        :border-bottom-width 0.5 :border-bottom-color @rnc/outline-color}}
       [rn-view {:style {:flex 1 :padding-top 8 :padding-bottom 8}}
        [rnp-text {:variant "bodySmall"
                   :style {:padding-left 16 :color @rnc/outline-color}} label]
        [rn-view {:style {:padding-left 12 :padding-top 2 :align-items "flex-start"}}
         switch-el]]
       [rnp-icon-button {:style {:margin-right 0}
                         :icon dots-icon-name
                         :onPress #(custom-field-menu-show % section-name key protected required data-type)}]]
      ;; Standard field, or read mode: label on the left and Switch on the right (disabled in
      ;; read mode), with a thin bottom underline like the other fields.
      [rn-view {:style {:flexDirection "row" :min-height 60 :justify-content "space-between" :align-items "center"
                        :border-bottom-width 0.5 :border-bottom-color @rnc/outline-color}}
       [rnp-text {:style {:align-self "center" :padding-left 15} :variant "bodySmall"} label]
       [rn-view {:style {:padding-right 10 :align-self "center"}}
        switch-el]])))

(defn- yyyy-mm-dd->date
  "Parses a stored 'yyyy-MM-dd' string into a local Date (at midnight). Returns nil for a
   blank/malformed value so the picker shows an empty field."
  [s]
  (when (and s (re-matches #"\d{4}-\d{2}-\d{2}" s))
    (let [[y m d] (mapv js/parseInt (str/split s #"-"))]
      (js/Date. y (dec m) d))))

(defn- date->yyyy-mm-dd
  "Formats a Date back to the stored 'yyyy-MM-dd' string using its local date parts. Returns
   an empty string when the date is nil/invalid (e.g. the field was cleared)."
  [^js/Date d]
  (if (and d (not (js/isNaN (.getTime d))))
    (let [mm (inc (.getMonth d))
          dd (.getDate d)]
      (str (.getFullYear d) "-"
           (when (< mm 10) "0") mm "-"
           (when (< dd 10) "0") dd))
    ""))

(defn date-field
  "Renders a Date field (core FieldDataType::Date) in edit mode using react-native-paper-dates'
   inline DatePickerInput - a Paper text field with a calendar icon that opens a Material date
   modal. The value is stored as a locale-independent 'yyyy-MM-dd' string (the picker itself
   displays/parses in the device locale format, shown in the label). Non-edit (read) mode is
   not handled here - it falls through to the plain text-field so the value is shown as text."
  [{:keys [key value error-text on-change-text edit section-name standard-field protected required data-type] :as kv}]
  (let [date-val (yyyy-mm-dd->date value)
        show-menu? (and edit (not standard-field))]
    [rn-view {:style {:flexDirection "column"}}
     [rn-view {:style {:flexDirection "row" :align-items "center"}}
      [rn-view {:style {:flex 1}}
       [rnc/date-picker-input
        {;; 'en-CA' keeps English labels but gives the ISO YYYY-MM-DD input mask (see rn-components)
         :locale "en-CA"
         :inputMode "start"
         :mode "flat"
         :label (to-field-label kv)
         :value date-val
         ;; Show the expected date format in the label
         :withDateFormatInLabel true
         :hasError (not (nil? error-text))
         ;; The picker uses a raw Paper TextInput whose flat-mode fill defaults to surfaceVariant
         ;; (grey). Set the same background the app's RNPTextInput uses so it matches other fields.
         :style {:backgroundColor @page-background-color}
         ;; onChange fires only with a valid Date (or nil when cleared); store it as 'yyyy-MM-dd'
         :onChange (fn [d] (on-change-text (date->yyyy-mm-dd d)))}]]
      ;; '...' menu to modify/delete this custom field (custom, non-standard fields only)
      (when show-menu?
        [rnp-icon-button {:style {:margin-right 0}
                          :icon dots-icon-name
                          :onPress #(custom-field-menu-show % section-name key protected required data-type)}])]
     (when (not (nil? error-text))
       [rnp-helper-text {:type "error" :visible true} error-text])]))

(defn otp-field-with-token-update
  "Shows token value which is updated to a new value based on its 'period' value - Typically every 30sec 
   Also there is progress indicator that is updated every second
   This is shown only when the form is in an non edit mode - that is when in read mode and edit is false
   "
  [{:keys [key
           protected
           _edit]}]
  (let [{:keys [token ttl period]} @(form-events/otp-currrent-token key)
        valid-token-found (not (nil? token))]
    [rn-view
     [rn-view {:flexDirection "row" :style {:flex 1}}
      [read-field-row {:key key
                       :protected protected
                       :value token
                       :field-name (if (= OTP key) (lstr-l 'oneTimePasswordTotp) key)}
       [rnp-text {:style {:fontSize 30}} (if valid-token-found (formatted-token token) "  ")]
       [rn-view {:style {:width "10%" :justify-content "center"}}
           ;; Use {:transform [{:scaleX -1} to reverse direction
        [animated-circular-progress {:style {:transform [{:scaleX 1}]}
                                     :tintColor @rnc/circular-progress-color
                                     :size 35
                                     :width 2
                                     :fill (js/Math.round (* 100 (/ ttl period)))
                                     :rotation 360}
         (fn [_v] (r/as-element [rnp-text {:style {:transform [{:scaleX 1}]}} ttl]))]]]]

     (when-not valid-token-found
       [rnp-helper-text {:type "error" :visible true} "Invalid otp url. No token is generated"])]))

(defn opt-field-no-token
  "This field will not show token and instead it is a text input with otp url.
   This is used during edit mode
  "
  [{:keys [key
           value
           section-name
           history-form
           in-deleted-category]}]

  [rnp-text-input {:label key
                   :value value
                   :showSoftInputOnFocus false
                   :multiline true
                   :right (when-not (or history-form in-deleted-category)
                            (r/as-element
                             [rnp-text-input-icon
                              {:icon const/ICON-TRASH-CAN-OUTLINE
                               :onPress (fn [] (ef-dlg/confirm-delete-otp-field-show section-name key))}]))}])

(defn setup-otp-button
  "Shows a button to add an otp field - particularly standard built-in field 'otp' "
  [section-name key standard-field]
  [rnp-button {:style {:margin-bottom 5 :margin-top 5}
               :labelStyle {:fontWeight "bold" :fontSize 15}
               :mode "text"
               :on-press (fn []
                           (form-events/show-form-fields-validation-error-or-call
                            #(setup-otp-action-dialog-show
                              section-name key standard-field)))}
   (lstr-bl 'setUpOneTimePassword)])

(defn otp-field
  "Otp field that shows the timed token value or the corresponding otp url or a button for 
   adding a new otp"
  [{:keys [key value section-name standard-field edit] :as kv}]
  (let [history-form? @(form-events/history-entry-form?)
        in-deleted-category @(form-events/deleted-category-showing)]
    (cond
      ;; No otp is yet set and shows a button to add
      (and edit (str/blank? value) (= key OTP))
      [setup-otp-button section-name key standard-field]

      ;; Token value is not shown if the form is in edit mode or a history entry or deleted one
      (or edit history-form? in-deleted-category)
      [opt-field-no-token (assoc kv :history-form history-form? :in-deleted-category in-deleted-category)]

      ;; This is the case where periodically updated token value is shown
      (not edit)
      [otp-field-with-token-update kv])))
