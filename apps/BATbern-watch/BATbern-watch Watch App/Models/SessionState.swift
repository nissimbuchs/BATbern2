import Foundation

/// Lifecycle state of an event session.
enum SessionState: String, Codable, Sendable {
    case scheduled
    case active
    case completed
    case skipped
}
