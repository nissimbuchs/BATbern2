//
//  EventHeroView.swift
//  BATbern-watch Watch App
//
//  Event hero screen (P1) showing current event details or empty state.
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
            VStack(spacing: 8) {
                Spacer()

                // BATbern symbol mark (~20pt, BATbern Blue)
                BATbernSymbolView(size: 20, color: Color(hex: "#2C5F7C") ?? .blue)

                // Event title (large, centered, white)
                Text(event.title)
                    .font(.system(.title3, design: .rounded, weight: .semibold))
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)

                Spacer()

                // Bottom info bar: date + time + venue
                HStack(spacing: 4) {
                    Text(event.eventDate, style: .date)
                        .font(.caption2)
                    Text("·")
                    Text(event.typicalStartTime)
                        .font(.caption2)
                    Text("·")
                    Text(event.venueName)
                        .font(.caption2)
                        .lineLimit(1)
                }
                .foregroundStyle(.secondary)

                // Scroll affordance
                if !event.sessions.isEmpty {
                    Text("▼ Scroll for program")
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

            Text("BATbern")
                .font(.system(size: 14, design: .rounded))
                .foregroundStyle(Color(hex: "#2C5F7C") ?? .blue)

            Text("No upcoming BATbern event")
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
