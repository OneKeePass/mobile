(ns onekeepass.mobile.events.search
  (:require
   [re-frame.core :refer [reg-event-db reg-event-fx reg-fx reg-sub dispatch subscribe]]
   [onekeepass.mobile.events.common :refer [on-ok
                                            assoc-in-key-db
                                            get-in-key-db
                                            active-db-key]]
   [clojure.string :as str]
   [onekeepass.mobile.background :as bg]))

(defn to-search-page []
  (dispatch [:common/next-page :search "search"]))

(defn show-selected-entry [entry-id]
  (dispatch [:entry-form/find-entry-by-id entry-id]))

(defn search-term-update [term]
  (dispatch [:search-term-update term]))

(defn search-result-entry-items
  "Returns an atom that has a list of all search term matched entry items "
  []
  (subscribe [:search-result-entry-items]))

(defn search-term []
  (subscribe [:search-term]))

(reg-event-fx
 :search-term-update
 (fn [{:keys [db]} [_event-id term]] 
   {:db (assoc-in-key-db db [:search :term] term)
    :fx [[:bg-start-term-search [(active-db-key db) term]]]}))

;; Called to refresh the search results 
;; if there is any update in the entry form after any previous search 
(reg-event-fx
 :search/reload
 (fn [{:keys [db]} [_event-id]]
   (let [term (get-in-key-db db [:search :term])]
     (if-not (str/blank? term)
       {:fx [[:bg-start-term-search [(active-db-key db) term]]]}
       {}))))

(defn- start-term-search [db-key term] 
  (bg/search-term db-key term
                  (fn [api-response]
                    (when-let [result (on-ok api-response #(dispatch [:search-error-text %]))]
                      (dispatch [:search-term-completed result])))))

;; Backend API call 
(reg-fx
 :bg-start-term-search
 ;; fn in 'reg-fx' accepts only single argument
 (fn [[db-key term]] 
   (start-term-search db-key term)))

(reg-event-db
 :search-term-clear
 (fn [db [_event-id]]
   (-> db (assoc-in-key-db [:search :term] nil)
       (assoc-in-key-db  [:search :error-text] nil)
       (assoc-in-key-db [:search :selected-entry-id] nil)
       (assoc-in-key-db  [:search :result] []))))

(reg-event-db
 :search-error-text
 (fn [db [_event-id error-text]] 
   (-> db (assoc-in-key-db  [:search :error-text] error-text)
       (assoc-in-key-db [:search :selected-entry-id] nil)
       (assoc-in-key-db  [:search :result] []))))

(reg-event-db
 :search-selected-entry-id-update
 (fn [db [_event-id uuid]]
   (assoc-in-key-db db [:search :selected-entry-id] uuid)))

(reg-event-fx
 :search-term-completed
 (fn [{:keys [db]} [_event-id result]]
   ;; result is a map {:entry-items [map of entry summary]} as defined in struct EntrySearchResult
   (let [not-matched (empty? (:entry-items result))]
     {:db (-> db (assoc-in-key-db  [:search :result] (:entry-items result))
              (assoc-in-key-db [:search :selected-entry-id] nil)
              (assoc-in-key-db  [:search :error-text] nil)
              (assoc-in-key-db  [:search :not-matched] not-matched))})))

;; Gets the matched entry items if any
(reg-sub
 :search-result-entry-items
 (fn [db _query-vec]
   (let [r (get-in-key-db db [:search :result])]
     ;; Note: if the ':search' key is not present in app-db, the r will be nil
     (if (nil? r)
       []
       r))))  

(reg-sub
 :search-selected-entry-id
 (fn [db _query-vec]
   (get-in-key-db db [:search :selected-entry-id])))

(reg-sub
 :search-term
 (fn [db _query-vec]
   (get-in-key-db db [:search :term])))