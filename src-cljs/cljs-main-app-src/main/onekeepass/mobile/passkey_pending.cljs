(ns onekeepass.mobile.passkey-pending
  "Review screen for pending passkeys created by the iOS Autofill extension.
   Shows passkeys awaiting commit to the real KDBX database, with Save/Discard actions.
   Also exports the notification snackbar rendered in core.cljs."
  (:require
   [reagent.core :as r]
   [onekeepass.mobile.rn-components :as rnc :refer [cust-rnp-divider
                                                    page-background-color
                                                    rn-safe-area-view
                                                    rn-scroll-view
                                                    rn-view
                                                    rnp-button
                                                    rnp-list-item
                                                    rnp-snackbar
                                                    rnp-text]]
   [onekeepass.mobile.events.passkey-pending :as pp-events]
   [onekeepass.mobile.translation :refer [lstr-bl lstr-pt lstr-sm]]))

(set! *warn-on-infer* true)

;;;;;;;;;;;;;;;;;;;;;;;;;; Notification snackbar ;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Rendered from core.cljs inside rnp-portal alongside message-snackbar.
;; Visible only when pending passkeys exist (iOS only — state is never
;; populated on Android).

(defn pending-passkey-snackbar []
  (let [open  @(pp-events/snackbar-open?)
        cnt   @(pp-events/pending-count)]
    [rnp-snackbar {:visible open
                   :onDismiss pp-events/close-snackbar
                   :action (clj->js {:label (lstr-bl "review")
                                     :onPress pp-events/show-review})
                   :duration 10000
                   :wrapperStyle {:bottom 20 :zIndex 10}}
     (str cnt " " (lstr-sm 'pendingPasskeys))]))

;;;;;;;;;;;;;;;;;;;;;;;;;; Review page ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- format-unix-ts
  "Format a Unix timestamp (seconds) as a human-readable local date string."
  [unix-secs]
  (when (pos? unix-secs)
    (.toLocaleDateString (js/Date. (* 1000 unix-secs)))))

(defn- pending-item
  [{:keys [record-uuid org-db-key created-at-unix passkey-info]}]
  (let [rp-id    (:rp-id passkey-info)
        username (:username passkey-info)
        date-str (format-unix-ts (or created-at-unix 0))]
    [rnp-list-item
     {:title       (r/as-element [rnp-text {:variant "titleMedium"} rp-id])
      :description (str (when (seq username) (str username "  ·  "))
                        (or date-str ""))
      :right       (fn [_props]
                     (r/as-element
                      [rn-view {:style {:flex-direction "row" :align-items "center"}}
                       [rnp-button {:mode     "text"
                                    :compact  true
                                    :on-press #(pp-events/commit record-uuid org-db-key)}
                        (lstr-bl "save")]
                       [rnp-button {:mode     "text"
                                    :compact  true
                                    :on-press #(pp-events/discard record-uuid org-db-key)}
                        (lstr-bl "discard")]]))}]))

(defn- empty-view []
  [rn-view {:style {:flex 1 :align-items "center" :justify-content "center"}}
   [rnp-text {:variant "bodyLarge"} (lstr-pt "noPendingPasskeys")]])

(defn content []
  (let [items @(pp-events/pending-items)]
    [rn-safe-area-view {:style {:flex 1 :background-color @page-background-color}}
     (if (empty? items)
       [empty-view]
       [rn-scroll-view
        (doall
         (for [item items]
           ^{:key (:record-uuid item)}
           [:<>
            [pending-item item]
            [cust-rnp-divider]]))])]))
