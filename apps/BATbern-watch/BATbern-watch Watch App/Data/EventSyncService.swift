//
//  EventSyncService.swift
//  BATbern-watch Watch App
//
//  Syncs active event data from backend to Watch, including speaker portraits.
//  W2.3: Event Join & Schedule Sync (AC#1, AC#3)
//  Source: docs/watch-app/architecture.md#API-Communication-Patterns
//

import Foundation
import SwiftData

// MARK: - Sync State

enum SyncState: Equatable {
    case idle
    case syncing
    case completed
    case noActiveEvent
    case error(String)
}

// MARK: - Sync Error

enum SyncError: Error, LocalizedError, Equatable {
    case notAuthenticated
    case authenticationRequired
    case networkError
    case serverError(Int)
    case noActiveEvent

    var errorDescription: String? {
        switch self {
        case .notAuthenticated:
            return NSLocalizedString("sync.error.not_authenticated", comment: "Not authenticated")
        case .authenticationRequired:
            return NSLocalizedString("sync.error.auth_required", comment: "Auth required")
        case .networkError:
            return NSLocalizedString("sync.error.network", comment: "Network error")
        case .serverError(let code):
            return "Server error: \(code)"
        case .noActiveEvent:
            return NSLocalizedString("sync.error.no_event", comment: "No active event")
        }
    }
}

// MARK: - Response DTOs

struct ActiveEventsWrapper: Codable {
    let activeEvents: [ActiveEventResponse]
}

struct ActiveEventResponse: Codable {
    let eventCode: String
    let title: String
    let eventDate: String
    let venueName: String
    let typicalStartTime: String?
    let typicalEndTime: String?
    let themeImageUrl: String?
    let currentPublishedPhase: String?
    let eventStatus: String
    let sessions: [WatchSessionResponse]
}

struct WatchSessionResponse: Codable {
    let sessionSlug: String
    let title: String
    let abstract: String?
    let sessionType: String?
    let scheduledStartTime: String?
    let scheduledEndTime: String?
    let durationMinutes: Int?
    let speakers: [WatchSpeakerResponse]
    let status: String?
    let actualStartTime: String?
    let actualEndTime: String?
    let overrunMinutes: Int?
    let completedBy: String?
}

struct WatchSpeakerResponse: Codable {
    let username: String
    let firstName: String?
    let lastName: String?
    let company: String?
    let companyLogoUrl: String?
    let profilePictureUrl: String?
    let bio: String?
    let speakerRole: String?
}

// MARK: - Protocol (for testability)

@MainActor
protocol EventSyncServiceProtocol: AnyObject {
    var syncState: SyncState { get }
    var syncProgress: Double { get }
    var currentEvent: CachedEvent? { get }
    func syncActiveEvent() async throws
}

// MARK: - EventSyncService

/// Fetches active events from backend, downloads speaker portraits, and persists to SwiftData.
/// AC#1: Full schedule sync within 5 seconds; AC#3: Portraits at Watch-optimized resolution.
@Observable
@MainActor
final class EventSyncService: EventSyncServiceProtocol {

    // MARK: - Published State

    var syncState: SyncState = .idle
    var syncProgress: Double = 0.0
    var currentEvent: CachedEvent?

    // MARK: - Dependencies

    private let authManager: AuthManagerProtocol
    private let modelContext: ModelContext
    private let portraitCache: PortraitCache
    private let session: URLSession

    // MARK: - Init

    init(
        authManager: AuthManagerProtocol,
        modelContext: ModelContext,
        portraitCache: PortraitCache = PortraitCache.shared,
        session: URLSession = .shared
    ) {
        self.authManager = authManager
        self.modelContext = modelContext
        self.portraitCache = portraitCache
        self.session = session
    }

    // MARK: - Sync

    /// AC#1: Syncs active event in phases: fetch → parse → portraits → save → complete.
    func syncActiveEvent() async throws {
        // TESTING_MODE: return mock data instead of hitting the network
        if ProcessInfo.processInfo.environment["TESTING_MODE"] == "1",
           let mockResponse = ProcessInfo.processInfo.environment["MOCK_SYNC_RESPONSE"] {
            try await handleMockSyncResponse(mockResponse)
            return
        }

        syncState = .syncing
        syncProgress = 0.0

        // Step 1: Fetch active events from backend (10% progress)
        guard let jwt = authManager.currentJWT else {
            syncState = .error("Not authenticated")
            throw SyncError.notAuthenticated
        }

        let events = try await fetchActiveEvents(jwt: jwt)
        syncProgress = 0.1

        guard let event = events.first else {
            syncState = .noActiveEvent
            return
        }

        // Step 2: Parse event data (20% progress)
        let cachedEvent = mapToCachedEvent(event)
        syncProgress = 0.2

        // Step 3: Download speaker portraits (20% → 80% progress) — AC#3
        let allSpeakers = cachedEvent.sessions.flatMap { $0.speakers }
        let totalSpeakers = allSpeakers.count

        for (index, speaker) in allSpeakers.enumerated() {
            if let urlString = speaker.profilePictureUrl,
               let portraitURL = URL(string: urlString) {
                _ = try? await portraitCache.downloadAndCache(url: portraitURL)
            }
            syncProgress = 0.2 + (0.6 * Double(index + 1) / Double(max(totalSpeakers, 1)))
        }

        // Step 4: Delete stale cached event data and save new (90% progress)
        let descriptor = FetchDescriptor<CachedEvent>()
        if let stale = try? modelContext.fetch(descriptor) {
            stale.forEach { modelContext.delete($0) }
        }
        modelContext.insert(cachedEvent)
        try? modelContext.save()
        syncProgress = 0.9

        // Step 5: Complete (100% progress)
        currentEvent = cachedEvent
        syncState = .completed
        syncProgress = 1.0
    }

    // MARK: - Private helpers

    private func fetchActiveEvents(jwt: String) async throws -> [ActiveEventResponse] {
        guard let url = URL(string: "\(BATbernAPIConfig.baseURL)/api/v1/watch/organizers/me/active-events") else {
            throw SyncError.networkError
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("Bearer \(jwt)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw SyncError.networkError
        }

        guard let httpResponse = response as? HTTPURLResponse else {
            throw SyncError.networkError
        }

        if httpResponse.statusCode == 401 {
            try? await authManager.refreshJWT()
            throw SyncError.authenticationRequired
        }

        guard httpResponse.statusCode == 200 else {
            throw SyncError.serverError(httpResponse.statusCode)
        }

        let wrapper = try JSONDecoder().decode(ActiveEventsWrapper.self, from: data)
        return wrapper.activeEvents
    }

    // MARK: - Mock Support (TESTING_MODE only)

    private func handleMockSyncResponse(_ response: String) async throws {
        syncState = .syncing
        syncProgress = 0.0

        switch response {
        case "loading":
            // Stay in syncing state long enough for the UI test to observe it
            try await Task.sleep(nanoseconds: 60_000_000_000)
            syncState = .error("loading cancelled")

        case "no_events":
            syncState = .noActiveEvent
            syncProgress = 1.0

        case "event_2h_away":
            currentEvent = makeMockEvent(startOffsetSeconds: 7200, endOffsetSeconds: 10800)
            syncState = .completed
            syncProgress = 1.0

        case "event_30min_away":
            currentEvent = makeMockEvent(startOffsetSeconds: 1800, endOffsetSeconds: 5400)
            syncState = .completed
            syncProgress = 1.0

        default:
            syncState = .noActiveEvent
            syncProgress = 1.0
        }
    }

    /// Build a mock CachedEvent whose start/end times are offset from now (Europe/Zurich).
    private func makeMockEvent(startOffsetSeconds: TimeInterval, endOffsetSeconds: TimeInterval) -> CachedEvent {
        let now = Date()
        var zurichCalendar = Calendar(identifier: .gregorian)
        zurichCalendar.timeZone = TimeZone(identifier: "Europe/Zurich") ?? .current

        let startDate = now.addingTimeInterval(startOffsetSeconds)
        let endDate = now.addingTimeInterval(endOffsetSeconds)
        let startHH = zurichCalendar.component(.hour, from: startDate)
        let startMM = zurichCalendar.component(.minute, from: startDate)
        let endHH = zurichCalendar.component(.hour, from: endDate)
        let endMM = zurichCalendar.component(.minute, from: endDate)

        return CachedEvent(
            eventCode: "BAT99",
            title: "BATbern 99 - Cloud Native",
            eventDate: now,
            themeImageUrl: nil,
            venueName: "Uni Bern",
            typicalStartTime: String(format: "%02d:%02d", startHH, startMM),
            typicalEndTime: String(format: "%02d:%02d", endHH, endMM),
            currentPublishedPhase: nil,
            lastSyncTimestamp: now
        )
    }

    private func mapToCachedEvent(_ response: ActiveEventResponse) -> CachedEvent {
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd"
        let eventDate = dateFormatter.date(from: response.eventDate) ?? Date()

        let cachedEvent = CachedEvent(
            eventCode: response.eventCode,
            title: response.title,
            eventDate: eventDate,
            themeImageUrl: response.themeImageUrl,
            venueName: response.venueName,
            typicalStartTime: response.typicalStartTime ?? "18:00",
            typicalEndTime: response.typicalEndTime ?? "22:00",
            currentPublishedPhase: response.currentPublishedPhase,
            lastSyncTimestamp: Date()
        )

        let iso = ISO8601DateFormatter()
        cachedEvent.sessions = response.sessions.map { sessionResp in
            let cachedSession = CachedSession(
                sessionSlug: sessionResp.sessionSlug,
                title: sessionResp.title,
                abstract: sessionResp.abstract,
                sessionType: sessionResp.sessionType.flatMap { SessionType(rawValue: $0) },
                startTime: sessionResp.scheduledStartTime.flatMap { iso.date(from: $0) },
                endTime: sessionResp.scheduledEndTime.flatMap { iso.date(from: $0) },
                actualStartTime: sessionResp.actualStartTime.flatMap { iso.date(from: $0) },
                overrunMinutes: sessionResp.overrunMinutes
            )
            cachedSession.speakers = sessionResp.speakers.map { speakerResp in
                CachedSpeaker(
                    username: speakerResp.username,
                    firstName: speakerResp.firstName ?? "",
                    lastName: speakerResp.lastName ?? "",
                    company: speakerResp.company,
                    companyLogoUrl: speakerResp.companyLogoUrl,
                    profilePictureUrl: speakerResp.profilePictureUrl,
                    bio: speakerResp.bio,
                    speakerRole: speakerResp.speakerRole.flatMap { SpeakerRole(rawValue: $0) } ?? .panelist
                )
            }
            return cachedSession
        }

        return cachedEvent
    }
}
