//
//  LiveCountdownView.swift
//  BATbern-watch Watch App
//
//  O3: Live countdown timer with progress ring, speaker name, and talk title.
//  W3.1: Replaces placeholder — full implementation per UX spec O3.
//  Source: docs/watch-app/ux-design-specification.md#O3
//

import SwiftUI

struct LiveCountdownView: View {

    @Environment(EventStateManager.self) private var eventState
    @State private var viewModel = LiveCountdownViewModel()

    var body: some View {
        Group {
            if viewModel.activeSession != nil {
                countdownContent
            } else {
                noSessionContent
            }
        }
        .onAppear {
            // Pass environment after it resolves (anti-pattern: onAppear fires on every tab swipe;
            // startTimer() is idempotent — cancels existing task before creating new one)
            viewModel.eventState = eventState
            viewModel.startTimer()
        }
        .onDisappear {
            viewModel.stopTimer()
        }
    }

    // MARK: - Countdown Content (AC1-AC5)

    private var countdownContent: some View {
        ZStack {
            // Background ring
            Circle()
                .stroke(Color.gray.opacity(0.3), lineWidth: 6)

            // Colored progress arc — starts at top (-90°)
            Circle()
                .trim(from: 0, to: viewModel.progress)
                .stroke(
                    countdownColor,
                    style: StrokeStyle(lineWidth: 6, lineCap: .round)
                )
                .rotationEffect(.degrees(-90))
                .animation(.linear(duration: 0.5), value: viewModel.progress)

            // Center: countdown + speaker name + talk title
            VStack(spacing: 2) {
                // Countdown in MM:SS (SF Mono 40pt bold) — AC1
                Text(viewModel.formattedTime)
                    .font(.system(size: 40, weight: .bold, design: .monospaced))
                    .foregroundStyle(countdownColor)
                    .minimumScaleFactor(0.5)

                // Speaker name (SF Pro Rounded 16pt semibold) — AC1
                Text(viewModel.speakerNames)
                    .font(.system(size: 16, weight: .semibold, design: .rounded))
                    .lineLimit(1)

                // Talk title (SF Pro 13pt) — AC1
                Text(viewModel.sessionTitle)
                    .font(.system(size: 13))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .padding(8)
    }

    // MARK: - No-Session Edge Case (2.8)

    private var noSessionContent: some View {
        VStack(spacing: 8) {
            Image(systemName: "timer")
                .font(.system(size: 24))
                .foregroundStyle(.secondary)

            Text("No active session")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    // MARK: - Color Mapping (AC2-AC5, 2.5)

    private var countdownColor: Color {
        switch viewModel.urgencyLevel {
        case .normal:               return .teal       // BATbern Blue / green accent
        case .caution:              return .yellow
        case .warning, .critical:   return .orange     // no distinct <1min color in spec
        case .overtime:             return .red
        }
    }
}

// MARK: - Preview (2.9)

#Preview {
    LiveCountdownView()
        .environment(EventStateManager())
}
