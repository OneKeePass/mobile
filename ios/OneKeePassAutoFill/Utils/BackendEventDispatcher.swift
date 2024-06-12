//
//  EventDispatchImpl.swift
//  OneKeePassMobile
//
//  Created by Jeyasankar  on 3/23/24.
//

import Foundation

// This is the callback implementation of the Trait 'EventDispatch' defined in db_service.udl
// We can not use name 'EventDispatchImpl' as this is used in the generated code in db_service.swift

// Rust side calls the funtions implemented here

class BackendEventDispatcher: EventDispatch {
  func sendTickUpdate(_ jsonString: String) throws {
    // cmnLogger.debug("Received sendTickUpdate jsonString \(jsonString)")
   // OkpEvents.sendTickUpdate(jsonString)
  }

  func sendOtpUpdate(_ jsonString: String) throws {
    // cmnLogger.debug("Received sendOtpUpdate jsonString \(jsonString)")
    //OkpEvents.sendEntryOtpUpdate(jsonString)
  }
}
