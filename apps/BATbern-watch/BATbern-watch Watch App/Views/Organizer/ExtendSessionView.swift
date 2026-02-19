//
//  ExtendSessionView.swift
//  BATbern-watch Watch App
//
//  W4.3 Task 2: Sheet for extending the current session's end time.
//  Presented from LiveCountdownView when shouldShowExtend == true.
//

import SwiftUI

struct ExtendSessionView: View {

    let sessionSlug: String
    let onExtend: (Int) -> Void
    let onDismiss: () -> Void

    @State private var isSending: Bool = false
    private let hapticService: HapticServiceProtocol

    init(
        sessionSlug: String,
        onExtend: @escaping (Int) -> Void,
        onDismiss: @escaping () -> Void,
        hapticService: HapticServiceProtocol = WatchHapticService()
    ) {
        self.sessionSlug = sessionSlug
        self.onExtend = onExtend
        self.onDismiss = onDismiss
        self.hapticService = hapticService
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 8) {
                Text("Extend session?")
                    .font(.system(size: 14, weight: .regular, design: .rounded))
                    .foregroundStyle(.secondary)
                    .padding(.top, 8)

                ForEach([5, 10, 15, 20], id: \.self) { minutes in
                    Button("+\(minutes) min") {
                        hapticService.play(.actionConfirm)
                        isSending = true
                        onExtend(minutes)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.blue)
                    .disabled(isSending)
                }
            }
            .padding(.horizontal, 10)
        }
    }
}
