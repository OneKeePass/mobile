//
//  AutoFillMessageHandler.swift
//  OneKeePassMobile
//
//  Created  on 6/12/24.
//

// TODO: Need to be removed and SwiftyMessenger from pod file

import Foundation
import SwiftyMessenger

class AutoFillMessageHandler {
  private static let groupIdentifier = "group.com.onekeepass.afshared"
  
  private var messenger: Messenger!
  
  private static let directory = "messenger"
  
  func listen() {
    messenger = Messenger(withApplicationGroupIdentifier: AutoFillMessageHandler.groupIdentifier, directory: AutoFillMessageHandler.directory)
    
//    DispatchQueue.global(qos: .background).async { [ self] in
//
//      Swift.debugPrint("Going to listen in a thread...")
//
//      while true {
//        Swift.debugPrint("listenForMessage begins ")
//        messenger.listenForMessage(withIdentifier: "messageOne") { message in
//
//          Swift.debugPrint("Received message \(message)")
//
//          guard let message = message as? [String: Any] else {
//            return
//          }
//          let receivedTxt = message["messageOneTxt"] as? String
//
//          Swift.debugPrint("Received message \(String(describing: receivedTxt))")
//        }
//        Swift.debugPrint("listenForMessage end and looping back ")
//      }
//
//      //Swift.debugPrint("Leaving the lsiten thread...")
//    }
  }
  
  func listen2() {
    messenger = Messenger(withApplicationGroupIdentifier: AutoFillMessageHandler.groupIdentifier, directory: AutoFillMessageHandler.directory)
    
    if let message = messenger.messageForIdentifier(identifier: "messageOne") as? [String: Any] {
      let receivedTxt = message["messageOneTxt"] as? String
      Swift.debugPrint("Received message \(String(describing: receivedTxt))")
    } else {
      Swift.debugPrint("No message from autofill")
    }
  }
  
  func post() {}
}
