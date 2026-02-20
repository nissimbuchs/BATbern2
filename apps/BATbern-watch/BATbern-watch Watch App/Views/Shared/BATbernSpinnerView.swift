//
//  BATbernSpinnerView.swift
//  BATbern-watch Watch App
//
//  Custom loading spinner using the BATbern logo — two arrows spinning together
//  in a rhythmic pause → spin → pause cycle, matching the web-frontend BATbernLoader.
//
//  Arrow paths converted from the SVG in web-frontend/src/components/shared/BATbernLoader.tsx.
//  ViewBox: "0 -1 100 100" — all coordinates are in that 100×100 space.
//
//  Cycle: pause for dur1 (logo position) → spin for dur2 = 2×dur1 → repeat.
//  Arrow 1 (light blue): 2 full rotations (720°) per spin phase.
//  Arrow 2 (dark blue):  1 full rotation  (360°) per spin phase.
//  Both arrows ease-in-out during the spin and return to logo position on each pause.
//

import SwiftUI

// MARK: - Speed

extension BATbernSpinnerView {
    enum Speed {
        case slow    // pauseDur = 1.6s, spinDur = 3.2s, cycle = 4.8s
        case normal  // pauseDur = 1.0s, spinDur = 2.0s, cycle = 3.0s
        case fast    // pauseDur = 0.6s, spinDur = 1.2s, cycle = 1.8s

        /// Length of the still pause (arrows at logo position).
        var pauseDur: Double {
            switch self {
            case .slow:   1.6
            case .normal: 1.0
            case .fast:   0.6
            }
        }

        /// Length of the spin phase — exactly 2 × pauseDur.
        var spinDur: Double { pauseDur * 2 }

        /// Full cycle duration: pause + spin = 3 × pauseDur.
        var cycleDur: Double { pauseDur * 3 }
    }
}

// MARK: - Public View

/// Drops in wherever a loading indicator is needed.
/// Defaults to 44pt — override with `.frame` or the `size` parameter.
struct BATbernSpinnerView: View {
    let size: CGFloat
    let speed: Speed

    init(size: CGFloat = 44, speed: Speed = .normal) {
        self.size = size
        self.speed = speed
    }

    var body: some View {
        TimelineView(.animation) { timeline in
            let t = timeline.date.timeIntervalSinceReferenceDate

            // Position within the current cycle (0 …cycleDur).
            let cyclePos = t.truncatingRemainder(dividingBy: speed.cycleDur)

            // Elapsed time inside the spin phase (0 during the pause, then 0…spinDur).
            let spinElapsed = max(0.0, cyclePos - speed.pauseDur)

            // Normalised spin progress 0…1 with ease-in-out matching the CSS animation.
            let raw = min(spinElapsed / speed.spinDur, 1.0)
            let eased = raw < 0.5 ? 2 * raw * raw : -1 + (4 - 2 * raw) * raw

            // Arrow 1 (light blue #3498DB): 2 full rotations per spin phase.
            let a1 = eased * 720.0
            // Arrow 2 (dark blue #1a6fa8): 1 full rotation per spin phase.
            let a2 = eased * 360.0

            ZStack {
                BATbernArrow1Shape()
                    .fill(Color(red: 0.204, green: 0.596, blue: 0.859))
                    .rotationEffect(.degrees(a1), anchor: UnitPoint(x: 0.356, y: 0.534))

                BATbernArrow2Shape()
                    .fill(Color(red: 0.102, green: 0.435, blue: 0.659))
                    .rotationEffect(.degrees(a2), anchor: UnitPoint(x: 0.643, y: 0.476))
            }
            .frame(width: size, height: size)
        }
    }
}

// MARK: - Arrow Shapes

/// Upper arrow (lighter blue). Rotates around its lower-centre (~50% x, 87% y of its bbox).
/// Pivot in view-space: UnitPoint(x: 0.356, y: 0.534)
private struct BATbernArrow1Shape: Shape {
    func path(in rect: CGRect) -> Path {
        // Scale SVG coords (viewBox "0 -1 100 100") to the view rect.
        // x' = x * sx,  y' = (y + 1) * sy
        let sx = rect.width  / 100.0
        let sy = rect.height / 100.0
        func p(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: x * sx, y: (y + 1) * sy) }

        var path = Path()
        path.move(to: p(35.822, 21.061))
        path.addCurve(to: p(58.166, 30.166), control1: p(44.699, 21.322), control2: p(52.100, 24.173))
        path.addCurve(to: p(61.362, 30.844), control1: p(59.186, 31.173), control2: p(60.028, 31.549))
        path.addCurve(to: p(64.946, 29.384), control1: p(62.497, 30.244), control2: p(63.762, 29.896))
        path.addCurve(to: p(66.399, 30.470), control1: p(66.116, 28.878), control2: p(66.633, 28.963))
        path.addCurve(to: p(64.318, 44.881), control1: p(65.655, 35.266), control2: p(65.009, 40.077))
        path.addCurve(to: p(63.382, 51.262), control1: p(64.012, 47.009), control2: p(63.671, 49.132))
        path.addCurve(to: p(61.957, 51.882), control1: p(63.239, 52.317), control2: p(62.828, 52.571))
        path.addCurve(to: p(45.153, 38.620), control1: p(56.359, 47.457), control2: p(50.764, 43.027))
        path.addCurve(to: p(45.473, 37.141), control1: p(44.151, 37.833), control2: p(44.620, 37.478))
        path.addCurve(to: p(48.380, 35.969), control1: p(46.445, 36.757), control2: p(47.414, 36.367))
        path.addCurve(to: p(49.612, 35.071), control1: p(48.869, 35.767), control2: p(49.594, 35.720))
        path.addCurve(to: p(48.595, 34.090), control1: p(49.626, 34.567), control2: p(48.990, 34.365))
        path.addCurve(to: p(24.061, 33.931), control1: p(41.463, 29.120), control2: p(31.487, 29.017))
        path.addCurve(to: p(13.917, 52.040), control1: p(17.596, 38.210), control2: p(14.359, 44.369))
        path.addCurve(to: p( 9.274, 57.127), control1: p(13.737, 55.171), control2: p(11.975, 57.165))
        path.addCurve(to: p( 4.747, 51.685), control1: p( 6.581, 57.089), control2: p( 4.686, 54.811))
        path.addCurve(to: p(32.371, 21.415), control1: p( 5.046, 36.348), control2: p(17.004, 23.240))
        path.addCurve(to: p(35.822, 21.061), control1: p(33.651, 21.262), control2: p(34.936, 21.151))
        path.closeSubpath()
        return path
    }
}

/// Lower arrow (darker blue). Rotates around its upper-centre (~50% x, 16% y of its bbox).
/// Pivot in view-space: UnitPoint(x: 0.643, y: 0.476)
private struct BATbernArrow2Shape: Shape {
    func path(in rect: CGRect) -> Path {
        let sx = rect.width  / 100.0
        let sy = rect.height / 100.0
        func p(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: x * sx, y: (y + 1) * sy) }

        var path = Path()
        path.move(to: p(63.149, 76.870))
        path.addCurve(to: p(41.776, 67.795), control1: p(55.233, 76.664), control2: p(47.859, 73.745))
        path.addCurve(to: p(38.579, 67.147), control1: p(40.743, 66.785), control2: p(39.897, 66.446))
        path.addCurve(to: p(35.164, 68.531), control1: p(37.500, 67.720), control2: p(36.288, 68.035))
        path.addCurve(to: p(33.542, 67.347), control1: p(33.882, 69.096), control2: p(33.313, 68.854))
        path.addCurve(to: p(35.487, 54.222), control1: p(34.207, 62.974), control2: p(34.844, 58.598))
        path.addCurve(to: p(36.457, 47.474), control1: p(35.817, 51.974), control2: p(36.141, 49.724))
        path.addCurve(to: p(38.594, 46.530), control1: p(36.753, 45.369), control2: p(36.975, 45.255))
        path.addCurve(to: p(54.388, 58.982), control1: p(43.862, 50.676), control2: p(49.105, 54.854))
        path.addCurve(to: p(54.155, 60.976), control1: p(55.527, 59.872), control2: p(55.824, 60.457))
        path.addCurve(to: p(51.414, 62.059), control1: p(53.220, 61.267), control2: p(52.343, 61.744))
        path.addCurve(to: p(51.273, 63.823), control1: p(49.933, 62.562), control2: p(50.232, 63.136))
        path.addCurve(to: p(68.535, 67.142), control1: p(56.569, 67.316), control2: p(62.325, 68.413))
        path.addCurve(to: p(85.969, 46.267), control1: p(77.915, 65.222), control2: p(85.812, 56.500))
        path.addCurve(to: p(90.358, 40.876), control1: p(86.019, 43.028), control2: p(87.736, 41.018))
        path.addCurve(to: p(95.143, 45.585), control1: p(93.041, 40.731), control2: p(95.069, 42.727))
        path.addCurve(to: p(73.509, 75.290), control1: p(95.480, 58.608), control2: p(86.407, 71.079))
        path.addCurve(to: p(63.149, 76.870), control1: p(70.331, 76.326), control2: p(67.061, 76.839))
        path.closeSubpath()
        return path
    }
}

// MARK: - Preview

#Preview {
    VStack(spacing: 20) {
        BATbernSpinnerView(size: 60, speed: .slow)
        BATbernSpinnerView(size: 60, speed: .normal)
        BATbernSpinnerView(size: 60, speed: .fast)
        BATbernSpinnerView(size: 28, speed: .normal)
    }
    .padding()
}
