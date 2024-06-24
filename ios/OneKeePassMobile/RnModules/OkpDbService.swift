import Foundation
import LocalAuthentication

@objc(OkpDbService)
class OkpDbService: NSObject {
  private let logger = OkpLogger(tag: "OkpDbService")
  private let E_PERMISSION_REQUIRED_TO_READ = "PERMISSION_REQUIRED_TO_READ"
  private let E_PERMISSION_REQUIRED_TO_WRITE = "PERMISSION_REQUIRED_TO_WRITE"
  private let E_FILE_NOT_FOUND = "FILE_NOT_FOUND"
  private let E_SAVE_CALL_FAILED = "SAVE_CALL_FAILED"
  private let E_CREATE_KDBX_FAILED = "CREATE_KDBX_FAILED"
  private let E_COORDINATOR_CALL_FAILED = "COORDINATOR_CALL_FAILED"
  private let E_DB_SERVICE_MODULE_ERROR = "DB_SERVICE_MODULE_ERROR"
  private let E_BOOK_MARK_STALE = "BOOK_MARK_STALE"
  private let E_BOOK_MARK_NOT_FOUND = "BOOK_MARK_NOT_FOUND"
  
  override init() {
    logger.debug("Going to call initialize from ")
    DbServiceAPI.initialize()
  }
  
  // Some test code to get language and country
  func getLocale() {
    let langs = UserDefaults.standard.object(forKey: "AppleLanguages") as! [NSObject]
    logger.debug("Langs are \(String(describing: langs))")
    
    let lan: String = langs.first as! String
    logger.debug("Lan is \(lan)")
    
    let la2 = Locale.preferredLanguages
    let c = Locale.current.regionCode
    logger.debug("Prefered Langs are \(String(describing: la2)) region code \(String(describing: c)) ")
  }
  
  @objc
  static func requiresMainQueueSetup() -> Bool {
    return true
  }
  
  @objc func constantsToExport() -> [AnyHashable: Any] {
    // getLocale()
    let b = availableBiometricType() == 0 ? "false" : "true"
    return [
      "CacheDir": NSSearchPathForDirectoriesInDomains(.cachesDirectory, .userDomainMask, true).first!,
      "DocumentDir": NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true).first!,
      "LibraryDir": NSSearchPathForDirectoriesInDomains(.libraryDirectory, .userDomainMask, true).first!,
      "MainBundleDir": Bundle.main.bundlePath,
      "Country": Locale.current.regionCode ?? "NONE", // Device country
      "Language": Locale.preferredLanguages.first!, // Device level language
      "BiometricAvailable": b
    ]
  }
  
  // UI layer needs to call to see if the app is opened by pressing a .kdbx file an
  // if that is the case, show the login dialog accordingly with the available uri
  @objc
  func kdbxUriToOpenOnCreate(_ resolve: @escaping RCTPromiseResolveBlock,
                             reject: @escaping RCTPromiseRejectBlock)
  {
    if let url = SceneDelegate.openUrl {
      _ = FileUtils.coordinatedSyncBookMarking(url: url) { url, error in
        if error == nil {
          resolve(DbServiceAPI.formJsonWithFileName(url.absoluteString))
        } else {
          reject(E_COORDINATOR_CALL_FAILED, error?.localizedDescription, error)
        }
      }
    } else {
      // Just return json map parseable string to UI
      resolve("{}")
    }
    // Ensure that we clear the url afeter UI pull call
    SceneDelegate.openUrl = nil
  }
  
  @objc
  func invokeCommand(_ commandName: String, args: String,
                     resolve: @escaping RCTPromiseResolveBlock,
                     reject _: @escaping RCTPromiseRejectBlock)
  {
    DispatchQueue.global(qos: .userInteractive).async { [unowned self] in
      logger.debug("InvokeCommand for \(commandName) called with args \(args) and delegating to api call")
      resolve(OneKeePassMobile.invokeCommand(commandName, args))
    }
  }
  
  // Called when user picked a file to save the changed kdbx during the 'Save As' call
  // This is used by user after a change detected on the previously read database
  @objc
  func completeSaveAsOnError(_ args: String,
                             resolve: @escaping RCTPromiseResolveBlock,
                             reject _: @escaping RCTPromiseRejectBlock)
  {
    DispatchQueue.global(qos: .userInteractive).async { [unowned self] in
      logger.debug("completeSaveAsOnError with args \(args) and delegating to api call")
      resolve(DbServiceAPI.completeSaveAsOnError(args))
    }
  }
  
  @objc
  func saveKdbx(_ fullFileNameUri: String, overwrite: Bool,
                resolve: @escaping RCTPromiseResolveBlock,
                reject: @escaping RCTPromiseRejectBlock)
  {
    DispatchQueue.global(qos: .userInteractive).async { [unowned self] in
      
      logger.debug("saveKdbx received fullFileNameUri is \(fullFileNameUri)")
      
      let dbFileUrl = URL(string: fullFileNameUri)
      
      let byteArray: [UInt8] = DbServiceAPI.iosSupportService().loadBookMarkData(dbFileUrl!.absoluteString)
      
      if byteArray.count > 0 {
        let bookmarkData = Data(_: byteArray)
        var isStale = false
        do {
          let burl = try URL(resolvingBookmarkData: bookmarkData, bookmarkDataIsStale: &isStale)
          // logger.debug("saveKdbx resolved url is \(burl.absoluteString)")
          if isStale {
            writeToBackupOnError(dbFileUrl?.absoluteString ?? fullFileNameUri)
            reject(E_BOOK_MARK_STALE, "Existing bookmark is stale.File selection is required before use", nil)
          } else {
            let isAccessed = burl.startAccessingSecurityScopedResource()
            defer { if isAccessed { burl.stopAccessingSecurityScopedResource() }}
            
            var error: NSError?
            NSFileCoordinator().coordinate(writingItemAt: burl, error: &error) { url in
              // logger.debug("saveKdbx in coordinate call url \(url.absoluteString)")
              resolveResponse(DbServiceAPI.saveKdbx(url.absoluteString, overwrite), resolve)
            }
            
            if error != nil {
              logger.error("In saveKdbx NSFileCoordinator().coordinate call error \(String(describing: error?.localizedDescription))")
              writeToBackupOnError(dbFileUrl?.absoluteString ?? fullFileNameUri)
              // reject(CallError.coordinateError.rawValue,CallError.coordinateError.errorDescription(error?.localizedDescription) , error)
              reject(E_COORDINATOR_CALL_FAILED, "\(String(describing: error?.localizedDescription))", error)
            }
          }
        }
        // see https://developer.apple.com/documentation/fileprovider/nsfileprovidererror/code/nosuchitem
        catch let error as NSFileProviderError where error.code == .noSuchItem {
          // This happens if a cloud file is updated after our has opened earlier. When we try to save any modiifcation
          // this error is thrown by the above URL resolvingBookmarkData call
          
          // Store the db file with changed data to backup for later offline use
          writeToBackupOnError(dbFileUrl?.absoluteString ?? fullFileNameUri)
          
          logger.error("saveKdbx:resolvingBookmarkData NSFileProviderError is \(error)")
          reject(E_FILE_NOT_FOUND, "\(error.localizedDescription)", error)
        } catch {
          // Store the db file with changed data to backup for later offline use
          writeToBackupOnError(dbFileUrl?.absoluteString ?? fullFileNameUri)
          
          // Other errors
          logger.error("saveKdbx:resolvingBookmarkData Error is \(error)")
          reject(E_SAVE_CALL_FAILED, "\(error.localizedDescription)", error)
        }
        
      } else {
        // Store the db file with changed data to backup for later offline use
        writeToBackupOnError(dbFileUrl?.absoluteString ?? fullFileNameUri)
        self.logger.error("No bookmark data is found for the url \(String(describing: dbFileUrl?.absoluteString))")
        reject(E_BOOK_MARK_NOT_FOUND, "No bookmark data is found for the url \(String(describing: dbFileUrl?.absoluteString))", nil)
      }
    }
  }
  
  @objc
  func readKdbx(_ fullFileNameUri: String, jsonArgs: String,
                resolve: @escaping RCTPromiseResolveBlock,
                reject: @escaping RCTPromiseRejectBlock)
  {
    DispatchQueue.global(qos: .userInteractive).async { [unowned self] in
      
      let dbFileUrl = URL(string: fullFileNameUri)
      guard dbFileUrl != nil else {
        reject(E_DB_SERVICE_MODULE_ERROR, "fullFileNameUri cannot be nil", nil)
        return
      }
      
      let byteArray: [UInt8] = DbServiceAPI.iosSupportService().loadBookMarkData(dbFileUrl!.absoluteString)
      
      if byteArray.count > 0 {
        let bookmarkData = Data(_: byteArray)
        var isStale = false
        do {
          let burl = try URL(resolvingBookmarkData: bookmarkData, bookmarkDataIsStale: &isStale)
          if isStale {
            reject(E_BOOK_MARK_STALE, "Existing bookmark is stale.File selection is required before use", nil)
          } else {
            let isAccessed = burl.startAccessingSecurityScopedResource()
            defer { if isAccessed { burl.stopAccessingSecurityScopedResource() }}
            
            var error: NSError?
            NSFileCoordinator().coordinate(readingItemAt: burl, error: &error) { _ in
              resolveResponse(DbServiceAPI.readKdbx(burl.absoluteString, jsonArgs), resolve)
            }
            
            if error != nil {
              logger.error("In readKdbx NSFileCoordinator().coordinate call error \(String(describing: error?.localizedDescription))")
              reject(E_COORDINATOR_CALL_FAILED, "\(String(describing: error?.localizedDescription))", error)
            }
          }
        } catch let error as NSFileProviderError where error.code == .noSuchItem {
          logger.error("readKdbx:resolvingBookmarkData NSFileProviderError is \(error)")
          reject(E_FILE_NOT_FOUND, "\(error.localizedDescription)", error)
        } catch let error as NSError {
          // See https://developer.apple.com/documentation/foundation/1448136-nserror_codes/nsfilereadnopermissionerror
          // The actual error we see is
          // Error Domain=NSCocoaErrorDomain Code=257 "The file couldn’t be opened because you don’t have permission to view it."
          // Typically this happens when user tries press on a database file on the home page databases list
          logger.error("readKdbx:resolvingBookmarkData other Error is \(error)")
          reject(E_PERMISSION_REQUIRED_TO_READ, "\(error.localizedDescription)", error)
        }
        
      } else {
        self.logger.error("No bookmark data is found for the url \(String(describing: dbFileUrl?.absoluteString))")
        reject(E_BOOK_MARK_NOT_FOUND, "No bookmark data is found for the url \(String(describing: dbFileUrl?.absoluteString))", nil)
      }
    }
  }
  
  // Called to copy the selected key file from the app's private area to any user selected location.
  // The location and uri to save is completed in 'pickKeyFileToSave' func of OkpDocumentPickerService
  @objc
  func copyKeyFile(_ fullFileNameUri: String,
                   resolve: @escaping RCTPromiseResolveBlock,
                   reject: @escaping RCTPromiseRejectBlock)
  {
    bookmarkedFileHandler(fullFileNameUri, reject) { url in
      resolve(DbServiceAPI.copyPickedKeyFile(url.absoluteString))
    }
  }
  
  // Called to upload any file as entry's attachment
  // Here the 'fullFileNameUri' is the file picked by the user to read
  @objc
  func uploadAttachment(_ fullFileNameUri: String, jsonArgs: String,
                        resolve: @escaping RCTPromiseResolveBlock,
                        reject: @escaping RCTPromiseRejectBlock)
  {
    bookmarkedFileHandler(fullFileNameUri, reject) { url in
      resolve(DbServiceAPI.uploadAttachment(url.absoluteString, jsonArgs))
    }
  }
  
  // TODO: Verify the use of  bookmarkedFileHandler in 'copyKeyFile' and 'uploadAttachment' works for both on sim and devices
  func bookmarkedFileHandler(_ fullFileNameUri: String, _ reject: @escaping RCTPromiseRejectBlock, _ handler: (URL) -> Void) {
    let keyFileUrl = URL(string: fullFileNameUri)
    guard keyFileUrl != nil else {
      reject(E_DB_SERVICE_MODULE_ERROR, "fullFileNameUri cannot be nil", nil)
      return
    }
    
    // Previously stored bookmark
    let byteArray: [UInt8] = DbServiceAPI.iosSupportService().loadBookMarkData(keyFileUrl!.absoluteString)
    
    guard byteArray.count > 0 else {
      logger.error("No bookmark data is found for the url \(String(describing: keyFileUrl?.absoluteString))")
      reject(E_BOOK_MARK_NOT_FOUND, "No bookmark data is found for the url \(String(describing: keyFileUrl?.absoluteString))", nil)
      return
    }
    
    let bookmarkData = Data(_: byteArray)
    var isStale = false
    do {
      let burl = try URL(resolvingBookmarkData: bookmarkData, bookmarkDataIsStale: &isStale)
      if isStale {
        reject(E_BOOK_MARK_STALE, "Existing bookmark is stale.File selection is required before use", nil)
      } else {
        let isAccessed = burl.startAccessingSecurityScopedResource()
        defer { if isAccessed { burl.stopAccessingSecurityScopedResource() }}
        
        var error: NSError?
        
        // Rational for the need to use NSFileCoordinator though somewhat old
        // https://karmeye.com/2014/12/18/uidocumentpicker-nsfilecoordinator/
        NSFileCoordinator().coordinate(readingItemAt: burl, error: &error, byAccessor: handler)
        
        if error != nil {
          logger.error("In copyKeyFile NSFileCoordinator().coordinate call error \(String(describing: error?.localizedDescription))")
          reject(E_COORDINATOR_CALL_FAILED, "\(String(describing: error?.localizedDescription))", error)
        }
      }
    } catch let error as NSFileProviderError where error.code == .noSuchItem {
      logger.error("copyKeyFile:resolvingBookmarkData NSFileProviderError is \(error)")
      reject(E_FILE_NOT_FOUND, "\(error.localizedDescription)", error)
    } catch {
      logger.error("copyKeyFile:resolvingBookmarkData other Error is \(error)")
      reject(E_PERMISSION_REQUIRED_TO_READ, "\(error.localizedDescription)", error)
    }
    
    // Need to delete the bookmark as we do not require this key file uri bookmark anymore
    DbServiceAPI.iosSupportService().deleteBookMarkData(keyFileUrl!.absoluteString)
  }
  
  @objc
  func authenticateWithBiometric(_ resolve: @escaping RCTPromiseResolveBlock,
                                 reject _: @escaping RCTPromiseRejectBlock)
  {
    DispatchQueue.global(qos: .userInteractive).async { [unowned self] in
      let localAuthenticationContext = LAContext()
      let reason = "Authentication is required to unlock database"
      
      localAuthenticationContext.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason) { success, evaluationError in
        if success {
          // authenticated = success
          resolve(DbServiceAPI.jsonService().okJsonString("AuthenticationSucceeded"))
        } else {
          self.logger.error("Error \(evaluationError!)")
          if let errorObj = evaluationError {
            let messageToDisplay = self.getAuthenticationErrorDescription(errorCode: errorObj._code)
            self.logger.error(messageToDisplay)
          }
          // For now, we use "AuthenticationFailed" whether user cancels the bio auth or any error in bio api call
          resolve(DbServiceAPI.jsonService().okJsonString("AuthenticationFailed"))
        }
      }
    }
  }
  
  func getAuthenticationErrorDescription(errorCode: Int) -> String {
    switch errorCode {
    case LAError.authenticationFailed.rawValue:
      return "Authentication was not successful, because user failed to provide valid credentials."
      
    case LAError.appCancel.rawValue:
      return "Authentication was canceled by application (e.g. invalidate was called while authentication was in progress)."
      
    case LAError.invalidContext.rawValue:
      return "LAContext passed to this call has been previously invalidated."
      
    case LAError.notInteractive.rawValue:
      return "Authentication failed, because it would require showing UI which has been forbidden by using interactionNotAllowed property."
      
    case LAError.passcodeNotSet.rawValue:
      return "Authentication could not start, because passcode is not set on the device."
      
    case LAError.systemCancel.rawValue:
      return "Authentication was canceled by system (e.g. another application went to foreground)."
      
    case LAError.userCancel.rawValue:
      return "Authentication was canceled by user (e.g. tapped Cancel button)."
      
    case LAError.userFallback.rawValue:
      return "Authentication was canceled, because the user tapped the fallback button (Enter Password)."
      
    default:
      return "Error code \(errorCode) not found"
    }
  }
  
  func writeToBackupOnError(_ fullFileNameUri: String) {
    // For now we are ignoring any backup writting error here write_to_backup_on_error
    let apiResponse = DbServiceAPI.writeToBackupOnError(fullFileNameUri)
    switch apiResponse {
    case let .success(result):
      logger.debug("Backup is written and api response is \(result)")
    case let .failure(result):
      logger.error("Backup writting failed with error \(result)")
    }
  }
  
  func resolveResponse(_ apiResponse: ApiResponse, _ resolve: RCTPromiseResolveBlock) {
    switch apiResponse {
    case let .success(result):
      resolve(result)
    case let .failure(result):
      resolve(result)
    }
  }
  
  func availableBiometricType() -> Int {
    let localAuthenticationContext = LAContext()
    var authorizationError: NSError?
    var supportedType: Int?
    
    if localAuthenticationContext.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &authorizationError) {
      switch localAuthenticationContext.biometryType {
      case .faceID:
        logger.info("Supported Biometric type is: faceID")
        supportedType = 2
      case .touchID:
        logger.info("Supported Biometric type is: touchID")
        supportedType = 1
      case .none:
        logger.info("No biometeric")
        supportedType = 0
      @unknown default:
        logger.info("@unknown biometeric")
        supportedType = 0
      }
    }
    
    if authorizationError != nil {
      logger.error("authorizationError is \(String(describing: authorizationError))")
      return 0
    }
    return supportedType ?? 0
  }
  
  /// All app group realted API calls
  
  @objc
  func autoFillInvokeCommand(_ commandName: String, args: String, resolve: @escaping RCTPromiseResolveBlock,reject _: @escaping RCTPromiseRejectBlock)
  {
    DispatchQueue.global(qos: .userInteractive).async { [unowned self] in
      logger.debug("InvokeCommand for \(commandName) called with args \(args) and delegating to api call")
      resolve(DbServiceAPI.iosAppGroupSupportService().invoke(commandName, args))
    }
  }
  
}

/*
 
 //  @objc
 //  func copyFileToAppGroup(_ args: String, resolve: @escaping RCTPromiseResolveBlock, reject _: @escaping RCTPromiseRejectBlock)
 //  {
 //    DispatchQueue.global(qos: .userInteractive).async { [unowned self] in
 //      logger.debug("copyFileToAppGroup called with args \(args) and delegating to api call")
 //      resolve(DbServiceAPI.iosAppGroupSupportService().copyFilesToAppGroup(args))
 //    }
 //  }
 //
 //  @objc
 //  func deleteAppGroupFiles(_ args: String, resolve : @escaping RCTPromiseResolveBlock, reject _: @escaping RCTPromiseRejectBlock) {
 //    DispatchQueue.global(qos: .userInteractive).async { [unowned self] in
 //      logger.debug("deleteAppGroupFiles called with args \(args) and delegating to api call")
 //      resolve(DbServiceAPI.iosAppGroupSupportService().deleteCopiedAutofillDetails(args))
 //    }
 //  }
   
   

  Before reusing common functionality with copyKeyFile and uploadAttachment with
  @objc
  func copyKeyFile(_ fullFileNameUri: String,
                   resolve: @escaping RCTPromiseResolveBlock,
                   reject: @escaping RCTPromiseRejectBlock)
  {
    let keyFileUrl = URL(string: fullFileNameUri)
    guard keyFileUrl != nil else {
      reject(E_DB_SERVICE_MODULE_ERROR, "fullFileNameUri cannot be nil", nil)
      return
    }

    // Previously stored bookmark
    let byteArray: [UInt8] = DbServiceAPI.iosSupportService().loadBookMarkData(keyFileUrl!.absoluteString)

    guard byteArray.count > 0 else {
      logger.error("No bookmark data is found for the url \(String(describing: keyFileUrl?.absoluteString))")
      reject(E_BOOK_MARK_NOT_FOUND, "No bookmark data is found for the url \(String(describing: keyFileUrl?.absoluteString))", nil)
      return
    }

    let bookmarkData = Data(_: byteArray)
    var isStale = false
    do {
      let burl = try URL(resolvingBookmarkData: bookmarkData, bookmarkDataIsStale: &isStale)
      if isStale {
        reject(E_BOOK_MARK_STALE, "Existing bookmark is stale.File selection is required before use", nil)
      } else {
        let isAccessed = burl.startAccessingSecurityScopedResource()
        defer { if isAccessed { burl.stopAccessingSecurityScopedResource() }}

        var error: NSError?
        NSFileCoordinator().coordinate(readingItemAt: burl, error: &error) { _ in
          resolve(DbServiceAPI.copyPickedKeyFile(keyFileUrl!.absoluteString))
        }

        if error != nil {
          logger.error("In copyKeyFile NSFileCoordinator().coordinate call error \(String(describing: error?.localizedDescription))")
          reject(E_COORDINATOR_CALL_FAILED, "\(String(describing: error?.localizedDescription))", error)
        }
      }
    } catch let error as NSFileProviderError where error.code == .noSuchItem {
      logger.error("copyKeyFile:resolvingBookmarkData NSFileProviderError is \(error)")
      reject(E_FILE_NOT_FOUND, "\(error.localizedDescription)", error)
    } catch {
      logger.error("copyKeyFile:resolvingBookmarkData other Error is \(error)")
      reject(E_PERMISSION_REQUIRED_TO_READ, "\(error.localizedDescription)", error)
    }

    // Need to delete the bookmark as we do not require this key file uri bookmark anymore
    DbServiceAPI.iosSupportService().deleteBookMarkData(keyFileUrl!.absoluteString)

    /*
     if byteArray.count > 0 {
       let bookmarkData = Data(_: byteArray)
       var isStale = false
       do {
         let burl = try URL(resolvingBookmarkData: bookmarkData, bookmarkDataIsStale: &isStale)
         if isStale {
           reject(E_BOOK_MARK_STALE, "Existing bookmark is stale.File selection is required before use", nil)
         } else {
           let isAccessed = burl.startAccessingSecurityScopedResource()
           defer { if isAccessed { burl.stopAccessingSecurityScopedResource() }}

           var error: NSError?
           NSFileCoordinator().coordinate(readingItemAt: burl, error: &error) { _ in
             resolve(DbServiceAPI.copyPickedKeyFile(keyFileUrl!.absoluteString))
           }

           if error != nil {
             logger.error("In copyKeyFile NSFileCoordinator().coordinate call error \(String(describing: error?.localizedDescription))")
             reject(E_COORDINATOR_CALL_FAILED, "\(String(describing: error?.localizedDescription))", error)
           }
         }
       } catch let error as NSFileProviderError where error.code == .noSuchItem {
         logger.error("copyKeyFile:resolvingBookmarkData NSFileProviderError is \(error)")
         reject(E_FILE_NOT_FOUND, "\(error.localizedDescription)", error)
       } catch {
         logger.error("copyKeyFile:resolvingBookmarkData other Error is \(error)")
         reject(E_PERMISSION_REQUIRED_TO_READ, "\(error.localizedDescription)", error)
       }

     } else {
       self.logger.error("No bookmark data is found for the url \(String(describing: keyFileUrl?.absoluteString))")
       reject(E_BOOK_MARK_NOT_FOUND, "No bookmark data is found for the url \(String(describing: keyFileUrl?.absoluteString))", nil)
     }
      */

 //    // Rational for the need to use NSFileCoordinator though somewhat old
 //    // http://karmeye.com/2014/12/18/uidocumentpicker-nsfilecoordinator/
 //
 //    let isAccessed =  keyFileUrl!.startAccessingSecurityScopedResource()
 //    defer { if isAccessed {  keyFileUrl!.stopAccessingSecurityScopedResource() }}
 //
 //    var error: NSError?
 //    NSFileCoordinator().coordinate(readingItemAt: keyFileUrl!, error: &error) { _ in
 //      resolve(DbServiceAPI.copyPickedKeyFile(keyFileUrl!.absoluteString))
 //    }
 //
 //    if error != nil {
 //      logger.error("In readKdbx NSFileCoordinator().coordinate call error \(String(describing: error?.localizedDescription))")
 //      reject(E_COORDINATOR_CALL_FAILED, "\(String(describing: error?.localizedDescription))", error)
 //    }
  }

  */

/*

 @objc
 func copyKeyFile(_ fullFileNameUri: String,
                  resolve: @escaping RCTPromiseResolveBlock,
                  reject: @escaping RCTPromiseRejectBlock) {

   let keyFileUrl = URL(string: fullFileNameUri)
   guard keyFileUrl != nil else {
     reject(E_DB_SERVICE_MODULE_ERROR, "fullFileNameUri cannot be nil", nil)
     return
   }

   // Rational for the need to use NSFileCoordinator though somewhat old
   // http://karmeye.com/2014/12/18/uidocumentpicker-nsfilecoordinator/

   let isAccessed =  keyFileUrl!.startAccessingSecurityScopedResource()
   defer { if isAccessed {  keyFileUrl!.stopAccessingSecurityScopedResource() }}

   var error: NSError?
   NSFileCoordinator().coordinate(readingItemAt: keyFileUrl!, error: &error) { _ in
     resolve(DbServiceAPI.copyPickedKeyFile(keyFileUrl!.absoluteString))
   }

   if error != nil {
     logger.error("In readKdbx NSFileCoordinator().coordinate call error \(String(describing: error?.localizedDescription))")
     reject(E_COORDINATOR_CALL_FAILED, "\(String(describing: error?.localizedDescription))", error)
   }
 }
 */
