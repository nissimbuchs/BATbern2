import Foundation
@testable import BATbern_watch_Watch_App

/// Mock WebSocket client that records actions and emits configurable state updates.
final class MockWebSocketClient: WebSocketClientProtocol, @unchecked Sendable {
    private(set) var _isConnected = false
    private(set) var connectCalls: [(eventCode: String, accessToken: String)] = []
    private(set) var disconnectCallCount = 0
    private(set) var sentActions: [WatchAction] = []

    private var stateUpdatesContinuation: AsyncStream<EventStateMessage>.Continuation?

    var isConnected: Bool { _isConnected }

    var connectShouldFail = false
    var sendActionShouldFail = false

    func connect(eventCode: String, accessToken: String) async throws {
        if connectShouldFail { throw MockError.simulatedFailure }
        connectCalls.append((eventCode, accessToken))
        _isConnected = true
    }

    func disconnect() {
        disconnectCallCount += 1
        _isConnected = false
        stateUpdatesContinuation?.finish()
    }

    func sendAction(_ action: WatchAction) async throws {
        if sendActionShouldFail { throw MockError.simulatedFailure }
        sentActions.append(action)
    }

    func stateUpdates() -> AsyncStream<EventStateMessage> {
        AsyncStream { continuation in
            self.stateUpdatesContinuation = continuation
        }
    }

    // MARK: - Test Helpers

    /// Emit a state update message to all subscribers.
    func emit(_ message: EventStateMessage) {
        stateUpdatesContinuation?.yield(message)
    }

    /// Simulate unexpected disconnection.
    func simulateDisconnect() {
        _isConnected = false
        stateUpdatesContinuation?.finish()
    }
}
