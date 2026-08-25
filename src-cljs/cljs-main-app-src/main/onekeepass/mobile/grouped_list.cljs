(ns onekeepass.mobile.grouped-list
  "Building blocks shared by the entry category page and the entry list page.

   Both pages show a section list where the rows of a section are drawn on a rounded card
   that sits on the page ground, each section is introduced by a quiet header that can
   collapse it, and both carry a search bar that searches the whole database."
  (:require
   [clojure.string :as str]
   [onekeepass.mobile.constants :as const]
   [onekeepass.mobile.rn-components :as rnc :refer [grouped-list-card-color
                                                    no-assist-text-props
                                                    on-surface-variant
                                                    outline-variant rn-view
                                                    rnp-icon-button
                                                    rnp-searchbar rnp-text
                                                    rnp-touchable-ripple]]
   [onekeepass.mobile.translation :refer [lstr-l]]))

(set! *warn-on-infer* true)

;; Gap kept between a card and either edge of the screen. Also used by whatever a page puts
;; beside its cards, so that everything on the page lines up
(def CARD-SIDE-MARGIN 12)

;; Space at the end of a scrollable list for pages whose add FAB floats over the content.
;; This lets the final row scroll fully above the button instead of remaining behind it.
;; Did not find any difference, but leaving it here to verify it really works or not
(def FAB-LIST-BOTTOM-CLEARANCE 88)

(def ^:private CARD-CORNER-RADIUS 12)

;; Left inset of the separator drawn between two rows of a card. Lines the separator up with
;; the row title instead of running it under the row's leading icon
(def ^:private ROW-SEPARATOR-INSET 60)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Cards ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn card-row
  "Wraps a single rendered row so that all rows of a section read as one rounded card.
   Only the first and the last row of a section get their outer corners rounded"
  [{:keys [first-row? last-row?]} row]
  [rn-view {:style (cond-> {:background-color @grouped-list-card-color
                            :margin-left CARD-SIDE-MARGIN
                            :margin-right CARD-SIDE-MARGIN
                            ;; Keeps the touch ripple of the row inside the rounded corners
                            :overflow "hidden"}

                     first-row?
                     (assoc :borderTopLeftRadius CARD-CORNER-RADIUS
                            :borderTopRightRadius CARD-CORNER-RADIUS)

                     last-row?
                     (assoc :borderBottomLeftRadius CARD-CORNER-RADIUS
                            :borderBottomRightRadius CARD-CORNER-RADIUS
                            :margin-bottom 16))}
   row])

(defn card-block-style
  "Style of a card that holds something other than the rows of a section list - a block of form
   fields, a panel of text. All four of its corners are rounded, where a card built out of rows
   rounds only the two at each of its ends"
  []
  {:background-color @grouped-list-card-color
   :margin-left CARD-SIDE-MARGIN
   :margin-right CARD-SIDE-MARGIN
   :margin-bottom 16
   :borderRadius CARD-CORNER-RADIUS
   ;; Keeps what the card holds - a field's underline, a row's touch feedback - inside the
   ;; rounded corners
   :overflow "hidden"})

(defn card-row-separator
  "Separator between two rows of the same card. It carries the card background itself so that
   the line stays within the card instead of running the full width of the page"
  []
  [rn-view {:style {:background-color @grouped-list-card-color
                    :margin-left CARD-SIDE-MARGIN
                    :margin-right CARD-SIDE-MARGIN}}
   [rn-view {:style {:height 1
                     :margin-left ROW-SEPARATOR-INSET
                     :background-color @outline-variant}}]])

(defn row-count-text
  "Renders the right hand side count of a row in the muted secondary color"
  [items-count]
  [rnp-text {:variant "bodyMedium"
             :style {:align-self "center" :color @on-surface-variant}}
   items-count])

(defn row-chevron
  "The disclosure arrow shown at the right end of a row that navigates to another page"
  []
  [rnp-icon-button {:icon const/ICON-CHEVRON-RIGHT
                    :size 20
                    :iconColor @on-surface-variant
                    :style {:align-self "center" :margin 0 :margin-left 2 :margin-right -4}}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;; Section header ;;;;;;;;;;;;;;;;;;;;;;;;

(defn section-header
  "A quiet grouped list section header.

   Keys of the passed map
     label             the already translated section name
     items-count       count shown in brackets after the label; not shown when nil
     collapsed?        whether the section's rows are currently hidden
     on-toggle         called when the label or the chevron is pressed. A section that cannot
                       be collapsed leaves this out and then carries no chevron
     trailing-icon     optional icon shown at the right end of the header
     on-trailing-press called when that icon is pressed
  "
  [{:keys [label items-count collapsed? on-toggle trailing-icon on-trailing-press]}]
  [rn-view {:style {:flexDirection "row"
                    :align-items "center"
                    :margin-left (+ CARD-SIDE-MARGIN 4)
                    :margin-right CARD-SIDE-MARGIN
                    :margin-top 4
                    :min-height 36}}

   [rnp-touchable-ripple {:style {:flex 1 :borderRadius 6}
                          :onPress on-toggle}
    [rn-view {:style {:flexDirection "row" :align-items "center"
                      :padding-top 6 :padding-bottom 6}}
     [rnp-text {:variant "labelLarge"
                :style {:textTransform "uppercase"
                        :letterSpacing 0.6
                        :color @on-surface-variant}}
      (if (nil? items-count) label (str label " (" items-count ")"))]

     (when on-toggle
       [rnp-icon-button {:icon (if collapsed? const/ICON-CHEVRON-DOWN const/ICON-CHEVRON-UP)
                         :size 18
                         :iconColor @on-surface-variant
                         :style {:margin 0 :margin-left 2}
                         :onPress on-toggle}])]]

   (when trailing-icon
     [rnp-icon-button {:icon trailing-icon
                       :size 20
                       :iconColor @on-surface-variant
                       :style {:margin 0}
                       :onPress on-trailing-press}])])

(defn section-spacer
  "Stands in for a section header where a card needs the same gap above it but has no
   header of its own - the search results are one such card"
  []
  [rn-view {:style {:height 10}}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Searching ;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn inline-search-bar
  "The search bar a page carries at its top. What is typed here searches the whole database,
   not just the rows of the page it sits on, and the page shows the matches in place of its
   own content while there is a term"
  [{:keys [term on-change]}]
  [rnp-searchbar (merge no-assist-text-props
                        {:style {:margin-left CARD-SIDE-MARGIN
                                 :margin-right CARD-SIDE-MARGIN
                                 :margin-top 8
                                 :margin-bottom 4
                                 :borderWidth 0
                                 ;; The bar is one more thing sitting on the page ground, so it
                                 ;; carries the color the cards and the bottom bar carry. Paper
                                 ;; otherwise gives it the level3 elevation color, which is a
                                 ;; primary tinted off white that matches nothing else here
                                 :background-color @grouped-list-card-color}
                         :placeholder (lstr-l 'search)
                         ;; A nil term would turn the input into an uncontrolled one
                         :value (if (nil? term) "" term)
                         :onChangeText on-change
                         :onClearIconPress (fn [] (on-change ""))})])

(defn searching?
  "True while the user has typed something into a page's search bar"
  [term]
  (not (str/blank? term)))

(defn no-match-view
  "Shown in place of the list when there is nothing to list - a search that matched nothing,
   or a page with no rows of its own.

   It takes the space the list would have taken, so that whatever the page puts below it -
   its bottom bar - stays where it belongs rather than riding up under this message"
  []
  [rn-view {:style {:flex 1 :margin-top 40 :align-items "center"}}
   [rnp-text {:variant "titleMedium" :style {:color @on-surface-variant}}
    (lstr-l 'noResult)]])

;;;;;;;;;;;;;;;;;;;;;;;;;;; Counts as text ;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- count-text [items-count singular-key plural-key]
  (if (= 1 items-count)
    (lstr-l singular-key)
    (lstr-l plural-key {:item-count items-count})))

(defn items-count-description
  "The description line of a row that stands for a container of other items -
   '9 entries' or, when the container also holds groups, '2 groups, 9 entries'"
  [entries-count groups-count]
  (let [entries-count (if (nil? entries-count) 0 entries-count)
        groups-count (if (nil? groups-count) 0 groups-count)
        entries-txt (count-text entries-count 'oneEntry 'nEntries)]
    (if (pos? groups-count)
      (str (count-text groups-count 'oneGroup 'nGroups) ", " entries-txt)
      entries-txt)))

;;;;;;;;;;;;;;;;;;;;;;;;;; Collapsed sections ;;;;;;;;;;;;;;;;;;;;;;

(defn collapsed?
  "The arg 'collapsed-ref' is a reagent atom holding the set of collapsed section keys"
  [collapsed-ref section-key]
  (contains? @collapsed-ref section-key))

(defn toggle-collapsed [collapsed-ref section-key]
  (swap! collapsed-ref (fn [keys-set]
                         (if (contains? keys-set section-key)
                           (disj keys-set section-key)
                           (conj keys-set section-key)))))

(defn section-data
  "Rows a section should hand to the section list - none while the section is collapsed"
  [collapsed-ref section-key data]
  (if (collapsed? collapsed-ref section-key) [] (vec data)))

(defn row-position
  "Where a row sits within its card, used to decide which of its corners are rounded"
  [index section-item-count]
  {:first-row? (= index 0)
   :last-row? (= index (dec section-item-count))})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Page ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn page-style
  "Style of the view holding a grouped list. Its ground is a shade apart from the cards"
  []
  {:flex 1 :background-color @rnc/grouped-list-ground-color})
