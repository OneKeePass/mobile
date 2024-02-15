(ns onekeepass.mobile.about
  (:require [reagent.core :as r]
            [onekeepass.mobile.rn-components :as rnc :refer [rn-safe-area-view
                                                             rn-view
                                                             rnp-text]]
            [onekeepass.mobile.events.common :as cmn-events]))

;; The version should match the one used in
;; iOS: 
;; See 'mobile/ios/OneKeePassMobile.xcodeproj'  (General -> Identity -> Version)
;; Android:
;; See 'mobile/android/app/build.gradle'  (versionName)
(def app-version "v0.11.0")

(defn link-text [url & opts]
  [rnp-text {:style (merge {:margin-left 5
                            :textDecorationLine "underline"
                            :color @rnc/primary-color} opts)
             :onPress #(cmn-events/open-https-url url)}
   url])

(defn privacy-link []
  [link-text "https://onekeepass.github.io/privacy"])

(defn privacy-policy-content []
  [rn-safe-area-view {:style {:flex 1 :background-color @rnc/page-background-color}}
   [rn-view {:style {:margin-top 30
                     :align-items "center"
                     :background-color @rnc/inverse-onsurface-color}}
    [rnp-text  {:style {:padding 5
                        :textTransform "uppercase"}
                :variant "titleSmall"} "Privacy Policy"]]

   [rn-view {:style {:margin-top 10 :justify-content "center" :align-items "center"}}
    [rnp-text "The application does not collect any of the personal identifiable information. Nothing is collected or shared with any external party"]

    [rn-view {:style {:margin-top 10 :padding 5 :justify-content "center" :align-items "center"}}
     [rn-view {:style {:margin-top 5} :flexDirection "row" :flexWrap "wrap"}
      [rnp-text {:style {:margin-left 5}}
       "Please visit"]

      [privacy-link]

      [rnp-text {:style {:margin-left 5}}
       "to see for details"]]]]])

(defn about-content []
  [rn-safe-area-view {:style {:flex 1 :background-color @rnc/page-background-color}}
   [rn-view {:style {:margin-top 50
                     :justify-content "center" :align-items "center"}}
    [rnp-text  {:style {:color @rnc/tertiary-color}
                :variant "headlineLarge"} "OneKeePass"]
    [rnp-text {:style {:color @rnc/primary-color}
               :variant "headlineSmall"} app-version]]

   [rn-view {:style {:margin-top 50
                     :align-items "center"
                     :background-color @rnc/inverse-onsurface-color}}
    [rnp-text  {:style {:padding 5
                        :textTransform "uppercase"}
                :variant "titleSmall"} "Support"]]

   [rn-view {:style {:margin-top 10 :justify-content "center" :align-items "center"}}
    [link-text "https://github.com/OneKeePass/mobile/issues"]
    [link-text "https://onekeepass.github.io/"]
    [rnp-text "onekeepass@gmail.com"]]

   [rn-view {:style {:margin-top 30
                     :align-items "center"
                     :background-color @rnc/inverse-onsurface-color}}
    [rnp-text  {:style {:padding 5
                        :textTransform "uppercase"}
                :variant "titleSmall"} "Privacy Policy"]]

   [rn-view {:style {:margin-top 10 :justify-content "center" :align-items "center"}}
    [rnp-text "The application does not collect any of the personal identifiable information. Nothing is collected or shared with any external party"]

    [rn-view {:style {:margin-top 10 :padding 5 :justify-content "center" :align-items "center"}}
     [rn-view {:style {:margin-top 5} :flexDirection "row" :flexWrap "wrap"}
      [rnp-text {:style {:margin-left 5}}
       "Please visit"]

      [privacy-link]

      [rnp-text {:style {:margin-left 5}}
       "to see for details"]]]]])