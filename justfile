alias rns := npx-rn-start
alias rni := npx-rn-ios

## Uses the debug build's appId 'com.onekeepassmobile.debug'
## We should see app as 'OKP'
alias rna-ds := npx-rn-adnroid-debug-select

## Following uses the release version's appId 'com.onekeepassmobile'
alias rna-s := npx-rn-android-select
alias rna := npx-rn-android

npx-rn-start:
    npx react-native start

npx-rn-ios:
    npx react-native run-ios

# need to use  npx react-native run-android --active-arch-only 
npx-rn-android:
    npx react-native run-android

## npx react-native run-android --list-devices (to choose one of the listed emulators)
npx-rn-android-select:
    npx react-native run-android --list-devices 

## Refer comments in android/app/build.gradle
## Uses app id 'com.onekeepassmobile.debug'
## Equivalent: npx react-native run-android --appId com.onekeepassmobile.debug --list-devices
## App will launch with activity as 'com.onekeepassmobile.debug/com.onekeepassmobile.MainActivity'
npx-rn-adnroid-debug-select:
    npx react-native run-android --appIdSuffix debug --list-devices

## npx react-native run-android --device=  (did not work)

# clj-nrepl:
#     clj -A:nrepl  -M -m nrepl.cmdline --middleware '[ "cider.piggieback/wrap-cljs-repl"]'

