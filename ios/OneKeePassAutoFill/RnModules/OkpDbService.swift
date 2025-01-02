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
    
    logger.debug("cancelExtension is called")
    CredentialProviderViewController.cancelExtension()
    resolve("{}")
  }
  
  @objc
  func credentialSelected(_ user:String, password:String,resolve: @escaping RCTPromiseResolveBlock,reject _: @escaping RCTPromiseRejectBlock) {
    logger.debug("credentialSelected is called ")
    CredentialProviderViewController.credentialSelected(user, password)
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
