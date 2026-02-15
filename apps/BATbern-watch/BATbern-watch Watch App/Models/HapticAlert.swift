import Foundation

/// Haptic alert types fired during event orchestration.
/// Each maps to a distinct WKHapticType pattern for tactile differentiation.
enum HapticAlert: String, Sendable, CaseIterable, Hashable {
    case fiveMinuteWarning   // .notification + short vibration
    case twoMinuteWarning    // .notification + double pulse
    case timesUp             // .notification + long sustained
    case overtimePulse       // .start repeating every 30s
    case gongReminder        // .notification + triple tap
    case actionConfirm       // .success
    case connectionLost      // .failure + visual banner
}
