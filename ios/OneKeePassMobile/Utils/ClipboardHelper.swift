//
//  ClipboardHelper.swift
//  OneKeePassMobile
//
//  Created by  on 6/27/24.
//

import Foundation

// import MobileCoreServices
import UIKit
import UniformTypeIdentifiers

class ClipboardHelper {
  public static let impl = ClipboardHelper()

  private init() {}

  public func copyString(text: String, timeout: Double? = nil) {
    var pasteboardItem = [String: Any]()
    pasteboardItem[UTType.utf8PlainText.identifier] = text

    if let timeout = timeout, timeout > 0.0 {
      UIPasteboard.general.setItems(
        [pasteboardItem],
        options: [
          .localOnly: true,
          .expirationDate: Date(timeIntervalSinceNow: timeout)
        ]
      )
    } else {
      UIPasteboard.general.setItems([pasteboardItem], options: [.localOnly: true])
    }
  }
}
