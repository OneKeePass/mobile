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
      
      dbServiceInitialize(cmnService, secKeyOps, eventDispatcher, apiCallBackService, securEnclaveService)
      
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
    
    // TODO:
    // Need to use "Libary" or "Library/Application Support"  instead of using 'Documents' as app_home dir
    // Existing prefernce.json, bookmarked,backup and keyfiles are need to be copied to the new app_home accordingly
    // See
    // https://developer.apple.com/library/archive/documentation/FileManagement/Conceptual/FileSystemProgrammingGuide/FileSystemOverview/FileSystemOverview.html#//apple_ref/doc/uid/TP40010672-CH2-SW13
    
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
