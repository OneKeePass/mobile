//
//  OkpDbService.swift
//  OneKeePassAutoFill
//
//  Created  on 6/14/24.
//

import Foundation
import LocalAuthentication

// Only few of the funcs implemented for AutoFill calls

@objc(OkpDbService)
class OkpDbService: NSObject {
  private let logger = OkpLogger(tag: "OkpDbService")
  
  override init() {
    logger.debug("Going to call initialize from autofill OkpDbService class using AutoFillDbServiceAPI.initialize")
    AutoFillDbServiceAPI.initialize()
  }

  @objc
  static func requiresMainQueueSetup() -> Bool {
    return true
  }
  
  @objc func constantsToExport() -> [AnyHashable: Any] {
    // getLocale()
    let b = availableBiometricType() == 0 ? "false" : "true"
    return [
      "CacheDir": NSSearchPathForDirectoriesInDomains(.cachesDirectory, .userDomainMask, true).first!,
      "DocumentDir": NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true).first!,
      "LibraryDir": NSSearchPathForDirectoriesInDomains(.libraryDirectory, .userDomainMask, true).first!,
      "MainBundleDir": Bundle.main.bundlePath,
      "Country": Locale.current.regionCode ?? "NONE", // Device country
      "Language": Locale.preferredLanguages.first!, // Device level language
      "BiometricAvailable": b
    ]
  }
  
  @objc
  func invokeCommand(_ commandName: String, args: String,
                     resolve: @escaping RCTPromiseResolveBlock,
                     reject _: @escaping RCTPromiseRejectBlock)
  {
    DispatchQueue.global(qos: .userInteractive).async { [unowned self] in
      logger.debug("InvokeCommand for \(commandName) called with args \(args) and delegating to api call")
      //resolve(OneKeePassAutoFill.invokeCommand(commandName, args))
      resolve(AutoFillDbServiceAPI.invokeCommand(commandName, args))
    }
  }
  
  /// All app group realted API calls
  
  @objc
  func autoFillInvokeCommand(_ commandName: String, args: String, resolve: @escaping RCTPromiseResolveBlock,reject _: @escaping RCTPromiseRejectBlock)
  {
    DispatchQueue.global(qos: .userInteractive).async { [unowned self] in
      logger.debug("InvokeCommand for \(commandName) called with args \(args) and delegating to api call")
      resolve(AutoFillDbServiceAPI.iosAppGroupSupportService().invoke(commandName, args))
    }
  }
  
  @objc
  func cancelExtension(_ resolve:@escaping RCTPromiseResolveBlock, reject _: @escaping RCTPromiseRejectBlock) {
    
    logger.debug("OkpDbService: cancelExtension is called")
    CredentialProviderViewController.cancelExtension()
    resolve("{}")
  }
  
  @objc
  func credentialSelected(_ user:String, password:String,resolve: @escaping RCTPromiseResolveBlock,reject _: @escaping RCTPromiseRejectBlock) {
    logger.debug("credentialSelected is called ")
    CredentialProviderViewController.credentialSelected(user, password)
  }

  // Returns JSON with the pending passkey assertion context (rpId + allowCredentialIds),
  // or {"ok":null} if this session was not triggered by a passkey assertion request.
  // Called by ClojureScript at startup to decide whether to show the passkey flow.
  @objc
  func getPendingPasskeyContext(_ resolve: @escaping RCTPromiseResolveBlock,
                                reject _: @escaping RCTPromiseRejectBlock) {
    guard #available(iOS 17.0, *),
          let rpId = CredentialProviderViewController.pendingPasskeyRpId else {
      resolve("{\"ok\":null}")
      return
    }
    let allowIds: [String] = CredentialProviderViewController.pendingPasskeyCredentialIds
      .map { $0.base64URLEncodedString() }
    let idsJson = allowIds.isEmpty
      ? "[]"
      : "[" + allowIds.map { "\"\($0)\"" }.joined(separator: ",") + "]"
    resolve("{\"ok\":{\"rp_id\":\"\(rpId)\",\"allow_credential_ids\":\(idsJson)}}")
  }

  // Returns a JSON array of passkeys matching the given relying-party ID and optional allow-list.
  @objc
  func findMatchingPasskeys(_ rpId: String, allowCredentialIds: [String],
                            resolve: @escaping RCTPromiseResolveBlock,
                            reject _: @escaping RCTPromiseRejectBlock) {
    DispatchQueue.global(qos: .userInteractive).async {
      let idsJson = allowCredentialIds.isEmpty
        ? "[]"
        : "[" + allowCredentialIds.map { "\"\($0)\"" }.joined(separator: ",") + "]"
      let args = "{\"rp_id\":\"\(rpId)\",\"allow_credential_ids\":\(idsJson)}"
      resolve(AutoFillDbServiceAPI.iosAppGroupSupportService().invoke("passkey_find_matching", args))
    }
  }

  // Signs the pending passkey assertion and completes the extension request.
  @objc
  func completePasskeyAssertion(_ entryUuid: String, dbKey: String,
                                resolve: @escaping RCTPromiseResolveBlock,
                                reject _: @escaping RCTPromiseRejectBlock) {
    
    logger.debug("Passkey completePasskeyAssertion is called for entryUuid: \(entryUuid)")
    
    guard #available(iOS 17.0, *) else {
      resolve("{}")
      return
    }
    guard let clientDataHash = CredentialProviderViewController.pendingPasskeyClientDataHash else {
      resolve("{\"error\":\"No pending passkey request\"}")
      return
    }
    DispatchQueue.global(qos: .userInteractive).async {
      let hashB64url = clientDataHash.base64URLEncodedString()
      let args = "{\"db_key\":\"\(dbKey)\",\"entry_uuid\":\"\(entryUuid)\",\"client_data_hash_b64url\":\"\(hashB64url)\"}"
      
      self.logger.debug("Invoking passkey_sign_assertion")
      
      let resultJson = AutoFillDbServiceAPI.iosAppGroupSupportService().invoke("passkey_sign_assertion", args)
      
      self.logger.debug("Rust passkey_sign_assertion returns \(resultJson)")
      
      if let data = resultJson.data(using: .utf8),
         let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
         let ok = dict["ok"] as? [String: Any] {

        let returnedCredId = ok["credential_id_b64url"] as? String ?? ""
        let expectedIds = CredentialProviderViewController.pendingPasskeyCredentialIds
          .map { $0.base64URLEncodedString() }
        self.logger.debug("Rust returned credentialId: \(returnedCredId)")
        self.logger.debug("iOS allowedCredentialIds: \(expectedIds)")
        self.logger.debug("OS rpId: \(CredentialProviderViewController.pendingPasskeyRpId ?? "nil"), Rust rpId: \(ok["rp_id"] as? String ?? "nil")")

        let rpId = CredentialProviderViewController.pendingPasskeyRpId ?? ok["rp_id"] as? String ?? ""
        let userHandle = ok["user_handle_b64url"] as? String ?? ""
        let signature = ok["signature_b64url"] as? String ?? ""
        let authData = ok["authenticator_data_b64url"] as? String ?? ""
        DispatchQueue.main.async {
          CredentialProviderViewController.completePasskeyAssertion(
            credentialIdB64url: returnedCredId,
            userHandleB64url: userHandle,
            signatureB64url: signature,
            authenticatorDataB64url: authData,
            rpId: rpId
          )
        }
      }
      resolve(resultJson)
    }
  }
  
  
  
  /// TDODO: Need to move authenticateWithBiometric and getAuthenticationErrorDescription to a common class and share between app and autofill

  
  @objc
  func authenticateWithBiometric(_ resolve: @escaping RCTPromiseResolveBlock,
                                 reject _: @escaping RCTPromiseRejectBlock)
  {
    DispatchQueue.global(qos: .userInteractive).async { [unowned self] in
      let localAuthenticationContext = LAContext()
      let reason = "Authentication is required to unlock database"
      
      localAuthenticationContext.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason) { success, evaluationError in
        if success {
          // authenticated = success
          resolve(AutoFillDbServiceAPI.jsonService().okJsonString("AuthenticationSucceeded"))
        } else {
          self.logger.error("Error \(evaluationError!)")
          if let errorObj = evaluationError {
            let messageToDisplay = self.getAuthenticationErrorDescription(errorCode: errorObj._code)
            self.logger.error(messageToDisplay)
          }
          // For now, we use "AuthenticationFailed" whether user cancels the bio auth or any error in bio api call
          resolve(AutoFillDbServiceAPI.jsonService().okJsonString("AuthenticationFailed"))
        }
      }
    }
  }
  
  func getAuthenticationErrorDescription(errorCode: Int) -> String {
    switch errorCode {
    case LAError.authenticationFailed.rawValue:
      return "Authentication was not successful, because user failed to provide valid credentials."
      
    case LAError.appCancel.rawValue:
      return "Authentication was canceled by application (e.g. invalidate was called while authentication was in progress)."
      
    case LAError.invalidContext.rawValue:
      return "LAContext passed to this call has been previously invalidated."
      
    case LAError.notInteractive.rawValue:
      return "Authentication failed, because it would require showing UI which has been forbidden by using interactionNotAllowed property."
      
    case LAError.passcodeNotSet.rawValue:
      return "Authentication could not start, because passcode is not set on the device."
      
    case LAError.systemCancel.rawValue:
      return "Authentication was canceled by system (e.g. another application went to foreground)."
      
    case LAError.userCancel.rawValue:
      return "Authentication was canceled by user (e.g. tapped Cancel button)."
      
    case LAError.userFallback.rawValue:
      return "Authentication was canceled, because the user tapped the fallback button (Enter Password)."
      
    default:
      return "Error code \(errorCode) not found"
    }
  }
  
  
  func availableBiometricType() -> Int {
    let localAuthenticationContext = LAContext()
    var authorizationError: NSError?
    var supportedType: Int?

    if localAuthenticationContext.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &authorizationError) {
      switch localAuthenticationContext.biometryType {
      case .faceID:
        logger.info("Supported Biometric type is: faceID")
        supportedType = 2
      case .touchID:
        logger.info("Supported Biometric type is: touchID")
        supportedType = 1
      case .none:
        logger.info("No biometeric")
        supportedType = 0
      @unknown default:
        logger.info("@unknown biometeric")
        supportedType = 0
      }
    }

    if authorizationError != nil {
      logger.error("authorizationError is \(String(describing: authorizationError))")
      return 0
    }
    return supportedType ?? 0
  }
}
