//
//  AutoFillEvents.swift
//  OneKeePassAutoFill
//
//  Created  on 6/17/24.
//

import Foundation
// Added when RN 0.81.5 time. Otherwise build failed with conflicting imports
import React


@objc(AutoFillEvents)
public class AutoFillEvents: RCTEventEmitter {
  private let logger = OkpLogger(tag: "AutoFillEvents")
  private static var instance: AutoFillEvents?
  
  
  static let EVENT_ENTRY_OTP_UPDATE = "onEntryOtpUpdate"
  
  override init() {
    super.init()
    AutoFillEvents.instance = self
  }
  
  var hasListeners = false
  
  @objc
  override public static func requiresMainQueueSetup() -> Bool {
    return false
  }

  override public func startObserving() {
    // logger.debug("startObserving called ..")
    hasListeners = true
  }
  
  override public func stopObserving() {
    // logger.debug("stopObserving called ..")
    hasListeners = false
  }
  
  override public func supportedEvents() -> [String]! {
    // At this time, only one event is supported
    return [AutoFillEvents.EVENT_ENTRY_OTP_UPDATE]
  }
  
  
  public static func sendEntryOtpUpdate(_ jsonString:String) {
    instance?.sendEvent(withName: EVENT_ENTRY_OTP_UPDATE, body: jsonString)
  }
}
