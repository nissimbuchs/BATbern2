//
//  EventPreviewView.swift
//  BATbern-watch Watch App
//
//  Organizer zone: No active event / event >1h away state.
//  W2.3: AC#4 — "No active event" message; AC#5 — event preview with countdown.
//

import SwiftUI

/// Shown when paired but no event is imminent:
/// - No event: "No active event" message.
/// - Event >1h away: title, date, venue, and countdown to start.
struct EventPreviewView: View {
    @Environment(EventStateManager.self) private var eventState

    var body: some View {
        VStack(spacing: 12) {
            if let event = eventState.currentEvent {
                eventPreviewContent(event: event)
            } else {
                noEventContent
            }
        }
        .padding()
    }

    // MARK: - Event Preview (AC#5)

    @ViewBuilder
    private func eventPreviewContent(event: CachedEvent) -> some View {
        Image(systemName: "calendar.badge.clock")
            .font(.system(size: 36))
            .foregroundStyle(.blue)

        Text(event.title)
            .font(.headline)
            .multilineTextAlignment(.center)
            .lineLimit(2)

        Text(event.eventDate, style: .date)
            .font(.subheadline)
            .foregroundStyle(.secondary)

        if let timeUntil = eventState.timeUntilEventStart, timeUntil > 0 {
            Text(String(
                format: NSLocalizedString("preview.starts_in", comment: "Starts in"),
                formatTimeInterval(timeUntil)
            ))
            .font(.caption)
            .foregroundStyle(.secondary)
        }

        Text(event.venueName)
            .font(.caption2)
            .foregroundStyle(.secondary)
    }

    // MARK: - No Active Event (AC#4)

    private var noEventContent: some View {
        VStack(spacing: 10) {
            Image(systemName: "calendar.badge.clock")
                .font(.system(size: 36))
                .foregroundStyle(.secondary)

            Text(NSLocalizedString("preview.no_active_event", comment: "No active event"))
                .font(.headline)

            Text(NSLocalizedString("preview.check_back_later", comment: "Check back closer"))
                .font(.caption2)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
    }

    // MARK: - Helpers

    private func formatTimeInterval(_ interval: TimeInterval) -> String {
        let hours = Int(interval) / 3600
        let minutes = (Int(interval) % 3600) / 60
        if hours > 0 {
            return "\(hours)h \(minutes)m"
        } else {
            return "\(minutes)m"
        }
    }
}

#Preview("No Event") {
    EventPreviewView()
        .environment(EventStateManager())
}
