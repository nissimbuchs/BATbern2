import Foundation
@testable import BATbern_watch_Watch_App

/// Configurable mock for API client tests.
/// Set result properties before test execution to control return values.
final class MockAPIClient: APIClientProtocol, @unchecked Sendable {
    var fetchCurrentEventResult: Result<WatchEvent, Error> = .failure(MockError.notConfigured)
    var fetchEventResult: Result<WatchEvent, Error> = .failure(MockError.notConfigured)
    var pairResult: Result<PairingResult, Error> = .failure(MockError.notConfigured)
    var authenticateResult: Result<AuthResult, Error> = .failure(MockError.notConfigured)

    private(set) var fetchCurrentEventCallCount = 0
    private(set) var fetchEventCodes: [String] = []
    private(set) var pairCodes: [String] = []
    private(set) var authenticateTokens: [String] = []

    func fetchCurrentEvent() async throws -> WatchEvent {
        fetchCurrentEventCallCount += 1
        return try fetchCurrentEventResult.get()
    }

    func fetchEvent(code: String) async throws -> WatchEvent {
        fetchEventCodes.append(code)
        return try fetchEventResult.get()
    }

    func pair(code: String) async throws -> PairingResult {
        pairCodes.append(code)
        return try pairResult.get()
    }

    func authenticate(pairingToken: String) async throws -> AuthResult {
        authenticateTokens.append(pairingToken)
        return try authenticateResult.get()
    }
}

/// Common mock errors for test scenarios.
enum MockError: Error, Equatable {
    case notConfigured
    case simulatedFailure
    case timeout
    case unauthorized
}
