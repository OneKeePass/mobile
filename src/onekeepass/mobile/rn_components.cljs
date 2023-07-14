(ns
 onekeepass.mobile.rn-components
  (:require-macros [onekeepass.mobile.comp-classes
                    :refer  [declare-comp-classes]])
  (:require
   [clojure.string :as str]
   [reagent.core :as r]
   [react]
   [react-native :as rn ]
   [onekeepass.mobile.background :refer [is-iOS]]
   ["react-i18next" :as ri18n] 
   ["react-native-paper" :as rnp]
   ["react-native-modal-selector" :as rnms]
   ["@react-native-community/slider" :as rnc-slider]
   ["react-native-gesture-handler" :as gh]
   ["react-native-vector-icons" :as vec-icons]
   ["@date-io/date-fns" :as DateAdapter]))

(set! *warn-on-infer* true)

;; When cljs files are compiled using Krell during development, we can see 
;; ./target/npm_deps.js and ./target/krell_npm_deps.js generated
;; All the require calls above of NPM packages will have an entry in npm_deps.js
;; All (js/require "../js/.....") calls will result an entry in krell_npm_deps.js

;; See https://react.dev/reference/react/useEffect
;; useEffect is called after a component is rendered
;; it runs both after the first render and after every update
(def react-use-effect (.-useEffect ^js/React react))

;; At this moment, these are not used
#_(def react-use-state (.-useState ^js/React react))
#_(def react-use-ref (.-useRef ^js/React react))
#_(def react-forward-ref (.-forwardRef ^js/React react))
#_(def window (-> rn/Dimensions ^js/Dim (.get "window")))

;; https://github.com/dmtrKovalenko/date-io
;;DateAdapter is #object[DateFnsUtils] 
;; (Object.keys date-fns-utils) will give all available fuctions from this util
;; In desktop version we need to use (.-default DateAdapter) and then use that to get utils. 
;; See 'onekeepass.frontend.mui-components'
(def ^js/DateAdapter.Utils date-fns-utils (DateAdapter.))

(def rn-keyboard ^js/RNKeyboard rn/Keyboard)

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
(def cust-dialog 
  (if (is-iOS)
    (r/adapt-react-class (.-default ^js/CustD (js/require "../js/components/KeyboardAvoidingDialog.js")))
    rnp-dialog))

;; All react native paper customizations
(def rnp-customization ^js/RNPC (js/require "../js/components/RNPCustomization.js"))

(def rnp-menu (r/adapt-react-class (.-RNPMenu rnp-customization)))
(def rnp-text-input (r/adapt-react-class (.-RNPTextInput rnp-customization)))

(def custom-theme (.-theme4 ^js/Theme4 rnp-customization))
(def theme-colors (->  ^js/Theme4Colors custom-theme .-colors))
;; Some useful custom colors
(def primary-color (.-primary theme-colors))
(def on-primary-color (.-onPrimary theme-colors)) ;; whiteish
(def primary-container-color (.-primaryContainer theme-colors ))
(def tertiary-color (.-tertiary theme-colors)) ;; slight reddish
(def outline-color (.-outline theme-colors))
(def background-color (.-background theme-colors))  ;; white
(def inverse-onsurface-color (.-inverseOnSurface theme-colors)) ;; very dim on white
#_(def on-background-color (.-onBackground theme-colors)) ;; primary color ?

;; Additional colors that are specifc to MD3/MD2 
(def md2-colors ^js/MD2Color rnp/MD2Colors)
(def md3-colors ^js/MD3Colors rnp/MD3Colors)


(def neutral50-color ^js/N50Color (.-neutral50 md3-colors))
(def neutral-variant60-color ^js/NV60Color (.-neutralVariant60 md3-colors))
(def neutral-variant20-color ^js/NV20Color (.-neutralVariant20 md3-colors))
;;;;

(def icon-color primary-color)
(def dots-icon-name (if (is-iOS) "dots-horizontal" "dots-vertical"))
(def page-title-text-variant "titleLarge") ;;"titleLarge" "titleMedium"

;;;;;;;;; i18n ;;;;;;;;;;;;;;;;;;;
;; This js/require loads the exported function for the i18n initializations routine 
(def i18n-support ^js/I18NSupport (js/require "../js/localization/i18n.js"))

(def init-i18n (.-initI18N i18n-support))

;; loads the i18n initializations routine. This needs to be called before the set-translator hook
;; call in any react componnent
;; This loads all translation files 
;; TODO: Figure out how to load only the relavant translation
;; TODO: Need to add getting the language code from the backend - from the exported constants in 'okp-db-service' (yet to be added)

(defn setup-i18n []
  (let [device-language (.-Language ^js/OkpDbService (.-OkpDbService rn/NativeModules))
        ;; device-language may be 'en' or 'es-US' ...
        device-language (-> device-language (str/split #"-") first)]
    ;; (println "Device language .." device-language)
    (init-i18n device-language)))

;; IMPORTANT: Needs to be called before set-translator in any component
(setup-i18n)

(def ^:private translator (atom nil)) ;; (Object.keys  @translator) => #js ["0" "1" "2" "t" "i18n" "ready"]

(defn set-translator
 " Needs to be called as hook in a functional react/reagent component" 
  []
  ;; (println "set-translator is called")
  (reset! translator (ri18n/useTranslation)))

(defn lstr 
  "Called to get the language specific text based 
   if any translation is available for the current active language
   IMPORTANT:
      This fn should be called only within a reagent component
   "
  [s]
  ;; translator should have been set before the first calling of this fn in any component
  ((.-t ^js/Translator @translator) s))

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
