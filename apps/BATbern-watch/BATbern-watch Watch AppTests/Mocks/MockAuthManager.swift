@testable import BATbern_watch_Watch_App
import Foundation
import Observation

/// Mock AuthManager for unit tests. Conforms to AuthManagerProtocol.
/// Tracks refreshJWT call count for 401-retry assertion tests.
/// @Observable enables withObservationTracking to detect currentJWT changes in WebSocketService tests.
@Observable
@MainActor
final class MockAuthManager: AuthManagerProtocol {
    var isPaired: Bool
    var organizerUsername: String?
    var organizerFirstName: String?
    var currentJWT: String?
    var pairingToken: String? = "mock-pairing-token"

    // Tracking for 401/refresh tests (EventSyncServiceTests)
    var refreshCallCount = 0
    var shouldFailRefresh = false

    init(
        isPaired: Bool = true,
        organizerUsername: String? = "marco.muster",
        organizerFirstName: String? = "Marco",
        currentJWT: String? = "mock-jwt-token"
    ) {
        self.isPaired = isPaired
        self.organizerUsername = organizerUsername
        self.organizerFirstName = organizerFirstName
        self.currentJWT = currentJWT
    }

    func pair(code: String) async throws {}

    func refreshJWT() async throws {
        refreshCallCount += 1
        if shouldFailRefresh {
            throw URLError(.userAuthenticationRequired)
        }
    }

    func unpair() { isPaired = false }
}
