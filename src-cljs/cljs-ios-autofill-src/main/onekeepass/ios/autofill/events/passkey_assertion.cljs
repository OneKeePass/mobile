(ns onekeepass.ios.autofill.events.passkey-assertion
  "Re-frame events and effects for the passkey assertion (iOS autofill) flow."
  (:require
   [onekeepass.ios.autofill.background :as bg]
   [onekeepass.ios.autofill.constants :refer [PASSKEY_ASSERTION_PAGE_ID]]
   [onekeepass.ios.autofill.events.common :refer [active-db-key on-ok]]
   [re-frame.core :refer [dispatch reg-event-fx reg-fx reg-sub subscribe]]))

(defn passkey-assertion-select [selection]
  (println "Passkey assertion selected " selection)
  (dispatch [:passkey-assertion/select selection]))

(defn passkey-assertion-items []
  (subscribe [:passkey-assertion/items]))

;; ── Subscriptions ────────────────────────────────────────────────────────────

(reg-sub
 :passkey-assertion/items
 (fn [db _]
   (get-in db [:passkey-assertion :items])))

(reg-sub
 :passkey-assertion/rp-id
 (fn [db _]
   (get-in db [:passkey-assertion :rp-id])))

;; ── Events ───────────────────────────────────────────────────────────────────

;; Dispatched at startup (from :autofill-init-data-loaded) to check whether iOS
;; launched this extension for a passkey assertion request.  If so, stores the
;; context (rp-id + allow-credential-ids) in the db so that :all-entries-loaded
;; can route to the passkey page instead of the normal entry-list.
(reg-event-fx
 :passkey-assertion/check-context
 (fn [_ _]
   {:fx [[:bg/get-pending-passkey-context nil]]}))

;; Stores the passkey context returned by getPendingPasskeyContext.
;; context is {:rp-id "..." :allow-credential-ids [...] :client-data-hash-b64url "..."} or nil.
(reg-event-fx
 :passkey-assertion/context-loaded
 (fn [{:keys [db]} [_ context]]
   (println "Context in :passkey-assertion/context-loaded" context)
   {:db (if context
          (-> db
              (assoc-in [:passkey-assertion :rp-id] (:rp-id context))
              (assoc-in [:passkey-assertion :allow-credential-ids]
                        (or (:allow-credential-ids context) []))
              (assoc-in [:passkey-assertion :client-data-hash-b64url]
                        (:client-data-hash-b64url context)))
          db)}))

;; Called after a database is unlocked when in passkey assertion mode.
;; rp-id is the relying-party identifier; allow-credential-ids is a vec of base64url strings.
(reg-event-fx
 :passkey-assertion/load-passkeys
 (fn [{:keys [db]} [_ rp-id allow-credential-ids]]
   {:db (assoc-in db [:passkey-assertion :rp-id] rp-id)
    :fx [[:dispatch [:passkey-assertion/fetch rp-id allow-credential-ids]]]}))

(reg-event-fx
 :passkey-assertion/fetch
 (fn [{:keys [db]} [_ rp-id allow-credential-ids]]
   (let [db-key (active-db-key db)]
     {:fx [[:bg/find-matching-passkeys
            [db-key rp-id allow-credential-ids
             (fn [response]
               (if-let [items (on-ok response)]
                 (dispatch [:passkey-assertion/loaded items])
                 nil))]]]})))

(reg-event-fx
 :passkey-assertion/loaded
 (fn [{:keys [db]} [_ items]]
   {:db (assoc-in db [:passkey-assertion :items] items)
    :fx [[:dispatch [:common/next-page PASSKEY_ASSERTION_PAGE_ID "Select Passkey"]]]}))

;; Called when the user taps a passkey in the list.
(reg-event-fx
 :passkey-assertion/select
 (fn [{:keys [db]} [_ {:keys [entry-uuid db-key]}]]
   (let [hash (get-in db [:passkey-assertion :client-data-hash-b64url])]
     {:fx [[:bg/complete-passkey-assertion
            [entry-uuid db-key hash
             (fn [response]
               (println "bg/complete-passkey-assertion response" response)
               ;; The extension is completed by the Rust/Swift callback; nothing more to do here.
               nil)]]]})))

;; ── Effects ──────────────────────────────────────────────────────────────────

(reg-fx
 :bg/get-pending-passkey-context
 (fn [_]
   (bg/get-pending-passkey-context
    (fn [response]
      (println "ASSERTION - bg/get-pending-passkey-context handles response" response)
      (dispatch [:passkey-assertion/context-loaded (on-ok response)])))))

(reg-fx
 :bg/find-matching-passkeys
 (fn [[db-key rp-id allow-ids dispatch-fn]]
   (bg/find-matching-passkeys db-key rp-id allow-ids dispatch-fn)))

(reg-fx
 :bg/complete-passkey-assertion
 (fn [[entry-uuid db-key client-data-hash-b64url dispatch-fn]]
   (println "bg/complete-passkey-assertion is called")
   (bg/complete-passkey-assertion db-key entry-uuid client-data-hash-b64url dispatch-fn)))
