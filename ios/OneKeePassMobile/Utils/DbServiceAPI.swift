import Foundation

class DbServiceAPI {
  static let logger = OkpLogger(tag: "DbServiceAPI")
  
  static var _iosSupportService = IosSupportService()
  static var _jsonService = JsonService()

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
      dbServiceInitialize(cmnService)
      initialized = true
      Swift.debugPrint("API initialize is done")
    } else {
      Swift.debugPrint("API initialize is alredy done")
    }
  }

  static func iosSupportService() -> IosSupportService {
    // IosSupportService()
    _iosSupportService
  }

  static func formJsonWithFileName(_ fullFileName: String) -> String {
    _jsonService.formWithFileName(fullFileName)
  }

  static func createKdbx(_ fullFileName: String, _ args: String) -> ApiResponse {
    let fileArgs = FileArgs.fullFileName(fullFileName: fullFileName)
    return OneKeePassMobile.createKdbx(fileArgs, args)
  }

  static func createTempKdbx(_ tempFileUri: String, _ args: String) -> ApiResponse {
    let fileArgs = FileArgs.fullFileName(fullFileName: tempFileUri)
    return OneKeePassMobile.createTempKdbx(fileArgs, args)
  }

  static func readKdbx(_ dbFileName: String, _ jsonArgs: String) -> ApiResponse {
    let fileArgs = FileArgs.fullFileName(fullFileName: dbFileName)
    return OneKeePassMobile.readKdbx(fileArgs, jsonArgs)
  }

  static func saveKdbx(_ fullFileName: String) -> ApiResponse {
    let fileArgs = FileArgs.fullFileName(fullFileName: fullFileName)
    return OneKeePassMobile.saveKdbx(fileArgs)
  }
  
  static func writeToBackup(_ fullFileName: String) -> ApiResponse {
    return OneKeePassMobile.writeToBackup(fullFileName)
  }
  
}

class CommonDeviceServiceImpl: CommonDeviceService {
  func appHomeDir() -> String {
    return NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true).first!
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
