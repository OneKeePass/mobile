//
//  OkpCrashLog.swift
//  OneKeePassMobile
//

import Foundation
import UIKit

// A javascript error that nothing caught reaches RCTFatal and the process is ended. The
// message and the javascript stack are passed to RCTFatal, but neither survives into the
// crash reports that App Store Connect shows - Apple drops the free text section that
// would carry them. Without this, a crash of that kind tells us only that it happened.
//
// The record is written before the process ends, so every write here is synchronous. A
// dispatch to another queue would not finish in time.
//
// Only diagnostic text is written. Nothing from the open database, and no value the user
// typed, is recorded here.
class OkpCrashLog {
  private static let logger = OkpLogger(tag: "OkpCrashLog")

  private static let fileName = "okp-js-crash-log.json"

  // A javascript stack from a release bundle is long. These caps keep the file small
  // enough that it never needs rotating
  private static let maxMessageLength = 2000
  private static let maxStackLength = 8000
  private static let maxRecordsKept = 5

  private static func logFileUrl() -> URL? {
    guard let libraryDir = FileManager.default.urls(for: .libraryDirectory, in: .userDomainMask).first else {
      return nil
    }
    return libraryDir.appendingPathComponent(fileName)
  }

  private static func truncated(_ value: String, _ limit: Int) -> String {
    if value.count <= limit {
      return value
    }
    return String(value.prefix(limit)) + "..."
  }

  private static func existingRecords(_ url: URL) -> [[String: String]] {
    guard let data = try? Data(contentsOf: url),
          let parsed = try? JSONSerialization.jsonObject(with: data),
          let records = parsed as? [[String: String]]
    else {
      return []
    }
    return records
  }

  // Called from the fatal handler with what react native passed to it
  static func record(message: String, jsStack: String) {
    guard let url = logFileUrl() else {
      logger.error("No library dir found and the js crash record is not written")
      return
    }

    let record: [String: String] = [
      "at": ISO8601DateFormatter().string(from: Date()),
      "appVersion": (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "")
        + " (" + (Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "") + ")",
      "osVersion": UIDevice.current.systemVersion,
      "message": truncated(message, maxMessageLength),
      "jsStack": truncated(jsStack, maxStackLength),
    ]

    // The whole file is rewritten with the most recent records. Appending would let it
    // grow without a bound
    let records = (existingRecords(url) + [record]).suffix(maxRecordsKept)

    guard let data = try? JSONSerialization.data(withJSONObject: Array(records), options: [.prettyPrinted]) else {
      logger.error("The js crash record could not be serialized")
      return
    }

    do {
      try data.write(to: url, options: [.atomic])
      var excluded = URLResourceValues()
      excluded.isExcludedFromBackup = true
      var mutableUrl = url
      try? mutableUrl.setResourceValues(excluded)
      logger.error("A js crash record was written for: \(record["message"] ?? "")")
    } catch {
      logger.error("Writing the js crash record failed \(error)")
    }
  }

  // Reads back what was recorded
  static func recordedCrashes() -> [[String: String]] {
    guard let url = logFileUrl() else {
      return []
    }
    return existingRecords(url)
  }

  // The records as the text shown to the user on the next launch after a crash. Empty
  // when nothing was recorded, which is the usual case
  static func recordedCrashesText() -> String {
    let records = recordedCrashes()
    if records.isEmpty {
      return ""
    }
    guard let data = try? JSONSerialization.data(withJSONObject: records, options: [.prettyPrinted, .sortedKeys]),
          let text = String(data: data, encoding: .utf8)
    else {
      return ""
    }
    return text
  }

  static func clearRecordedCrashes() throws {
    guard let url = logFileUrl() else {
      throw NSError(domain: "OkpCrashLog", code: 1,
                    userInfo: [NSLocalizedDescriptionKey: "Could not locate the crash log directory"])
    }
    do {
      try FileManager.default.removeItem(at: url)
    } catch let error as NSError {
      // Clearing an absent log is successful; other failures must reach the caller.
      if error.domain != NSCocoaErrorDomain || error.code != NSFileNoSuchFileError {
        throw error
      }
    }
  }
}
