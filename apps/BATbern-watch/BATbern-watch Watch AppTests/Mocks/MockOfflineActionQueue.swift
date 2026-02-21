//
//  MockOfflineActionQueue.swift
//  BATbern-watch Watch AppTests
//
//  Test double for OfflineActionQueueProtocol.
//  Story W5.2 Task 6.
//

import Foundation
@testable import BATbern_watch_Watch_App

/// Recording mock for OfflineActionQueueProtocol. Captures enqueued actions and
/// allows tests to pre-configure pending actions for replay tests.
final class MockOfflineActionQueue: OfflineActionQueueProtocol {

    // MARK: - Recorded calls

    private(set) var enqueuedActions: [WatchAction] = []
    private(set) var removedActions: [OfflineAction] = []
    private(set) var clearAllCallCount: Int = 0
    private(set) var markFailedCallCount: Int = 0

    // MARK: - Configuration

    /// Actions returned by `pendingActions()` — configure before the test.
    var pendingActionsToReturn: [OfflineAction] = []

    /// Value returned by `markFailed(_:)` — set to `true` to simulate 3-attempt cap.
    var markFailedShouldDrop: Bool = false

    // MARK: - OfflineActionQueueProtocol

    func enqueue(_ action: WatchAction) {
        enqueuedActions.append(action)
    }

    func pendingActions() -> [OfflineAction] {
        return pendingActionsToReturn
    }

    func remove(_ action: OfflineAction) {
        removedActions.append(action)
    }

    func clearAll() {
        clearAllCallCount += 1
    }

    func markFailed(_ action: OfflineAction) -> Bool {
        markFailedCallCount += 1
        return markFailedShouldDrop
    }

    // MARK: - Helpers

    func reset() {
        enqueuedActions = []
        removedActions = []
        clearAllCallCount = 0
        markFailedCallCount = 0
        pendingActionsToReturn = []
        markFailedShouldDrop = false
    }
}
