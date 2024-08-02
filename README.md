<h1 align="center">
  <img src="./android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="OneKeePass" width="128" />
  <br>
  <div>OneKeePass</div>
</h1>

**OneKeePass Mobile Application** is a open source and secure password manager. It supports the KeePass-compatible database (Kdbx 4.x). It stores and manages all your passwords and other sensitive information in a single encrypted file that can be stored in any location, including local and public cloud solutions. The app relies on iOS and Android OS provided integration with storage providers.  


All created database files are encrypted using the best and most secure encryption algorithms currently known (AES-256, ChaCha20).The database is locked with a master key.

You can install OneKeePass for Android from Google Play store and OneKeePass for iOS from Apple app store

<a href="https://apps.apple.com/us/app/onekeepass-password-manager/id6448712640" target="_blank"><img alt="Get it on App Store" src="https://imgur.com/GdGqPMY.png" width="153" height="46"></a>
<a href="https://play.google.com/store/apps/details?id=com.onekeepassmobile" target="_blank"><img alt="Get it on Google Play" src="https://imgur.com/YQzmZi9.png" width="153" height="46"></a> 

| iOS  | Android |
| ------------- | ------------- |
| <img src="./screenshots/i_entrytypescat.png" alt="" width="325" height="650" /> | <img src="./screenshots/a_entrytypescat.png" alt="" width="300" height="650" />|

<br>

OneKeePass is also available for **Windows, macOS and Linux desktops** can be downloaded from [here](https://github.com/OneKeePass/desktop/releases)

## Features
- Entries are grouped as Types or Categories or the standard Group tree
- Any number of custom fields can be added
- Custom fields can be organized as section
- Any number of databases can be created and used
- Fully offline (you decide the location where the password database files are stored)
- Optional additional security by using a key file with or without a master password
- Database quick unlock using biometrics (fingerprint)
- Upload attachments to an entry
- Time based One-Time Passwords (TOTP) can added and used
- Supports Light or Dark Theme
- Multi-language support
- AutoFill supported



## FAQ
Common FAQ about the application can be found [here](./docs/FAQ.md)

## How to build

OneKeePass mobile application is developed primarily using the **ClojureScript** and **Rust** programming languages. Swift and Kotlin are used for the native modules development that interact with Rust core api lib.

The apps are built with - [React Native](https://reactnative.dev/),[React Native Paper](https://reactnativepaper.com/) and many other well known react native packages 

To build and run on a simulator/emulator locally, please follow the instructions [here](./docs/BUILD.md) for both iOS and Android platforms

## How to Contribute

### Translations

If you want to contribute translation of texts used in this application in your language of choice, please see [OneKeePass Translations](https://github.com/OneKeePass/onekeepass-translations)

### Code
The CONTRIBUTING document and other guidelines for code contributions are not yet ready. Till that time, please feel free to::
- [Report bugs, suggest new features](https://github.com/OneKeePass/mobile/issues)

## License
This project is licensed under the GPLv3.
