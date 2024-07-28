(ns onekeepass.mobile.android.autofill.events.entry-list
  "Only the Android Autofill specific entry-list events. All events should be prefixed with :android-af"
  (:require [onekeepass.mobile.android.autofill.events.common :refer [ENTRY_LIST_PAGE_ID]]
            [re-frame.core :refer [dispatch reg-event-fx reg-sub subscribe]]))

(defn selected-entry-items []
  (subscribe [:android-af-selected-entry-items]))

(reg-event-fx
 :android-af/entry-list-load-complete
 (fn [{:keys [db]} [_event-id entry-summaries]]
   {:db (-> db
            (assoc-in [:android-af :entry-list :selected-entry-items] entry-summaries))
    :fx [[:dispatch [:android-af-common/next-page ENTRY_LIST_PAGE_ID "Entries"]]]}))

(reg-sub
 :android-af-selected-entry-items
 (fn [db _query-vec]
   (get-in db [:android-af :entry-list :selected-entry-items])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Short and Long press related ;;;;;;;;;;;;;;;;;;;;;;

#_(defn entry-pressed
  "Called when user just presses on an entry"
  [entry-uuid]
  #_(dispatch [:entry-form/send-credentials-selected entry-uuid]))

(defn complete-login-autofill 
  "Complets the autofill activity. For now only used for 'Login' data i.e (username,password)"
  []
  (dispatch [:android-af-entry-form/complete-login-autofill]))

(defn long-press-menu-hide []
  (dispatch [:android-af-long-press-menu-hide]))

(defn long-press-start
  "Called when user does a long press on an entry"
  [x y entry-uuid]
  (dispatch [:android-af-long-press-start x y entry-uuid]))

(defn entry-list-long-press-data []
  (subscribe [:android-af-entry-list-long-press-data]))

(reg-event-fx
 :android-af-long-press-start
 (fn [{:keys [db]} [_event-id x y entry-uuid]]
   {:db (-> db
            (assoc-in  [:android-af :entry-list-long-press :x] x)
            (assoc-in  [:android-af :entry-list-long-press :y] y)
            (assoc-in  [:android-af :entry-list-long-press :show] false))
    :fx [[:dispatch [:android-af-entry-form/find-entry-by-id entry-uuid]]]}))

(reg-event-fx
 :android-af-long-press-menu-hide
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db (assoc-in  [:android-af :entry-list-long-press :show] false))}))

;; :android-af-long-press-start -> :android-af-entry-form/find-entry-by-id -> :android-af-entry-list/form-loaded
;; Called when an entry data is loaded
;; See  android-af entry-form find-entry-by-id event
(reg-event-fx
 :android-af-entry-list/form-loaded
 (fn [{:keys [db]} [_event-id]]
   {:db (assoc-in db [:android-af :entry-list-long-press :show] true)}))

(reg-sub
 :android-af-entry-list-long-press-data
 (fn [db _query-vec]
   (let [d (get-in db [:android-af :entry-list-long-press])]
     (if (empty? d)
       {:show false
        :x 0
        :y 0}
       d))))

(comment
  (in-ns 'onekeepass.mobile.android.autofill.events.entry-list))