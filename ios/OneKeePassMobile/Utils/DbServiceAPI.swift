import Foundation


// Only one logger with variable 'cmnLogger' be declared
let cmnLogger = OkpLogger(tag: "common ios ffi")

class DbServiceAPI {
  static let logger = OkpLogger(tag: "DbServiceAPI")
  
  private static var _iosSupportService = IosSupportService()
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
      dbServiceInitialize(cmnService,secKeyOps,eventDispatcher)
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
    return OneKeePassMobile.saveKdbx(fileArgs,overwrite)
  }
  
  static func copyPickedKeyFile(_ fullFileName: String) -> String {
    let fileArgs = FileArgs.fullFileName(fullFileName: fullFileName)
    return OneKeePassMobile.copyPickedKeyFile(fileArgs)
  }
  
  static func uploadAttachment(_ fullFileName: String,_ jsonArgs: String) -> String {
    let fileArgs = FileArgs.fullFileName(fullFileName: fullFileName)
    return OneKeePassMobile.uploadAttachment(fileArgs, jsonArgs)
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
    return NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true).first!
  }
  
  func cacheDir() -> String {
    NSSearchPathForDirectoriesInDomains(.cachesDirectory, .userDomainMask, true).first!
  }
  
  func tempDir() -> String {
    let tempDirectoryPath = URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
    return tempDirectoryPath.absoluteString
  }

  func uriToFileName(_ fullFileNameUri: String) -> String? {
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
