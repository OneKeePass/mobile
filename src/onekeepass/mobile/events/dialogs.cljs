(ns onekeepass.mobile.events.dialogs
  "All common dialog events that are used across many pages"
  (:require-macros [onekeepass.mobile.okp-macros
                    :refer  [def-generic-dialog-events as-map]])
  (:require [clojure.string :as str]
            [onekeepass.mobile.constants :refer [OTP_KEY_DECODE_ERROR]]
            [onekeepass.mobile.events.common :refer [on-ok]]
            [onekeepass.mobile.events.entry-form-common :refer [is-field-exist]]
            [re-frame.core :refer [dispatch reg-event-fx  reg-sub]]
            [onekeepass.mobile.utils :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;; macro def-generic-dialog-events used ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; The macro (def-generic-dialog-events setup-otp-action-dialog [[show nil]]) will 
;; generate wrapper functions something like the following

;; (clojure.core/defn setup-otp-action-dialog-show []
;;   (re-frame.core/dispatch [:generic-dialog-show :setup-otp-action-dialog]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;   confirm-delete-otp-field-dialog  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Creates wrapper functions confirm-delete-otp-field-dialog-show, confirm-delete-otp-field-dialog-close
;; confirm-delete-otp-field-dialog-on-ok, confirm-delete-otp-field-dialog-show,confirm-delete-otp-field-dialog-show-with-state
(def-generic-dialog-events confirm-delete-otp-field-dialog  [[show nil]
                                                             [close nil]
                                                             [on-ok nil]
                                                             [show-with-state state-m]] false)

;; Creates the subscribe event wrapper fn 'confirm-delete-otp-field-dialog-data'
;; Last argument 'true' means this macro is called to generate a subscribe event wrapper
(def-generic-dialog-events confirm-delete-otp-field-dialog [[data nil]] true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  setup-otp-action-dialog   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def-generic-dialog-events setup-otp-action-dialog  [[show nil] [close nil] [show-with-state state-m]] false)

(def-generic-dialog-events setup-otp-action-dialog [[data nil]] true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  otp-settings-dialog    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; fields-value is a vector of [kws-v value] [[:data :some-field] value] or [:some-field value]
(def-generic-dialog-events otp-settings-dialog  [[show nil] [close nil] [show-with-state state-m] [update fields-value]] false)

(def-generic-dialog-events otp-settings-dialog [[data nil]] true)

(defn otp-settings-dialog-complete-ok []
  (dispatch [:otp-settings-dialog-complete-ok]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  start-page-storage-selection-dialog   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; dialog-identifier-kw start-page-storage-selection-dialog

(def-generic-dialog-events start-page-storage-selection-dialog  [#_[init state-m]
                                                                 #_[update-and-show state-m]
                                                                 #_[show nil]
                                                                 [close nil]
                                                                 [show-with-state state-m]] false)

;; a subscribe event wrapper
(def-generic-dialog-events start-page-storage-selection-dialog [[data nil]] true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; auto-open-key-file-pick-required-info-dialog ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; 

#_(def-generic-dialog-events auto-open-key-file-pick-required-info-dialog  [[init state-m] [dispatch-on-ok nil] [close nil]] false)

;; dialog-identifier-kw :auto-open-key-file-pick-required-info-dialog

(def-generic-dialog-events auto-open-key-file-pick-required-info-dialog  [[close nil]] false)

(def-generic-dialog-events auto-open-key-file-pick-required-info-dialog  [[data nil]] true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; auto-open-db-file-required-info-dialog ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; dialog-identifier-kw is :auto-open-db-file-required-info-dialog

(def-generic-dialog-events auto-open-db-file-required-info-dialog [[close nil]] false)

;; a subscribe event wrapper
(def-generic-dialog-events auto-open-db-file-required-info-dialog [[data nil]] true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;   before-storage-selection-info-dialog   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def-generic-dialog-events before-storage-selection-info-dialog [[close nil] [show-with-state state-m]] false)

(def-generic-dialog-events before-storage-selection-info-dialog [[data nil]] true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- init-dialog-map
  "Called to initialize fields of a dialog identified by 'dialog-identifier-kw' 
   Returns a map which is set as intial values of these fieldds"
  []
  (-> {}
      (assoc-in [:dialog-show] false)
      (assoc-in [:title] nil)
      (assoc-in [:confirm-text] nil)
      (assoc-in [:actions] [])
      (assoc-in [:call-on-ok-fn] #())
      (assoc-in [:dispatch-on-ok] {})
      (assoc-in [:error-fields] {})
      (assoc-in [:api-error-text] nil)
      (assoc-in [:data] {})))

;; new-dialog-state is a map
(defn- set-dialog-state
  "The arg 'new-dialog-state' is a map (keys similar to ones listed in 'init-dialog-map' fn)
   Returns the updated map"
  [db dialog-identifier-kw new-dialog-state]
  (assoc-in db [dialog-identifier-kw] new-dialog-state))

;; Called to initialize all fields of a dialog identified by 'dialog-identifier-kw'
;; The arg 'dialog-state' may have no :dialog-show key (default is false) or :dialog-show false or :dialog-show false 
;; If we want to initialize and show dialog, then the event ':generic-dialog-show-with-state' should be used
(reg-event-fx
 :generic-dialog-init
 (fn [{:keys [db]} [_event-id dialog-identifier-kw dialog-state]]
   (let [final-dialog-state (init-dialog-map)
         final-dialog-state (u/deep-merge final-dialog-state dialog-state) #_(merge final-dialog-state dialog-state)]
     {:db (set-dialog-state db dialog-identifier-kw final-dialog-state)})))

;; Shows the dialog
;; Typically called through (dispatch [:generic-dialog-show dialog-identifier-kw])
(reg-event-fx
 :generic-dialog-show
 (fn [{:keys [db]} [_event-id dialog-identifier-kw]]
   (let [db (assoc-in db [dialog-identifier-kw :dialog-show] true)]
     {:db db})))

;; Initializes the dialog data with the initial state map,
;; updates the state with passed arg 'state-m' and shows the dialog  

;; Typically called through a wrapper fn '(dialog-identifier-show-with-state state-m)'
;; which in turns call
;; (dispatch [:generic-dialog-show-with-state :dialog-identifier state-m])

(reg-event-fx
 :generic-dialog-show-with-state
 (fn [{:keys [_db]} [_event-id dialog-identifier-kw state-m]]
   {:fx [[:dispatch [:generic-dialog-init dialog-identifier-kw (assoc state-m :dialog-show true)]]]}))

;; state is a map having keys as in 'init-dialog-map'
;; Here we are assuming the 'dialog-state' for the given 'dialog-identifier-kw' is already initialized
;; if the arg 'state' has key ':dialog-show true', then the dialog will be shown
(reg-event-fx
 :generic-dialog-update-with-map
 (fn [{:keys [db]} [_event-id dialog-identifier-kw state]]
   (let [dialog-state (get-in db [dialog-identifier-kw])
         dialog-state (u/deep-merge dialog-state state)]
     {:db (-> db (assoc dialog-identifier-kw dialog-state))})))

;; Not yet used
;; Updates the existing dialog state and sets :dialog-show true so that dialog is shown
;; This is similar to event ':generic-dialog-update-with-map' but ensures that dialog is open
(reg-event-fx
 :generic-dialog-update-and-show
 (fn [{:keys [db]} [_event-id dialog-identifier-kw state]]
   (let [dialog-state (get-in db [dialog-identifier-kw])
         dialog-state (u/deep-merge dialog-state state)
         dialog-state (assoc dialog-state :dialog-show true)]
     {:db (-> db (assoc dialog-identifier-kw dialog-state))})))

;; Called to execute a fn that is set in field ':call-on-ok-fn' and closes the dialog 
;; It is assumed some valid fn is set for 'call-on-ok-fn'
(reg-event-fx
 :generic-dialog-on-ok
 (fn [{:keys [db]} [_event-id dialog-identifier-kw]]
   (let [call-on-ok-fn (get-in db [dialog-identifier-kw :call-on-ok-fn])]
     ;; Side effect?
     (call-on-ok-fn)
     {:fx [[:dispatch [:generic-dialog-close dialog-identifier-kw]]]})))

;; Not yet used
;; Called to dispatch an event that is set in field ':dispatch-on-ok' and closes the dialog 
;; It is assumed some valid fn is set for 'dispatch-on-ok'
;; dispatch-on-ok is a map with keys [fx-event-kw args]
(reg-event-fx
 :generic-dialog-dispatch-on-ok
 (fn [{:keys [db]} [_event-id dialog-identifier-kw]]
   (let [{:keys [fx-event-kw args]} (get-in db [dialog-identifier-kw :dispatch-on-ok])]
     (if-not (nil? fx-event-kw)
       {:fx [[:dispatch [fx-event-kw args]]
             [:dispatch [:generic-dialog-close dialog-identifier-kw]]]}
       {:fx [[:dispatch [:generic-dialog-close dialog-identifier-kw]]]}))))

(reg-event-fx
 :generic-dialog-close
 (fn [{:keys [db]} [_event-id dialog-identifier-kw]]
   {:db (assoc-in db [dialog-identifier-kw] (init-dialog-map))}))

(reg-event-fx
 :generic-dialog-update
 (fn [{:keys [db]} [_event-id dialog-identifier-kw [kws-v value]]]
   (let [db (assoc-in db (into [dialog-identifier-kw] (if (vector? kws-v)
                                                        kws-v
                                                        [kws-v])) value)
         ;; For now clear any previous errors set
         db (assoc-in db [dialog-identifier-kw :error-fields] {})]
     {:db db})))

(reg-sub
 :generic-dialog-data
 (fn [db [_event-id dialog-identifier-kw]]
   (get-in db [dialog-identifier-kw])))

(declare validate-otp-settings-fields)

(def field-validators {:otp-settings-dialog validate-otp-settings-fields})


;;;;;;;;;;;;;;;;;;;;;;;;;;; Following are otp-settings-dialog specific ;;;;;;;;;;;;;;;;;;;

;; All otp-settings-dialog specific fields are under the key :otp-settings-dialog

(defn validate-otp-settings-fields
  "Validates each field and accumulates the errors"
  [db]
  (let [{:keys [secret-or-url standard-field field-name code-entry-type]} (get-in db [:otp-settings-dialog])]
    (cond-> {}
      (and (= code-entry-type :manual) (nil? secret-or-url))
      (assoc :secret-or-url "A valid value is required for secret code")

      (and (not standard-field) (nil? field-name))
      (assoc :field-name "A valid field name is required ")

      (and (not standard-field) (is-field-exist db field-name) #_(= OTP field-name))
      (assoc :field-name "Please provide another name for the field")

      #_(or (nil? period) (or (< period 1) (> period 60)))
      #_(assoc :period "Valid values should be in the range 1 - 60")

      #_(or (nil? digits) (or (< digits 6) (> digits 10)))
      #_(assoc :digits "Valid values should be in the range 6 - 10"))))

(defn callback-on-form-otp-url [api-response]
  (when-let [opt-url (on-ok
                      api-response
                      #(dispatch [:otp-settings-form-url-error %]))]
    (dispatch [:otp-settings-form-url-success opt-url])))

(defn manual-or-scan-qr-dispatch [section-name field-name standard-field secret-or-url code-entry-type]
  (if (= code-entry-type :manual)
    [[:entry-form/bg-form-otp-url [{:secret-or-url secret-or-url} callback-on-form-otp-url]]]
    [[:scan-qr/initiate-scan-qr (as-map [section-name field-name standard-field])]
     [:dispatch [:generic-dialog-close :otp-settings-dialog]]]))

(reg-event-fx
 :otp-settings-dialog-complete-ok
 (fn [{:keys [db]} [_event-id]]
   (let [errors (validate-otp-settings-fields db)
         errors-found (boolean (seq errors))
         {:keys [section-name field-name standard-field secret-or-url code-entry-type]} (get-in db [:otp-settings-dialog])
         dispatch-events (when-not errors-found
                           (manual-or-scan-qr-dispatch
                            section-name field-name standard-field secret-or-url code-entry-type))]
     (if errors-found
       {:db (assoc-in db [:otp-settings-dialog :error-fields] errors)
        :fx []}
       {:fx dispatch-events}))))

;; Called when api call returns any error
(reg-event-fx
 :otp-settings-form-url-error
 (fn [{:keys [db]} [_event-id error]]
   (let [secret-code-field-error (if (str/starts-with? error OTP_KEY_DECODE_ERROR)
                                   (assoc {} :secret-or-url "Valid encoded key or full TOTPAuth URL is required") {})]
     {:db (-> db
              (assoc-in [:otp-settings-dialog :error-fields] secret-code-field-error))
      ;; :fx can have empty [], not nil; Otherwise re-frame will throw warning
      :fx (if (empty? secret-code-field-error)
            [[:dispatch [:generic-dialog-close :otp-settings-dialog]] ;; Using the generic close event :generic-dialog-close
             [:dispatch [:common/error-box-show "Error" error]]]
            [])})))

(reg-event-fx
 :otp-settings-form-url-success
 (fn [{:keys [db]} [_event-id otp-url]]
   (let [{:keys [section-name field-name standard-field]} (get-in db [:otp-settings-dialog])]
     ;; We can also use [:dispatch [:generic-dialog-close :otp-settings-dialog] instead of updating db with 
     ;; init-dialog-map call done here
     {:db  (-> db (assoc-in [:otp-settings-dialog] (init-dialog-map)))
      :fx  [[:dispatch [:entry-form/otp-url-form-success (as-map [section-name field-name otp-url standard-field])]]]})))

(comment
  (in-ns 'onekeepass.mobile.events.dialogs)

  (-> @re-frame.db/app-db keys) ;; will give the kw name of all generic dialogs when opened

  ;; :setup-otp-action-dialog is dialog specific kw
  ;; all fields specific this dialog is under this kw and will have some value after opening the dialog first time 

  (-> @re-frame.db/app-db :setup-otp-action-dialog keys)

  ;; => (:call-on-ok-fn :show-action-as-vertical :actions 
  ;; :title :confirm-text :error-fields :api-error-text :dialog-show :section-name :data)

  (def db-key (-> @re-frame.db/app-db :current-db-file-name))
  (-> @re-frame.db/app-db (get db-key) keys)

  ;; To see all fns defined by the macro call (def-generic-dialog-events...)
  ;; Use the following in a repl
  (require '[clojure.repl :refer [dir]])
  (dir onekeepass.mobile.events.dialogs))