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
    var isPreEvent: Bool { get }
    var isLive: Bool { get }
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

    var currentEvent: CachedEvent?

    let clock: ClockProtocol

    init(clock: ClockProtocol = SystemClock()) {
        self.clock = clock
    }

    /// True when within 1 hour before event start time (O2: SpeakerArrivalView).
    var isPreEvent: Bool {
        guard let event = currentEvent else { return false }

        let now = clock.now
        let eventStart = parseEventTime(event.typicalStartTime, on: event.eventDate)
        let oneHourBefore = eventStart.addingTimeInterval(-3600)

        return now >= oneHourBefore && now < eventStart
    }

    /// True when current time is between event start and end times (O3: LiveCountdownView).
    var isLive: Bool {
        guard let event = currentEvent else { return false }

        let now = clock.now
        let eventStart = parseEventTime(event.typicalStartTime, on: event.eventDate)
        let eventEnd = parseEventTime(event.typicalEndTime, on: event.eventDate)

        return now >= eventStart && now <= eventEnd
    }

    // MARK: - Private helpers

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
