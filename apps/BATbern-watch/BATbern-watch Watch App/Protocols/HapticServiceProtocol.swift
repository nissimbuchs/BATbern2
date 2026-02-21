import Foundation

/// Contract for haptic feedback delivery on Apple Watch.
protocol HapticServiceProtocol: Sendable {
    /// Play a specific haptic alert pattern immediately.
    func play(_ alert: HapticAlert)

    /// Schedule a haptic alert for a future time.
    func schedule(_ alert: HapticAlert, at date: Date)

    /// Cancel all scheduled haptic alerts.
    func cancelAll()

    /// Cancel a specific scheduled alert type.
    func cancel(_ alert: HapticAlert)

    /// Start an Extended Runtime session to keep the app alive for background haptic delivery (NFR9).
    func startEventSession()

    /// Stop the Extended Runtime session.
    func stopEventSession()
}

// Default no-op implementations — concrete types override only what they support.
extension HapticServiceProtocol {
    func startEventSession() {}
    func stopEventSession() {}
}
