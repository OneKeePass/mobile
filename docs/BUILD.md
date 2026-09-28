# Building OneKeePass Mobile for development

This guide builds the app for an iOS simulator or an Android emulator or device. To build the Android release APK, see [APK-RELEASE-BUILD.md](./APK-RELEASE-BUILD.md).

Development and releases are done on macOS. Android builds on Linux should work, but the Rust build recipes in `db-service-ffi/justfile` assume the macOS Android SDK location (see [Building on Linux](#building-on-linux)).

## How the app is put together

| Part | Location | Built with |
|---|---|---|
| Rust core (`onekeepass-core`) + FFI layer | `db-service-ffi` | `cargo` / `cargo-ndk`, driven by `just` |
| Kotlin / Swift bindings for the Rust layer | generated into `android/` and `ios/` | `uniffi-bindgen` (from `./uniffi-bindgen`) |
| UI (ClojureScript) | `src-cljs` | `shadow-cljs` |
| React Native app shell | `android`, `ios` | Gradle / Xcode, Metro |

## 1. Install the tools

| Tool | Version used | Notes |
|---|---|---|
| Rust | pinned in `db-service-ffi/rust-toolchain.toml` | [rustup](https://rustup.rs) installs it automatically |
| [just](https://github.com/casey/just) | 1.40+ | `cargo install just` |
| Node.js | 20.19.4 or newer | |
| Yarn | 1.22.x | |
| JDK | 17 or 21 | needed by both Gradle and shadow-cljs |
| Clojure CLI | 1.12+ | [install guide](https://clojure.org/guides/install_clojure) |
| Android Studio | latest | Android only |
| Android NDK | 28.2.13676358 (r28c) and 27.1.12297006 | Android only. r28c builds the Rust lib; 27.1 is used by Gradle |
| [cargo-ndk](https://github.com/bbqsrc/cargo-ndk) | 3.5.7 | Android only. `cargo install cargo-ndk --version 3.5.7 --locked` |
| Xcode | latest | iOS only |
| CocoaPods | via `bundle install` (see `Gemfile`) | iOS only |

Also follow the React Native [environment setup](https://reactnative.dev/docs/set-up-your-environment) for your target platform.

Install the NDK versions from Android Studio's SDK Manager (SDK Tools → NDK (Side by side) → Show Package Details).

## 2. One-time setup

Get the JavaScript dependencies. This also applies the patches in `patches/`.

```
cd mobile
yarn install
```

Build the `uniffi-bindgen` tool that generates the Kotlin and Swift bindings:

```
cd mobile/uniffi-bindgen
cargo build --release
```

iOS only, install the pods:

```
cd mobile
bundle install
cd ios
bundle exec pod install
```

## 3. Build the Rust library

Android emulator or device (arm64):

```
cd mobile/db-service-ffi
just bca
```

iOS simulator:

```
cd mobile/db-service-ffi
just bcis
```

Each command builds the Rust library in debug mode, generates the bindings, and copies both into the Android or Xcode project. Run it again whenever the Rust code changes.

## 4. Build the UI

Start shadow-cljs in watch mode and leave it running:

```
cd mobile/src-cljs
just shcr
```

For the iOS AutoFill extension, run this too in another terminal:

```
cd mobile/src-cljs
just shexcr
```

`just shcr` builds the main app for both platforms. Near the top of `src-cljs/cljs-main-app-src/main/onekeepass/mobile/core.cljs`, and near its bottom, there are platform-specific lines (the `android-core` require and the two `render-root` calls). Make sure the lines for the platform you are running are active and the others are commented out with `#_`.

## 5. Run the app

In one terminal, start Metro:

```
cd mobile
just rns
```

In another terminal, launch the app:

```
cd mobile
just rni    # iOS simulator
just rna    # Android emulator or device
```

To install the Android debug build next to a released version of the app, use `just rna-ds`. It uses the app id `com.onekeepassmobile.debug`, and the app shows up as "OKP".

## Development with the REPL

With `just shcr` running and the app open in a simulator or emulator, connect your editor's nREPL client to shadow-cljs and select the `:app` build. UI code changes reload into the running app.

## Building on Linux

`db-service-ffi/justfile` points at the NDK under `$HOME/Library/Android/sdk/ndk/28.2.13676358/android-ndk-r28c` and at the `darwin-x86_64` prebuilt toolchain. On Linux, change `ndk_botan_build_home`, `ndk_ar` and `ndk_cxx` to your NDK location and the `linux-x86_64` toolchain.
