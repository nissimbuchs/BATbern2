//
//  NextSessionPeekView.swift
//  BATbern-watch Watch App
//
//  W4.2 Task 0: Shared component extracted from LiveCountdownView.nextSessionCard().
//  Supports .compact mode (O3 peek) and .prominent mode (O6 SessionTransitionView).
//  Source: docs/watch-app/epic-4-reuse-map.md#Area-1
//

import SwiftUI

// MARK: - Style

/// Display mode for NextSessionPeekView.
/// .compact — dimmed peek card below O3 countdown (extracted from nextSessionCard()).
/// .prominent — full-screen layout for O6 SessionTransitionView (portrait + name + title).
enum NextSessionPeekStyle {
    case compact
    case prominent
}

// MARK: - View

struct NextSessionPeekView: View {

    let session: WatchSession
    let style: NextSessionPeekStyle

    /// Portrait image data — loaded externally via PortraitCache.shared (Area 1 mandate).
    @State private var portraitData: Data?

    var body: some View {
        switch style {
        case .compact:
            compactLayout
        case .prominent:
            prominentLayout
        }
    }

    // MARK: - Compact Layout (O3 peek)

    /// Dimmed card: "NEXT" label, speaker first-name-or-title, HH:mm start time, 0.6 opacity.
    private var compactLayout: some View {
        HStack {
            VStack(alignment: .leading, spacing: 1) {
                Text("NEXT")
                    .font(.system(size: 7))
                    .foregroundStyle(Color(white: 0.4))
                    .kerning(0.5)
                Text(session.speakers.first?.fullName ?? session.title)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer()
            Text(startTimeString(session.startTime))
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

    // MARK: - Prominent Layout (O6 full-screen)

    /// Full-screen layout: large portrait circle (40pt), full speaker name, talk title.
    private var prominentLayout: some View {
        VStack(spacing: 10) {
            portraitCircle
                .frame(width: 40, height: 40)

            VStack(spacing: 3) {
                Text(session.speakers.first?.fullName ?? session.title)
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                    .multilineTextAlignment(.center)
                    .lineLimit(2)

                Text(session.title)
                    .font(.system(size: 10))
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
            }
        }
        .task(id: session.id) {
            await loadPortrait()
        }
    }

    // MARK: - Portrait Circle (prominent mode)

    @ViewBuilder
    private var portraitCircle: some View {
        if let data = portraitData, let uiImage = UIImage(data: data) {
            Image(uiImage: uiImage)
                .resizable()
                .aspectRatio(contentMode: .fill)
                .clipShape(Circle())
        } else {
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
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.white)
            }
        }
    }

    // MARK: - Helpers

    /// Cache-first portrait load using PortraitCache.shared (Area 1 mandate — same pattern
    /// as LiveCountdownView.loadPortrait(), lines 196–207).
    private func loadPortrait() async {
        guard let urlString = session.speakers.first?.profilePictureUrl,
              let url = URL(string: urlString) else {
            portraitData = nil
            return
        }
        if let cached = PortraitCache.shared.getCachedPortrait(url: url) {
            portraitData = cached
            return
        }
        portraitData = try? await PortraitCache.shared.downloadAndCache(url: url)
    }

    private static let startTimeFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        f.timeZone = TimeZone(identifier: "Europe/Zurich") ?? .current
        return f
    }()

    private func startTimeString(_ date: Date) -> String {
        NextSessionPeekView.startTimeFormatter.string(from: date)
    }

    private var speakerInitials: String {
        let firstName = session.speakers.first?.fullName ?? session.title
        let parts = firstName.components(separatedBy: " ").filter { !$0.isEmpty }
        let first = parts.first?.first.map(String.init) ?? ""
        let last = parts.dropFirst().last?.first.map(String.init) ?? ""
        let initials = (first + last).uppercased()
        return initials.isEmpty ? "?" : initials
    }
}
