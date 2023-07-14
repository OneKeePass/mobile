//
//  OkpEvents.swift
//  OneKeePassMobile
//
//  Created on 3/1/23.
//

import Foundation


@objc(OkpEvents)
public class OkpEvents: RCTEventEmitter {
  private let logger = OkpLogger(tag: "OkpEvents")
  private static var instance: OkpEvents?
  
  override init() {
    super.init()
    OkpEvents.instance = self
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
    return ["onApplicationOpenURL"]
  }
  
  // Called from SceneDelegate when user presses a .kdbx file
  @objc
  public static func calledWithUrl(_ url: URL) {
    // logger.debug("Called with url in event .. \(url) and instance is \(instance)")
    let r = FileUtils.coordinatedSyncBookMarking(url: url) { url, error in
      // logger.debug("In closure... error \(error)")
      if error == nil {
        instance?.sendEvent(withName: "onApplicationOpenURL", body: DbServiceAPI.formJsonWithFileName(url.absoluteString))
      } else {
        instance?.sendEvent(withName: "onApplicationOpenURL", body: "{\"error\" \(String(describing: error?.localizedDescription))}")
      }
    }
    // logger.debug("Bookmarking cll  ret val \(r))")
  }
}
