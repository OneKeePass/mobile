(ns onekeepass.mobile.otp-badge
  "The current token of an entry, shown on a row of an entry list.

   Kept apart from the entry form's own otp field, which is a full text input with a
   countdown ring beside it. A row has space for a code and little else, and there may be
   as many rows as the database has entries, so the time left is shown by a bar that the
   native thread animates rather than by anything javascript has to redraw."
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [onekeepass.mobile.rn-components :as rnc :refer [rn-animated
                                                            rn-animated-view
                                                            rn-easing
                                                            rn-view
                                                            rnp-text]]))

;; Width the bar is drawn at. The bar sits under the code and is scaled horizontally, so
;; this is also the width the code is centred over
(def ^:private BAR-WIDTH 74)

(def ^:private BAR-HEIGHT 2)

(defn formatted-token
  "Groups digits with spaces between them for easy reading"
  [token]
  (let [len (count token)
        n (cond
            (or (= len 6) (= len 7) (= len 9))
            3

            (or (= len 8) (= len 10))
            4

            :else
            3)
        ;; step = n, pad = ""
        parts (partition n n "" token)
        parts (map (fn [c] (str/join c)) parts)
        spaced (str/join " " parts)]
    spaced))

(defn- animate-bar
  "Runs the bar down from the share of the period the token has left to nothing, over
   exactly the seconds it has left.

   The time left is worked out from 'expires-at' and the clock rather than from the ttl the
   backend replied with. That ttl is what was left at the moment of the fetch and is never
   updated afterwards, so anything mounting later - a row returning from the entry form,
   say - would read it as a full period and start the bar from the top.

   'useNativeDriver' hands the animation to the native thread, so no javascript runs while
   it plays - which is what makes one of these affordable on every row of a list. Only a
   transform can be driven that way, hence scaling the bar rather than resizing it. The
   linear easing is what makes it read as a clock; the default eases in and out."
  [^js/RNAnimatedValue anim-value expires-at period]
  (let [remaining (/ (max 0 (- expires-at (js/Date.now))) 1000)
        total (max 1 (or period 1))]
    (.setValue anim-value (min 1 (/ remaining total)))
    (.start (.timing rn-animated
                     anim-value
                     #js {:toValue 0
                          :duration (* 1000 remaining)
                          :easing (.-linear ^js/RNEasing rn-easing)
                          :useNativeDriver true}))))

(defn otp-badge
  "The token of one entry with the time it has left.

   'token-data' is a map of [token expires-at period], or nil when the entry has no code to
   show - in which case nothing is rendered at all, so a row without 2FA looks exactly as
   it did before.

   Colours are passed in rather than read from a theme namespace, so that the same
   component serves the extension and the main app."
  [_token-data _colors]
  (let [anim-value (new (.-Value rn-animated) 1)
        ;; Re-run whenever a new expiry arrives, and on mounting. The bar is otherwise left
        ;; alone, since the native thread is already playing the animation started for the
        ;; expiry in hand. Keyed on the expiry rather than on the token so that a refresh
        ;; which happens to return the same token still restarts the bar
        started-for (atom nil)
        ensure-animation (fn [{:keys [token expires-at period]}]
                           (when (and token expires-at (not= expires-at @started-for))
                             (reset! started-for expires-at)
                             (animate-bar anim-value expires-at period)))]

    (r/create-class
     {:display-name "otp-badge"

      ;; argv holds the component itself at the head, so the first argument is second
      :component-did-mount
      (fn [this] (ensure-animation (second (r/argv this))))

      :component-did-update
      (fn [this _old-argv] (ensure-animation (second (r/argv this))))

      :reagent-render
      (fn [{:keys [token] :as _token-data} {:keys [code-color bar-color bar-track-color]}]
        (when-not (str/blank? token)
          [rn-view {:style {:align-items "center" :justify-content "center"}}
           [rnp-text {:variant "titleMedium"
                      :numberOfLines 1
                      :style {:color code-color
                              :letter-spacing 0.5
                              :font-variant ["tabular-nums"]}}
            (formatted-token token)]

           ;; The track stays put and clips the bar, which is scaled from its left edge so
           ;; that it empties towards the right the way a countdown reads
           [rn-view {:style {:margin-top 3
                             :width BAR-WIDTH
                             :height BAR-HEIGHT
                             :borderRadius BAR-HEIGHT
                             :overflow "hidden"
                             :background-color bar-track-color}}
            [rn-animated-view {:style {:width BAR-WIDTH
                                       :height BAR-HEIGHT
                                       :background-color bar-color
                                       :transformOrigin "left"
                                       :transform [{:scaleX anim-value}]}}]]]))})))
