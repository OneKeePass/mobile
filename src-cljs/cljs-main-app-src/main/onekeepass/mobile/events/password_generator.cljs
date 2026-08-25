(ns onekeepass.mobile.events.password-generator
  (:require
   [clojure.string :as str]
   [re-frame.core :refer [reg-event-db reg-event-fx reg-fx reg-sub dispatch subscribe]]
   [onekeepass.mobile.events.common :as cmn-events :refer [on-error on-ok]]
   [onekeepass.mobile.background :as bg]
   [onekeepass.mobile.constants :as const]))


(def PASS-PHRASE-OPTIONS :pass-phrase-options)

(def PASSWORD-OPTIONS :password-options)

(def write-string-to-clipboard bg/write-string-to-clipboard)

(defn generator-data-update
  "Called to update any field found in the map in [:generator :data]"
  [kw value]
  (dispatch [:generator-data-update kw value]))

(defn password-options-update
  "Called to update any field in the map found in PASSWORD-OPTIONS"
  [kw value]
  (dispatch [:password-options-update kw value]))

;; Called from the password field of an entry form with non nil 'on-selection-callback'
(defn generate-password
  "Receives a callback function that accepts one argument and opens up the password generator
   with the generated password for the default options. 
   on-selection-callback can be nil and in that case no function is called with the generated password
   "
  ([on-selection-callback]
   (dispatch [:password-generator/start on-selection-callback]))
  ([]
   (dispatch [:password-generator/start nil])))

(defn generator-panel-shown-update
  "Called to select the panel to show - Password or Pass Phrase panel"
  [panel-shown]
  (dispatch [:generator-panel-shown-update panel-shown]))

(defn regenerate-password
  "Called to regenerate a new password or pass phrase"
  []
  (dispatch [:regenerate-password]))

(defn generated-password-selected
  "Called to copy the generated password when user presses 'Select' button on appbar"
  []
  (dispatch [:generated-password-selected]))

(defn password-generation-options []
  (subscribe [:password-generation-options]))

(defn generator-password-result []
  (subscribe [:generator-password-result]))

(defn generated-analyzed-password []
  (subscribe [:generated-analyzed-password]))

(defn password-length-slider-value []
  (subscribe [:password-length-slider-value]))

(defn on-selection-available []
  (subscribe [:on-selection-available]))

(defn generator-panel-shown []
  (subscribe [:generator-panel-shown]))

;; (-> @re-frame.db/app-db :generator keys) => (:data)
;; We have just one member :data under :generator 
(def generator-init-data {:panel-shown "password"
                          ;; reflects the slider component value
                          ;; This value is upated continuously when user moves the slider 
                          ;; The initial value is set from :length field of password options map  or :words field of pass phrase options
                          :slider-value nil
                          ;; A function that is called when 'Select' is pressed
                          :on-selection nil
                          ;; All fields from struct PasswordGenerationOptions
                          PASSWORD-OPTIONS {;; length is updated when slider movement completes
                                            ;; This is the fallback default only. Once the user
                                            ;; changes any option, the stored preference is used
                                            ;; - see 'init-dialog-data'
                                            :length 16
                                            :numbers true
                                            :lowercase-letters true
                                            :uppercase-letters true
                                            :symbols true
                                            :spaces false
                                            :exclude-similar-characters true
                                            :strict true}
                          ;; All fields from struct PassphraseGenerationOptions
                          PASS-PHRASE-OPTIONS {:word-list-source {:type-name "EFFLarge"}
                                               ;; words is updated when slider movement completes
                                               :words 3
                                               :separator "."
                                               :capitalize-first {:type-name "Always" :content nil}
                                               :capitalize-words {:type-name "Never" :content nil}}
                          ;; Set by password and pass phrase generated result
                          :password-result {:password nil
                                            :analyzed-password nil
                                            :is-common false
                                            :score {:name nil
                                                    :raw-value nil
                                                    :score-text nil}}})

;; We have just one member :data under :generator
;; Note in 'desktop' app, we use [:generator :dialog-data] instead of [:generator :data]
(defn- init-dialog-data [app-db]
  (let [;; The options used when the generator was last used. These are nil when the stored
        ;; preference.json predates the 'password_gen_preference' field and in that case the
        ;; defaults from 'generator-init-data' are used
        password-options (cmn-events/app-preference-password-generation-options app-db)
        phrase-options (cmn-events/app-preference-phrase-generator-options app-db)
        db (assoc-in app-db [:generator :data] generator-init-data)
        db (if (seq password-options)
             (assoc-in db [:generator :data PASSWORD-OPTIONS] password-options)
             db)
        db (if (seq phrase-options)
             (assoc-in db [:generator :data PASS-PHRASE-OPTIONS] phrase-options)
             db)]
    db))

;; Updates all top level fields of data in :data
(defn- to-generator-data [db & {:as kws}]
  (let [data (get-in db [:generator :data])
        data (merge data kws)]
    (assoc-in db [:generator :data] data)))

;; Updates the password-options fields
(defn- to-password-options-data [db & {:as kws}]
  (let [data (get-in db [:generator :data PASSWORD-OPTIONS])
        data (merge data kws)]
    (assoc-in db [:generator :data PASSWORD-OPTIONS] data)))

(reg-event-db
 :generator-data-update
 (fn [db [_event-id field-name-kw value]]
   (-> db
       (to-generator-data field-name-kw value))))

(reg-event-fx
 :password-options-update
 (fn [{:keys [db]} [_event-id field-name-kw value]]
   (let [db (-> db
                (to-password-options-data field-name-kw value)
                #_(to-generator-data :text-copied false))
         po (get-in db [:generator :data PASSWORD-OPTIONS])]
     {:db db
      :fx [[:bg-analyzed-password po]]})))

(reg-event-fx
 :generated-password-selected
 (fn [{:keys [db]} [_event-id]]
   (let [on-selection-callback-fn (get-in db [:generator :data :on-selection])
         analyzed-password (get-in db [:generator :data :password-result :analyzed-password])]
     (when on-selection-callback-fn
       (on-selection-callback-fn analyzed-password)) ;; side effect
     {:fx [[:dispatch [:common/previous-page]]]})))

(reg-event-fx
 :password-generator/start
 (fn [{:keys [db]} [_event-id on-selection]]
   (let [db (init-dialog-data db)
         po (get-in db [:generator :data PASSWORD-OPTIONS])]
     {:db (-> db (assoc-in [:generator :data :on-selection] on-selection))
      :fx [[:bg-analyzed-password po]]})))

(reg-event-fx
 :regenerate-password
 (fn [{:keys [db]} [_event-id]]
   (let [panel-shown (get-in db [:generator :data :panel-shown])]
     {:fx [(if (= panel-shown "password")
             [:bg-analyzed-password (get-in db [:generator :data PASSWORD-OPTIONS])]
             [:bg-generate-password-phrase (get-in db [:generator :data PASS-PHRASE-OPTIONS])])]})))

(reg-fx
 :bg-analyzed-password
 (fn [password-options]
   (bg/analyzed-password password-options
                         (fn [api-response]
                           ;; result is a map from struct AnalyzedPassword
                           (when-let [result (on-ok api-response)]
                             (dispatch [:password-generation-complete result]))))))

(reg-event-fx
 :password-generation-complete
 (fn [{:keys [db]} [_event-id password-result]]
   (let [;; The password options are stored in the preference so that the generator opens
         ;; with the options the user used last. Saved only when the password panel is the
         ;; active one and the options differ from the stored ones - this handler is also
         ;; called when the generator page is opened with the options loaded from preference
         panel-shown (get-in db [:generator :data :panel-shown])
         pref-po (cmn-events/app-preference-password-generation-options db)
         po (get-in db [:generator :data PASSWORD-OPTIONS])
         modified (and (= panel-shown "password") (not= pref-po po))]
     {:db (-> db
              (assoc-in  [:generator :data :password-result] password-result)
              (assoc-in  [:generator :data :slider-value] (:length password-result)))
      ;; Need to navigate to the generator page if not yet already
      ;; This is an idempotent action
      :fx [[:dispatch [:common/next-page const/PASSWORD_GENERATOR_PAGE_ID  "generator"]]
           (when modified
             [:bg-update-pass-gen-preference {:password-options po}])]})))

;; Called when panel selection is changed
(reg-event-fx
 :generator-panel-shown-update
 (fn [{:keys [db]} [_event-id panel-shown]]
   (let [po (get-in db [:generator :data PASSWORD-OPTIONS])
         ppo (get-in db [:generator :data PASS-PHRASE-OPTIONS])
         slider-value (if (= panel-shown "password")
                        (get-in db [:generator :data PASSWORD-OPTIONS :length])
                        (get-in db [:generator :data PASS-PHRASE-OPTIONS :words]))]
     {:db (-> db (assoc-in [:generator :data :panel-shown] panel-shown)
              (assoc-in  [:generator :data :slider-value] slider-value))
      :fx [(if (= panel-shown "password")
             [:bg-analyzed-password po]
             [:bg-generate-password-phrase ppo])]})))

(reg-sub
 :password-generation-options
 (fn [db [_query-id]]
   (get-in db [:generator :data PASSWORD-OPTIONS])))

(reg-sub
 :generator-password-result
 (fn [db [_query-id]]
   (get-in db [:generator :data :password-result])))

(reg-sub
 :generated-analyzed-password
 (fn [db [_query-id]]
   (get-in db [:generator :data :password-result :analyzed-password])))

(reg-sub
 :password-length-slider-value
 (fn [db [_query-id]]
   (get-in db [:generator :data :slider-value])))

(reg-sub
 :on-selection-available
 (fn [db [_query-id]]
   (if (get-in db [:generator :data :on-selection]) true false)))

(reg-sub
 :generator-panel-shown
 (fn [db [_query-id]]
   (get-in db [:generator :data :panel-shown])))

;;;;;;;;;;;;;;;  Pass phrase  ;;;;;;;;;;;;;;

;; All Pass phrase fields in [:generator :data PASS-PHRASE-OPTIONS]

(defn pass-phrase-options-update
  "Called to update pass phrase option fields that are not enum fields"
  [kw value]
  (dispatch [:pass-phrase-options-update kw value]))

(defn pass-phrase-options-select-field-update
  "Called to update pass phrase option fields that are formed from enums WordListSource and ProbabilityOption "
  [field-name-kw value]
  (dispatch [:pass-phrase-options-select-field-update field-name-kw value]))

(defn pass-phrase-generation-options []
  (subscribe [:pass-phrase-generation-options]))

;; Updates the pass-phrase-options fields
(defn- to-pass-phrase-options-data [db & {:as kws}]
  (let [data (get-in db [:generator :data PASS-PHRASE-OPTIONS])
        data (merge data kws)]
    (assoc-in db [:generator :data PASS-PHRASE-OPTIONS] data)))

(reg-event-fx
 :pass-phrase-options-update
 (fn [{:keys [db]} [_event-id field-name-kw value]]
   (let [db (-> db
                (to-pass-phrase-options-data field-name-kw value))
         {:keys [words] :as po} (get-in db [:generator :data PASS-PHRASE-OPTIONS])]
     (if (empty? (str/trim (str words)))
       {:db db
        :fx [#_[:dispatch [:common/message-snackbar-error-open (tr-m passwordGenerator "atLeastOneWord")]]]}
       {:db db
        :fx [#_[:dispatch [:common/message-snackbar-error-close]]
             [:bg-generate-password-phrase po]]}))))

(reg-event-fx
 :pass-phrase-options-select-field-update
 (fn [{:keys [db]} [_event-id field-name-kw value]] 
   (let [db (-> db (assoc-in [:generator :data PASS-PHRASE-OPTIONS field-name-kw :type-name] value))
         ;; For now, we set probability value of 50% as content. That is half the time
         db (if (or (= field-name-kw :capitalize-first) (= field-name-kw :capitalize-words))
              (if (= value "Sometimes")
                (assoc-in db [:generator :data PASS-PHRASE-OPTIONS field-name-kw :content] 0.5)
                (assoc-in db [:generator :data PASS-PHRASE-OPTIONS field-name-kw :content] nil))
              db)

         po (get-in db [:generator :data PASS-PHRASE-OPTIONS])]

     {:db db
      :fx [[:bg-generate-password-phrase po]]})))


(reg-event-fx
 :pass-phrase-generator/start
 (fn [{:keys [db]} [_event-id on-selection]]
   (let [;;db (init-dialog-data db)
         po (get-in db [:generator :data PASS-PHRASE-OPTIONS])]
     {;;:db (-> db (assoc-in [:generator :data :on-selection] on-selection))
      :fx [[:bg-generate-password-phrase po]]})))

(reg-fx
 :bg-generate-password-phrase
 (fn [pass-phrase-options]
   (bg/generate-password-phrase
    pass-phrase-options
    (fn [api-response]
      ;; gen-pass-phrase is a map from struct GeneratedPassPhrase
      (when-let [gen-pass-phrase (on-ok  api-response)]
        #_(println "result .." gen-pass-phrase)
        (dispatch [:pass-phrase-generation-complete gen-pass-phrase]))))))

(reg-event-fx
 :pass-phrase-generation-complete
 (fn [{:keys [db]} [_event-id {:keys [password] :as gen-pass-phrase}]]
   (let [gen-pass-phrase (assoc gen-pass-phrase :analyzed-password password)
         words (get-in db [:generator :data PASS-PHRASE-OPTIONS :words])
         ;; As done for the password options, the pass phrase options are stored in the
         ;; preference when the user changes any of them
         pref-ppo (cmn-events/app-preference-phrase-generator-options db)
         ppo (get-in db [:generator :data PASS-PHRASE-OPTIONS])
         modified (not= pref-ppo ppo)]
     {:db (-> db
              (assoc-in  [:generator :data :password-result] gen-pass-phrase)
              (assoc-in  [:generator :data :slider-value] words))
         ;; This is an idempotent action and nothing happens if the page is already on PASSWORD_GENERATOR_PAGE_ID
      :fx [[:dispatch [:common/next-page const/PASSWORD_GENERATOR_PAGE_ID  "generator"]]
           (when modified
             [:bg-update-pass-gen-preference {:pass-phrase-options ppo}])]})))

;; Stores the generator options in the app preference (struct PasswordGeneratorPreference)
;; The arg 'preference-data' is a map with the key :password-options or :pass-phrase-options
(reg-fx
 :bg-update-pass-gen-preference
 (fn [preference-data]
   (bg/update-preference preference-data
                         (fn [api-response]
                           (when-not (on-error api-response)
                             ;; Reloads the whole app preference so that the stored options and
                             ;; the in memory ones are the same. Otherwise the comparison done in
                             ;; the generation complete events will keep saving the preference
                             (dispatch [:load-app-preference]))))))

;; Returns all pass phrase option fields to generate pass phrase
(reg-sub
 :pass-phrase-generation-options
 (fn [db [_query-id]]
   (get-in db [:generator :data PASS-PHRASE-OPTIONS])))

(comment
  (in-ns 'onekeepass.mobile.events.password-generator)

  (-> @re-frame.db/app-db :generator :data keys)
  ;; => (:panel-shown :slider-value :on-selection :password-options :pass-phrase-options :password-result :length :text-copied)

  ;; For password
  (-> @re-frame.db/app-db :generator :data :password-result keys)
  ;; => 
  (:uppercase-letters-count
   :spaces-count :numbers-count :non-consecutive-count :password :consecutive-count :symbols-count
   :other-characters-count :lowercase-letters-count :progressive-count :score :length :is-common :analyzed-password)

  ;; for pass phrase 

  (-> @re-frame.db/app-db :generator :data :pass-phrase-options keys)
  ;; => (:word-list-source :words :separator :capitalize-first :capitalize-words)

  (-> @re-frame.db/app-db :generator :data :password-result keys)
  ; => (:password :score :entropy-bits :analyzed-password)

  (def db-key (-> @re-frame.db/app-db :current-db-file-name)))