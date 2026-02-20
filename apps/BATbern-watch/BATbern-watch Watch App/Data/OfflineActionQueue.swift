//
//  OfflineActionQueue.swift
//  BATbern-watch Watch App
//
//  SwiftData-backed queue for offline WatchActions.
//  Story W5.2 Task 2 (AC#1, #2, #3).
//
//  Actions are persisted to SwiftData via OfflineAction @Model records.
//  They survive app kill/restart (NFR11) and replay in chronological order
//  when WebSocket connectivity is restored.
//

import Foundation
import SwiftData
import OSLog

private let logger = Logger(subsystem: "ch.batbern.watch", category: "OfflineActionQueue")

/// SwiftData-backed persistent queue for WatchActions queued while offline.
final class OfflineActionQueue: OfflineActionQueueProtocol {

    // MARK: - Dependencies

    private let modelContext: ModelContext

    // MARK: - Init

    /// - Parameter modelContext: The shared SwiftData context (must be the same as the app's main context).
    init(modelContext: ModelContext) {
        self.modelContext = modelContext
    }

    // MARK: - OfflineActionQueueProtocol

    /// Encode the action as JSON and persist it as an OfflineAction record.
    /// Uses WatchActionDto encoding — the same format sent over STOMP (W5.2 Task 2.3).
    func enqueue(_ action: WatchAction) {
        let dto = WatchActionDto(from: action)
        guard let data = try? JSONEncoder().encode(dto) else {
            logger.error("OfflineActionQueue: failed to encode action \(dto.type, privacy: .public)")
            return
        }
        let record = OfflineAction(
            actionType: dto.type,
            payload: data
        )
        modelContext.insert(record)
        do {
            try modelContext.save()
            logger.info("OfflineActionQueue: enqueued \(dto.type, privacy: .public)")
        } catch {
            logger.error("OfflineActionQueue: save failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    /// Fetch all pending actions sorted oldest-first (chronological replay order).
    func pendingActions() -> [OfflineAction] {
        let descriptor = FetchDescriptor<OfflineAction>(
            sortBy: [SortDescriptor(\.enqueuedAt, order: .forward)]
        )
        do {
            return try modelContext.fetch(descriptor)
        } catch {
            logger.error("OfflineActionQueue: fetch failed: \(error.localizedDescription, privacy: .public)")
            return []
        }
    }

    /// Delete a single OfflineAction after successful replay.
    func remove(_ action: OfflineAction) {
        modelContext.delete(action)
        do {
            try modelContext.save()
        } catch {
            logger.error("OfflineActionQueue: remove save failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    /// Delete all queued actions (post-drain cleanup).
    func clearAll() {
        for action in pendingActions() {
            modelContext.delete(action)
        }
        do {
            try modelContext.save()
            logger.info("OfflineActionQueue: cleared all pending actions")
        } catch {
            logger.error("OfflineActionQueue: clearAll save failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    /// Increment attemptCount and persist. Returns true if the action should be dropped (>= 3 attempts).
    func markFailed(_ action: OfflineAction) -> Bool {
        action.attemptCount += 1
        do {
            try modelContext.save()
        } catch {
            logger.error("OfflineActionQueue: markFailed save failed: \(error.localizedDescription, privacy: .public)")
        }
        logger.warning(
            "OfflineActionQueue: action \(action.actionType, privacy: .public) failed (attempt \(action.attemptCount, privacy: .public))"
        )
        return action.attemptCount >= 3
    }
}
