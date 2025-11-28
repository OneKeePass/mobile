//
//  OkpAutofillReactViewController.swift
//  OneKeePassMobile
//
//  Created  on 11/23/25.
//

import React
import React_RCTAppDelegate
import ReactAppDependencyProvider
import UIKit
//import ReactJSC


class OKpReactView {
  static func createOkpAutofillReactView() -> UIView {
    let reactNativeFactoryDelegate = ReactNativeDelegate()
    reactNativeFactoryDelegate.dependencyProvider = RCTAppDependencyProvider()
    let reactNativeFactory = RCTReactNativeFactory(delegate: reactNativeFactoryDelegate)
    let view = reactNativeFactory.rootViewFactory.view(withModuleName: "OneKeePassMobile", initialProperties: [:])

    return view
  }
}

class ReactNativeDelegate: RCTDefaultReactNativeFactoryDelegate {
  override func sourceURL(for bridge: RCTBridge) -> URL? {
    bundleURL()
  }

  override func bundleURL() -> URL? {
    #if DEBUG
      return RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot: "index.ios.autofill.extension")
    // return localBundle()
    #else
      return Bundle.main.url(forResource: "main", withExtension: "jsbundle")
    #endif
  }

  // Not able to include the header file for RCTHost even after trying to include this in OneKeePassAutoFill-Bridging-Header.h
  // #import #import <React-RuntimeApple/RCTHost.h>
  // or #import <RCTHost.h>
  // or #import <React/RCTHost.h>
  // or #import <ReactCommon/RCTHost.h>
  // or import React_RuntimeApple in this file with error  No such module 'React_RuntimeApple'
  // or import ReactCommon 

//  func hostDidStart(_ host: RCTHost) {
//    //debugPrint("Host did start is called \(host)")
//  }
}

// This is based on https://reactnative.dev/docs/0.81/integration-with-existing-apps#create-the-reactviewcontroller

// Using the 'view' from this UIViewController in CredentialProviderViewController's view did not work

/*
 class OkpReactViewController: UIViewController {
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
 */
