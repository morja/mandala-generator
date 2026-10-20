import SwiftUI

// MARK: - Emblem layer card

struct EmblemLayerCard: View {
    @EnvironmentObject private var appState: AppState
    @Binding var settings: EmblemLayerSettings
    @State private var isExpanded = true

    @ViewBuilder
    private func sectionLabel(_ title: String) -> some View {
        Text(title)
            .font(.system(size: 9, weight: .semibold))
            .foregroundColor(.secondary).kerning(1.1)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 4)
    }

    @ViewBuilder
    private func row<Content: View>(_ label: String, @ViewBuilder content: () -> Content) -> some View {
        HStack(spacing: 6) {
            Text(label)
                .font(.system(size: 10)).foregroundColor(.secondary)
                .frame(width: 52, alignment: .leading)
            content()
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            // ── Header ─────────────────────────────────────────────────
            HStack(spacing: 8) {
                Image(systemName: "circle.circle")
                    .font(.system(size: 12))
                    .foregroundColor(settings.isEnabled ? .accentColor : .secondary)
                    .frame(width: 16)
                Toggle("", isOn: $settings.isEnabled)
                    .toggleStyle(.switch).scaleEffect(0.7).labelsHidden()
                Text("Emblem")
                    .font(.caption.weight(.medium))
                    .foregroundColor(settings.isEnabled ? .primary : .secondary)
                Spacer()
                Button(action: randomize) {
                    Image(systemName: "dice")
                        .font(.system(size: 10)).foregroundColor(.purple)
                }
                .buttonStyle(.plain).help("Random emblem")
                Button(action: { settings = EmblemLayerSettings(isEnabled: settings.isEnabled) }) {
                    Image(systemName: "arrow.counterclockwise")
                        .font(.system(size: 10)).foregroundColor(.secondary)
                }
                .buttonStyle(.plain).help("Reset emblem layer")
                Button(action: { withAnimation(.easeInOut(duration: 0.18)) { isExpanded.toggle() } }) {
                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .font(.system(size: 9)).foregroundColor(.secondary)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 10).padding(.vertical, 8)
            .background(Color(NSColor.windowBackgroundColor))
            .cornerRadius(isExpanded ? 0 : 8)
            .cornerRadius(8, corners: [.topLeft, .topRight])

            if isExpanded {
                VStack(spacing: 8) {
                    // ── Motif ─────────────────────────────────────────────
                    Picker("", selection: $settings.motif) {
                        ForEach(EmblemMotif.allCases) { Text($0.displayName).tag($0) }
                    }
                    .pickerStyle(.segmented).labelsHidden()

                    row("Style") {
                        Picker("", selection: $settings.rendering) {
                            ForEach(EmblemRendering.allCases) { Text($0.displayName).tag($0) }
                        }
                        .pickerStyle(.segmented).labelsHidden()
                    }
                    row("Frame") {
                        Picker("", selection: $settings.frame) {
                            ForEach(EmblemFrame.allCases) { Text($0.displayName).tag($0) }
                        }
                        .pickerStyle(.menu).labelsHidden().fixedSize()
                        Spacer()
                    }

                    if settings.motif == .treeOfLife {
                        sectionLabel("TREE")
                        row("Shape") {
                            Text("\(settings.seed)")
                                .font(.system(size: 10)).monospacedDigit()
                            Spacer()
                            Button(action: { settings.seed = UInt64.random(in: 1...999_999) }) {
                                Label("New shape", systemImage: "dice")
                                    .font(.system(size: 9))
                            }
                            .buttonStyle(.plain).foregroundColor(.purple)
                        }
                        SceneSlider(label: "Branches", value: $settings.branching, color: .green)
                        row("Roots") {
                            Toggle("", isOn: $settings.roots)
                                .toggleStyle(.switch).scaleEffect(0.7).labelsHidden()
                            Spacer()
                        }
                    }

                    // ── Shape ─────────────────────────────────────────────
                    sectionLabel("SHAPE")
                    SceneSlider(label: "Size",   value: $settings.size,   range: 0.1...1.0, color: .cyan)
                    SceneSlider(label: "Weight", value: $settings.weight, color: .cyan)

                    // ── Color ─────────────────────────────────────────────
                    sectionLabel("COLOR")
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 3), spacing: 4) {
                        ForEach(Array(appState.allPalettes.enumerated()), id: \.offset) { idx, pal in
                            PaletteSwatch(palette: pal, isSelected: settings.paletteIndex == idx)
                                .onTapGesture { settings.paletteIndex = idx }
                        }
                    }
                    SceneSlider(label: "Position", value: $settings.colorPosition, color: .orange)
                    SceneSlider(label: "Spread",   value: $settings.colorSpread,   color: .orange)
                    SceneSlider(label: "Glow",     value: $settings.glow,          color: .purple)

                    // ── Layer ─────────────────────────────────────────────
                    sectionLabel("LAYER")
                    SceneSlider(label: "Opacity", value: $settings.opacity, color: .white)
                    row("Blend") {
                        Picker("", selection: $settings.blendMode) {
                            ForEach(LayerBlendMode.allCases) { Text($0.displayName).tag($0) }
                        }
                        .pickerStyle(.menu).labelsHidden().fixedSize()
                        Spacer()
                    }
                }
                .padding(10)
                .background(Color(NSColor.controlBackgroundColor).opacity(0.7))
                .cornerRadius(8, corners: [.bottomLeft, .bottomRight])
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8)
            .stroke(Color.accentColor.opacity(settings.isEnabled ? 0.35 : 0.08), lineWidth: 1))
    }

    private func randomize() {
        settings.motif = EmblemMotif.allCases.randomElement() ?? .om
        settings.rendering = EmblemRendering.allCases.randomElement() ?? .filled
        settings.frame = EmblemFrame.allCases.randomElement() ?? .ring
        settings.seed = UInt64.random(in: 1...999_999)
        settings.branching = Double.random(in: 0.2...0.9)
        settings.weight = Double.random(in: 0.3...0.8)
        settings.paletteIndex = Int.random(in: 0..<max(1, appState.allPalettes.count))
        settings.colorPosition = Double.random(in: 0...1)
        settings.colorSpread = Double.random(in: 0...0.6)
        settings.glow = Double.random(in: 0.2...0.8)
    }
}

extension EmblemLayerSettings {
    init(isEnabled: Bool) {
        self.init()
        self.isEnabled = isEnabled
    }
}
