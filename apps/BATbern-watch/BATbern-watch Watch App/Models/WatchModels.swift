import Foundation

// MARK: - Domain Models
// Lightweight value types for domain logic and API DTOs.
// Separate from SwiftData @Model classes used for persistence.

struct WatchEvent: Sendable, Identifiable {
    let id: String // eventCode
    let title: String
    let date: Date
    let themeImageUrl: String?
    let venueName: String
    let sessions: [WatchSession]
}

struct WatchSession: Sendable, Identifiable {
    let id: String // sessionSlug
    let title: String
    let abstract: String?
    let sessionType: SessionType
    var startTime: Date
    var endTime: Date
    let speakers: [WatchSpeaker]
    var state: SessionState
    var actualStartTime: Date?
    var overrunMinutes: Int?

    var duration: TimeInterval {
        endTime.timeIntervalSince(startTime)
    }

    var isBreak: Bool {
        sessionType == .breakTime || sessionType == .lunch
    }
}

enum SessionType: String, Codable, Sendable {
    case keynote
    case presentation
    case workshop
    case panelDiscussion = "panel_discussion"
    case networking
    case breakTime = "break"
    case lunch
}

struct WatchSpeaker: Sendable, Identifiable {
    let id: String // username
    let firstName: String
    let lastName: String
    let company: String?
    let companyLogoUrl: String?
    let profilePictureUrl: String?
    let bio: String?
    let speakerRole: SpeakerRole
    var arrived: Bool
    var arrivedConfirmedBy: String?
    var arrivedAt: Date?

    var fullName: String { "\(firstName) \(lastName)" }
}

enum SpeakerRole: String, Codable, Sendable {
    case keynoteSpeaker = "keynote_speaker"
    case panelist
    case moderator
}

// MARK: - Urgency Level

/// Urgency level for countdown display, driving color transitions.
enum UrgencyLevel: String, Sendable {
    case normal    // Green — > 5 min
    case caution   // Yellow — 2-5 min
    case warning   // Orange — 1-2 min
    case critical  // Red — < 1 min
    case overtime  // Pulsing red — past end time
}
