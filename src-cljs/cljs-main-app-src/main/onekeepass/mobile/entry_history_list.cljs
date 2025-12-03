(ns onekeepass.mobile.entry-history-list
  (:require
   [reagent.core :as r]
   [onekeepass.mobile.rn-components :as rnc :refer [icon-color
                                                    page-background-color
                                                    rnp-menu
                                                    rnp-menu-item
                                                    rn-safe-area-view
                                                    rn-section-list
                                                    rnp-list-item
                                                    rnp-divider
                                                    rnp-list-icon
                                                    rnp-text]]
   [onekeepass.mobile.translation :refer [lstr-ml lstr-bl lstr-dlg-text lstr-dlg-title]]
   [onekeepass.mobile.date-utils :refer [utc-str-to-local-datetime-str]]
   [onekeepass.mobile.icons-list :refer [icon-id->name]]
   [onekeepass.mobile.common-components :refer [confirm-dialog message-modal message-dialog]]
   [onekeepass.mobile.events.common :as cmn-events]
   [onekeepass.mobile.events.entry-form :as form-events]))

(set! *warn-on-infer* true)

;;;;;;;;;; Menu ;;;;;;;;
(def ^:private entry-long-press-menu-data (r/atom {:show false :x 0 :y 0}))

(defn hide-entry-long-press-menu []
  (swap! entry-long-press-menu-data assoc :show false))

(defn show-entry-long-press-menu [^js/PEvent event entry-uuid history-index]
  (swap! entry-long-press-menu-data assoc
         :show true
         :entry-uuid entry-uuid
         :history-index history-index
         :x (-> event .-nativeEvent .-pageX) :y (-> event .-nativeEvent .-pageY)))

;; TODO: Add Restore menu item. This requires backend api works
(defn entry-long-press-menu [{:keys [show x y]}]
  [rnp-menu {:visible show :key (str show) :onDismiss hide-entry-long-press-menu :anchor (clj->js {:x x :y y})}
   [rnp-menu-item {:title (lstr-ml "delete")
                   :disabled  @(cmn-events/current-db-disable-edit)
                   :onPress (fn [_e]
                              (hide-entry-long-press-menu)
                              (form-events/show-history-entry-delete-confirm-dialog))}]])

;;;;;;;;;;;;;;;;;;;;;;;;

(defn delete-dialog []
  (let [entry-uuid (:entry-uuid @entry-long-press-menu-data)
        index (:history-index @entry-long-press-menu-data)]
    [confirm-dialog {:dialog-show @(form-events/history-entry-delete-flag)
                     :title (lstr-dlg-title "historyDelete")
                     :confirm-text (lstr-dlg-text "historyDelete")
                     :actions [{:label (lstr-bl "yes")
                                :on-press #(form-events/delete-history-entry-by-index entry-uuid index)}
                               {:label (lstr-bl "no")
                                :on-press form-events/close-history-entry-delete-confirm-dialog}]}]))

(defn delete-all-dialog []
  (let [entry-uuid @(form-events/entry-form-uuid)]
    [confirm-dialog {:dialog-show @(form-events/history-entry-delete-all-flag)
                     :title (lstr-dlg-title "historyDeleteAll")
                     :confirm-text (lstr-dlg-text "historyDeleteAll")
                     :actions [{:label (lstr-bl "yes")
                                :on-press #(form-events/delete-all-history-entries entry-uuid)}
                               {:label (lstr-bl "no")
                                :on-press form-events/close-history-entry-delete-all-confirm-dialog}]}]))

(defn row-item []
  (fn [{:keys [title secondary-title icon-id uuid history-index] :as _entry-summary}]
    (let [icon-name (icon-id->name icon-id)]
      [rnp-list-item {:onPress #(form-events/load-selected-history-entry uuid history-index)
                      :onLongPress  (fn [e] (show-entry-long-press-menu e uuid history-index))
                      :title (r/as-element
                              [rnp-text {:variant "titleMedium"} title])
                      :description (utc-str-to-local-datetime-str secondary-title)
                      :left (fn [_props] (r/as-element
                                          [rnp-list-icon
                                           {:style {:align-self "center"}
                                            :icon icon-name
                                            :color @icon-color}]))}])))

(defn main-content []
  (let [sections  [{:title "Entries"
                    :key "Entries"
                    :data @(form-events/history-summary-list)}]]
    [rn-section-list {:sections (clj->js sections)
                      :renderItem (fn [props] ;; keys are (:item :index :section :separators)
                                    (let [props (js->clj props :keywordize-keys true)]
                                      (r/as-element [row-item (-> props :item)])))
                      :ItemSeparatorComponent (fn [_p]
                                                (r/as-element [rnp-divider]))
                      :stickySectionHeadersEnabled false
                      :renderSectionHeader nil}]))


(defn content []
  [rn-safe-area-view {:style {:flex 1 :background-color @page-background-color}}
   [main-content]
   [delete-dialog]
   [delete-all-dialog]
   [entry-long-press-menu @entry-long-press-menu-data]
   [message-modal @(cmn-events/message-modal-data)]
   [message-dialog @(cmn-events/message-dialog-data)]])