import Foundation

/// Contract for REST API communication with BATbern backend.
protocol APIClientProtocol: Sendable {
    /// Fetch the current public event with sessions and speakers.
    func fetchCurrentEvent() async throws -> WatchEvent

    /// Fetch a specific event by code.
    func fetchEvent(code: String) async throws -> WatchEvent

    /// Exchange pairing code for pairing token.
    func pair(code: String) async throws -> PairingResult

    /// Exchange pairing token for access JWT.
    func authenticate(pairingToken: String) async throws -> AuthResult
}

struct PairingResult: Sendable {
    let pairingToken: String
    let organizerUsername: String
    let organizerFirstName: String
}

struct AuthResult: Sendable {
    let accessToken: String
    let expiresIn: TimeInterval
}
