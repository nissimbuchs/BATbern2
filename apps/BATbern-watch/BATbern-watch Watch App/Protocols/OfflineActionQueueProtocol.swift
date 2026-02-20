//
//  OfflineActionQueueProtocol.swift
//  BATbern-watch Watch App
//
//  DI contract for the offline action queue.
//  Story W5.2 Task 2 — protocol-based dependency injection pattern.
//

import Foundation

/// Contract for persisting and replaying WatchActions queued while offline.
protocol OfflineActionQueueProtocol: AnyObject {

    /// Encode and persist a WatchAction for later replay.
    func enqueue(_ action: WatchAction)

    /// Return all queued actions sorted by enqueuedAt ascending (oldest first).
    func pendingActions() -> [OfflineAction]

    /// Delete a single action after successful replay.
    func remove(_ action: OfflineAction)

    /// Delete all queued actions (used after full successful replay drain).
    func clearAll()

    /// Increment the attempt counter and persist. Returns true if the action
    /// has exceeded the 3-attempt cap and should be dropped.
    func markFailed(_ action: OfflineAction) -> Bool
}
