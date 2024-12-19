//
//  SecureEnclaveServiceSupport.swift
//  OneKeePassMobile
//
//  Created on 11/10/24.
//

class SecureEnclaveServiceSupport: SecureEnclaveCbService {
  let logger = OkpLogger(tag: "SecureEnclaveServiceSupport")
  
  private let ALOGRITHM = SecKeyAlgorithm.eciesEncryptionStandardVariableIVX963SHA256AESGCM
  
  private let KEY_TYPE = kSecAttrKeyTypeECSECPrimeRandom
  
  // Data("Okp-Secure-Store-Key".utf8) is equivalent to
  // Objective C (NSData *)dataUsingEncoding:(NSStringEncoding)encoding;
  // e.g [someNSstringPointer dataUsingEncoding:NSUTF8StringEncoding]
  
  private let SECURE_STORE_LABEL_AS_DATA = Data("Okp-Secure-Store-Key".utf8) // "Okp-Secure-Store-Key".data(using: .utf8)!
  
  func encryptBytes(_ identifier: String, _ plainData: Data) throws -> Data {
    let privateKey = try createEncryptionKey(identifier)
    
    // func SecKeyCopyPublicKey(_ key: SecKey) -> SecKey?
    let publicKey = SecKeyCopyPublicKey(privateKey)
    
    guard publicKey != nil else {
      throw ApiCallbackError.InternalCallbackError(reason: "No public key is found from the enclave")
    }
    
    let algSupported = SecKeyIsAlgorithmSupported(publicKey!, .encrypt, ALOGRITHM)
    guard algSupported else {
      throw ApiCallbackError.InternalCallbackError(reason: "Algorithm for encryption is not supported")
    }
    
    var error: Unmanaged<CFError>?
    let cipherData: CFData? = SecKeyCreateEncryptedData(publicKey!, ALOGRITHM, plainData as CFData, &error)
    
    guard cipherData != nil else {
      throw ApiCallbackError.InternalCallbackError(reason: "Encryption output is empty")
    }
    
    return cipherData! as Data
  }
  
  func decryptBytes(_ identifier: String, _ encryptedData: Data) throws -> Data {
    let privateKey = try createEncryptionKey(identifier)
    
    guard SecKeyIsAlgorithmSupported(privateKey, .decrypt, ALOGRITHM) else {
      throw ApiCallbackError.InternalCallbackError(reason: "Algorithm for decryption is not supported")
    }
    
    var error: Unmanaged<CFError>?
    guard let decryptedData = SecKeyCreateDecryptedData(privateKey, ALOGRITHM, encryptedData as CFData, &error) else {
      throw ApiCallbackError.InternalCallbackError(reason: "Decryption output is empty")
    }
    
    return decryptedData as Data
  }
  
  func removeKey(_ identifier: String) throws -> Bool {
    let query = prepareQueryAttributes(identifier)
    
    let status: OSStatus = SecItemDelete(query as CFDictionary)
    
    if let error = status.error {
      logger.error("Removing key for the identifier \(identifier) failed wirth error \(error)")
      return false
    }
    
    return true
  }
  
  // Called to create a key
  private func createEncryptionKey(_ identifier: String) throws -> SecKey {
    // Find out if the key is already created and then use it
    let privateKey = try getPrivateKey(identifier)
    
    if privateKey != nil {
      logger.debug("Previously generated key is returned")
      return privateKey!
    } else {
      let attributes = try prepareKeyCreationttributes(identifier)
      
      var error: Unmanaged<CFError>?
      
      guard let privateKey: SecKey = SecKeyCreateRandomKey(attributes, &error) else {
        let e = error!.takeRetainedValue() as Error
        logger.error("SecKeyCreateRandomKey call failed \(e.localizedDescription) ")
        throw e
      }
      
      logger.debug("SecKeyCreateRandomKey call is success and a new key is created for  \(identifier)")
      
      return privateKey
    }
  }
  
  // Finds if there is any existing key for the identifier
  private func getPrivateKey(_ identifier: String) throws -> SecKey? {
    let query = prepareQueryAttributes(identifier)
    var itemCopy: CFTypeRef? // AnyObject? can also be used
    let status = SecItemCopyMatching(
      query,
      &itemCopy
    )
    
    // https://developer.apple.com/documentation/security/security-framework-result-codes
    
    if status == errSecSuccess {
      logger.debug("OSStatus is errSecSuccess")
      let privateKey = itemCopy as! SecKey
      return privateKey
    } else if status == errSecItemNotFound {
      logger.debug("OSStatus is errSecItemNotFound")
      return nil
    } else if let error = status.error {
      logger.error("SecItemCopyMatching Get key error is \(error)")
      throw error
    } else {
      logger.info("Unknown OSStatus \(status). Returning nil private key")
      return nil
    }
  }

  private func prepareKeyCreationttributes(_ identifier: String) throws -> CFDictionary {
    var error: Unmanaged<CFError>?
    
    guard let accessControl = SecAccessControlCreateWithFlags(
      kCFAllocatorDefault,
      kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
      .privateKeyUsage,
      &error
    )
    else {
      let e = error!.takeRetainedValue() as Error
      logger.error("SecAccessControlCreateWithFlags call failed \(e.localizedDescription) ")
      throw e
    }
    
    let attributes: CFDictionary =
      [
        kSecAttrKeyType: KEY_TYPE,
        kSecAttrKeySizeInBits: 256,
        kSecAttrEffectiveKeySize: 256,
        kSecAttrApplicationLabel: SECURE_STORE_LABEL_AS_DATA,
        kSecAttrTokenID: kSecAttrTokenIDSecureEnclave,
        kSecPrivateKeyAttrs as String:
          [kSecAttrIsPermanent as String: true,
           kSecAttrAccessControl: accessControl,
           kSecAttrApplicationTag as String: Data(identifier.utf8)],
      ] as CFDictionary
    
    return attributes
  }
  
  private func prepareQueryAttributes(_ identifier: String) -> CFDictionary {
    let query: CFDictionary = [kSecClass: kSecClassKey,
                               kSecAttrKeyType: KEY_TYPE,
                               kSecAttrApplicationLabel: SECURE_STORE_LABEL_AS_DATA,
                               kSecAttrKeySizeInBits: 256,
                               kSecAttrEffectiveKeySize: 256,
                               kSecAttrApplicationTag: Data(identifier.utf8),
                               kSecMatchLimit: kSecMatchLimitOne,
                               kSecReturnRef: true] as CFDictionary
    
    return query
  }
}

/*
 private func isEncryptionKeyAvailable(_ identifier: String) -> Bool {
   let query = prepareQueryAttributes(identifier)
   
   // SecItemCopyMatching will attempt to copy the item
   // identified by query to the reference itemCopy
   
   var itemCopy: CFTypeRef? // AnyObject? can also be used
   let status = SecItemCopyMatching(
     query,
     &itemCopy
   )
   
   // https://developer.apple.com/documentation/security/errsecduplicateitem
   
   logger.debug("OSStatus after SecItemCopyMatching is \(status)")
   
   if status == errSecSuccess {
     // Matching key found
     logger.debug("OSStatus is errSecSuccess")
     return true
   } else if status == errSecItemNotFound {
     logger.debug("OSStatus is errSecItemNotFound")
     return false
   } else {
     // Need to throw some error?
     if let error = status.error {
       logger.error("SecItemCopyMatching Get key error is \(error)")
     }
     return false
   }
 }
 
 */
