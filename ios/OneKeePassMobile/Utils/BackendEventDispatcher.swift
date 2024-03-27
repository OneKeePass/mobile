//
//  EventDispatchImpl.swift
//  OneKeePassMobile
//
//  Created by Jeyasankar  on 3/23/24.
//

import Foundation

class BackendEventDispatcher: EventDispatch {
  func sendTickUpdate(_ jsonString: String) throws {
    //cmnLogger.debug("Received sendTickUpdate jsonString \(jsonString)")
    OkpEvents.sendTickUpdate(jsonString)
  }
  
  func sendOtpUpdate(_ jsonString: String) throws {
    //cmnLogger.debug("Received sendOtpUpdate jsonString \(jsonString)")
    OkpEvents.sendEntryOtpUpdate(jsonString)
  }
}
