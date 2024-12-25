//
//  OkpDbService.swift
//  OneKeePassAutoFill
//
//  Created  on 6/14/24.
//

import Foundation
import LocalAuthentication

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
  
  //read_kdbx_from_app_group
  
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
