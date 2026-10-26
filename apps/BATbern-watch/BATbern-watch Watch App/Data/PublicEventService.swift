//
//  PublicEventService.swift
//  BATbern-watch Watch App
//
//  REST API client for fetching public event data.
//  Conforms to APIClientProtocol for testability with MockAPIClient.
//  Source: docs/watch-app/architecture.md#Public-Zone-Data-Flow
//

import Foundation

/// Error types for API operations
enum APIError: Error, LocalizedError {
    case noCurrentEvent
    case invalidResponse
    case networkError(Error)
    case decodingError(Error)

    var errorDescription: String? {
        switch self {
        case .noCurrentEvent:
            return "No current BATbern event found"
        case .invalidResponse:
            return "Invalid API response"
        case .networkError(let error):
            return "Network error: \(error.localizedDescription)"
        case .decodingError(let error):
            return "Failed to decode response: \(error.localizedDescription)"
        }
    }
}

/// Production REST client implementing APIClientProtocol
class PublicEventService: APIClientProtocol {
    // MARK: - Configuration

    private let baseURL: String
    private let session: URLSession

    init(
        baseURL: String = BATbernAPIConfig.baseURL,
        session: URLSession = .shared
    ) {
        self.baseURL = baseURL
        self.session = session
    }

    // MARK: - APIClientProtocol Implementation

    func fetchCurrentEvent() async throws -> WatchEvent {
        // Public read model (#1070): sessions/speakers shaped by the publishing phase, the
        // same answer for every caller. The watch never sent a JWT here, so the organizer
        // zone always received this public view on the legacy /events/current as well.
        guard let url = URL(string: "\(baseURL)/api/v1/public/events/current") else {
            print("❌ Failed to construct URL")
            throw APIError.invalidResponse
        }

        print("🌐 Fetching event from: \(url.absoluteString)")

        // Execute HTTP GET request
        let (data, response) = try await get(url)

        // Validate HTTP status
        guard let httpResponse = response as? HTTPURLResponse else {
            print("❌ Response is not HTTPURLResponse")
            throw APIError.invalidResponse
        }

        print("📡 HTTP Status: \(httpResponse.statusCode)")

        switch httpResponse.statusCode {
        case 200:
            break  // Success
        case 404:
            print("⚠️ No current event (404)")
            throw APIError.noCurrentEvent
        default:
            print("❌ Unexpected status code: \(httpResponse.statusCode)")
            throw APIError.invalidResponse
        }

        // Parse JSON response
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601  // Generated types expect Date objects
        let eventDetail: EventDetail

        print("📦 Response data size: \(data.count) bytes")
        if let jsonString = String(data: data, encoding: .utf8) {
            print("📄 Raw JSON: \(jsonString.prefix(500))...")
        }

        do {
            eventDetail = try decoder.decode(EventDetail.self, from: data)
            print("✅ Decoded EventDetail: \(eventDetail.eventCode) - \(eventDetail.title)")
            print("   Sessions count: \(eventDetail.sessions?.count ?? 0)")
            print("   currentPublishedPhase: \(String(describing: eventDetail.currentPublishedPhase))")
        } catch {
            print("❌ Decoding error: \(error)")
            if let decodingError = error as? DecodingError {
                switch decodingError {
                case .keyNotFound(let key, let context):
                    print("   Missing key: \(key.stringValue) at \(context.codingPath)")
                case .typeMismatch(let type, let context):
                    print("   Type mismatch: expected \(type) at \(context.codingPath)")
                case .valueNotFound(let type, let context):
                    print("   Value not found: \(type) at \(context.codingPath)")
                case .dataCorrupted(let context):
                    print("   Data corrupted at \(context.codingPath)")
                @unknown default:
                    print("   Unknown decoding error")
                }
            }
            throw APIError.decodingError(error)
        }

        // Convert generated EventDetail → domain model WatchEvent (using generated field)
        let watchEvent = eventDetail.toWatchEvent()
        print("✅ Converted to WatchEvent with \(watchEvent.sessions.count) sessions")
        return watchEvent
    }

    func fetchEvent(code: String) async throws -> WatchEvent {
        // Construct URL
        guard let url = URL(string: "\(baseURL)/api/v1/public/events/\(code)") else {
            throw APIError.invalidResponse
        }

        // Execute HTTP GET request
        let (data, response) = try await get(url)

        // Validate HTTP status
        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }

        guard httpResponse.statusCode == 200 else {
            throw APIError.invalidResponse
        }

        // Parse JSON response
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let eventDetail: EventDetail

        do {
            eventDetail = try decoder.decode(EventDetail.self, from: data)
        } catch {
            throw APIError.decodingError(error)
        }

        // Convert generated EventDetail → domain model
        return eventDetail.toWatchEvent()
    }

    // MARK: - Transport

    /// GET that reports transport failures (server unreachable, timeout, no route) as
    /// `APIError.networkError`, so EventDataController classifies them as offline (#1122).
    /// A raw URLError used to fall through to its generic "unexpected error" branch,
    /// leaving `isOffline` false.
    private func get(_ url: URL) async throws -> (Data, URLResponse) {
        do {
            return try await session.data(from: url)
        } catch {
            throw APIError.networkError(error)
        }
    }

    // MARK: - Epic 2 Stubs (not implemented yet)

    func pair(code: String) async throws -> PairingResult {
        throw APIError.notImplemented
    }

    func authenticate(pairingToken: String) async throws -> AuthResult {
        throw APIError.notImplemented
    }
}

// MARK: - Error Extension

extension APIError {
    static let notImplemented = APIError.invalidResponse  // Placeholder for unimplemented methods
}
