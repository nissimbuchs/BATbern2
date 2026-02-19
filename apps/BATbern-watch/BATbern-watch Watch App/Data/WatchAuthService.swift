//
//  WatchAuthService.swift
//  BATbern-watch Watch App
//
//  REST client for watch pairing and JWT authentication endpoints.
//  W2.2: Implements POST /api/v1/watch/pair and POST /api/v1/watch/authenticate.
//  Source: docs/watch-app/architecture.md#Authentication-Security
//

import Foundation

// MARK: - Protocol (for testability)

/// Contract for Watch pairing and authentication operations.
protocol WatchAuthServiceProtocol: Sendable {
    func pair(code: String) async throws -> WatchPairingResult
    func authenticate(pairingToken: String) async throws -> WatchAuthTokenResult
}

// MARK: - Domain Result Types

struct WatchPairingResult: Sendable {
    let pairingToken: String
    let organizerUsername: String
    let organizerFirstName: String
}

struct WatchAuthTokenResult: Sendable {
    let jwt: String
    let expiresAt: Date
}

// MARK: - Network DTOs

private struct PairingRequest: Encodable {
    let pairingCode: String
}

private struct PairingResponse: Decodable {
    let pairingToken: String
    let organizerUsername: String
    let organizerFirstName: String
}

private struct AuthRequest: Encodable {
    let pairingToken: String
}

private struct AuthResponse: Decodable {
    let jwt: String
    let expiresAt: String  // ISO 8601
}

// MARK: - Error Types

enum WatchAuthError: Error, LocalizedError {
    case invalidCode
    case invalidToken
    case networkError(Error)
    case serverError(Int)
    case decodingError(Error)

    var errorDescription: String? {
        switch self {
        case .invalidCode:
            return NSLocalizedString("pairing.error", comment: "Code invalid or expired")
        case .invalidToken:
            return NSLocalizedString("error.auth_failed", comment: "Authentication failed")
        case .networkError:
            return NSLocalizedString("error.network", comment: "Network error")
        case .serverError(let code):
            return "Server error: \(code)"
        case .decodingError:
            return NSLocalizedString("error.invalid_response", comment: "Invalid response")
        }
    }
}

// MARK: - Production Implementation

/// Production REST client for watch pairing and authentication.
final class WatchAuthService: WatchAuthServiceProtocol {
    private let baseURL: String
    private let session: URLSession

    init(
        baseURL: String = BATbernAPIConfig.baseURL,
        session: URLSession = .shared
    ) {
        self.baseURL = baseURL
        self.session = session
    }

    private static func parseExpiresAt(_ isoString: String) -> Date {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = formatter.date(from: isoString) { return date }
        // Fallback: try without fractional seconds (e.g. "2026-02-16T15:30:00Z")
        let fallback = ISO8601DateFormatter()
        fallback.formatOptions = [.withInternetDateTime]
        if let date = fallback.date(from: isoString) { return date }
        // L1 fix: log warning when both formats fail — silent fallback hides backend date format bugs
        print("⚠️ WatchAuthService: parseExpiresAt failed for '\(isoString)' — using fallback 1h from now")
        return Date().addingTimeInterval(3600)
    }

    // MARK: - WatchAuthServiceProtocol

    /// AC2/AC3: Exchange 6-digit pairing code for pairing token.
    func pair(code: String) async throws -> WatchPairingResult {
        guard let url = URL(string: "\(baseURL)/api/v1/watch/pair") else {
            throw WatchAuthError.networkError(URLError(.badURL))
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(PairingRequest(pairingCode: code))

        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw WatchAuthError.networkError(URLError(.badServerResponse))
        }

        switch httpResponse.statusCode {
        case 200:
            break
        case 400:
            throw WatchAuthError.invalidCode
        default:
            throw WatchAuthError.serverError(httpResponse.statusCode)
        }

        do {
            let dto = try JSONDecoder().decode(PairingResponse.self, from: data)
            return WatchPairingResult(
                pairingToken: dto.pairingToken,
                organizerUsername: dto.organizerUsername,
                organizerFirstName: dto.organizerFirstName
            )
        } catch {
            throw WatchAuthError.decodingError(error)
        }
    }

    /// AC4/NFR16: Exchange pairing token for short-lived JWT.
    func authenticate(pairingToken: String) async throws -> WatchAuthTokenResult {
        guard let url = URL(string: "\(baseURL)/api/v1/watch/authenticate") else {
            throw WatchAuthError.networkError(URLError(.badURL))
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(AuthRequest(pairingToken: pairingToken))

        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw WatchAuthError.networkError(URLError(.badServerResponse))
        }

        switch httpResponse.statusCode {
        case 200:
            break
        case 401:
            throw WatchAuthError.invalidToken
        default:
            throw WatchAuthError.serverError(httpResponse.statusCode)
        }

        do {
            let dto = try JSONDecoder().decode(AuthResponse.self, from: data)
            // Parse ISO 8601 expiry date
            let expiresAt = WatchAuthService.parseExpiresAt(dto.expiresAt)
            return WatchAuthTokenResult(jwt: dto.jwt, expiresAt: expiresAt)
        } catch {
            throw WatchAuthError.decodingError(error)
        }
    }
}
