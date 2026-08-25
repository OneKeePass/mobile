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

  // Registers the entries that can produce a TOTP as one time code identities, so iOS offers
  // OneKeePass on a verification code field. Without these the OS never calls
  // prepareOneTimeCodeCredentialList. One identity per service url of an entry.
  func registerOneTimeCodeIdentities(
    _ dbKey: String,
    _ oldIdentities: [OtpIdentityData],
    _ newIdentities: [OtpIdentityData]
  ) throws {
    guard #available(iOS 18.0, *) else { return }

    func toCredentialIdentities(_ items: [OtpIdentityData]) -> [ASOneTimeCodeCredentialIdentity] {
      return items.flatMap { item in
        item.serviceUrls.map { url in
          ASOneTimeCodeCredentialIdentity(
            serviceIdentifier: ASCredentialServiceIdentifier(identifier: url, type: .URL),
            label: item.label,
            recordIdentifier: item.entryUuid)
        }
      }
    }

    let oldOnes = toCredentialIdentities(oldIdentities)
    let newOnes = toCredentialIdentities(newIdentities)

    cmnLogger.debug("registerOneTimeCodeIdentities: removing \(oldOnes.count), saving \(newOnes.count)")
    for i in newOnes {
      cmnLogger.debug(
        "registerOneTimeCodeIdentities: identity service '\(i.serviceIdentifier.identifier)' label '\(i.label)'")
    }

    let store = ASCredentialIdentityStore.shared

    // The store silently drops everything when autofill is off for the app, and it ignores the
    // incremental save/remove calls when it does not support incremental updates. Both look
    // exactly like a successful registration from here, so the state is checked first
    store.getState { state in
      cmnLogger.debug(
        "registerOneTimeCodeIdentities: store enabled \(state.isEnabled), supportsIncrementalUpdates \(state.supportsIncrementalUpdates)")

      guard state.isEnabled else {
        cmnLogger.error("registerOneTimeCodeIdentities: identity store is not enabled; nothing registered")
        return
      }

      let onSaved: (Bool, Error?) -> Void = { success, error in
        if let error {
          cmnLogger.error("registerOneTimeCodeIdentities save error: \(error)")
        } else {
          cmnLogger.debug("registerOneTimeCodeIdentities: saved \(newOnes.count) identities, success \(success)")
        }
      }

      guard state.supportsIncrementalUpdates else {
        // No incremental updates - the only thing that takes effect is replacing the whole set.
        // Passkey identities live in the same store, so replacing here would drop them; that is
        // why this path only logs rather than calling replaceCredentialIdentities
        cmnLogger.error(
          "registerOneTimeCodeIdentities: store does not support incremental updates; identities not registered")
        return
      }

      if oldOnes.isEmpty {
        guard !newOnes.isEmpty else { return }
        store.saveCredentialIdentities(newOnes, completion: onSaved)
      } else {
        // Remove the previously registered identities for this db before saving the current set
        store.removeCredentialIdentities(oldOnes) { removed, error in
          if let error {
            cmnLogger.error("registerOneTimeCodeIdentities remove error: \(error)")
          } else {
            cmnLogger.debug("registerOneTimeCodeIdentities: removed \(oldOnes.count) identities, success \(removed)")
          }
          guard !newOnes.isEmpty else { return }
          store.saveCredentialIdentities(newOnes, completion: onSaved)
        }
      }
    }
  }

  func registerPasskeyIdentities(_ dbKey: String, _ oldPasskeys: [PasskeySummaryData], _ newPasskeys: [PasskeySummaryData]) throws {
    guard #available(iOS 17.0, *) else { return }

    let oldIdentities: [ASPasskeyCredentialIdentity] = oldPasskeys.compactMap { item in
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
    let newIdentities: [ASPasskeyCredentialIdentity] = newPasskeys.compactMap { item in
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
    if oldIdentities.isEmpty {
      // Just the new identities for a db and save that
      guard !newIdentities.isEmpty else { return }
      store.saveCredentialIdentities(newIdentities) { _, error in
        if let error { cmnLogger.error("registerPasskeyIdentities save error: \(error)") }
      }
    } else {
      // We remove old identities for a db before saving new one
      store.removeCredentialIdentities(oldIdentities) { _, _ in
        guard !newIdentities.isEmpty else { return }
        store.saveCredentialIdentities(newIdentities) { _, error in
          if let error { cmnLogger.error("registerPasskeyIdentities save error: \(error)") }
        }
      }
    }
  }

  // Called by Rust after signing a passkey assertion; completes the iOS credential provider request.
  // No-op in the main app target — only the autofill extension has CredentialProviderViewController.
  func completePasskeyAssertion(_ data: PasskeyAssertionCallbackData) throws {
      
    #if OKP_APP_EXTENSION
    guard #available(iOS 17.0, *) else { return }
    CredentialProviderViewController.completePasskeyAssertion(
      credentialIdB64url:      data.credentialIdB64url,
      userHandleB64url:        data.userHandleB64url,
      signatureB64url:         data.signatureB64url,
      authenticatorDataB64url: data.authenticatorDataB64url,
      rpId:                    data.rpId)
    #endif
  }

  // Called by Rust after creating a passkey registration; completes the iOS credential provider request.
  // No-op in the main app target — only the autofill extension has CredentialProviderViewController.
  func completePasskeyRegistration(_ data: PasskeyRegistrationCallbackData) throws {
    #if OKP_APP_EXTENSION
    guard #available(iOS 17.0, *),
          let clientDataHash = Data(base64URLEncoded: data.clientDataHashB64url)
    else { return }
    CredentialProviderViewController.completePasskeyRegistration(
      credentialIdB64url:      data.credentialIdB64url,
      attestationObjectB64url: data.attestationObjectB64url,
      clientDataHash:          clientDataHash)
    #endif
  }

  private func decodeBase64URL(_ s: String) -> Data? {
    var b64 = s.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
    b64 += String(repeating: "=", count: (4 - b64.count % 4) % 4)
    return Data(base64Encoded: b64)
  }

}

