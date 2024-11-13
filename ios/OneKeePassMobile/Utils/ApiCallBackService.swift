//
//  ApiCallBackService.swift
//  OneKeePassMobile
//
//  Created by on 6/27/24.
//

import Foundation

// This is intialized and set in a global singleton holder in rust

class ApiCallBackService: IosApiService,CommonDeviceServiceEx {
  
  func testSecureStore() throws {
    let s = SecureStoreService()
    
    cmnLogger.debug("Calling delete key")
    s.deleteKey()
    cmnLogger.debug("Calling delete key DONE")
    
    cmnLogger.debug("Calling createEncryptionKey key")
    try s.createEncryptionKey()
    cmnLogger.debug("Calling createEncryptionKey key DONE")
    
    cmnLogger.debug("Calling getPrivateKey key")
    s.getPrivateKey()
    cmnLogger.debug("Calling getPrivateKey key DONE")
  }
  
  
  func ascCredentialServiceIdentifiers() throws -> [String: String] {
    #if OKP_APP_EXTENSION
      return CredentialProviderViewController.serviceIdentifiersReceived()
    #else
      return [:]
    #endif
  }
  
  func clipboardCopyString(_ text: String, _ timeout: UInt32) throws {
    #if OKP_APP_EXTENSION
      cmnLogger.debug("In OKP_APP_EXTENSION Called clipboardCopyString text \(text) and timeout \(timeout)")
    #else
      cmnLogger.debug("In APP  Called clipboardCopyString text \(text) and timeout \(timeout)")
    #endif
    
    // cmnLogger.debug("Called clipboardCopyString text \(text) and timeout \(timeout)")
    ClipboardHelper.impl.copyString(text: text, timeout: Double(timeout))
  }
  
  // An implementation of a fn in CommonDeviceServiceEx
  func clipboardCopyString(_ clipData: AppClipboardCopyData) throws {
    cmnLogger.debug("Common service clipboardCopyString is called with clipData as \(clipData)")
    try clipboardCopyString(clipData.fieldValue, clipData.cleanupAfter )
  }
  
  
}
