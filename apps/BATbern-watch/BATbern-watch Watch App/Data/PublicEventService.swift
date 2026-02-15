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
        baseURL: String = "https://api.staging.batbern.ch",
        session: URLSession = .shared
    ) {
        self.baseURL = baseURL
        self.session = session
    }

    // MARK: - APIClientProtocol Implementation

    func fetchCurrentEvent() async throws -> WatchEvent {
        // Construct URL with query parameters
        guard var components = URLComponents(string: "\(baseURL)/api/v1/events/current") else {
            throw APIError.invalidResponse
        }

        components.queryItems = [
            URLQueryItem(name: "include", value: "topics,venue,sessions")
        ]

        guard let url = components.url else {
            throw APIError.invalidResponse
        }

        // Execute HTTP GET request
        let (data, response) = try await session.data(from: url)

        // Validate HTTP status
        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }

        switch httpResponse.statusCode {
        case 200:
            break  // Success
        case 404:
            throw APIError.noCurrentEvent
        default:
            throw APIError.invalidResponse
        }

        // Parse JSON response
        let decoder = JSONDecoder()
        let eventResponse: EventResponse

        do {
            eventResponse = try decoder.decode(EventResponse.self, from: data)
        } catch {
            throw APIError.decodingError(error)
        }

        // Convert DTO → domain model
        return eventResponse.toWatchEvent()
    }

    func fetchEvent(code: String) async throws -> WatchEvent {
        // Construct URL
        guard let url = URL(string: "\(baseURL)/api/v1/events/\(code)?include=topics,venue,sessions") else {
            throw APIError.invalidResponse
        }

        // Execute HTTP GET request
        let (data, response) = try await session.data(from: url)

        // Validate HTTP status
        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }

        guard httpResponse.statusCode == 200 else {
            throw APIError.invalidResponse
        }

        // Parse JSON response
        let decoder = JSONDecoder()
        let eventResponse: EventResponse

        do {
            eventResponse = try decoder.decode(EventResponse.self, from: data)
        } catch {
            throw APIError.decodingError(error)
        }

        // Convert DTO → domain model
        return eventResponse.toWatchEvent()
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
