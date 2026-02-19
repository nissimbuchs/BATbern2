//
//  DelayedSessionView.swift
//  BATbern-watch Watch App
//
//  W4.3 Task 3: Sheet for giving the previous session more time.
//  Presented from LiveCountdownView when shouldShowDelayed == true.
//

import SwiftUI

struct DelayedSessionView: View {

    let currentSlug: String
    let onDelay: (Int) -> Void
    let onDismiss: () -> Void

    @State private var isSending: Bool = false
    private let hapticService: HapticServiceProtocol

    init(
        currentSlug: String,
        onDelay: @escaping (Int) -> Void,
        onDismiss: @escaping () -> Void,
        hapticService: HapticServiceProtocol = WatchHapticService()
    ) {
        self.currentSlug = currentSlug
        self.onDelay = onDelay
        self.onDismiss = onDismiss
        self.hapticService = hapticService
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 8) {
                VStack(spacing: 2) {
                    Text("Give prev session")
                        .font(.system(size: 13, weight: .regular, design: .rounded))
                        .foregroundStyle(.secondary)
                    Text("more time?")
                        .font(.system(size: 13, weight: .regular, design: .rounded))
                        .foregroundStyle(.secondary)
                }
                .padding(.top, 8)

                ForEach([5, 10, 15, 20], id: \.self) { minutes in
                    Button("+\(minutes) min") {
                        hapticService.play(.actionConfirm)
                        isSending = true
                        onDelay(minutes)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.orange)
                    .disabled(isSending)
                }
            }
            .padding(.horizontal, 10)
        }
    }
}
