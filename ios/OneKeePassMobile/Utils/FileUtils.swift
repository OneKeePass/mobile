import Foundation

@objc(FileUtils)
class FileUtils: NSObject {
  private static let logger = OkpLogger(tag: "FileUtils")

  static func bookMark(url: URL) -> Bool {
    do {
      // Start accessing a security-scoped resource.
      guard url.startAccessingSecurityScopedResource() else {
        logger.error("startAccessingSecurityScopedResource call failed")
        return false
      }

      // Make sure you release the security-scoped resource when you finish.
      defer { url.stopAccessingSecurityScopedResource() }

      logger.debug("Creating bookmark for the url \(url)")
      let bookmarkData = try url.bookmarkData(options: .minimalBookmark, includingResourceValuesForKeys: nil, relativeTo: nil)
      let byteArray: [UInt8] = .init(bookmarkData)
      
      let byteData = Data(byteArray)

      logger.debug("Calling rust api with data size \(byteArray.count)")
      // let b = DbServiceAPI.iosSupportService().saveBookMarkData(url.absoluteString, byteArray)
      let b = DbServiceAPI.iosSupportService().saveBookMarkData(url.absoluteString, byteData)
      logger.debug("Bookmark save call result is \(b)")

      return b
    } catch {
      logger.error("\(error)")
      return false
    }
  }
  
  static func coordinatedSyncBookMarking(url: URL, reader: (URL, Error?) -> Void) -> Bool {
    var error: NSError?
    var success = false

    NSFileCoordinator().coordinate(readingItemAt: url, options: [.withoutChanges, .resolvesSymbolicLink], error: &error) { url in
      // When another app (OneDrive, GDrive etc) hands over the file as a copy instead of
      // opening it in place, the url points to a file inside our own sandbox (Documents/Inbox).
      // Such a url is not a security scoped one and this call returns false for it.
      // No scoped access is needed to read it and we should continue instead of failing
      let isAccessed = url.startAccessingSecurityScopedResource()
      if !isAccessed {
        logger.debug("startAccessingSecurityScopedResource returned false for \(url). Continuing as this url may not be a security scoped one")
      }

      defer { if isAccessed { url.stopAccessingSecurityScopedResource() } }
      do {
        // Secured access to url should be available before bookmarking
        logger.debug("Creating bookmark for the saved_file_url \(url)")
        let bookmarkData = try url.bookmarkData(options: .minimalBookmark, includingResourceValuesForKeys: nil, relativeTo: nil)
        // let byteArray: [UInt8] = .init(bookmarkData)
        // logger.debug("Calling rust api to save bookmark data with size \(byteArray.count)")
        let b = DbServiceAPI.iosSupportService().saveBookMarkData(url.absoluteString, bookmarkData)
        logger.debug("Bookmark save rust api call result is \(b)")
        
        success = true
        reader(url, nil)
      
      } catch {
        // Handle the error here.
        logger.error("E_READ_FILE_PICK_DELEGATE_FAILED: bookmarkData Error \(error)")
        // The caller is told about the failure. Without this the reader is never called
        // for a bookmarking failure and the UI is left waiting for an event that
        // never arrives
        reader(url, error)
      }
    }

    if error != nil {
      logger.debug("In readKdbx NSFileCoordinator().coordinate call error \(String(describing: error?.localizedDescription))")
      reader(url, error)
    }
    
    return success
  }
  
  // Needs more work to return proper return object and/or including throws
  static func coordinatedBookMarking(url: URL) -> Bool {
    let fc = NSFileCoordinator()
    
    let intent = NSFileAccessIntent.readingIntent(with: url, options: [.withoutChanges, .resolvesSymbolicLink])
    var success = false
    fc.coordinate(with: [intent], queue: .main) { err in
      guard err == nil else {
        logger.error("Coordinate error  is \(err)")
        return
      }
      
      guard url.startAccessingSecurityScopedResource() else {
        logger.error("startAccessingSecurityScopedResource call failed")
        return
      }
      
      // Make sure we release the security-scoped resource when finished.
      defer { url.stopAccessingSecurityScopedResource() }
      do {
        // Secured access to url should be available before bookmarking
        logger.debug("Creating bookmark for the saved_file_url \(url)")
        let bookmarkData = try url.bookmarkData(options: .minimalBookmark, includingResourceValuesForKeys: nil, relativeTo: nil)
        
        // let byteArray: [UInt8] = .init(bookmarkData)
        // logger.debug("Calling rust api to save bookmark data with size \(byteArray.count)")
        
        let b = DbServiceAPI.iosSupportService().saveBookMarkData(url.absoluteString, bookmarkData)
        logger.debug("Bookmark save rust api call result is \(b)")
      
        // logger.debug("Json with file name \(DbServiceAPI.formJsonWithFileName(saved_file_url.absoluteString))")
        // resolve (DbServiceAPI.formJsonWithFileName(url.absoluteString))
        success = true
      } catch {
        // Handle the error here.
        logger.error("E_READ_FILE_PICK_DELEGATE_FAILED: bookmarkData Error \(error)")
      }
    }
    // This is not correct as this func may return coordinate queue completes
    return success
  }
  
  @objc
  static func testLog() {
    logger.debug("Called from obj-c")
  }

  /// Documents/Inbox handling
  ///
  /// When another app hands over a file as a copy (openInPlace false), iOS puts that
  /// copy in our own sandbox under Documents/Inbox. The dir is read only for us (we may
  /// read and delete but not write), it is not visible to the user in the Files app and
  /// nothing in iOS ever cleans it up.
  ///
  /// We never open a db from such a copy. Editing it would write to a file the user
  /// cannot find and the original in the sending app would never see those changes.
  /// Instead the user is asked to use our own 'Open Database' action to pick the file
  /// from its own location, which opens it in place and writable.
  /// So every file that lands in this dir is removed as soon as we notice it

  private static var inboxDirUrl: URL? {
    guard let documentsDir = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true).first else {
      return nil
    }
    return URL(fileURLWithPath: documentsDir, isDirectory: true).appendingPathComponent("Inbox", isDirectory: true)
  }

  // Deletes everything in Documents/Inbox
  //
  // Called with the file another app has just handed over and also at every app start so
  // that anything left behind by a version of the app that did not delete it, or by a
  // force quit before the delete, does not stay around
  static func sweepInboxFiles() {
    guard let inboxDir = inboxDirUrl,
          FileManager.default.fileExists(atPath: inboxDir.path) else {
      return
    }

    do {
      let urls = try FileManager.default.contentsOfDirectory(at: inboxDir, includingPropertiesForKeys: nil)
      for url in urls {
        do {
          try FileManager.default.removeItem(at: url)
          logger.debug("Inbox sweep removed the file \(url.lastPathComponent)")
        } catch {
          logger.error("Inbox sweep could not remove \(url.lastPathComponent) \(error)")
        }
      }
    } catch {
      logger.error("Reading the inbox dir contents failed \(error)")
    }
  }
}
