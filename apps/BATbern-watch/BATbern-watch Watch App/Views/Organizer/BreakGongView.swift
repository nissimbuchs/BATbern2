//
//  BreakGongView.swift
//  BATbern-watch Watch App
//
//  O5: Break screen shown as .fullScreenCover from O3 LiveCountdownView
//  when the active session is a break.
//
//  Displays:
//    - BreakCardLayout: type-specific SF Symbol icon + break title
//    - Remaining time countdown
//    - NextSessionPeekView: next talk preview (if viewModel.nextSession != nil) — W4.4 AC1
//    - "Break ending soon" banner when gongOverlayVisible (≤60s remain) — W4.4 AC2
//
//  W4.4 AC1 (O3→O5 auto-transition), W4.4 AC2 (gong overlay).
//  Source: docs/watch-app/ux-design-specification.md#Session-Card-Pages
//

import SwiftUI

struct BreakGongView: View {
    let viewModel: LiveCountdownViewModel

    var body: some View {
        ZStack(alignment: .top) {
            Color.black.ignoresSafeArea()

            VStack(spacing: 12) {
                Spacer()

                // Break icon + session title (shared with SessionCardView public zone)
                if let session = viewModel.activeSession {
                    BreakCardLayout(
                        sessionType: session.sessionType,
                        title: session.title
                    )
                }

                // Remaining time display
                Text(viewModel.formattedTime)
                    .font(.system(size: 20, weight: .semibold, design: .monospaced))
                    .foregroundStyle(.secondary)

                Spacer()

                // W4.4 AC1: Next speaker preview — reuses W4.2 shared component.
                // Hidden when viewModel.nextSession is nil (final break before event end).
                if let next = viewModel.nextSession {
                    NextSessionPeekView(session: next, style: .compact)
                }
            }
            .padding(.horizontal, 12)
            .padding(.top, 8)

            // W4.4 AC2: "Break ending soon" banner — slides in from top when ≤60s remain.
            if viewModel.gongOverlayVisible {
                HStack(spacing: 6) {
                    Image(systemName: "bell.fill")
                        .font(.caption2)
                        .foregroundStyle(.orange)
                    Text(NSLocalizedString("break.ending.soon", comment: "Break ending soon banner"))
                        .font(.caption2)
                        .foregroundStyle(.orange)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Color.orange.opacity(0.15), in: Capsule())
                .padding(.top, 6)
                .transition(.asymmetric(
                    insertion: .move(edge: .top).combined(with: .opacity),
                    removal: .opacity
                ))
            }
        }
        .animation(.easeInOut(duration: 0.3), value: viewModel.gongOverlayVisible)
    }
}

// MARK: - Previews

#Preview("Break Active — No Gong") {
    BreakGongView(viewModel: LiveCountdownViewModel())
}

#Preview("Break Active — Gong Overlay") {
    BreakGongView(viewModel: LiveCountdownViewModel())
}
