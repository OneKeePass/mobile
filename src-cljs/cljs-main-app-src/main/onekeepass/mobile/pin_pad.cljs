(ns onekeepass.mobile.pin-pad
  "A PIN entry panel with a row of dots and a number pad, in the manner of the system lock
   screens. Used for the app lock screen and for the PIN check and set up in app lock settings.
   The pad is drawn here as neither iOS nor Android offers a PIN pad component to apps.
   Using it in place of a text field also keeps the PIN away from the system and third party
   keyboards"
  (:require
   [onekeepass.mobile.background :refer [is-iOS]]
   [onekeepass.mobile.rn-components :as rnc :refer [rn-animated
                                                    rn-animated-view
                                                    rn-pressable
                                                    rn-view
                                                    rnp-icon
                                                    rnp-button
                                                    rnp-text]]
   [onekeepass.mobile.translation :refer [lstr-bl lstr-ml]]
   [reagent.core :as r]))

;; Long enough for any PIN set with the earlier text field
(def ^:private MAX-PIN-LENGTH 16)

;; The dot row shows at least these many places so that it does not jump about as the
;; first few digits are entered. The PIN length is not stored and the row grows past this as needed
(def ^:private MIN-DOT-PLACES 4)

(def ^:private KEY-SIZE 76)

(def ^:private DOT-SIZE 12)

;; Pause after the last digit of a fixed length PIN so that the last dot is seen filled
;; before the PIN is checked
(def ^:private AUTO-SUBMIT-DELAY 120)

(defn- shake
  "Plays a short side to side shake on the dot row when a PIN is not accepted"
  [^js/RNAnimatedValue anim-value]
  (.setValue anim-value 0)
  (.start (.sequence rn-animated
                     (into-array (map (fn [to-value]
                                        (.timing rn-animated anim-value #js {:toValue to-value
                                                                             :duration 50
                                                                             :useNativeDriver true}))
                                      [12 -12 9 -9 4 0])))))

(defn- dot-row [digit-count pin-length]
  (let [filled-color @rnc/primary-color
        empty-color @rnc/outline-color]
    [rn-view {:style {:flexDirection "row" :flexWrap "wrap" :justifyContent "center"}}
     (for [i (range (or pin-length (max MIN-DOT-PLACES digit-count)))]
       ^{:key i} [rn-view {:style {:width DOT-SIZE
                                   :height DOT-SIZE
                                   :borderRadius (/ DOT-SIZE 2)
                                   :margin 7
                                   :borderWidth 1.5
                                   :borderColor (if (< i digit-count) filled-color empty-color)
                                   :backgroundColor (if (< i digit-count) filled-color "transparent")}}])]))

(defn- pad-key
  "One round key. iOS keys have a filled circle that darkens while pressed. Android keys are
   flat with the ripple that Android uses for its own PIN pad"
  [{:keys [on-press on-long-press disabled accessibility-label]} child]
  (let [ios? (is-iOS)
        key-color @rnc/secondary-container-color
        pressed-color @rnc/primary-container-color]
    [rn-pressable {:onPress on-press
                   :onLongPress on-long-press
                   :disabled disabled
                   :accessibilityRole "button"
                   :accessibilityLabel accessibility-label
                   :accessibilityState #js {:disabled (boolean disabled)}
                   :android_ripple (when-not ios? #js {:color pressed-color
                                                       :borderless true
                                                       :radius (/ KEY-SIZE 2)})
                   :style (fn [^js state]
                            #js {:width KEY-SIZE
                                 :height KEY-SIZE
                                 :borderRadius (/ KEY-SIZE 2)
                                 :margin 9
                                 :alignItems "center"
                                 :justifyContent "center"
                                 :opacity (if disabled 0.35 1)
                                 :backgroundColor (cond
                                                    (not ios?) "transparent"
                                                    (.-pressed state) pressed-color
                                                    :else key-color)})}
     child]))

(defn pin-pad
  "The PIN entry panel. The props map has these keys
   :title          - text above the dots
   :header         - optional component shown between the title and the dots
   :message        - optional text below the dots, e.g a hint or a warning
   :message-error? - shows the message in the error color when true
   :pin-length     - when given, the PIN has this many digits. The dots show that many places,
                     the PIN is submitted once the last digit is entered and there is no done key
                     (as on iOS). Otherwise the PIN may be of any length and the done key
                     submits it (as on Android)
   :min-length     - digits required before the done key is enabled. Default is 1
   :on-submit      - (fn [pin-str rejected-fn]). 'rejected-fn' is to be called when the PIN is
                     not accepted or could not be checked. The dots then shake and clear and the
                     pad takes digits again. When the PIN is accepted, the caller moves away from
                     the pad (or remounts it with a new key) and nothing is called
   :on-cancel      - optional. A cancel button is shown under the pad when this is given"
  [_props]
  (let [digits (r/atom "")
        ;; Set while the caller checks a submitted PIN so that a second press of the done key
        ;; does not submit it again. Each wrong submission counts against the allowed attempts
        submitting (r/atom false)
        anim-value (new (.-Value rn-animated) 0)
        rejected-fn (fn []
                      (reset! digits "")
                      (reset! submitting false)
                      (shake anim-value))
        add-digit (fn [d pin-length on-submit]
                    (when (and (not @submitting)
                               (< (count @digits) (or pin-length MAX-PIN-LENGTH)))
                      (swap! digits str d)
                      (when (= (count @digits) pin-length)
                        (reset! submitting true)
                        (js/setTimeout #(on-submit @digits rejected-fn) AUTO-SUBMIT-DELAY))))
        remove-digit (fn []
                       (when-not @submitting
                         (swap! digits #(subs % 0 (max 0 (dec (count %)))))))]
    (fn [{:keys [title header message message-error? pin-length min-length on-submit on-cancel]
          :or {min-length 1}}]
      (let [digit-count (count @digits)
            text-color @rnc/on-background-color
            done-disabled (or @submitting (< digit-count min-length))]
        [rn-view {:style {:align-items "center" :justify-content "center" :padding-vertical 16}}
         [rnp-text {:variant "titleLarge" :style {:color text-color :text-align "center"}} title]

         (when header
           [rn-view {:style {:margin-top 16}} header])

         [rn-animated-view {:style {:margin-top 20
                                    :min-height (+ DOT-SIZE 14)
                                    :max-width (* 3 (+ KEY-SIZE 18))
                                    :transform [{:translateX anim-value}]}}
          [dot-row digit-count pin-length]]

         ;; Kept at a fixed height so that the pad does not move when a message appears
         [rn-view {:style {:min-height 44 :justify-content "center" :padding-horizontal 24}}
          (when message
            [rnp-text {:variant "bodyMedium"
                       :style {:text-align "center"
                               :color (if message-error? @rnc/error-color @rnc/on-surface-variant)}}
             message])]

         (for [row [[1 2 3] [4 5 6] [7 8 9]]]
           ^{:key (first row)}
           [rn-view {:style {:flexDirection "row"}}
            (for [d row]
              ^{:key d}
              [pad-key {:on-press #(add-digit d pin-length on-submit) :accessibility-label (str d)}
               [rnp-text {:variant "headlineMedium" :style {:color text-color}} (str d)]])])

         (let [delete-key [pad-key {:on-press remove-digit
                                    ;; A long press clears all digits
                                    :on-long-press #(when-not @submitting (reset! digits ""))
                                    :disabled (zero? digit-count)
                                    :accessibility-label (lstr-ml 'delete)}
                           [rnp-icon {:source "backspace-outline" :size 28 :color text-color}]]
               zero-key [pad-key {:on-press #(add-digit 0 pin-length on-submit) :accessibility-label "0"}
                         [rnp-text {:variant "headlineMedium" :style {:color text-color}} "0"]]]
           (if pin-length
             ;; iOS passcode layout - empty, 0, delete
             [rn-view {:style {:flexDirection "row"}}
              [rn-view {:style {:width KEY-SIZE :height KEY-SIZE :margin 9}}]
              zero-key
              delete-key]
             ;; Android PIN layout - delete, 0, done
             [rn-view {:style {:flexDirection "row"}}
              delete-key
              zero-key
              [pad-key {:on-press (fn []
                                    (reset! submitting true)
                                    (on-submit @digits rejected-fn))
                        :disabled done-disabled
                        :accessibility-label (lstr-bl "ok")}
               [rnp-icon {:source "check" :size 30 :color text-color}]]]))

         (when on-cancel
           [rnp-button {:style {:margin-top 12} :mode "text" :onPress on-cancel}
            (lstr-bl "cancel")])]))))
