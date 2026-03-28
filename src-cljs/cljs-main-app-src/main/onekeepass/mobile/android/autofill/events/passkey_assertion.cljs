(ns onekeepass.mobile.android.autofill.events.passkey-assertion
  "Re-frame events and effects for the Android passkey assertion (Credential Manager) flow.
   All event names and db keys are prefixed with :android-pk-assertion to avoid conflicts."
  (:require
   [onekeepass.mobile.android.autofill.events.common :refer [android-af-active-db-key
                                                             PASSKEY_ASSERTION_PAGE_ID
                                                             to-page]]
   [onekeepass.mobile.background :as bg]
   [onekeepass.mobile.events.common :refer [on-ok]]
   [re-frame.core :refer [dispatch reg-event-fx reg-fx reg-sub subscribe]]))

;; ── Public API (for UI — no direct dispatch/subscribe in UI code) ─────────────

(defn passkey-assertion-items []
  (subscribe [:android-pk-assertion/items]))

(defn passkey-assertion-select [selection]
  (dispatch [:android-pk-assertion/select selection]))

;; ── Subscriptions ─────────────────────────────────────────────────────────────

(reg-sub
 :android-pk-assertion/items
 (fn [db _]
   (get-in db [:android-af :passkey-assertion :items] [])))

(reg-sub
 :android-pk-assertion/rp-id
 (fn [db _]
   (get-in db [:android-af :passkey-assertion :rp-id])))

;; ── Shared context check (routes to assertion or registration) ────────────────

;; Dispatched at startup (from core.cljs) to check whether PasskeyActivity
;; was launched for a passkey request.  If mode is "assertion" the assertion
;; context is stored and we navigate to the passkey assertion page.
;; If mode is "registration" we hand off to the registration namespace.
;; Returns nil context when launched as regular password autofill.
(reg-event-fx
 :android-pk/check-context
 (fn [db _]
   ;; Ensure that any previous values are reset on this launch
   {:db (-> db
            (assoc-in [:android-af :passkey-assertion] {})
            (assoc-in [:android-af :passkey-registration] {}))

    :fx [[:bg/android-get-passkey-context nil]]}))

(reg-event-fx
 :android-pk/context-loaded
 (fn [{:keys [db]} [_ context]]
   (println "android-pk/context-loaded" context)
   (cond
     (nil? context)
     {}

     (= "assertion" (:mode context))
     {:db (-> db
              (assoc-in [:android-af :passkey-assertion :rp-id]
                        (:rp-id context))
              (assoc-in [:android-af :passkey-assertion :allow-credential-ids]
                        (or (:allow-credential-ids context) []))
              (assoc-in [:android-af :passkey-assertion :client-data-hash-b64url]
                        (:client-data-hash-b64url context))
              (assoc-in [:android-af :passkey-assertion :client-data-json-b64url]
                        (:client-data-json-b64url context)))}

     (= "registration" (:mode context))
     {:fx [[:dispatch [:android-pk-registration/context-loaded context]]]}

     :else {})))

;; ── Assertion events ──────────────────────────────────────────────────────────

(reg-event-fx
 :android-pk-assertion/load-passkeys
 (fn [{:keys [db]} [_ rp-id allow-credential-ids]]
   {:db (assoc-in db [:android-af :passkey-assertion :rp-id] rp-id)
    :fx [[:dispatch [:android-pk-assertion/fetch rp-id allow-credential-ids]]]}))

(reg-event-fx
 :android-pk-assertion/fetch
 (fn [{:keys [db]} [_ rp-id allow-credential-ids]]
   (let [db-key (android-af-active-db-key db)]
     {:fx [[:bg/android-find-matching-passkeys
            [db-key rp-id allow-credential-ids
             (fn [response]
               (if-let [items (on-ok response)]
                 (dispatch [:android-pk-assertion/loaded items])
                 nil))]]]})))

(reg-event-fx
 :android-pk-assertion/loaded
 (fn [{:keys [db]} [_ items]]
   {:db (assoc-in db [:android-af :passkey-assertion :items] items)
    :fx [[:dispatch-passkey-assertion-page nil]]}))

;; Called when the user taps a passkey in the assertion list.
;; Single-step flow: FFI call signs the assertion, builds the JSON, and calls the
;; Kotlin callback (PendingIntentHandler + activity.finish) all within Rust.
;; PasskeyActivity is already finished by the time dispatch-fn fires on success.
(reg-event-fx
 :android-pk-assertion/select
 (fn [{:keys [db]} [_ {:keys [entry-uuid db-key]}]]
   (let [hash (get-in db [:android-af :passkey-assertion :client-data-hash-b64url])
         cdj  (get-in db [:android-af :passkey-assertion :client-data-json-b64url])]
     {:fx [[:bg/android-complete-passkey-assertion
            [entry-uuid db-key hash cdj
             (fn [response]
               (println "bg/android-complete-passkey-assertion response" response)
               ;; Activity already finished by Rust→Kotlin callback on success.
               (on-ok response
                      #_(fn [error]
                        (println "passkey assertion error:" error))))]]]})))

;; ── Effects ───────────────────────────────────────────────────────────────────

(reg-fx
 :bg/android-get-passkey-context
 (fn [_]
   (bg/android-get-passkey-context
    (fn [response]
      (println "bg/android-get-passkey-context response" response)
      (dispatch [:android-pk/context-loaded (on-ok response)])))))

(reg-fx
 :bg/android-find-matching-passkeys
 (fn [[db-key rp-id allow-ids dispatch-fn]]
   (bg/android-find-matching-passkeys db-key rp-id allow-ids dispatch-fn)))

(reg-fx
 :bg/android-complete-passkey-assertion
 (fn [[entry-uuid db-key client-data-hash-b64url client-data-json-b64url dispatch-fn]]
   (bg/android-complete-passkey-assertion
    entry-uuid db-key client-data-hash-b64url client-data-json-b64url dispatch-fn)))

(reg-fx
 :dispatch-passkey-assertion-page
 (fn [_]
   (to-page PASSKEY_ASSERTION_PAGE_ID "Select Passkey")))
