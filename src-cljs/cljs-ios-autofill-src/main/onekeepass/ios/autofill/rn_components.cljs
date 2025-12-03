(ns
 onekeepass.ios.autofill.rn-components
  (:require-macros [onekeepass.ios.autofill.okp-macros
                    :refer  [declare-comp-classes]])
  (:require
   ;; We need this as the default Hermes engine in RN does not support Intl API fully
   ;; If we use the previous JSC engine, this polyfill is not be needed
   ;; This needs to be the first require to ensure polyfill is loaded first
   ;; before any other intl usage. If calling here does not work, need to call this in the core.cljs itself
   ;; See https://formatjs.github.io/docs/polyfills/intl-pluralrules#react-native
   
   ["@formatjs/intl-pluralrules/polyfill-force" :as polyfill-force]
   ["@react-native-community/slider" :as rnc-slider]
   ["react-native-circular-progress" :as rn-circular-progress]
   ["react-native-gesture-handler" :as gh]
   ["react-native-modal-selector" :as rnms]
   ["react-native-paper" :as rnp]
   ["react-native-safe-area-context" :as sa-context]
   ["react-native-vector-icons" :as vec-icons]

   ;; Local js components
   ["/components/RNPCustomization" :as rnp-customization]
   ["/components/KeyboardAvoidingDialog" :as kb-dialog]
   ["/components/CustomSafeAreaView" :as cust-safe-area-view]
   #_[onekeepass.mobile.background :refer [is-iOS]]
   [onekeepass.ios.autofill.constants :refer [DEFAULT-SYSTEM-THEME]]

   [react]
   [react-native :as rn]
   [reagent.core :as r]))

(set! *warn-on-infer* true)

;;;;;; TO_BE_REMOVED: Krell is no more used to build the project
;; When cljs files are compiled using Krell during development, we can see 
;; ./target/npm_deps.js and ./target/krell_npm_deps.js generated
;; All the require calls above of NPM packages will have an entry in npm_deps.js
;; All (js/require "../js/.....") calls will result an entry in krell_npm_deps.js
;;;;;;;;;;;;;

;; Also this defined again in background as rn-components is not refered in background module to avoid circular references
(def rn-native-linking ^js/RNLinking rn/Linking)

;; See https://react.dev/reference/react/useEffect
;; useEffect is called after a component is rendered
;; it runs both after the first render and after every update
(def react-use-effect (.-useEffect ^js/React react))

(def use-color-scheme rn/useColorScheme)

(def appearance ^js/RNAppearance rn/Appearance)

(defn theme-to-use [prefered-theme]
  (let [theme (if (= prefered-theme DEFAULT-SYSTEM-THEME)
                (do
                  (.setColorScheme appearance nil)
                  (.getColorScheme appearance))
                prefered-theme)]
    theme))

;; At this moment, these are not used
#_(def react-use-state (.-useState ^js/React react))
#_(def react-use-ref (.-useRef ^js/React react))
#_(def react-forward-ref (.-forwardRef ^js/React react))
#_(def window (-> rn/Dimensions ^js/Dim (.get "window")))

(def use-safe-area-insets (.-useSafeAreaInsets ^js/SAInsets sa-context))

;; As per https://reactnative.dev/blog/2025/08/12/react-native-0.81, we need to deprecate the built-in SafeAreaView
;; with this one from 'react-native-safe-area-context' package
;; TODO: need to replace rn-safe-area-view with rnsa-safe-area-view in all places
(def rn-safe-area-view (r/adapt-react-class (.-RNPSafeAreaView cust-safe-area-view)))

(def rn-keyboard ^js/RNKeyboard rn/Keyboard)

(def rn-back-handler ^js/RNBackHandler rn/BackHandler)

;; React native components
(declare-comp-classes [ActivityIndicator
                       Button
                       ;; See comments above about SafeAreaView
                       ;; SafeAreaView
                       FlatList
                       KeyboardAvoidingView
                       Pressable
                       TouchableWithoutFeedback
                       TouchableHighlight
                       ScrollView
                       SectionList
                       View
                       Modal
                       StatusBar
                       TextInput
                       Text]
                      "rn-" "rn/")

;; All React Native Paper components
;; See RNPCustomization.js for the customization of some of the React Native Paper components
(declare-comp-classes [Button
                       BottomNavigation
                       BottomNavigation.Bar
                       Checkbox
                       Checkbox.Item
                       Chip
                       Divider
                       Dialog
                       Dialog.Title
                       Dialog.Icon
                       Dialog.Content
                       Dialog.Actions
                       FAB
                       HelperText
                       IconButton
                       List.Section
                       List.Item
                       List.Icon
                       List.Subheader
                       ;;Menu 
                       Menu.Item
                       Modal
                       Portal
                       Provider
                       Paragraph
                       ProgressBar
                       Snackbar
                       Searchbar
                       Switch
                       ;;TextInput
                       TextInput.Icon
                       Text
                       TouchableRipple] "rnp-" "rnp/")

#_(def rnp-appbar (r/adapt-react-class rnp/Appbar))
(def rnp-appbar-header (r/adapt-react-class rnp/Appbar.Header))
(def rnp-appbar-content (r/adapt-react-class rnp/Appbar.Content))
(def rnp-appbar-action (r/adapt-react-class rnp/Appbar.Action))
(def rnp-appbar-back-action (r/adapt-react-class rnp/Appbar.BackAction))

(def rnms-modal-selector (r/adapt-react-class (.-default ^js/rnms rnms)))

;; Slider component from react native community
(def rnp-slider (r/adapt-react-class (.-default ^js/RncSlider rnc-slider)))

(declare-comp-classes [GestureHandlerRootView] "gh-" "gh/")

;; In case of iOS, any dialog with text input will be hidden partially by the Virtual Keyboard popup 
;; Could not make the Dialog work with KeyboardAvoidingView as generally used for other cases
;; It seems no support is vailable for this in react native paper 
;; Finally the solution is based on https://github.com/callstack/react-native-paper/issues/2172
;; In Android, the overlapping of Keyboard over Dialog does not happen. So we can continue using the Dialog
#_(def cust-dialog
    (r/adapt-react-class (.-default ^js/CustD (js/require "../js/components/KeyboardAvoidingDialog.js"))))

(def cust-dialog (r/adapt-react-class (.-default kb-dialog)))

;; All react native paper customizations
#_(def rnp-customization ^js/RNPC (js/require "../js/components/RNPCustomization.js"))

(def rnp-menu (r/adapt-react-class (.-RNPMenu rnp-customization)))
(def rnp-text-input (r/adapt-react-class (.-RNPTextInput rnp-customization)))
(def cust-rnp-divider (r/adapt-react-class (.-RNPDivider rnp-customization)))

;;;;;;
(def dark-theme (.-custDarkTheme ^js/CustomDarkTheme rnp-customization))
(def light-theme (.-custLightTheme ^js/CustomLightTheme rnp-customization))

;; In case we want to use theme in any of the reagent component, this can be used
(def current-theme (r/atom nil))

;;;; Some standard colors based on the theme selected
;; blue (in light theme), light blue (in dark theme)
(def primary-color (r/atom nil))

;; white (in light theme), blue (in dark theme)
;; A color that's clearly legible when drawn on primary
;; See https://api.flutter.dev/flutter/material/ColorScheme/onPrimary.html
(def on-primary-color (r/atom nil))

;; A color used for elements needing less emphasis than primary
;; light blue (in light theme), dark blue in (dark mode)
(def primary-container-color (r/atom nil))

(def secondary-color (r/atom nil))
(def on-secondary-color (r/atom nil))
(def secondary-container-color (r/atom nil))

;; white (in light theme), black (in dark theme)
(def background-color (r/atom nil))
(def on-background-color (r/atom nil))

;; slight reddish (in light theme), 
(def tertiary-color (r/atom nil))
(def outline-color (r/atom nil))
;; very dim on white 
(def inverse-onsurface-color (r/atom nil))

(def error-color (r/atom nil))
(def on-error-container (r/atom nil))

(def surface-variant (r/atom nil))
(def outline-variant (r/atom nil))

(def custom-color0 (r/atom nil))

(def circular-progress-color custom-color0 #_(r/atom "#F8BD2A"))

;; Component specific colors
;; TODO: Need to use only these colors instead of refering the above standard colors
(def icon-color primary-color)
(def appbar-text-color on-primary-color)
(def message-modal-background-color secondary-container-color)
(def modal-selector-colors {:background-color secondary-container-color
                            :selected-text-color primary-color})
(def page-background-color background-color)

(def divider-color-1 outline-color)

(defn reset-colors
  "Called to set all colors that are used in many components.
   The arg theme-name is passed when main root component is formed - see core.cljs
   "
  [theme-name]
  (let [^js/CurrentTheme theme (if (= "dark" theme-name) dark-theme light-theme)
        ^js/CurrentThemeColors colors (.-colors theme)]
    (reset! current-theme theme)
    (reset! primary-color (.-primary colors))
    (reset! on-primary-color (.-onPrimary colors))
    (reset! primary-container-color (.-primaryContainer colors))

    (reset! secondary-color (.-secondary colors))
    (reset! on-secondary-color (.-onSecondary colors))
    (reset! secondary-container-color (.-secondaryContainer colors))

    (reset! background-color (.-background colors))
    (reset! on-background-color (.-onBackground colors))
    (reset! tertiary-color (.-tertiary colors))
    (reset! outline-color (.-outline colors))
    (reset! error-color (.-error colors))
    (reset! inverse-onsurface-color (.-inverseOnSurface colors))
    (reset! surface-variant (.-surfaceVariant colors))
    (reset! outline-variant (.-outlineVariant colors))
    (reset! on-error-container (.-onErrorContainer colors))

    (reset! custom-color0 (.-custom0 colors))))


;;;;;;;;;;


(def dots-icon-name "dots-horizontal")
(def page-title-text-variant "titleLarge") ;;"titleLarge" "titleMedium"


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; react-native-circular-progress ;;;;;;;;;;;;;;;;;;;;

(def animated-circular-progress (r/adapt-react-class (.-AnimatedCircularProgress ^js/RNCircularProgress rn-circular-progress)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  TO_BE_REMOVED: All example Krell based build time components ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Following are some sample React Native components in Javascript based examples that
;; can be loaded here using Krell's js/require feature and create cljs reagent component and use 
;; like any other reagent components

;; All (js/require "../js/.....") calls will result an entry in krell_npm_deps.js

;; (def icon-test  (r/adapt-react-class (.-default (js/require "../js/components/examples/IconTest.js"))))
;; (def rnp-examples (js/require "../js/components/examples/RNPExamples.js"))
;; (def centerview  (r/adapt-react-class (.-CenterView (js/require "../js/components/examples/RNPExamples.js"))))
;; (def appbar-example  (r/adapt-react-class (.-AppbarExample (js/require "../js/components/examples/RNPExamples.js"))))
;; (def textinput-example  (r/adapt-react-class (.-TextInputExample (js/require "../js/components/examples/RNPExamples.js"))))
;; (def surface-example  (r/adapt-react-class (.-SurfaceExample rnp-examples)))

(comment
  (in-ns 'onekeepass.ios.autofill.rn-components))