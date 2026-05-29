(ns onekeepass.mobile.remote-connection-unavailable
  (:require
   [onekeepass.mobile.events.dialogs :as dlg-events]
   [onekeepass.mobile.events.open-database :as opndb-events]
   [onekeepass.mobile.rn-components :refer [cust-dialog
                                            rn-view
                                            rnp-button
                                            rnp-dialog-actions
                                            rnp-dialog-content
                                            rnp-dialog-title
                                            rnp-text]]
   [onekeepass.mobile.translation :refer [lstr-bl
                                          lstr-dlg-text
                                          lstr-dlg-title]]))

;; Shown when a remote db could not be opened live (see
;; events/open_database.cljs :open-database-read-kdbx-error). Two kinds:
;;   :config-not-available - the db holding this connection's entry isn't open;
;;                           offer to open that db or open read-only.
;;   :server-unreachable   - the server is unreachable; offer read-only or cancel.
;; "Open Read-Only" reads the latest backup; the title/text translation key is
;; reused across the messageDialog titles and texts sections.
(defn- remote-connection-unavailable-dialog [{:keys [dialog-show data]}]
  (let [{:keys [kind] :as action-data} data
        config-not-available? (= kind :config-not-available)
        text-key (if config-not-available? "remoteConfigNotAvailable" "remoteServerUnreachable")]
    [cust-dialog {:style {} :dismissable false :visible (boolean dialog-show) :onDismiss #()}
     [rnp-dialog-title (lstr-dlg-title text-key)]
     [rnp-dialog-content {:style {:min-height 80}}
      [rnp-text (lstr-dlg-text text-key)]]
     [rnp-dialog-actions {:style {:justify-content "center"}}
      [rnp-button {:mode "text"
                   :onPress #(opndb-events/remote-connection-unavailable-open-read-only action-data)}
       (lstr-bl "openReadOnly")]
      [rnp-button {:mode "text"
                   :onPress #(opndb-events/remote-connection-unavailable-cancel)}
       (lstr-bl "cancel")]
      
      #_[rn-view {:style {}}
         (when config-not-available?
           [rnp-button {:mode "text"
                        :onPress #(opndb-events/remote-connection-unavailable-open-concerned-db)}
            (lstr-bl "opendb")])
         [rn-view {:style {:margin-top 20}}] ;; a gap
         [rnp-button {:mode "text"
                      :onPress #(opndb-events/remote-connection-unavailable-open-read-only action-data)}
          (lstr-bl "openReadOnly")]
         [rn-view {:style {:margin-top 20}}] ;; a gap
         [rnp-button {:mode "text"
                      :onPress #(opndb-events/remote-connection-unavailable-cancel)}
          (lstr-bl "cancel")]]]]))

(defn remote-connection-unavailable-dialog-mounted []
  [remote-connection-unavailable-dialog @(dlg-events/remote-connection-unavailable-dialog-data)])
