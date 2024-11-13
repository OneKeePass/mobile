//
//  SecureKeyService.swift
//  OneKeePassMobile
//
//  Created by on 6/23/23.
//

import Foundation

class SecureKeyOperationImpl: SecureKeyOperation {
  
  static var shared:SecureKeyOperationImpl = SecureKeyOperationImpl();
  
  func storeKey(_ dbKey: String, _ encKeyData: String) throws {
    let kv = encKeyData.data(using: String.Encoding.utf8)!
    let query: [String: AnyObject] = [
      // kSecAttrService,  kSecAttrAccount, and kSecClass
      // uniquely identify the item to save in Keychain
      // kSecAttrService as String: "OKP-Service' as AnyObject,
      kSecAttrAccount as String: dbKey as AnyObject,
      kSecClass as String: kSecClassGenericPassword,

      kSecAttrSynchronizable as String: kCFBooleanFalse,

      // kSecValueData is the item value to save
      kSecValueData as String: kv as AnyObject
    ]

    let status: OSStatus = SecItemAdd(query as CFDictionary, nil)

    if status < 0 {
      cmnLogger.error("Store key call failed with OSStatus \(status)")
    }

    if let error = status.error {
      cmnLogger.error("SecItemAdd error is \(error)")
      if status == errSecDuplicateItem {
        throw SecureKeyOperationError.StoringKeyDuplicateItemError(message: "errSecDuplicateItem")
      } else {
        throw SecureKeyOperationError.StoringKeyError(message: error.description)
      }
    }
    cmnLogger.debug("Stored key suucessfully in Keychain")
  }

  func deleteKey(_ dbKey: String) throws {
    let query: [String: AnyObject] =
      [
        kSecAttrAccount as String: dbKey as AnyObject,
        kSecClass as String: kSecClassGenericPassword
      ]

    let status: OSStatus = SecItemDelete(query as CFDictionary)
    
//    if status < 0 {
//      cmnLogger.error("Delete Key call failed with OSStatus \(status)")
//    }

    if let error = status.error {
      cmnLogger.error("SecItemDelete error is \(error)")
      throw SecureKeyOperationError.DeleteKeyError(message: error.description)
    }

    cmnLogger.debug("Delete Key call completed successfully")
  }

  func getKey(_ dbKey: String) throws -> String? {
    let query: [String: AnyObject] = [
      // kSecAttrService,  kSecAttrAccount, and kSecClass
      // uniquely identify the item to read in Keychain

      kSecAttrAccount as String: dbKey as AnyObject,
      kSecClass as String: kSecClassGenericPassword,

      // kSecMatchLimitOne indicates keychain should read
      // only the most recent item matching this query
      kSecMatchLimit as String: kSecMatchLimitOne,

      // kSecReturnData is set to kCFBooleanTrue in order
      // to retrieve the data for the item
      // the search returns a CFData instance that holds the actual data in 'itemCopy'.
      kSecReturnData as String: kCFBooleanTrue
    ]

    // SecItemCopyMatching will attempt to copy the item
    // identified by query to the reference itemCopy
    var itemCopy: AnyObject?
    let status = SecItemCopyMatching(
      query as CFDictionary,
      &itemCopy
    )
    
    if let error = status.error {
      cmnLogger.error("Get key error is \(error)")
      throw SecureKeyOperationError.QueryKeyError(message: error.description)

    } else {
      // itemCopy is "CFTypeRef  _Nullable *result" in Objective-C or "UnsafeMutablePointer<CFTypeRef?>?" in Swift
      // See https://developer.apple.com/documentation/security/keychain_services/keychain_items/item_return_result_keys

      // See https://stackoverflow.com/questions/37539997/save-and-load-from-keychain-swift

      // This also works
      // let str = String(decoding: itemCopy as! Data, as: UTF8.self)

      // cast the copied value from key chain as NSData using 'if let'
      // If this is successful, retrievedData will be of 'NSData' object and we can get the String from that
      // using NSUTF8StringEncoding
      if let retrievedData = itemCopy as? NSData {
        let contentsOfKeychain = NSString(data: retrievedData as Data, encoding: NSUTF8StringEncoding)
        cmnLogger.info("contentsOfKeychain  \(String(describing: contentsOfKeychain))")
        return contentsOfKeychain as? String
        // return SRString(contentsOfKeychain as? String ??  "Error:StringConversion error" )
      } else {
        return nil
      }
    }
  }
}

extension OSStatus {
  var error: NSError? {
    guard self != errSecSuccess else { return nil }

    let message = SecCopyErrorMessageString(self, nil) as String? ?? "Unknown error"

    return NSError(domain: NSOSStatusErrorDomain, code: Int(self), userInfo: [
      NSLocalizedDescriptionKey: message
    ])
  }

  func getErrorCodeDecription() -> String {
    // error code type is Int32
    // See
    switch self {
    case errSecDuplicateItem:
      return "errSecDuplicateItem"
    // case errKCDuplicateItem :
    //    return "errSecDuplicateItem"
    default:
      return "Some error"
    }
  }
}
