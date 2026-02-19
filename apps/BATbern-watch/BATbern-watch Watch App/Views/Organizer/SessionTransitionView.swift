//
//  SessionTransitionView.swift
//  BATbern-watch Watch App
//
//  O6: Full-screen session transition displayed after SESSION_ENDED broadcast confirmed.
//  W4.2 Task 3: Shows next speaker portrait, name, talk title, then auto-dismisses after 5s.
//  Source: docs/watch-app/epic-4-reuse-map.md#Area-1
//
//  Design: NextSessionPeekView in .prominent style + "UP NEXT" header + 5s countdown bar.
//  NO ViewModel, NO findNextSession() — nextSession is passed in from LiveCountdownView.
//

import SwiftUI

struct SessionTransitionView: View {

    let nextSession: WatchSession
    let onDismiss: () -> Void

    /// Fraction of the 5-second timer elapsed — drives the bottom progress bar.
    @State private var progress: Double = 1.0

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VStack(spacing: 0) {
                // "UP NEXT" header
                Text("UP NEXT")
                    .font(.system(size: 9, weight: .semibold))
                    .foregroundStyle(.secondary)
                    .kerning(1.0)
                    .padding(.top, 8)

                Spacer()

                // Speaker portrait + name + title via NextSessionPeekView (prominent)
                NextSessionPeekView(session: nextSession, style: .prominent)
                    .padding(.horizontal, 10)

                Spacer()

                // 5-second countdown progress bar at bottom
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Rectangle()
                            .fill(Color(white: 0.15))
                            .frame(height: 2)
                        Rectangle()
                            .fill(Color.teal)
                            .frame(width: geo.size.width * progress, height: 2)
                            .animation(.linear(duration: 5.0), value: progress)
                    }
                }
                .frame(height: 2)
                .padding(.horizontal, 10)
                .padding(.bottom, 10)
            }
        }
        // W4.2 Task 3.4: Auto-dismiss after 5 seconds.
        // M3 fix (W4.2 code review): do NOT use try? — swallowing CancellationError would
        // call onDismiss() even after the task is cancelled (e.g., parent dismisses cover
        // early). Let cancellation propagate naturally by catching it explicitly.
        .task {
            progress = 0.0
            do {
                try await Task.sleep(nanoseconds: 5_000_000_000)
                onDismiss()
            } catch {
                // CancellationError: parent dismissed fullScreenCover before 5s elapsed — no-op.
            }
        }
    }
}

// MARK: - Previews

#Preview("Session Transition") {
    let speaker = WatchSpeaker(
        id: "marco.organizer",
        firstName: "Marco",
        lastName: "Rossi",
        company: "Tech Corp",
        companyLogoUrl: nil,
        profilePictureUrl: nil,
        bio: nil,
        speakerRole: .primarySpeaker,
        arrived: true
    )
    let session = WatchSession(
        id: "microservices-mistakes",
        title: "Microservices Mistakes You'll Make",
        abstract: nil,
        sessionType: .presentation,
        startTime: Date().addingTimeInterval(300),
        endTime: Date().addingTimeInterval(3000),
        speakers: [speaker],
        state: .scheduled
    )
    SessionTransitionView(nextSession: session, onDismiss: {})
}
