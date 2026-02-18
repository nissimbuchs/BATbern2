//
//  PublicViewModel.swift
//  BATbern-watch Watch App
//
//  Presentation-only view model for the public zone.
//  Event data is sourced entirely from EventDataController — no network calls here.
//  Source: docs/watch-app/architecture.md#Public-Zone-Data-Flow
//

import Foundation
import SwiftData

@Observable
final class PublicViewModel {

    // MARK: - Dependencies

    private let eventDataController: EventDataController

    // MARK: - Forwarded State (from EventDataController)

    var event: CachedEvent? { eventDataController.currentEvent }
    var isLoading: Bool { eventDataController.isLoading }
    var isOffline: Bool { eventDataController.isOffline }
    var lastSynced: Date? { eventDataController.lastSynced }

    // MARK: - Computed Properties (W1.2 - Session Card Browsing)

    /// Returns displayable sessions: filters out placeholders, sorted by startTime (AC#7)
    var displayableSessions: [CachedSession] {
        (event?.sessions ?? [])
            .filter { session in
                // Exclude placeholder sessions (null sessionType or null startTime/endTime)
                session.sessionType != nil && session.startTime != nil && session.endTime != nil
            }
            .sorted { ($0.startTime ?? Date()) < ($1.startTime ?? Date()) }
    }

    /// True when currentPublishedPhase is SPEAKERS or AGENDA (AC#6)
    var hasSpeakerPhase: Bool {
        guard let phase = event?.currentPublishedPhase else { return false }
        return phase == "SPEAKERS" || phase == "AGENDA"
    }

    /// True when currentPublishedPhase is AGENDA (AC#6)
    var hasAgendaPhase: Bool {
        event?.currentPublishedPhase == "AGENDA"
    }

    /// Checks if session is a break/networking/lunch session (AC#3)
    func isBreakSession(_ session: CachedSession) -> Bool {
        guard let type = session.sessionType else { return false }
        return type == .breakTime || type == .lunch || type == .networking
    }

    // MARK: - Initialization

    init(eventDataController: EventDataController) {
        self.eventDataController = eventDataController
    }
}
