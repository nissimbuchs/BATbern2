//
//  EventHeroView.swift
//  BATbern-watch Watch App
//
//  Event hero screen (P1) showing current event details or empty state.
//  Uses Swiss German (de_CH) locale for all date/time formatting.
//  Source: docs/watch-app/ux-design-specification.md#Event-Hero-Screen
//

import SwiftUI
import SwiftData

struct EventHeroView: View {
    @Environment(EventDataController.self) private var eventDataController

    var body: some View {
        Group {
            if let event = eventDataController.currentEvent {
                // Event Hero (P1)
                eventHeroContent(event: event)
            } else if eventDataController.isLoading {
                // Loading state
                BATbernSpinnerView(size: 44, speed: .normal)
            } else {
                // Empty state
                emptyStateContent
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
    }

    // MARK: - Event Hero Content

    @ViewBuilder
    private func eventHeroContent(event: CachedEvent) -> some View {
        ZStack {
            // Theme image background (dimmed for readability)
            if let themeUrl = event.themeImageUrl, let url = URL(string: themeUrl) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            // AC#7: Bottom-heavy gradient — image visible at top, text readable at bottom
                            .overlay(
                                LinearGradient(
                                    stops: [
                                        .init(color: .black.opacity(0.3), location: 0.0),
                                        .init(color: .black.opacity(0.5), location: 0.4),
                                        .init(color: .black.opacity(0.85), location: 1.0)
                                    ],
                                    startPoint: .top,
                                    endPoint: .bottom
                                )
                            )
                    case .failure, .empty:
                        Color.black
                    @unknown default:
                        Color.black
                    }
                }
                .ignoresSafeArea()
            } else {
                Color.black
                    .ignoresSafeArea()
            }

            // Foreground content
            VStack(spacing: 0) {
                Spacer()

                // BATbern animated spinner (larger for hero visibility)
                BATbernSpinnerView(size: 45, speed: .slow)
                    .padding(.bottom, 8)

                // Event title (large, centered, white, wrapped) - flexible space
                Text(event.title)
                    .font(BATbernWatchStyle.Typography.heroTitle)
                    .foregroundStyle(BATbernWatchStyle.Colors.textPrimary)
                    .multilineTextAlignment(.center)
                    .lineLimit(5)
                    .minimumScaleFactor(0.7)
                    .padding(.horizontal, 8)
                    .frame(maxWidth: .infinity)

                Spacer(minLength: 16)

                // Bottom info bar: date + time (phase-aware) + venue (Swiss German format)
                VStack(spacing: 4) {
                    HStack(spacing: 4) {
                        Text(SwissDateFormatter.formatEventDate(event.eventDate))
                            .font(.caption2)

                        // Time range: only show in SPEAKERS and AGENDA phases (AC#1, AC#2, AC#3)
                        if event.currentPublishedPhase == "SPEAKERS" || event.currentPublishedPhase == "AGENDA",
                           let firstStart = event.sessions.compactMap({ $0.startTime }).min() {
                            Text("·")
                            Text(SwissDateFormatter.formatEventTime(firstStart))
                                .font(.caption2)
                        }
                    }

                    Text(event.venueName)
                        .font(.caption2)
                        .lineLimit(2)
                        .multilineTextAlignment(.center)
                }
                .foregroundStyle(.secondary)

                // AC#6: Scroll hint removed — .verticalPage TabView provides native page indicator dots
            }
            .padding()
        }
    }

    // MARK: - Empty State

    private var emptyStateContent: some View {
        VStack(spacing: 12) {
            BATbernSpinnerView(size: 32, speed: .slow)

            Text(NSLocalizedString("event.hero.empty.title", comment: "App title"))
                .font(.system(size: 14, design: .rounded))
                .foregroundStyle(BATbernWatchStyle.Colors.batbernBlue)

            Text(NSLocalizedString("event.hero.empty.message", comment: "Empty state message"))
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
    }
}

#Preview("Event Loaded") {
    let auth = AuthManager()
    let container = try! ModelContainer(for: CachedEvent.self)
    let controller = EventDataController(
        authManager: auth,
        modelContext: container.mainContext
    )
    EventHeroView()
        .modelContainer(container)
        .environment(controller)
}

#Preview("Empty State") {
    let auth = AuthManager()
    let container = try! ModelContainer(for: CachedEvent.self)
    let controller = EventDataController(
        authManager: auth,
        modelContext: container.mainContext
    )
    EventHeroView()
        .modelContainer(container)
        .environment(controller)
}
