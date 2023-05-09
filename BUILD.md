
## Setting up your environment (macOS)

Please install the following

1. The first step is to install Rust. 
2. Install [Node](https://nodejs.org/)
3. Install JDK from [Adoptium](https://adoptium.net/) or from [azul](https://www.azul.com/downloads/?package=jdk#zulu) or from [Oracle](https://www.oracle.com/java/technologies/downloads/)
4. Install [Clojure](https://clojure.org/guides/install_clojure). See more details on ClojureScript [here](https://clojurescript.org/guides/quick-start)
5. Setup the development enviroment for [React Native](https://reactnative.dev/docs/0.71/environment-setup) 

## Build

One time install:

- Install rust tool [just](https://github.com/casey/just)
- Install rust tool [cargo-ndk](https://github.com/bbqsrc/cargo-ndk)
``` 
cargo install just

cargo install cargo-ndk 
```

### iOS

Install the node dependencies.This is done one time
```
cd mobile 

yarn install

cd ios 

pod install ( or arch -x86_64 pod install)

```

Build the Rust Api lib for iOS simulator. 
```
cd mobile/db-service-ffi
just bcis (or just cp-ios-ffi-fat-sim-debug-lib-src)

# This builds and copies the rust lib binary and the Swift wrapper code to xcode project
```


Build the clojurecript bundle

```
cd mobile

just clj-main-build
```

Start the react native metro watcher

```
cd mobile

yarn start or npx react-native start
```

Open another terminal and do the following to launch the app in an iOS simulator
```
cd mobile

just rni or (npx react-native run-ios)
```

### Android

If this is not yet done once, install the node dependencies.
```
cd mobile 

yarn install

```

Build the Rust Api lib for android emulator. 
```
cd mobile/db-service-ffi

just bca (just cp-android-ffi-lib-src)

# This builds and copies the rust lib binary and the Kotlin wrapper code to android project
```


Build the clojurecript bundle

```
cd mobile

just clj-main-build
```

Start the react native metro watcher

```
cd mobile

yarn start or npx react-native start
```

Open another terminal and do the following to launch the app in an iOS simulator
```
cd mobile

just rna or (npx react-native run-android)
```

## Development using REPL 
Comming soon