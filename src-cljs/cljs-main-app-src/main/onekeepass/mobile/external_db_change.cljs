(ns onekeepass.mobile.external-db-change
  (:require
   [onekeepass.mobile.events.dialogs :as dlg-events]
   [onekeepass.mobile.events.external-db-change :as ext-change-events]
   [onekeepass.mobile.rn-components :refer [cust-dialog
                                            rnp-button
                                            rnp-dialog-actions
                                            rnp-dialog-content
                                            rnp-dialog-title
                                            rnp-text]]
   [onekeepass.mobile.translation :refer [lstr-bl
                                          lstr-dlg-text
                                          lstr-dlg-title]]))

;; Shown when the foreground poll (or the manual menu check) sees that the
;; active remote db diverged from what we last cached. User picks Merge to
;; reload/three-way-merge the remote into the in-memory db, or Ignore to snooze
;; this specific remote state for the session. Ignore does NOT touch the backend
;; or the cached mtime — the next save still hits the conflict guard, and a
;; genuinely new remote change (different mtime) re-surfaces this dialog.
(defn- external-db-change-dialog [{:keys [dialog-show data]}]
  (let [{:keys [db-key remote-mtime]} data]
    [cust-dialog {:style {} :dismissable false :visible (boolean dialog-show) :onDismiss #()}
     [rnp-dialog-title (lstr-dlg-title "externalDbChanged")]
     [rnp-dialog-content {:style {:min-height 80}}
      [rnp-text (lstr-dlg-text "externalDbChangedTxt1")]]
     [rnp-dialog-actions
      [rnp-button {:mode "text"
                   :onPress #(ext-change-events/external-change-ignore db-key remote-mtime)}
       (lstr-bl "notNow")]
      [rnp-button {:mode "contained"
                   :onPress #(ext-change-events/external-change-merge-start db-key)}
       (lstr-bl "merge")]]]))

(defn external-db-change-dialog-mounted []
  [external-db-change-dialog @(dlg-events/external-db-change-dialog-data)])
