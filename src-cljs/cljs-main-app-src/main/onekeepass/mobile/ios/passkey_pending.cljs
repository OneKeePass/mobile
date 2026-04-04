(ns onekeepass.mobile.ios.passkey-pending
  "Review screen for pending passkeys created by the iOS Autofill extension.
   Shows passkeys awaiting commit to the real KDBX database, with Save/Discard actions.
   Also exports the notification snackbar rendered in core.cljs."
  (:require
   [clojure.string :as str]
   [onekeepass.mobile.events.dialogs :as dlg-events]
   [onekeepass.mobile.events.ios.passkey-pending :as pp-events]
   [onekeepass.mobile.events.common :as cmn-events]
   [onekeepass.mobile.rn-components :as rnc :refer [appbar-text-color
                                                    cust-dialog
                                                    cust-rnp-divider
                                                    page-background-color
                                                    page-title-text-variant
                                                    rn-safe-area-view
                                                    rnp-dialog-actions
                                                    rnp-dialog-content
                                                    rnp-dialog-title
                                                    rn-scroll-view rn-view
                                                    rnp-button rnp-list-item
                                                    rnp-snackbar rnp-text]]
   [onekeepass.mobile.translation :refer [lstr-bl lstr-dlg-text lstr-dlg-title lstr-l
                                          lstr-sm]]
   [reagent.core :as r]))

(set! *warn-on-infer* true)

;; This is not used. Intead ios-pending-passkey-notification-dialog is used 
;;;;;;;;;;;;;;;;;;;;;;;;;; Notification snackbar ;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Rendered from core.cljs inside rnp-portal alongside message-snackbar.
;; Visible only when pending passkeys exist (iOS only — state is never
;; populated on Android).

#_(defn pending-passkey-snackbar []
    (let [open  @(pp-events/snackbar-open?)
          cnt   @(pp-events/pending-count)]
      [rnp-snackbar {:visible open
                     :onDismiss pp-events/close-snackbar
                     :action (clj->js {:label (lstr-bl "review")
                                       :onPress pp-events/show-review})
                     :duration 10000
                     :wrapperStyle {:bottom 20 :zIndex 10}}
       (str cnt " " (lstr-sm 'pendingPasskeys))]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Notification Dialog ;;;;;;;;;;;;;;;;;;;;;

(defn ios-pending-passkey-notification-dialog
  ([{:keys [dialog-show]}]
   [cust-dialog {:style {} :dismissable false :visible dialog-show :onDismiss #()}
    [rnp-dialog-title {:ellipsizeMode "tail" :numberOfLines 1 :style {:color @rnc/error-color}} (lstr-dlg-title 'pendingPasskeys)]
    [rnp-dialog-content
     [rn-view {:style {:flexDirection "column" :justify-content "center"}}
      [rnp-text (lstr-dlg-text 'pendingPasskeysMergeNeeded)]]]
    [rnp-dialog-actions
     [rnp-button {:mode "text"
                  :onPress (fn []
                             (dlg-events/ios-pending-passkey-notification-dialog-close)
                             (pp-events/show-review))}
      (lstr-bl 'review)]]])

  ([]
   (ios-pending-passkey-notification-dialog @(dlg-events/ios-pending-passkey-notification-dialog-data))))


;;;;;;;;;;;;;;;;;;;;; Autofill-disable warning dialog ;;;;;;;;;;;;;;;;;;;;

(defn ios-autofill-disable-pending-passkey-dialog
  ([{:keys [dialog-show]}]
   [cust-dialog {:style {} :dismissable false :visible dialog-show :onDismiss #()}
    [rnp-dialog-title {:ellipsizeMode "tail" :numberOfLines 1 :style {:color @rnc/error-color}} (lstr-dlg-title 'pendingPasskeys)]
    [rnp-dialog-content
     [rn-view {:style {:flexDirection "column" :justify-content "center"}}
      [rnp-text (lstr-dlg-text 'pendingPasskeysDisableWarning)]]]
    [rnp-dialog-actions
     [rnp-button {:mode "text"
                  :onPress dlg-events/ios-autofill-disable-pending-passkey-dialog-close}
      (lstr-bl 'close)]
     [rnp-button {:mode "text"
                  :onPress (fn []
                             (dlg-events/ios-autofill-disable-pending-passkey-dialog-close)
                             (pp-events/show-review))}
      (lstr-bl 'review)]]])

  ([]
   (ios-autofill-disable-pending-passkey-dialog
    @(dlg-events/ios-autofill-disable-pending-passkey-dialog-data))))

;;;;;;;;;;;;; All-databases pending passkeys notification dialog ;;;;;;;;;;

(defn- pending-passkeys-by-db
  "Group pending passkeys by database, returning [{:db-name :count}]"
  [items]
  (->> items
       (group-by :org-db-key)
       (mapv (fn [[db-key db-items]]
               {:db-name (last (str/split db-key "/"))
                :count   (count db-items)}))))

(defn ios-all-pending-passkeys-notification-dialog
  ([{:keys [dialog-show pending-passkeys]}]
   (let [by-db (pending-passkeys-by-db (or pending-passkeys []))]
     [cust-dialog {:style {} :dismissable false :visible dialog-show :onDismiss #()}
      [rnp-dialog-title {:ellipsizeMode "tail" :numberOfLines 1 :style {:color @rnc/error-color} } (lstr-dlg-title 'pendingPasskeys)]
      [rnp-dialog-content
       [rn-view {:style {:flexDirection "column"}}
        [rnp-text (lstr-dlg-text 'pendingPasskeysMultiDb)]
        [rn-view {:style {:marginTop 8}}
         (doall
          (for [{:keys [db-name count]} by-db]
            ^{:key db-name}
            [rnp-text {:style {:marginTop 4}} (str "• " db-name " (" count ")")]))]]]
      [rnp-dialog-actions
       [rnp-button {:mode "text"
                    :onPress dlg-events/ios-all-pending-passkeys-notification-dialog-close}
        (lstr-bl 'close)]]]))

  ([]
   (ios-all-pending-passkeys-notification-dialog
    @(dlg-events/ios-all-pending-passkeys-notification-dialog-data))))

;;;;;;;;;;;;;;;;;;;;;;;;;; Review page appbar title ;;;;;;;;;;;;;;;;;;;;;;

(defn appbar-title []
  [rn-view {:flexDirection "row"
            :style {:alignItems "center"
                    :justify-content "space-between"}}
   [rnp-button {:style {}
                :textColor @appbar-text-color
                :mode "text"
                :onPress cmn-events/to-previous-page}
    (lstr-bl "cancel")]
   [rnp-text {:style {:color @appbar-text-color
                      :max-width 200
                      :margin-right 20 :margin-left 20}
              :ellipsizeMode "tail"
              :numberOfLines 1
              :variant page-title-text-variant}
    (lstr-dlg-title 'pendingPasskeys)]
   [rnp-button {:style {}
                :textColor @appbar-text-color
                :disabled (empty? @(pp-events/pending-items))
                :mode "text"
                :onPress pp-events/commit-all}
    (lstr-bl 'saveAll)]])

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
                        (lstr-bl 'discard)]]))}]))

(defn- empty-view []
  [rn-view {:style {:flex 1 :align-items "center" :justify-content "center"}}
   [rnp-text {:variant "bodyLarge"} (lstr-l 'noPendingPasskeys)]])

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
