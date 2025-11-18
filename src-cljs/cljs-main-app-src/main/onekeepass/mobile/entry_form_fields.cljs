(ns
 onekeepass.mobile.entry-form-fields

  (:require [clojure.string :as str]
            [onekeepass.mobile.background :refer [is-iOS]]
            [onekeepass.mobile.constants :as const :refer [OTP PASSWORD URL]]
            [onekeepass.mobile.entry-form-dialogs :as ef-dlg  :refer [setup-otp-action-dialog-show]]
            [onekeepass.mobile.events.entry-form-auto-open :as ef-ao]
            [onekeepass.mobile.entry-form-menus :refer [custom-field-menu-show]]
            [onekeepass.mobile.events.common :as cmn-events]
            [onekeepass.mobile.events.entry-form :as form-events]
            [onekeepass.mobile.events.password-generator :as pg-events]
            [onekeepass.mobile.rn-components
             :as rnc :refer [animated-circular-progress dots-icon-name
                             page-background-color rn-view rnp-button rnp-helper-text
                             rnp-icon-button rnp-text rnp-text-input
                             rnp-text-input-icon]]
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

(def ^:private field-focused (r/atom {:key nil :focused false}))

(defn field-focus-action [key flag]
  (swap! field-focused assoc :field-name key :focused flag))

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
        val (to-field-value kv)] 
    ;; We need to use this ^:key so that text-input field is unique 
    ;; Otherwise :defaultValue will show old value when we change from non edit to edit
    ^{:key (str edit key protected)} [rnp-text-input {:label label
                                                      :defaultValue val
                                                 ;; :value val
                                                 ;;:editable edit
                                                      :showSoftInputOnFocus edit
                                                      :ref (fn [^js/Ref ref]
                                                             (when (and (not (nil? ref)) (str/blank? value)) (.clear ref)))
                                                      :autoCapitalize "none"
                                                      :keyboardType (if-not protected "email-address" "default")
                                                      :autoComplete "off"
                                                      :autoCorrect false
                                                      :style {:width (if icon-space-required  "90%" "100%") :min-height 70}
                                                      ;; If multiline true, then secureTextEntry is not working
                                                      :multiline (not protected)
                                                      :numberOfLines 1
                                                      :onFocus #(field-focus-action key true)
                                                      :onBlur #(field-focus-action key false)
                                                      :onChangeText (if edit on-change-text nil)
                                                      :onPressOut (if-not edit
                                                                    #(cmn-events/write-string-to-clipboard
                                                                      {:field-name key
                                                                       :protected protected
                                                                       :value val})
                                                                    nil)
                                                      :secureTextEntry (if (or (not protected) visible) false true)
                                                     ;; It looks like we can have only one icon
                                                      :right (when protected
                                                               (if visible
                                                                 (r/as-element [rnp-text-input-icon
                                                                                {:icon "eye"
                                                                                 :onPress #(form-events/entry-form-field-visibility-toggle key)}])
                                                                 (r/as-element [rnp-text-input-icon
                                                                                {:icon "eye-off"
                                                                                 :onPress #(form-events/entry-form-field-visibility-toggle key)}])))}]))


;; For "react-native-paper": "^5.12.3"

;; There are somes issues seen while using multiline in iOS and added comments below on these observations

;; When using multiline text input (rnp component) in iOS, the label is not shown when we have some value in input
;; See https://github.com/callstack/react-native-paper/issues/4482

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
        val (to-field-value kv)]
    [rnp-text-input {:label label
                     ;; In 0.16.0, while using multiline = true, edit did not work with :value val and need to use :defaultValue
                     :value val
                     ;;:defaultValue val

                     :showSoftInputOnFocus edit
                     :autoCapitalize "none"

                     ;; Using the 'keyboardType email-address' will not show ":" in the keyboard (0.16.0)
                     ;; :keyboardType "email-address" 

                     :autoCorrect false

                     ;; :contextMenuHidden true

                     :selectTextOnFocus false
                     :spellCheck false ;;ios
                     :textContentType "none"

                       ;; Sometime in iOS when a text input has its secureTextEntry with true value
                       ;; Strong Password prompt comes up and hides the actual input box preventing any entry
                       ;; Particularly it happened with Simulator. For now, we can disable the Password AutoFill feature
                       ;; in the Simulator Phone's Settings or setting the textContentType as shown below also works
                       ;; On device, this behaviour is not seen
                       ;; :textContentType (if (or (not protected) visible) nil "newPassword")
                     :style {:width (if icon-space-required  "90%" "100%")}

                     ;; Sometimes when kdbx url is long, the full text is not shown
                     ;; When the field is focused, we are able to see full text by scrolling
                     ;; :autoFocus non-edit-kdbx-url

                    ;; multiline needs to be for password's secureTextEntry to work
                    ;; :multiline (not protected)

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
                     :secureTextEntry (if (or (not protected) visible) false true)
                     ;; It looks like we can have only one icon
                     :right (when protected
                              (if visible
                                (r/as-element [rnp-text-input-icon
                                               {:icon "eye"
                                                :onPress #(form-events/entry-form-field-visibility-toggle key)}])
                                (r/as-element [rnp-text-input-icon
                                               {:icon "eye-off"
                                                :onPress #(form-events/entry-form-field-visibility-toggle key)}])))}]))

(defn text-field
  "Called to show form fields"
  [{:keys [key
           value
           _field-name
           protected
           standard-field
           required
           edit
           on-change-text
           error-text
           section-name]
    :or {edit false
         protected false
         on-change-text #(println (str "No on change text handler yet registered for " key))
         required false}
    :as kvm}]
  (let [cust-color @page-background-color
        is-password-edit? (and edit (= key PASSWORD))
        ;; kdbx:// or https:// or http
        non-edit-kdbx-url (and (not edit) (= key URL))
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
        icon-space-required (or is-password-edit? custom-field-edit-focused?)]
    [rn-view {:flexDirection "column"}
     ;; text input field and optional icon button in the same row
     [rn-view {:flexDirection "row" :style {:flex 1}}
      ;; First platform specific text input field
      (if (is-iOS)
        [ios-form-text-input (assoc kvm :icon-space-required icon-space-required :non-edit-kdbx-url non-edit-kdbx-url)]
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
                           :onPress #(custom-field-menu-show % section-name key protected required)}]])

      ;; We are using 'absolute' position to add this icon button instead of setting to "right" prop of textinput field 
      ;; and it works in iOS (needs checking in android)
      (when non-edit-kdbx-url
        [rn-view {:style {:margin-left -5 :backgroundColor cust-color :position "absolute" :right 0}}
         [rnp-icon-button {:style {:margin-right 0}
                           :icon const/ICON-LAUNCH
                           :onPress (fn [] 
                                      (ef-ao/entry-form-open-url value))}]])]

     ;; Any error text below the field
     (when (and edit (not (nil? error-text)))
       [rnp-helper-text {:type "error" :visible true} error-text])]))

(defn formatted-token
  "Groups digits with spaces between them for easy reading"
  [token]
  (let [len (count token)
        n (cond
            (or (= len 6) (= len 7) (= len 9))
            3

            (or (= len 8) (= len 10))
            4

            :else
            3)
        ;; step = n, pad = ""
        parts (partition n n "" token)
        parts (map (fn [c] (str/join c)) parts)
        spaced (str/join " " parts)]
    spaced))

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
