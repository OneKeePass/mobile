(ns onekeepass.mobile.biometrics-settings
  (:require
   [onekeepass.mobile.constants :as const]
   [onekeepass.mobile.rn-components :as rnc :refer [inverse-onsurface-color
                                                    page-background-color
                                                    rn-safe-area-view
                                                    rn-section-list
                                                    rn-view
                                                    rnp-divider
                                                    rnp-switch
                                                    rnp-text]]
   [onekeepass.mobile.translation :refer [lstr-l lstr-mt]]
   [reagent.core :as r]))