
## Setting up your environment (macOS)

Please install the following

1. The first step is to install Rust. 
2. Install [Node](https://nodejs.org/)
3. Install JDK from [Adoptium](https://adoptium.net/) or from [azul](https://www.azul.com/downloads/?package=jdk#zulu) or from [Oracle](https://www.oracle.com/java/technologies/downloads/)
4. Install [Clojure](https://clojure.org/guides/install_clojure). See more details on Clojurescript [here](https://clojurescript.org/guides/quick-start)
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

Build the node dependencies
```
cd mobile 

yarn install

cd ios 

pod install ( or arch -x86_64 pod install)

```

Build the Rust Api lib for iOS simulator
```
cd mobile/db-service-ffi
just bcis
```

Build the clojurecript bundle

```
just cljc
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
