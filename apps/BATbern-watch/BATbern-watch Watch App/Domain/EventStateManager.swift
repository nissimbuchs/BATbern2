//
//  EventStateManager.swift
//  BATbern-watch Watch App
//
//  Determines event state (pre-event, live, no event) for organizer zone routing.
//  W2.2: State-dependent organizer zone entry (O1/O2/O3 selection logic).
//  Source: docs/watch-app/architecture.md#Navigation-Architecture
//

import Foundation

/// Protocol for EventStateManager dependency injection (testability).
@MainActor
protocol EventStateManagerProtocol: AnyObject {
    var currentEvent: CachedEvent? { get set }
    var hasActiveEvent: Bool { get }
    var isPreEvent: Bool { get }
    var isLive: Bool { get }
    var timeUntilEventStart: TimeInterval? { get }
    /// W4.4: True when the server has broadcast EVENT_COMPLETED for today's event.
    var isEventCompletedToday: Bool { get }
}

/// Tracks event timing state to determine which organizer screen to show.
///
/// | Condition             | isPreEvent | isLive | Entry Screen   |
/// |-----------------------|------------|--------|----------------|
/// | No event              | false      | false  | EventPreview   |
/// | > 1h before event     | false      | false  | EventPreview   |
/// | < 1h before event     | true       | false  | SpeakerArrival |
/// | Event active          | false      | true   | LiveCountdown  |
@Observable
@MainActor
final class EventStateManager: EventStateManagerProtocol {

    /// EventDataController is the single source of truth for event data.
    /// currentEvent reads through to the controller so this manager never holds
    /// a stale copy — OrganizerZoneView no longer needs to set it manually after sync.
    private let eventDataController: EventDataController

    /// Forwarded from EventDataController; the protocol setter is intentionally a no-op
    /// because EventDataController owns writes.
    var currentEvent: CachedEvent? {
        get { eventDataController.currentEvent }
        set { /* EventDataController is the source of truth — writes are ignored */ }
    }

    let clock: ClockProtocol

    init(
        eventDataController: EventDataController,
        clock: ClockProtocol = SystemClock()
    ) {
        self.eventDataController = eventDataController
        self.clock = clock
    }

    /// True when a current event is loaded (AC#4 guard).
    var hasActiveEvent: Bool {
        return currentEvent != nil
    }

    /// Time remaining until event start, in seconds. Nil if no event or start unparseable.
    var timeUntilEventStart: TimeInterval? {
        guard let event = currentEvent else { return nil }
        let now = clock.now
        let eventStart = effectiveStartDate(for: event)
        let interval = eventStart.timeIntervalSince(now)
        return interval > 0 ? interval : nil
    }

    /// True when within 1 hour before event start time (O2: SpeakerArrivalView).
    var isPreEvent: Bool {
        guard let event = currentEvent else { return false }

        let now = clock.now
        let eventStart = effectiveStartDate(for: event)
        let oneHourBefore = eventStart.addingTimeInterval(-3600)

        return now >= oneHourBefore && now < eventStart
    }

    /// True when current time is between event start and end times (O3: LiveCountdownView).
    /// W4.4: Returns false when server has broadcast EVENT_COMPLETED — even within the time window.
    var isLive: Bool {
        guard let event = currentEvent else { return false }
        if event.workflowState == "EVENT_COMPLETED" { return false }

        let now = clock.now
        let eventStart = effectiveStartDate(for: event)
        let eventEnd = effectiveEndDate(for: event)

        return now >= eventStart && now <= eventEnd
    }

    /// W4.4: True when server has broadcast EVENT_COMPLETED for today's event.
    /// Only true on the event day — shows "Event Complete" screen instead of EventPreview.
    var isEventCompletedToday: Bool {
        guard let event = currentEvent,
              event.workflowState == "EVENT_COMPLETED" else { return false }
        return Calendar.current.isDateInToday(event.eventDate)
    }

    // MARK: - Private helpers

    /// Earliest session startTime, falling back to typicalStartTime.
    /// Uses session schedule so the organizer countdown matches the visible agenda
    /// rather than the backend's configured "typical" time.
    private func effectiveStartDate(for event: CachedEvent) -> Date {
        event.sessions.compactMap { $0.startTime }.min()
            ?? parseEventTime(event.typicalStartTime, on: event.eventDate)
    }

    /// Latest session endTime, falling back to typicalEndTime.
    private func effectiveEndDate(for event: CachedEvent) -> Date {
        event.sessions.compactMap { $0.endTime }.max()
            ?? parseEventTime(event.typicalEndTime, on: event.eventDate)
    }

    /// Parse "HH:mm" time string and combine with event date (Europe/Zurich timezone).
    private func parseEventTime(_ timeString: String, on date: Date) -> Date {
        let components = timeString.split(separator: ":").compactMap { Int($0) }
        guard components.count >= 2 else { return date }

        var calendar = Calendar.current
        calendar.timeZone = TimeZone(identifier: "Europe/Zurich") ?? .current

        var dateComponents = calendar.dateComponents([.year, .month, .day], from: date)
        dateComponents.hour = components[0]
        dateComponents.minute = components[1]

        return calendar.date(from: dateComponents) ?? date
    }
}
