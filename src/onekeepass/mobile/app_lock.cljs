(ns onekeepass.mobile.app-lock
  (:require
   [onekeepass.mobile.background :refer [is-iOS]]
   [onekeepass.mobile.rn-components :as rnc :refer [custom-color0
                                                    page-title-text-variant
                                                    appbar-text-color
                                                    page-background-color
                                                    rnp-text-input
                                                    rn-scroll-view
                                                    rn-keyboard-avoiding-view
                                                    rn-view
                                                    rnp-button
                                                    rnp-divider
                                                    rnp-slider
                                                    rnp-switch
                                                    rnp-segmented-buttons
                                                    rnp-text
                                                    rnp-icon-button]]
   [onekeepass.mobile.common-components :as cc :refer [select-field get-form-style]]
   [onekeepass.mobile.utils :as u :refer [contains-val?]]
   [onekeepass.mobile.events.common :as cmn-events] 
   [onekeepass.mobile.translation :refer [lstr-pt lstr-bl lstr-l lstr-cv]]))


