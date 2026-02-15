import Foundation
@testable import BATbern_watch_Watch_App

/// Records haptic alert invocations for test assertions.
final class MockHapticService: HapticServiceProtocol, @unchecked Sendable {
    private(set) var playedAlerts: [HapticAlert] = []
    private(set) var scheduledAlerts: [(alert: HapticAlert, date: Date)] = []
    private(set) var cancelledAlerts: [HapticAlert] = []
    private(set) var cancelAllCalled = false

    func play(_ alert: HapticAlert) {
        playedAlerts.append(alert)
    }

    func schedule(_ alert: HapticAlert, at date: Date) {
        scheduledAlerts.append((alert, date))
    }

    func cancelAll() {
        cancelAllCalled = true
        scheduledAlerts.removeAll()
    }

    func cancel(_ alert: HapticAlert) {
        cancelledAlerts.append(alert)
        scheduledAlerts.removeAll { $0.alert == alert }
    }

    /// Reset all recorded state between tests.
    func reset() {
        playedAlerts.removeAll()
        scheduledAlerts.removeAll()
        cancelledAlerts.removeAll()
        cancelAllCalled = false
    }
}
