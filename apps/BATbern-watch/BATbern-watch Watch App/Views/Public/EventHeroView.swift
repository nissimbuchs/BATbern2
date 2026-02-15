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
    @Environment(\.modelContext) private var modelContext
    @State private var viewModel: PublicViewModel?

    var body: some View {
        Group {
            if let vm = viewModel, let event = vm.event {
                // Event Hero (P1)
                eventHeroContent(event: event)
            } else if let vm = viewModel, vm.isLoading {
                // Loading state
                ProgressView()
                    .tint(.white)
            } else {
                // Empty state
                emptyStateContent
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
        .onAppear {
            if viewModel == nil {
                viewModel = PublicViewModel(modelContext: modelContext)
            }
        }
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
                            .overlay(Color.black.opacity(0.6))  // Dimming overlay
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

                // BATbern logo with text (larger for hero visibility)
                BATbernSymbolView(size: 45, color: Color(hex: "#2C5F7C") ?? .blue)
                    .padding(.bottom, 8)

                // Event title (large, centered, white, wrapped) - flexible space
                Text(event.title)
                    .font(.system(.title3, design: .rounded, weight: .semibold))
                    .foregroundStyle(.white)
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
                        if event.currentPublishedPhase == "SPEAKERS" || event.currentPublishedPhase == "AGENDA" {
                            Text("·")
                            Text(SwissDateFormatter.formatTimeString(event.typicalStartTime))
                                .font(.caption2)
                        }
                    }

                    Text(event.venueName)
                        .font(.caption2)
                        .lineLimit(2)
                        .multilineTextAlignment(.center)
                }
                .foregroundStyle(.secondary)

                Spacer(minLength: 16)

                // Scroll affordance (localized)
                if !event.sessions.isEmpty {
                    Text(NSLocalizedString("event.hero.scroll_hint", comment: "Scroll hint"))
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                        .padding(.top, 4)
                }
            }
            .padding()
        }
    }

    // MARK: - Empty State

    private var emptyStateContent: some View {
        VStack(spacing: 12) {
            BATbernSymbolView(size: 32, color: Color(hex: "#2C5F7C") ?? .blue)

            Text(NSLocalizedString("event.hero.empty.title", comment: "App title"))
                .font(.system(size: 14, design: .rounded))
                .foregroundStyle(Color(hex: "#2C5F7C") ?? .blue)

            Text(NSLocalizedString("event.hero.empty.message", comment: "Empty state message"))
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
    }
}

#Preview("Event Loaded") {
    EventHeroView()
        .modelContainer(for: [CachedEvent.self], inMemory: true)
}

#Preview("Empty State") {
    EventHeroView()
        .modelContainer(for: [CachedEvent.self], inMemory: true)
}
