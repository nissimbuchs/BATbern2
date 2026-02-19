//
//  ConnectionStatusBar.swift
//  BATbern-watch Watch App
//
//  Thin status bar showing offline state and stale data indicator.
//  Appears at top of SessionListView when offline or data >15min old.
//  AC: #4 (Offline Browsing), #7 (Stale Data Indicator)
//

import SwiftUI

struct ConnectionStatusBar: View {
    let isOffline: Bool
    let lastSynced: Date?
    let presenceCount: Int
    let isConnected: Bool
    let clock: ClockProtocol

    // MARK: - Initialization

    init(
        isOffline: Bool,
        lastSynced: Date?,
        presenceCount: Int = 0,
        isConnected: Bool = true,
        clock: ClockProtocol = SystemClock()
    ) {
        self.isOffline = isOffline
        self.lastSynced = lastSynced
        self.presenceCount = presenceCount
        self.isConnected = isConnected
        self.clock = clock
    }

    // MARK: - Constants

    private let staleThresholdSeconds: TimeInterval = 15 * 60  // 15 minutes

    // MARK: - Computed State

    /// True when lastSynced is older than 15 minutes
    private var isStale: Bool {
        guard let lastSync = lastSynced else { return false }
        return clock.now.timeIntervalSince(lastSync) > staleThresholdSeconds
    }

    /// True when status bar should be visible (offline OR stale)
    private var shouldShow: Bool {
        isOffline || isStale
    }

    var body: some View {
        HStack(spacing: 5) {
            Image(systemName: isOffline ? "wifi.slash" : "wifi")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(isOffline ? Color.orange : Color.teal.opacity(0.8))

            if shouldShow, let lastSync = lastSynced {
                Text(relativeTimeString(from: lastSync))
                    .font(.system(size: 9))
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 5)
                    .padding(.vertical, 2)
                    .background(Color(white: 0.18), in: Capsule())
            }

            if presenceCount > 0 {
                Text("·")
                    .font(.system(size: 10))
                    .foregroundStyle(.tertiary)
                Image(systemName: presenceCount > 1 ? "person.2.fill" : "person.fill")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(isConnected ? .teal : .orange)
                Text("\(presenceCount)")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(isConnected ? .teal : .orange)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 4)
    }

    // MARK: - Helpers

    /// Format relative time using Swiss German locale
    /// Examples: "vor 2 Min.", "vor 15 Min.", "vor 1 Std."
    private func relativeTimeString(from date: Date) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.locale = Locale(identifier: "de_CH")  // Swiss German
        formatter.unitsStyle = .abbreviated  // "vor 2 Min." not "vor 2 Minuten"
        return formatter.localizedString(for: date, relativeTo: clock.now)
    }
}

// MARK: - Previews

#Preview("Online - Fresh") {
    ConnectionStatusBar(
        isOffline: false,
        lastSynced: Date()  // Just now — teal wifi icon only
    )
}

#Preview("Online - Stale") {
    ConnectionStatusBar(
        isOffline: false,
        lastSynced: Date().addingTimeInterval(-20 * 60)  // 20 min ago — teal icon + time capsule
    )
}

#Preview("Offline - With Cache") {
    ConnectionStatusBar(
        isOffline: true,
        lastSynced: Date().addingTimeInterval(-5 * 60)  // 5 min ago — orange icon + time capsule
    )
}

#Preview("Offline - No Cache") {
    ConnectionStatusBar(
        isOffline: true,
        lastSynced: nil  // Never synced — orange icon only
    )
}
