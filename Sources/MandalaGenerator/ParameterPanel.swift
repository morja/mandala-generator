import SwiftUI

struct ParameterPanel: View {
    @EnvironmentObject private var appState: AppState

    var body: some View {
        ScrollView(.vertical, showsIndicators: true) {
            VStack(spacing: 16) {
                // PRESET Section
                SectionCard(title: "Preset") {
                    Picker("Style", selection: $appState.parameters.style) {
                        ForEach(MandalaStyle.allCases) { style in
                            Label(style.displayName, systemImage: style.sfSymbol)
                                .tag(style)
                        }
                    }
                    .pickerStyle(.menu)
                    .labelsHidden()
                }

                // PALETTE Section
                SectionCard(title: "Palette") {
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                        ForEach(Array(ColorPalettes.all.enumerated()), id: \.offset) { index, palette in
                            PaletteSwatch(palette: palette, isSelected: appState.parameters.paletteIndex == index)
                                .onTapGesture {
                                    appState.parameters.paletteIndex = index
                                }
                        }
                    }
                }

                // ABSTRACT Section
                SectionCard(title: "Abstract") {
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text("Crisp")
                                .font(.caption)
                                .foregroundColor(.secondary)
                            Spacer()
                            Text("Painted")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        Slider(value: $appState.parameters.abstractLevel, in: 0...1)
                            .accentColor(.purple)
                        Text(String(format: "%.2f", appState.parameters.abstractLevel))
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .frame(maxWidth: .infinity, alignment: .trailing)
                    }
                }

                // FORM Section
                SectionCard(title: "Form") {
                    VStack(spacing: 10) {
                        LabeledSlider(label: "Complexity", value: $appState.parameters.complexity)
                        LabeledSlider(label: "Density",    value: $appState.parameters.density)
                        LabeledSlider(label: "Glow",       value: $appState.parameters.glowIntensity)
                        LabeledSlider(label: "Color Drift",value: $appState.parameters.colorDrift)
                    }
                }

                // COLOUR Section
                SectionCard(title: "Colour") {
                    VStack(spacing: 10) {
                        LabeledSlider(label: "Saturation", value: $appState.parameters.saturation, color: .pink)
                        LabeledSlider(label: "Brightness", value: $appState.parameters.brightness, color: .yellow)
                    }
                }

                // DISTORTION Section
                SectionCard(title: "Distortion") {
                    VStack(spacing: 10) {
                        VStack(alignment: .leading, spacing: 2) {
                            HStack {
                                Text("Ripple")
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                                Spacer()
                                Text(String(format: "%.2f", appState.parameters.ripple))
                                    .font(.caption).foregroundColor(.secondary).monospacedDigit()
                            }
                            Slider(value: $appState.parameters.ripple, in: 0...1)
                                .accentColor(.cyan)
                            Text("Sine-wave distortion on curve points")
                                .font(.system(size: 9)).foregroundColor(.secondary)
                        }
                        Divider()
                        VStack(alignment: .leading, spacing: 2) {
                            HStack {
                                Text("Wash")
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                                Spacer()
                                Text(String(format: "%.2f", appState.parameters.wash))
                                    .font(.caption).foregroundColor(.secondary).monospacedDigit()
                            }
                            Slider(value: $appState.parameters.wash, in: 0...1)
                                .accentColor(.teal)
                            Text("Watercolour colour bleed on base layer")
                                .font(.system(size: 9)).foregroundColor(.secondary)
                        }
                    }
                }

                // STRUCTURE Section
                SectionCard(title: "Structure") {
                    VStack(spacing: 10) {
                        HStack {
                            Text("Symmetry")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                            Spacer()
                            Stepper("\(appState.parameters.symmetry)×",
                                    value: $appState.parameters.symmetry,
                                    in: 1...8)
                            .fixedSize()
                        }

                        HStack {
                            Text("Output Size")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                            Spacer()
                            Picker("", selection: $appState.parameters.outputSize) {
                                Text("512").tag(512)
                                Text("800").tag(800)
                                Text("1024").tag(1024)
                                Text("2048").tag(2048)
                            }
                            .pickerStyle(.menu)
                            .fixedSize()
                        }
                    }
                }

                // SEED Section
                SectionCard(title: "Seed") {
                    HStack {
                        TextField("Seed", value: $appState.parameters.seed, format: .number)
                            .textFieldStyle(.roundedBorder)
                            .font(.system(.body, design: .monospaced))

                        Button(action: { appState.randomizeSeed() }) {
                            Image(systemName: "dice")
                                .foregroundColor(.blue)
                        }
                        .buttonStyle(.plain)
                        .help("Randomize seed")
                    }
                }

                Divider().padding(.horizontal)

                // Generate button
                Button(action: {
                    Task { await appState.generate() }
                }) {
                    Label("Generate", systemImage: "sparkles")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                }
                .buttonStyle(.borderedProminent)
                .tint(.blue)
                .disabled(appState.isGenerating)
                .padding(.horizontal)

                // Randomize All button
                Button(action: {
                    appState.randomizeSeed()
                    appState.parameters.paletteIndex = Int.random(in: 0..<ColorPalettes.all.count)
                    appState.parameters.style = MandalaStyle.allCases.randomElement() ?? .mixed
                    appState.parameters.complexity   = Double.random(in: 0.2...1.0)
                    appState.parameters.density      = Double.random(in: 0.2...1.0)
                    appState.parameters.colorDrift   = Double.random(in: 0.1...0.9)
                    appState.parameters.symmetry     = Int.random(in: 1...8)
                    appState.parameters.ripple        = Double.random(in: 0.0...0.7)
                    appState.parameters.wash          = Double.random(in: 0.0...0.6)
                    appState.parameters.abstractLevel = Double.random(in: 0.1...0.8)
                    appState.parameters.saturation    = Double.random(in: 0.3...1.0)
                    appState.parameters.brightness    = Double.random(in: 0.3...0.7)
                    Task { await appState.generate() }
                }) {
                    Label("Randomize All", systemImage: "shuffle")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 4)
                }
                .buttonStyle(.bordered)
                .disabled(appState.isGenerating)
                .padding(.horizontal)
                .padding(.bottom, 16)
            }
            .padding(.vertical, 12)
        }
        .background(Color(NSColor.controlBackgroundColor))
    }
}

// MARK: - Sub-components

private struct SectionCard<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title.uppercased())
                .font(.system(size: 10, weight: .semibold, design: .default))
                .foregroundColor(.secondary)
                .kerning(1.2)
                .padding(.horizontal, 16)

            VStack(alignment: .leading, spacing: 0) {
                content()
                    .padding(12)
            }
            .background(Color(NSColor.windowBackgroundColor).opacity(0.5))
            .cornerRadius(8)
            .padding(.horizontal, 12)
        }
    }
}

private struct LabeledSlider: View {
    let label: String
    @Binding var value: Double
    var color: Color = .blue

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(label)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                Spacer()
                Text(String(format: "%.2f", value))
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .monospacedDigit()
            }
            Slider(value: $value, in: 0...1)
                .accentColor(color)
        }
    }
}

private struct PaletteSwatch: View {
    let palette: ColorPalette
    let isSelected: Bool

    var body: some View {
        VStack(spacing: 3) {
            ZStack {
                LinearGradient(
                    gradient: Gradient(stops: palette.stops.map { stop in
                        Gradient.Stop(
                            color: Color(nsColor: stop.1),
                            location: stop.0
                        )
                    }),
                    startPoint: .leading,
                    endPoint: .trailing
                )
                .frame(height: 24)
                .cornerRadius(4)
                .overlay(
                    RoundedRectangle(cornerRadius: 4)
                        .stroke(isSelected ? Color.white : Color.clear, lineWidth: 2)
                )
            }

            Text(palette.name)
                .font(.system(size: 9))
                .foregroundColor(isSelected ? .primary : .secondary)
                .lineLimit(1)
        }
        .padding(3)
        .background(isSelected ? Color.blue.opacity(0.2) : Color.clear)
        .cornerRadius(6)
        .contentShape(Rectangle())
    }
}
