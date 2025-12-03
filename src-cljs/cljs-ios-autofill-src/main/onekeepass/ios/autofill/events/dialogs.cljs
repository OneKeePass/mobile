(ns onekeepass.ios.autofill.events.dialogs
  (:require-macros [onekeepass.ios.autofill.okp-macros
                    :refer  [def-generic-dialog-events]])

  (:require [re-frame.core :refer [reg-event-fx  reg-sub]]
            [onekeepass.ios.autofill.utils :as u]))


;; Not yet used in any package ;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;   locked-app-log-in-dialog   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; dialog-identifier-kw is :locked-app-log-in-dialog
(def-generic-dialog-events locked-app-log-in-dialog [[show nil]
                                                     [update-with-map state-m]
                                                     [close nil]] false)

(def-generic-dialog-events locked-app-log-in-dialog [[data nil]] true)


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

;; The event arg is a map  
;; See event :generic-dialog-update where the event arg is a vec

;; state is a map having keys as in 'init-dialog-map'
;; Here we are assuming the 'dialog-state' for the given 'dialog-identifier-kw' is already initialized
;; if the arg 'state' has key ':dialog-show true', then the dialog will be shown
(reg-event-fx
 :generic-dialog-update-with-map
 (fn [{:keys [db]} [_event-id dialog-identifier-kw state]]
   (let [dialog-state (get-in db [dialog-identifier-kw])
         dialog-state (u/deep-merge dialog-state state)]
     {:db (-> db (assoc dialog-identifier-kw dialog-state))})))

;; The event arg is a vec  - [kws-v value]
;; See event :generic-dialog-update-with-map where the event arg is a map

(reg-event-fx
 :generic-dialog-update
 (fn [{:keys [db]} [_event-id dialog-identifier-kw [kws-v value]]]
   (let [db (assoc-in db (into [dialog-identifier-kw] (if (vector? kws-v)
                                                        kws-v
                                                        [kws-v])) value)
         ;; For now clear any previous errors set
         db (assoc-in db [dialog-identifier-kw :error-fields] {})]
     {:db db})))

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

(reg-sub
 :generic-dialog-data
 (fn [db [_event-id dialog-identifier-kw]]
   (get-in db [dialog-identifier-kw])))

(comment
  (in-ns 'onekeepass.ios.autofill.events.dialogs)

  (def db-key (-> @re-frame.db/app-db :current-db-file-name))
  (-> @re-frame.db/app-db  keys)

  (require '[clojure.repl :refer [dir]])
  (dir onekeepass.ios.autofill.events.dialogs))