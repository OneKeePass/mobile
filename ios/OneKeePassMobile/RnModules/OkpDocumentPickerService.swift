import Foundation
import UniformTypeIdentifiers

@objc(OkpDocumentPickerService)
class OkpDocumentPickerService: NSObject {
  private let logger = OkpLogger(tag: "OkpDocumentPickerService")
  
  static let E_DOCUMENT_PICKER_CANCELED = "DOCUMENT_PICKER_CANCELED"
  static let E_TEMP_DB_FILE_CREATE = "TEMP_DB_FILE_CREATE"
  static let E_ACCESSING_SECURITY_SCOPED_RESOURCE = "ACCESSING_SECURITY_SCOPED_RESOURCE"
  static let E_READ_FILE_PICK_DELEGATE_FAILED = "READ_FILE_PICK_DELEGATE_FAILED"
  static let E_COORDINATOR_CALL_FAILED = "COORDINATOR_CALL_FAILED"
  static let E_DOCUMENT_PICKER_EMPTY_URLS = "DOCUMENT_PICKER_EMPTY_URLS"
  
  //var promiseWrapper: PromiseWrapper?
  var readFilePickDelegate: ReadFilePickDelegate?
  var keyFilePickDelegate: KeyFilePickDelegate?
  var dummyDocumentPickDelegate:DummyDocumentPickDelegate?
  
  
  
  @objc static func requiresMainQueueSetup() -> Bool {
    return false
  }

  // Used to select a kdbx file to open and read
  @objc
  func pickKdbxFileToOpen(_ resolve: @escaping (RCTPromiseResolveBlock), reject: @escaping (RCTPromiseRejectBlock)) {
    DispatchQueue.main.async {
      //self.promiseWrapper = PromiseWrapper(resolve: resolve, reject: reject)
      let controller = RCTPresentedViewController()
      // let documentPicker = UIDocumentPickerViewController(documentTypes: ["public.item"], in: .open)
      let documentPicker = UIDocumentPickerViewController(forOpeningContentTypes: [.data, .content, .item])
      documentPicker.allowsMultipleSelection = false
      documentPicker.modalPresentationStyle = .pageSheet
      
      // Need to use NSFileCoordinator in delegate so that we can create the required bookmarks
      self.readFilePickDelegate = ReadFilePickDelegate(resolve, reject)
      documentPicker.delegate = self.readFilePickDelegate
      controller?.present(documentPicker, animated: true, completion: nil)
    }
  }
  
  // Provides an UI view for the user to select any file to copy as key file
  @objc
  func pickKeyFileToCopy(_ resolve: @escaping (RCTPromiseResolveBlock), reject: @escaping (RCTPromiseRejectBlock)) {
    DispatchQueue.main.async {
      //self.promiseWrapper = PromiseWrapper(resolve: resolve, reject: reject)
      let controller = RCTPresentedViewController()
      // let documentPicker = UIDocumentPickerViewController(documentTypes: ["public.item"], in: .open)
      let documentPicker = UIDocumentPickerViewController(forOpeningContentTypes: [.data, .content, .item])
      documentPicker.allowsMultipleSelection = false
      documentPicker.modalPresentationStyle = .pageSheet
      
      // Need to hold the Delegate in an instance variable. Otherwise we will get the error
      // "Instance will be immediately deallocated because property 'delegate' is 'weak'"
      self.keyFilePickDelegate = KeyFilePickDelegate(resolve, reject)
      documentPicker.delegate = self.keyFilePickDelegate
      controller?.present(documentPicker, animated: true, completion: nil)
    }
  }
  
  // Called to save the selected key file from app's private area to any user picked location
  // fullKeyFileName is an existing file's full url
  @objc
  func pickKeyFileToSave(_ fullKeyFileName: String,
                         keyFileName: String,
                         resolve: @escaping (RCTPromiseResolveBlock),
                         reject: @escaping (RCTPromiseRejectBlock))
  {
    DispatchQueue.main.async {
      
      // In rust side, file path should not have file:// prefix.
      // The fullKeyFileName returned from earlier call does not start with file://
      // UIDocumentPickerView expects proper file scheme URL
      
      /*
      // Another way building the file url
      var u2 = URLComponents()
      u2.scheme = "file"
      u2.path = fullKeyFileName
      self.logger.debug("u2 is \(u2) and u1 is \(u1)")
      */
      
      let keyFileUrl = URL(fileURLWithPath: fullKeyFileName)
      
      // For the file with non zero bytes of data needs to exist before user is presented with UIDocumentPickerView to use 'Save' 
      guard FileManager.default.fileExists(atPath:keyFileUrl.path) else {
        reject("FILE_IS_NOT_FOUND", "No file is found at the url \(fullKeyFileName)", nil)
        return
      }
      
      let controller = RCTPresentedViewController()
      // When we use this option, the UIDocumentPickerView shows "Save" on the top right side
      let documentPicker = UIDocumentPickerViewController(forExporting: [keyFileUrl], asCopy: true) // to export or copy
      
      documentPicker.allowsMultipleSelection = false
      //documentPicker.modalPresentationStyle = .pageSheet
      
      // We are reusing the same Delegate that we have used for 'pickKeyFileToCopy'
      // TODO: Is it required to create bookmark for this "Save as" call?
      // In case of 'pickKeyFileToCopy', we do need the bookmark for copyKeyFile to work and it is deleetd there
      // Here it is not used
      
      // self.keyFilePickDelegate = KeyFilePickDelegate(resolve, reject)
      // documentPicker.delegate = self.keyFilePickDelegate
      
      // We can use DummyDocumentPickDelegate instead of KeyFilePickDelegate as we need not to bookmark the url
      self.dummyDocumentPickDelegate = DummyDocumentPickDelegate(resolve, reject)
      documentPicker.delegate = self.dummyDocumentPickDelegate
    
      controller?.present(documentPicker, animated: true, completion: nil)
    }
  }
  
  // Called to save the selected attachment file from an entry's attachment data to a location picked by the user
  // This works the same way as 'pickKeyFileToSave'
  @objc
  func pickAttachmentFileToSave(_ fullTempAttachmentFileName: String,
                         attachmentName: String,
                         resolve: @escaping (RCTPromiseResolveBlock),
                         reject: @escaping (RCTPromiseRejectBlock))
  {
    pickKeyFileToSave(fullTempAttachmentFileName,keyFileName: attachmentName,resolve: resolve,reject: reject)
  }
  
  /*
  // Called to save the selected attachment file from an entry's attachment data to a location picked by the user
  @objc
  func pickAttachmentFileToSave(_ fullTempAttachmentFileName: String,
                         attachmentName: String,
                         resolve: @escaping (RCTPromiseResolveBlock),
                         reject: @escaping (RCTPromiseRejectBlock))
  {
    DispatchQueue.main.async {
      
      // In rust side, file path should not have file:// prefix.
      // The fullTempAttachmentFileName returned from earlier call to UI  does not start with file://
      // UIDocumentPickerView expects proper file scheme URL
      
      let fileUrl = URL(fileURLWithPath: fullTempAttachmentFileName)
      
      // For the file with non zero bytes of data needs to exist before user is presented with UIDocumentPickerView to use 'Save'
      guard FileManager.default.fileExists(atPath:fileUrl.path) else {
        reject("ATTACHMENT_FILE_IS_NOT_FOUND", "No temporary attachment file is found at the url \(fullTempAttachmentFileName)", nil)
        return
      }
      
      let controller = RCTPresentedViewController()
      // When we use this option, the UIDocumentPickerView shows "Save" on the top right side
      let documentPicker = UIDocumentPickerViewController(forExporting: [fileUrl], asCopy: true) // to export or copy
      
      documentPicker.allowsMultipleSelection = false
      //documentPicker.modalPresentationStyle = .pageSheet
      
      
      self.dummyDocumentPickDelegate = DummyDocumentPickDelegate(resolve, reject)
      documentPicker.delegate = self.dummyDocumentPickDelegate
    
      controller?.present(documentPicker, animated: true, completion: nil)
    }
  }
  */
  
  // Used to create a new kdbx file followed by a readKdbx call
  // fileName is the suggested kdbx file name to use and user can change the name in the document picker
  @objc
  func pickAndSaveNewKdbxFile(_ fileName: String, jsonArgs: String,
                              resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock)
  {
    DispatchQueue.main.async {
      //self.promiseWrapper = PromiseWrapper(resolve: resolve, reject: reject)
      let controller = RCTPresentedViewController()

      let fileURL = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
      // let r = DbServiceAPI.createTempKdbx(fileURL.absoluteString, jsonArgs)
      
      // A temp new kdbx database is created using the temp file url from 'fileURL'
      if case .failure(let result) = DbServiceAPI.createTempKdbx(fileURL.absoluteString, jsonArgs) {
        reject(OkpDocumentPickerService.E_TEMP_DB_FILE_CREATE, result, nil)
        return
      }
      
      self.logger.info("Temp kbbx file \(fileURL) is written")
      
      let documentPicker = UIDocumentPickerViewController(forExporting: [fileURL], asCopy: true)
      documentPicker.allowsMultipleSelection = false
      
      // We are reusing the same delegate as that is used in readKdbx call
      // This works for creating a new kdbx db file to Local, iCloud and Dropbox without any issues
      // However for OneCloud and GDrive, though the new kdbx file is created,
      // we get 'COORDINATOR_CALL_FAILED' error "Couldn’t communicate with a helper application.".
      // But the user can use OpenDatabse option in UI to select the database again.
      // There may be some issues with some FileProvider API implementation in OneCloud and GDrive
      // TODO: Need to add a help text for this in UI.
      self.readFilePickDelegate = ReadFilePickDelegate(resolve, reject)
      documentPicker.delegate = self.readFilePickDelegate
      
      // This delegate uses writingIntent in addition to readingIntent. This did not work for OneDrive and GDrive
      // self.createFilePickDelegate = CreateFilePickDelegate(resolve, reject)
      // documentPicker.delegate = self.createFilePickDelegate
      
      // This delegate behavioued the same as ReadFilePickDelegate
      // self.delegate1 = Delegate1(resolve, reject)
      // documentPicker.delegate = self.delegate1

      // self.logger.debug("Going to present to controller ")
      controller?.present(documentPicker, animated: true, completion: nil)
    }
  }
  
  // Called when user opts to use 'Save as' when there unsolvable save time error
  // fileName is the suggested kdbx file name to use and user can change the name in the document picker
  @objc
  func pickOnSaveErrorSaveAs(_ fileName: String,existingFullFileNameUri: String,
                             resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock)
  {
    DispatchQueue.main.async {
      //self.promiseWrapper = PromiseWrapper(resolve: resolve, reject: reject)
      let controller = RCTPresentedViewController()
      
      let tempFileName =  DbServiceAPI.iosSupportService().copyLastBackupToTempFile(fileName,existingFullFileNameUri)
      guard tempFileName != nil else {
        reject("NO_BACK_FILE_IS_FOUND", "Temp file name is nil", nil)
        return
      }
      
      guard let tempFileUrl = URL(string:tempFileName!) else {
        reject("NO_BACK_FILE_IS_FOUND", "Temp file url is nil", nil)
        return
      }
      
      self.logger.debug("tempFileUrl is \(tempFileUrl.absoluteString)")
      
      // If we use this, the UIDocumentPickerView shows "Save" on the top right side
      let documentPicker = UIDocumentPickerViewController(forExporting: [tempFileUrl], asCopy: true) // to export or copy
      
      // The temp file is moved when user selects a location
      // If we use this, the UIDocumentPickerView shows "Move" on the top right side
      // let documentPicker = UIDocumentPickerViewController(forExporting: [tempFileUrl])
      
      documentPicker.allowsMultipleSelection = false
      
      self.readFilePickDelegate = ReadFilePickDelegate(resolve, reject)
      documentPicker.delegate = self.readFilePickDelegate
    
      controller?.present(documentPicker, animated: true, completion: nil)
    }
  }
  

}

// Used mainly when we want to do "Save As" of an attachment
class DummyDocumentPickDelegate : NSObject, UIDocumentPickerDelegate{
  private let logger = OkpLogger(tag: "DummyDocumentPickDelegate")
  var resolve: RCTPromiseResolveBlock
  var reject: RCTPromiseRejectBlock
  init(_ resolve: @escaping RCTPromiseResolveBlock, _ reject: @escaping RCTPromiseRejectBlock) {
    self.resolve = resolve
    self.reject = reject
  }
  
  func documentPicker(_: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
    let pickedFileUrl = urls.first!
    // Just return the ok value. No book mark etc done as we do not need this url for any other use
    // We do not bookmark this url because the data is copied to the selected file from the existing file in
    // 'pickKeyFileToSave' and 'pickAttachmentFileToSave'
    resolve(DbServiceAPI.jsonService().okJsonString(pickedFileUrl.absoluteString))
  }
  
  func documentPickerWasCancelled(_: UIDocumentPickerViewController) {
    reject(OkpDocumentPickerService.E_DOCUMENT_PICKER_CANCELED, "User cancelled the picking a directory or a document", nil)
  }
}

// A delegate used in 'pickKdbxFileToOpen' when user selects the db file to read
// Also used for 'pickAndSaveNewKdbxFile' and 'pickOnSaveErrorSaveAs'
class ReadFilePickDelegate: NSObject, UIDocumentPickerDelegate {
  private let logger = OkpLogger(tag: "ReadFilePickDelegate")
  var resolve: RCTPromiseResolveBlock
  var reject: RCTPromiseRejectBlock
  init(_ resolve: @escaping RCTPromiseResolveBlock, _ reject: @escaping RCTPromiseRejectBlock) {
    self.resolve = resolve
    self.reject = reject
  }
  
  func documentPicker(_: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
    // logger.debug("Picked urls: \(urls)")
    let saved_file_url = urls.first!
    let fc = NSFileCoordinator()
    let intent = NSFileAccessIntent.readingIntent(with: saved_file_url, options: [.withoutChanges, .resolvesSymbolicLink])
      
    fc.coordinate(with: [intent], queue: .main) { [unowned self] err in
      guard err == nil else {
        logger.error("Coordinate error  is \(String(describing: err))")
        // reject(CallError.coordinateError.rawValue, CallError.errorDescription(err! as NSError), err)
        reject(OkpDocumentPickerService.E_COORDINATOR_CALL_FAILED, "\(String(describing: err?.localizedDescription))", err)
        return
      }
    
      // self.logger.debug("In ReadFilePickDelegate coordinate saved_file_url is \(saved_file_url)")
      // Need to access the url with security scope
      guard saved_file_url.startAccessingSecurityScopedResource() else {
        logger.error("startAccessingSecurityScopedResource call failed")
        reject(OkpDocumentPickerService.E_ACCESSING_SECURITY_SCOPED_RESOURCE, "startAccessingSecurityScopedResource failed", nil)
        return
      }

      // getProvideName(at: saved_file_url)
      
      // Make sure we release the security-scoped resource when finished.
      defer { saved_file_url.stopAccessingSecurityScopedResource() }
      do {
        // Secured access to url should be available before bookmarking
        let bookmarkData = try saved_file_url.bookmarkData(options: .minimalBookmark, includingResourceValuesForKeys: nil, relativeTo: nil)
        let byteArray: [UInt8] = .init(bookmarkData)
        logger.debug("Calling rust api to save bookmark data with size \(byteArray.count)")
        let b = DbServiceAPI.iosSupportService().saveBookMarkData(saved_file_url.absoluteString, byteArray)
        logger.debug("Bookmark save rust api call result is \(b)")
      
        // After bookmarking just the uri is returned to the UI to use
        // The file is read and loaded only in the subsequent call from UI
        resolve(DbServiceAPI.formJsonWithFileName(saved_file_url.absoluteString))
        
      } catch {
        // Handle the error here.
        logger.error("E_READ_FILE_PICK_DELEGATE_FAILED: bookmarkData Error \(error)")
        reject(OkpDocumentPickerService.E_READ_FILE_PICK_DELEGATE_FAILED, "\(error.localizedDescription)", error)
      }
    }
  }
  
  func documentPickerWasCancelled(_: UIDocumentPickerViewController) {
    reject(OkpDocumentPickerService.E_DOCUMENT_PICKER_CANCELED, "User cancelled the picking a directory or a document", nil)
  }
  
  // Just a test code
  func getProvideName(at url: URL) {
    FileManager.default.getFileProviderServicesForItem(at: url) { services, error in
      // Check to see if an error occurred.
      guard error == nil else {
        // Handle the error here...
        self.logger.error("Error in calling getFileProviderServicesForItem \(String(describing: error))")
        return
      }
      // For urls accessed from onedrive and gdrive services is nil
      // Only in case iCloud and dropbox some values are returned
      self.logger.debug("Provider Service names \(String(describing: services?.first)) name: \(String(describing: services?.first?.key.rawValue))")
    }
  }
}

class KeyFilePickDelegate: NSObject, UIDocumentPickerDelegate {
  private let logger = OkpLogger(tag: "KeyFilePickDelegate")
  var resolve: RCTPromiseResolveBlock
  var reject: RCTPromiseRejectBlock
  init(_ resolve: @escaping RCTPromiseResolveBlock, _ reject: @escaping RCTPromiseRejectBlock) {
    self.resolve = resolve
    self.reject = reject
  }
  
  func documentPicker(_: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
    // logger.debug("Picked urls: \(urls)")
    let pickedFileUrl = urls.first!
    let fc = NSFileCoordinator()
    let intent = NSFileAccessIntent.readingIntent(with: pickedFileUrl, options: [.withoutChanges, .resolvesSymbolicLink])
    
    // IMPORTANT:
    // Any file picked by the user is outside the app's sandbox. The opening and reading of any
    // such file can only be done after a successful 'startAccessingSecurityScopedResource'
    // Instead of opening and reading the file here, we keep a bookmark and that
    // bookmark is read in 'copyKeyFile' fun from OkpDbService. Without the bookmark, we cannot read the file
      
    fc.coordinate(with: [intent], queue: .main) { [unowned self] err in
      guard err == nil else {
        logger.error("Coordinate error  is \(String(describing: err))")
        reject(OkpDocumentPickerService.E_COORDINATOR_CALL_FAILED, "\(String(describing: err?.localizedDescription))", err)
        return
      }
    
    
      // Need to access the url with security scope
      guard pickedFileUrl.startAccessingSecurityScopedResource() else {
        logger.error("startAccessingSecurityScopedResource call failed")
        reject(OkpDocumentPickerService.E_ACCESSING_SECURITY_SCOPED_RESOURCE, "startAccessingSecurityScopedResource failed", nil)
        return
      }
      
      // Make sure we release the security-scoped resource when finished.
      defer { pickedFileUrl.stopAccessingSecurityScopedResource() }
      do {
        // Secured access to url should be available before bookmarking
        let bookmarkData = try pickedFileUrl.bookmarkData(options: .minimalBookmark, includingResourceValuesForKeys: nil, relativeTo: nil)
        let byteArray: [UInt8] = .init(bookmarkData)
        
        // Note: At this time both kdbx and the key file uri bookmarking use the same way
        let b = DbServiceAPI.iosSupportService().saveBookMarkData(pickedFileUrl.absoluteString, byteArray)
        logger.debug("Bookmark save rust api call result is \(b)")
      
        // After bookmarking just the uri is returned to the UI to use
        // The file is read and loaded only in 'copyKeyFile' after the subsequent call from UI
        resolve(DbServiceAPI.jsonService().okJsonString(pickedFileUrl.absoluteString))
        
      } catch {
        // Handle the error here.
        logger.error("E_READ_FILE_PICK_DELEGATE_FAILED: bookmarkData Error \(error)")
        reject(OkpDocumentPickerService.E_READ_FILE_PICK_DELEGATE_FAILED, "\(error.localizedDescription)", error)
      }
    }
  }
  
  func documentPickerWasCancelled(_: UIDocumentPickerViewController) {
    reject(OkpDocumentPickerService.E_DOCUMENT_PICKER_CANCELED, "User cancelled the picking a directory or a document", nil)
  }
  
  /*
  
   // This did not work as we have not read the file here itself or created a bookmark.
  func documentPicker(_: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
    // logger.debug("Picked urls: \(urls)")
    let pickedFileUrl = urls.first!
    
    // Rational for the needto use NSFileCoordinator though somewhat old
    // http://karmeye.com/2014/12/18/uidocumentpicker-nsfilecoordinator/
    let fc = NSFileCoordinator()
    let intent = NSFileAccessIntent.readingIntent(with: pickedFileUrl, options: [.withoutChanges, .resolvesSymbolicLink])
      
    fc.coordinate(with: [intent], queue: .main) { [unowned self] err in
      guard err == nil else {
        logger.error("Coordinate error  is \(String(describing: err))")
        // reject(CallError.coordinateError.rawValue, CallError.errorDescription(err! as NSError), err)
        reject(OkpDocumentPickerService.E_COORDINATOR_CALL_FAILED, "\(String(describing: err?.localizedDescription))", err)
        return
      }
    
      // Need to access the url with security scope
      guard pickedFileUrl.startAccessingSecurityScopedResource() else {
        logger.error("startAccessingSecurityScopedResource call failed")
        reject(OkpDocumentPickerService.E_ACCESSING_SECURITY_SCOPED_RESOURCE, "startAccessingSecurityScopedResource failed", nil)
        return
      }

      // Make sure we release the security-scoped resource when finished.
      defer { pickedFileUrl.stopAccessingSecurityScopedResource() }
      
      // Send the picked full uri to the cljs caller.
      // The file is read and loaded only in the subsequent call from UI
      resolve(DbServiceAPI.jsonService().okJsonString(pickedFileUrl.absoluteString))
    }
  }
   */
}


///
///


/*
 
 class PromiseWrapper {
   var resolve: RCTPromiseResolveBlock
   var reject: RCTPromiseRejectBlock

   init(resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
     self.resolve = resolve
     self.reject = reject
   }
 }
 
 // Not used- see comments in pickAndSaveNewKdbxFile
 // Leaving it here in case we need to use this if the current way of doing stuff fails
 // Delegate used in testing the creation of a new kdbx file
 // This delegate uses writingIntent in addition to readingIntent. This did not work for OneDrive and GDrive
 class CreateFilePickDelegate: NSObject, UIDocumentPickerDelegate {
   private let logger = OkpLogger(tag: "CreateFilePickDelegate")
   var resolve: RCTPromiseResolveBlock
   var reject: RCTPromiseRejectBlock
   init(_ resolve: @escaping RCTPromiseResolveBlock, _ reject: @escaping RCTPromiseRejectBlock) {
     self.resolve = resolve
     self.reject = reject
   }
     
   func documentPicker(_: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
     logger.debug("CreateFilePickDelegate Picked urls: \(urls)")
     let saved_file_url = urls.first!
     let fc = NSFileCoordinator()
     let writeIntent = NSFileAccessIntent.writingIntent(with: saved_file_url, options: [.forMerging])
     let readingIntent = NSFileAccessIntent.readingIntent(with: saved_file_url, options: [.withoutChanges, .resolvesSymbolicLink])
       
     fc.coordinate(with: [writeIntent, readingIntent], queue: .main) { [unowned self] err in
       guard err == nil else {
         logger.error("Coordinate error  is \(String(describing: err))")
         // reject(CallError.coordinateError.rawValue, CallError.errorDescription(err! as NSError), err)
         reject(OkpDocumentPickerService.E_COORDINATOR_CALL_FAILED, "\(String(describing: err?.localizedDescription))", err)
         return
       }
     
       self.logger.debug("In CreateFilePickDelegate coordinate saved_file_url is \(saved_file_url)")
       // Need to access the url with security scope
       guard saved_file_url.startAccessingSecurityScopedResource() else {
         logger.error("startAccessingSecurityScopedResource call failed")
         reject(OkpDocumentPickerService.E_ACCESSING_SECURITY_SCOPED_RESOURCE, "startAccessingSecurityScopedResource failed", nil)
         return
       }

       // Make sure we release the security-scoped resource when finished.
       defer { saved_file_url.stopAccessingSecurityScopedResource() }
       do {
         // Secured access to url should be available before bookmarking
         logger.debug("Creating bookmark for the saved_file_url \(saved_file_url)")
         let bookmarkData = try saved_file_url.bookmarkData(options: .minimalBookmark, includingResourceValuesForKeys: nil, relativeTo: nil)
         let byteArray: [UInt8] = .init(bookmarkData)

         logger.debug("Calling rust api to save bookmark data with size \(byteArray.count)")
         let b = DbServiceAPI.iosSupportService().saveBookMarkData(saved_file_url.absoluteString, byteArray)
         logger.debug("Bookmark save rust api call result is \(b)")
       
         // logger.debug("Json with file name \(DbServiceAPI.formJsonWithFileName(saved_file_url.absoluteString))")
         // resolve ("\(urls.first!)")
         resolve(DbServiceAPI.formJsonWithFileName(saved_file_url.absoluteString))
         
       } catch {
         // Handle the error here.
         logger.error("E_READ_FILE_PICK_DELEGATE_FAILED: bookmarkData Error \(error)")
         reject(OkpDocumentPickerService.E_READ_FILE_PICK_DELEGATE_FAILED, "\(error.localizedDescription)", error)
       }
     }
   }
   
   func documentPickerWasCancelled(_: UIDocumentPickerViewController) {
     reject(OkpDocumentPickerService.E_DOCUMENT_PICKER_CANCELED, "User cancelled the picking a directory or a document", nil)
   }
 }

 
 
 
 
// Delegate used in testing the creation of a new kdbx file
class Delegate1: NSObject, UIDocumentPickerDelegate {
  private let logger = OkpLogger(tag: "CreateFilePickDelegate")
  var resolve: RCTPromiseResolveBlock
  var reject: RCTPromiseRejectBlock
  init(_ resolve: @escaping RCTPromiseResolveBlock, _ reject: @escaping RCTPromiseRejectBlock) {
    self.resolve = resolve
    self.reject = reject
  }
  
  // Default delegate. Used in deprecated pickKdbxFileToCreate call
  func documentPicker(_: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
    logger.debug("urls is \(urls)")
    
    guard !urls.isEmpty else {
      reject(OkpDocumentPickerService.E_DOCUMENT_PICKER_EMPTY_URLS, "No file is picked. At leaset one file should have been picked", nil)
      return
    }

    // Need to store the url's book mark and then return url to UI
    if FileUtils.bookMark(url: urls.first!) {
      logger.debug("Bookmark created for the url  \(urls.first!)")
      resolve(DbServiceAPI.formJsonWithFileName(urls.first!.absoluteString))
    } else {
      logger.error("Bookmarking failed and still sending the url selected to UI layer")
      reject(OkpDocumentPickerService.E_ACCESSING_SECURITY_SCOPED_RESOURCE, "startAccessingSecurityScopedResource failed", nil)
    }
  }

  func documentPickerWasCancelled(_: UIDocumentPickerViewController) {
    reject(OkpDocumentPickerService.E_DOCUMENT_PICKER_CANCELED, "User cancelled the picking a directory or a document", nil)
  }
}
*/

///

/*
 
 /// ---------------------------------------------------------------------------------------------
 // Leaving it here in case we need to use this if the current way of doing stuff fails
 
 // *** Not used currently ***
 // See pickAndSaveNewKdbxFile
 
 // Used to create a new kdbx file followed by createKdbx call
 // This worked fine for Local,iCloud and Dropbox. It did not work with GDrive
 // In case of OneDrive, empty file is created.
 // We need to call saveKdbx (e.g by adding a new entry or changing db name etc) for the newly created db to be written
 @available(*, deprecated, message: "See pickAndSaveNewKdbxFile")
 @objc
 func pickKdbxFileToCreate(_ fileName: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
   logger.debug("pickKdbxFileToCreate is called for the fileName \(fileName)")
   DispatchQueue.main.async {
     self.promiseWrapper = PromiseWrapper(resolve: resolve, reject: reject)
     let controller = RCTPresentedViewController()

     let fileManager = FileManager.default
     do {
       // Need to create an empty temp file before using UIDocumentPickerViewController with option 'forExporting' and 'asCopy'
       // Otherwise the file is not created when user saves the file in the location presented in UIDocumentPickerView
       // TODO: Need to delete this temp file in documentPicker and in documentPickerWasCancelled
       let fileURL = fileManager.temporaryDirectory.appendingPathComponent(fileName)
       let data = Data()
       try data.write(to: fileURL)
       self.logger.debug("Empty file \(fileURL) is written")
       
       let documentPicker = UIDocumentPickerViewController(forExporting: [fileURL], asCopy: true)
       documentPicker.allowsMultipleSelection = false
       
       documentPicker.delegate = self
       
       self.logger.debug("Going to present to controller ")
       controller?.present(documentPicker, animated: true, completion: nil)

     } catch {
       // Handle the error here.
       self.logger.error("Error is \(error)")
       reject(OkpDocumentPickerService.E_TEMP_DB_FILE_CREATE, "\(error.localizedDescription)", error)
     }
   }
 }
 
 // *** Not used currently ***
 // Picking dir works for File App folder and iCloud.
 // All other drives - Dropbox,OneDrive and GDrive are grayed out on device and not used
 @objc
 func pickDirectory(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
   DispatchQueue.main.async {
     self.promiseWrapper = PromiseWrapper(resolve: resolve, reject: reject)
     let controller = RCTPresentedViewController()
     let documentPicker = UIDocumentPickerViewController(forOpeningContentTypes: [.folder]) // This works only in iOs14+
     // let documentPicker = UIDocumentPickerViewController(documentTypes: ["public.folder"], in: .open)
     documentPicker.delegate = self
     controller?.present(documentPicker, animated: true, completion: nil)
   }
 }
 
 /// ---------------------------------------------------------------------------------------------
 
 */
