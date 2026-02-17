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
    let clock: ClockProtocol

    // MARK: - Initialization

    init(isOffline: Bool, lastSynced: Date?, clock: ClockProtocol = SystemClock()) {
        self.isOffline = isOffline
        self.lastSynced = lastSynced
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
        if shouldShow {
            HStack(spacing: 4) {
                if isOffline {
                    // Offline indicator with WiFi slash icon
                    Image(systemName: "wifi.slash")
                        .font(BATbernWatchStyle.Typography.statusBar)
                        .foregroundStyle(.secondary)

                    Text(NSLocalizedString("status.offline", comment: "Offline"))
                        .font(BATbernWatchStyle.Typography.statusBar)
                        .foregroundStyle(.secondary)

                    if let lastSync = lastSynced {
                        Text("·")
                            .font(BATbernWatchStyle.Typography.statusBar)
                            .foregroundStyle(.tertiary)

                        Text(relativeTimeString(from: lastSync))
                            .font(BATbernWatchStyle.Typography.statusBar)
                            .foregroundStyle(.tertiary)
                    }
                } else if isStale {
                    // Stale data indicator (connected but old cache)
                    Text(NSLocalizedString("status.updated", comment: "Aktualisiert"))
                        .font(BATbernWatchStyle.Typography.statusBar)
                        .foregroundStyle(.secondary)

                    if let lastSync = lastSynced {
                        Text(relativeTimeString(from: lastSync))
                            .font(BATbernWatchStyle.Typography.statusBar)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 8)  // Thin 8pt bar per UX spec
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
        }
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

#Preview("Fresh - Hidden") {
    ConnectionStatusBar(
        isOffline: false,
        lastSynced: Date()  // Just now - fresh
    )
}

#Preview("Stale - Visible") {
    ConnectionStatusBar(
        isOffline: false,
        lastSynced: Date().addingTimeInterval(-20 * 60)  // 20 minutes ago - stale
    )
}

#Preview("Offline - Visible") {
    ConnectionStatusBar(
        isOffline: true,
        lastSynced: Date().addingTimeInterval(-5 * 60)  // 5 minutes ago
    )
}

#Preview("Offline No Cache") {
    ConnectionStatusBar(
        isOffline: true,
        lastSynced: nil  // Never synced
    )
}
