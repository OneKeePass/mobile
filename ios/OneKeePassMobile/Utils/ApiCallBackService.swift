//
//  ApiCallBackService.swift
//  OneKeePassMobile
//
//  Created by on 6/27/24.
//

import Foundation
import AuthenticationServices

// This is intialized and set in a global singleton holder in rust


// To fix the warning:
// Non-final class 'ApiCallBackService' cannot conform to 'Sendable'; use '@unchecked Sendable'; this is an error in the Swift 6 language mode
// We may need to use the solution as here. We need to do more testing with this
// class ApiCallBackService:@unchecked Sendable, IosApiService,CommonDeviceServiceEx

// See https://www.avanderlee.com/swift/sendable-protocol-closures/ for some explanation


class ApiCallBackService:@unchecked Sendable, IosApiService,CommonDeviceServiceEx {
  
  func ascCredentialServiceIdentifiers() throws -> [String: String] {
    #if OKP_APP_EXTENSION
      return CredentialProviderViewController.serviceIdentifiersReceived()
    #else
      return [:]
    #endif
  }
  
  func clipboardCopyString(_ text: String, _ timeout: UInt32) throws {
    #if OKP_APP_EXTENSION
      cmnLogger.debug("In OKP_APP_EXTENSION Called clipboardCopyString text \(text) and timeout \(timeout)")
    #else
      cmnLogger.debug("In APP  Called clipboardCopyString text \(text) and timeout \(timeout)")
    #endif
    
    // cmnLogger.debug("Called clipboardCopyString text \(text) and timeout \(timeout)")
    ClipboardHelper.impl.copyString(text: text, timeout: Double(timeout))
  }
  
  // An implementation of a fn in CommonDeviceServiceEx
  func clipboardCopyString(_ clipData: AppClipboardCopyData) throws {
    cmnLogger.debug("Common service clipboardCopyString is called with clipData as \(clipData)")
    try clipboardCopyString(clipData.fieldValue, clipData.cleanupAfter )
  }

  // Static cache: dbKey → registered identities (so we can remove old ones before adding new)
  @available(iOS 17.0, *)
  private static var cachedPasskeyIdentities: [String: [ASPasskeyCredentialIdentity]] = [:]

  func registerPasskeyIdentities(_ dbKey: String, _ passkeys: [PasskeySummaryData]) throws {
    guard #available(iOS 17.0, *) else { return }

    let newIdentities: [ASPasskeyCredentialIdentity] = passkeys.compactMap { item in
      guard let credData = decodeBase64URL(item.credentialIdB64url),
            let uhData   = decodeBase64URL(item.userHandleB64url)
      else { return nil }
      return ASPasskeyCredentialIdentity(
        relyingPartyIdentifier: item.rpId,
        userName:               item.username,
        credentialID:           credData,
        userHandle:             uhData,
        recordIdentifier:       item.entryUuid)
    }

    let store = ASCredentialIdentityStore.shared
    let oldIdentities = ApiCallBackService.cachedPasskeyIdentities[dbKey] ?? []

    if oldIdentities.isEmpty {
      guard !newIdentities.isEmpty else { return }
      store.saveCredentialIdentities(newIdentities) { _, error in
        if let error { cmnLogger.error("registerPasskeyIdentities save error: \(error)") }
      }
    } else {
      store.removeCredentialIdentities(oldIdentities) { _, _ in
        guard !newIdentities.isEmpty else { return }
        store.saveCredentialIdentities(newIdentities) { _, error in
          if let error { cmnLogger.error("registerPasskeyIdentities save error: \(error)") }
        }
      }
    }

    ApiCallBackService.cachedPasskeyIdentities[dbKey] = newIdentities
  }

  private func decodeBase64URL(_ s: String) -> Data? {
    var b64 = s.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
    b64 += String(repeating: "=", count: (4 - b64.count % 4) % 4)
    return Data(base64Encoded: b64)
  }

}

