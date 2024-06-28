//
//  ApiCallBackService.swift
//  OneKeePassMobile
//
//  Created by on 6/27/24.
//

import Foundation

// This is intialized and set to global singleton holder in rust

class ApiCallBackService:IosApiService {
  func clipboardCopyString(_ text: String, _ timeout: UInt32) throws {
    cmnLogger.debug("Called clipboardCopyString text \(text) and timeout \(timeout)")
  }
}
