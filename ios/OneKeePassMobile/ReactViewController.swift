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
}

class ReactNativeDelegate: RCTDefaultReactNativeFactoryDelegate {
  override func sourceURL(for bridge: RCTBridge) -> URL? {
    bundleURL()
  }

  override func bundleURL() -> URL? {
    #if DEBUG
      RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot: "index")
    #else
      Bundle.main.url(forResource: "main", withExtension: "jsbundle")
    #endif
  }
}

