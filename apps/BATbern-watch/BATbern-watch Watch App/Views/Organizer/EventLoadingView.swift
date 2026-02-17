//
//  EventLoadingView.swift
//  BATbern-watch Watch App
//
//  Loading view shown during event schedule sync.
//  W2.3: AC#2 — "Connecting to event..." spinner with progress indicator.
//

import SwiftUI

/// Shown while EventSyncService is syncing the active event schedule.
/// AC#2: Displays event title, "Connecting to event..." and percentage progress.
struct EventLoadingView: View {
    let eventTitle: String
    let progress: Double  // 0.0 to 1.0

    var body: some View {
        VStack(spacing: 12) {
            ProgressView(value: progress, total: 1.0)
                .progressViewStyle(.circular)
                .scaleEffect(1.4)

            VStack(spacing: 4) {
                Text(NSLocalizedString("sync.connecting_to_event", comment: "Connecting to event"))
                    .font(.headline)
                    .multilineTextAlignment(.center)

                if !eventTitle.isEmpty {
                    Text(eventTitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                        .multilineTextAlignment(.center)
                }

                Text("\(Int(progress * 100))%")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .monospacedDigit()
            }
        }
        .padding()
    }
}

#Preview {
    EventLoadingView(
        eventTitle: "BATbern 56 - Cloud Native Architectures",
        progress: 0.45
    )
}
