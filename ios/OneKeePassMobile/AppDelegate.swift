//
//  AppDelegate.swift
//  OneKeePassMobile
//
//  Created on 3/7/23.
//

import Foundation
import UIKit

// We need to implement this class instead of using RN created AppDelegate.mm so that we can use 'SceneDelegate'
// We use 'SceneDelegate' so that our app can be opened when user presses *.kdbx file on iOS File explorer

// This implementation replaces the RN init project created files AppDelegate.h, AppDelegate.mm and main.m

// Till RN 0.73.4, subclassing from RCTAppDelegate and copying some steps from 'func application' in RCTAppDelegate.mm to AppDelegate.swift 
// and changing for our need worked. 
// But on upgarde to RN 0.74.x, this did not work and we need to copy more steps from 'func application' 
// in RCTAppDelegate.mm and more supporting functions are to be reimplemented in swift. So decided to use the following subclassing method to make it simple

// This is based on 'Integration with Existing Apps' https://reactnative.dev/docs/integration-with-existing-apps?language=swift

// When we decide to use new architecture -  the new native module system (TurboModules) and the new renderer (Fabric) etc, we need to make more changes here

// This class provides the entry point 'main'
@main
class AppDelegate: UIResponder, UIApplicationDelegate, RCTBridgeDelegate {
  static var rctRootView: RCTRootView?

  // This is required to be declared and will be set by the system (how?)
  // This is declared in UIApplicationDelegate as 'optional var window: UIWindow? { get set }'
  var window: UIWindow?

  func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
    
    // This is the module name used in app.json and used in AppRegistry.registerComponent call in index.js
    let moduleName = "OneKeePassMobile"
    let initialProps: [AnyHashable: Any] = [:] // [AnyHashable:Any]

    // This will also work, but RCTBridge based init is done. See comments there
    // let rootView = RCTRootView( bundleURL: bundleURL()!, moduleName: "OneKeePassMobile",initialProperties: [:],launchOptions: nil)

    // We are using a bridge so that we can have multiple views powered by
    // React Native that are associated with a single JS runtime if required
    let bridge = RCTBridge(delegate: self, launchOptions: nil)
    let rootView = RCTRootView(bridge: bridge!, moduleName: moduleName, initialProperties: initialProps)

    if #available(iOS 13.0, *) {
      rootView.backgroundColor = UIColor.systemBackground
    } else {
      rootView.backgroundColor = UIColor.white
    }

    // UIViewController will be created in SceneDelegate using this rootView and we should not create it here
    // Accordingly all that calls to create 'UIViewController' and setting the view etc from the parent class are not used here
    // and moved to SceneDelegate.swift

    // Keep a ref to RCTRootView to use in SceneDelegate
    AppDelegate.rctRootView = rootView

    return true
  }

  // Similar to the function from 'AppDelegate.mm' of the sample app
  func bundleURL() -> URL? {
    #if DEBUG
      // Same as  return [[RCTBundleURLProvider sharedSettings] jsBundleURLForBundleRoot:@"index"];
      return RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot: "index")
    #else
      // Same as return [[NSBundle mainBundle] URLForResource:@"main" withExtension:@"jsbundle"];
      return Bundle.main.url(forResource: "main", withExtension: "jsbundle")
    #endif
  }

  /// This fn implementation is to conform to the protocol 'RCTBridgeDelegate'
  func sourceURL(for bridge: RCTBridge) -> URL? {
    return self.bundleURL()
  }
  
  /**
   // Called when the UIKit is about to create & vend a new UIScene instance to the application.
   func application(_ application: UIApplication, configurationForConnecting connectingSceneSession: UISceneSession,
                    options: UIScene.ConnectionOptions) -> UISceneConfiguration {
   }
   */
}

// Few resources referred and may be outdated ones
/**
 This implementation replaces the RN init project created files AppDelegate.h, AppDelegate.mm and main.m
  Sources used :

  SceneDelegate - Linking getInitialURL() and addEventListener() don't work on iOS #35191  - https://github.com/facebook/react-native/issues/35191
  https://stackoverflow.com/questions/74738520/change-appdelegate-mm-to-appdelegate-swift-on-react-native-0-71
  https://ospfranco.com/post/2021/07/31/migrate-rn-appdelegate-to-swift/ ( old one)
  https://reactnative.dev/docs/integration-with-existing-apps
 */
