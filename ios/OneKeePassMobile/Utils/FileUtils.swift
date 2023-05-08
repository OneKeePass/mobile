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

      logger.debug("Calling rust api with data size \(byteArray.count)")
      let b = DbServiceAPI.iosSupportService().saveBookMarkData(url.absoluteString, byteArray)
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
      guard url.startAccessingSecurityScopedResource() else {
        logger.error("startAccessingSecurityScopedResource call failed for \(url)")
        return
      }
      
      defer { url.stopAccessingSecurityScopedResource() }
      do {
        // Secured access to url should be available before bookmarking
        logger.debug("Creating bookmark for the saved_file_url \(url)")
        let bookmarkData = try url.bookmarkData(options: .minimalBookmark, includingResourceValuesForKeys: nil, relativeTo: nil)
        let byteArray: [UInt8] = .init(bookmarkData)

        logger.debug("Calling rust api to save bookmark data with size \(byteArray.count)")
        let b = DbServiceAPI.iosSupportService().saveBookMarkData(url.absoluteString, byteArray)
        logger.debug("Bookmark save rust api call result is \(b)")
        
        success = true
        reader(url, nil)
      
      } catch {
        // Handle the error here.
        logger.error("E_READ_FILE_PICK_DELEGATE_FAILED: bookmarkData Error \(error)")
      }
    }
    
    if error != nil {
      logger.debug("In readKdbx NSFileCoordinator().coordinate call error \(error?.localizedDescription)")
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
        let byteArray: [UInt8] = .init(bookmarkData)

        logger.debug("Calling rust api to save bookmark data with size \(byteArray.count)")
        let b = DbServiceAPI.iosSupportService().saveBookMarkData(url.absoluteString, byteArray)
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
}
