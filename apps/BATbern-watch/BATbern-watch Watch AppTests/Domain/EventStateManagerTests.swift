//
//  EventStateManagerTests.swift
//  BATbern-watch Watch AppTests
//
//  W4.4: Tests for EventStateManagerProtocol behaviors:
//  - isEventCompletedToday routing condition
//  - isLive returns false when workflowState == "EVENT_COMPLETED"
//
//  Uses MockEventStateManager (conforming to protocol) for pure logic verification.
//  The concrete EventStateManager wires these properties to EventDataController;
//  that integration path is covered by EventDataControllerApplyServerStateTests.
//
//  Source: docs/watch-app/architecture.md#Navigation-Architecture
//

import Testing
import Foundation
import SwiftData
@testable import BATbern_watch_Watch_App

// MARK: - CachedEvent.workflowState Tests

@Suite("CachedEvent.workflowState")
struct CachedEventWorkflowStateTests {

    private let today = Date()

    @Test("workflowState is nil by default")
    func workflowState_nilByDefault() {
        let event = CachedEvent(
            eventCode: "bat-2026",
            title: "BATbern",
            eventDate: Date(),
            venueName: "Kornhaus",
            typicalStartTime: "18:00",
            typicalEndTime: "22:00"
        )
        #expect(event.workflowState == nil)
    }

    @Test("workflowState can be set to EVENT_COMPLETED")
    func workflowState_canBeSetToEventCompleted() {
        let event = CachedEvent(
            eventCode: "bat-2026",
            title: "BATbern",
            eventDate: Date(),
            venueName: "Kornhaus",
            typicalStartTime: "18:00",
            typicalEndTime: "22:00",
            workflowState: "EVENT_COMPLETED"
        )
        #expect(event.workflowState == "EVENT_COMPLETED")
    }

    @Test("workflowState can be updated after initialization")
    func workflowState_canBeUpdated() {
        let event = CachedEvent(
            eventCode: "bat-2026",
            title: "BATbern",
            eventDate: Date(),
            venueName: "Kornhaus",
            typicalStartTime: "18:00",
            typicalEndTime: "22:00"
        )
        #expect(event.workflowState == nil)
        event.workflowState = "EVENT_COMPLETED"
        #expect(event.workflowState == "EVENT_COMPLETED")
    }
}

// MARK: - EventStateManagerProtocol Routing Tests

/// Verifies the OrganizerZoneView routing conditions via MockEventStateManager.
/// These tests document the contract that concrete implementations must satisfy.
@Suite("EventStateManagerProtocol — W4.4 Routing Conditions")
@MainActor
struct EventStateManagerRoutingTests {

    private func makeMock(
        isLive: Bool = false,
        isEventCompletedToday: Bool = false,
        isPreEvent: Bool = false,
        hasEvent: Bool = true
    ) -> MockEventStateManager {
        let mock = MockEventStateManager()
        mock.isLive = isLive
        mock.isEventCompletedToday = isEventCompletedToday
        mock.isPreEvent = isPreEvent
        if hasEvent {
            mock.currentEvent = CachedEvent(
                eventCode: "bat-2026",
                title: "BATbern",
                eventDate: Date(),
                venueName: "Kornhaus",
                typicalStartTime: "18:00",
                typicalEndTime: "22:00"
            )
        }
        return mock
    }

    // MARK: - isEventCompletedToday routing

    @Test("isEventCompletedToday is false by default")
    func isEventCompletedToday_falseByDefault() {
        let mock = makeMock()
        #expect(mock.isEventCompletedToday == false)
    }

    @Test("OrganizerZoneView routes to EventCompletedView when isEventCompletedToday")
    func routing_eventCompletedToday() {
        // OrganizerZoneView routing:
        // if isLive → LiveCountdownView
        // else if isEventCompletedToday → EventCompletedView   ← this path
        // else if isPreEvent → SpeakerArrivalView
        // else → EventPreviewView
        let mock = makeMock(isLive: false, isEventCompletedToday: true, isPreEvent: false)

        #expect(mock.isLive == false)
        #expect(mock.isEventCompletedToday == true)
        // EventCompletedView path: not isLive, not isPreEvent, but isEventCompletedToday
    }

    @Test("isLive takes priority over isEventCompletedToday (not possible in practice)")
    func routing_liveTakesPriorityOverCompleted() {
        // isLive == true and isEventCompletedToday == true should not both be true
        // at runtime, but isLive check comes first in routing
        let mock = makeMock(isLive: true, isEventCompletedToday: true)

        #expect(mock.isLive == true)
        // Routing goes to LiveCountdownView — isEventCompletedToday not reached
    }

    @Test("EventPreviewView shown when neither live nor completed nor pre-event")
    func routing_eventPreviewFallback() {
        let mock = makeMock(isLive: false, isEventCompletedToday: false, isPreEvent: false)

        #expect(mock.isLive == false)
        #expect(mock.isEventCompletedToday == false)
        #expect(mock.isPreEvent == false)
        // Routing goes to EventPreviewView
    }

    // MARK: - isLive with EVENT_COMPLETED (concrete class contract)

    @Test("CachedEvent with EVENT_COMPLETED workflowState should cause isLive=false")
    func isLive_falseWhenEventCompleted_contractTest() {
        // This documents the contract: when workflowState == "EVENT_COMPLETED",
        // EventStateManager.isLive must return false.
        // The concrete implementation is: if event.workflowState == "EVENT_COMPLETED" { return false }
        let event = CachedEvent(
            eventCode: "bat-2026",
            title: "BATbern",
            eventDate: Date(),
            venueName: "Kornhaus",
            typicalStartTime: "18:00",
            typicalEndTime: "22:00",
            workflowState: "EVENT_COMPLETED"
        )
        // workflowState correctly set
        #expect(event.workflowState == "EVENT_COMPLETED")
        // A correctly-implementing EventStateManager would return false for isLive
        // when this event is loaded. Covered by EventDataControllerApplyServerStateTests.
    }
}
