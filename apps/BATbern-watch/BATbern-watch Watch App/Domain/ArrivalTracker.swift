//  ArrivalTracker.swift
//  Domain/ArrivalTracker.swift
//
//  Speaker arrival state management for O2.
//  Subscribes to WebSocket arrivals topic and updates CachedSpeaker via SwiftData.
//  W2.4: FR38, FR39.
//  Source: docs/watch-app/architecture.md#ArrivalTracker

import Foundation
import SwiftData
import OSLog

private let logger = Logger(subsystem: "ch.batbern.watch", category: "ArrivalTracker")

/// Protocol for ArrivalTracker dependency injection (testability).
@MainActor
protocol ArrivalTrackerProtocol: AnyObject {
    var arrivedCount: Int { get }
    var totalCount: Int { get }
    func confirmArrival(speaker: CachedSpeaker) async throws
    func startListening(eventCode: String) async
    func stopListening()
}

/// Manages speaker arrival state for O2 (SpeakerArrivalView).
/// Handles optimistic local updates and real-time WebSocket sync.
@Observable
@MainActor
final class ArrivalTracker: ArrivalTrackerProtocol {

    // MARK: - Published State

    /// Number of confirmed arrived speakers (synced across all watches).
    private(set) var arrivedCount: Int = 0
    /// Total number of speakers for tonight.
    private(set) var totalCount: Int = 0

    // MARK: - Dependencies

    private let authManager: AuthManagerProtocol
    private let modelContext: ModelContext
    private let webSocketClient: WebSocketClientProtocol?
    // Stored as a closure so tests can inject a direct mock without going through URLSession
    // or URLProtocol (which is unreliable on watchOS simulator for @Observable @MainActor classes).
    @ObservationIgnored
    private let httpFetcher: @Sendable (URLRequest) async throws -> (Data, URLResponse)

    // MARK: - Private State

    private var listeningTask: Task<Void, Never>?
    private var currentEventCode: String?

    // MARK: - Init

    /// Production init. Wraps `urlSession` in the http fetcher closure.
    init(
        authManager: AuthManagerProtocol,
        modelContext: ModelContext,
        webSocketClient: WebSocketClientProtocol? = nil,
        urlSession: URLSession = .shared
    ) {
        self.authManager = authManager
        self.modelContext = modelContext
        self.webSocketClient = webSocketClient
        let capturedSession = urlSession
        self.httpFetcher = { request in
            try await withCheckedThrowingContinuation { continuation in
                capturedSession.dataTask(with: request) { data, response, error in
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

    /// Test-only init. Accepts an http fetcher closure directly, bypassing URLSession and
    /// URLProtocol entirely (URLProtocol interception is unreliable on watchOS simulator
    /// for @Observable @MainActor classes).
    init(
        authManager: AuthManagerProtocol,
        modelContext: ModelContext,
        webSocketClient: WebSocketClientProtocol? = nil,
        httpFetcher: @escaping @Sendable (URLRequest) async throws -> (Data, URLResponse)
    ) {
        self.authManager = authManager
        self.modelContext = modelContext
        self.webSocketClient = webSocketClient
        self.httpFetcher = httpFetcher
    }

    // MARK: - Public API

    /// Start listening for arrival updates via WebSocket.
    /// Also fetches initial arrival state from REST endpoint.
    func startListening(eventCode: String) async {
        currentEventCode = eventCode
        await fetchInitialArrivals(eventCode: eventCode)
        startWebSocketListener()
    }

    func stopListening() {
        listeningTask?.cancel()
        listeningTask = nil
        currentEventCode = nil
    }

    /// Confirm a speaker's arrival (optimistic update + WebSocket send).
    /// Idempotent: server ignores duplicates (UNIQUE constraint on speaker_arrivals table).
    func confirmArrival(speaker: CachedSpeaker) async throws {
        guard let organizerUsername = authManager.organizerUsername else {
            throw ArrivalError.notAuthenticated
        }
        guard let eventCode = currentEventCode else {
            throw ArrivalError.noActiveEvent
        }

        // Optimistic update: mark locally immediately (AC3)
        speaker.arrived = true
        speaker.arrivedConfirmedBy = organizerUsername
        speaker.arrivedAt = Date()

        // Save optimistic update to SwiftData
        try modelContext.save()

        // Recompute counter
        recomputeCounter()

        // Send to server via WebSocket (FR38: syncs within 3 seconds)
        if let wsClient = webSocketClient, wsClient.isConnected {
            try await wsClient.sendAction(.speakerArrived(speakerUsername: speaker.username))
        } else {
            // REST fallback when WebSocket offline
            try await confirmArrivalViaREST(
                eventCode: eventCode,
                speakerUsername: speaker.username
            )
        }
    }

    // MARK: - Private: Initial State Fetch

    private func fetchInitialArrivals(eventCode: String) async {
        guard let jwt = authManager.currentJWT else { return }

        do {
            let url = URL(string: "\(BATbernAPIConfig.baseURL)/api/v1/watch/events/\(eventCode)/arrivals")!
            var request = URLRequest(url: url)
            request.httpMethod = "GET"
            request.setValue("Bearer \(jwt)", forHTTPHeaderField: "Authorization")
            request.setValue("application/json", forHTTPHeaderField: "Accept")

            let (data, response) = try await httpFetcher(request)

            guard let httpResponse = response as? HTTPURLResponse,
                  httpResponse.statusCode == 200 else { return }

            let wrapper = try JSONDecoder().decode(ArrivalStatusWrapper.self, from: data)

            // Apply arrivals to SwiftData cache
            for arrival in wrapper.arrivals {
                updateSpeakerArrival(
                    username: arrival.speakerUsername,
                    confirmedBy: arrival.confirmedBy,
                    arrivedAt: Self.iso8601Formatter.date(from: arrival.arrivedAt) ?? Date()
                )
            }

            try? modelContext.save()
            recomputeCounter()

        } catch {
            logger.warning("Failed to fetch initial arrivals: \(error.localizedDescription)")
            // Non-fatal: continue without initial state; WebSocket updates will fill in
        }
    }

    // MARK: - Private: WebSocket Listener

    private func startWebSocketListener() {
        guard let wsClient = webSocketClient else { return }

        // Call arrivalUpdates() BEFORE creating the Task so the AsyncStream's continuation
        // is stored in wsClient synchronously. Any emitArrival() calls that happen before
        // the Task has a chance to run will buffer into the stream and be delivered later.
        let updates = wsClient.arrivalUpdates()

        listeningTask = Task { [weak self] in
            for await message in updates {
                guard !Task.isCancelled else { break }
                await self?.processArrivalMessage(message)
            }
        }
    }

    private func processArrivalMessage(_ message: SpeakerArrivalMessage) {
        updateSpeakerArrival(
            username: message.speakerUsername,
            confirmedBy: message.confirmedBy,
            arrivedAt: message.arrivedAt
        )

        // Use server-authoritative counts (FR39: real-time across all watches)
        arrivedCount = message.arrivedCount
        totalCount = message.totalCount

        do {
            try modelContext.save()
        } catch {
            logger.warning("Failed to persist arrival update: \(error.localizedDescription)")
        }
    }

    // MARK: - Private: REST Fallback

    private func confirmArrivalViaREST(eventCode: String, speakerUsername: String) async throws {
        guard let jwt = authManager.currentJWT,
              let organizerUsername = authManager.organizerUsername else {
            throw ArrivalError.notAuthenticated
        }

        let url = URL(string: "\(BATbernAPIConfig.baseURL)/api/v1/watch/events/\(eventCode)/arrivals")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(jwt)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body: [String: String] = [
            "speakerUsername": speakerUsername,
            "confirmedBy": organizerUsername
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (_, response) = try await httpFetcher(request)

        guard let httpResponse = response as? HTTPURLResponse,
              (200...201).contains(httpResponse.statusCode) else {
            throw ArrivalError.serverError
        }
    }

    // MARK: - Private: SwiftData Helpers

    private func updateSpeakerArrival(username: String, confirmedBy: String, arrivedAt: Date) {
        let descriptor = FetchDescriptor<CachedSpeaker>(
            predicate: #Predicate { speaker in speaker.username == username }
        )

        if let speaker = try? modelContext.fetch(descriptor).first {
            speaker.arrived = true
            speaker.arrivedConfirmedBy = confirmedBy
            speaker.arrivedAt = arrivedAt
        }
    }

    private func recomputeCounter() {
        // CachedSpeaker has no eventCode field, so this fetches ALL speakers from all cached
        // events. For a single-event app this is acceptable. Server-authoritative counts from
        // SpeakerArrivalMessage ALWAYS override local counts (see processArrivalMessage).
        // recomputeCounter() is only used for the optimistic local update — the next WebSocket
        // message will correct it.
        let descriptor = FetchDescriptor<CachedSpeaker>()
        guard let allSpeakers = try? modelContext.fetch(descriptor) else { return }

        totalCount = allSpeakers.count
        arrivedCount = allSpeakers.filter { $0.arrived }.count
    }
}

// MARK: - Supporting Types

private extension ArrivalTracker {
    /// Static formatter — avoids allocation per decoded arrival.
    static let iso8601Formatter: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()
}

enum ArrivalError: Error, LocalizedError {
    case notAuthenticated
    case noActiveEvent
    case serverError

    var errorDescription: String? {
        switch self {
        case .notAuthenticated:
            return NSLocalizedString("arrival.error.not_authenticated", comment: "")
        case .noActiveEvent:
            return NSLocalizedString("arrival.error.no_event", comment: "")
        case .serverError:
            return NSLocalizedString("arrival.error.server", comment: "")
        }
    }
}

private struct ArrivalStatusWrapper: Decodable {
    let arrivals: [ArrivalStatus]
}

private struct ArrivalStatus: Decodable {
    let speakerUsername: String
    let confirmedBy: String
    let arrivedAt: String
}
