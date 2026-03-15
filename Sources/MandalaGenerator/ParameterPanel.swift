import SwiftUI

struct ParameterPanel: View {
    @EnvironmentObject private var appState: AppState

    var body: some View {
        ScrollView(.vertical, showsIndicators: true) {
            VStack(spacing: 16) {

                // STRUCTURE
                SectionCard(title: "Structure") {
                    VStack(spacing: 10) {
                        HStack {
                            Text("Symmetry").font(.subheadline).foregroundColor(.secondary)
                            Spacer()
                            Stepper("\(appState.parameters.symmetry)×",
                                    value: $appState.parameters.symmetry, in: 1...8)
                            .fixedSize()
                        }
                        HStack {
                            Text("Output Size").font(.subheadline).foregroundColor(.secondary)
                            Spacer()
                            Picker("", selection: $appState.parameters.outputSize) {
                                Text("512").tag(512)
                                Text("800").tag(800)
                                Text("1024").tag(1024)
                                Text("2048").tag(2048)
                            }
                            .pickerStyle(.menu).fixedSize()
                        }
                    }
                }

                // SEED
                SectionCard(title: "Seed") {
                    HStack {
                        TextField("Seed", value: $appState.parameters.seed, format: .number)
                            .textFieldStyle(.roundedBorder)
                            .font(.system(.body, design: .monospaced))
                        Button(action: { appState.randomizeSeed() }) {
                            Image(systemName: "dice").foregroundColor(.blue)
                        }
                        .buttonStyle(.plain)
                        .help("Randomize seed")
                    }
                }

                Divider().padding(.horizontal)

                Button(action: { Task { await appState.generate() } }) {
                    Label("Generate", systemImage: "sparkles")
                        .frame(maxWidth: .infinity).padding(.vertical, 6)
                }
                .buttonStyle(.borderedProminent).tint(.blue)
                .disabled(appState.isGenerating).padding(.horizontal)

                Button(action: { appState.randomizeAll() }) {
                    Label("Randomize All", systemImage: "shuffle")
                        .frame(maxWidth: .infinity).padding(.vertical, 4)
                }
                .buttonStyle(.bordered)
                .disabled(appState.isGenerating).padding(.horizontal).padding(.bottom, 16)
            }
            .padding(.vertical, 12)
        }
        .background(Color(NSColor.controlBackgroundColor))
    }
}

// MARK: - Sub-components (shared)

struct SectionCard<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title.uppercased())
                .font(.system(size: 10, weight: .semibold))
                .foregroundColor(.secondary).kerning(1.2)
                .padding(.horizontal, 16)
            VStack(alignment: .leading, spacing: 0) {
                content().padding(12)
            }
            .background(Color(NSColor.windowBackgroundColor).opacity(0.5))
            .cornerRadius(8).padding(.horizontal, 12)
        }
    }
}

struct LabeledSlider: View {
    let label: String
    @Binding var value: Double
    var color: Color = .blue

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(label).font(.subheadline).foregroundColor(.secondary)
                Spacer()
                Text(String(format: "%.2f", value)).font(.caption).foregroundColor(.secondary).monospacedDigit()
            }
            Slider(value: $value, in: 0...1).accentColor(color)
        }
    }
}

struct PaletteSwatch: View {
    let palette: ColorPalette
    let isSelected: Bool
    var isBlend: Bool = false

    var body: some View {
        VStack(spacing: 2) {
            LinearGradient(
                gradient: Gradient(stops: palette.stops.map {
                    Gradient.Stop(color: Color(nsColor: $0.1), location: $0.0)
                }),
                startPoint: .leading, endPoint: .trailing
            )
            .frame(height: 18)
            .cornerRadius(3)
            .overlay(RoundedRectangle(cornerRadius: 3)
                .stroke(isSelected ? Color.white : isBlend ? Color.orange : Color.clear, lineWidth: 2))

            Text(palette.name)
                .font(.system(size: 8))
                .foregroundColor(isSelected || isBlend ? .primary : .secondary)
                .lineLimit(1)
        }
        .padding(2)
        .background(isSelected ? Color.blue.opacity(0.2) : isBlend ? Color.orange.opacity(0.15) : Color.clear)
        .cornerRadius(5)
        .contentShape(Rectangle())
    }
}
