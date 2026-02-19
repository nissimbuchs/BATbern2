//
//  OrganizerEventClientProtocol.swift
//  BATbern-watch Watch App
//
//  Protocol + implementation for the watch-specific organizer event endpoint.
//  Extracted from EventSyncService to enable dependency injection and testing.
//

import Foundation

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

// MARK: - Protocol

protocol OrganizerEventClientProtocol: AnyObject, Sendable {
    func fetchActiveEvents(jwt: String) async throws -> [ActiveEventResponse]
}

// MARK: - Production Implementation

final class WatchOrganizerEventService: OrganizerEventClientProtocol, @unchecked Sendable {

    private let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    func fetchActiveEvents(jwt: String) async throws -> [ActiveEventResponse] {
        guard let url = URL(string: "\(BATbernAPIConfig.baseURL)/api/v1/watch/organizers/me/active-events") else {
            throw SyncError.networkError
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("Bearer \(jwt)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await fetchData(for: request)
        } catch {
            throw SyncError.networkError
        }

        guard let httpResponse = response as? HTTPURLResponse else {
            throw SyncError.networkError
        }

        if httpResponse.statusCode == 401 {
            throw SyncError.authenticationRequired
        }

        guard httpResponse.statusCode == 200 else {
            throw SyncError.serverError(httpResponse.statusCode)
        }

        let wrapper = try JSONDecoder().decode(ActiveEventsWrapper.self, from: data)
        return wrapper.activeEvents
    }

    // MARK: - Networking Helper

    /// Uses completion-handler dataTask instead of Swift async data(for:) because the async
    /// path bypasses URLProtocol on watchOS Simulator (test mocks don't intercept it).
    private func fetchData(for request: URLRequest) async throws -> (Data, URLResponse) {
        try await withCheckedThrowingContinuation { continuation in
            session.dataTask(with: request) { data, response, error in
                if let error = error {
                    continuation.resume(throwing: error)
                } else if let data = data, let response = response {
                    continuation.resume(returning: (data, response))
                } else {
                    continuation.resume(throwing: URLError(.unknown))
                }
            }.resume()
        }
    }
}
