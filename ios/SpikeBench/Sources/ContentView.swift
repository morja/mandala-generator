import SwiftUI

/// One screen: start buttons, the latest picture and the measured times.
struct ContentView: View {
    @StateObject private var bench = Bench()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("SPIKE-3 · Kotlin/Native core").font(.headline)
                Text(bench.status).font(.caption).foregroundStyle(.secondary)
                HStack {
                    Button("Run all") { bench.start(threads: nil) }
                    Button("Run, 1 thread") { bench.start(threads: 1) }
                }
                .buttonStyle(.borderedProminent)
                .disabled(bench.running)
                if let image = bench.image {
                    Image(decorative: image, scale: 1)
                        .resizable()
                        .aspectRatio(1, contentMode: .fit)
                }
                Text(bench.lines.joined(separator: "\n"))
                    .font(.system(.caption2, design: .monospaced))
                    .textSelection(.enabled)
            }
            .padding()
        }
        .onAppear {
            // Keep the screen on: the phone must not lock during a run of several minutes.
            UIApplication.shared.isIdleTimerDisabled = true
            if ProcessInfo.processInfo.arguments.contains("-autorun") { bench.start(threads: nil) }
        }
    }
}
