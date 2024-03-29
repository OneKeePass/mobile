(ns onekeepass.mobile.events.dialogs
  "All common dialog events that are used across many pages"
  (:require-macros [onekeepass.mobile.okp-macros
                    :refer  [def-generic-dialog-events]])
  (:require
   [clojure.string :as str]
   [cljs.core.async :refer [go go-loop timeout <!]]
   [re-frame.core :refer [reg-event-db
                          reg-event-fx
                          reg-fx
                          reg-sub
                          dispatch
                          dispatch-sync
                          subscribe]]
   [onekeepass.mobile.utils :as u :refer [tags->vec str->int]]
   [onekeepass.mobile.background :as bg]))

;;;;;;;;;;;;;;;   confirm-delete-otp-field-dialog  ;;;;;;;;;;;;;;
;; kw :confirm-delete-otp-field-dialog

;; (defn confirm-delete-otp-field-dialog-init [state-m]
;;   (dispatch [:generic-dialog-init :confirm-delete-otp-field-dialog state-m]))

;; (defn confirm-delete-otp-field-dialog-show []
;;   (dispatch [:generic-dialog-show :confirm-delete-otp-field-dialog]))

;; (defn confirm-delete-otp-field-dialog-show-with-state [state-m]
;;   (dispatch [:generic-dialog-show-with-state :confirm-delete-otp-field-dialog state-m]))

;; (defn confirm-delete-otp-field-dialog-on-ok []
;;   (dispatch [:generic-dialog-call-on-ok :confirm-delete-otp-field-dialog]))

;; (defn confirm-delete-otp-field-dialog-close []
;;   (dispatch [:generic-dialog-close :confirm-delete-otp-field-dialog]))

;; (defn confirm-delete-otp-field-dialog-data []
;;   (subscribe [:generic-dialog-data :confirm-delete-otp-field-dialog]))



#_{:clj-kondo/ignore [:unresolved-symbol]}
(def-generic-dialog-events confirm-delete-otp-field-dialog  [[show nil]
                                                             [close nil]
                                                             [on-ok nil]
                                                             [show-with-state state-m]] false)

#_{:clj-kondo/ignore [:unresolved-symbol]}
(def-generic-dialog-events confirm-delete-otp-field-dialog [[data nil]] true)


;;;;;;;;;;;;;;;;;;;;;;;;  setup-otp-action-dialog    ;;;;;;;;;;;;;;;;;;
;; (def SETUP_OTP_ACTION_DIALOG :setup-otp-action-dialog)

;; (defn setup-otp-action-dialog-show []
;;   (dispatch [:generic-dialog-show SETUP_OTP_ACTION_DIALOG]))

;; (defn setup-otp-action-dialog-show-with-state [state-m]
;;   (dispatch [:generic-dialog-show-with-state SETUP_OTP_ACTION_DIALOG state-m]))

;; (defn setup-otp-action-dialog-close []
;;   (dispatch [:generic-dialog-close SETUP_OTP_ACTION_DIALOG]))

;; (defn setup-otp-action-dialog-data []
;;   (subscribe [:generic-dialog-state SETUP_OTP_ACTION_DIALOG]))

#_{:clj-kondo/ignore [:unresolved-symbol]}
(def-generic-dialog-events setup-otp-action-dialog  [[show nil] [close nil] [show-with-state state-m]] false)

#_{:clj-kondo/ignore [:unresolved-symbol]}
(def-generic-dialog-events setup-otp-action-dialog [[data nil]] true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
 (fn [{:keys [db]} [_event-id dialog-identifier-kw {:keys [data dialog-show] :as dialog-state}]]
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

(reg-event-fx
 :generic-dialog-update
 (fn [{:keys [db]} [_event-id dialog-identifier-kw [kws-v value]]]
   {:db (assoc-in db (into [dialog-identifier-kw] kws-v ) value)}))


#_(reg-sub
   :generic-dialog-with-init-state
   (fn [db [_event-id dialog-identifier-kw init-state-m]]
     (let [db (set-dialog-state db dialog-identifier-kw (init-state-m))]
       (get-in db [dialog-identifier-kw])
       #_(if (nil? dg)
           (set-dialog-state db dialog-identifier-kw (init-dialog-map))
           dg))))


(reg-sub
 :generic-dialog-data
 (fn [db [_event-id dialog-identifier-kw]]
   (get-in db [dialog-identifier-kw])
   #_(let [dg (get-in db [dialog-identifier-kw])]
       (if (nil? dg)
         (set-dialog-state db dialog-identifier-kw (init-dialog-map))
         dg))))


(comment
  (in-ns 'onekeepass.mobile.events.dialogs)

  (-> @re-frame.db/app-db keys) ;; will show the generic dialogs keywords 

  (def db-key (-> @re-frame.db/app-db :current-db-file-name))
  (-> @re-frame.db/app-db (get db-key) keys)

  ;; To see all fns defined by the macro call (def-generic-dialog-events...)
  ;; Use the following in a repl
  (require '[clojure.repl :refer [dir]])
  (dir onekeepass.mobile.events.dialogs))