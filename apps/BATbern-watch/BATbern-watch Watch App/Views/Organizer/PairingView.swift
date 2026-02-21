//
//  PairingView.swift
//  BATbern-watch Watch App
//
//  O1: 6-digit pairing code entry with Digital Crown navigation.
//  W2.2: AC1 (Crown-scroll picker), AC2 (success haptic), AC3 (error handling).
//  Source: docs/watch-app/ux-design-specification.md#Pairing-Authentication-UX
//

import SwiftUI
import WatchKit

struct PairingView: View {
    @Environment(AuthManager.self) private var authManager

    @State private var digits: [Int] = Array(repeating: 0, count: 6)
    @State private var focusedDigit: Int = 0
    @State private var isPairing: Bool = false
    @State private var errorMessage: String?
    @FocusState private var crownFocused: Bool

    var body: some View {
        // No ScrollView: ScrollView owns the Crown sequencer and conflicts with
        // .digitalCrownRotation on child views, causing the "Crown Sequencer set
        // up without a view property" warning and broken Crown input.
        // The content fits on all watch sizes because the button is in safeAreaInset.
        VStack(spacing: 8) {
            Text(NSLocalizedString("pairing.title", comment: "Pairing screen title"))
                .font(.headline)

            // 6-digit display with Crown-scroll interaction
            digitPicker

            Text(NSLocalizedString("pairing.instructions", comment: "Crown/tap instructions"))
                .font(.caption2)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)

            if let errorMessage {
                Text(errorMessage)
                    .font(.caption2)
                    .foregroundStyle(.red)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(.horizontal)
        .padding(.top, 4)
        // Pin button to bottom so it is always visible regardless of screen size.
        .safeAreaInset(edge: .bottom, spacing: 4) {
            Button(NSLocalizedString("pairing.button", comment: "Pair button")) {
                Task { await pairWatch() }
            }
            .buttonStyle(.borderedProminent)
            .padding(.horizontal)
            .disabled(isPairing)
        }
    }

    // MARK: - Digit Picker

    private var digitPicker: some View {
        HStack(spacing: 4) {
            ForEach(0..<6, id: \.self) { index in
                digitCell(index: index)
            }
        }
        .focusable()
        .focused($crownFocused)
        .digitalCrownRotation(
            Binding(
                get: { Double(digits[focusedDigit]) },
                set: { newValue in
                    let clamped = max(0, min(9, Int(newValue.rounded())))
                    digits[focusedDigit] = clamped
                }
            ),
            from: 0,
            through: 9,
            by: 1,
            sensitivity: .medium,
            isContinuous: false
        )
        .onTapGesture {
            if crownFocused {
                // AC1: Already focused — tap advances to next digit
                focusedDigit = (focusedDigit + 1) % 6
                WKInterfaceDevice.current().play(.click)
            } else {
                // Not focused (e.g. scrolled away): reclaim Crown without advancing
                crownFocused = true
            }
        }
        .onAppear {
            // Auto-claim Crown focus so the first digit is live immediately,
            // without requiring an initial tap.
            Task { @MainActor in crownFocused = true }
        }
    }

    private func digitCell(index: Int) -> some View {
        Text("\(digits[index])")
            .font(.system(size: 22, weight: .bold, design: .monospaced))
            .frame(width: 22, height: 30)
            .background(
                RoundedRectangle(cornerRadius: 4)
                    .fill(focusedDigit == index ? Color.blue.opacity(0.3) : Color.clear)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 4)
                    .stroke(focusedDigit == index ? Color.blue : Color.secondary.opacity(0.4), lineWidth: 1)
            )
    }

    // MARK: - Pairing Action

    @MainActor
    private func pairWatch() async {
        guard !isPairing else { return }  // Prevent duplicate calls from rapid double-taps
        isPairing = true
        errorMessage = nil

        let code = digits.map { String($0) }.joined()

        do {
            try await authManager.pair(code: code)
            // AC2: Success haptic — organizer zone loads automatically (isPaired → true)
            WKInterfaceDevice.current().play(.success)
        } catch {
            // AC3: Error message — preserve entered code for retry
            errorMessage = NSLocalizedString("pairing.error", comment: "Pairing error message")
            WKInterfaceDevice.current().play(.failure)
        }

        isPairing = false
    }
}

#Preview {
    PairingView()
        .environment(AuthManager())
}
