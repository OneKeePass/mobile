# Compile and run the repl
alias cljcr := clj-compile-repl 

alias cljc := clj-compile
alias clj-clean-c := clj-clean-compile
alias cljr := clj-repl

# Default is simple build
# Use 'just cljb advanced' for advanced build of cljs files
alias cljb  := clj-main-build  

alias ex-cljb := ios-ext-clj-build

# Set the the host for krell and metro connection to work
# localhost works only for iOS krell repl connection
# host := "localhost" 
# For android, need to use instead of localhost or 127.0.0.1
# host := "10.0.2.2"
host := "192.168.1.5"

# The clojurescript compiled output found './target/main.js' is used in index.js

# We can use something like '... -co build.edn -rc false -c -r' to disable hot reloading

clj-clean-compile:
    rm -rf target
    clj -M -m krell.main --host {{host}} -co build.edn -c

clj-compile:
    clj -M -m krell.main --host {{host}} -co build.edn -c

clj-repl:
    clj -M -m krell.main --host {{host}} -co build.edn -r

clj-compile-repl:
    rm -rf target
    clj -M -m krell.main --host {{host}} -co build.edn -c -r

# use 'just cljb advanced' for advanced compilation
clj-main-build type="simple" :
    rm -rf target
    clj -M -m krell.main -O {{type}}  -co build.edn -c

# By default port 5001 is used for the main app
ios-ext-port := "5002"

# Need to make sure that this is clj watches only the extension source changes 
ios-ext-dir := "./cljs-ios-autofill-extension/src"

ios-ext-cljcr:
    rm -rf target-ios-autofill-extension
    clj -A:ios-ext -M -m krell.main --host {{host}} -p {{ios-ext-port}} -co  build-ios-autofill-extension.edn --index-js-out index.ios.autofill.extension.js -wd {{ios-ext-dir}}  -c  -r

# use 'just  ex-cljb advanced' for advanced compilation
ios-ext-clj-build type="simple" :
    rm -rf target-ios-autofill-extension
    clj -A:ios-ext -M -m krell.main -O {{type}}  -co build-ios-autofill-extension.edn --index-js-out index.ios.autofill.extension.js -c

##############################  React Native  ###################################### 

alias rns := npx-rn-start
alias rni := npx-rn-ios
alias rna := npx-rn-android

npx-rn-start:
    npx react-native start

npx-rn-ios:
    npx react-native run-ios

# need to use  npx react-native run-android --active-arch-only 
npx-rn-android:
    npx react-native run-android


# clj-nrepl:
#     clj -A:nrepl  -M -m nrepl.cmdline --middleware '[ "cider.piggieback/wrap-cljs-repl"]'