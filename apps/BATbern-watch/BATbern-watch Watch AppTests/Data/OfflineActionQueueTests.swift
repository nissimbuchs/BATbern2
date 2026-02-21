//
//  OfflineActionQueueTests.swift
//  BATbern-watch Watch AppTests
//
//  Unit tests for OfflineActionQueue persistence contract.
//  Story W5.2 Task 6.1 (AC#2: survives model context reload, #3: ordered replay).
//

import Testing
import Foundation
import SwiftData
@testable import BATbern_watch_Watch_App

@Suite("OfflineActionQueue", .serialized)
@MainActor
struct OfflineActionQueueTests {

    // MARK: - Helpers

    private func makeQueue() throws -> (OfflineActionQueue, ModelContext) {
        let schema = Schema([OfflineAction.self])
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        let container = try ModelContainer(for: schema, configurations: [config])
        let context = ModelContext(container)
        let queue = OfflineActionQueue(modelContext: context)
        return (queue, context)
    }

    // MARK: - enqueue / persist / pendingActions

    @Test("enqueue: persists action to SwiftData context")
    func enqueue_persistsAction() throws {
        let (queue, context) = try makeQueue()

        queue.enqueue(.endSession(sessionSlug: "cloud-talk"))

        let stored = try context.fetch(FetchDescriptor<OfflineAction>())
        #expect(stored.count == 1)
        #expect(stored[0].actionType == "END_SESSION")
        #expect(!stored[0].payload.isEmpty)
    }

    @Test("enqueue + pendingActions: action survives model context reload (NFR11)")
    func enqueue_survivesContextReload() throws {
        let schema = Schema([OfflineAction.self])
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        let container = try ModelContainer(for: schema, configurations: [config])

        // Enqueue in one context
        let contextA = ModelContext(container)
        let queueA = OfflineActionQueue(modelContext: contextA)
        queueA.enqueue(.extendSession(sessionSlug: "talk-2", minutes: 5))

        // Read from a fresh context on the same container — simulates app restart with same store
        let contextB = ModelContext(container)
        let queueB = OfflineActionQueue(modelContext: contextB)
        let pending = queueB.pendingActions()

        #expect(pending.count == 1)
        #expect(pending[0].actionType == "EXTEND_SESSION")
    }

    @Test("pendingActions: returns in chronological order (oldest first)")
    func pendingActions_chronologicalOrder() throws {
        let (queue, _) = try makeQueue()

        let baseTime = Date()
        // Insert newer action first to verify ordering is by enqueuedAt, not insert order
        let dto1 = WatchActionDto(from: .endSession(sessionSlug: "session-A"))
        let payload1 = try #require(try? JSONEncoder().encode(dto1))
        let newer = OfflineAction(
            actionType: "END_SESSION",
            payload: payload1,
            enqueuedAt: baseTime.addingTimeInterval(5)
        )

        let dto2 = WatchActionDto(from: .startSession(sessionSlug: "session-B"))
        let payload2 = try #require(try? JSONEncoder().encode(dto2))
        let older = OfflineAction(
            actionType: "START_SESSION",
            payload: payload2,
            enqueuedAt: baseTime
        )

        // Insert newer first, then older — queue should sort by enqueuedAt ascending
        let schema = Schema([OfflineAction.self])
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        let container = try ModelContainer(for: schema, configurations: [config])
        let context = ModelContext(container)
        context.insert(newer)
        context.insert(older)
        try context.save()

        let queueFromContext = OfflineActionQueue(modelContext: context)
        let pending = queueFromContext.pendingActions()

        #expect(pending.count == 2)
        #expect(pending[0].actionType == "START_SESSION", "Oldest action must be first")
        #expect(pending[1].actionType == "END_SESSION", "Newer action must be second")
    }

    // MARK: - remove

    @Test("remove: deletes only the target action")
    func remove_deletesTargetOnly() throws {
        let (queue, _) = try makeQueue()

        queue.enqueue(.endSession(sessionSlug: "a"))
        queue.enqueue(.startSession(sessionSlug: "b"))

        let pending = queue.pendingActions()
        #expect(pending.count == 2)

        queue.remove(pending[0])

        let remaining = queue.pendingActions()
        #expect(remaining.count == 1)
        // The second action (start-session "b") must still be there
        #expect(remaining[0].actionType != pending[0].actionType
            || remaining[0].id != pending[0].id)
    }

    @Test("remove: does not affect other actions")
    func remove_otherActionsUnaffected() throws {
        let (queue, _) = try makeQueue()

        queue.enqueue(.endSession(sessionSlug: "first"))
        queue.enqueue(.extendSession(sessionSlug: "second", minutes: 3))
        queue.enqueue(.speakerArrived(speakerUsername: "third-user"))

        let all = queue.pendingActions()
        queue.remove(all[1]) // remove middle

        let remaining = queue.pendingActions()
        #expect(remaining.count == 2)
        #expect(remaining[0].actionType == "END_SESSION")
        #expect(remaining[1].actionType == "SPEAKER_ARRIVED")
    }

    // MARK: - clearAll

    @Test("clearAll: empties the queue")
    func clearAll_emptiesQueue() throws {
        let (queue, _) = try makeQueue()

        queue.enqueue(.endSession(sessionSlug: "a"))
        queue.enqueue(.startSession(sessionSlug: "b"))
        #expect(queue.pendingActions().count == 2)

        queue.clearAll()

        #expect(queue.pendingActions().isEmpty)
    }

    @Test("clearAll: no-op on empty queue")
    func clearAll_noopWhenEmpty() throws {
        let (queue, _) = try makeQueue()
        // Should not crash on empty queue
        queue.clearAll()
        #expect(queue.pendingActions().isEmpty)
    }

    // MARK: - markFailed

    @Test("markFailed: returns false before 3 attempts")
    func markFailed_belowThreshold() throws {
        let (queue, _) = try makeQueue()
        queue.enqueue(.endSession(sessionSlug: "s"))
        let action = queue.pendingActions()[0]

        let drop1 = queue.markFailed(action) // attempt 1
        #expect(!drop1)

        let drop2 = queue.markFailed(action) // attempt 2
        #expect(!drop2)
    }

    @Test("markFailed: returns true at exactly 3 attempts")
    func markFailed_atThreshold() throws {
        let (queue, _) = try makeQueue()
        queue.enqueue(.endSession(sessionSlug: "s"))
        let action = queue.pendingActions()[0]

        _ = queue.markFailed(action) // attempt 1
        _ = queue.markFailed(action) // attempt 2
        let drop3 = queue.markFailed(action) // attempt 3
        #expect(drop3)
    }

    // MARK: - WatchActionDto round-trip (encode → decode → WatchAction)

    @Test("WatchActionDto round-trip: endSession encodes and decodes correctly")
    func watchActionDto_endSession_roundTrip() throws {
        let original: WatchAction = .endSession(sessionSlug: "cloud-talk")
        let dto = WatchActionDto(from: original)
        let data = try JSONEncoder().encode(dto)
        let decoded = try JSONDecoder().decode(WatchActionDto.self, from: data)
        let reconstructed = try #require(decoded.toWatchAction())
        #expect(reconstructed == original)
    }

    @Test("WatchActionDto round-trip: extendSession preserves minutes")
    func watchActionDto_extendSession_roundTrip() throws {
        let original: WatchAction = .extendSession(sessionSlug: "talk-1", minutes: 7)
        let dto = WatchActionDto(from: original)
        let data = try JSONEncoder().encode(dto)
        let decoded = try JSONDecoder().decode(WatchActionDto.self, from: data)
        let reconstructed = try #require(decoded.toWatchAction())
        #expect(reconstructed == original)
    }

    @Test("WatchActionDto round-trip: speakerArrived preserves username")
    func watchActionDto_speakerArrived_roundTrip() throws {
        let original: WatchAction = .speakerArrived(speakerUsername: "anna.meier")
        let dto = WatchActionDto(from: original)
        let data = try JSONEncoder().encode(dto)
        let decoded = try JSONDecoder().decode(WatchActionDto.self, from: data)
        let reconstructed = try #require(decoded.toWatchAction())
        #expect(reconstructed == original)
    }
}
