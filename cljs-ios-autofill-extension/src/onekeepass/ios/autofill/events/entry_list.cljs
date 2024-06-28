(ns onekeepass.ios.autofill.events.entry-list
  (:require [onekeepass.ios.autofill.events.common :refer [ENTRY_LIST_PAGE_ID]]
            [re-frame.core :refer [dispatch reg-event-fx reg-sub subscribe]]))


(defn find-entry-by-id [entry-uuid]
  (dispatch [:entry-form/find-entry-by-id entry-uuid]))

(defn selected-entry-items []
  (subscribe [:selected-entry-items]))

;; On successful loading of entries, we also set current-db-file-name to db-key so that
;; we can use 'active-db-key' though only one db is opened at a time
(reg-event-fx
 :update-selected-entry-items
 (fn [{:keys [db]} [_event-id db-key entry-summaries]]
   {:db (-> db
            (assoc-in [:current-db-file-name] db-key)
            (assoc-in  [:entry-list :selected-entry-items] entry-summaries))
    :fx [[:dispatch [:common/next-page ENTRY_LIST_PAGE_ID "Entries"]]]}))


(reg-sub
 :selected-entry-items
 (fn [db _query-vec]
   (get-in db [:entry-list :selected-entry-items])))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Menu Related ;;;;;;;;;;;;;;;;;;;;;;

(defn long-press-menu-hide []
  (dispatch [:long-press-menu-hide]))

(defn long-press-start [x y entry-uuid]
  (dispatch [:long-press-start x y entry-uuid]))

(defn entry-list-long-press-data []
  (subscribe [:entry-list-long-press-data]))

(reg-event-fx
 :long-press-start
 (fn [{:keys [db]} [_event-id x y entry-uuid]]
   {:db (-> db
            (assoc-in  [:entry-list-long-press :x] x)
            (assoc-in  [:entry-list-long-press :y] y)
            (assoc-in  [:entry-list-long-press :show] false))
    :fx [[:dispatch [:entry-form/find-entry-by-id-1 entry-uuid]]]}))

(reg-event-fx
 :long-press-menu-hide
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db (assoc-in  [:entry-list-long-press :show] false))}))


(reg-event-fx
 :entry-list/form-loaded
 (fn [{:keys [db]} [_event-id]]
   {:db (assoc-in db [:entry-list-long-press :show] true)}))

(reg-sub
 :entry-list-long-press-data
 (fn [db _query-vec]
   (let [d (get-in db [:entry-list-long-press])]
     (if (empty? d)
       {:show false
        :x 0
        :y 0
        }
       d))))

