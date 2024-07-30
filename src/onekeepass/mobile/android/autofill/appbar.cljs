(ns onekeepass.mobile.android.autofill.appbar
  "Only the Android Autofill specific appbar components"
  (:require [onekeepass.mobile.android.autofill.entry-list :as el]
            [onekeepass.mobile.android.autofill.entry-form :as ef]
            [onekeepass.mobile.android.autofill.events.common :as cmn-events :refer [ENTRY_FORM_PAGE_ID
                                                                                     ENTRY_LIST_PAGE_ID
                                                                                     HOME_PAGE_ID
                                                                                     to-previous-page]]
            [onekeepass.mobile.android.autofill.start-page :refer [open-page-content]]
            [onekeepass.mobile.rn-components :as rnc :refer [background-color
                                                             primary-color
                                                             rnp-appbar-header
                                                             rnp-appbar-back-action
                                                             rnp-appbar-content]]))

(defn- back-action
  "Called when user presses the hardware or os provided back button 
   This is somewhat similar to the app back action - see inside appbar-header-content
   Returns 
      true - the app handles the back action and the event will not be bubbled up
      false - the system's default back action to be executed
   "
  [{:keys [page]}]
  (println "back-action is called for page " page)
  (if
   (= page HOME_PAGE_ID)
    false
    (do
      (to-previous-page)
      true)))

;; holds additional copy of the current page for android hardware back action handling use
(def ^:private current-page-info (atom nil))

(defn hardware-back-pressed [] 
  (back-action @current-page-info))

;; TODO: Need to use lstr-pt as done in main app
(defn positioned-title [& {:keys [title _page style titleStyle]}]
  [rnp-appbar-content 
   {:style (merge 
            {:marginLeft 0  :position "absolute", :left 0, :right 0, :zIndex -1} 
            style)
    :color @background-color
    :titleStyle (merge {:align-self "center"} titleStyle)
    :title title}])

(defn appbar-body-content  [{:keys [page]}]
  (cond
    (= page HOME_PAGE_ID)
    [open-page-content]

    (= page ENTRY_LIST_PAGE_ID)
    [el/content]

    (= page ENTRY_FORM_PAGE_ID)
    [ef/content]

    :else
    [open-page-content]))

(defn appbar-header-content
  "The page body content based on the page info set"
  [{:keys [page title] :as page-info}]
  
  (reset! current-page-info page-info)

  [rnp-appbar-header {:style {:backgroundColor @primary-color}}

   (when-not (= page HOME_PAGE_ID)
     [rnp-appbar-back-action {:style {}
                              :color @background-color
                              :onPress (fn [] (to-previous-page))}])

   [positioned-title :title title]])


(defn appbar-main-content []
  [rnc/rn-view {:style {:flex 1}}
   [appbar-header-content @(cmn-events/page-info)]
   [appbar-body-content   @(cmn-events/page-info)]])