import Foundation

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
    return [
      "CacheDir": NSSearchPathForDirectoriesInDomains(.cachesDirectory, .userDomainMask, true).first!,
      "DocumentDir": NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true).first!,
      "LibraryDir": NSSearchPathForDirectoriesInDomains(.libraryDirectory, .userDomainMask, true).first!,
      "MainBundleDir": Bundle.main.bundlePath,
      "Country": Locale.current.regionCode ?? "NONE", // Device country
      "Language": Locale.preferredLanguages.first! // Device level language
    ]
  }

  // UI layer needs to call to see if the app is opened by pressing a .kdbx file an
  // if that is the case, show the login dialog accordingly with the available uri
  @objc
  func kdbxUriToOpenOnCreate(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
    if let url = SceneDelegate.openUrl {
      _ = FileUtils.coordinatedSyncBookMarking(url: url) { url, error in
        if error == nil {
          resolve(DbServiceAPI.formJsonWithFileName(url.absoluteString))
        } else {
          reject(E_COORDINATOR_CALL_FAILED, error?.localizedDescription, error)
        }
      }
    } else {
      resolve("{}")
    }
    // Ensure that we clear the url afeter UI pull call
    SceneDelegate.openUrl = nil
  }

  @objc
  func invokeCommand(_ commandName: String, args: String, resolve: @escaping RCTPromiseResolveBlock, reject _: @escaping RCTPromiseRejectBlock) {
    DispatchQueue.global(qos: .userInteractive).async { [unowned self] in
      logger.debug("InvokeCommand for \(commandName) called with args \(args) and delegating to api call")
      resolve(OneKeePassMobile.invokeCommand(commandName, args))
    }
  }
  
  @objc
  func completeSaveAsOnError(_ args: String, resolve: @escaping RCTPromiseResolveBlock, reject _: @escaping RCTPromiseRejectBlock) {
    DispatchQueue.global(qos: .userInteractive).async { [unowned self] in
      logger.debug("completeSaveAsOnError with args \(args) and delegating to api call")
      resolve(DbServiceAPI.completeSaveAsOnError(args))
    }
  }

  @objc
  func saveKdbx(_ fullFileNameUri: String, overwrite: Bool,resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
    DispatchQueue.global(qos: .userInteractive).async { [unowned self] in
      
      logger.debug("saveKdbx received fullFileNameUri is \(fullFileNameUri)")

      let db_file_url = URL(string: fullFileNameUri)

      let byteArray: [UInt8] = DbServiceAPI.iosSupportService().loadBookMarkData(db_file_url!.absoluteString)

      if byteArray.count > 0 {
        let bookmarkData = Data(_: byteArray)
        var isStale = false
        do {
          let burl = try URL(resolvingBookmarkData: bookmarkData, bookmarkDataIsStale: &isStale)
          logger.debug("saveKdbx resolved url is \(burl.absoluteString)")
          if isStale {
            writeToBackupOnError(db_file_url?.absoluteString ?? fullFileNameUri)
            reject(E_BOOK_MARK_STALE, "Existing bookmark is stale.File selection is required before use", nil)
          } else {
            let isAccessed = burl.startAccessingSecurityScopedResource()
            defer { if isAccessed { burl.stopAccessingSecurityScopedResource() }}

            var error: NSError?
            NSFileCoordinator().coordinate(writingItemAt: burl, error: &error) { url in
              logger.debug("saveKdbx in coordinate call url \(url.absoluteString)")
              resolveResponse(DbServiceAPI.saveKdbx(url.absoluteString,overwrite), resolve)
            }

            if error != nil {
              logger.error("In saveKdbx NSFileCoordinator().coordinate call error \(String(describing: error?.localizedDescription))")
              writeToBackupOnError(db_file_url?.absoluteString ?? fullFileNameUri)
              // reject(CallError.coordinateError.rawValue,CallError.coordinateError.errorDescription(error?.localizedDescription) , error)
              reject(E_COORDINATOR_CALL_FAILED, "\(String(describing: error?.localizedDescription))", error)
            }
          }

        }
        //see https://developer.apple.com/documentation/fileprovider/nsfileprovidererror/code/nosuchitem
        catch let error as NSFileProviderError where error.code == .noSuchItem {
          // This happens if a cloud file is updated after our has opened earlier. When we try to save any modiifcation
          // this error is thrown by the above URL resolvingBookmarkData call
          
          // Store the db file with changed data to backup for later offline use
          writeToBackupOnError(db_file_url?.absoluteString ?? fullFileNameUri)
          
          logger.error("saveKdbx:resolvingBookmarkData NSFileProviderError is \(error)")
          reject(E_FILE_NOT_FOUND, "\(error.localizedDescription)",error)
        }
        catch let error {
          // Store the db file with changed data to backup for later offline use
          writeToBackupOnError(db_file_url?.absoluteString ?? fullFileNameUri)
          
          //Other errors
          logger.error("saveKdbx:resolvingBookmarkData Error is \(error)")
          reject(E_SAVE_CALL_FAILED, "\(error.localizedDescription)", error)
        }

      } else {
        //Store the db file with changed data to backup for later offline use
        writeToBackupOnError(db_file_url?.absoluteString ?? fullFileNameUri)
        self.logger.error("No bookmark data is found for the url \(String(describing: db_file_url?.absoluteString))")
        reject(E_BOOK_MARK_NOT_FOUND, "No bookmark data is found for the url \(String(describing: db_file_url?.absoluteString))", nil)
      }
    }
  }

  @objc
  func readKdbx(_ fullFileNameUri: String, jsonArgs: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
    DispatchQueue.global(qos: .userInteractive).async { [unowned self] in

      let db_file_url = URL(string: fullFileNameUri)
      guard db_file_url != nil else {
        reject(E_DB_SERVICE_MODULE_ERROR, "fullFileNameUri cannot be nil", nil)
        return
      }

      let byteArray: [UInt8] = DbServiceAPI.iosSupportService().loadBookMarkData(db_file_url!.absoluteString)

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

        }
        catch let error as NSFileProviderError where error.code == .noSuchItem {
          logger.error("readKdbx:resolvingBookmarkData NSFileProviderError is \(error)")
          reject(E_FILE_NOT_FOUND, "\(error.localizedDescription)",error)
        }
        catch let error {
          logger.error("readKdbx:resolvingBookmarkData other Error is \(error)")
          reject(E_PERMISSION_REQUIRED_TO_READ, "\(error.localizedDescription)", error)
        }

      } else {
        self.logger.error("No bookmark data is found for the url \(String(describing: db_file_url?.absoluteString))")
        reject(E_BOOK_MARK_NOT_FOUND, "No bookmark data is found for the url \(String(describing: db_file_url?.absoluteString))", nil)
      }
    }
  }
  
  func writeToBackupOnError(_ fullFileNameUri:String) {
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
}
