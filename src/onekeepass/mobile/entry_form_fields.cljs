(ns
 onekeepass.mobile.entry-form-fields

  (:require
   [reagent.core :as r]
   [clojure.string :as str]
   [onekeepass.mobile.entry-form-menus :refer [custom-field-menu-show]]
   [onekeepass.mobile.entry-form-dialogs :as ef-dlg  :refer [setup-otp-action-dialog-show]]
   [onekeepass.mobile.rn-components
    :as rnc :refer [lstr
                    animated-circular-progress
                    icon-color
                    on-primary-color
                    primary-container-color
                    page-background-color
                    appbar-text-color
                    rn-keyboard
                    dots-icon-name
                    page-title-text-variant
                    rnp-button
                    rn-view
                    rnp-text-input
                    rnp-text
                    rnp-helper-text
                    rnp-text-input-icon
                    rnp-icon-button]]
   [onekeepass.mobile.constants :as const :refer [OTP]]
   [onekeepass.mobile.utils :as u]
   [onekeepass.mobile.events.entry-form :as form-events]
   [onekeepass.mobile.events.dialogs :as dlg-vents]
   [onekeepass.mobile.events.password-generator :as pg-events]
   [onekeepass.mobile.background :refer [is-iOS is-Android]]
   [onekeepass.mobile.events.common :as cmn-events]
   [onekeepass.mobile.events.dialogs :as dlg-events]))

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
                                       required
                                       visible
                                       edit
                                       on-change-text]} is-password-edit? custom-field-edit-focused?]

  ;;(println "protected " protected " visible " visible " , " (if (or (not protected) visible) false true))
  ^{:key (str key protected)} [rnp-text-input {:label key #_(if required (str key "*") key)
                                               :defaultValue value
                       ;;:value value
                       ;;:editable edit
                                               :showSoftInputOnFocus edit
                                               :ref (fn [^js/Ref ref]
                                                      (when (and (not (nil? ref)) (str/blank? value)) (.clear ref)))
                                               :autoCapitalize "none"
                                               :keyboardType (if-not protected "email-address" "default")
                                               :autoComplete "off"
                                               :autoCorrect false
                                               :style {:width (if (or is-password-edit? custom-field-edit-focused?)  "90%" "100%")}
                                               :onFocus #(field-focus-action key true)
                                               :onBlur #(field-focus-action key false)
                                               :onChangeText (if edit on-change-text nil)
                                               :onPressOut (if-not edit
                                                             #(cmn-events/write-string-to-clipboard {:field-name key
                                                                                                     :protected protected
                                                                                                     :value value})
                                                             nil)
                                               :secureTextEntry (if (or (not protected) visible) false true)
                   ;; It looks like we can have only one icon
                                               :right (when protected
                                                        (if visible
                                                          (r/as-element [rnp-text-input-icon {:icon "eye"
                                                                                              :onPress #(form-events/entry-form-field-visibility-toggle key)}])
                                                          (r/as-element [rnp-text-input-icon {:icon "eye-off"
                                                                                              :onPress #(form-events/entry-form-field-visibility-toggle key)}])))}])

;; In iOS, we do not see the same issue as seen with the use of text input in android 
(defn ios-form-text-input [{:keys [key
                                   value
                                   protected
                                   required
                                   visible
                                   edit
                                   on-change-text]} is-password-edit? custom-field-edit-focused?]
  ;;(println "Key is " key " and value " value)
  [rnp-text-input {:label key #_(if required (str key "*") key)
                   :value value
                   :showSoftInputOnFocus edit
                   :autoCapitalize "none"
                   :keyboardType "email-address"
                   ;;:autoComplete "off"
                   :autoCorrect false
                   ;;:contextMenuHidden true
                   :selectTextOnFocus false
                   :spellCheck false ;;ios
                   :textContentType "none"
                   ;; Sometime in iOS when a text input has its secureTextEntry with true value
                   ;; Strong Password prompt comes up and hides the actual input box preventing any entry
                   ;; Particularly it happened with Simulator. For now, we can disable the Password AutoFill feature
                   ;; in the Simulator Phone's Settings or setting the textContentType as shown below also works
                   ;; On device, this behaviour is not seen
                   ;; :textContentType (if (or (not protected) visible) nil "newPassword")
                   :style {:width (if (or is-password-edit? custom-field-edit-focused?)  "90%" "100%")}
                   :onFocus #(field-focus-action key true)
                   :onBlur #(field-focus-action key false)
                   :onChangeText (if edit on-change-text nil)
                   :onPressOut (if-not edit
                                 #(cmn-events/write-string-to-clipboard {:field-name key
                                                                         :protected protected
                                                                         :value value})
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
                                              :onPress #(form-events/entry-form-field-visibility-toggle key)}])))}])

(defn text-field
  "Called to show form fields"
  [{:keys [key
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
        is-password-edit? (and edit (= key "Password"))
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
                                      (not standard-field)))]
    [rn-view {:flexDirection "column"}
     [rn-view {:flexDirection "row" :style {:flex 1}}
      (if (is-iOS)
        [ios-form-text-input kvm is-password-edit? custom-field-edit-focused?]
        [android-form-text-input kvm is-password-edit? custom-field-edit-focused?])
      (when is-password-edit?
        [rn-view {:style {:margin-left -5 :backgroundColor cust-color}}
         [rnp-icon-button {:style {:margin-right 0}
                           :icon "cached"
                           ;; on-change-text is a single argument function
                           ;; This function is called when the generated password is selected in Generator page
                           :onPress #(pg-events/generate-password on-change-text)}]])

     ;; In Android, when we press this dot-icon, the custom field first loses focus and the keyborad dimiss takes place 
     ;; and then menu pops up only when we press second time the dot-icon
      (when custom-field-edit-focused?
        [rn-view {:style {:margin-left -5 :backgroundColor cust-color}}
         [rnp-icon-button {:style {:margin-right 0}
                           :icon dots-icon-name
                           :onPress #(custom-field-menu-show % section-name key protected required)}]])]

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

(defn otp-read-field
  [{:keys [key
           value
           protected
           edit]}]
  (let [{:keys [token ttl period]} @(form-events/otp-currrent-token key)]
    [rn-view {:flexDirection "row" :style {:flex 1}}
     [rnp-text-input {:label (if (= OTP key) "One-Time Password" key)
                      :value (if-not edit (formatted-token token) value)
                      :showSoftInputOnFocus edit
                      :autoCapitalize "none"
                      :keyboardType "email-address"
                      :autoCorrect false
                      :selectTextOnFocus false
                      :spellCheck false
                      :textContentType "none"
                      :style {:width "90%" :fontSize 30}
                      :onFocus #(field-focus-action key true)
                      :onBlur #(field-focus-action key false)
                      :onChangeText nil
                      :onPressOut (if-not edit
                                    #(cmn-events/write-string-to-clipboard
                                      {:field-name key
                                       :protected protected
                                       :value value})
                                    nil)
                      :secureTextEntry false
                      :right nil}]
     [rn-view {:style {:width "10%" :justify-content "center"}}
      ;; Use {:transform [{:scaleX -1} to reverse direction
      [animated-circular-progress {:style {:transform [{:scaleX 1}]}
                                   ;;:tintColor "#00e0ff"
                                   :size 35
                                   :width 1
                                   :fill (js/Math.round (* 100 (/ ttl period)))
                                   :rotation 360}
       (fn [_v] (r/as-element [rnp-text {:style {:transform [{:scaleX 1}]}} ttl]))]]]))

(defn opt-field-no-token
  [{:keys [key
           value
           section-name
           history-form
           in-deleted-category]}]

  [rnp-text-input {:label key
                   :value value
                   :showSoftInputOnFocus false
                   :multiline true
                   :right (when-not (or history-form in-deleted-category ) 
                            (r/as-element
                             [rnp-text-input-icon
                              {:icon const/ICON-TRASH-CAN-OUTLINE
                               :onPress (fn []
                                          (ef-dlg/confirm-delete-otp-field-show section-name key)
                                          #_(dlg-vents/confirm-delete-otp-field-dialog-show-with-state
                                             {:call-on-ok-fn #(form-events/entry-form-delete-otp-field section-name key)}))}]))}])

(defn setup-otp-button [section-name key standard-field]
  [rnp-button {:style {:margin-bottom 5 :margin-top 5 }
               :labelStyle {:fontWeight "bold" :fontSize 15}
               :mode "text"
               :on-press #(setup-otp-action-dialog-show section-name key standard-field)} "Set up One-Time Password"])

(defn otp-field [{:keys [key value section-name standard-field edit] :as kv}]
  (let [history-form? @(form-events/history-entry-form?)
        in-deleted-category @(form-events/deleted-category-showing)
        ]
    (cond
      (and edit (str/blank? value) (= key OTP))
      [setup-otp-button section-name key standard-field]
      
      (or edit history-form? in-deleted-category)
      [opt-field-no-token (assoc kv :history-form history-form? :in-deleted-category in-deleted-category)]
      
      (not edit)
      [otp-read-field kv])))
