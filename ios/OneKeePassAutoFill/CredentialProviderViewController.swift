//
//  CredentialProviderViewController.swift
//  OneKeePassAutoFill
//
//  Created  on 6/6/24.
//

// https://reactnative.dev/docs/integration-with-existing-apps

import AuthenticationServices
import Foundation

@objc
class CredentialProviderViewController: ASCredentialProviderViewController, RCTBridgeDelegate {
  
  static let logger1 = OkpLogger(tag: "CredentialProviderViewController")
  
  static var extContext: ASCredentialProviderExtensionContext?
  
  static var serviceIdentifierDomain:String?
  static var serviceIdentifierUrl:String?
  
  private let logger = logger1
  
  // Need this inner class so that we can handle viewDidDisappear call and cancel the CredentialProviderViewController sheet
  class OkpViewController: UIViewController {
    override func viewDidLoad() {
      debugPrint("OkpViewController viewDidLoad is called")
    }
    
    override func viewDidDisappear(_ animated: Bool) {
      super.viewDidDisappear(animated)
      debugPrint("OkpViewController viewDidDisappear is called \(animated)")
      CredentialProviderViewController.cancelExtension()
    }
  }
  
  // ================================== //
  
  static func cancelExtension() {
    if extContext != nil {
      logger1.debug("Going to cancel the extension view...")
      extContext!.cancelRequest(withError: NSError(domain: ASExtensionErrorDomain, code: ASExtensionError.userCanceled.rawValue))
    }
    debugPrint("Extension view is cancelled")
  }
  
  static func credentialSelected(_ user: String, _ password: String) {
    guard extContext != nil else {
      logger1.error("The func credentialSelected but extContext is not set")
      return
    }
    
    let passwordCredential = ASPasswordCredential(user: user, password: password)
    extContext!.completeRequest(withSelectedCredential: passwordCredential, completionHandler: nil)
  }
 
  
  static func serviceIdentifiersReceived() -> [String: String]{
    var dict: [String: String] = [:]
    
    dict["domain"] = serviceIdentifierDomain
    dict["url"] = serviceIdentifierUrl
    
    return dict
  }
  
  // ================================== //
  
  override func viewDidLoad() {
    super.viewDidLoad()
    CredentialProviderViewController.extContext = extensionContext
    debugPrint("viewDidLoad is called")
  }
  
  override func viewWillDisappear(_ animated: Bool) {
    super.viewWillDisappear(animated)
    debugPrint("viewWillDisappear is called \(animated)")
  }
  
  override func viewDidDisappear(_ animated: Bool) {
    super.viewDidDisappear(animated)
    debugPrint("viewDidDisappear is called \(animated)")
  }
  
  override func viewWillAppear(_ animated: Bool) {
    super.viewWillAppear(animated)
    logger.debug("viewWillAppear is called")
    prepareUI()
  }

  override func didReceiveMemoryWarning() {
    super.didReceiveMemoryWarning()
    logger.debug("didReceiveMemoryWarning is called")
  }
  
  func getBundleURL() -> URL! {
    #if DEBUG
      // index.ios.autofill.extension.js is the extension specific entry file
      return RCTBundleURLProvider.sharedSettings()?.jsBundleURL(forBundleRoot: "index.ios.autofill.extension")
    #else
      // Same as return [[NSBundle mainBundle] URLForResource:@"main" withExtension:@"jsbundle"];
      return Bundle.main.url(forResource: "main", withExtension: "jsbundle")
    #endif
  }

  // This fn implementation is to conform to protocol 'RCTBridgeDelegate'
  func sourceURL(for bridge: RCTBridge!) -> URL! {
    getBundleURL()
  }

  func prepareUI() {
    let bridge = RCTBridge(delegate: self, launchOptions: nil)!

    let rootView = RCTRootView(
      bridge: bridge,
      moduleName: "OneKeePassMobile",
      initialProperties: nil
    )
    
    let vc = OkpViewController() // UIViewController()
    vc.view = rootView
    present(vc, animated: true, completion: nil)
  }

  /*
    Prepare your UI to list available credentials for the user to choose from. The items in
    'serviceIdentifiers' describe the service the user is logging in to, so your extension can
    prioritize the most relevant credentials in the list.
   */
  override func prepareCredentialList(for serviceIdentifiers: [ASCredentialServiceIdentifier]) {
    logger.debug("prepareCredentialList is called")
    for si in serviceIdentifiers {
      switch si.type {
      case .domain:
        logger.debug("Domain identified \(si.identifier)")
        CredentialProviderViewController.serviceIdentifierDomain = si.identifier
      case .URL:
        logger.debug("Url identified \(si.identifier)")
        CredentialProviderViewController.serviceIdentifierUrl = si.identifier
      @unknown default:
        logger.debug("Unknown identifier \(si.type)")
      }
    }
    // prepareUI()
  }

  /*
    Implement this method if your extension supports showing credentials in the QuickType bar.
    When the user selects a credential from your app, this method will be called with the
    ASPasswordCredentialIdentity your app has previously saved to the ASCredentialIdentityStore.
    Provide the password by completing the extension request with the associated ASPasswordCredential.
    If using the credential would require showing custom UI for authenticating the user, cancel
    the request with error code ASExtensionError.userInteractionRequired.

   override func provideCredentialWithoutUserInteraction(for credentialIdentity: ASPasswordCredentialIdentity) {
       let databaseIsUnlocked = true
       if (databaseIsUnlocked) {
           let passwordCredential = ASPasswordCredential(user: "j_appleseed", password: "apple1234")
           self.extensionContext.completeRequest(withSelectedCredential: passwordCredential, completionHandler: nil)
       } else {
           self.extensionContext.cancelRequest(withError: NSError(domain: ASExtensionErrorDomain, code:ASExtensionError.userInteractionRequired.rawValue))
       }
   }
   */

  /*
    Implement this method if provideCredentialWithoutUserInteraction(for:) can fail with
    ASExtensionError.userInteractionRequired. In this case, the system may present your extension's
    UI and call this method. Show appropriate UI for authenticating the user then provide the password
    by completing the extension request with the associated ASPasswordCredential.

   override func prepareInterfaceToProvideCredential(for credentialIdentity: ASPasswordCredentialIdentity) {
   }
   */

  /*
   @IBAction func cancel(_ sender: AnyObject?) {
     logger.debug("Cancel is called")
     extensionContext.cancelRequest(withError: NSError(domain: ASExtensionErrorDomain, code: ASExtensionError.userCanceled.rawValue))
   }

   @IBAction func passwordSelected(_ sender: AnyObject?) {
     let passwordCredential = ASPasswordCredential(user: "j_appleseed", password: "apple1234")
     extensionContext.completeRequest(withSelectedCredential: passwordCredential, completionHandler: nil)
   }
  
   @IBAction func showRN(_ sender: AnyObject?) {
     prepareUI()
    
   }
   */
}

/*** ========== ===== ===== ===== ===== ===== ===== ===== ===== ===== ===== ===== ===== ===== ===== ===== ===== ===== =====  **/
/*
 // Use NSExtensionMainStoryboard just to test the basic autofill testing
 // Need to uncomment all @IBAction

 <key>NSExtension</key>
 		<dict>
 			<key>NSExtensionMainStoryboard</key>
 			<string>MainInterface</string>
			
       <!--		<key>NSExtensionPrincipalClass</key> -->
 			<!--		<string>$(PRODUCT_MODULE_NAME).CredentialProviderViewController</string> -->
 			<!--		<key>NSExtensionPointIdentifier</key> -->
 			<!--		<string>com.apple.authentication-services-credential-provider-ui</string> -->
 		</dict>
 */
