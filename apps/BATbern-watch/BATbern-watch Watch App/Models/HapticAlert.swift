import Foundation

/// Haptic alert types fired during event orchestration.
/// Each maps to a distinct WKHapticType pattern for tactile differentiation.
enum HapticAlert: String, Sendable, CaseIterable, Hashable {
    case fiveMinuteWarning   // .notification — medium single buzz
    case twoMinuteWarning    // .failure — heavy single bump (type-distinct from 5-min)
    case timesUp             // .failure + .stop (300ms) — heavy-thud combo
    case overtimePulse       // .failure repeating every 30s
    case gongReminder        // .notification × 3 (150/300ms) — "last call" for break end
    case actionConfirm       // .success
    case connectionLost      // .failure + visual banner
}
