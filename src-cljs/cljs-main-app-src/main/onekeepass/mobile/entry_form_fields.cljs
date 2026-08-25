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
                             page-background-color rn-keyboard rn-pressable
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
   carry that card's color. 'RNPTextInput' otherwise gives every text input the theme background,
   which is the card color only in the light theme"
  []
  @rnc/grouped-list-card-color)

;; The read mode field shown as a text display - a revealed password, a key - that was last
;; pressed, or nil. Such a field is not a text input and so cannot hold the focus, but it is drawn
;; with the underline and the label of a focused one so that pressing it gives the same feedback
;; as pressing any other field of the form does
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
  "Pressing the value of a read mode field copies it to the clipboard and leaves that field
   looking like the focused one.

   Dropping the keyboard first blurs whichever text input of the form holds the focus - pressing
   a view of our own does not take the focus off a native input on either platform - so that only
   one field of the form is ever drawn as focused"
  [{:keys [key protected] :as kv}]
  (fn []
    (cmn-events/write-string-to-clipboard {:field-name key
                                           :protected protected
                                           :value (to-field-value kv)})
    (.dismiss rn-keyboard)
    (reset! pressed-read-field key)))

(defn- visibility-toggle-on-press
  "Shows or masks the value of a protected field.

   Revealing takes the masked text input out of the tree - it is replaced by
   'colored-read-field' or by 'multi-line-read-field'. Android then hands the focus that input
   held to another text input on the page, and that one draws its underline and its label in
   the active color as though the user had tapped it. iOS does not move focus in that way

   Dropping whatever holds the focus once the change has been made leaves the other fields
   looking as they did. 'Keyboard.dismiss' is what does it - it blurs the currently focused
   input. The wait is for React to have taken the input out of the tree first, since it is
   only then that Android hands the focus on"
  [key]
  (fn []
    (form-events/entry-form-field-visibility-toggle key)
    ;; Masking again puts a text input back in place of the text display, so the mark the display
    ;; carried goes with it and must not be left behind for the next reveal
    (reset! pressed-read-field nil)
    (when (is-Android)
      (js/setTimeout (fn [] (.dismiss rn-keyboard)) 0))))

(def ^:private field-focused (r/atom {:key nil :focused false}))

(defn field-focus-action [key flag]
  (swap! field-focused assoc :field-name key :focused flag)
  ;; A text input taking the focus is what takes the mark off a pressed read mode field, the same
  ;; way it takes the focus off the input that held it before
  (when flag
    (reset! pressed-read-field nil)))

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
                                       edit
                                       on-change-text
                                       icon-space-required
                                       _non-edit-kdbx-url] :as kv}]
  (let [label (to-field-label kv)
        val (to-field-value kv)
        masked? (and protected (not visible))
        multi-line? (multi-line-field? key val)
        ;; A masked value is left as a single line as secureTextEntry does not work with multiline.
        ;; Revealing such a field is what turns it into a multi line input - in edit mode that is
        ;; also the only way a key with its line breaks can be typed or pasted into it.
        ;; A revealed one in read mode never reaches here - see 'multi-line-read-field'
        grow? (and multi-line? (not masked?))]

    ;; (println "Lable is " label)
    ;; We need to use this ^:key so that text-input field is unique
    ;; Otherwise :defaultValue will show old value when we change from non edit to edit
    ^{:key (str edit key protected (mask-remount-key multi-line? masked?))} [rnp-text-input (merge
                                                      no-assist-text-props
                                                      {:label label
                                                      :defaultValue val
                                                      ;; :value val
                                                      ;;:editable edit
                                                      :showSoftInputOnFocus edit
                                                      :ref (fn [^js/Ref ref]
                                                             (when (and (not (nil? ref)) (str/blank? value)) (.clear ref)))
                                                      :keyboardType "default" #_(if-not protected "email-address" "default")
                                                      :style (merge {:width (if icon-space-required  "90%" "100%")}
                                                                    (when-not edit {:backgroundColor (read-field-background)})
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
                                                      :onChangeText (if edit on-change-text nil)
                                                      :onPressOut (if-not edit
                                                                    #(cmn-events/write-string-to-clipboard
                                                                      {:field-name key
                                                                       :protected protected
                                                                       :value val})
                                                                    nil)
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
                                   _read-value
                                   protected
                                   visible
                                   edit
                                   on-change-text
                                   icon-space-required
                                   _non-edit-kdbx-url] :as kv}]
  (let [label (to-field-label kv)
        val (to-field-value kv)
        masked? (and protected (not visible))
        multi-line? (multi-line-field? key val)
        ;; A masked value is left as a single line as secureTextEntry does not work with multiline.
        ;; Revealing such a field is what turns it into a multi line input - in edit mode that is
        ;; also the only way a key with its line breaks can be typed or pasted into it.
        ;; A revealed one in read mode never reaches here - see 'multi-line-read-field'
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

                     :showSoftInputOnFocus edit

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
                                   (when-not edit {:backgroundColor (read-field-background)})
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
                     :onChangeText (if edit on-change-text nil)
                     :onPressOut (if-not edit
                                   #(cmn-events/write-string-to-clipboard 
                                     {:field-name key
                                      :protected protected
                                      :value val})
                                   nil)
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

;; Read mode monospace + per character coloring (see 'colored-read-field') is applied only to
;; the protected fields whose key is in this set. A password and the passkey secret fields are
;; random looking strings and benefit from this. Other protected fields (e.g a free text
;; security answer) are left as plain text. Add a field key here to opt it in
(def ^:private colorable-protected-field-names
  #{PASSWORD
    const/KPEX_PASSKEY_USER_HANDLE
    const/KPEX_PASSKEY_CREDENTIAL_ID})

(defn- colored-read-field
  "Shows a revealed protected field in read mode as a text display instead of the native
   text input so that each character can have its own color - a native TextInput shows its
   value in a single color

   The field is laid out like the flat text input used in read mode - the label on top, the
   value below it and a thin underline. Pressing on the value copies it to the clipboard and
   the eye icon toggles back to the masked native input
  "
  [{:keys [key icon-space-required] :as kv}]
  (let [label (to-field-label kv)
        val (to-field-value kv)]
    [rn-view {:style {:width (if icon-space-required "90%" "100%")
                      :flexDirection "row"
                      :align-items "center"
                      :min-height 60
                      :padding-left 16
                      :border-bottom-width 0.5
                      :border-bottom-color @rnc/outline-color
                      :backgroundColor (read-field-background)}}
     [rn-pressable {:style {:flex 1 :padding-top 8 :padding-bottom 8}
                    :onPress (read-field-on-press kv)}
      [rnp-text {:variant "bodySmall" :style {:color (read-field-label-color key)}} label]
      [cc/colored-password val true {:style {:fontSize 18 :letterSpacing 0.5}}]]
     [rnp-icon-button {:style {:margin-right 0}
                       :icon "eye"
                       :onPress (visibility-toggle-on-press key)}]
     [read-field-active-underline key]]))

(defn- multi-line-read-field
  "Shows a field whose value needs more than one line - a passkey private key pem, an ssh key,
   the additional urls - in read mode as a text display instead of the native text input

   A text input is not used here for two reasons. It lays such a value out on a single line so
   only the tail of the value can be seen, and it takes focus when pressed which brings up the
   soft keyboard even though nothing can be typed in read mode

   A masked value is not shown here but by the native text input - its secureTextEntry draws a
   dot that a text of our own cannot match in size

   The field is laid out like the flat text input used in read mode - the label on top, the value
   below it and a thin underline. Pressing on the value copies it to the clipboard and, for a
   protected field, the eye icon masks it again
  "
  [{:keys [key protected icon-space-required] :as kv}]
  (let [label (to-field-label kv)
        val (to-field-value kv)]
    [rn-view {:style {:width (if icon-space-required "90%" "100%")
                      :flexDirection "row"
                      :align-items "center"
                      :min-height 60
                      :padding-left 16
                      :border-bottom-width 0.5
                      :border-bottom-color @rnc/outline-color
                      :backgroundColor (read-field-background)}}
     [rn-pressable {:style {:flex 1 :padding-top 8 :padding-bottom 8}
                    :onPress (read-field-on-press kv)}
      [rnp-text {:variant "bodySmall" :style {:color (read-field-label-color key)}} label]
      ;; A key is monospaced and a bit smaller as a pem or an openssh text reads better that way
      ;; and more of it fits on a line. Anything else keeps the font of the rest of the form so
      ;; that it does not read as a different kind of value than the fields around it
      ;;
      ;; The whole value is shown either way - the form is inside a scroll view and so a long
      ;; value is read by scrolling the page rather than scrolling within the field
      ;; 'textBreakStrategy' is an android only prop and iOS ignores it. Android breaks the lines
      ;; of a text with the high quality strategy by default, which ends a line well before the
      ;; width it has for a value that is one long run of letters and digits like a pem body. The
      ;; key then sat in a narrow column with a wide empty strip before the eye icon. The simple
      ;; strategy fills each line as far as it goes, which is what iOS does
      [rnp-text {:textBreakStrategy "simple"
                 :style (when (contains? const/KEY_FIELD_NAMES key)
                          {:fontFamily rnc/monospace-font-family :fontSize 14})} val]]
     (when protected
       [rnp-icon-button {:style {:margin-right 0}
                         :icon "eye"
                         :onPress (visibility-toggle-on-press key)}])
     [read-field-active-underline key]]))

(defn text-field
  "Called to show form fields"
  [{:keys [key
           value
           _field-name
           protected
           visible
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
        ;; A revealed password or passkey secret in read mode is shown as a colored text
        ;; display instead of the native text input - see 'colored-read-field'
        colored-read? (and (not edit) protected visible
                           (contains? colorable-protected-field-names key))
        ;; A revealed value with embedded new lines is shown in read mode as a text display and
        ;; not as a text input - see 'multi-line-read-field'. A masked one stays with the text
        ;; input so that its dots keep looking like those of the other masked fields
        multi-line-read? (and (not edit)
                              (not (and protected (not visible)))
                              (multi-line-field? key (to-field-value kvm)))]
    [rn-view {:flexDirection "column"}
     ;; text input field and optional icon button in the same row
     [rn-view {:flexDirection "row" :style {:flex 1}}
      ;; First platform specific text input field
      (cond
        colored-read?
        [colored-read-field (assoc kvm :icon-space-required icon-space-required)]

        multi-line-read?
        [multi-line-read-field (assoc kvm :icon-space-required icon-space-required)]

        (is-iOS)
        [ios-form-text-input (assoc kvm :icon-space-required icon-space-required :non-edit-kdbx-url non-edit-kdbx-url)]

        :else
        [android-form-text-input (assoc kvm :icon-space-required icon-space-required :non-edit-kdbx-url non-edit-kdbx-url)])
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
                           :onPress #(custom-field-menu-show % section-name key protected required data-type)}]])

      ;; We are using 'absolute' position to add this icon button instead of setting to "right" prop of textinput field 
      ;; and it works in iOS (needs checking in android)
      (when non-edit-kdbx-url
        [rn-view {:style {:margin-left -5 :backgroundColor cust-color :position "absolute" :right 0}}
         [rnp-icon-button {:style {:margin-right 0}
                           :icon const/ICON-LAUNCH
                           :onPress (fn []
                                      (ef-ao/entry-form-open-url value))}]])

      ;; Launch the remote Storage Browser using this connection entry
      (when rs-conn-launch?
        [rn-view {:style {:margin-left -5 :backgroundColor cust-color :position "absolute" :right 0}}
         [rnp-icon-button {:style {:margin-right 0}
                           :icon const/ICON-LAUNCH
                           :onPress (fn []
                                      (rs-events/open-entry-remote entry-type-uuid entry-uuid))}]])]

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
      [rnp-text-input {:label (if (= OTP key) (lstr-l 'oneTimePasswordTotp) key)
                       :value (if valid-token-found (formatted-token token) "  ")
                       :showSoftInputOnFocus false
                       :autoCapitalize "none"
                       :keyboardType "email-address"
                       :autoCorrect false
                       :selectTextOnFocus false
                       :spellCheck false
                       :textContentType "none"
                       :style {:width "90%" :fontSize 30}
                           ;;:textColor @rnc/custom-color0
                       :onFocus #(field-focus-action key true)
                       :onBlur #(field-focus-action key false)
                       :onChangeText nil
                       :onPressOut #(cmn-events/write-string-to-clipboard
                                     {:field-name key
                                      :protected protected
                                      :value token})
                       :secureTextEntry false
                       :right nil}]
      [rn-view {:style {:width "10%" :justify-content "center"}}
           ;; Use {:transform [{:scaleX -1} to reverse direction
       [animated-circular-progress {:style {:transform [{:scaleX 1}]}
                                    :tintColor @rnc/circular-progress-color
                                    :size 35
                                    :width 2
                                    :fill (js/Math.round (* 100 (/ ttl period)))
                                    :rotation 360}
        (fn [_v] (r/as-element [rnp-text {:style {:transform [{:scaleX 1}]}} ttl]))]]]

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
