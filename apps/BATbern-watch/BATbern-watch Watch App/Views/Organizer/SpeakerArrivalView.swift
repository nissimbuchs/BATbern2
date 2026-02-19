//  SpeakerArrivalView.swift
//  Views/Organizer/SpeakerArrivalView.swift
//
//  O2: Speaker portrait overview with arrival tracking.
//  W2.4: FR36-FR39.
//  Source: docs/watch-app/ux-design-specification.md#Speaker-Portrait-Overview-(O2)

import SwiftUI
import SwiftData

struct SpeakerArrivalView: View {
    @Environment(ArrivalTracker.self) private var arrivalTracker
    @Environment(EventStateManager.self) private var eventState
    @Query private var speakers: [CachedSpeaker]

    @State private var selectedSpeaker: CachedSpeaker?

    /// Deduplicates by username — same speaker appears once per session in SwiftData.
    /// Prefers the copy with `arrived = true` so the badge renders immediately after confirmation.
    private var uniqueSpeakers: [CachedSpeaker] {
        var bestByUsername: [String: CachedSpeaker] = [:]
        for speaker in speakers {
            if let existing = bestByUsername[speaker.username] {
                if speaker.arrived && !existing.arrived {
                    bestByUsername[speaker.username] = speaker
                }
            } else {
                bestByUsername[speaker.username] = speaker
            }
        }
        return Array(bestByUsername.values).sorted { $0.lastName < $1.lastName }
    }

    var body: some View {
        GeometryReader { geo in
            let portraitSize = Self.cellPortraitSize(screenWidth: geo.size.width)
            ScrollView {
                VStack(spacing: 8) {
                    // Arrival counter header (AC1, FR39)
                    arrivalCounterHeader

                    // Portrait grid (2 columns, AC1)
                    LazyVGrid(
                        columns: [
                            GridItem(.flexible(), spacing: 8),
                            GridItem(.flexible(), spacing: 8)
                        ],
                        spacing: 8
                    ) {
                        ForEach(uniqueSpeakers) { speaker in
                            SpeakerPortraitCell(speaker: speaker, portraitSize: portraitSize)
                                .onTapGesture {
                                    selectedSpeaker = speaker
                                }
                        }
                    }

                    // Event start reminder — use first session's real start time if available.
                    if let event = eventState.currentEvent {
                        let firstStart = event.sessions.compactMap(\.startTime).min()
                        let startLabel = firstStart.map { SwissDateFormatter.formatEventTime($0) }
                            ?? SwissDateFormatter.formatTimeString(event.typicalStartTime)
                        Text(
                            NSLocalizedString("arrival.event_starts_at", comment: "")
                                + " "
                                + startLabel
                        )
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .padding(.top, 4)
                    }
                }
                .padding(.horizontal, 4)
            }
        }
        // Confirmation sheet (AC2, AC5)
        .sheet(item: $selectedSpeaker) { speaker in
            ArrivalConfirmationView(
                speaker: speaker,
                arrivalTracker: arrivalTracker as any ArrivalTrackerProtocol
            )
        }
        .navigationTitle(NSLocalizedString("arrival.tonight_speakers", comment: ""))
        .navigationBarTitleDisplayMode(.inline)
        .task {
            // Start WebSocket listener when view appears
            if let eventCode = eventState.currentEvent?.eventCode {
                await arrivalTracker.startListening(eventCode: eventCode)
            }
        }
        .onDisappear {
            arrivalTracker.stopListening()
        }
    }

    // MARK: - Helpers

    /// Computes the portrait circle diameter so cells fill the screen without overflow.
    /// Two columns with 8pt column spacing and 4pt horizontal padding each side.
    private static func cellPortraitSize(screenWidth: CGFloat) -> CGFloat {
        let cellWidth = (screenWidth - 8 - 8) / 2   // 8 = column spacing, 8 = total horizontal padding
        let cellPadding: CGFloat = 12                // 6pt padding each side inside the cell
        return min(cellWidth - cellPadding, 52)
    }

    // MARK: - Subviews

    private var arrivalCounterHeader: some View {
        HStack {
            Image(systemName: "checkmark.circle.fill")
                .foregroundStyle(.green)
                .font(.caption)
            Text(
                String(
                    format: NSLocalizedString("arrival.counter_format", comment: ""),
                    arrivalTracker.arrivedCount,
                    arrivalTracker.totalCount
                )
            )
            .font(.caption)
            .fontWeight(.semibold)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 4)
    }
}

// MARK: - Speaker Portrait Cell

struct SpeakerPortraitCell: View {
    let speaker: CachedSpeaker
    let portraitSize: CGFloat

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            // Portrait + name — company logo hidden to prevent overflow on small watches.
            // Logo is visible in ArrivalConfirmationView (full-width sheet).
            SpeakerPortraitView(speaker: speaker, size: portraitSize, showCompanyLogo: false)
                .padding(6)
                .background(
                    RoundedRectangle(cornerRadius: 8)
                        .fill(speaker.arrived
                            ? Color.green.opacity(0.1)
                            : Color.gray.opacity(0.15)
                        )
                )

            // Green checkmark badge (AC3)
            if speaker.arrived {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(.green)
                    .background(Circle().fill(.black))
                    .offset(x: 4, y: 4)
            }
        }
    }
}

// MARK: - Confirmation Sheet

struct ArrivalConfirmationView: View {
    let speaker: CachedSpeaker
    let arrivalTracker: any ArrivalTrackerProtocol

    @Environment(\.dismiss) private var dismiss
    @State private var isConfirming = false

    var body: some View {
        VStack(spacing: 12) {
            // Portrait
            SpeakerPortraitView(speaker: speaker, size: 44)

            if speaker.arrived {
                // AC5: Already confirmed — show who confirmed (no duplicate action)
                alreadyConfirmedView
            } else {
                // AC2: Confirmation prompt
                confirmationPromptView
            }
        }
        .padding()
    }

    @ViewBuilder
    private var alreadyConfirmedView: some View {
        VStack(spacing: 8) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 28))
                .foregroundStyle(.green)

            Text(speaker.fullName)
                .font(.headline)
                .multilineTextAlignment(.center)

            if let confirmedBy = speaker.arrivedConfirmedBy, !confirmedBy.isEmpty {
                Text(
                    String(
                        format: NSLocalizedString("arrival.confirmed_by_format", comment: ""),
                        confirmedBy
                    )
                )
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            }

            Button(NSLocalizedString("arrival.close", comment: "")) {
                dismiss()
            }
            .buttonStyle(.plain)
            .font(.caption)
        }
    }

    @ViewBuilder
    private var confirmationPromptView: some View {
        VStack(spacing: 8) {
            Text(
                String(
                    format: NSLocalizedString("arrival.confirm_question_format", comment: ""),
                    speaker.firstName
                )
            )
            .font(.headline)
            .multilineTextAlignment(.center)

            // "Arrived ✓" button — Text only; the ✓ in the localized string is the indicator
            Button {
                Task {
                    isConfirming = true
                    try? await arrivalTracker.confirmArrival(speaker: speaker)
                    isConfirming = false
                    dismiss()
                }
            } label: {
                Text(NSLocalizedString("arrival.confirm_arrived", comment: ""))
                    .font(.caption)
            }
            .buttonStyle(.borderedProminent)
            .tint(.green)
            .disabled(isConfirming)

            // "Not yet" button
            Button(NSLocalizedString("arrival.not_yet", comment: "")) {
                dismiss()
            }
            .buttonStyle(.plain)
            .font(.caption)
            .foregroundStyle(.secondary)
        }
    }
}

// Note: @Environment(ArrivalTracker.self) binds to concrete type — SwiftUI limitation;
// protocol injection via EnvironmentKey would require more boilerplate than it saves.
#Preview {
    let auth = AuthManager()
    let container = try! ModelContainer(for: CachedSpeaker.self)
    let controller = EventDataController(
        authManager: auth,
        modelContext: container.mainContext,
        skipAutoSync: true
    )
    NavigationStack {
        SpeakerArrivalView()
    }
    .environment(ArrivalTracker(
        authManager: auth,
        modelContext: container.mainContext
    ))
    .environment(controller)
    .environment(EventStateManager(eventDataController: controller))
}
