//
//  OkpExport.swift
//  OneKeePassMobile
//
//  Created on 3/2/23.
//

import Foundation

private let logger = OkpLogger(tag: "OkpExport")
@objc(OkpExport)
public class OkpExport: NSObject {
  let E_EXPORT_SERVICE_MODULE_ERROR = "DB_SERVICE_MODULE_ERROR"
  
  @objc
  static func requiresMainQueueSetup() -> Bool {
    return false
  }
  
  @objc
  func exportKdbx(_ fullFileNameUri: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
    // IMPORTANT: We need to call to present UIActivityViewController in the main thread
    DispatchQueue.main.async { [unowned self] in
      // logger.debug("In exportKdbx fullFileNameUriis \(fullFileNameUri)")
      let db_file_url = URL(string: fullFileNameUri)
      
      guard db_file_url != nil else {
        reject(E_EXPORT_SERVICE_MODULE_ERROR, "fullFileNameUri cannot be nil", nil)
        return
      }
      
      let controller = RCTPresentedViewController()
      let activityViewController = UIActivityViewController(activityItems: [db_file_url!], applicationActivities: nil)
      activityViewController.completionWithItemsHandler = { [unowned self] _, _, _, error in
        
        // Need to close the activity view
        activityViewController.dismiss(animated: true, completion: nil)
        
        if error != nil {
          // Exclude error - Optional(Error Domain=NSCocoaErrorDomain Code=3072 "The operation was cancelled.") - We should not call reject for this
          if let e = error as? NSError {
            if e.code == 3072 {
              // No reject call for the cancelled operation
              // Resolving with empty json object "{}"
              resolve("{}")
              return
            }
          }
          reject(E_EXPORT_SERVICE_MODULE_ERROR, "Completetion failed", error)
          return
        }
        // Everything worked fine
        // Resolving with empty json object "{}" means no error
        resolve("{}")
      }
      
      activityViewController.modalPresentationStyle = UIModalPresentationStyle.popover
      controller?.present(activityViewController, animated: true, completion: nil)
    }
  }
}
