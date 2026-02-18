//
//  EventSyncService.swift
//  BATbern-watch Watch App
//
//  Event sync state enums shared by EventDataController and legacy tests.
//  The sync implementation has moved to EventDataController.
//  The DTOs have moved to OrganizerEventClientProtocol.
//

import Foundation

// MARK: - Sync State

enum SyncState: Equatable {
    case idle
    case syncing
    case completed
    case noActiveEvent
    case error(String)
}

// MARK: - Sync Error

enum SyncError: Error, LocalizedError, Equatable {
    case notAuthenticated
    case authenticationRequired
    case networkError
    case serverError(Int)
    case noActiveEvent

    var errorDescription: String? {
        switch self {
        case .notAuthenticated:
            return NSLocalizedString("sync.error.not_authenticated", comment: "Not authenticated")
        case .authenticationRequired:
            return NSLocalizedString("sync.error.auth_required", comment: "Auth required")
        case .networkError:
            return NSLocalizedString("sync.error.network", comment: "Network error")
        case .serverError(let code):
            return "Server error: \(code)"
        case .noActiveEvent:
            return NSLocalizedString("sync.error.no_event", comment: "No active event")
        }
    }
}
