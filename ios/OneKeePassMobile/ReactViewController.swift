//
//  ReactViewController.swift
//  OneKeePassMobile
//
//  Created  on 11/23/25.
//

import React
import React_RCTAppDelegate
import ReactAppDependencyProvider
import UIKit
// import ReactJSC


// This is based on https://reactnative.dev/docs/0.81/integration-with-existing-apps#create-the-reactviewcontroller

// Using this ViewController in the main app works. But not in the autofill extension with error in building extension

class ReactViewController: UIViewController {
   var reactNativeFactory: RCTReactNativeFactory?
   var reactNativeFactoryDelegate: RCTReactNativeFactoryDelegate?

  override func viewDidLoad() {
    super.viewDidLoad()
    reactNativeFactoryDelegate = ReactNativeDelegate()
    reactNativeFactoryDelegate!.dependencyProvider = RCTAppDependencyProvider()
    reactNativeFactory = RCTReactNativeFactory(delegate: reactNativeFactoryDelegate!)
    view = reactNativeFactory!.rootViewFactory.view(withModuleName: "OneKeePassMobile", initialProperties: [:])
  }

// Without using instance variables also works 
//  override func viewDidLoad() {
//    super.viewDidLoad()
//    var reactNativeFactoryDelegate = ReactNativeDelegate()
//    reactNativeFactoryDelegate.dependencyProvider = RCTAppDependencyProvider()
//    var reactNativeFactory = RCTReactNativeFactory(delegate: reactNativeFactoryDelegate)
//    view = reactNativeFactory.rootViewFactory.view(withModuleName: "OneKeePassMobile", initialProperties: [:])
//  }
}

class ReactNativeDelegate: RCTDefaultReactNativeFactoryDelegate {
  override func sourceURL(for bridge: RCTBridge) -> URL? {
    bundleURL()
  }


  override func bundleURL() -> URL? {
    #if DEBUG
      return RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot: "index")
      // return localBundle()
    #else
      return Bundle.main.url(forResource: "main", withExtension: "jsbundle")
    #endif
  }
  
  override func createJSRuntimeFactory() -> JSRuntimeFactoryRef {
    debugPrint("Creating JSRuntimeFactory")
    return super.createJSRuntimeFactory()
    //return jsrt_create_jsc_factory() // Use JavaScriptCore runtime
  }

// Need to figure out to use for both app and extension and share this class bewtween them 
//  func localBundle() -> URL? {
//    
//    #if OKP_APP_EXTENSION
//        return RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot: "index")
//    #else
//        return RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot: "index.ios.autofill.extension")
//    #endif
//    
//  }

}

