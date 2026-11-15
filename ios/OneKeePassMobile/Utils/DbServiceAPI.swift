import Foundation

// TODO:
// For now DbServiceAPI is duplicated between main app and extension (named as AutoFillDbServiceAPI)
// This was done mainly because of accessin global level functions under the name 'OneKeePassMobile' vs 'OneKeePassAutoFill'
// Need to move all common functions to a common class and share between two

// Use #if OKP_APP_EXTENSION checking to use either 'OneKeePassMobile' or 'OneKeePassAutoFill'

// Only one logger with variable 'cmnLogger' be declared
let cmnLogger = OkpLogger(tag: "common ios ffi")

class DbServiceAPI {
  static let logger = OkpLogger(tag: "DbServiceAPI")
  
  // These are implemented in Rust
  private static var _iosSupportService = IosSupportService()
  private static var _iosAppGroupSupportService = IosAppGroupSupportService()
  private static var _jsonService = JsonService()

  static var initialized = false

  private init() {
    dbServiceEnableLogging()
    Swift.debugPrint("dbServiceEnableLogging is called")
  }

  deinit {}

  static func initialize() {
    if !initialized {
      Swift.debugPrint("Calling dbServiceEnableLogging...")
      dbServiceEnableLogging()
      Swift.debugPrint("dbServiceEnableLogging call is done")
      let cmnService = CommonDeviceServiceImpl()
      let secKeyOps = SecureKeyOperationImpl.shared // SecureKeyOperationImpl()
      let eventDispatcher = BackendEventDispatcher()
      
      // ApiCallBackService implements the protocols IosApiService,CommonDeviceServiceEx
      let apiCallBackService = ApiCallBackService()
      iosCallbackServiceInitialize(apiCallBackService)
      
      let securEnclaveService = SecureEnclaveServiceSupport()
      
      // App home dir move (v0.25.0)
      // Anything still in the other home location is moved to the current one by the rust side
      let initOptions = PlatformInitOptions(previousAppHomeDir: AppHomeLocation.previous)

      dbServiceInitialize(cmnService, secKeyOps, eventDispatcher, apiCallBackService, securEnclaveService, initOptions)

      BackupExclusion.apply()

      initialized = true
      Swift.debugPrint("API initialize is done")
    } else {
      Swift.debugPrint("API initialize is alredy done")
    }
  }

  static func iosSupportService() -> IosSupportService {
    _iosSupportService
  }

  // IMPORTANT:
  // 'IosSupportService()' is swift class contruction  whereas 'iosSupportService()' is func defined here
  // Note the diff between 'Ios' (First letter is uppercase) vs 'ios' (all are lowercase)
  static func iosAppGroupSupportService() -> IosAppGroupSupportService {
    _iosAppGroupSupportService
  }
  
  static func jsonService() -> JsonService {
    _jsonService
  }

  static func formJsonWithFileName(_ fullFileName: String) -> String {
    _jsonService.formWithFileName(fullFileName)
  }

  // Used when another app hands over a db file as a copy instead of opening it in place.
  // We do not open such a copy and so there is no url to pass on. Only the file name is
  // sent so that the UI can name the file in the message asking the user to open the
  // database from its own location
  static func formJsonCopyHandedOver(_ fullFileName: String) -> String {
    // This is how the file name part is formed in 'formWithFileName' - see uriToFileName
    let fileName = URL(string: fullFileName)?.lastPathComponent ?? ""

    return _jsonService.mapAsOkJsonString([
      "file_name": fileName,
      "copy_handed_over": "true",
    ])
  }

  static func createTempKdbx(_ tempFileUri: String, _ args: String) -> ApiResponse {
    let fileArgs = FileArgs.fullFileName(fullFileName: tempFileUri)
    return OneKeePassMobile.createTempKdbx(fileArgs, args)
  }

  static func readKdbx(_ dbFileName: String, _ jsonArgs: String) -> ApiResponse {
    let fileArgs = FileArgs.fullFileName(fullFileName: dbFileName)
    return OneKeePassMobile.readKdbx(fileArgs, jsonArgs)
  }

  static func saveKdbx(_ fullFileName: String, _ overwrite: Bool) -> ApiResponse {
    let fileArgs = FileArgs.fullFileName(fullFileName: fullFileName)
    return OneKeePassMobile.saveKdbx(fileArgs, overwrite)
  }
  
  static func copyPickedKeyFile(_ fullFileName: String) -> String {
    let fileArgs = FileArgs.fullFileName(fullFileName: fullFileName)
    return OneKeePassMobile.copyPickedKeyFile(fileArgs)
  }
  
  static func uploadAttachment(_ fullFileName: String, _ jsonArgs: String) -> String {
    let fileArgs = FileArgs.fullFileName(fullFileName: fullFileName)
    return OneKeePassMobile.uploadAttachment(fileArgs, jsonArgs)
  }

  static func addCustomIconFromFile(_ fullFileName: String, _ jsonArgs: String) -> String {
    let fileArgs = FileArgs.fullFileName(fullFileName: fullFileName)
    return OneKeePassMobile.addCustomIconFromFile(fileArgs, jsonArgs)
  }
  
  static func handlePickedFile(_ fullFileName: String, _ jsonArgs: String) -> String {
    let fileArgs = FileArgs.fullFileName(fullFileName: fullFileName)
    return OneKeePassMobile.handlePickedFile(fileArgs, jsonArgs)
  }
  
  static func completeSaveAsOnError(_ jsonArgs: String) -> String {
    _iosSupportService.completeSaveAsOnError(jsonArgs)
  }
  
  static func writeToBackupOnError(_ fullFileName: String) -> ApiResponse {
    return OneKeePassMobile.writeToBackupOnError(fullFileName)
  }
}

enum CallbackErrors: Error {
  case apiIsNotSupported
}

// App home dir move (v0.25.0). 'previous' must keep returning 'documents' while users may still
// update from a version before v0.25.0. The OKP_HOME_IN_DOCUMENTS toggle can be removed earlier
// The main app's home dir for its internal dirs and files (bookmarks, backups, export_data,
// remote_storage). It was the app sandbox's 'Documents' and is now 'okp_main' in the app group
// container, which keeps these out of the dir meant for user visible documents.
// The AutoFill extension does not use this and stays on its own 'Documents'
enum AppHomeLocation {
  static let documents: String = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true).first!

  // nil only when the app group entitlement is missing
  static let okpMain: String? = FileManager.default
    .containerURL(forSecurityApplicationGroupIdentifier: "group.com.onekeepass.afshared")?
    .appendingPathComponent("okp_main", isDirectory: true).path

  // App home dir move (v0.25.0): Debug toggle, removable once the move is verified on devices
  // OKP_HOME_IN_DOCUMENTS is set only in the Debug configuration to verify the move both ways.
  // Without it the app group location is used
  static var current: String {
    #if OKP_HOME_IN_DOCUMENTS
    return documents
    #else
    return okpMain ?? documents
    #endif
  }

  // The location not in use. Anything found there is moved to 'current' on app start
  static var previous: String? {
    guard let okpMain else { return nil }
    return current == okpMain ? documents : okpMain
  }
}

// Leaves the dirs below out of iCloud and computer backups. Everything else is backed up, which
// is meant for 'okp_shared/preference.json', 'okp_shared/key_files' and the app home's 'backups'.
// A dir is marked rather than its files, as the rust side often writes a file to a temp file and
// renames it over the old one, which drops a mark set on the file. The mark is set on every app
// start, as a dir can be created again (e.g after a reset). A missing dir is created here so that
// it is marked before anything (e.g the AutoFill extension) writes into it.
// The dir names are the same as the constants used on the rust side (app_state.rs,
// autofill_app_group.rs, passkey_service.rs, otp_identity_service.rs)
enum BackupExclusion {
  static let logger = OkpLogger(tag: "BackupExclusion")

  // Relative to the main app's home dir
  // 'remote_storage' : its configs are encrypted with a key that never leaves this device and the
  // sftp private keys are in plain text. 'bookmarks' : security scoped bookmarks are stale on
  // another device. 'export_data' : temporary files for sharing
  private static let appHomeDirs = ["remote_storage", "bookmarks", "export_data"]

  // Relative to the app group container
  // 'okp' : AutoFill's copies of dbs, made again from the main app. 'pending_passkeys' holds private
  // keys in plain text. The registered ids mirror the system credential identity store of this device
  private static let appGroupDirs = [
    "okp",
    "okp_shared/pending_passkeys",
    "okp_shared/registered_passkey_ids",
    "okp_shared/registered_otp_ids",
  ]

  // Must be called after 'dbServiceInitialize', so that the app home dir has been set up
  static func apply() {
    var paths = appHomeDirs.map { (AppHomeLocation.current as NSString).appendingPathComponent($0) }
    if let groupRoot = FileManager.default
      .containerURL(forSecurityApplicationGroupIdentifier: "group.com.onekeepass.afshared")?.path
    {
      paths += appGroupDirs.map { (groupRoot as NSString).appendingPathComponent($0) }
    }

    var marked: [String] = []
    for path in paths {
      var url = URL(fileURLWithPath: path, isDirectory: true)
      do {
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try url.setResourceValues(values)
        // Read back so that the log shows the mark as iOS has stored it
        let stored = try url.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup ?? false
        if stored {
          marked.append(path)
        } else {
          logger.error("Backup exclusion is not stored for \(path)")
        }
      } catch {
        logger.error("Setting backup exclusion for \(path) failed: \(error)")
      }
    }
    logger.info("Excluded from backup: \(marked)")
  }
}

class CommonDeviceServiceImpl: CommonDeviceService {
 
  func appHomeDir() -> String {
//    let  burl = Bundle.main.bundleURL
//    cmnLogger.debug("$$$$$ bundleURL is \(burl)")
//
//    let resUrl = Bundle.main.resourceURL
//
//    cmnLogger.debug("$$$$$ resourceURL is \(burl)")
//
//    var p = Bundle.main.path(forResource: "en", ofType: "json", inDirectory: "Translations")
//
//    cmnLogger.debug("$$$$$ orResource is \(String(describing: p))")
//
    
    AppHomeLocation.current
  }
  
  func cacheDir() -> String {
    NSSearchPathForDirectoriesInDomains(.cachesDirectory, .userDomainMask, true).first!
  }
  
  func tempDir() -> String {
    let tempDirectoryPath = URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
    return tempDirectoryPath.absoluteString
  }
  
  func appGroupHomeDir() -> String? {
    guard let appGroupContainerUrl = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: "group.com.onekeepass.afshared") else {
      return nil
    }
    cmnLogger.debug("appGroupContainerUrl is \(appGroupContainerUrl)")
    return appGroupContainerUrl.absoluteString
  }
  
  func loadLanguageTranslation(_ languageId: String) -> String? {
    // var p = Bundle.main.path(forResource: languageId, ofType: "json", inDirectory: "Translations")
    
    // Or use if let jsonFileURL = Bundle.main.path(forResource: languageId, ofType: "json", inDirectory: "Translations") {}
    // Or use if let jsonFileURL = Bundle.main.url(forResource: languageId, ofType: "json", subdirectory: "Translations") {}
    
    let jsonFileURL = Bundle.main.url(forResource: languageId, withExtension: "json", subdirectory: "Translations")
    guard jsonFileURL != nil else {
      return nil
    }
    
    cmnLogger.debug("Translation jsonFileURL for language \(languageId) is \(String(describing: jsonFileURL))")
    
    if let fileContents = try? String(contentsOf: jsonFileURL!) {
      return fileContents
    }
    
    return nil
  }
  
  func loadResourceWordlist(_ wordlistFileName: String) throws -> String {
    cmnLogger.debug("loadWordlist is called with \(wordlistFileName)")
    let wordlistFileURL = Bundle.main.url(forResource: wordlistFileName, withExtension: "txt", subdirectory: "wordlists")
    
    guard wordlistFileURL != nil else {
      cmnLogger.debug("Bundle.main.url for \(wordlistFileName) failed")
      throw ApiCallbackError.InternalCallbackError(reason: "Word list file \(wordlistFileName) is not found ")
    }
    
    cmnLogger.debug("Word list file  \(wordlistFileName)  full path is \(String(describing: wordlistFileURL))")
    
    if let fileContents = try? String(contentsOf: wordlistFileURL!) {
      return fileContents
    } else {
      throw ApiCallbackError.InternalCallbackError(reason: "Word list file \(wordlistFileName) loading failed")
    }
  }
  
  func uriToFileName(_ fullFileNameUri: String) -> String? {
    // cmnLogger.debug("Going to get the file name part of fullFileNameUri passed \(fullFileNameUri) ")
    
    let file_url = URL(string: fullFileNameUri)
    return file_url?.lastPathComponent
    
  }

  func uriToFileInfo(_ fullFileNameUri: String) -> FileInfo? {
    let attributes: URLResourceValues
    let location = OneKeePassMobile.extractFileProvider(fullFileNameUri)
    do {
      let attributeKeys: Set<URLResourceKey> = [
        .fileSizeKey,
        .contentModificationDateKey,
        .isExcludedFromBackupKey,
      ]
      let db_file_url = URL(string: fullFileNameUri)
      attributes = try db_file_url!.resourceValues(forKeys: attributeKeys)

      // timeIntervalSince1970 is the interval between the date value and 00:00:00 UTC on 1 January 1970
      // It is a Double type in seconds; it yields sub-millisecond precision over a range of 10,000 years.
      // Multiplying the interval by 1000 gives the whole interval in milliseconds sice 00:00:00 UTC on 1 January 1970
      let fileInfo = FileInfo(fileName: db_file_url?.lastPathComponent,
                              fileSize: Int64(attributes.fileSize ?? -1),
                              // Timestamp when a document was last modified, in milliseconds since January 1, 1970 00:00:00.0 UTC
                              lastModified: Int64((attributes.contentModificationDate?.timeIntervalSince1970 ?? -1) * 1000),
                              location: location)
      return fileInfo

    } catch {
      DbServiceAPI.logger.error("Failed to get file info [reason: \(error.localizedDescription)]")
      return FileInfo(fileName: nil,
                      fileSize: nil,
                      lastModified: nil,
                      location: location)
    }
  }
}
