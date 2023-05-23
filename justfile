alias cljc := clj-compile
alias clj-clean-c := clj-clean-compile
alias cljr := clj-repl
alias cljnr := clj-nrepl
alias cljcr := clj-compile-repl
alias cljb  := clj-main-build
alias rns := npx-rn-start
alias rni := npx-rn-ios
alias rna := npx-rn-android

# Set the the host for krell and metro connection to work
# localhost works only for iOS krell repl connection
# host := "localhost" 
# For android, need to use instead of localhost or 127.0.0.1
# host := "10.0.2.2"
host := "192.168.1.9"

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

clj-nrepl:
    clj -A:nrepl  -M -m nrepl.cmdline --middleware '[ "cider.piggieback/wrap-cljs-repl"]'

# use 'just cljb advanced' for advanced compilation
clj-main-build type="simple" :
    clj -M -m krell.main -O {{type}}  -co build.edn -c


npx-rn-start:
    npx react-native start

npx-rn-ios:
    npx react-native run-ios

# need to use  npx react-native run-android --active-arch-only 
npx-rn-android:
    npx react-native run-android