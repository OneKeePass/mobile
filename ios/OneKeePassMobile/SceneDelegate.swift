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

  // False when the sending app handed over the file as a copy instead of opening it in place.
  // In that case the copy is in our own Documents/Inbox dir and we do not open it. See
  // FileUtils and OkpEvents.calledWithUrl.
  // Which app sends a copy and which one opens in place is decided by the sending app,
  // so this flag from iOS is the only signal we use
  static var openUrlInPlace: Bool = true
  
  var window: UIWindow?
  
  // A flag to indicate that user is doing 'authenticateWithBiometric'
  // This ensures that we do not send native event in 'sceneWillResignActive'
  // It looks like 'sceneWillResignActive' is fired when FaceID is shown. This causes the App lock
  static var inBiometricCall: Bool = false

  // The user's 'Block screenshots' setting. Set from OkpDbService.setScreenCaptureBlocked each time
  // the UI loads the app preference and when the setting changes.
  // iOS has no API to block screenshots. When this is true:
  //  - the app window is moved into the layer of a secure text field (see 'updateScreenshotShield')
  //    so that screenshots and recordings show it blank
  //  - a cover is shown while the screen is recorded or mirrored, and when the app goes to the
  //    background so that the app switcher snapshot does not show the database content. This
  //    still works when the secure layer could not be found
  private(set) static var screenCaptureBlocked: Bool = false

  private static weak var current: SceneDelegate?

  // A separate window above the app window so that the cover is also above any RN modal
  private var privacyCoverWindow: UIWindow?

  private var inBackground: Bool = false

  // Set while the app window's layer is inside the secure text field's layer
  private var secureField: UITextField?
  private weak var windowLayerOriginalSuperlayer: CALayer?

  static func setScreenCaptureBlocked(_ blocked: Bool) {
    screenCaptureBlocked = blocked
    current?.updateScreenshotShield()
    current?.updatePrivacyCover()
  }

  // iOS leaves the content of a secure text entry field out of screenshots and recordings.
  // This is not a documented API: it relies on the field drawing its text in an internal canvas
  // view. The app window's layer is placed under that canvas layer. Only the layer tree changes;
  // the view tree is untouched, so touches, the keyboard and accessibility keep working.
  // If the canvas layer is not found on the running iOS version, nothing is changed
  private func updateScreenshotShield() {
    guard let window = window else { return }

    if Self.screenCaptureBlocked, secureField == nil {
      guard let originalSuperlayer = window.layer.superlayer else {
        logger.error("Screenshot shield not applied: window layer has no superlayer")
        return
      }

      let field = UITextField()
      field.isSecureTextEntry = true
      field.isUserInteractionEnabled = false
      window.addSubview(field)
      field.layoutIfNeeded()

      guard let canvasLayer = Self.secureCanvasLayer(of: field) else {
        logger.error("Screenshot shield not applied: secure canvas layer not found. Sublayers are \(Self.sublayerDelegateNames(of: field))")
        field.removeFromSuperview()
        return
      }

      originalSuperlayer.addSublayer(field.layer)
      canvasLayer.addSublayer(window.layer)

      secureField = field
      windowLayerOriginalSuperlayer = originalSuperlayer
      logger.debug("Screenshot shield applied using \(Self.sublayerDelegateNames(of: field))")

    } else if !Self.screenCaptureBlocked, let field = secureField {
      if let originalSuperlayer = windowLayerOriginalSuperlayer {
        originalSuperlayer.addSublayer(window.layer)
      }
      field.layer.removeFromSuperlayer()
      field.removeFromSuperview()
      secureField = nil
      windowLayerOriginalSuperlayer = nil
      logger.debug("Screenshot shield removed")
    }
  }

  // The internal view's class name differs between iOS versions (_UITextFieldCanvasView,
  // _UITextLayoutCanvasView ...), so it is matched by the 'Canvas' part of the name
  private static func secureCanvasLayer(of field: UITextField) -> CALayer? {
    field.layer.sublayers?.first { layer in
      guard let delegate = layer.delegate else { return false }
      return String(describing: type(of: delegate)).contains("Canvas")
    }
  }

  private static func sublayerDelegateNames(of field: UITextField) -> [String] {
    (field.layer.sublayers ?? []).map { layer in
      layer.delegate.map { String(describing: type(of: $0)) } ?? String(describing: type(of: layer))
    }
  }

  private func updatePrivacyCover() {
    guard let windowScene = window?.windowScene else { return }

    let show = Self.screenCaptureBlocked && (inBackground || windowScene.screen.isCaptured)

    if show, privacyCoverWindow == nil {
      let coverWindow = UIWindow(windowScene: windowScene)
      coverWindow.windowLevel = .alert + 1
      coverWindow.rootViewController = Self.privacyCoverViewController()
      coverWindow.isHidden = false
      privacyCoverWindow = coverWindow
    } else if !show, let coverWindow = privacyCoverWindow {
      coverWindow.isHidden = true
      privacyCoverWindow = nil
    }
  }

  // Uses the boot splash screen as the cover so that it looks like a deliberate screen
  private static func privacyCoverViewController() -> UIViewController {
    if let vc = UIStoryboard(name: "BootSplash", bundle: nil).instantiateInitialViewController() {
      return vc
    }
    let vc = UIViewController()
    vc.view.backgroundColor = .systemBackground
    return vc
  }

  @objc private func screenCaptureChanged(_: Notification) {
    logger.debug("Screen capture changed")
    updatePrivacyCover()
  }

  func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
    // A new scene was added to the app.

    logger.debug("In SceneDelegate scene func...\(connectionOptions.urlContexts)")

    SceneDelegate.current = self

    // Posted when screen recording, AirPlay mirroring or similar capture starts or stops
    NotificationCenter.default.addObserver(self,
                                           selector: #selector(screenCaptureChanged(_:)),
                                           name: UIScreen.capturedDidChangeNotification,
                                           object: nil)
    
    // The url is passed when the user presses .kdbx file and the app is not running
    if let urlContext = connectionOptions.urlContexts.first {
      // As ReactContext is not yet, we cannot emit event to UI, we can store here
      // and UI side the open url is pulled
      SceneDelegate.openUrl = urlContext.url
      SceneDelegate.openUrlInPlace = urlContext.options.openInPlace
    }

    // Old way of setting rootViewController using AppDelegate's rctRootView
    // let rootViewController = UIViewController()
    // rootViewController.view = AppDelegate.rctRootView!

    
    let rootViewController = ReactViewController()

    if let windowScene = scene as? UIWindowScene {
      let window = UIWindow(windowScene: windowScene)
      window.rootViewController = rootViewController
      self.window = window // any use ? - It may be required because of UIWindowSceneDelegate
      window.makeKeyAndVisible()

      // Old way of initializing RNBootSplash using AppDelegate's rctRootView
      // RNBootSplash.initWithStoryboard("BootSplash", rootView: AppDelegate.rctRootView!)
      
      RNBootSplash.initWithStoryboard("BootSplash", rootView: rootViewController.view)
    }
  }
  
  // Following are from UIWindowSceneDelegate
  // See https://developer.apple.com/documentation/uikit/uiscenedelegate
  
  // This will be called when user presses .kdbx file and the app is in the background
  func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
    logger.debug("scene with openURLContexts called \(URLContexts)")
    
    if let urlContext = URLContexts.first {
      OkpEvents.calledWithUrl(urlContext.url, openInPlace: urlContext.options.openInPlace)
    }
    
    URLContexts.forEach { context in
      logger.debug("URLContexts are \(context.url)")
    }
  }
  
  func sceneWillEnterForeground(_ scene: UIScene) {
    // A scene is about to enter the foreground.
    // It appears all RN modules init are happening after this call only
    logger.debug("A scene is about to enter the foreground")

    inBackground = false
    updatePrivacyCover()
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

    // The app switcher snapshot is taken after this call. 'sceneWillResignActive' is not used for
    // the cover as it is also called for Face ID, Control Center and the notification pull-down
    inBackground = true
    updatePrivacyCover()
  }

  func sceneDidDisconnect(_ scene: UIScene) {
    logger.debug("sceneDidDisconnect...")
  }
}
