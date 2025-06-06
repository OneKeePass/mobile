(ns
 onekeepass.mobile.rn-components
  (:require-macros [onekeepass.mobile.okp-macros
                    :refer  [declare-comp-classes]])
  (:require
   ["@date-io/date-fns" :as DateAdapter]
   ["@react-native-community/slider" :as rnc-slider]
   ["react-native-circular-progress" :as rn-circular-progress]
   ["react-native-gesture-handler" :as gh]
   ["react-native-modal-selector" :as rnms]
   ["react-native-paper" :as rnp]
   ["react-native-safe-area-context" :as sa-context]
   ["react-native-vector-icons" :as vec-icons]
   ["react-native-vision-camera" :as rn-vision-camera]
   [onekeepass.mobile.background :refer [get-constants is-Android is-iOS]]
   [onekeepass.mobile.constants :as const :refer [DEFAULT-SYSTEM-THEME]]
   [react]
   [react-native :as rn]
   [reagent.core :as r]))

(set! *warn-on-infer* true)

;; When cljs files are compiled using Krell during development, we can see 
;; ./target/npm_deps.js and ./target/krell_npm_deps.js generated
;; All the require calls above of NPM packages will have an entry in npm_deps.js
;; All (js/require "../js/.....") calls will result an entry in krell_npm_deps.js

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

;; https://github.com/dmtrKovalenko/date-io
;;DateAdapter is #object[DateFnsUtils] 
;; (Object.keys date-fns-utils) will give all available fuctions from this util
;; In desktop version we need to use (.-default DateAdapter) and then use that to get utils. 
;; See 'onekeepass.frontend.mui-components'
(def ^js/DateAdapter.Utils date-fns-utils (DateAdapter.))

(def rn-keyboard ^js/RNKeyboard rn/Keyboard)

(def rn-back-handler ^js/RNBackHandler rn/BackHandler)

;; React native components
(declare-comp-classes [ActivityIndicator
                       Button
                       SafeAreaView
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
                       SegmentedButtons
                       Surface
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
;; See https://github.com/callstack/react-native-slider for all props
(def rnp-slider (r/adapt-react-class (.-default ^js/RncSlider rnc-slider)))

(declare-comp-classes [GestureHandlerRootView] "gh-" "gh/")

;; In case of iOS, any dialog with text input will be hidden partially by the Virtual Keyboard popup 
;; Could not make the Dialog work with KeyboardAvoidingView as generally used for other cases
;; It seems no support is available for this in react native paper 
;; Finally the solution is based on https://github.com/callstack/react-native-paper/issues/2172

;;;;;;;;;;  This no more works for android - See comments below  ;;;;;;;;;;;;
;; In Android, the overlapping of Keyboard over Dialog does not happen. We need to do 
;; add android:windowSoftInputMode="adjustResize" in ' AndroidManifest.xml' for this

#_(def cust-dialog
    (if (is-iOS)
      (r/adapt-react-class (.-default ^js/CustD (js/require "../js/components/KeyboardAvoidingDialog.js")))
      rnp-dialog))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; After Android 'compileSdkVersion = 35 introduction
;; Also see comments in js/components/KeyboardAvoidingDialog.js
(def cust-dialog (r/adapt-react-class (.-default ^js/CustD (js/require "../js/components/KeyboardAvoidingDialog.js"))))

;; All react native paper customizations
(def rnp-customization ^js/RNPC (js/require "../js/components/RNPCustomization.js"))

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
(def on-error-container-color (r/atom nil))
(def error-container-color (r/atom nil))

(def surface-variant (r/atom nil))
(def outline-variant (r/atom nil))

(def custom-color0 (r/atom nil))
(def custom-color0-ontainer (r/atom nil))

(def custom-color1 (r/atom nil))
(def custom-color1-ontainer (r/atom nil))

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
    (reset! error-container-color (.-errorContainer colors))
    (reset! on-error-container-color (.-onErrorContainer colors))

    (reset! inverse-onsurface-color (.-inverseOnSurface colors))
    (reset! surface-variant (.-surfaceVariant colors))
    (reset! outline-variant (.-outlineVariant colors))


    (reset! custom-color0 (.-custom0 colors))
    (reset! custom-color0-ontainer (.-custom0Container colors))

    (reset! custom-color1 (.-custom1 colors))
    (reset! custom-color1-ontainer (.-custom1Container colors))))

(defn is-light-theme? []
  (= const/LIGHT-THEME @current-theme))


(def ^:private insets (r/atom nil))

;; Called from a functional component found inside 'rn-safe-area-view'
;; It makes use of calling the useSafeAreaInsets hook 
(defn set-insets [insets-val]
  (reset! insets (js->clj insets-val :keywordize-keys true)))

(defn- get-insets []
  @insets)

;; Inset bottom value is used maily in android and it is 0 for iOS
(defn get-inset-bottom []
  (if (is-Android)
    (let [{:keys [bottom]} (get-insets)
          bottom (if (nil? bottom) 0 bottom)
          api-ver (.-AndroidSdkApi (get-constants))]
      (if (<= api-ver 31)
        bottom
        0))
    0))

;;;;;;;;;;

(def dots-icon-name (if (is-iOS) "dots-horizontal" "dots-vertical"))
(def page-title-text-variant "titleLarge") ;;"titleLarge" "titleMedium"

;;;;;;;;; i18n ;;;;;;;;;;;;;;;;;;;
;; This js/require loads the exported function for the i18n initializations routine 
#_(def i18n-support ^js/I18NSupport (js/require "../js/localization/i18n.js"))

#_(def init-i18n (.-initI18N i18n-support))

;; loads the i18n initializations routine. This needs to be called before the set-translator hook
;; call in any react componnent
;; This loads all translation files 
;; TODO: Figure out how to load only the relavant translation
;; TODO: Need to add getting the language code from the backend - from the exported constants in 'okp-db-service' (yet to be added)

#_(defn setup-i18n []
    (let [device-language (.-Language ^js/OkpDbService (.-OkpDbService rn/NativeModules))
          ;; device-language may be 'en' or 'es-US' ...
          device-language (-> device-language (str/split #"-") first)]
      ;; (println "Device language .." device-language)
      (init-i18n device-language)))

;; IMPORTANT: Needs to be called before set-translator in any component
#_(setup-i18n)

#_(def ^:private translator (atom nil)) ;; (Object.keys  @translator) => #js ["0" "1" "2" "t" "i18n" "ready"]

#_(defn set-translator
    " Needs to be called as hook in a functional react/reagent component"
    []
    ;; (println "set-translator is called")
    (reset! translator (ri18n/useTranslation)))

#_(defn lstr
    "Called to get the language specific text based 
   if any translation is available for the current active language
   IMPORTANT:
      This fn should be called only within a reagent component
   "
    [s]
    ;; translator should have been set before the first calling of this fn in any component
    (t/lstr s)
    #_((.-t ^js/Translator @translator) s))


;; Additional colors that are specifc to MD3/MD2 
;; (def md2-colors ^js/MD2Color rnp/MD2Colors)
;; (def md3-colors ^js/MD3Colors rnp/MD3Colors)
;; (def neutral50-color ^js/N50Color (.-neutral50 md3-colors))
;; (def neutral-variant60-color ^js/NV60Color (.-neutralVariant60 md3-colors))
;; (def neutral-variant20-color ^js/NV20Color (.-neutralVariant20 md3-colors))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Pan Responder ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-pan-responder
  "Creates a pan responder with the supplied handler functions. Used mainly for session time out
   Arg 'handler-fns-m' is a map with keys matching reponder handler names of a view 
   See for an example https://reactnative.dev/docs/view#onmoveshouldsetrespondercapture
   Returns a PanResponder object with all gesture handlers
   "
  [handler-fns-m]
  ;; handler-fns-m is clojure map and PanResponder expects a js object
  (.create rn/PanResponder (clj->js handler-fns-m)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; react-native-vision-camera - VisionCamera  ;;;;;;;;;;

;; https://react-native-vision-camera.com/docs/api/#usecamerapermission
;; https://github.com/mrousavy/react-native-vision-camera/blob/main/package/src/hooks/useCameraPermission.ts 

(def use-camera-permission (.-useCameraPermission ^js/RNVisionCamera rn-vision-camera))

(def use-camera-device (.-useCameraDevice ^js/RNVisionCamera rn-vision-camera))

(def use-code-scanner (.-useCodeScanner ^js/RNVisionCamera rn-vision-camera))

(def camera (r/adapt-react-class (.-Camera ^js/RNVisionCamera rn-vision-camera)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; react-native-circular-progress ;;;;;;;;;;;;;;;;;;;;

(def animated-circular-progress (r/adapt-react-class (.-AnimatedCircularProgress ^js/RNCircularProgress rn-circular-progress)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  All example components ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
  (in-ns 'onekeepass.mobile.rn-components))