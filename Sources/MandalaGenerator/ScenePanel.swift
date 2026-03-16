import SwiftUI
import UniformTypeIdentifiers

// MARK: - Scene Panel (left side) — Background & Effects

struct ScenePanel: View {
    @EnvironmentObject private var appState: AppState

    var body: some View {
        ScrollView(.vertical, showsIndicators: true) {
            VStack(spacing: 8) {
                // ── Background ─────────────────────────────────────────
                Text("BACKGROUND")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundColor(.secondary).kerning(1.2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 12)
                    .padding(.top, 12)

                BaseLayerCard(settings: $appState.parameters.baseLayer)
                    .padding(.horizontal, 8)

                // ── Effects ────────────────────────────────────────────
                Text("EFFECTS")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundColor(.secondary).kerning(1.2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 12)

                EffectsLayerCard(settings: $appState.parameters.effectsLayer)
                    .padding(.horizontal, 8)

                // ── Export ─────────────────────────────────────────────
                Text("EXPORT")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundColor(.secondary).kerning(1.2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 12)

                ExportCard(appState: appState)
                    .padding(.horizontal, 8)

                Spacer(minLength: 20)
            }
        }
        .background(Color(NSColor.controlBackgroundColor))
    }
}

// MARK: - Base Layer Card

struct BaseLayerCard: View {
    @Binding var settings: BaseLayerSettings
    @State private var isExpanded = false

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack(spacing: 8) {
                Image(systemName: settings.isEnabled ? "square.stack.fill" : "square.stack")
                    .font(.system(size: 12))
                    .foregroundColor(settings.isEnabled ? .accentColor : .secondary)
                    .frame(width: 16)
                Toggle("", isOn: $settings.isEnabled)
                    .toggleStyle(.switch).scaleEffect(0.7).labelsHidden()
                Text("Background")
                    .font(.caption.weight(.medium))
                    .foregroundColor(settings.isEnabled ? .primary : .secondary)
                Spacer()
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
                    // Type picker
                    Picker("", selection: $settings.type) {
                        ForEach(BaseLayerType.allCases) { t in
                            Label(t.displayName, systemImage: t.sfSymbol).tag(t)
                        }
                    }
                    .pickerStyle(.menu).labelsHidden().frame(maxWidth: .infinity)

                    Divider()

                    if settings.type == .auto {
                        Text("Uses the active layer's palette to generate a dark ambient background automatically.")
                            .font(.system(size: 9)).foregroundColor(.secondary)
                            .multilineTextAlignment(.leading)
                    } else {
                    // Primary color
                    Text("PRIMARY")
                        .font(.system(size: 9, weight: .semibold)).foregroundColor(.secondary).kerning(1.0)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    SceneSlider(label: "Hue",   value: $settings.hue,        color: .purple)
                    SceneSlider(label: "Sat",   value: $settings.saturation, color: .pink)
                    SceneSlider(label: "Bri",   value: $settings.brightness, color: .yellow)
                    }

                    // Secondary color
                    if settings.type == .gradient || settings.type == .pattern {
                        Divider()
                        Text("SECONDARY")
                            .font(.system(size: 9, weight: .semibold)).foregroundColor(.secondary).kerning(1.0)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        SceneSlider(label: "Hue", value: $settings.hue2,        color: .purple)
                        SceneSlider(label: "Sat", value: $settings.saturation2, color: .pink)
                        SceneSlider(label: "Bri", value: $settings.brightness2, color: .yellow)
                    }

                    // Gradient controls
                    if settings.type == .gradient {
                        Divider()
                        HStack(spacing: 6) {
                            Text("Style")
                                .font(.system(size: 10)).foregroundColor(.secondary)
                                .frame(width: 52, alignment: .leading)
                            Picker("", selection: $settings.isRadial) {
                                Text("Radial").tag(true)
                                Text("Linear").tag(false)
                            }
                            .pickerStyle(.segmented).labelsHidden()
                        }
                        if !settings.isRadial {
                            SceneSlider(label: "Angle", value: $settings.gradientAngle, color: .blue)
                        }
                    }

                    // Pattern controls
                    if settings.type == .pattern {
                        Divider()
                        HStack(spacing: 6) {
                            Text("Type")
                                .font(.system(size: 10)).foregroundColor(.secondary)
                                .frame(width: 52, alignment: .leading)
                            Picker("", selection: $settings.patternType) {
                                Text("⊞").tag(0)
                                Text("≡").tag(1)
                                Text("⟋").tag(2)
                                Text("#").tag(3)
                            }
                            .pickerStyle(.segmented).labelsHidden()
                        }
                        SceneSlider(label: "Scale",  value: $settings.patternScale,     color: .blue)
                        SceneSlider(label: "Sharp",  value: $settings.patternSharpness, color: .cyan)
                    }

                    // Grain controls
                    if settings.type == .grain {
                        Divider()
                        SceneSlider(label: "Amount", value: $settings.grainAmount, color: .orange)
                        HStack(spacing: 6) {
                            Text("Colored")
                                .font(.system(size: 10)).foregroundColor(.secondary)
                                .frame(width: 52, alignment: .leading)
                            Toggle("", isOn: $settings.grainColored).labelsHidden()
                            Spacer()
                        }
                    }

                    // Image controls
                    if settings.type == .image {
                        Divider()
                        HStack {
                            Button(action: pickImage) {
                                Label(settings.imageURL != nil ? "Change…" : "Open Image…",
                                      systemImage: "photo.badge.plus")
                                    .font(.system(size: 10))
                            }
                            .buttonStyle(.bordered)
                            Spacer()
                        }
                        if let url = settings.imageURL {
                            HStack(spacing: 4) {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundColor(.green).font(.caption)
                                Text(url.lastPathComponent)
                                    .font(.system(size: 9)).foregroundColor(.secondary).lineLimit(1)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        SceneSlider(label: "Blend", value: $settings.imageBlend, color: .blue)
                    }

                    if settings.type != .auto {
                        Divider()
                        SceneSlider(label: "Opacity", value: $settings.opacity, color: .white)
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

    private func pickImage() {
        let panel = NSOpenPanel()
        panel.title = "Choose Background Image"
        panel.allowedContentTypes = [.png, .jpeg, .tiff, .heic, .bmp]
        panel.canChooseFiles = true
        panel.canChooseDirectories = false
        if panel.runModal() == .OK { settings.imageURL = panel.url }
    }
}

// MARK: - Effects Layer Card

struct EffectsLayerCard: View {
    @Binding var settings: EffectsLayerSettings
    @State private var isExpanded = true

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack(spacing: 8) {
                Image(systemName: settings.isEnabled ? "sparkles" : "sparkle")
                    .font(.system(size: 12))
                    .foregroundColor(settings.isEnabled ? .accentColor : .secondary)
                    .frame(width: 16)
                Toggle("", isOn: $settings.isEnabled)
                    .toggleStyle(.switch).scaleEffect(0.7).labelsHidden()
                Text("Effects")
                    .font(.caption.weight(.medium))
                    .foregroundColor(settings.isEnabled ? .primary : .secondary)
                Spacer()
                Button(action: { settings = EffectsLayerSettings(isEnabled: settings.isEnabled) }) {
                    Image(systemName: "arrow.counterclockwise").font(.system(size: 10)).foregroundColor(.secondary)
                }
                .buttonStyle(.plain).help("Reset all effects to defaults")
                Button(action: {
                    settings.dimmingSeed    = UInt64.random(in: 1...UInt64.max)
                    settings.erasureSeed    = UInt64.random(in: 1...UInt64.max)
                    settings.highlightsSeed = UInt64.random(in: 1...UInt64.max)
                    settings.starsSeed      = UInt64.random(in: 1...UInt64.max)
                }) {
                    Image(systemName: "dice").font(.system(size: 10)).foregroundColor(.blue)
                }
                .buttonStyle(.plain).help("Randomize all effect positions")
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
                    SceneSlider(label: "Brightness", value: $settings.brightness, color: .white)
                    SceneSlider(label: "Contrast",  value: $settings.contrast,   color: .orange)
                    Divider()
                    SceneSlider(label: "Vignette",  value: $settings.vignette,   color: .gray)
                    SceneSlider(label: "Chromatic", value: $settings.chromatic,  color: .cyan)
                    SceneSlider(label: "Relief",    value: $settings.relief,     color: .mint)
                    if settings.relief > 0 {
                        SceneSlider(label: "Light", value: $settings.reliefAngle, color: .yellow)
                    }
                    Divider()
                    EffectRow(label: "Dimming",    value: $settings.dimming,    seed: $settings.dimmingSeed,    color: .indigo)
                    EffectRow(label: "Erasure",    value: $settings.erasure,    seed: $settings.erasureSeed,    color: .red)
                    Divider()
                    EffectRow(label: "Highlights", value: $settings.highlights, seed: $settings.highlightsSeed, color: .orange)
                    EffectRow(label: "Stars",      value: $settings.stars,      seed: $settings.starsSeed,      color: .yellow)
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
}

// MARK: - Effect row: slider + per-effect dice button

private struct EffectRow: View {
    let label: String
    @Binding var value: Double
    @Binding var seed: UInt64
    var color: Color = .blue

    var body: some View {
        HStack(spacing: 4) {
            Text(label)
                .font(.system(size: 10)).foregroundColor(.secondary)
                .frame(width: 52, alignment: .leading)
            Slider(value: $value, in: 0...1).accentColor(color)
            Text(String(format: "%.2f", value))
                .font(.system(size: 9)).foregroundColor(.secondary).monospacedDigit()
                .frame(width: 28, alignment: .trailing)
            Button(action: { seed = UInt64.random(in: 1...UInt64.max) }) {
                Image(systemName: "dice")
                    .font(.system(size: 9))
                    .foregroundColor(value > 0 ? color : .secondary.opacity(0.4))
            }
            .buttonStyle(.plain)
            .help("Randomize \(label.lowercased()) positions")
        }
    }
}

// MARK: - Export Card

struct ExportCard: View {
    @ObservedObject var appState: AppState
    @State private var customSizeText: String = "2048"

    var body: some View {
        VStack(spacing: 8) {
            // Size
            HStack(spacing: 6) {
                Text("Size")
                    .font(.system(size: 10)).foregroundColor(.secondary)
                    .frame(width: 52, alignment: .leading)
                Picker("", selection: $appState.parameters.outputSize) {
                    Text("512 px").tag(512)
                    Text("800 px").tag(800)
                    Text("1024 px").tag(1024)
                    Text("1400 px").tag(1400)
                    Text("2048 px").tag(2048)
                    Text("Custom…").tag(0)
                }
                .pickerStyle(.menu).labelsHidden().fixedSize()
                Spacer()
            }
            if appState.parameters.outputSize == 0 {
                HStack(spacing: 6) {
                    Text("")
                        .frame(width: 52, alignment: .leading)
                    TextField("px", text: $customSizeText)
                        .textFieldStyle(.roundedBorder)
                        .font(.system(size: 10, design: .monospaced))
                        .frame(width: 70)
                        .onSubmit { applyCustomSize() }
                        .onChange(of: customSizeText) { applyCustomSize() }
                    Text("px")
                        .font(.system(size: 10)).foregroundColor(.secondary)
                    Spacer()
                }
            }

            // Format
            HStack(spacing: 6) {
                Text("Format")
                    .font(.system(size: 10)).foregroundColor(.secondary)
                    .frame(width: 52, alignment: .leading)
                Picker("", selection: $appState.parameters.outputFormat) {
                    Text("PNG").tag("png")
                    Text("JPG").tag("jpg")
                    Text("WebP").tag("webp")
                }
                .pickerStyle(.menu).labelsHidden().fixedSize()
                Spacer()
            }

            // Shape
            HStack(spacing: 6) {
                Text("Shape")
                    .font(.system(size: 10)).foregroundColor(.secondary)
                    .frame(width: 52, alignment: .leading)
                Picker("", selection: $appState.parameters.outputShape) {
                    Text("Square").tag("square")
                    Text("Circle").tag("circle")
                    Text("Squircle").tag("squircle")
                    Text("Rounded").tag("rounded")
                }
                .pickerStyle(.menu).labelsHidden().fixedSize()
                .disabled(appState.parameters.outputFormat == "jpg")
                Spacer()
            }

            Divider()

            // Save Image button
            Button(action: { appState.saveImage() }) {
                Label("Save Image", systemImage: "square.and.arrow.down")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .disabled(appState.currentImage == nil || appState.isGenerating)
            .keyboardShortcut("s", modifiers: [.command, .shift])

            Button(action: { appState.exportAnimation() }) {
                Label("Export Animation…", systemImage: "film.stack")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .disabled(appState.currentImage == nil || appState.isGenerating)
            .help("Export a looping MOV animation with rotating layers (⌘⌥E)")
        }
        .padding(10)
        .background(Color(NSColor.controlBackgroundColor).opacity(0.7))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.white.opacity(0.08), lineWidth: 1))
    }

    private func applyCustomSize() {
        let clamped = max(64, min(8192, Int(customSizeText) ?? appState.parameters.outputSizeCustom))
        appState.parameters.outputSizeCustom = clamped
    }
}

// MARK: - Compact slider for scene panel

private struct SceneSlider: View {
    let label: String
    @Binding var value: Double
    var range: ClosedRange<Double> = 0...1
    var color: Color = .blue

    var body: some View {
        HStack(spacing: 6) {
            Text(label)
                .font(.system(size: 10)).foregroundColor(.secondary)
                .frame(width: 52, alignment: .leading)
            Slider(value: $value, in: range).accentColor(color)
            Text(String(format: "%.2f", value))
                .font(.system(size: 9)).foregroundColor(.secondary).monospacedDigit()
                .frame(width: 28, alignment: .trailing)
        }
    }
}
