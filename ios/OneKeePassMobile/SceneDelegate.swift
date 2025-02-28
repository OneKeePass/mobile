//
//  SceneDelegate.swift
//  OneKeePassMobile
//
//  Created on 3/6/23.
//

import Foundation
import UIKit

// Based on the following sources
// https://developer.apple.com/documentation/uikit/app_and_environment/scenes/specifying_the_scenes_your_app_supports?language=objc
// https://dev.to/kevinmaarek/add-a-scene-delegate-to-your-current-project-5on
// https://www.adapptor.com.au/blog/enhance-existing-apps-with-carplay
// https://gist.github.com/Zamerot/f5df698e11f1bd8033eb9172cb9406a2,https://gist.github.com/Zamerot?page=3
// https://samwize.com/2019/08/05/setup-scenedelegate-without-storyboard/

// This class needs to be referred in Info.plist
// See Info.plist - UIApplicationSceneManifest -> UISceneConfigurations -> UISceneDelegateClassName

@available(iOS 13, *)
class SceneDelegate: NSObject, UIWindowSceneDelegate {
  private let logger = OkpLogger(tag: "SceneDelegate")
  
  // Keep the ref to URL to use in native modules OkpDbService and OkpEvents
  static var openUrl: URL?
  
  var window: UIWindow?
  
  // A flag to indicate that user is doing 'authenticateWithBiometric'
  // This ensures that we do not send native event in 'sceneWillResignActive'
  // It looks like 'sceneWillResignActive' is fired when FaceID is shown. This causes the App lock
  static var inBiometricCall: Bool = false
  
  func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
    // A new scene was added to the app.
    
    logger.debug("In SceneDelegate scene func...\(connectionOptions.urlContexts)")
    
    // The url is passed when the user presses .kdbx file and the app is not running
    if let urlContext = connectionOptions.urlContexts.first {
      // As ReactContext is not yet, we cannot emit event to UI, we can store here
      // and UI side the open url is pulled
      SceneDelegate.openUrl = urlContext.url
    }

    let rootViewController = UIViewController()
    rootViewController.view = AppDelegate.rctRootView!

    if let windowScene = scene as? UIWindowScene {
      let window = UIWindow(windowScene: windowScene)
      window.rootViewController = rootViewController
      self.window = window // any use ? - It may be required because of UIWindowSceneDelegate
      window.makeKeyAndVisible()
      RNBootSplash.initWithStoryboard("BootSplash", rootView: AppDelegate.rctRootView!)
    }
  }
  
  // Following are from UIWindowSceneDelegate
  // See https://developer.apple.com/documentation/uikit/uiscenedelegate
  
  // This will be called when user presses .kdbx file and the app is in the background
  func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
    logger.debug("scene with openURLContexts called \(URLContexts)")
    
    if !URLContexts.isEmpty {
      OkpEvents.calledWithUrl(URLContexts.first!.url)
    }
    
    URLContexts.forEach { context in
      logger.debug("URLContexts are \(context.url)")
    }
  }
  
  func sceneWillEnterForeground(_ scene: UIScene) {
    // A scene is about to enter the foreground.
    // It appears all RN modules init are happening after this call only
    logger.debug("A scene is about to enter the foreground")
  }
  
  func sceneDidBecomeActive(_ scene: UIScene) {
    logger.debug("sceneDidBecomeActive.....")
    
    // By this all RN modules and rust init should have happened
    OkpEvents.sendAppBecomesActive()
  }
  
  func sceneWillResignActive(_ scene: UIScene) {
    // A scene is becoming inactive.
    
    logger.debug("A scene sceneWillResignActive .... \(Self.inBiometricCall)")
    
    // The sceneWillResignActive method is called when the app is about to move
    // from active to inactive state, which can occur when Face ID is used for authentication
    // We do not want to emit 'sendAppBecomesInActive' in that case.
    // For this the Self.inBiometricCall is set true in OkpDbService.authenticateWithBiometric before calling system bioauthentication
    
    // Also see https://stackoverflow.com/questions/26035546/touchid-calls-applicationwillresignactive-and-applicationdidbecomeactive
    
    if !Self.inBiometricCall {
      logger.debug("A scene sceneWillResignActive ...sending sendAppBecomesInActive as \(Self.inBiometricCall)")
      OkpEvents.sendAppBecomesInActive()
    } else {
      logger.debug("A scene sceneWillResignActive ...is NOT sending sendAppBecomesInActive")
      
      // Need to reset so that we can send 'sendAppBecomesInActive' to UI when the app is becoming inactive
      Self.inBiometricCall = false
    }
  }

  func sceneDidEnterBackground(_ scene: UIScene) {
    // A scene did enter the background.
    logger.debug("A scene did enter the background")
  }

  func sceneDidDisconnect(_ scene: UIScene) {
    logger.debug("sceneDidDisconnect...")
  }
}
