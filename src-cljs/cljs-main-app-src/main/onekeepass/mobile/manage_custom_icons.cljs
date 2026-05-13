(ns onekeepass.mobile.manage-custom-icons
  "Per-database Manage Custom Icons page — list existing icons with a small
   × delete button (confirms before removing) plus Add From URL / Add From
   File buttons. Mirrors the desktop dialog of the same name."
  (:require
   [re-frame.core :refer [dispatch]]
   [reagent.core :as r]
   [onekeepass.mobile.common-components :refer [confirm-dialog-with-lstr]]
   [onekeepass.mobile.events.custom-icons :as ci-events]
   [onekeepass.mobile.rn-components :refer [icon-color
                                            page-background-color
                                            rn-image
                                            rn-safe-area-view
                                            rn-text
                                            rn-view
                                            rnp-button
                                            rnp-dialog
                                            rnp-dialog-actions
                                            rnp-dialog-content
                                            rnp-dialog-title
                                            rnp-icon-button
                                            rnp-text-input]]
   [onekeepass.mobile.translation :as t :refer [lstr-bl lstr-l lstr-dlg-title lstr-dlg-text]]))

;; Local state for the delete-confirm dialog. We keep a single instance
;; for the whole page so any cell's × button drives it.
(defonce ^:private delete-confirm-state (r/atom {:show false :uuid nil}))

(defn- show-delete-confirm [uuid]
  (reset! delete-confirm-state {:show true :uuid uuid}))

(defn- hide-delete-confirm []
  (reset! delete-confirm-state {:show false :uuid nil}))

(defn- icon-image [uuid]
  (ci-events/ensure-icon-data-url uuid)
  (let [data-url @(ci-events/icon-data-url uuid)]
    (if data-url
      [rn-image {:source {:uri data-url}
                 :style {:width 48 :height 48}}]
      [rn-view {:style {:width 48 :height 48 :justify-content "center"
                        :align-items "center"}}
       [rn-text "…"]])))

(defn- icon-cell [{:keys [uuid]}]
  [rn-view {:style {:width 72 :height 80 :margin 4 :padding 4
                    :border-width 1 :border-color "#ccc" :border-radius 4
                    :justify-content "center" :align-items "center"}}
   [icon-image uuid]
   [rnp-icon-button {:icon "close"
                     :iconColor @icon-color
                     :size 14
                     :style {:position "absolute" :top -6 :right -6
                             :margin 0 :padding 0}
                     :onPress #(show-delete-confirm uuid)}]])

(defn- delete-confirm-dialog []
  (let [{:keys [show uuid]} @delete-confirm-state]
    [confirm-dialog-with-lstr
     {:dialog-show show
      :title 'deleteCustomIcon 
      :confirm-text 'deleteCustomIcon
      :actions [{:label 'cancel :on-press hide-delete-confirm}
                {:label 'ok
                 :on-press (fn []
                             (hide-delete-confirm)
                             (println "Going to call ci-events/remove-icon for uuid" uuid)
                             (when uuid (ci-events/remove-icon uuid)))}]}]))

(defn- url-add-dialog [{:keys [open url on-change on-cancel on-add]}]
  [rnp-dialog {:visible open :dismissable true :onDismiss on-cancel}
   [rnp-dialog-title (lstr-dlg-title 'addCustomIcon)]
   [rnp-dialog-content
    [rnp-text-input {:label (lstr-l 'url)
                     :autoCapitalize "none"
                     :autoCorrect false
                     :keyboardType "url"
                     :value (or url "")
                     :onChangeText on-change}]]
   [rnp-dialog-actions
    [rnp-button {:mode "text" :onPress on-cancel} (lstr-bl 'cancel)]
    [rnp-button {:mode "text"
                 :disabled (or (nil? url) (empty? (.trim url)))
                 :onPress on-add} (lstr-bl 'add)]]])

(defn main-content []
  (let [dialog-state (r/atom {:open false :url ""})]
    (fn []
      (let [icons @(ci-events/icons-list)
            {:keys [open url]} @dialog-state
            close-dialog #(reset! dialog-state {:open false :url ""})
            on-add (fn []
                     (let [trimmed (some-> url .trim)]
                       (when-not (empty? trimmed)
                         (close-dialog)
                         (ci-events/add-icon-from-url trimmed nil))))]
        [rn-view {:style {:flex 1 :padding 8}}
         [rn-view {:style {:flex-direction "row" :justify-content "space-around"
                           :padding 8}}
          [rnp-button {:mode "outlined"
                       :icon "link"
                       :onPress #(reset! dialog-state {:open true :url ""})}
           (lstr-l 'addFromUrl)]
          [rnp-button {:mode "outlined"
                       :icon "file-image"
                       :onPress (fn [] (ci-events/add-icon-from-file nil))}
           (lstr-l 'addFromFile)]]

         (if (empty? icons)
           [rn-view {:style {:padding 16 :align-items "center"}}
            [rn-text (lstr-l 'noCustomIcons)]]
           [rn-view {:style {:flexDirection "row" :flexWrap "wrap"
                             :padding 8}}
            (doall
             (for [icon icons]
               ^{:key (:uuid icon)} [icon-cell icon]))])

         [url-add-dialog
          {:open open
           :url url
           :on-change #(swap! dialog-state assoc :url %)
           :on-cancel close-dialog
           :on-add on-add}]

         [delete-confirm-dialog]]))))

(defn content []
  [rn-safe-area-view {:style {:flex 1 :background-color @page-background-color}}
   [main-content]])

(defn open-page
  "Navigate to the Manage Custom Icons page."
  []
  (dispatch [:common/next-page :manage-custom-icons "manageCustomIcons"]))
