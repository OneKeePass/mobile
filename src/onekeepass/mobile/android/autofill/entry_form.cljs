(ns onekeepass.mobile.android.autofill.entry-form
  (:require [clojure.string :as str]
            [onekeepass.mobile.android.autofill.events.entry-form :as android-af-ef-events]
            [onekeepass.mobile.common-components :refer [select-field-view]]
            [onekeepass.mobile.constants :as const :refer [ONE_TIME_PASSWORD_TYPE
                                                           OTP]]
            [onekeepass.mobile.entry-form-fields :refer [field-focus-action
                                                         formatted-token text-field]]
            [onekeepass.mobile.rn-components
             :as rnc
             :refer [animated-circular-progress page-background-color
                     primary-container-color rn-view rnp-chip rnp-helper-text
                     rnp-text rnp-text-input]]
            [onekeepass.mobile.translation :refer [lstr-l lstr-section-name]]
            [onekeepass.mobile.utils :as u]
            [reagent.core :as r]))


(def box-style-1 {:flexDirection "column"
                  :padding-right 5
                  :padding-left 5
                  :margin-bottom 5
                  :borderWidth .20
                  :borderRadius 4})

(def box-style-2 (merge box-style-1 {:padding-bottom 10 :padding-top 5}))

(def notes-ref (atom nil))

(defn clear-notes []
  (when @notes-ref (.clear ^js/Ref  @notes-ref)))

(defn notes [edit]
  (let [value @(android-af-ef-events/entry-form-data-fields :notes)]
    (when (or edit (not (str/blank? value)))
      [rn-view {:style {:padding-right 5 :padding-left 5 :borderWidth .20 :borderRadius 4}}
       [rnp-text-input {:style {:width "100%"} :multiline true :label (lstr-l "notes")
                        :defaultValue value
                        :ref (fn [^js/Ref ref]
                               (reset! notes-ref ref))
                        :showSoftInputOnFocus edit
                        :onChangeText #()  #_(when edit #(form-events/entry-form-data-update-field-value :notes %))}]])))

(defn tags [edit]
  (let [entry-tags @(android-af-ef-events/entry-form-data-fields :tags)
        tags-availble (boolean (seq entry-tags))]

    (when (or edit tags-availble)
      [rn-view {:style {:flexDirection "column" :justify-content "center"
                        :min-height 50  :margin-top 5 :padding 5 :borderWidth .20 :borderRadius 4}}
       [rn-view {:style {:flexDirection "row" :backgroundColor  @primary-container-color :min-height 25}}
        [rnp-text {:style {:alignSelf "center" :width "85%" :padding-left 15} :variant "titleMedium"}
         (lstr-section-name 'tags)]

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

;; This is based on the original from the main app's entry-form
;; TODO: Need to make it generic by passing few subscribe/event calls in args 
(defn otp-field-with-token-update
  "Shows token value which is updated to a new value based on its 'period' value - Typically every 30sec 
   Also there is progress indicator that is updated every second
   This is shown only when the form is in an non edit mode - that is when in read mode and edit is false
   "
  [{:keys [key
           _protected
           _edit]}]
  (let [{:keys [token ttl period]} @(android-af-ef-events/otp-currrent-token key)
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

(defn section-header [section-name] 
  (let [standard-sections @(android-af-ef-events/entry-form-data-fields :standard-section-names)
        standard-section? (u/contains-val? standard-sections section-name)
        tr-section-name (if standard-section? (lstr-section-name section-name) section-name)]
    [rn-view {:style {:flexDirection "row"
                      :backgroundColor  @primary-container-color
                      :margin-top 5
                      :min-height 35}}
     [rnp-text {:style {:alignSelf "center" :width "85%" :padding-left 15} :variant "titleMedium"}
      tr-section-name]]))

(defn section-content [edit section-name section-data] 
  (let [errors @(android-af-ef-events/entry-form-field :error-fields)]
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
                      password-score] :as kv} section-data]
          ;; All fields of this section is shown in edit mode. In case of non edit mode, 
          ;; all required fields and other fields with values are shown
          (when (or edit (not (str/blank? value))) #_(or edit (or required (not (str/blank? value))))
                (cond
                  (not (nil? select-field-options))
                  ^{:key key} [select-field-view {:text-label key #_(if required (str key "*") key)
                                                  :options  (mapv (fn [v] {:key v :label v}) select-field-options)
                                                  :value value
                                                  :disabled (not edit)
                                                  :pressable-on-press #() #_#(android-af-ef-events/copy-field-to-clipboard key)
                                                  :on-change #()}]

                  ;; otp-field-with-token-update is called instead of otp-field as this the entry form is read only
                  (= data-type ONE_TIME_PASSWORD_TYPE)
                  ^{:key key} [otp-field-with-token-update (assoc kv
                                                                  :edit edit
                                                                  :section-name section-name
                                                                  :standard-field standard-field)]

                  :else
                  ^{:key key} [text-field (assoc kv
                                                 :required false ;; make all fields as optional 
                                                 :section-name section-name
                                                 :edit edit
                                                 :error-text (get errors key)
                                                 :on-change-text #() 
                                                 :password-score password-score
                                                 :visible @(android-af-ef-events/visible? key))]))))])))


(defn all-sections-content []
  (let [{:keys [edit showing]
         {:keys [section-names section-fields]} :data} @(android-af-ef-events/entry-form)] 
    (rnc/react-use-effect
       (fn []
       ;; cleanup fn is returned which is called when this component unmounts or any passed dependencies are changed
       ;;(println "all-sections-content effect init - showing edit : " showing edit )
         (when (and (= showing :selected) (not edit))
         ;;(println "From effect init entry-form-otp-start-polling is called")
           (android-af-ef-events/entry-form-otp-start-polling))

         (fn []
         ;; (println "all-sections-content effect cleanup - showing edit in-deleted-category: " showing edit in-deleted-category)
         ;; (println "From effect cleanup entry-form-otp-stop-polling is called")
           (android-af-ef-events/entry-form-otp-stop-polling)))

     ;; Need to pass the list of all reactive values (dependencies) referenced inside of the setup code or empty list
       (clj->js [showing edit]))

    ;; section-names is a list of section names
    ;; section-fields is a list of map - one map for each field in that section
    [rn-view {:style box-style-2}
     (doall
      (for [section-name section-names]
        ^{:key section-name} [section-content edit section-name (get section-fields section-name)]))]))

(defn main-content []
  (let [edit false]
    [rn-view {:style {:flexDirection "column" :justify-content "center" :padding 5}}
     [:f> all-sections-content]
     [notes edit]
     [tags edit]]))

#_(defn main-content []
  [rn-view [rnp-text "Entry form will come here"]])

(defn content []
  [rnc/rn-safe-area-view {:style {:flex 1 :background-color @page-background-color}}
   [rnc/rn-scroll-view {:style {} :contentContainerStyle {:flexGrow 1 :background-color @page-background-color}}
    [main-content]]])