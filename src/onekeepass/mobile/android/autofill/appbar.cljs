(ns onekeepass.mobile.android.autofill.appbar
  (:require [onekeepass.mobile.android.autofill.entry-list :as el]
            [onekeepass.mobile.android.autofill.entry-form :as ef]
            [onekeepass.mobile.android.autofill.events.common :as cmn-events :refer [ENTRY_FORM_PAGE_ID
                                                                                     ENTRY_LIST_PAGE_ID
                                                                                     HOME_PAGE_ID
                                                                                     to-previous-page]]
            [onekeepass.mobile.android.autofill.start-page :refer [open-page-content]]
            [onekeepass.mobile.rn-components :as rnc :refer [background-color
                                                             primary-color
                                                             rnp-appbar-back-action
                                                             rnp-appbar-content
                                                             rnp-appbar-header]]))


(defn positioned-title [& {:keys [title page style titleStyle]}]
  [rnp-appbar-content {:style (merge {:marginLeft 0  :position "absolute", :left 0, :right 0, :zIndex -1} style)
                       :color @background-color
                       :titleStyle (merge {:align-self "center"} titleStyle)
                       :title title}])

(defn appbar-body-content  [{:keys [page]}]
  #_(println "Page id is " page)
  (cond

    (= page HOME_PAGE_ID)
    [open-page-content]
    
    (= page ENTRY_LIST_PAGE_ID)
    [el/content]
    
    (= page ENTRY_FORM_PAGE_ID)
    [ef/content]
    
    :else
    [open-page-content]
    
    #_[rnc/rnp-text (str "Unknown page id " page)]))

(defn appbar-header-content
  "The page body content based on the page info set"
  [{:keys [page title]}]

  [rnp-appbar-header {:style {:backgroundColor @primary-color}}

   [rnp-appbar-back-action {:style {}
                            :color @background-color
                            :onPress (fn [] (to-previous-page))}]


   [positioned-title :title title]])


(defn appbar-main-content []
  [rnc/rn-view {:style {:flex 1}}
   [appbar-header-content @(cmn-events/page-info) #_{:page :home :title "Home"}]
   [appbar-body-content   @(cmn-events/page-info) #_{:page :home :title "Home"}]])


#_(defn appbar-main-content
  "An App bar has both header and the body combined"
  []
  (let [handler-fns-m {:onStartShouldSetPanResponderCapture (fn []
                                                              
                                                              false)}

        pan-handlers-m (-> (rnc/create-pan-responder handler-fns-m)
                           (js->clj :keywordize-keys true) :panHandlers)]
    [rnc/rn-view (merge {:style {:flex 1}} pan-handlers-m)
     [appbar-header-content {:page :home :title "Home"}]
     [appbar-body-content {:page :home :title "Home"}]]))