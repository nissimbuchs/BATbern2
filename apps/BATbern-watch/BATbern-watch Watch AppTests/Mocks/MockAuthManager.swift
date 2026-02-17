@testable import BATbern_watch_Watch_App
import Foundation

/// Mock AuthManager for unit tests. Conforms to AuthManagerProtocol.
@MainActor
final class MockAuthManager: AuthManagerProtocol {
    var isPaired: Bool
    var organizerUsername: String?
    var organizerFirstName: String?
    var currentJWT: String?

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
    func refreshJWT() async throws {}
    func unpair() { isPaired = false }
}
