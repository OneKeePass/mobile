(ns onekeepass.ios.autofill.entry-form
  (:require [clojure.string :as str]
            [onekeepass.ios.autofill.common-components :refer [select-field]]
            [onekeepass.ios.autofill.constants :as const :refer [ONE_TIME_PASSWORD_TYPE
                                                                 OTP]]
            [onekeepass.ios.autofill.events.entry-form :as form-events]
            [onekeepass.ios.autofill.rn-components :as rnc :refer [animated-circular-progress
                                                                   dots-icon-name
                                                                   on-primary-color
                                                                   page-background-color
                                                                   primary-container-color
                                                                   rn-view
                                                                   rnp-button
                                                                   rnp-helper-text
                                                                   rnp-chip
                                                                   rnp-icon-button
                                                                   rnp-text
                                                                   rnp-text-input
                                                                   rnp-text-input-icon]]
            [onekeepass.ios.autofill.utils :as u]
            [reagent.core :as r]))

(def box-style-1 {:flexDirection "column"
                  :padding-right 5
                  :padding-left 5
                  :margin-bottom 5
                  :borderWidth .20
                  :borderRadius 4})

(def box-style-2 (merge box-style-1 {:padding-bottom 10 :padding-top 5}))

(def ^:private field-focused (r/atom {:key nil :focused false}))

(defn field-focus-action [key flag]
  (swap! field-focused assoc :field-name key :focused flag))

;; In iOS, we do not see the same issue as seen with the use of text input in android 
(defn ios-form-text-input [{:keys [key
                                   value
                                   protected
                                   visible
                                   standard-field
                                   edit
                                   on-change-text]} is-password-edit? custom-field-edit-focused?]
  ;;(println "Key is " key " and value " value)
  [rnp-text-input {:label key #_(if standard-field (lstr-field-name key) key)
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
                   :onPressOut #() #_(if-not edit
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
        custom-field-edit-focused? (and
                                    edit
                                    (= (:field-name @field-focused) key)
                                    (not standard-field))]
    [rn-view {:flexDirection "column"}
     [rn-view {:flexDirection "row" :style {:flex 1}}
      [ios-form-text-input kvm is-password-edit? custom-field-edit-focused?]
      (when is-password-edit?
        [rn-view {:style {:margin-left -5 :backgroundColor cust-color}}
         [rnp-icon-button {:style {:margin-right 0}
                           :icon "cached"
                           ;; on-change-text is a single argument function
                           ;; This function is called when the generated password is selected in Generator page
                           :onPress #() #_#(pg-events/generate-password on-change-text)}]])

     ;; In Android, when we press this dot-icon, the custom field first loses focus and the keyborad dimiss takes place 
     ;; and then menu pops up only when we press second time the dot-icon
      (when custom-field-edit-focused?
        [rn-view {:style {:margin-left -5 :backgroundColor cust-color}}
         [rnp-icon-button {:style {:margin-right 0}
                           :icon dots-icon-name
                           :onPress #() #_#(custom-field-menu-show % section-name key protected required)}]])]

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
      [rnp-text-input {:label (if (= OTP key) "One TimePassword Totp" #_(lstr-l 'oneTimePasswordTotp) key)
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
                       :onPressOut #() #_#(cmn-events/write-string-to-clipboard
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
           value]}]
  [rnp-text-input {:label key
                   :value value
                   :showSoftInputOnFocus false
                   :multiline true
                   :right nil}])

(defn setup-otp-button
  "Shows a button to add an otp field - particularly standard built-in field 'otp' "
  [section-name key standard-field]
  [rnp-button {:style {:margin-bottom 5 :margin-top 5}
               :labelStyle {:fontWeight "bold" :fontSize 15}
               :mode "text"
               :on-press #() #_(fn []
                                 (form-events/show-form-fields-validation-error-or-call
                                  #(setup-otp-action-dialog-show
                                    section-name key standard-field)))}
   "Setup OTP"
   #_(lstr-bl 'setUpOneTimePassword)])

(defn otp-field
  "Otp field that shows the timed token value or the corresponding otp url or a button for 
   adding a new otp"
  [{:keys [key value section-name standard-field edit] :as kv}]
  (let [history-form? false #_@(form-events/history-entry-form?)
        in-deleted-category false #_@(form-events/deleted-category-showing)]
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


;;;;;;;;;;;;;

(def notes-ref (atom nil))

(defn clear-notes []
  (when @notes-ref (.clear ^js/Ref  @notes-ref)))

(defn notes [edit]
  (let [value @(form-events/entry-form-data-fields :notes)]
    (when (or edit (not (str/blank? value)))
      [rn-view {:style {:padding-right 5 :padding-left 5 :borderWidth .20 :borderRadius 4}}
       [rnp-text-input {:style {:width "100%"} :multiline true :label "Notes " #_(lstr-l "notes")
                        :defaultValue value
                        :ref (fn [^js/Ref ref]
                               (reset! notes-ref ref))
                        :showSoftInputOnFocus edit
                        :onChangeText #()  #_(when edit #(form-events/entry-form-data-update-field-value :notes %))}]])))



(defn tags [edit]
  (let [entry-tags @(form-events/entry-form-data-fields :tags)
        tags-availble (boolean (seq entry-tags))]

    (when (or edit tags-availble)
      [rn-view {:style {:flexDirection "column" :justify-content "center"
                        :min-height 50  :margin-top 5 :padding 5 :borderWidth .20 :borderRadius 4}}
       [rn-view {:style {:flexDirection "row" :backgroundColor  @primary-container-color :min-height 25}}
        [rnp-text {:style {:alignSelf "center" :width "85%" :padding-left 15} :variant "titleMedium"}
         "Tags"
         #_(lstr-section-name 'tags)]

        #_(when edit
            [rnp-icon-button {:icon const/ICON-PLUS :style {:height 35 :margin-right 0 :backgroundColor @on-primary-color}
                              :onPress (fn [] (cmn-events/tags-dialog-init-selected-tags entry-tags))}])]
       [rn-view {:style {:flexDirection "column" :padding-top 10}}
        [rn-view {:style {:flexDirection "row" :flexWrap "wrap"}}
         (doall
          (for [tag  entry-tags]
            ^{:key tag} [rnp-chip {:style {:margin 5}
                                   :onClose #() #_(when edit
                                                    (fn []
                                                      (form-events/entry-form-data-update-field-value
                                                       :tags (filterv #(not= tag %) entry-tags))))} tag]))]]])))


;;;;;;;;;;;


(defn section-header [section-name]
  (let [edit @(form-events/entry-form-field :edit)
        standard-sections @(form-events/entry-form-data-fields :standard-section-names)
        standard-section? (u/contains-val? standard-sections section-name)
        tr-section-name section-name #_(if standard-section? (lstr-section-name section-name) section-name)]
    [rn-view {:style {:flexDirection "row"
                      :backgroundColor  @primary-container-color
                      :margin-top 5
                      :min-height 35}}
     [rnp-text {:style {:alignSelf "center" :width "85%" :padding-left 15} :variant "titleMedium"}
      tr-section-name]]))


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
                                             :on-change #() #_#(form-events/update-section-value-on-change
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
                                                 :on-change-text #() #_#(form-events/update-section-value-on-change
                                                                         section-name key %)
                                                 :password-score password-score
                                                 :visible @(form-events/visible? key))]))))])))


(defn all-sections-content []
  (let [{:keys [edit showing]
         {:keys [section-names section-fields]} :data} @(form-events/entry-form)]
    #_(rnc/react-use-effect
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


(defn main-content []
  (let [edit @(form-events/form-edit-mode)]
    [rn-view {:style {:flexDirection "column" :justify-content "center" :padding 5}}
     [:f> all-sections-content]
     [notes edit]
     [tags edit]]))

(defn content []
  [rnc/rn-safe-area-view {:style {:flex 1 :background-color @page-background-color}}
   [rnc/rn-scroll-view {:style {} :contentContainerStyle {:flexGrow 1 :background-color @page-background-color}}
    [main-content]]])