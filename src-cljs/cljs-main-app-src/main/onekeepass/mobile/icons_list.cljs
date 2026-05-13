(ns onekeepass.mobile.icons-list
  (:require
   [reagent.core :as r]
   [onekeepass.mobile.events.common :as cmn-events]
   [onekeepass.mobile.events.custom-icons :as ci-events]
   [onekeepass.mobile.rn-components :refer [icon-color
                                            page-background-color
                                            rn-image
                                            rn-pressable
                                            rn-text
                                            rn-view
                                            rnp-button
                                            rnp-dialog
                                            rnp-dialog-actions
                                            rnp-dialog-content
                                            rnp-dialog-title
                                            rnp-icon-button
                                            rnp-segmented-buttons
                                            rnp-text-input
                                            rn-safe-area-view]]
   [onekeepass.mobile.translation :as t :refer [lstr-bl lstr-l]]))

;; All icons are from MaterialCommunityIcons. This will use the react-native-vector-icons library to display the icon.
;; See the https://pictogrammers.com/library/mdi/ for MaterialCommunityIcons
(def standard-icons '[key-variant
                      earth
                      alert-rhombus
                      server-security
                      clipboard-text-outline
                      account-tie
                      cogs
                      notebook-edit-outline
                      debug-step-out
                      badge-account-horizontal
                      at
                      camera
                      weather-lightning
                      key-chain
                      power-plug-outline
                      projector
                      bookmark
                      disc
                      monitor
                      email-open-outline
                      cog-outline
                      clipboard-check-outline
                      note-outline
                      square-circle
                      flash
                      folder-text-outline
                      content-save
                      network-outline
                      movie-roll
                      console
                      console
                      printer
                      vector-square-close
                      dots-grid
                      wrench
                      plus-network-outline
                      folder-download-outline
                      percent
                      monitor-dashboard
                      clock-outline
                      magnify
                      drawing
                      memory
                      delete-circle
                      note-text-outline
                      close-circle
                      help-circle
                      package
                      folder
                      folder-open
                      folder-zip
                      ;;
                      shield-lock-open
                      shield-lock
                      check-circle
                      draw-pen
                      note-text
                      card-account-details
                      text-recognition
                      bucket
                      tools
                      home
                      star
                      penguin
                      android
                      apple
                      wikipedia
                      cash
                      certificate
                      cellphone])

(def icons-count (count standard-icons))

(defn icon-id->name [index]
  (name (nth standard-icons (if (< index icons-count) index 0))))

(defn standard-icons-grid []
  [rn-view {:style {:flexDirection "row" :flexWrap "wrap"}}
   (doall
    (for [[index icon-name] (keep-indexed (fn [i v] [i (name v)]) standard-icons)]
      ^{:key index} [rnp-icon-button {:icon icon-name
                                      :iconColor @icon-color
                                      :onPress #(cmn-events/icon-selected icon-name index)}]))])

(defn- icon-image [uuid]
  ;; Triggers a lazy fetch on first render; the data URL appears once cached.
  (ci-events/ensure-icon-data-url uuid)
  (let [data-url @(ci-events/icon-data-url uuid)]
    (if data-url
      [rn-image {:source {:uri data-url}
                 :style {:width 40 :height 40}}]
      [rn-view {:style {:width 40 :height 40 :justify-content "center"
                        :align-items "center"}}
       [rn-text "…"]])))

(defn- custom-icon-cell [{:keys [uuid]}]
  [rn-pressable {:onPress (fn []
                            ;; icon-id 0 + uuid signals "use this custom icon"
                            (cmn-events/icon-selected "" 0 uuid))
                 :onLongPress (fn []
                                (ci-events/remove-icon uuid))}
   [rn-view {:style {:width 56 :height 56 :margin 4
                     :justify-content "center" :align-items "center"}}
    [icon-image uuid]]])

(defn- url-add-dialog [{:keys [open url on-change on-cancel on-add]}]
  [rnp-dialog {:visible open :dismissable true :onDismiss on-cancel}
   [rnp-dialog-title (t/lstr-dlg-title 'addCustomIcon)]
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
                 :disabled (empty? (and url (.trim url)))
                 :onPress on-add} (lstr-bl 'add)]]])

(defn- custom-icons-tab []
  (let [dialog-state (r/atom {:open false :url ""})]
    (fn []
      (let [icons @(ci-events/icons-list)
            prefill-url @(cmn-events/icon-picker-prefill-url)
            {:keys [open url]} @dialog-state
            close-dialog #(reset! dialog-state {:open false :url ""})
            open-dialog #(reset! dialog-state {:open true
                                               :url (or prefill-url "")})
            on-add (fn []
                     (let [trimmed (some-> url .trim)]
                       (when-not (empty? trimmed)
                         (close-dialog)
                         (ci-events/add-icon-from-url
                          trimmed
                          ;; Once added, the user has indicated they want
                          ;; this icon: select it, which closes the picker.
                          [:common/icon-selected "" 0]))))]
        #_(println "Count of custom icons" (count icons))
        [rn-view {:style {:flex 1}}
         [rn-view {:style {:flex-direction "row" :justify-content "space-around"
                           :padding 8}}
          [rnp-button {:mode "outlined"
                       :icon "link"
                       :onPress open-dialog}
           (lstr-l 'addFromUrl)]
          [rnp-button {:mode "outlined"
                       :icon "file-image"
                       :onPress (fn []
                                  (ci-events/add-icon-from-file
                                   [:common/icon-selected "" 0]))}
           (lstr-l 'addFromFile)]]

         (if (empty? icons)
           [rn-view {:style {:padding 16 :align-items "center"}}
            [rn-text (lstr-l 'noCustomIcons)]]
           [rn-view {:style {:flexDirection "row" :flexWrap "wrap"
                             :padding 8}}
            (doall
             (for [icon icons]
               ^{:key (:uuid icon)} [custom-icon-cell icon]))])

         [url-add-dialog
          {:open open
           :url url
           :on-change #(swap! dialog-state assoc :url %)
           :on-cancel close-dialog
           :on-add on-add}]]))))

(defn main-content []
  (let [tab (r/atom "standard")]
    (fn []
      [rn-view {:style {:flex 1}}
       [rn-view {:style {:padding 8}}
        [rnp-segmented-buttons
         {:value @tab
          :onValueChange (fn [v] (reset! tab v))
          :buttons (clj->js
                    [{:value "standard" :label (lstr-l 'standard)}
                     {:value "custom" :label (lstr-l 'custom)}])}]]
       (if (= @tab "standard")
         [standard-icons-grid]
         [custom-icons-tab])])))

(defn content []
  [rn-safe-area-view {:style {:flex 1 :background-color @page-background-color}}
   [main-content]])
