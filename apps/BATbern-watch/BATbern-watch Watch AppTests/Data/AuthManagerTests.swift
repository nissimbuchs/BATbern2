//
//  AuthManagerTests.swift
//  BATbern-watch Watch AppTests
//
//  Unit tests for AuthManager pairing token + JWT lifecycle.
//  W2.2: AC2 (pair success), AC3 (invalid code), AC4 (persistent pairing), unpair.
//

import Testing
import Foundation
@testable import BATbern_watch_Watch_App

// MARK: - Mock WatchAuthService

@MainActor
final class MockWatchAuthService: WatchAuthServiceProtocol, @unchecked Sendable {
    // Stubbed results
    var pairResult: Result<WatchPairingResult, Error> = .success(
        WatchPairingResult(
            pairingToken: "mock-token-abc123",
            organizerUsername: "marco.organizer",
            organizerFirstName: "Marco"
        )
    )
    var authenticateResult: Result<WatchAuthTokenResult, Error> = .success(
        WatchAuthTokenResult(jwt: "mock.jwt.token", expiresAt: Date().addingTimeInterval(3600))
    )

    var pairCallCount = 0
    var authenticateCallCount = 0
    var lastPairCode: String?
    var lastAuthToken: String?

    nonisolated func pair(code: String) async throws -> WatchPairingResult {
        let result: Result<WatchPairingResult, Error> = await MainActor.run {
            pairCallCount += 1
            lastPairCode = code
            return pairResult
        }
        return try result.get()
    }

    nonisolated func authenticate(pairingToken: String) async throws -> WatchAuthTokenResult {
        let result: Result<WatchAuthTokenResult, Error> = await MainActor.run {
            authenticateCallCount += 1
            lastAuthToken = pairingToken
            return authenticateResult
        }
        return try result.get()
    }
}

// MARK: - AuthManager Tests

@Suite("AuthManagerTests", .serialized)
@MainActor
struct AuthManagerTests {

    // MARK: - Init / Keychain Load

    @Test("shouldStartUnpaired_whenNoKeychainToken")
    func shouldStartUnpaired_whenNoKeychainToken() async throws {
        // Ensure Keychain is clean
        KeychainHelper.shared.delete(key: "pairingToken")

        let authManager = AuthManager(authService: MockWatchAuthService(), clock: MockClock())
        #expect(authManager.isPaired == false)
        #expect(authManager.currentJWT == nil)
    }

    // MARK: - Pair Success (AC2)

    @Test("shouldPairSuccessfully_whenValidCode")
    func shouldPairSuccessfully_whenValidCode() async throws {
        KeychainHelper.shared.delete(key: "pairingToken")

        let mock = MockWatchAuthService()
        let authManager = AuthManager(authService: mock, clock: MockClock())

        try await authManager.pair(code: "482715")

        #expect(authManager.isPaired == true)
        #expect(authManager.organizerUsername == "marco.organizer")
        #expect(authManager.organizerFirstName == "Marco")
        #expect(authManager.currentJWT == "mock.jwt.token")
        #expect(mock.pairCallCount == 1)
        #expect(mock.lastPairCode == "482715")

        // Cleanup
        authManager.unpair()
    }

    @Test("shouldSavePairingTokenToKeychain_afterSuccessfulPair")
    func shouldSavePairingTokenToKeychain_afterSuccessfulPair() async throws {
        KeychainHelper.shared.delete(key: "pairingToken")

        let mock = MockWatchAuthService()
        let authManager = AuthManager(authService: mock, clock: MockClock())

        try await authManager.pair(code: "123456")

        let saved = KeychainHelper.shared.load(key: "pairingToken")
        #expect(saved == "mock-token-abc123")

        // Cleanup
        authManager.unpair()
    }

    // MARK: - Pair Error (AC3)

    @Test("shouldHandlePairingError_whenInvalidCode")
    func shouldHandlePairingError_whenInvalidCode() async throws {
        KeychainHelper.shared.delete(key: "pairingToken")

        let mock = MockWatchAuthService()
        mock.pairResult = .failure(WatchAuthError.invalidCode)
        let authManager = AuthManager(authService: mock, clock: MockClock())

        await #expect(throws: (any Error).self) {
            try await authManager.pair(code: "999999")
        }

        #expect(authManager.isPaired == false)
        #expect(authManager.currentJWT == nil)
        #expect(KeychainHelper.shared.load(key: "pairingToken") == nil)
    }

    // MARK: - JWT Refresh (NFR16)

    @Test("shouldRefreshJWT_whenCalled")
    func shouldRefreshJWT_whenCalled() async throws {
        // Pre-condition: set up a saved pairing token
        _ = KeychainHelper.shared.save(key: "pairingToken", value: "test-pairing-token")

        let mock = MockWatchAuthService()
        let authManager = AuthManager(authService: mock, clock: MockClock())
        authManager.isPaired = true

        try await authManager.refreshJWT()

        #expect(authManager.currentJWT == "mock.jwt.token")
        #expect(mock.authenticateCallCount >= 1)

        // Cleanup
        authManager.unpair()
    }

    // MARK: - Unpair (AC4 reverse)

    @Test("shouldClearAllState_whenUnpaired")
    func shouldClearAllState_whenUnpaired() async throws {
        KeychainHelper.shared.delete(key: "pairingToken")

        let mock = MockWatchAuthService()
        let authManager = AuthManager(authService: mock, clock: MockClock())

        // Pair first
        try await authManager.pair(code: "482715")
        #expect(authManager.isPaired == true)

        // Unpair
        authManager.unpair()

        #expect(authManager.isPaired == false)
        #expect(authManager.organizerUsername == nil)
        #expect(authManager.organizerFirstName == nil)
        #expect(authManager.currentJWT == nil)
        #expect(KeychainHelper.shared.load(key: "pairingToken") == nil)
    }
}
