//
//  ApiCallBackService.swift
//  OneKeePassMobile
//
//  Created by on 6/27/24.
//

import Foundation

// This is intialized and set to global singleton holder in rust

class ApiCallBackService: IosApiService {
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
}
