(ns
 onekeepass.mobile.rn-components
  (:require-macros [onekeepass.mobile.okp-macros
                    :refer  [declare-comp-classes]])
  (:require
   ;; We need this as the default Hermes engine in RN does not support Intl API fully
   ;; If we use the previous JSC engine, this polyfill is not be needed
   ;; This needs to be the first require to ensure polyfill is loaded first
   ;; before any other intl usage. If calling here does not work, need to call this in the core.cljs itself
   ;; See https://formatjs.github.io/docs/polyfills/intl-pluralrules#react-native
   ["@formatjs/intl-pluralrules/polyfill-force" :as polyfill-force]
   ["@date-io/date-fns" :as DateAdapter]
   ["@react-native-community/slider" :as rnc-slider]
   ["react-native-circular-progress" :as rn-circular-progress]
   ["react-native-gesture-handler" :as gh]
   ["react-native-modal-selector" :as rnms]
   ["react-native-paper" :as rnp]
   ["react-native-paper-dates" :as paper-dates]
   ["react-native-safe-area-context" :as sa-context]
   ["@react-native-vector-icons/material-design-icons"]
   #_["react-native-vector-icons" :as vec-icons]
   ["react-native-vision-camera" :as rn-vision-camera]
   ;; Local js components. 
   ;; Note the use of path relative to cljs-main-app-src/src/gen
   ;; which is included in the :source-paths in shadow-cljs.edn
   ;; These are transpiled by babel(one time) from js-components to cljs-main-app-src/gen
   ;; using the command shown (in a comment) in the shadow-cljs.edn file
   ["/components/RNPCustomization" :as rnp-customization]
   ["/components/KeyboardAvoidingDialog" :as kb-dialog]
   ["/components/CustomSafeAreaView" :as cust-safe-area-view]
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
                  ;; RN 0.85 (New Arch): Appearance.setColorScheme's param is non-null
                  ;; ColorSchemeName ('light' | 'dark' | 'unspecified'). "unspecified"
                  ;; means "follow the OS scheme" — the old `nil` now crashes with
                  ;; "Parameter specified as non-null is null ... setColorScheme".
                  (.setColorScheme appearance "unspecified")
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
#_(def rn-safe-area-view (r/adapt-react-class (.-SafeAreaView ^js/SASafeAreaView sa-context)))

(def rn-safe-area-view (r/adapt-react-class (.-RNPSafeAreaView cust-safe-area-view)))

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
                       ;;SafeAreaView
                       FlatList
                       Image
                       KeyboardAvoidingView
                       Pressable
                       TouchableWithoutFeedback
                       TouchableHighlight
                       TouchableOpacity
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
                       Icon
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
                       ;;Searchbar
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

;; react-native-paper-dates: 'DatePickerInput' is an inline Paper TextInput with a calendar
;; icon that opens a Material date-picker modal. Used for entry-type Date fields.
;; The translation must be registered once (before the component first renders). We register
;; the English text under both 'en' and 'en-CA': the picker derives its input mask from the
;; locale via Intl, and 'en-CA' yields the ISO 'YYYY-MM-DD' order while keeping English labels.
(.registerTranslation ^js paper-dates "en" (.-en ^js paper-dates))
(.registerTranslation ^js paper-dates "en-CA" (.-en ^js paper-dates))
(def date-picker-input (r/adapt-react-class (.-DatePickerInput ^js paper-dates)))

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
#_(def cust-dialog (r/adapt-react-class (.-default ^js/CustD (js/require "../js/components/KeyboardAvoidingDialog.js"))))

(def cust-dialog (r/adapt-react-class (.-default kb-dialog)))


;; All react native paper customizations
#_(def rnp-customization ^js/RNPC (js/require "../js/components/RNPCustomization.js"))

;; After 0.81.5 upgrade, menu popup works onetime and then does not remain open when second time it is opened
;; Luckily the workaround disscused here https://github.com/callstack/react-native-paper/issues/4807
;; worked. Added :key (str show) to the rnp-menu component in all places where it is used
(def rnp-menu (r/adapt-react-class (.-RNPMenu rnp-customization)))
(def rnp-text-input (r/adapt-react-class (.-RNPTextInput rnp-customization)))
(def rnp-searchbar (r/adapt-react-class (.-RNPSearchbar rnp-customization)))
(def cust-rnp-divider (r/adapt-react-class (.-RNPDivider rnp-customization)))

;; Keyboard assistance props used with 'rnp-text-input'
;; The RN TextInput defaults are :autoCapitalize "sentences" and :autoCorrect true. For a password
;; manager those defaults are wrong for most fields - the keyboard opens in caps and the predictive
;; text replaces what is typed. These maps are merged into the props of a text input to turn that off
;; Merged first so that a call site can still override any individual prop

;; For secrets, user names, urls, host names, tags, field names - values where neither
;; capitalization nor any correction is wanted
(def no-assist-text-props {:autoCapitalize "none"
                           :autoCorrect false
                           :autoComplete "off"
                           ;; spellCheck and textContentType are iOS only
                           :spellCheck false
                           :textContentType "none"})

;; For free text - notes, descriptions, group and entry names - where the sentence capitalization
;; of the keyboard is still useful but the autocorrect replacing the typed words is not
(def no-autocorrect-text-props {:autoCorrect false
                                :autoComplete "off"
                                :spellCheck false
                                :textContentType "none"})

;; About the iOS "Passwords" key that comes up above the keyboard in the entry form
;;
;; iOS offers its own credential autofill for any field it decides belongs to a login form. What
;; was found by testing on an iOS 18.2 simulator:
;;
;; - the trigger is a field with 'secureTextEntry' being present in the form. Making the password
;;   field visible with the eye icon makes the key go away for the user name field as well. It is
;;   re-evaluated only when a field gets the focus again, so the change shows up after moving to
;;   another field and coming back
;; - :textContentType does NOT control it. Neither "none" (which RN maps to an empty string) nor an
;;   explicit non credential type like "nickname" makes any difference. iOS seems to force the
;;   credential handling when secureTextEntry is set, whatever the content type says
;;
;; So there is no text input prop that turns this off. It can only be avoided by not using
;; secureTextEntry, which would mean masking the value ourselves

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

;; Muted color meant for secondary text - a row's description line, a section label
(def on-surface-variant (r/atom nil))

;; Colors for the grouped lists used in the entry category and the entry list pages
;; There the rows of a section are drawn on a rounded card that has to sit a shade apart from
;; the page behind it. In the light theme the card is the lighter of the two; in the dark
;; theme it is the other way round - the page ground is the darker one
(def grouped-list-ground-color (r/atom nil))
(def grouped-list-card-color (r/atom nil))

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

;; A list row keeps this background while its long press menu is open so that it is clear
;; which row the menu is going to act on. The row goes back to its normal ground when the
;; menu is dismissed or one of its actions is selected
(defn row-highlight-style [highlighted?]
  (when highlighted? {:backgroundColor @secondary-container-color}))

;; There is no generic "monospace" font family in iOS and we need to use one of the
;; fixed width fonts that ship with the platform. Android has the generic "monospace"
;; This is used to show random looking values (generated password, password field in
;; read mode) so that similar looking characters are easier to distinguish
(def monospace-font-family (if (is-iOS) "Menlo" "monospace"))

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
    (reset! on-surface-variant (.-onSurfaceVariant colors))

    (let [light-theme? (not= const/DARK-THEME theme-name)]
      (reset! grouped-list-ground-color (if light-theme? (.-inverseOnSurface colors) (.-background colors)))
      (reset! grouped-list-card-color (if light-theme? (.-background colors) (.-inverseOnSurface colors))))


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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Animated ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; An animation of a transform or of opacity can be handed to the native thread, where it
;; then runs without javascript doing anything per frame. Used by the entry list's token
;; bar, which would otherwise cost work every second for every row on the page

(def rn-animated ^js/RNAnimated rn/Animated)

(def rn-easing ^js/RNEasing rn/Easing)

(def rn-animated-view (r/adapt-react-class (.-View ^js/RNAnimated rn/Animated)))

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