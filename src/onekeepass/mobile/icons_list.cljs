(ns onekeepass.mobile.icons-list
  (:require
   [onekeepass.mobile.rn-components :refer [icon-color
                                            page-background-color
                                            rn-view
                                            rnp-icon-button
                                            rn-safe-area-view]]
   [onekeepass.mobile.events.common :as cmn-events]))

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

(defn main-content []
  [rn-view {:style {:flexDirection "row" :flexWrap "wrap"}}
   (doall
    (for [[index icon-name] (keep-indexed (fn [i v] [i (name v)]) standard-icons)]
      ^{:key index} [rnp-icon-button {:icon icon-name
                                      :iconColor @icon-color
                                      :onPress #(cmn-events/icon-selected icon-name index)}]))])

(defn content []
  [rn-safe-area-view {:style {:flex 1 :background-color @page-background-color}}
   [main-content]])