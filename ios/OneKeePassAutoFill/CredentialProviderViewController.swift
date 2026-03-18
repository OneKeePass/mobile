//
//  CredentialProviderViewController.swift
//  OneKeePassAutoFill
//
//  Created  on 6/6/24.
//

// https://reactnative.dev/docs/integration-with-existing-apps

import AuthenticationServices
import Foundation

// Need to add this if we include 'OkpAutofillReactViewController.swift' in the project even if
// we are  using RCTBridge only. Otherwise the build fails not seeing 'RCTBridgeDelegate'
// import React_RCTAppDelegate

// class CredentialProviderViewController: ASCredentialProviderViewController, RCTBridgeDelegate

@objc
class CredentialProviderViewController: ASCredentialProviderViewController {
  static let logger1 = OkpLogger(tag: "CredentialProviderViewController")
  
  static var extContext: ASCredentialProviderExtensionContext?
  
  // Hold values identified by ASCredentialServiceIdentifier.IdentifierType
  static var serviceIdentifierDomain: String?
  static var serviceIdentifierUrl: String?
  // This is not yet used
  static var serviceIdentifierDisplayName: String?
  
  static var cancelled: Bool = false

  // Passkey assertion state (iOS 17+)
  static var pendingPasskeyRpId: String?
  static var pendingPasskeyCredentialIds: [Data] = []
  static var pendingPasskeyClientDataHash: Data?

  private let logger = logger1
  
  //==============================================================================================================//
  /*
   // Need this inner class so that we can handle viewDidDisappear call and cancel the CredentialProviderViewController sheet
   class OkpViewController: UIViewController {
     override func viewDidLoad() {
       debugPrint("OkpViewController viewDidLoad is called")
     }
    
     override func viewDidDisappear(_ animated: Bool) {
       super.viewDidDisappear(animated)
       debugPrint("OkpViewController viewDidDisappear is called \(animated)")
       // The following will be called in the CredentialProviderViewController's viewDidDisappear fn when its view
       // is set directly instead of the 'present' call.
       // If this UIViewController is used, then uncomment this and comment the other one
       //CredentialProviderViewController.cancelExtension()
     }
   }
  
   // RCTBridgeDelegate implementation
   // This class needs to extend RCTBridgeDelegate so that we use RCTBridge to create the RCTRootView (Legacy architecture)
   func getBundleURL() -> URL! {
     #if DEBUG
       // index.ios.autofill.extension.js is the extension specific entry file
       return RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot: "index.ios.autofill.extension")
     #else
       // Same as return [[NSBundle mainBundle] URLForResource:@"main" withExtension:@"jsbundle"];
       return Bundle.main.url(forResource: "main", withExtension: "jsbundle")
     #endif
   }

   // This fn implementation is to conform to the protocol 'RCTBridgeDelegate'
   func sourceURL(for bridge: RCTBridge) -> URL? {
     getBundleURL()
   }
   */
  
  // ====================================================================================================== //
  
  static func cancelExtension() {
    logger1.debug("CredentialProviderViewController - In cancelExtension fn and  current cancelled state is \(cancelled) and extContext is \(String(describing: extContext))")
    
    if extContext != nil, !cancelled {
      logger1.debug("CredentialProviderViewController - Going to cancel the extension view...")
      extContext!.cancelRequest(withError: NSError(domain: ASExtensionErrorDomain, code: ASExtensionError.userCanceled.rawValue))
      cancelled = true
      logger1.debug("CredentialProviderViewController -Extension view is cancelled")
    } else {
      logger1.debug("CredentialProviderViewController - Cancelation is not needed/called")
    }
  }
  
  static func credentialSelected(_ user: String, _ password: String) {
    guard extContext != nil else {
      logger1.error("CredentialProviderViewController - The func credentialSelected but extContext is not set")
      return
    }
    
    let passwordCredential = ASPasswordCredential(user: user, password: password)
    extContext!.completeRequest(withSelectedCredential: passwordCredential, completionHandler: nil)
  }
 
  @available(iOS 17.0, *)
  static func completePasskeyAssertion(
    credentialIdB64url: String,
    userHandleB64url: String,
    signatureB64url: String,
    authenticatorDataB64url: String,
    rpId: String
  ) {
    
    guard let ctx = extContext,
          let credentialIdData = Data(base64URLEncoded: credentialIdB64url),
          let userHandleData = Data(base64URLEncoded: userHandleB64url),
          let signatureData = Data(base64URLEncoded: signatureB64url),
          let authData = Data(base64URLEncoded: authenticatorDataB64url),
          let clientDataHash = pendingPasskeyClientDataHash
    else {
      logger1.error("CredentialProviderViewController - completePasskeyAssertion: missing context or could not decode base64url data")
      return
    }

    let credential = ASPasskeyAssertionCredential(
      userHandle: userHandleData,
      relyingParty: rpId,
      signature: signatureData,
      clientDataHash: clientDataHash,
      authenticatorData: authData,
      credentialID: credentialIdData
    )
    
    logger1.debug("CredentialProviderViewController -  ASPasskeyAssertionCredential formed successfully \(credential) and sent to app")

    cancelled = true  // Prevent viewDidDisappear's cancelExtension from racing with this call
    Task { @MainActor in
      logger1.debug("CredentialProviderViewController -  completeAssertionRequest Task started — calling completeAssertionRequest")
      await ctx.completeAssertionRequest(using: credential)
      logger1.debug("CredentialProviderViewController - completeAssertionRequest Task completed")
    }
  }

  static func serviceIdentifiersReceived() -> [String: String] {
    var dict: [String: String] = [:]
    
    dict["domain"] = serviceIdentifierDomain
    dict["url"] = serviceIdentifierUrl
    
    return dict
  }
  
  // ====================================================================================================== //
  
  override func viewDidLoad() {
    super.viewDidLoad()
    CredentialProviderViewController.extContext = extensionContext
    debugPrint("CredentialProviderViewController - viewDidLoad is called")
  }
  
  override func viewWillDisappear(_ animated: Bool) {
    super.viewWillDisappear(animated)
    debugPrint("CredentialProviderViewController- viewWillDisappear is called \(animated)")
  }
  
  override func viewDidDisappear(_ animated: Bool) {
    super.viewDidDisappear(animated)
    logger.debug("CredentialProviderViewController - viewDidDisappear is called \(animated)")
    // This needs to be added here when use this class's view directly
    // CredentialProviderViewController.cancelExtension()
  }
  
  override func viewWillAppear(_ animated: Bool) {
    super.viewWillAppear(animated)
    logger.debug("CredentialProviderViewController - viewWillAppear is called")
    // This was used when we used the inner clsss 'OkpViewController' till RN 0.78.2
    // This is commented out if  we set this class controllers's view directly
    // See prepareCredentialList where this is called when we set the view directly instead of using the custom controller
    if !Self.cancelled {
      prepareUI()
    }
  }

  override func didReceiveMemoryWarning() {
    super.didReceiveMemoryWarning()
    logger.info("CredentialProviderViewController - didReceiveMemoryWarning is called")
  }
  
  // Following used till RN 0.78.2
  // This also worked with RN 0.81.5. But will work only leagacy architecture as RCTBridge is supported only for leagacy architecture
  
  /*
   func prepareUI() {
     let bridge = RCTBridge(delegate: self, launchOptions: nil)!
    
     let rootView = RCTRootView(
       bridge: bridge,
       moduleName: "OneKeePassMobile",
       initialProperties: nil
     )
    
     let vc = OkpViewController() // a custom UIViewController
     vc.view = rootView
     present(vc, animated: true, completion: nil)
   }
   */
  
  // This works with RN 0.81.5. But only with leagacy architecture and with new architecture the texts and buttons do not show
  // Issue may be something similar to the one raised here
  // https://github.com/facebook/react-native/issues/54642
  
  func prepareUI() {
    logger.debug("CredentialProviderViewController - prepareUI is called")
    let vc = OkpReactViewController()
    present(vc, animated: true, completion: nil)
  }
  
  // This is another way of adding the RN view directly as a sub view
  // This was checked with RN 0.81.5 and works with only with Legacy architecture
  /*
   func directUI() {
     let rnView = OKpReactView.createOkpAutofillReactView()
     rnView.frame = view.bounds
     rnView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
     // rnView.backgroundColor = .black
     // This class is a sub class of UIViewController. So we can use the ReactView created as its view
     view.addSubview(rnView)
   }
   */
  
  // This works with RN 0.81.5 legacy architecture but shows blank screen when new architecture is enabled
  /*
   func prepareUI() {
     // This class is a sub class of UIViewController. So we can use the ReactView created as its view
     view = OKpReactView.createOkpAutofillReactView()
   }
   */
  
  /*
    Prepare your UI to list available credentials for the user to choose from. The items in
    'serviceIdentifiers' describe the service the user is logging in to, so your extension can
    prioritize the most relevant credentials in the list.
   */
  override func prepareCredentialList(for serviceIdentifiers: [ASCredentialServiceIdentifier]) {
    logger.debug("CredentialProviderViewController - prepareCredentialList for password auth is called")
    
    Self.cancelled = false

    CredentialProviderViewController.serviceIdentifierDomain = nil
    CredentialProviderViewController.serviceIdentifierUrl = nil
    CredentialProviderViewController.serviceIdentifierDisplayName = nil
    
    for si in serviceIdentifiers {
      switch si.type {
      case .domain:
        logger.debug("CredentialProviderViewController - Domain identified \(si.identifier)")
        CredentialProviderViewController.serviceIdentifierDomain = si.identifier
      
      case .URL:
        logger.debug("CredentialProviderViewController -Url identified \(si.identifier)")
        CredentialProviderViewController.serviceIdentifierUrl = si.identifier
      
      case .app:
        // Not yet used
        if #available(iOS 26.2, *) {
          logger.debug("CredentialProviderViewController - App displayname \(String(describing: si.displayName))")
          CredentialProviderViewController.serviceIdentifierDisplayName = si.displayName
        }
      
      @unknown default:
        logger.debug("CredentialProviderViewController - Unknown identifier \(si.type)")
      }
    }
    // Calling these from here also worked. See viewWillAppear where the view is called
    // directUI()
    // prepareUI()
  }

  // iOS 17+ passkey assertion entry point via the credential list (no pre-registered identities needed).
  // Called when the user triggers a WebAuthn assertion on a site and iOS presents the credential picker.
  // Stores the passkey context so the ClojureScript layer can route to the passkey selection page.
  @available(iOS 17.0, *)
  override func prepareCredentialList(
    for serviceIdentifiers: [ASCredentialServiceIdentifier],
    requestParameters: ASPasskeyCredentialRequestParameters
  ) {
    logger.debug("CredentialProviderViewController - prepareCredentialList(requestParameters:) for passkey auth is called — passkey assertion for rpId: \(requestParameters.relyingPartyIdentifier)")
    Self.cancelled = false
    Self.pendingPasskeyRpId = requestParameters.relyingPartyIdentifier
    Self.pendingPasskeyClientDataHash = requestParameters.clientDataHash
    Self.pendingPasskeyCredentialIds = requestParameters.allowedCredentials
    prepareUI()
  }

  // Called by iOS after provideCredentialWithoutUserInteraction returns userInteractionRequired.
  // Sets up the passkey assertion state and launches the extension UI so the user can
  // unlock the database and select the passkey.
  @available(iOS 17.0, *)
  override func prepareInterfaceToProvideCredential(for credentialRequest: any ASCredentialRequest) {
    logger.debug("CredentialProviderViewController - prepareInterfaceToProvideCredential for passkey auth called")
    Self.cancelled = false

    guard let request = credentialRequest as? ASPasskeyCredentialRequest,
          let identity = request.credentialIdentity as? ASPasskeyCredentialIdentity
    else {
      logger.debug("CredentialProviderViewController - prepareInterfaceToProvideCredential: not a passkey request")
      return
    }

    Self.pendingPasskeyRpId = identity.relyingPartyIdentifier
    Self.pendingPasskeyClientDataHash = request.clientDataHash
    Self.pendingPasskeyCredentialIds = [identity.credentialID]
    logger.debug("CredentialProviderViewController - prepareInterfaceToProvideCredential: passkey for rpId=\(identity.relyingPartyIdentifier)")
    //prepareUI()
  }

  // Called by iOS when it tries to provide a registered passkey identity without showing UI.
  // Since OneKeePass requires the user to unlock the database, always request user interaction
  // so iOS falls through to prepareInterfaceToProvideCredential.
  @available(iOS 17.0, *)
  override func provideCredentialWithoutUserInteraction(for credentialRequest: any ASCredentialRequest) {
    logger.debug("CredentialProviderViewController - provideCredentialWithoutUserInteraction called — requiring user interaction")
    extensionContext.cancelRequest(withError: NSError(
      domain: ASExtensionErrorDomain,
      code: ASExtensionError.userInteractionRequired.rawValue))
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

//  func prepareUI() {
//    let bridge = RCTBridge(delegate: self, launchOptions: nil)!
//
//    let rootView = RCTAppSetupDefaultRootView(bridge, "OneKeePassMobile",nil,false)
//    rootView?.backgroundColor = .systemBackground
//    // let vc = OkpViewController()
//    self.view = rootView
//    // present(self, animated: true, completion: nil)
//  }
  
//  func prepareUI() {
//    let bridge = RCTBridge(delegate: self, launchOptions: nil)!
//
//
//
//    let cf = RCTRootViewFactoryConfiguration(bundleURL:self.getBundleURL(), newArchEnabled: true )
//    let rc = RCTRootViewFactory(configuration: cf)
//
//
//
//    // let rc = RCTRootViewFactory()
//    // rc.bridge = bridge
//
//    let rootView = rc.view(withModuleName: "OneKeePassMobile", initialProperties: nil)
//
//    //let rootView = RCTAppSetupDefaultRootView(bridge, "OneKeePassMobile",nil,false)
//    //rootView?.backgroundColor = .systemBackground
//    // let vc = OkpViewController()
//
//    self.view = rootView
//    // present(self, animated: true, completion: nil)
//  }

// Decodes a base64url-encoded string (no padding, using - and _ instead of + and /) into Data.
extension Data {
  init?(base64URLEncoded string: String) {
    var base64 = string
      .replacingOccurrences(of: "-", with: "+")
      .replacingOccurrences(of: "_", with: "/")
    let remainder = base64.count % 4
    if remainder != 0 {
      base64 += String(repeating: "=", count: 4 - remainder)
    }
    self.init(base64Encoded: base64)
  }

  func base64URLEncodedString() -> String {
    return base64EncodedString()
      .replacingOccurrences(of: "+", with: "-")
      .replacingOccurrences(of: "/", with: "_")
      .replacingOccurrences(of: "=", with: "")
  }
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
