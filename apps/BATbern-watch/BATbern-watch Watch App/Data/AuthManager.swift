//
//  AuthManager.swift
//  BATbern-watch Watch App
//
//  Manages organizer pairing token (Keychain) and JWT lifecycle.
//  W2.2: Pairing + JWT management.
//  Source: docs/watch-app/architecture.md#Authentication-Security
//

import Foundation
import Security

/// Protocol for AuthManager dependency injection (testability).
@MainActor
protocol AuthManagerProtocol: AnyObject {
    var isPaired: Bool { get }
    var organizerUsername: String? { get }
    var organizerFirstName: String? { get }
    var currentJWT: String? { get }
    func pair(code: String) async throws
    func refreshJWT() async throws
    func unpair()
}

/// Manages organizer authentication state: pairing token (Keychain) + JWT (in-memory).
/// NFR15: Pairing token stored in Keychain. NFR16: JWT has 1-hour lifespan, auto-refreshed.
@Observable
@MainActor
final class AuthManager: AuthManagerProtocol {

    // MARK: - Published State

    var isPaired: Bool = false
    var organizerUsername: String?
    var organizerFirstName: String?
    var currentJWT: String?

    // MARK: - Private State

    private var jwtExpiresAt: Date?
    private var refreshTimer: Timer?
    private let keychainService = "ch.batbern.watch"
    private let pairingTokenKey = "pairingToken"
    private let usernameDefaultsKey = "batbern.organizerUsername"
    private let firstNameDefaultsKey = "batbern.organizerFirstName"

    // MARK: - Dependencies

    let authService: WatchAuthServiceProtocol
    let clock: ClockProtocol

    // MARK: - Init

    init(
        authService: WatchAuthServiceProtocol = WatchAuthService(),
        clock: ClockProtocol = SystemClock()
    ) {
        self.authService = authService
        self.clock = clock

        // TESTING_MODE: override auth state from launch environment (UI tests only)
        if ProcessInfo.processInfo.environment["TESTING_MODE"] == "1" {
            if ProcessInfo.processInfo.environment["AUTH_STATE"] == "paired" {
                isPaired = true
                currentJWT = "ui-test-jwt"
            }
            return  // Skip Keychain loading in test mode
        }

        // Load pairing token from Keychain on init (AC4: persistent pairing)
        if let token = loadPairingTokenFromKeychain() {
            isPaired = true
            // Restore username + firstName from UserDefaults (set during pair())
            organizerUsername = UserDefaults.standard.string(forKey: usernameDefaultsKey)
            organizerFirstName = UserDefaults.standard.string(forKey: firstNameDefaultsKey)
            // Fetch fresh JWT using saved pairing token (non-blocking)
            Task {
                do {
                    try await self.refreshJWT()
                    print("✅ AuthManager: JWT refreshed on init")
                } catch {
                    print("⚠️ AuthManager: refreshJWT failed on init: \(error)")
                }
            }
        } else {
            print("ℹ️ AuthManager: no pairing token in Keychain")
        }
    }

    // MARK: - Public API

    /// AC2: Exchange 6-digit code for pairing token + JWT.
    func pair(code: String) async throws {
        // TESTING_MODE: simulate pair responses without hitting the network
        if ProcessInfo.processInfo.environment["TESTING_MODE"] == "1" {
            if ProcessInfo.processInfo.environment["MOCK_PAIR_RESPONSE"] == "invalid_code" {
                throw NSError(domain: "MockPairError", code: 400,
                              userInfo: [NSLocalizedDescriptionKey: "Invalid pairing code"])
            }
        }

        let result = try await authService.pair(code: code)

        // Save pairing token to Keychain (NFR15)
        savePairingTokenToKeychain(result.pairingToken)

        // Fetch JWT using the new pairing token (NFR16)
        let authResult = try await authService.authenticate(pairingToken: result.pairingToken)
        currentJWT = authResult.jwt
        jwtExpiresAt = authResult.expiresAt

        // Update pairing state and persist username/firstName for restarts
        organizerUsername = result.organizerUsername
        organizerFirstName = result.organizerFirstName
        UserDefaults.standard.set(result.organizerUsername, forKey: usernameDefaultsKey)
        UserDefaults.standard.set(result.organizerFirstName, forKey: firstNameDefaultsKey)
        isPaired = true

        // Schedule JWT auto-refresh (NFR16: refresh 10 min before expiry)
        scheduleJWTRefresh()
    }

    /// NFR16: Exchange pairing token for fresh JWT.
    func refreshJWT() async throws {
        guard let token = loadPairingTokenFromKeychain() else { return }

        let authResult = try await authService.authenticate(pairingToken: token)
        currentJWT = authResult.jwt
        jwtExpiresAt = authResult.expiresAt

        // Recover username from JWT sub claim if not yet known (e.g. after restart
        // before re-pairing with the UserDefaults persistence fix in place).
        if organizerUsername == nil, let sub = decodeJWTSubject(from: authResult.jwt) {
            organizerUsername = sub
            UserDefaults.standard.set(sub, forKey: usernameDefaultsKey)
        }

        scheduleJWTRefresh()
    }

    // MARK: - JWT Utilities

    /// Decodes the payload of a JWT (without verification) and returns the `sub` claim.
    private func decodeJWTSubject(from jwt: String) -> String? {
        let parts = jwt.split(separator: ".")
        guard parts.count == 3 else { return nil }
        var base64 = String(parts[1])
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let remainder = base64.count % 4
        if remainder > 0 { base64 += String(repeating: "=", count: 4 - remainder) }
        guard let data = Data(base64Encoded: base64),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let sub = json["sub"] as? String else { return nil }
        return sub
    }

    /// Remove pairing token from Keychain and clear all auth state.
    func unpair() {
        KeychainHelper.shared.delete(key: pairingTokenKey)
        refreshTimer?.invalidate()
        refreshTimer = nil
        isPaired = false
        organizerUsername = nil
        organizerFirstName = nil
        currentJWT = nil
        UserDefaults.standard.removeObject(forKey: usernameDefaultsKey)
        UserDefaults.standard.removeObject(forKey: firstNameDefaultsKey)
        jwtExpiresAt = nil
    }

    // MARK: - JWT Auto-Refresh (NFR16)

    private func scheduleJWTRefresh() {
        refreshTimer?.invalidate()

        guard let expiresAt = jwtExpiresAt else { return }

        // Refresh 10 minutes before expiry
        let refreshAt = expiresAt.addingTimeInterval(-600)
        let timeUntilRefresh = refreshAt.timeIntervalSince(clock.now)

        if timeUntilRefresh > 0 {
            refreshTimer = Timer.scheduledTimer(withTimeInterval: timeUntilRefresh, repeats: false) { [weak self] _ in
                Task { @MainActor in
                    try? await self?.refreshJWT()
                }
            }
        } else {
            // Already past refresh time — refresh immediately
            Task {
                try? await refreshJWT()
            }
        }
    }

    // MARK: - Keychain Helpers

    private func savePairingTokenToKeychain(_ token: String) {
        _ = KeychainHelper.shared.save(key: pairingTokenKey, value: token)
    }

    private func loadPairingTokenFromKeychain() -> String? {
        return KeychainHelper.shared.load(key: pairingTokenKey)
    }
}
