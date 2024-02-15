//
//  AppDelegate.swift
//  OneKeePassMobile
//
//  Created on 3/7/23.
//

import Foundation
import UIKit

/**
 This implementation replaces the RN init project created files AppDelegate.h, AppDelegate.mm and main.m
  Sources used :

  SceneDelegate - Linking getInitialURL() and addEventListener() don't work on iOS #35191  - https://github.com/facebook/react-native/issues/35191
  https://stackoverflow.com/questions/74738520/change-appdelegate-mm-to-appdelegate-swift-on-react-native-0-71
  https://ospfranco.com/post/2021/07/31/migrate-rn-appdelegate-to-swift/ ( old one)
  https://reactnative.dev/docs/integration-with-existing-apps
 */

// We are subclassing RCTAppDelegate instead of usual "class AppDelegate: UIResponder, UIApplicationDelegate, RCTBridgeDelegate"
// so that we can reuse the original RN implementation of AppDelegate from RCTAppDelegate and be ready to support new architecture if possible

@main // or @UIApplicationMain ?
class AppDelegate: RCTAppDelegate {
  static var rctRootView: RCTRootView?

  // IMPORTANT: Need to override the one used in RCTAppDelegate to make it work SceneDelegate and code from RCTAppDelegate
  // copied here instead of calling super.application.. at the end so that we do not create any UIViewController during AppDelegate call
  // Here we setup only the the RCTRootView
  // See https://github.com/facebook/react-native/blob/v0.71.3/Libraries/AppDelegate/RCTAppDelegate.mm
  // mainly the method in
  // https://github.com/facebook/react-native/blob/d9321c0866a9270f8aae0d37c11584f819bc11ce/Libraries/AppDelegate/RCTAppDelegate.mm#L33

  override func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
    self.moduleName = "OneKeePassMobile"
    self.initialProps = [:] // [AnyHashable:Any]

    // Copied from the implemetation in RCTAppDelegate.mm after removing all new architecure (cond RCT_NEW_ARCH_ENABLED) related calls
    RCTAppSetupPrepareApp(application, false)

    if self.bridge == nil {
      self.bridge = self.createBridge(with: self, launchOptions: launchOptions)
    }

    let rootView = self.createRootView(with: self.bridge, moduleName: self.moduleName, initProps: self.initialProps)

    if #available(iOS 13.0, *) {
      rootView?.backgroundColor = UIColor.systemBackground
    } else {
      rootView?.backgroundColor = UIColor.white
    }

    // UIViewController will be created in SceneDelegate using this rootView and we should not create it here
    // Accordingly all that calls to create 'UIViewController' and setting the view etc from the parent class are not used here
    // and moved to SceneDelegate

    // Keep a ref to RCTRootView to use in SceneDelegate
    AppDelegate.rctRootView = rootView as? RCTRootView

    return true
  }

  override func sourceURL(for bridge: RCTBridge!) -> URL! {
    // IMPORTANT: Need to set DEBUG for Swift compiler
    // Go to XCode project, hit Build Settings, search for Swift Compiler - Custom Flags and then under Active Compilation Conditions,
    // add the following flags to Debug only
    // Flags to add:
    // DEBUG

    /* 
    #if DEBUG
      // Same as  return [[RCTBundleURLProvider sharedSettings] jsBundleURLForBundleRoot:@"index"];
      return RCTBundleURLProvider.sharedSettings()?.jsBundleURL(forBundleRoot: "index")
    #else
      // Same as return [[NSBundle mainBundle] URLForResource:@"main" withExtension:@"jsbundle"];
      return Bundle.main.url(forResource: "main", withExtension: "jsbundle")
    #endif
    */

    return self.getBundleURL()
  }

  // On RN 0.73.1 upgrade, this func was introduced
  func getBundleURL() -> URL! {
    #if DEBUG
      // Same as  return [[RCTBundleURLProvider sharedSettings] jsBundleURLForBundleRoot:@"index"];
      return RCTBundleURLProvider.sharedSettings()?.jsBundleURL(forBundleRoot: "index")
    #else
      // Same as return [[NSBundle mainBundle] URLForResource:@"main" withExtension:@"jsbundle"];
      return Bundle.main.url(forResource: "main", withExtension: "jsbundle")
    #endif
  }
}

// File AppDelegate.mm
/**
 #import "AppDelegate.h"

 #import <React/RCTAppSetupUtils.h>

 #import <React/RCTBundleURLProvider.h>
 // Jey - Needs to include so that we can use swift code in Obj-c as per
 // https://developer.apple.com/documentation/swift/importing-swift-into-objective-c
 #import "OneKeePassMobile-Swift.h"

 @implementation AppDelegate

 // Copied the implementation of this method from
 // https://github.com/facebook/react-native/blob/v0.71.3/Libraries/AppDelegate/RCTAppDelegate.mm
 // and modified so that we can use SceneDelegate

 - (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions
 {
   self.moduleName = @"OneKeePassMobile";
   // You can add your custom initial props in the dictionary below.
   // They will be passed down to the ViewController used by React Native.
   self.initialProps = @{};

   // Jey - For testing the use Swift class in Obj-c
   // [FileUtils testLog];

   NSLog(@"launchOptions in didFinishLaunchingWithOptions are  %@", launchOptions);
 // This is the orignal code and replaced with the following
 //  to support SceneDelegate along with ApplicationDelegate
 //  return [super application:application didFinishLaunchingWithOptions:launchOptions];

   // --------------------------------------------------------------- //
   // Removed new architecture related code
   BOOL enableTM = NO;

     RCTAppSetupPrepareApp(application, enableTM);

     if (!self.bridge) {
       self.bridge = [self createBridgeWithDelegate:self launchOptions:launchOptions];
     }

     NSDictionary *initProps = [self prepareInitialProps];
     UIView *rootView = [self createRootViewWithBridge:self.bridge moduleName:self.moduleName initProps:initProps];

     if (@available(iOS 13.0, *)) {
       rootView.backgroundColor = [UIColor systemBackgroundColor];
     } else {
       rootView.backgroundColor = [UIColor whiteColor];
     }

     // Following of RCTAppDelegate.mm need to commented out so that, we can have only
     // one UIViewController created in SceneDelegate

     // self.window = [[UIWindow alloc] initWithFrame:[UIScreen mainScreen].bounds];
     // UIViewController *rootViewController = [self createRootViewController];
     // rootViewController.view = rootView;
     //self.window.rootViewController = rootViewController;
     //[self.window makeKeyAndVisible];

     // ----------------------------------------------------- //

     RNBridgeShare.sharedInstance.bridge = self.bridge;
     RNBridgeShare.sharedInstance.rctRootView = (RCTRootView * )rootView;

     return YES;

 }
 // Copied from RCTAppDelegate.mm
 - (NSDictionary *)prepareInitialProps
 {
   NSMutableDictionary *initProps = self.initialProps ? [self.initialProps mutableCopy] : [NSMutableDictionary new];

 #ifdef RCT_NEW_ARCH_ENABLED
   initProps[kRNConcurrentRoot] = @([self concurrentRootEnabled]);
 #endif

   return initProps;
 }

 // Jey - Called when user clicks or shares a file with kdbx extension as we have added UTImportedTypeDeclarations in info.plist
 // This worked only when the app is in the background and user clicks .kdbx in File App
 // The issue Method 'application:openURL:options:' is not called when the app is not running
 // and user clicks the db file with extension .kdbx
 // https://stackoverflow.com/questions/58624786/method-applicationopenurloptions-is-not-called
 // No more this is called by iOS as we need to use Scene based setup for iOS 13+ to receive open url
 // See SceneDelegate functions
 // See https://developer.apple.com/documentation/xcode/defining-a-custom-url-scheme-for-your-app

 - (BOOL)application:(UIApplication *)app openURL:(NSURL *)url options:(NSDictionary<UIApplicationOpenURLOptionsKey, id> *)options
 {
   NSLog(@"Received url is %@", url);
   NSLog(@"Received url options are  %@", options);
   // Send an event to the JS side
   [OkpEvents calledWithUrl:url];
   return true;
 }

 - (NSURL *)sourceURLForBridge:(RCTBridge *)bridge
 {
 #if DEBUG
   return [[RCTBundleURLProvider sharedSettings] jsBundleURLForBundleRoot:@"index"];
 #else
   return [[NSBundle mainBundle] URLForResource:@"main" withExtension:@"jsbundle"];
 #endif
 }

 /// This method controls whether the `concurrentRoot`feature of React18 is turned on or off.
 ///
 /// @see: https://reactjs.org/blog/2022/03/29/react-v18.html
 /// @note: This requires to be rendering on Fabric (i.e. on the New Architecture).
 /// @return: `true` if the `concurrentRoot` feature is enabled. Otherwise, it returns `false`.
 - (BOOL)concurrentRootEnabled
 {
   return true;
 }

 @end

 */

// File AppDelegate.h
/**
 #import <RCTAppDelegate.h>
 #import <UIKit/UIKit.h>

 @interface AppDelegate : RCTAppDelegate

 @end

 */

// File main.m
/**
 #import <UIKit/UIKit.h>

 #import "AppDelegate.h"

 int main(int argc, char *argv[])
 {
   @autoreleasepool {
     return UIApplicationMain(argc, argv, nil, NSStringFromClass([AppDelegate class]));
   }
 }

 */
