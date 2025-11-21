(ns 
 onekeepass.mobile.react-native
  "This is copied from 'shadow.react-native' ns and modified 'render-root' fn
   to accept props passed from native side to the root component"
  (:require
   ["react-native" :as rn]
   ;; Need to use create-react-class to create the Root component because of Hermes engine is now
   ;; used in RN apps and it does not support ES6 classes
   ;; See https://reactnative.dev/docs/hermes, https://legacy.reactjs.org/docs/react-without-es6.html
   ["create-react-class" :as crc]))

(defonce root-ref (atom nil))
(defonce root-component-ref (atom nil))

(defn render-root
  "Replaces the one from shadow.react-native/render-root so that we can pass the args passed from native side to the root component"
  [app-id root]
  (let [first-call? (nil? @root-ref)]
    (reset! root-ref root)

    (if-not first-call?
      (when-let [root @root-component-ref]
        (.forceUpdate ^js root))
      (let [Root
            (crc
             #js {:componentDidMount
                  (fn []
                    (this-as this (reset! root-component-ref this)))
                  :componentWillUnmount
                  (fn []
                    (reset! root-component-ref nil))
                  :render
                  (fn []
                    #_(js/console.log "Jey: In Root render fn ")
                    (let [body @root-ref]
                      (if (fn? body)
                        (this-as this (body (.-props ^js/Root this)))
                        body)))})]
        ;; Something similar to the one 'AppRegistry.registerComponent(appName, () => App);' we see in RN apps
        (rn/AppRegistry.registerComponent app-id (fn [] Root))))))
