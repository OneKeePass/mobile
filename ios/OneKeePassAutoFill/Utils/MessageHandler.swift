//
//  MessageHandler.swift
//  OneKeePassAutoFill
//
//  Created by on 6/12/24.
//

import Foundation
import SwiftyMessenger


class MessageHandler {
  private static let groupIdentifier = "group.com.onekeepass.afshared"
  
  private var messenger: Messenger!
  
  private static let directory = "messenger"
  
  
  func post() {
    messenger = Messenger(withApplicationGroupIdentifier: MessageHandler.groupIdentifier, directory: MessageHandler.directory)
    
    messenger.passMessage(message: ["messageOneTxt": "Today-One"], identifier: "messageOne")
    
  }
  
}
