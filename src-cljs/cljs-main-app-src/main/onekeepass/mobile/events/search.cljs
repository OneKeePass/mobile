(ns onekeepass.mobile.events.search
  (:require
   [re-frame.core :refer [reg-event-db reg-event-fx reg-fx reg-sub dispatch subscribe]]
   [onekeepass.mobile.constants :as const]
   [onekeepass.mobile.events.common :refer [on-ok
                                            assoc-in-key-db
                                            get-in-key-db
                                            active-db-key]]
   [onekeepass.mobile.events.entry-list :refer [sort-entries-with-criteria]]
   [onekeepass.mobile.events.otp-url-received :refer [picking-entry-mode?]]
   [clojure.string :as str]
   [onekeepass.mobile.background :as bg]))

(defn to-search-page []
  (dispatch [:search/to-search-page]))

(defn entry-row-pressed
  "Called when the user presses an entry row of the search result"
  [entry-id]
  (dispatch [:search/entry-row-pressed entry-id]))

(defn search-term-update [term]
  (dispatch [:search-term-update term]))

(defn search-result-entry-items
  "Returns an atom that has a list of all search term matched entry items "
  []
  (subscribe [:search-result-entry-items]))

(defn search-term []
  (subscribe [:search-term]))

(defn search-not-matched
  "True once a search has come back with nothing. False while the search of what has just
   been typed is still to be made, so that 'no result' is not shown before anything has
   actually been looked for"
  []
  (subscribe [:search/not-matched]))

;; The search bar sits on the pages the user spends most of the time on and every key stroke
;; would otherwise mean a backend call. The call is made only after the typing pauses
(def ^:private SEARCH-DEBOUNCE-MILLIS 250)

;; Timeout id of the search waiting to be made, if any
(def ^:private pending-search-timer (atom nil))

(defn- cancel-pending-search []
  (when-let [timer-id @pending-search-timer]
    (js/clearTimeout timer-id)
    (reset! pending-search-timer nil)))

(reg-event-fx
 :search-term-update
 (fn [{:keys [db]} [_event-id term]]
   (if (str/blank? term)
     ;; Nothing to ask the backend for. The search waiting to be made is dropped as well, so
     ;; that its result cannot land after the term has been cleared
     {:db (-> db (assoc-in-key-db [:search :term] term)
              (assoc-in-key-db [:search :result] [])
              (assoc-in-key-db [:search :error-text] nil)
              (assoc-in-key-db [:search :not-matched] false))
      :fx [[:cancel-pending-term-search]]}

     {:db (assoc-in-key-db db [:search :term] term)
      :fx [[:bg-start-term-search-debounced [(active-db-key db) term]]]})))

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
                      ;; The term is carried through so that a result of a term the user has
                      ;; already typed past can be recognised and dropped
                      (dispatch [:search-term-completed result term])))))

;; Backend API call
(reg-fx
 :bg-start-term-search
 ;; fn in 'reg-fx' accepts only single argument
 (fn [[db-key term]]
   (start-term-search db-key term)))

(reg-fx
 :bg-start-term-search-debounced
 (fn [[db-key term]]
   (cancel-pending-search)
   (reset! pending-search-timer
           (js/setTimeout (fn []
                            (reset! pending-search-timer nil)
                            (start-term-search db-key term))
                          SEARCH-DEBOUNCE-MILLIS))))

(reg-fx
 :cancel-pending-term-search
 (fn [_]
   (cancel-pending-search)))

;; Dispatched when the user navigates from one list page to another, so that the page landed
;; on shows its own content and not the matches of a term left over from the page before
;; Any pending otp url pick mode is cancelled as this is the normal way of coming to the
;; search page. See onekeepass.mobile.events.otp-url-received
(reg-event-fx
 :search/to-search-page
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:dispatch [:otp-url-received/picking-cancel]]
         [:dispatch [:common/next-page const/SEARCH_PAGE_ID "search"]]]}))

;; The search page is also used to pick the entry an otp url received from another app is
;; to be added to and then the pressed entry is not shown in the entry form straight away
(reg-event-fx
 :search/entry-row-pressed
 (fn [{:keys [db]} [_event-id entry-id]]
   (if (picking-entry-mode? db)
     {:fx [[:dispatch [:otp-url-received/entry-picked entry-id]]]}
     {:fx [[:dispatch [:entry-form/find-entry-by-id entry-id]]]})))

(reg-event-fx
 :search/term-clear
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db (assoc-in-key-db [:search :term] nil)
            (assoc-in-key-db  [:search :error-text] nil)
            (assoc-in-key-db [:search :selected-entry-id] nil)
            (assoc-in-key-db  [:search :not-matched] false)
            (assoc-in-key-db  [:search :result] []))
    :fx [[:cancel-pending-term-search]]}))

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
 (fn [{:keys [db]} [_event-id result searched-term]]
   ;; result is a map {:entry-items [map of entry summary]} as defined in struct EntrySearchResult
   (let [not-matched (empty? (:entry-items result))]
     ;; A result that belongs to an earlier term is of no use - the user has typed on since
     (if-not (= searched-term (get-in-key-db db [:search :term]))
       {}
       {:db (-> db (assoc-in-key-db  [:search :result] (:entry-items result))
                (assoc-in-key-db [:search :selected-entry-id] nil)
                (assoc-in-key-db  [:search :error-text] nil)
                (assoc-in-key-db  [:search :not-matched] not-matched))}))))

;; Gets the matched entry items if any
(reg-sub
 :search-result-entry-items
 (fn [db _query-vec]
   (let [r (get-in-key-db db [:search :result])]
     ;; Note: if the ':search' key is not present in app-db, the r will be nil
     (if (nil? r)
       []
       ;; Search currently returns matches, not relevance scores. Until relevance-based
       ;; ranking is designed, use the same deterministic ordering as every entry list.
       ;; Sorting here also makes visible results react immediately when the user changes
       ;; the shared list sort criteria.
       (sort-entries-with-criteria db r)))))

(reg-sub
 :search/not-matched
 (fn [db _query-vec]
   (boolean (get-in-key-db db [:search :not-matched]))))

(reg-sub
 :search-selected-entry-id
 (fn [db _query-vec]
   (get-in-key-db db [:search :selected-entry-id])))

(reg-sub
 :search-term
 (fn [db _query-vec]
   (get-in-key-db db [:search :term])))
