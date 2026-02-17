//
//  LiveCountdownView.swift
//  BATbern-watch Watch App
//
//  O3: Live countdown timer — placeholder for W3.1.
//  W2.2: Ensures navigation works end-to-end before W3.1 implements full view.
//

import SwiftUI

struct LiveCountdownView: View {
    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: "timer")
                .font(.system(size: 32))
                .foregroundStyle(.secondary)

            Text("Live Countdown")
                .font(.headline)

            Text("Story W3.1")
                .font(.caption2)
                .foregroundStyle(.tertiary)
        }
        .padding()
    }
}

#Preview {
    LiveCountdownView()
}
