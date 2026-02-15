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
}
