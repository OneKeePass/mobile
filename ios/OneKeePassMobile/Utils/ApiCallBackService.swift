//
//  ApiCallBackService.swift
//  OneKeePassMobile
//
//  Created by on 6/27/24.
//

import Foundation

// This is intialized and set in a global singleton holder in rust


// To fix the warning:
// Non-final class 'ApiCallBackService' cannot conform to 'Sendable'; use '@unchecked Sendable'; this is an error in the Swift 6 language mode
// We may need to use the solution as here. We need to do more testing with this
// class ApiCallBackService:@unchecked Sendable, IosApiService,CommonDeviceServiceEx

// See https://www.avanderlee.com/swift/sendable-protocol-closures/ for some explanation


class ApiCallBackService:@unchecked Sendable, IosApiService,CommonDeviceServiceEx {
  
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
