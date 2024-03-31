(ns onekeepass.mobile.events.dialogs
  "All common dialog events that are used across many pages"
  (:require-macros [onekeepass.mobile.okp-macros
                    :refer  [def-generic-dialog-events]])
  (:require [clojure.string :as str]
            [onekeepass.mobile.background :as bg]
            [onekeepass.mobile.constants :refer [ONE_TIME_PASSWORD_TYPE]]
            [onekeepass.mobile.events.common :refer [on-ok]]
            [onekeepass.mobile.events.entry-form-common :refer [add-section-field
                                                                is-field-exist]]
            [re-frame.core :refer [dispatch reg-event-fx reg-fx reg-sub]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;; macro def-generic-dialog-events used ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The macro will generate wrapper functions something like the following

;; (clojure.core/defn setup-otp-action-dialog-show []
;;   (re-frame.core/dispatch [:generic-dialog-show :setup-otp-action-dialog]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;   confirm-delete-otp-field-dialog  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def-generic-dialog-events confirm-delete-otp-field-dialog  [[show nil]
                                                             [close nil]
                                                             [on-ok nil]
                                                             [show-with-state state-m]] false)


(def-generic-dialog-events confirm-delete-otp-field-dialog [[data nil]] true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  setup-otp-action-dialog   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def-generic-dialog-events setup-otp-action-dialog  [[show nil] [close nil] [show-with-state state-m]] false)

(def-generic-dialog-events setup-otp-action-dialog [[data nil]] true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  otp-settings-dialog    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; fields-value is a vector of [kws-v value] [[:data :some-field] value] or [:some-field value]
(def-generic-dialog-events otp-settings-dialog  [[show nil] [close nil] [show-with-state state-m] [update fields-value]] false)

(def-generic-dialog-events otp-settings-dialog [[data nil]] true)

(defn otp-settings-dialog-manual-code-entered-ok []
  (dispatch [:otp-settings-dialog-manual-code-entered-ok]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; 

(defn- init-dialog-map
  "Returns a map"
  []
  (-> {}
      (assoc-in [:dialog-show] false)
      (assoc-in [:title] nil)
      (assoc-in [:confirm-text] nil)
      (assoc-in [:actions] [])
      (assoc-in [:call-on-ok-fn] #())
      (assoc-in [:error-fields] {})
      (assoc-in [:api-error-text] nil)
      (assoc-in [:data] {})))

;; new-dialog-state is a map
(defn- set-dialog-state
  "Returns the updated map"
  [db dialog-identifier-kw new-dialog-state]
  (assoc-in db [dialog-identifier-kw] new-dialog-state))

(reg-event-fx
 :generic-dialog-init
 (fn [{:keys [db]} [_event-id dialog-identifier-kw dialog-state]]
   (let [final-dialog-state (init-dialog-map)
         final-dialog-state (merge final-dialog-state dialog-state)]
     {:db (set-dialog-state db dialog-identifier-kw final-dialog-state)})))

;; Shows the dialog
(reg-event-fx
 :generic-dialog-show
 (fn [{:keys [db]} [_event-id dialog-identifier-kw]]
   (let [db (assoc-in db [dialog-identifier-kw :dialog-show] true)]
     {:db db})))

;; Shows the dialog and initializes the dialog data with the initial state map
(reg-event-fx
 :generic-dialog-show-with-state
 (fn [{:keys [_db]} [_event-id dialog-identifier-kw state]]
   {:fx [[:dispatch [:generic-dialog-init dialog-identifier-kw (assoc state :dialog-show true)]]]}))

;; Called to execute a fn that is set in field ':call-on-ok-fn' and closes the dialog 
;; It is assumed some valid fn is set for 'call-on-ok-fn'
(reg-event-fx
 :generic-dialog-on-ok
 (fn [{:keys [db]} [_event-id dialog-identifier-kw]]
   (let [call-on-ok-fn (get-in db [dialog-identifier-kw :call-on-ok-fn])]
     (call-on-ok-fn)
     {:fx [[:dispatch [:generic-dialog-close dialog-identifier-kw]]]})))

(reg-event-fx
 :generic-dialog-close
 (fn [{:keys [db]} [_event-id dialog-identifier-kw]]
   {:db (assoc-in db [dialog-identifier-kw] (init-dialog-map))}))

(declare validate-otp-settings-fields)

(def field-validators {:otp-settings-dialog validate-otp-settings-fields})

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

;;;;;;;;;;;;;;;;;;;;;;;;;;; Following are otp-settings-dialog specific ;;;;;;;;;;;;;;;;;;;

;; All otp-settings-dialog specific fields are under the key :otp-settings-dialog

(defn validate-otp-settings-fields
  "Validates each field and accumulates the errors"
  [db]
  (let [{:keys [secret-or-url standard-field field-name]} (get-in db [:otp-settings-dialog])]
    (cond-> {}
      (nil? secret-or-url)
      (assoc :secret-or-url "A valid value is required for secret code")

      (and (not standard-field) (nil? field-name))
      (assoc :field-name "A valid field name is required ")

      (and (not standard-field) (is-field-exist db field-name) #_(= OTP field-name))
      (assoc :field-name "Please provide another name for the field")

      #_(or (nil? period) (or (< period 1) (> period 60)))
      #_(assoc :period "Valid values should be in the range 1 - 60")

      #_(or (nil? digits) (or (< digits 6) (> digits 10)))
      #_(assoc :digits "Valid values should be in the range 6 - 10"))))

(reg-event-fx
 :otp-settings-dialog-manual-code-entered-ok
 (fn [{:keys [db]} [_event-id]]
   (let [errors (validate-otp-settings-fields db)
         errors-found (boolean (seq errors))]
     (if errors-found
       {:db (assoc-in db [:otp-settings-dialog :error-fields] errors)}
       {:fx [[:bg-form-otp-url [{:secret-or-url (get-in db [:otp-settings-dialog :secret-or-url])}]]]}))))

(reg-fx
 :bg-form-otp-url
 (fn [[otp-settings]]
   (bg/form-otp-url otp-settings (fn [api-response]
                                   (when-let [opt-url (on-ok
                                                       api-response
                                                       #(dispatch [:otp-settings-form-url-error %]))]
                                     (dispatch [:otp-settings-form-url-success opt-url]))))))

;; Called when api call returns any error
(reg-event-fx
 :otp-settings-form-url-error
 (fn [{:keys [db]} [_event-id error]]
   (let [secret-code-field-error (if (str/starts-with? error "OtpKeyDecodeError")
                                   (assoc {} :secret-or-url "Valid encoded key or full TOTPAuth URL is required") {})]
     {:db (-> db
              (assoc-in [:otp-settings-dialog :error-fields] secret-code-field-error))
      :fx (when (empty? secret-code-field-error)
            [[:dispatch [:generic-dialog-close :otp-settings-dialog]] ;; Using the generic close event :generic-dialog-close
             [:dispatch [:common/error-box-show "Error" error]]])})))

(reg-event-fx
 :otp-settings-form-url-success
 (fn [{:keys [db]} [_event-id otp-url]]
   (let [{:keys [section-name field-name standard-field]} (get-in db [:otp-settings-dialog])
         ;; Need to add the field to the section if it is not standard field
         ;; A map corresponding to struct KeyValueData is passed to add this extra otp field
         db (if standard-field  db (add-section-field db {:section-name section-name
                                                          :field-name field-name
                                                          :protected true
                                                          :required false
                                                          :data-type ONE_TIME_PASSWORD_TYPE}))]

     ;; We can also use [:dispatch [:generic-dialog-close :otp-settings-dialog] instead of updating db with 
     ;; init-dialog-map call done here
     {:db  (-> db (assoc-in [:otp-settings-dialog] (init-dialog-map)))
      :fx [[:dispatch [:entry-form/otp-url-formed section-name field-name otp-url]]]})))



(comment
  (in-ns 'onekeepass.mobile.events.dialogs)

  (-> @re-frame.db/app-db keys) ;; will show the generic dialogs keywords 

  (def db-key (-> @re-frame.db/app-db :current-db-file-name))
  (-> @re-frame.db/app-db (get db-key) keys)

  ;; To see all fns defined by the macro call (def-generic-dialog-events...)
  ;; Use the following in a repl
  (require '[clojure.repl :refer [dir]])
  (dir onekeepass.mobile.events.dialogs))