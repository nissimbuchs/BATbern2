# Story W2.4: Speaker Arrival Tracking

Status: done
Review: pre-implementation adversarial review complete (2026-02-17) — all HIGH/MEDIUM issues fixed in story design

## Story

As an organizer,
I want to see all speakers before the event and tap to confirm their arrival, with the status syncing to all organizer watches,
so that the whole team knows who's here.

## Acceptance Criteria

1. **AC1 — Portrait Overview**: Given I'm paired and the event is <1 hour away, When I enter the organizer zone, Then I see the speaker portrait overview (O2) with all speakers and an arrival counter ("0 of N arrived").

2. **AC2 — Confirmation Prompt**: Given I'm on O2, When I tap a speaker's portrait, Then I see a confirmation prompt: "Has [Name] arrived?" with "Arrived" and "Not yet" buttons.

3. **AC3 — Local Confirmation**: Given I confirm a speaker's arrival, When I tap "Arrived", Then a green checkmark badge appears on their portrait immediately (optimistic UI) and syncs to all organizer watches within 3 seconds (FR38).

4. **AC4 — Real-Time Sync**: Given another organizer confirms an arrival on their Watch, When the sync arrives via WebSocket `/topic/events/{eventCode}/arrivals`, Then my arrival counter and badges update in real time (FR39).

5. **AC5 — No Duplicate Confirmation**: Given a speaker is already confirmed, When I tap their portrait, Then I see "Confirmed by [organizer name]" — no second confirmation button shown.

## Tasks / Subtasks

- [x] **Task 1: Extend WebSocketClientProtocol with Arrival Updates** (AC: #4)
  - [ ] 1.1 Modify `apps/BATbern-watch/BATbern-watch Watch App/Protocols/WebSocketClientProtocol.swift`
  - [ ] 1.2 Add `SpeakerArrivalMessage` struct (separate from `EventStateMessage`):
    ```swift
    struct SpeakerArrivalMessage: Sendable {
        let speakerUsername: String
        let speakerFirstName: String
        let speakerLastName: String
        let confirmedBy: String
        let arrivedAt: Date
        let arrivedCount: Int
        let totalCount: Int
    }
    ```
  - [ ] 1.3 Add `arrivalUpdates() -> AsyncStream<SpeakerArrivalMessage>` to protocol
  - [ ] 1.4 Update `MockWebSocketClient.swift` — add `arrivalUpdates()` conformance + test helper:
    ```swift
    // ADD to MockWebSocketClient — alongside existing stateUpdatesContinuation:
    private var arrivalUpdatesContinuation: AsyncStream<SpeakerArrivalMessage>.Continuation?

    func arrivalUpdates() -> AsyncStream<SpeakerArrivalMessage> {
        AsyncStream { continuation in
            self.arrivalUpdatesContinuation = continuation
        }
    }

    /// Emit an arrival update to subscribers (mirrors existing emit() for state updates).
    func emitArrival(_ message: SpeakerArrivalMessage) {
        arrivalUpdatesContinuation?.yield(message)
    }
    ```
    > ⚠️ [AI-Review][HIGH] Adding `arrivalUpdates()` to the protocol immediately breaks existing tests
    > because `MockWebSocketClient` won't conform. This subtask MUST be done atomically with 1.3.

  - [ ] 1.5 Create `apps/BATbern-watch/BATbern-watch Watch AppTests/Mocks/MockAuthManager.swift`:
    ```swift
    @testable import BATbern_watch_Watch_App
    import Foundation

    /// Mock AuthManager for unit tests. Conforms to AuthManagerProtocol.
    @MainActor
    final class MockAuthManager: AuthManagerProtocol {
        var isPaired: Bool
        var organizerUsername: String?
        var organizerFirstName: String?
        var currentJWT: String?

        init(
            isPaired: Bool = true,
            organizerUsername: String? = "marco.muster",
            organizerFirstName: String? = "Marco",
            currentJWT: String? = "mock-jwt-token"
        ) {
            self.isPaired = isPaired
            self.organizerUsername = organizerUsername
            self.organizerFirstName = organizerFirstName
            self.currentJWT = currentJWT
        }

        func pair(code: String) async throws {}
        func refreshJWT() async throws {}
        func unpair() { isPaired = false }
    }
    ```
    > ⚠️ [AI-Review][HIGH] Task 11 tests all reference `MockAuthManager` but this file does NOT exist
    > in `BATbern-watch Watch AppTests/Mocks/`. Must create before Task 11 tests can compile.

  - [ ] 1.6 Clarify `EventStateMessageType.speakerArrived` in `WebSocketClientProtocol.swift`:
    - The existing `case speakerArrived = "SPEAKER_ARRIVED"` on `EventStateMessageType` was added as a placeholder
    - **Remove** it from `EventStateMessageType` — arrival events are handled exclusively via the new `arrivalUpdates()` stream on the dedicated topic
    - This prevents ambiguity: state updates go through `stateUpdates()`, arrival updates through `arrivalUpdates()`
    > ⚠️ [AI-Review][MEDIUM] Without this clarification the dev will leave dead code or route the same
    > event through two different streams causing unpredictable behaviour.

- [x] **Task 2: ArrivalTracker — Domain Layer** (AC: #1, #3, #4, #5)
  - [ ] 2.1 Create `apps/BATbern-watch/BATbern-watch Watch App/Domain/ArrivalTracker.swift`
  - [ ] 2.2 ArrivalTracker responsibilities:
    - Manage speaker arrival state (optimistic + server-confirmed)
    - Subscribe to WebSocket `/topic/events/{eventCode}/arrivals`
    - Merge WebSocket arrivals into `CachedSpeaker` via SwiftData
    - Compute `arrivedCount` and `totalCount` for the counter display
  - [ ] 2.3 Implementation:
    ```swift
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
        private let apiConfig = BATbernAPIConfig.self

        // MARK: - Private State

        private var listeningTask: Task<Void, Never>?
        private var currentEventCode: String?

        // MARK: - Init

        init(
            authManager: AuthManagerProtocol,
            modelContext: ModelContext,
            webSocketClient: WebSocketClientProtocol? = nil
        ) {
            self.authManager = authManager
            self.modelContext = modelContext
            self.webSocketClient = webSocketClient
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
            try? modelContext.save()

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

                let (data, response) = try await URLSession.shared.data(for: request)

                guard let httpResponse = response as? HTTPURLResponse,
                      httpResponse.statusCode == 200 else { return }

                let wrapper = try JSONDecoder().decode(ArrivalStatusWrapper.self, from: data)

                // Apply arrivals to SwiftData cache
                // ⚠️ [AI-Review][MEDIUM] Use static formatter — ISO8601DateFormatter is expensive to create
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

            listeningTask = Task { [weak self] in
                for await message in wsClient.arrivalUpdates() {
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

            try? modelContext.save()
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

            let (_, response) = try await URLSession.shared.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse,
                  (200...201).contains(httpResponse.statusCode) else {
                throw ArrivalError.serverError
            }
        }

        // MARK: - Private: SwiftData Helpers

        private func updateSpeakerArrival(username: String, confirmedBy: String, arrivedAt: Date) {
            // Find speaker in SwiftData context
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
            // ⚠️ [AI-Review][MEDIUM] CachedSpeaker has no eventCode field, so this fetches ALL
            // speakers from all cached events. For a single-event app this is acceptable.
            // Server-authoritative counts from SpeakerArrivalMessage ALWAYS override local counts
            // (see processArrivalMessage). recomputeCounter() is only used for the optimistic
            // local update in confirmArrival() — the next WebSocket message will correct it.
            let descriptor = FetchDescriptor<CachedSpeaker>()
            guard let allSpeakers = try? modelContext.fetch(descriptor) else { return }

            totalCount = allSpeakers.count
            arrivedCount = allSpeakers.filter { $0.arrived }.count
        }
    }

    // MARK: - Supporting Types

    // ⚠️ [AI-Review][MEDIUM] Static formatter — avoids allocation per decoded arrival
    private extension ArrivalTracker {
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
            case .notAuthenticated: return NSLocalizedString("arrival.error.not_authenticated", comment: "")
            case .noActiveEvent: return NSLocalizedString("arrival.error.no_event", comment: "")
            case .serverError: return NSLocalizedString("arrival.error.server", comment: "")
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
    ```

- [x] **Task 3: Full SpeakerArrivalView — Portrait Grid UI** (AC: #1, #2, #5)
  - [ ] 3.1 Replace placeholder in `apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/SpeakerArrivalView.swift`
  - [ ] 3.2 Display 2-column portrait grid with arrival badges
  - [ ] 3.3 Show arrival counter header ("0 of N arrived")
  - [ ] 3.4 Tap speaker → sheet confirmation prompt (AC2)
  - [ ] 3.5 Already-confirmed speakers show "Confirmed by [name]" on tap (AC5)
  - [ ] 3.6 Implementation:
    ```swift
    //  SpeakerArrivalView.swift
    //  Views/Organizer/SpeakerArrivalView.swift
    //
    //  O2: Speaker portrait overview with arrival tracking.
    //  W2.4: FR36-FR39.
    //  Source: docs/watch-app/ux-design-specification.md#Speaker-Portrait-Overview-(O2)

    import SwiftUI
    import SwiftData

    struct SpeakerArrivalView: View {
        @Environment(ArrivalTracker.self) private var arrivalTracker
        @Environment(EventStateManager.self) private var eventState
        @Query private var speakers: [CachedSpeaker]

        @State private var selectedSpeaker: CachedSpeaker?

        var body: some View {
            ScrollView {
                VStack(spacing: 8) {
                    // Arrival counter header (AC1, FR39)
                    arrivalCounterHeader

                    // Portrait grid (2 columns, AC1)
                    LazyVGrid(
                        columns: [
                            GridItem(.flexible(), spacing: 8),
                            GridItem(.flexible(), spacing: 8)
                        ],
                        spacing: 8
                    ) {
                        ForEach(speakers) { speaker in
                            SpeakerPortraitCell(speaker: speaker)
                                .onTapGesture {
                                    selectedSpeaker = speaker
                                }
                        }
                    }

                    // Event start reminder
                    if let event = eventState.currentEvent {
                        Text(
                            NSLocalizedString("arrival.event_starts_at", comment: "")
                                + " "
                                + SwissDateFormatter.formatTimeString(event.typicalStartTime)
                        )
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .padding(.top, 4)
                    }
                }
                .padding(.horizontal, 4)
            }
            // Confirmation sheet (AC2, AC5)
            // ⚠️ Pass as `any ArrivalTrackerProtocol` — see ArrivalConfirmationView
            .sheet(item: $selectedSpeaker) { speaker in
                ArrivalConfirmationView(
                    speaker: speaker,
                    arrivalTracker: arrivalTracker as any ArrivalTrackerProtocol
                )
            }
            .navigationTitle(NSLocalizedString("arrival.tonight_speakers", comment: ""))
            .navigationBarTitleDisplayMode(.inline)
            .task {
                // Start WebSocket listener when view appears
                if let eventCode = eventState.currentEvent?.eventCode {
                    await arrivalTracker.startListening(eventCode: eventCode)
                }
            }
            .onDisappear {
                arrivalTracker.stopListening()
            }
        }

        // MARK: - Subviews

        private var arrivalCounterHeader: some View {
            HStack {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(.green)
                    .font(.caption)
                Text(
                    String(
                        format: NSLocalizedString("arrival.counter_format", comment: ""),
                        arrivalTracker.arrivedCount,
                        arrivalTracker.totalCount
                    )
                )
                .font(.caption)
                .fontWeight(.semibold)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 4)
        }
    }

    // MARK: - Speaker Portrait Cell

    struct SpeakerPortraitCell: View {
        let speaker: CachedSpeaker

        var body: some View {
            ZStack(alignment: .bottomTrailing) {
                VStack(spacing: 4) {
                    // Portrait
                    SpeakerPortraitView(
                        speaker: speaker,
                        size: 52
                    )

                    // Name (first name only for space efficiency on Watch)
                    Text(speaker.firstName)
                        .font(.caption2)
                        .lineLimit(1)
                        .foregroundStyle(speaker.arrived ? .primary : .secondary)
                }
                .padding(6)
                .background(
                    RoundedRectangle(cornerRadius: 8)
                        .fill(speaker.arrived
                            ? Color.green.opacity(0.1)
                            : Color(.systemGray6)
                        )
                )

                // Green ✓ badge (AC3)
                if speaker.arrived {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.green)
                        .background(Circle().fill(.black))
                        .offset(x: 4, y: 4)
                }
            }
        }
    }

    // MARK: - Confirmation Sheet

    // ⚠️ [AI-Review][HIGH] Use protocol type — concrete ArrivalTracker violates DI pattern
    struct ArrivalConfirmationView: View {
        let speaker: CachedSpeaker
        let arrivalTracker: any ArrivalTrackerProtocol

        @Environment(\.dismiss) private var dismiss
        @State private var isConfirming = false

        var body: some View {
            VStack(spacing: 12) {
                // Portrait
                SpeakerPortraitView(speaker: speaker, size: 44)

                if speaker.arrived {
                    // AC5: Already confirmed — show who confirmed (no duplicate action)
                    alreadyConfirmedView
                } else {
                    // AC2: Confirmation prompt
                    confirmationPromptView
                }
            }
            .padding()
        }

        @ViewBuilder
        private var alreadyConfirmedView: some View {
            VStack(spacing: 8) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 28))
                    .foregroundStyle(.green)

                Text(speaker.fullName)
                    .font(.headline)
                    .multilineTextAlignment(.center)

                if let confirmedBy = speaker.arrivedConfirmedBy {
                    Text(
                        String(
                            format: NSLocalizedString("arrival.confirmed_by_format", comment: ""),
                            confirmedBy
                        )
                    )
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                }

                Button(NSLocalizedString("arrival.close", comment: "")) {
                    dismiss()
                }
                .buttonStyle(.plain)
                .font(.caption)
            }
        }

        @ViewBuilder
        private var confirmationPromptView: some View {
            VStack(spacing: 8) {
                Text(
                    String(
                        format: NSLocalizedString("arrival.confirm_question_format", comment: ""),
                        speaker.firstName
                    )
                )
                .font(.headline)
                .multilineTextAlignment(.center)

                // "Arrived ✓" button
                Button {
                    Task {
                        isConfirming = true
                        try? await arrivalTracker.confirmArrival(speaker: speaker)
                        isConfirming = false
                        dismiss()
                    }
                } label: {
                    Label(
                        NSLocalizedString("arrival.confirm_arrived", comment: ""),
                        systemImage: "checkmark"
                    )
                    .font(.caption)
                }
                .buttonStyle(.borderedProminent)
                .tint(.green)
                .disabled(isConfirming)

                // "Not yet" button
                Button(NSLocalizedString("arrival.not_yet", comment: "")) {
                    dismiss()
                }
                .buttonStyle(.plain)
                .font(.caption)
                .foregroundStyle(.secondary)
            }
        }
    }

    // ⚠️ [AI-Review][HIGH] MockAuthManager must exist (Task 1.5) before this Preview compiles.
    // ⚠️ [AI-Review][MEDIUM] @Environment(ArrivalTracker.self) binds to concrete type — SwiftUI
    // limitation; protocol injection via EnvironmentKey would require more boilerplate than it saves.
    // The Preview below is the canonical pattern; it will work once Task 1.5 is complete.
    #Preview {
        NavigationStack {
            SpeakerArrivalView()
        }
        .environment(ArrivalTracker(
            authManager: MockAuthManager(),
            modelContext: ModelContext(try! ModelContainer(for: CachedSpeaker.self))
        ))
        .environment(EventStateManager())
    }
    ```

- [x] **Task 4: Inject ArrivalTracker as Environment Object** (AC: #1, #4)
  - [ ] 4.1 Modify `apps/BATbern-watch/BATbern-watch Watch App/App/BATbernWatchApp.swift`
  - [ ] 4.2 Add `@State private var arrivalTracker: ArrivalTracker` initialized with `authManager` and model context
  - [ ] 4.3 Inject into `ContentView` via `.environment(arrivalTracker)`
  - [ ] 4.4 The `arrivalTracker` needs the ModelContext from the SwiftData container — initialize it in `BATbernWatchApp.init()` using `modelContainer.mainContext` (see Task 4.5 for the correct pattern)
    > ⚠️ [AI-Review][HIGH] Do NOT initialize via `@State var arrivalTracker: ArrivalTracker?` with a
    > nil-coalescing call in `body` — this mutates `@State` during body evaluation causing infinite re-render.
  - [ ] 4.5 **Correct pattern** — initialize `ArrivalTracker` in `BATbernWatchApp.swift`, pass `modelContainer.mainContext`:
    ```swift
    // BATbernWatchApp.swift — add alongside existing authManager + eventStateManager
    // ⚠️ [AI-Review][HIGH] Do NOT initialize in ContentView.body via ??/makeArrivalTracker().
    // Mutating @State inside body causes infinite re-render. Use the same direct @State
    // init pattern as authManager and eventStateManager.

    @State private var arrivalTracker: ArrivalTracker  // initialized in init()

    init() {
        // ... existing modelContainer setup ...
        let container = /* existing modelContainer init */
        modelContainer = container
        // ArrivalTracker needs a ModelContext — use the container's mainContext
        // Note: authManager must be passed; use a temporary and then connect in .task if needed.
        // Simplest: defer WebSocket; ArrivalTracker.startListening() called from SpeakerArrivalView.task
        _arrivalTracker = State(wrappedValue: ArrivalTracker(
            authManager: AuthManager(),   // will be overwritten — see note below
            modelContext: container.mainContext
        ))
    }
    ```
    > **Note:** Because `@State private var authManager = AuthManager()` is initialized before
    > `arrivalTracker`, pass the same instance. The cleanest approach is to pull `authManager`
    > init into the `init()` body too:
    ```swift
    // In init() — initialize both together so arrivalTracker gets the real authManager:
    let auth = AuthManager()
    _authManager = State(wrappedValue: auth)
    _arrivalTracker = State(wrappedValue: ArrivalTracker(
        authManager: auth,
        modelContext: container.mainContext
    ))
    ```
    Then in `WindowGroup.body`:
    ```swift
    ContentView()
        .environment(authManager)
        .environment(eventStateManager)
        .environment(arrivalTracker)   // ADD THIS LINE
    ```
    > No changes needed to `ContentView.swift` itself — `OrganizerZoneView` → `SpeakerArrivalView`
    > picks up `ArrivalTracker` from the environment automatically.

- [x] **Task 4b: Fix Xcode Preview injections** (no AC, build quality)
  - [ ] 4b.1 Update `#Preview` in `ContentView.swift` — add `.environment(ArrivalTracker(authManager: MockAuthManager(), modelContext: ...))` so the preview doesn't crash when `OrganizerZoneView → SpeakerArrivalView` attempts to resolve `ArrivalTracker` from environment
  - [ ] 4b.2 Update `#Preview` in `OrganizerZoneView.swift` — same injection needed
  > ⚠️ [AI-Review][LOW] Without these fixes, Xcode Previews for ContentView and OrganizerZoneView
  > will crash with "No value for type ArrivalTracker in environment" immediately after Task 4.

- [x] **Task 5: Backend — Flyway Migration** (AC: #3, #4)
  - [ ] 5.1 Create `services/event-management-service/src/main/resources/db/migration/V{next}__add_speaker_arrival_tracking.sql`
  - [ ] 5.2 Migration SQL (from architecture.md#Data-Architecture):
    ```sql
    -- V{next}__add_speaker_arrival_tracking.sql
    -- Epic 2, Story W2.4: Speaker arrival tracking persistence
    -- Unique constraint ensures idempotent arrival confirmations (no duplicates)
    CREATE TABLE speaker_arrivals (
        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        event_code VARCHAR(50) NOT NULL,
        speaker_username VARCHAR(100) NOT NULL,
        confirmed_by_username VARCHAR(100) NOT NULL,
        arrived_at TIMESTAMP NOT NULL DEFAULT NOW(),
        UNIQUE (event_code, speaker_username)  -- Idempotent: one arrival per speaker per event
    );
    CREATE INDEX idx_speaker_arrivals_event ON speaker_arrivals(event_code);
    ```
  - [ ] 5.3 Run via `./gradlew :services:event-management-service:flywayMigrate` from repo root

- [x] **Task 6: Backend — SpeakerArrival Entity** (AC: #3, #4)
  - [ ] 6.1 Create `services/event-management-service/src/main/java/ch/batbern/events/watch/domain/SpeakerArrival.java`
    ```java
    package ch.batbern.events.watch.domain;

    import jakarta.persistence.*;
    import java.time.Instant;
    import java.util.UUID;

    @Entity
    @Table(name = "speaker_arrivals",
           uniqueConstraints = @UniqueConstraint(columnNames = {"event_code", "speaker_username"}))
    public class SpeakerArrival {

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @Column(name = "event_code", nullable = false, length = 50)
        private String eventCode;

        @Column(name = "speaker_username", nullable = false, length = 100)
        private String speakerUsername;

        @Column(name = "confirmed_by_username", nullable = false, length = 100)
        private String confirmedByUsername;

        @Column(name = "arrived_at", nullable = false)
        private Instant arrivedAt;

        // Getters, setters, constructors
        public SpeakerArrival() {}

        public SpeakerArrival(String eventCode, String speakerUsername, String confirmedBy) {
            this.eventCode = eventCode;
            this.speakerUsername = speakerUsername;
            this.confirmedByUsername = confirmedBy;
            this.arrivedAt = Instant.now();
        }

        // ... standard getters/setters
    }
    ```

- [x] **Task 7: Backend — WatchSpeakerArrivalService** (AC: #3, #4, #5)
  - [ ] 7.1 Create `services/event-management-service/src/main/java/ch/batbern/events/watch/WatchSpeakerArrivalService.java`
  - [ ] 7.2 Create `SpeakerArrivalRepository.java` (Spring Data JPA)
  - [ ] 7.3 Service implementation:
    ```java
    @Service
    public class WatchSpeakerArrivalService {

        private final SpeakerArrivalRepository arrivalRepository;
        private final SimpMessagingTemplate messagingTemplate;
        private final UserRepository userRepository;  // For speaker name lookup

        // GET: Return all arrivals for an event
        public List<ArrivalStatusDto> getArrivals(String eventCode) {
            return arrivalRepository.findByEventCode(eventCode)
                .stream()
                .map(a -> new ArrivalStatusDto(
                    a.getSpeakerUsername(),
                    a.getConfirmedByUsername(),
                    a.getArrivedAt().toString()
                ))
                .collect(Collectors.toList());
        }

        // POST (REST fallback) / STOMP action handler:
        // ⚠️ [AI-Review][MEDIUM] exists-check + save is NOT atomic — two concurrent confirms
        // both pass the exists-check and then both attempt save → DataIntegrityViolationException.
        // Use @Transactional + catch the exception to make this truly idempotent.
        @Transactional
        public SpeakerArrivalBroadcast confirmArrival(
            String eventCode,
            String speakerUsername,
            String confirmedBy
        ) {
            // Idempotent: catch DataIntegrityViolationException from UNIQUE(event_code, speaker_username)
            try {
                if (!arrivalRepository.existsByEventCodeAndSpeakerUsername(eventCode, speakerUsername)) {
                    arrivalRepository.save(new SpeakerArrival(eventCode, speakerUsername, confirmedBy));
                }
            } catch (DataIntegrityViolationException e) {
                // Concurrent confirmation — already inserted by another organizer. Safe to ignore.
                log.debug("Concurrent arrival confirmation for {}/{} — already recorded", eventCode, speakerUsername);
            }

            // Count for broadcast
            long totalSpeakers = getTotalSpeakerCount(eventCode);
            long arrivedCount = arrivalRepository.countByEventCode(eventCode);

            // Lookup speaker name for broadcast
            User speaker = userRepository.findByUsername(speakerUsername).orElseThrow();

            SpeakerArrivalBroadcast broadcast = new SpeakerArrivalBroadcast(
                "SPEAKER_ARRIVED",
                eventCode,
                speakerUsername,
                speaker.getFirstName(),
                speaker.getLastName(),
                confirmedBy,
                Instant.now().toString(),
                new ArrivalCount((int) arrivedCount, (int) totalSpeakers)
            );

            // Broadcast to all watches subscribed to arrivals topic (FR38: <3 seconds)
            messagingTemplate.convertAndSend(
                "/topic/events/" + eventCode + "/arrivals",
                broadcast
            );

            return broadcast;
        }
    }
    ```

- [x] **Task 8: Backend — REST Controller Endpoints** (AC: #3, #4)
  - [ ] 8.1 Add to existing `WatchRestController.java` (or create new if not yet exists):
    ```java
    // GET /api/v1/watch/events/{eventCode}/arrivals
    @GetMapping("/events/{eventCode}/arrivals")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ArrivalStatusListDto> getArrivals(
        @PathVariable String eventCode,
        @AuthenticationPrincipal UserDetails user
    ) {
        List<ArrivalStatusDto> arrivals = arrivalService.getArrivals(eventCode);
        return ResponseEntity.ok(new ArrivalStatusListDto(arrivals));
    }

    // POST /api/v1/watch/events/{eventCode}/arrivals (REST fallback, WebSocket offline)
    @PostMapping("/events/{eventCode}/arrivals")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SpeakerArrivalBroadcast> confirmArrival(
        @PathVariable String eventCode,
        @RequestBody ConfirmArrivalRequest request,
        @AuthenticationPrincipal UserDetails user
    ) {
        SpeakerArrivalBroadcast result = arrivalService.confirmArrival(
            eventCode,
            request.speakerUsername(),
            user.getUsername()
        );
        return ResponseEntity.status(201).body(result);
    }
    ```

  - [ ] 8.2 Create DTO records:
    ```java
    record ConfirmArrivalRequest(String speakerUsername) {}
    record ArrivalStatusDto(String speakerUsername, String confirmedBy, String arrivedAt) {}
    record ArrivalStatusListDto(List<ArrivalStatusDto> arrivals) {}
    record ArrivalCount(int arrived, int total) {}
    record SpeakerArrivalBroadcast(
        String type, String eventCode,
        String speakerUsername, String speakerFirstName, String speakerLastName,
        String confirmedBy, String arrivedAt, ArrivalCount arrivalCount
    ) {}
    ```

- [x] **Task 9: Backend — WebSocket STOMP Handler** (AC: #3, #4)
  - [ ] 9.1 Add STOMP message handler in `WatchWebSocketController.java` (or equivalent):
    ```java
    // Handles: /app/watch/events/{eventCode}/speaker-arrived
    @MessageMapping("/watch/events/{eventCode}/speaker-arrived")
    public void handleSpeakerArrived(
        @DestinationVariable String eventCode,
        @Payload SpeakerArrivedAction action,
        Principal principal
    ) {
        arrivalService.confirmArrival(
            eventCode,
            action.speakerUsername(),
            principal.getName()
        );
        // arrivalService.confirmArrival broadcasts to /topic/events/{eventCode}/arrivals
    }
    ```

  - [ ] 9.2 `SpeakerArrivedAction` record:
    ```java
    record SpeakerArrivedAction(String speakerUsername) {}
    ```

- [x] **Task 10: Localization** (AC: all)
  - [ ] 10.1 Add to `apps/BATbern-watch/BATbern-watch Watch App/Base.lproj/Localizable.strings`:
    ```
    "arrival.tonight_speakers" = "Tonight's Speakers";
    "arrival.counter_format" = "%d of %d arrived";
    "arrival.confirm_question_format" = "Has %@ arrived?";
    "arrival.confirm_arrived" = "Arrived ✓";
    "arrival.not_yet" = "Not yet";
    "arrival.confirmed_by_format" = "Confirmed by %@";
    "arrival.close" = "Close";
    "arrival.event_starts_at" = "Event starts at";
    "arrival.error.not_authenticated" = "Not authenticated. Please pair your Watch.";
    "arrival.error.no_event" = "No active event.";
    "arrival.error.server" = "Server error confirming arrival.";
    ```
  - [ ] 10.2 Add to `apps/BATbern-watch/BATbern-watch Watch App/de.lproj/Localizable.strings`:
    ```
    "arrival.tonight_speakers" = "Heutige Referenten";
    "arrival.counter_format" = "%d von %d angekommen";
    "arrival.confirm_question_format" = "Ist %@ angekommen?";
    "arrival.confirm_arrived" = "Angekommen ✓";
    "arrival.not_yet" = "Noch nicht";
    "arrival.confirmed_by_format" = "Bestätigt von %@";
    "arrival.close" = "Schließen";
    "arrival.event_starts_at" = "Event beginnt um";
    "arrival.error.not_authenticated" = "Nicht authentifiziert. Bitte Watch koppeln.";
    "arrival.error.no_event" = "Kein aktives Event.";
    "arrival.error.server" = "Serverfehler beim Bestätigen der Ankunft.";
    ```

- [x] **Task 11: Unit Tests — ArrivalTracker** (AC: #1, #3, #4, #5)
  - [ ] 11.1 Create `apps/BATbern-watch/BATbern-watch Watch AppTests/Domain/ArrivalTrackerTests.swift` (Swift Testing framework)
  - [ ] 11.2 Test: `confirmArrival_updatesLocalStateOptimistically()`
    - Mock: `MockAuthManager` with `organizerUsername = "marco"`
    - Mock: `MockWebSocketClient` that records sent actions
    - Action: Call `arrivalTracker.confirmArrival(speaker: speaker)` where `speaker.arrived = false`
    - Assert: `speaker.arrived == true`
    - Assert: `speaker.arrivedConfirmedBy == "marco"`
    - Assert: `MockWebSocketClient.sentActions.contains(.speakerArrived(speakerUsername: speaker.username))`
  - [ ] 11.3 Test: `processArrivalMessage_updatesCounter()`
    - Setup: 3 speakers in SwiftData, none arrived
    - Simulate WebSocket message with `arrivedCount: 2, totalCount: 3`
    - Assert: `arrivalTracker.arrivedCount == 2`
    - Assert: `arrivalTracker.totalCount == 3`
  - [ ] 11.4 Test: `confirmArrival_isIdempotent()`
    - Setup: Speaker already marked `arrived = true` locally
    - Action: Call `confirmArrival` again
    - Assert: No error thrown, speaker state unchanged
    - Assert: WebSocket sends action (server handles idempotency via UNIQUE constraint)
  - [ ] 11.5 Test: `fetchInitialArrivals_appliesStateToCachedSpeakers()`
    - Mock URLSession to return `{"arrivals": [{"speakerUsername": "anna", "confirmedBy": "marco", "arrivedAt": "2026-02-16T17:15:00Z"}]}`
    - Call `startListening(eventCode: "BATbern56")`
    - Assert: Speaker "anna" in SwiftData has `arrived == true`, `arrivedConfirmedBy == "marco"`
  - [ ] 11.6 Test: `usesRESTFallback_whenWebSocketDisconnected()`
    - Mock: `MockWebSocketClient.isConnected = false`
    - Mock URLSession to accept POST and return 201
    - Call `confirmArrival(speaker:)`
    - Assert: URLSession received POST to `/api/v1/watch/events/{eventCode}/arrivals`
    - Assert: No WebSocket action sent

- [x] **Task 12: Backend Integration Tests** (AC: #3, #4, #5)
  - [ ] 12.1 Create `services/event-management-service/src/test/java/ch/batbern/events/watch/WatchSpeakerArrivalIntegrationTest.java`
    (extends `AbstractIntegrationTest` — real PostgreSQL via Testcontainers)
  - [ ] 12.2 Test: `shouldReturnEmptyArrivals_whenNoneConfirmed()`
    - GET `/api/v1/watch/events/{eventCode}/arrivals` with valid organizer JWT
    - Assert: 200 OK, `arrivals` array is empty
  - [ ] 12.3 Test: `shouldConfirmArrival_andReturnBroadcast()`
    - POST `/api/v1/watch/events/{eventCode}/arrivals` with `{"speakerUsername": "anna.meier"}`
    - Assert: 201 Created
    - Assert: Response contains `type: "SPEAKER_ARRIVED"`, `speakerUsername: "anna.meier"`
    - Assert: `arrivalCount.arrived == 1`
  - [ ] 12.4 Test: `shouldBeIdempotent_whenConfirmingSameArrivalTwice()`
    - POST arrival for "anna.meier" twice
    - Assert: Both return 201 (no 409 Conflict)
    - Assert: GET returns only 1 arrival for "anna.meier"
  - [ ] 12.5 Test: `shouldReturn401_whenUnauthenticated()`
    - GET/POST without JWT header
    - Assert: 401 Unauthorized
  - [ ] 12.6 Test: `shouldReturn403_whenNotOrganizer()`
    - GET with attendee JWT (not ROLE_ORGANIZER)
    - Assert: 403 Forbidden
  - [ ] 12.7 Test: `shouldBroadcastToArrivalsTopicViaWebSocket()`
    > ⚠️ [AI-Review][MEDIUM] STOMP integration tests require explicit infrastructure — do NOT skip.
    > Use Spring's `WebSocketStompClient` with `SockJsClient` and a `CompletableFuture`-based handler:
    ```java
    @Test
    void shouldBroadcastToArrivalsTopicViaWebSocket() throws Exception {
        // Requires spring-boot-test + WebSocketStompClient on the test classpath
        WebSocketStompClient stompClient = new WebSocketStompClient(
            new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient())))
        );
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        String url = "ws://localhost:" + port + "/ws";
        CompletableFuture<SpeakerArrivalBroadcast> receivedFuture = new CompletableFuture<>();

        StompSession session = stompClient.connectAsync(url, new StompSessionHandlerAdapter() {}).get(5, SECONDS);
        session.subscribe("/topic/events/" + eventCode + "/arrivals",
            new StompFrameHandler() {
                public Type getPayloadType(StompHeaders h) { return SpeakerArrivalBroadcast.class; }
                public void handleFrame(StompHeaders h, Object payload) {
                    receivedFuture.complete((SpeakerArrivalBroadcast) payload);
                }
            });

        // POST arrival via REST to trigger broadcast
        mockMvc.perform(post("/api/v1/watch/events/{eventCode}/arrivals", eventCode)
            .header("Authorization", "Bearer " + organizerJwt)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"speakerUsername\":\"anna.meier\"}"))
            .andExpect(status().isCreated());

        SpeakerArrivalBroadcast broadcast = receivedFuture.get(3, SECONDS);
        assertThat(broadcast.type()).isEqualTo("SPEAKER_ARRIVED");
        assertThat(broadcast.speakerUsername()).isEqualTo("anna.meier");
        assertThat(broadcast.arrivalCount().arrived()).isEqualTo(1);
        session.disconnect();
    }
    ```
    - Class must be annotated with `@SpringBootTest(webEnvironment = RANDOM_PORT)` (not `MOCK`)
    - Inject `@LocalServerPort int port` for the STOMP URL

- [x] **Task 13: Update OpenAPI Specification** (AC: #3, #4)
  - [ ] 13.1 Add to `docs/api/event-management-api.openapi.yml`:
    - `GET /api/v1/watch/events/{eventCode}/arrivals` — Returns arrival status for all speakers
    - `POST /api/v1/watch/events/{eventCode}/arrivals` — Confirms speaker arrival (REST fallback)
  - [ ] 13.2 Define schemas: `ArrivalStatusList`, `ArrivalStatus`, `ConfirmArrivalRequest`, `SpeakerArrivalBroadcast`

## Dev Notes

### Story Context & Epic Breakdown

**Epic 2: Watch Pairing & Organizer Access**

W2.4 is the final story of Epic 2. It implements the O2 organizer screen (Speaker Arrival Overview), completing the pre-event coordinator workflow. This story integrates WebSocket real-time sync for the first time in Epic 2.

**Epic 2 Story Sequence:**
- **W2.1 (DONE)**: Pairing Code Backend & Web Frontend — Generate 6-digit codes on web
- **W2.2 (DONE)**: Watch Pairing Flow & Organizer Zone Navigation — Enter code on Watch, dual-zone routing
- **W2.3 (READY-FOR-DEV)**: Event Join & Schedule Sync — Sync full schedule with portraits to Watch
- **W2.4 (THIS STORY)**: Speaker Arrival Tracking — Portrait grid with tap-to-confirm, real-time sync

### What's Already Built (W2.1-W2.3 Foundations)

| Component | File | W2.4 Usage |
|---|---|---|
| `CachedSpeaker` | `Models/CachedSpeaker.swift` | Has `arrived`, `arrivedConfirmedBy`, `arrivedAt` fields — already defined! |
| `AuthManager` | `Data/AuthManager.swift` | Provides `currentJWT` and `organizerUsername` |
| `EventStateManager` | `Domain/EventStateManager.swift` | Routes to O2 when `isPreEvent == true` |
| `OrganizerZoneView` | `Views/OrganizerZoneView.swift` | Already calls `SpeakerArrivalView()` — routing is complete |
| `SpeakerPortraitView` | `Views/Shared/SpeakerPortraitView.swift` | Reuse for portrait cells |
| `PortraitCache` | `Data/PortraitCache.swift` | Portraits already downloaded by W2.3 EventSyncService |
| `WebSocketClientProtocol` | `Protocols/WebSocketClientProtocol.swift` | Has `sendAction(.speakerArrived)` — extend with `arrivalUpdates()` |
| `WatchAction.speakerArrived` | `Protocols/WebSocketClientProtocol.swift` | Action enum already has the case |

**Critical**: `CachedSpeaker.arrived`, `CachedSpeaker.arrivedConfirmedBy`, `CachedSpeaker.arrivedAt` are already defined in `Models/CachedSpeaker.swift` (added in W2.3 story, confirmed by reading file). Do NOT redefine these fields.

### Architecture Constraints

**WebSocket Topics:**
- **Subscribe**: `/topic/events/{eventCode}/arrivals` — lightweight arrival-only broadcasts (separate from state topic)
- **Send**: `/app/watch/events/{eventCode}/speaker-arrived` — action for confirming arrival
- Real-time requirement: sync to all watches within 3 seconds (FR38, NFR3)

**REST Fallback Pattern:**
- `GET /api/v1/watch/events/{eventCode}/arrivals` — Initial load on app open (if WebSocket not yet connected)
- `POST /api/v1/watch/events/{eventCode}/arrivals` — Offline fallback when WebSocket disconnected
- Backend is idempotent: `UNIQUE (event_code, speaker_username)` constraint in DB
- `SPEAKER_ARRIVED` is idempotent: confirming an already-arrived speaker is a no-op

**ArrivalTracker Placement:**
- Located in `Domain/` (pure business logic with DI)
- Injected as environment object (same pattern as `AuthManager`, `EventStateManager`)
- Uses `ClockProtocol` is NOT needed (time comes from server, not local clock)

**Database Constraint:**
```sql
UNIQUE (event_code, speaker_username)
```
This means `SpeakerArrivalRepository.save()` with duplicate (eventCode, speakerUsername) will throw `DataIntegrityViolationException` — handle in service layer with exists-check or catch the exception.

### O2 Screen UX (from ux-design-specification.md#Speaker-Portrait-Overview-(O2))

```
┌──────────────────────┐
│  Tonight's Speakers  │  ← Navigation title (localized)
│  2 of 4 arrived      │  ← Arrival counter (synced in real-time)
│                       │
│  ┌────┐  ┌────┐      │
│  │ 📷 │  │ 📷 │      │
│  │Anna│  │Tom │      │
│  │ ✓  │  │    │      │  ← Green ✓ = confirmed arrived
│  └────┘  └────┘      │
│  ┌────┐  ┌────┐      │
│  │ 📷 │  │ 📷 │      │
│  │Lisa│  │Marc│      │
│  │ ✓  │  │    │      │
│  └────┘  └────┘      │
│                       │
│  Event starts at 18:00│  ← Using SwissDateFormatter (24h)
└──────────────────────┘
```

**Tap behavior:**
- Not arrived → Sheet: "Has [FirstName] arrived?" + "Arrived ✓" + "Not yet"
- Already arrived → Sheet: "[Name] — Checkmark icon — Confirmed by [username]" + "Close"

### Backend Service Boundary

Speaker arrival tracking is in `event-management-service` (EMS) — NOT company-user-management-service (CUMS):
- EMS owns session + event data and WebSocket connections
- EMS has access to `speaker_arrivals` table and STOMP messaging
- CUMS only handles pairing tokens and user authentication

See architecture.md line 810: `WatchSpeakerArrivalService.java` belongs to `event-management-service`

### Previous Story Learnings

**From W2.2 (Watch Pairing Flow):**
- `AuthManager.organizerUsername` is the confirmed organizer identifier for "confirmedBy" tracking
- Protocol-based DI pattern: every dependency has a protocol (`AuthManagerProtocol`, `WebSocketClientProtocol`)
- Environment objects injected in `BATbernWatchApp.swift` and passed via `.environment()`

**From W2.3 (Event Join & Schedule Sync):**
- Speakers are already synced to SwiftData via `EventSyncService.syncActiveEvent()`
- Portrait images already cached in file system via `PortraitCache`
- `EventStateManager.currentEvent?.eventCode` provides the event code needed for API calls
- SwiftData `@Query` in SwiftUI views auto-updates when underlying data changes

**From W1.x (Public Zone):**
- `SpeakerPortraitView` reused for portrait cells (already handles nil portrait gracefully)
- `SwissDateFormatter.formatTimeString()` for 24h event time display
- All user-facing text uses `NSLocalizedString("key", comment: "")` with German primary

### Testing Strategy

**Swift Testing Unit Tests (ArrivalTrackerTests.swift):**
- Use `MockAuthManager` and `MockWebSocketClient` from `BATbern-watch Watch AppTests/Mocks/`
- In-memory SwiftData context for testing (not file-backed)
- Test optimistic update, WebSocket message processing, REST fallback, idempotency

**Backend Integration Tests (WatchSpeakerArrivalIntegrationTest.java):**
- Extends `AbstractIntegrationTest` (PostgreSQL via Testcontainers — NEVER H2)
- Tests for 200/201/401/403 responses
- Idempotency test (double-confirm same speaker)
- WebSocket broadcast test via mock STOMP subscriber

### Files to Create

**New Files (watchOS):**
- `apps/BATbern-watch/BATbern-watch Watch App/Domain/ArrivalTracker.swift`
- `apps/BATbern-watch/BATbern-watch Watch AppTests/Domain/ArrivalTrackerTests.swift`

**Modified Files (watchOS):**
- `apps/BATbern-watch/BATbern-watch Watch App/Protocols/WebSocketClientProtocol.swift` — Add `SpeakerArrivalMessage` + `arrivalUpdates()`
- `apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/SpeakerArrivalView.swift` — Replace placeholder with full implementation
- `apps/BATbern-watch/BATbern-watch Watch App/App/ContentView.swift` — Add ArrivalTracker as environment (or BATbernWatchApp.swift)
- `apps/BATbern-watch/BATbern-watch Watch App/Base.lproj/Localizable.strings` — Add arrival strings
- `apps/BATbern-watch/BATbern-watch Watch App/de.lproj/Localizable.strings` — Add German arrival strings

**New Files (Backend — event-management-service):**
- `services/event-management-service/src/main/resources/db/migration/V{next}__add_speaker_arrival_tracking.sql`
- `services/event-management-service/src/main/java/ch/batbern/events/watch/domain/SpeakerArrival.java`
- `services/event-management-service/src/main/java/ch/batbern/events/watch/WatchSpeakerArrivalService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/watch/dto/SpeakerArrivalBroadcast.java`
- `services/event-management-service/src/main/java/ch/batbern/events/watch/SpeakerArrivalRepository.java`
- `services/event-management-service/src/test/java/ch/batbern/events/watch/WatchSpeakerArrivalIntegrationTest.java`

**New Files (Backend, added by code review — Task 12.7):**
- `services/event-management-service/src/test/java/ch/batbern/events/watch/WatchSpeakerArrivalWebSocketTest.java`

**Modified Files (Backend):**
- `services/event-management-service/src/main/java/ch/batbern/events/watch/WatchEventController.java` — Added GET/POST arrivals endpoints
- `services/event-management-service/src/main/java/ch/batbern/events/watch/WatchWebSocketController.java` — Added STOMP handler for `speaker-arrived`
- `services/event-management-service/src/main/java/ch/batbern/events/repository/SessionRepository.java` — Added `countDistinctSpeakersByEventCode` JPQL query (N+1 fix)
- `docs/api/event-management-api.openapi.yml` — Add arrivals endpoints

**⚠️ Uncommitted changes NOT in this story (W2.3 post-review fixes — commit separately):**
- `apps/BATbern-watch/BATbern-watch Watch App/Data/EventSyncService.swift`
- `apps/BATbern-watch/BATbern-watch Watch App/Data/PortraitCache.swift`
- `apps/BATbern-watch/BATbern-watch Watch AppTests/Data/EventSyncServiceTests.swift`

### Project Structure After W2.4

```
apps/BATbern-watch/BATbern-watch Watch App/
├── Domain/
│   ├── EventStateManager.swift      (W2.2 — routes to O2)
│   ├── HapticScheduler.swift        (W3)
│   ├── SessionTimerEngine.swift     (W3)
│   └── ArrivalTracker.swift         NEW: Speaker arrival state management
├── Protocols/
│   └── WebSocketClientProtocol.swift  MODIFIED: + SpeakerArrivalMessage, arrivalUpdates()
└── Views/
    └── Organizer/
        └── SpeakerArrivalView.swift   REPLACED: Full portrait grid implementation

services/event-management-service/
└── src/main/java/ch/batbern/events/watch/
    ├── domain/
    │   └── SpeakerArrival.java        NEW: JPA entity
    ├── WatchSpeakerArrivalService.java  NEW: Arrival tracking service
    ├── SpeakerArrivalRepository.java    NEW: Spring Data JPA repo
    └── dto/
        └── SpeakerArrivalBroadcast.java  NEW: WebSocket broadcast DTO
```

### References

- [Source: docs/watch-app/epics.md#W2.4] — User story and acceptance criteria
- [Source: docs/watch-app/architecture.md#FR36-39] — Speaker arrival tracking architectural decisions
- [Source: docs/watch-app/architecture.md#ArrivalTracker] — Domain layer placement at line 603
- [Source: docs/watch-app/architecture.md#WebSocket-STOMP] — `/topic/events/{eventCode}/arrivals` topic, SPEAKER_ARRIVED message schema
- [Source: docs/watch-app/architecture.md#Data-Architecture] — `speaker_arrivals` table schema (lines 194-203)
- [Source: docs/watch-app/architecture.md#Backend-REST-Endpoints] — `GET/POST /api/v1/watch/events/{eventCode}/arrivals`
- [Source: docs/watch-app/ux-design-specification.md#Speaker-Portrait-Overview-(O2)] — Portrait grid UI design
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Models/CachedSpeaker.swift] — `arrived`, `arrivedConfirmedBy`, `arrivedAt` fields already defined
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Protocols/WebSocketClientProtocol.swift] — Existing protocol + `WatchAction.speakerArrived` already defined
- [Source: _bmad-output/implementation-artifacts/w2-3-event-join-schedule-sync.md] — Previous story: EventSyncService + PortraitCache patterns
- [Source: docs/watch-app/prd-batbern-watch.md#FR36-FR39] — Functional requirements for speaker arrival

## Dev Agent Record

### Agent Model Used

Claude Sonnet 4.5 (claude-sonnet-4-5-20250929)

### Debug Log References

N/A — Story file created by SM agent (Bob) in YOLO mode

### Completion Notes List

**AI Code Review (2026-02-17) — Pre-implementation adversarial review by Amelia (Dev Agent):**

Reviewed story design against existing codebase before implementation. Fixed 4 HIGH + 6 MEDIUM + 2 LOW issues:

| ID | Severity | Fix Applied |
|---|---|---|
| H1 | HIGH | Added Task 1.5: Create `MockAuthManager.swift` — missing mock needed by all Task 11 tests |
| H2 | HIGH | Expanded Task 1.4: Added `arrivalUpdatesContinuation` + `emitArrival()` to `MockWebSocketClient` |
| H3 | HIGH | Fixed `ArrivalConfirmationView.arrivalTracker` type: `ArrivalTracker` → `any ArrivalTrackerProtocol` |
| H4 | HIGH | Replaced broken `makeArrivalTracker()` in ContentView.body with `BATbernWatchApp.init()` pattern |
| M1 | MEDIUM | Added clarifying comment to `recomputeCounter()` — no event filter possible; server counts authoritative |
| M2 | MEDIUM | Added `@Transactional` + `catch DataIntegrityViolationException` to `confirmArrival()` |
| M3 | MEDIUM | Added Task 1.6: Remove `EventStateMessageType.speakerArrived` to avoid dual-stream ambiguity |
| M4 | MEDIUM | Added `private static let iso8601Formatter` to `ArrivalTracker` with `.withFractionalSeconds` |
| M5 | MEDIUM | Added concrete `WebSocketStompClient`-based implementation to Task 12.7 |
| M6 | MEDIUM | Documented `@Environment(ArrivalTracker.self)` SwiftUI limitation in Preview comment |
| L1 | LOW | Added Task 4b: Fix ContentView + OrganizerZoneView Xcode Preview injections |
| L2 | LOW | No change needed — German encoding is correct |

**Story Preparation Summary:**
- Speaker Arrival Tracking: O2 screen with real-time WebSocket sync across organizer watches
- `CachedSpeaker` model already has `arrived`/`arrivedConfirmedBy`/`arrivedAt` fields — no schema change needed on watchOS side
- `WatchAction.speakerArrived` already defined in `WebSocketClientProtocol.swift` — only need to add `arrivalUpdates()` + `SpeakerArrivalMessage`
- `OrganizerZoneView` already routes to `SpeakerArrivalView()` when `isPreEvent == true` — navigation is complete
- `ArrivalTracker` uses optimistic UI (local update first, then sync) for responsive feel
- Backend is idempotent via `UNIQUE (event_code, speaker_username)` constraint
- Separate `/topic/events/{eventCode}/arrivals` WebSocket topic for lightweight arrival-only broadcasts
- REST fallback path for offline/reconnecting scenarios

**Architecture Decisions Applied:**
- `ArrivalTracker` placed in `Domain/` (pure business logic) with protocol for testability
- Injected as environment object matching W2.2 pattern (`AuthManager`, `EventStateManager`)
- `arrivalUpdates()` added to `WebSocketClientProtocol` for separate arrivals topic subscription
- Swiss German primary locale — all strings in German (de.lproj)

---

**Pre-implementation Code Quality Review (2026-02-17) — Amelia (Dev Agent):**

Reviewed existing codebase dependencies before W2.4 implementation. Found and auto-fixed 4 HIGH + 2 MEDIUM bugs in the Public zone code that W2.4 builds on:

| ID | Severity | File | Fix Applied |
|---|---|---|---|
| H1 | HIGH | `PublicViewModel.swift:156` | `isOffline = false` cleared before network request — moved inside success path |
| H2 | HIGH | `ConnectionStatusBar.swift:49` | Text/icon used `.secondary` foreground on dark capsule — changed to `.white` for readability |
| H3 | HIGH | `SessionListView.swift:39` | `.safeAreaInset` ignored by `TabView(.verticalPage)` — fixed via `statusBarVisible` flag to `SessionCardView` |
| H4 | HIGH | `SpeakerBioView.swift:47` | Company logo + name both shown simultaneously — show logo OR text (not both) |
| M1 | MEDIUM | `SpeakerPortraitView.swift:50` | Same logo+text double-render in portrait card — `if logoData == nil, let company =` |
| M2 | MEDIUM | `SessionCardView.swift:36` | Hardcoded `padding(.top, 28)` ignores connection bar — computed `timeSlotTopPadding` property |

### File List

**Story File:**
- `_bmad-output/implementation-artifacts/w2-4-speaker-arrival-tracking.md`

**Pre-implementation Code Quality Fixes (existing files):**
- `apps/BATbern-watch/BATbern-watch Watch App/ViewModels/PublicViewModel.swift` — fix `isOffline` premature reset (H1)
- `apps/BATbern-watch/BATbern-watch Watch App/Views/Shared/ConnectionStatusBar.swift` — fix text visibility (H2)
- `apps/BATbern-watch/BATbern-watch Watch App/Views/Public/SessionListView.swift` — fix StatusBar+TabView overlap (H3)
- `apps/BATbern-watch/BATbern-watch Watch App/Views/Public/SpeakerBioView.swift` — fix logo+text double-render (H4)
- `apps/BATbern-watch/BATbern-watch Watch App/Views/Shared/SpeakerPortraitView.swift` — fix logo+text double-render (M1)
- `apps/BATbern-watch/BATbern-watch Watch App/Views/Public/SessionCardView.swift` — fix top padding when status bar visible (M2)

---

**Post-implementation Code Review (2026-02-17) — Amelia (Dev Agent):**

Adversarial review of committed implementation. Fixed 2 HIGH + 4 MEDIUM issues. Story promoted to `done`.

| ID | Severity | File | Fix Applied |
|---|---|---|---|
| H1 | HIGH | `WatchSpeakerArrivalWebSocketTest.java` | Created missing STOMP broadcast test (Task 12.7 was deferred with non-existent file reference) |
| H2 | HIGH | N/A (process) | Documented uncommitted W2.3 post-review fixes — must be committed separately from W2.4 |
| M1 | MEDIUM | `ArrivalTracker.swift:96` | `try? modelContext.save()` → `try` in `confirmArrival()` (throws context) |
| M1b | MEDIUM | `ArrivalTracker.swift:174` | `try? modelContext.save()` → do-catch with `logger.warning()` in `processArrivalMessage()` |
| M2 | MEDIUM | `SessionRepository.java` + `WatchSpeakerArrivalService.java` | N+1 in `getTotalSpeakerCount()` — added `countDistinctSpeakersByEventCode` JPQL; replaced N-query stream with single DB call |
| M3 | MEDIUM | Story File List | `WatchRestController.java` corrected to `WatchEventController.java` (wrong filename in story) |
| M4 | MEDIUM | `ArrivalTracker.swift:48` | `var currentEventCode` → `private var` (removed unnecessary internal visibility) |

**Code Review Files Changed:**
- `apps/BATbern-watch/BATbern-watch Watch App/Domain/ArrivalTracker.swift`
- `services/event-management-service/src/main/java/ch/batbern/events/repository/SessionRepository.java`
- `services/event-management-service/src/main/java/ch/batbern/events/watch/WatchSpeakerArrivalService.java`
- `services/event-management-service/src/test/java/ch/batbern/events/watch/WatchSpeakerArrivalWebSocketTest.java` (NEW)
