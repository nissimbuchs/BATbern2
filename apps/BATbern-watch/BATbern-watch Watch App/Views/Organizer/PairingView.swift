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

    var body: some View {
        ScrollView {
            VStack(spacing: 10) {
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

                Button(NSLocalizedString("pairing.button", comment: "Pair button")) {
                    Task { await pairWatch() }
                }
                .buttonStyle(.borderedProminent)
                .disabled(isPairing)
            }
            .padding()
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
            // AC1: Tap advances to next digit
            let next = (focusedDigit + 1) % 6
            focusedDigit = next
            WKInterfaceDevice.current().play(.click)
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
