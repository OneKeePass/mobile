//
//  SecureStoreService.swift
//  OneKeePassMobile
//
//  Created on 11/8/24.
//

class SecureStoreService {
  let logger = OkpLogger(tag: "SecureStoreService")
  
  // Data("Okp-Secure-Store-Key".utf8) is equivalent to
  // Objective C (NSData *)dataUsingEncoding:(NSStringEncoding)encoding;
  // e.g [someNSstringPointer dataUsingEncoding:NSUTF8StringEncoding]
  
  private let SECURE_STORE_LABEL_AS_DATA = Data("Okp-Secure-Store-Key".utf8) // "Okp-Secure-Store-Key".data(using: .utf8)!
  
  private let SECURE_STORE_KEY_TAG_AS_DATA = Data("okp_secure".utf8)
  
  func createEncryptionKey() throws {
    // See https://developer.apple.com/documentation/security/protecting-keys-with-the-secure-enclave
    
    // kSecAttrKeyType as String: kSecAttrKeyTypeRSA,
    // kSecAttrKeySizeInBits as String: 2048,
    
    /*
     NSDictionary* attributes =
             @{ (id)kSecAttrKeyType:             (id)[SecretStore keyType],
                (id)kSecAttrKeySizeInBits:       @256,
                (id)kSecAttrEffectiveKeySize :   @256,
                (id)kSecAttrApplicationLabel :   kKeyApplicationLabel,
                (id)kSecAttrTokenID:             (id)kSecAttrTokenIDSecureEnclave,
                (id)kSecPrivateKeyAttrs:
                  @{ (id)kSecAttrIsPermanent:    @YES,
                     (id)kSecAttrApplicationTag: [identifier dataUsingEncoding:NSUTF8StringEncoding],
                     (id)kSecAttrAccessControl:  (__bridge id)accessControl,
                   },
              };
     */
    
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
        kSecAttrKeyType: kSecAttrKeyTypeECSECPrimeRandom,
        kSecAttrKeySizeInBits: 256,
        kSecAttrEffectiveKeySize: 256,
        kSecAttrApplicationLabel: SECURE_STORE_LABEL_AS_DATA,
        kSecAttrTokenID: kSecAttrTokenIDSecureEnclave,
        kSecPrivateKeyAttrs as String:
          [kSecAttrIsPermanent as String: true,
           kSecAttrAccessControl: accessControl,
           kSecAttrApplicationTag as String: SECURE_STORE_KEY_TAG_AS_DATA],
      ] as CFDictionary
    
    
    
    guard let privateKey: SecKey = SecKeyCreateRandomKey(attributes, &error) else {
      let e = error!.takeRetainedValue() as Error
      logger.error("SecKeyCreateRandomKey call failed \(e.localizedDescription) ")
      throw e
    }
    
    logger.info("SecKeyCreateRandomKey call is success")
    
    let publicKey = SecKeyCopyPublicKey(privateKey)
    
    if publicKey == nil {
      logger.error("Getting public key failed")
    }
  }
  
  func encrypt() {}
  
  func decrypt() {}
  
  func deleteKey() {
    let query: CFDictionary = [kSecClass: kSecClassKey,
                               kSecAttrApplicationLabel: SECURE_STORE_LABEL_AS_DATA,
                               kSecAttrKeySizeInBits: 256,
                               kSecAttrEffectiveKeySize: 256] as CFDictionary
    
    let status: OSStatus = SecItemDelete(query as CFDictionary)
    

    if let error = status.error {
      logger.error("SecItemDelete error is \(error)")
      //throw SecureKeyOperationError.DeleteKeyError(message: error.description)
    }

    logger.debug("Delete Key call completed successfully")
  }
  
  func getPrivateKey() {
    // key kSecAttrKeyTypeECSECPrimeRandom
    // algorithm kSecKeyAlgorithmECIESEncryptionStandardVariableIVX963SHA256AESGCM
    /*
     NSMutableDictionary* ret = [NSMutableDictionary dictionaryWithDictionary:@{
             (id)kSecClass :                 (id)kSecClassKey,
             (id)kSecAttrKeyType :           (id)[SecretStore keyType],
             (id)kSecAttrKeySizeInBits:      @256,
             (id)kSecAttrEffectiveKeySize :  @256,
             (id)kSecAttrApplicationLabel :  kKeyApplicationLabel,
             (id)kSecAttrApplicationTag :    [identifier dataUsingEncoding:NSUTF8StringEncoding],
             (id)kSecReturnRef :             @YES
         }];
     
     let query: CFDictionary = [kSecClass as String: kSecClassKey, //1
                                kSecAttrApplicationTag as String: tag, // 2
                                kSecAttrKeyType as String: kSecAttrKeyTypeRSA, //3
                                kSecReturnRef as String: true] as CFDictionary //4
     
     */
    
    // https://developer.apple.com/documentation/security/errsecduplicateitem
    // For key items, the primary keys include  kSecAttrKeyClass, kSecAttrKeyType,
    // kSecAttrApplicationLabel, kSecAttrApplicationTag, kSecAttrKeySizeInBits, and kSecAttrEffectiveKeySize
    
    let query: CFDictionary = [kSecClass: kSecClassKey,
                               kSecAttrKeyType: kSecAttrKeyTypeECSECPrimeRandom,
                               kSecAttrApplicationLabel: SECURE_STORE_LABEL_AS_DATA,
                               kSecAttrKeySizeInBits: 256,
                               kSecAttrEffectiveKeySize: 256,
                               kSecMatchLimit: kSecMatchLimitOne,
                               kSecReturnRef: true] as CFDictionary
    
    // SecItemCopyMatching
    
    // SecItemCopyMatching will attempt to copy the item
    // identified by query to the reference itemCopy
    var itemCopy: AnyObject?
    let status = SecItemCopyMatching(
      query,
      &itemCopy
    )
    
//    var pk: UnsafeMutablePointer<CFTypeRef?>?
//    let status = SecItemCopyMatching(
//      query,
//      pk
//    )
    
    if let error = status.error {
      
      logger.error("SecItemCopyMatching Get key error is \(error)")

    } else {
      //let privateKey = pk.take() as! SecKey // let e = error!.takeRetainedValue() as Error
      let privateKey = itemCopy as! SecKey
      
      logger.info("SecItemCopyMatching: Got the private key in item")
    }
  }
}
