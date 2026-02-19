//
//  KeychainHelper.swift
//  BATbern-watch Watch App
//
//  Lightweight Keychain CRUD wrapper for secure token storage.
//  W2.2: Used by AuthManager to persist pairing token (NFR15).
//  Source: docs/watch-app/architecture.md#Authentication-Security
//

import Foundation
import Security

/// Wrapper for Keychain CRUD operations.
/// NFR15: Pairing tokens must be stored in Keychain, never UserDefaults.
final class KeychainHelper {
    static let shared = KeychainHelper()
    private let service = "ch.batbern.watch"

    private init() {}

    /// Save (or overwrite) a string value for the given key.
    /// Returns true on success.
    @discardableResult
    func save(key: String, value: String) -> Bool {
        guard let data = value.data(using: .utf8) else { return false }

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecValueData as String: data
        ]

        // Delete existing item before adding (avoids errSecDuplicateItem)
        SecItemDelete(query as CFDictionary)

        let status = SecItemAdd(query as CFDictionary, nil)
        return status == errSecSuccess
    }

    /// Load a string value for the given key, or nil if not found.
    func load(key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess,
              let data = result as? Data,
              let value = String(data: data, encoding: .utf8) else {
            return nil
        }

        return value
    }

    /// Delete the item for the given key.
    func delete(key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key
        ]

        SecItemDelete(query as CFDictionary)
    }
}
