# Building the Android release APK

These are the steps used to build the APK attached to each GitHub release. Follow them to build the same APK from source and compare it with the official one.

The APK on GitHub is the fully FOSS build: it leaves out the camera-based QR code scanner (`react-native-vision-camera`) that is included in the Google Play build.

For the development setup, see [BUILD.md](./BUILD.md).

## Toolchain

Official releases are built on macOS (Apple Silicon). Each GitHub release from 0.24.0 onwards has a `BUILD-INFO.txt` next to the APK. It lists the exact versions of rustc, cargo-ndk, both Android NDKs, Gradle, the JDK, Node.js, Yarn and shadow-cljs used for that release. Install those versions before building.

The Rust version is also pinned in the source, in `db-service-ffi/rust-toolchain.toml`, so rustup switches to it and installs the Android target automatically.

Releases up to 0.23.0 have no `BUILD-INFO.txt` and no `rust-toolchain.toml`. They were built with rustc 1.91.0, cargo-ndk 3.5.7, NDK 28.2.13676358 (r28c) for the Rust lib and NDK 27.1.12297006 for Gradle. For those, run `rustup override set 1.91.0` in the `db-service-ffi` directory before building.

Install cargo-ndk, using the version from `BUILD-INFO.txt`:

```
cargo install cargo-ndk --version <version> --locked
```

Install both NDK versions from Android Studio's SDK Manager.

## Steps

### 1. Check out the release tag

Android releases are tagged `android-v<version>`, for example `android-v0.24.0`. Releases up to 0.22.1 are tagged `v<version>`.

```
git clone https://github.com/OneKeePass/mobile.git
cd mobile
git checkout android-v0.24.0
```

The tag already has the FOSS APK configuration committed: the vision-camera module and the camera permission are removed, and the permissions that libraries add on their own are stripped. There is nothing to edit.

### 2. Install the JavaScript dependencies

```
yarn install --frozen-lockfile
```

### 3. Build the uniffi-bindgen tool

```
cd uniffi-bindgen
cargo build --release
cd ..
```

### 4. Build the UI bundle

```
cd src-cljs
just shre
cd ..
```

### 5. Build the Rust library

```
cd db-service-ffi
just bca-r
cd ..
```

`just bca-r` sets the Botan build options and the NDK paths, then builds with `cargo ndk -p 31 -t aarch64-linux-android --release`. It copies the library into `android/app/src/main/jniLibs` and the Kotlin bindings into `android/app/src/main/java`. Calling `cargo ndk` yourself without these settings gives a different library.

`onekeepass-core` is fetched from its git tag, as set in `db-service-ffi/Cargo.toml`. `db-service-ffi/Cargo.lock` pins every other crate.

For tags up to `android-v0.23.0`, `just bca-r` expects a debug build to exist. Run these instead:

```
cd db-service-ffi
just build-android-lib-all true
cp -R target/jniLibs ../android/app/src/main
cd ..
```

The Kotlin bindings for those releases are already committed in `android/app/src/main/java/onekeepass/mobile/ffi/db_service.kt`.

On Linux, first change the NDK paths in `db-service-ffi/justfile` (see [BUILD.md](./BUILD.md#building-on-linux)).

### 6. Build the APK

```
cd android
rm -rf app/.cxx
OKP_FOSS_BUILD=1 ./gradlew clean assembleRelease
```

`OKP_FOSS_BUILD=1` is required. It makes Metro replace `react-native-vision-camera` with the stub in `foss-stubs/`. Without it, `assets/index.android.bundle` differs from the official one, and the app crashes at startup.

The unsigned APK is written to:

```
android/app/build/outputs/apk/release/app-arm64-v8a-release-unsigned.apk
```

## Signing

The official APK is signed with a private key. The signing config in `android/app/build.gradle` is used only when the key properties (`OKP_APP_APK_STORE_FILE` and the others) are passed to Gradle. A build without them produces the unsigned APK above. Signing is the only step that is not public.

To compare your build with the official APK while ignoring the signature, use [apksigcopier](https://github.com/obfusk/apksigcopier):

```
apksigcopier compare OneKeePass-arm64-v8a-release.apk --unsigned app-arm64-v8a-release-unsigned.apk
```

To see which files differ, and how, use [diffoscope](https://diffoscope.org).

## Known differences

The build is not yet fully reproducible across machines:

- **Native libraries built by Gradle.** The React Native and third-party C++ libraries (`libreactnative.so`, `libappmodules.so`, `libreanimated.so` and others) are compiled on the build machine. Their contents depend on the host OS and on the absolute build paths.
- **The Rust library.** `libdb_service_ffi.so` can differ when the rustc version, the NDK version, the host OS or the absolute paths of the source and the Cargo registry differ.
