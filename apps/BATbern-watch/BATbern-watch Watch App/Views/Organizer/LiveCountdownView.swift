//
//  LiveCountdownView.swift
//  BATbern-watch Watch App
//
//  O3: Live countdown — Progress Ring + Card Stack (chosen design direction).
//  W3.1: Compact ring in upper half, speaker card, next-session peek below.
//  Source: docs/watch-app/ux-design-directions.html#Chosen-Direction
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
            viewModel.eventState = eventState
            viewModel.startTimer()
        }
        .onDisappear {
            viewModel.stopTimer()
        }
    }

    // MARK: - Countdown Content

    private var countdownContent: some View {
        VStack(spacing: 5) {
            statusBar
            compactRing
            speakerCard
            if let next = viewModel.nextSession {
                nextSessionCard(next: next)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
    }

    // MARK: - Status Bar

    /// Connection indicator + urgency state label (e.g. "ON TRACK", "5 MIN LEFT").
    private var statusBar: some View {
        HStack(spacing: 4) {
            Circle()
                .fill(countdownColor)
                .frame(width: 5, height: 5)
            Spacer()
            Text(stateLabel)
                .font(.system(size: 8, weight: .semibold))
                .foregroundStyle(countdownColor)
        }
    }

    // MARK: - Compact Progress Ring (AC1-AC5)

    /// Fixed-size ring with countdown inside — not edge-to-edge.
    private var compactRing: some View {
        ZStack {
            // Background track
            Circle()
                .stroke(Color(white: 0.15), lineWidth: 7)

            // Colored progress arc — starts at top (-90°)
            Circle()
                .trim(from: 0, to: viewModel.progress)
                .stroke(
                    countdownColor,
                    style: StrokeStyle(lineWidth: 7, lineCap: .round)
                )
                .rotationEffect(.degrees(-90))
                .animation(.linear(duration: 0.5), value: viewModel.progress)

            // Center: countdown + "REMAINING" label
            VStack(spacing: 0) {
                Text(viewModel.formattedTime)
                    .font(.system(size: 26, weight: .bold, design: .monospaced))
                    .foregroundStyle(countdownColor)
                    .minimumScaleFactor(0.6)
                if viewModel.urgencyLevel != .overtime {
                    Text("REMAINING")
                        .font(.system(size: 7))
                        .foregroundStyle(.tertiary)
                        .kerning(0.3)
                }
            }
        }
        .frame(width: 96, height: 96)
    }

    // MARK: - Speaker Card (AC1)

    /// Styled card: initials avatar + speaker name + talk title.
    private var speakerCard: some View {
        HStack(spacing: 7) {
            // Initials avatar
            ZStack {
                Circle()
                    .fill(LinearGradient(
                        colors: [
                            Color(red: 0.17, green: 0.37, blue: 0.49),
                            Color(red: 0.29, green: 0.57, blue: 0.72)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ))
                Text(speakerInitials)
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(.white)
            }
            .frame(width: 24, height: 24)

            // Name + title (SF Pro Rounded / SF Pro per AC1)
            VStack(alignment: .leading, spacing: 1) {
                Text(viewModel.speakerNames)
                    .font(.system(size: 11, weight: .semibold, design: .rounded))
                    .lineLimit(1)
                Text(viewModel.sessionTitle)
                    .font(.system(size: 9))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 10)
        .padding(.vertical, 7)
        .background(Color(white: 0.10))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color(white: 0.15), lineWidth: 1)
        )
    }

    // MARK: - Next Session Peek Card

    /// Dimmed card peeking below — next non-break session name + start time.
    private func nextSessionCard(next: WatchSession) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 1) {
                Text("NEXT")
                    .font(.system(size: 7))
                    .foregroundStyle(Color(white: 0.4))
                    .kerning(0.5)
                Text(next.speakers.first?.fullName ?? next.title)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer()
            Text(startTimeString(next.startTime))
                .font(.system(size: 9, design: .monospaced))
                .foregroundStyle(Color(white: 0.3))
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(Color(white: 0.067))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color(white: 0.10), lineWidth: 1)
        )
        .opacity(0.6)
    }

    // MARK: - No-Session State

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

    // MARK: - Helpers

    /// Countdown color mapping per AC2-AC5.
    private var countdownColor: Color {
        switch viewModel.urgencyLevel {
        case .normal:               return .teal
        case .caution:              return .yellow
        case .warning, .critical:   return .orange
        case .overtime:             return .red
        }
    }

    /// Status label reflects urgency state at a glance.
    private var stateLabel: String {
        switch viewModel.urgencyLevel {
        case .normal:               return "ON TRACK"
        case .caution:              return "5 MIN LEFT"
        case .warning, .critical:   return "2 MIN LEFT"
        case .overtime:             return "OVERRUN"
        }
    }

    /// Two-letter initials from the first speaker's full name.
    private var speakerInitials: String {
        let firstName = viewModel.speakerNames
            .components(separatedBy: ",")
            .first?
            .trimmingCharacters(in: .whitespaces) ?? viewModel.speakerNames
        let parts = firstName.components(separatedBy: " ").filter { !$0.isEmpty }
        let first = parts.first?.first.map(String.init) ?? ""
        let last = parts.dropFirst().last?.first.map(String.init) ?? ""
        let initials = (first + last).uppercased()
        return initials.isEmpty ? "?" : initials
    }

    private static let startTimeFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        f.timeZone = TimeZone(identifier: "Europe/Zurich") ?? .current
        return f
    }()

    private func startTimeString(_ date: Date) -> String {
        LiveCountdownView.startTimeFormatter.string(from: date)
    }
}
