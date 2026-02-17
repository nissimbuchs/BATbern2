//
//  WatchHapticService.swift
//  BATbern-watch Watch App
//
//  Concrete HapticServiceProtocol implementation using WKInterfaceDevice.
//  W3.1: Minimal implementation that drives HapticScheduler evaluations.
//  W3.2: Will expand with distinct pattern differentiation per alert type.
//  Source: docs/watch-app/architecture.md#Implementation-Patterns
//

import WatchKit

/// Plays haptic patterns on Apple Watch via WKInterfaceDevice.
/// Uses WKHapticType to differentiate alert types where possible.
final class WatchHapticService: HapticServiceProtocol, @unchecked Sendable {

    func play(_ alert: HapticAlert) {
        let type: WKHapticType
        switch alert {
        case .fiveMinuteWarning:  type = .notification
        case .twoMinuteWarning:   type = .notification
        case .timesUp:            type = .notification
        case .overtimePulse:      type = .start
        case .gongReminder:       type = .notification
        case .actionConfirm:      type = .success
        case .connectionLost:     type = .failure
        }
        WKInterfaceDevice.current().play(type)
    }

    func schedule(_ alert: HapticAlert, at date: Date) {
        // Scheduled haptics expanded in W3.2 — no-op in W3.1
    }

    func cancelAll() {}

    func cancel(_ alert: HapticAlert) {}
}
