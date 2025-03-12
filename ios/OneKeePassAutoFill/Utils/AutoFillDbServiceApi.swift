import Foundation

// Need to copy this from OneKeePassMobile as we need to use
// Target name prefix to rust global functions

// Use #if OKP_APP_EXTENSION checking to use either 'OneKeePassMobile' or 'OneKeePassAutoFill'
// See also comments in 'DbServiceAPI.swift' 

// Only one logger with variable 'cmnLogger' be declared
let cmnLogger = OkpLogger(tag: "common ios ffi")

class AutoFillDbServiceAPI {
  static let logger = OkpLogger(tag: "AutoFillDbServiceAPI")
  
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
      let secKeyOps = SecureKeyOperationImpl()
      let eventDispatcher = BackendEventDispatcher()
      
      // ApiCallBackService implements the protocols IosApiService,CommonDeviceServiceEx
      let apiCallBackService = ApiCallBackService()
      iosCallbackServiceInitialize(apiCallBackService)
      
      let securEnclaveService = SecureEnclaveServiceSupport()
      
      dbServiceInitialize(cmnService, secKeyOps, eventDispatcher,apiCallBackService,securEnclaveService)
            
      initialized = true
      Swift.debugPrint("API initialize is done")
    } else {
      Swift.debugPrint("API initialize is alredy done")
    }
  }

  static func iosSupportService() -> IosSupportService {
    _iosSupportService
  }
  
  static func jsonService() -> JsonService {
    _jsonService
  }
  
  // IMPORTANT:
  // 'IosSupportService()' is swift class contruction  whereas 'iosSupportService()' is func defined here
  // Note the diff between 'Ios' (First letter is uppercase) vs 'ios' (all are lowercase)
  static func iosAppGroupSupportService() -> IosAppGroupSupportService {
    _iosAppGroupSupportService
  }

  static func formJsonWithFileName(_ fullFileName: String) -> String {
    _jsonService.formWithFileName(fullFileName)
  }

  static func createTempKdbx(_ tempFileUri: String, _ args: String) -> ApiResponse {
    let fileArgs = FileArgs.fullFileName(fullFileName: tempFileUri)
    return OneKeePassAutoFill.createTempKdbx(fileArgs, args)
  }

  static func readKdbx(_ dbFileName: String, _ jsonArgs: String) -> ApiResponse {
    let fileArgs = FileArgs.fullFileName(fullFileName: dbFileName)
    return OneKeePassAutoFill.readKdbx(fileArgs, jsonArgs)
  }

  static func saveKdbx(_ fullFileName: String, _ overwrite: Bool) -> ApiResponse {
    let fileArgs = FileArgs.fullFileName(fullFileName: fullFileName)
    return OneKeePassAutoFill.saveKdbx(fileArgs, overwrite)
  }
  
  static func copyPickedKeyFile(_ fullFileName: String) -> String {
    let fileArgs = FileArgs.fullFileName(fullFileName: fullFileName)
    return OneKeePassAutoFill.copyPickedKeyFile(fileArgs)
  }
  
  static func uploadAttachment(_ fullFileName: String, _ jsonArgs: String) -> String {
    let fileArgs = FileArgs.fullFileName(fullFileName: fullFileName)
    return OneKeePassAutoFill.uploadAttachment(fileArgs, jsonArgs)
  }
  
  static func completeSaveAsOnError(_ jsonArgs: String) -> String {
    _iosSupportService.completeSaveAsOnError(jsonArgs)
  }
  
  static func writeToBackupOnError(_ fullFileName: String) -> ApiResponse {
    return OneKeePassAutoFill.writeToBackupOnError(fullFileName)
  }
  
  static func invokeCommand(_ commandName: String, _ args: String) -> String {
    OneKeePassAutoFill.invokeCommand(commandName, args)
    
    // https://matteomanferdini.com/swift-switch/
    
//    switch (commandName, args) {
//    case let ("list_app_group_db_files", args):
//      return _iosSupportService.listAppGroupDbFiles()
//
//    case let ("all_entries_on_db_open", args):
//      return _iosSupportService.allEntriesOnDbOpen(args)
//
//    case let ("read_kdbx_from_app_group", args):
//      return _iosSupportService.readKdbxFromAppGroup(args)
//
//    default:
//      return OneKeePassAutoFill.invokeCommand(commandName, args)
//    }
  }
}

enum CallbackErrors: Error {
  case apiIsNotSupported
}

class CommonDeviceServiceImpl: CommonDeviceService {
  
  // For now, this is not required for AutoFill
  func loadResourceWordlist(_ wordlistFileName: String) throws -> String {
    return ""
  }
  
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
    
    return NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true).first!
  }
  
  func cacheDir() -> String {
    NSSearchPathForDirectoriesInDomains(.cachesDirectory, .userDomainMask, true).first!
  }
  
  func tempDir() -> String {
    let tempDirectoryPath = URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
    return tempDirectoryPath.absoluteString
  }
  
  func appGroupHomeDir() -> String? {
    // https://developer.apple.com/documentation/foundation/filemanager/1412643-containerurl
    // The system creates only the Library/Caches subdirectory automatically
    guard let appGroupContainerUrl = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: "group.com.onekeepass.afshared") else {
      return nil
    }
    cmnLogger.debug("appGroupContainerUrl is \(appGroupContainerUrl)")
    return appGroupContainerUrl.absoluteString
  }
  
  func loadLanguageTranslation(_ languageId: String) -> String? {
    var p = Bundle.main.path(forResource: languageId, ofType: "json", inDirectory: "Translations")
    
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

  func uriToFileName(_ fullFileNameUri: String) -> String? {
    let file_url = URL(string: fullFileNameUri)
    return file_url?.lastPathComponent
  }

  func uriToFileInfo(_ fullFileNameUri: String) -> FileInfo? {
    let attributes: URLResourceValues
    let location = OneKeePassAutoFill.extractFileProvider(fullFileNameUri)
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
                              lastModified: Int64((attributes.contentModificationDate?.timeIntervalSince1970 ?? -1) * 1000),
                              location: location)
      return fileInfo

    } catch {
      AutoFillDbServiceAPI.logger.error("Failed to get file info [reason: \(error.localizedDescription)]")
      return FileInfo(fileName: nil,
                      fileSize: nil,
                      lastModified: nil,
                      location: location)
    }
  }
}
