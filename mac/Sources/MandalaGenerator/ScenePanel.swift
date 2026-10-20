import SwiftUI
import UniformTypeIdentifiers

// MARK: - Scene Panel (left side) — Effects & Export

struct ScenePanel: View {
    @EnvironmentObject private var appState: AppState

    var body: some View {
        VStack(spacing: 0) {
            // ── Header ─────────────────────────────────────────────────
            HStack {
                Text("SCENE")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundColor(.secondary).kerning(1.2)
                Spacer()
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(Color(NSColor.controlBackgroundColor))

            Divider()

            ScrollView(.vertical, showsIndicators: true) {
                VStack(spacing: 8) {
                    // invisible but necessary for sheet attachment
                    Color.clear.frame(height: 0)
                        .sheet(isPresented: Binding(
                            get: { appState.showAnimationOptions },
                            set: { appState.showAnimationOptions = $0 }
                        )) {
                            AnimationOptionsSheet()
                                .environmentObject(appState)
                        }

                    // ── Effects ────────────────────────────────────────
                    Text("EFFECTS")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundColor(.secondary).kerning(1.2)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 12)
                        .padding(.top, 12)

                    EffectsLayerCard(settings: $appState.parameters.effectsLayer)
                        .padding(.horizontal, 8)

                    // ── Export ─────────────────────────────────────────
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
        }
        .background(Color(NSColor.controlBackgroundColor))
    }
}

// MARK: - Base Layer Card

struct BaseLayerCard: View {
    @Binding var settings: BaseLayerSettings
    let defaults = BaseLayerSettings()   // reset values for double-click
    @State private var isExpanded = true

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
                    HueSlider(value: $settings.hue, reset: defaults.hue)
                    SceneSlider(label: "Sat",   value: $settings.saturation, color: .pink, reset: defaults.saturation)
                    SceneSlider(label: "Bri",   value: $settings.brightness, color: .yellow, reset: defaults.brightness)
                    }

                    // Secondary color
                    if settings.type == .gradient || settings.type == .sunburst {
                        Divider()
                        Text("SECONDARY")
                            .font(.system(size: 9, weight: .semibold)).foregroundColor(.secondary).kerning(1.0)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        HueSlider(value: $settings.hue2, reset: defaults.hue2)
                        SceneSlider(label: "Sat", value: $settings.saturation2, color: .pink, reset: defaults.saturation2)
                        SceneSlider(label: "Bri", value: $settings.brightness2, color: .yellow, reset: defaults.brightness2)
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
                            SceneSlider(label: "Angle", value: $settings.gradientAngle, color: .blue, reset: defaults.gradientAngle)
                        }
                    }

                    // Sunburst controls
                    if settings.type == .sunburst {
                        Divider()
                        SceneSlider(label: "Rays",  value: $settings.patternScale,     color: .orange, reset: defaults.patternScale)
                        SceneSlider(label: "Sharp", value: $settings.patternSharpness, color: .cyan, reset: defaults.patternSharpness)
                    }

                    // Grain controls
                    if settings.type == .grain {
                        Divider()
                        SceneSlider(label: "Amount", value: $settings.grainAmount, color: .orange, reset: defaults.grainAmount)
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
                        SceneSlider(label: "Blend", value: $settings.imageBlend, color: .blue, reset: defaults.imageBlend)
                    }

                    if settings.type != .auto {
                        Divider()
                        SceneSlider(label: "Opacity", value: $settings.opacity, color: .white, reset: defaults.opacity)
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
    let defaults = EffectsLayerSettings()   // reset values for double-click
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
                    settings.glitterSeed    = UInt64.random(in: 1...UInt64.max)
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
                    SceneSlider(label: "Brightness", value: $settings.brightness, color: .white, reset: defaults.brightness)
                    SceneSlider(label: "Contrast",  value: $settings.contrast,   color: .orange, reset: defaults.contrast)
                    Divider()
                    SceneSlider(label: "Vignette",  value: $settings.vignette,   color: .gray, reset: defaults.vignette)
                    SceneSlider(label: "Chromatic", value: $settings.chromatic,  color: .cyan, reset: defaults.chromatic)
                    SceneSlider(label: "Relief",    value: $settings.relief,     color: .mint, reset: defaults.relief)
                    if settings.relief > 0 {
                        SceneSlider(label: "Light", value: $settings.reliefAngle, color: .yellow, reset: defaults.reliefAngle)
                    }
                    Divider()
                    SceneSlider(label: "Wash",          value: $settings.wash,         color: .white, reset: defaults.wash)
                    SceneSlider(label: "Sepia",         value: $settings.sepia,        color: Color(red: 0.8, green: 0.6, blue: 0.3), reset: defaults.sepia)
                    SceneSlider(label: "Fade",          value: $settings.fade,         color: .secondary, reset: defaults.fade)
                    SceneSlider(label: "Bloom",         value: $settings.bloom,        color: .purple, reset: defaults.bloom)
                    SceneSlider(label: "Local Contrast",value: $settings.localContrast,color: .teal, reset: defaults.localContrast)
                    SceneSlider(label: "Grain",         value: $settings.grain,        color: Color(red: 0.6, green: 0.55, blue: 0.5), reset: defaults.grain)
                    Divider()
                    EffectRow(label: "Dimming",    value: $settings.dimming,    seed: $settings.dimmingSeed,    color: .indigo, reset: defaults.dimming)
                    EffectRow(label: "Erasure",    value: $settings.erasure,    seed: $settings.erasureSeed,    color: .red, reset: defaults.erasure)
                    Divider()
                    EffectRow(label: "Highlights", value: $settings.highlights, seed: $settings.highlightsSeed, color: .orange, reset: defaults.highlights)
                    EffectRow(label: "Stars",      value: $settings.stars,      seed: $settings.starsSeed,      color: .yellow, reset: defaults.stars)
                    EffectRow(label: "Glitter",    value: $settings.glitter,    seed: $settings.glitterSeed,    color: .pink, reset: defaults.glitter)
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
    var reset: Double? = nil

    var body: some View {
        HStack(spacing: 4) {
            Text(label)
                .font(.system(size: 10)).foregroundColor(.secondary)
                .frame(width: 52, alignment: .leading)
            ResettableSlider(value: $value, range: 0...1, tint: color, reset: reset)
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
        .contentShape(Rectangle())
        .simultaneousGesture(TapGesture(count: 2).onEnded { if let r = reset { value = r } })
        .help(reset == nil ? "" : "Double-click to reset")
    }
}

// MARK: - Export Card

struct ExportCard: View {
    @ObservedObject var appState: AppState
    @State private var customSizeText: String = "2048"

    var body: some View {
        VStack(spacing: 8) {
            // Export size
            HStack(spacing: 6) {
                Text("Export")
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
                        .onChange(of: customSizeText) { _, newValue in
                            let digitsOnly = newValue.filter { $0.isNumber }
                            let limited = String(digitsOnly.prefix(4))
                            if limited != newValue {
                                customSizeText = limited
                                return
                            }
                            applyCustomSize()
                        }
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
                    if AppState.webPSupported {
                        Text("WebP").tag("webp")
                    }
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
                if appState.isExporting {
                    Label("Exporting…", systemImage: "hourglass")
                        .frame(maxWidth: .infinity)
                } else {
                    Label("Save Image", systemImage: "square.and.arrow.down")
                        .frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.bordered)
            .disabled(appState.currentImage == nil || appState.isGenerating || appState.isExporting)
            .keyboardShortcut("s", modifiers: [.command, .shift])

            Button(action: { appState.showAnimationOptions = true }) {
                Label("Export Animation…", systemImage: "film.stack")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .disabled(appState.currentImage == nil || appState.isGenerating || appState.animationExportProgress != nil)
            .help("Export a looping animation with rotating layers (⌘⌥E)")

            if let progress = appState.animationExportProgress {
                VStack(spacing: 3) {
                    ProgressView(value: progress)
                        .progressViewStyle(.linear)
                        .accentColor(.accentColor)
                    Text(progress < 0.92 ? "Rendering frames… \(Int(progress * 100))%" : "Writing file…")
                        .font(.system(size: 9)).foregroundColor(.secondary)
                }
            }
        }
        .padding(10)
        .background(Color(NSColor.controlBackgroundColor).opacity(0.7))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.white.opacity(0.08), lineWidth: 1))
    }

    private func applyCustomSize() {
        let parsed = Int(customSizeText) ?? appState.parameters.outputSizeCustom
        let clamped = max(64, min(4000, parsed))
        appState.parameters.outputSizeCustom = clamped
        if customSizeText != String(clamped) {
            customSizeText = String(clamped)
        }
    }
}

// MARK: - Animation Export Options Sheet

private struct AnimationOptionsSheet: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 20) {
            Text("Export Animation")
                .font(.headline)

            Form {
                Picker("Format", selection: $appState.animationOptions.format) {
                    Text("QuickTime MOV").tag("mov")
                    Text("Animated GIF").tag("gif")
                }
                .pickerStyle(.segmented)

                Picker("Frames", selection: $appState.animationOptions.frameCount) {
                    Text("24 frames").tag(24)
                    Text("36 frames").tag(36)
                    Text("48 frames").tag(48)
                    Text("72 frames").tag(72)
                    Text("90 frames").tag(90)
                }

                Picker("Speed", selection: $appState.animationOptions.fps) {
                    Text("12 fps  (slow)").tag(12)
                    Text("24 fps").tag(24)
                    Text("30 fps  (smooth)").tag(30)
                }

                let duration = Double(appState.animationOptions.frameCount) / Double(appState.animationOptions.fps)
                LabeledContent("Duration") {
                    Text(String(format: "%.1f sec", duration))
                        .foregroundColor(.secondary)
                }
            }
            .formStyle(.grouped)

            HStack(spacing: 12) {
                Button("Cancel") { dismiss() }
                    .keyboardShortcut(.cancelAction)
                Button("Export…") {
                    dismiss()
                    appState.exportAnimation(options: appState.animationOptions)
                }
                .keyboardShortcut(.defaultAction)
                .buttonStyle(.borderedProminent)
            }
        }
        .padding(24)
        .frame(width: 340)
    }
}

// MARK: - Text Layer Card

struct TextLayerCard: View {
    @EnvironmentObject private var appState: AppState
    @Binding var settings: TextLayerSettings
    let defaults = TextLayerSettings()   // reset values for double-click
    @State private var isExpanded = true
    @State private var showQuotePicker = false
    @State private var shadowExpanded = false
    @State private var cloudExpanded = false

    private var isInMyQuotes: Bool {
        MyQuotes.contains(text: settings.text, author: settings.customAuthor, in: appState.myQuotes)
    }

    private var selectedFontDisplay: String {
        QuoteDatabase.availableFonts.first { $0.name == settings.fontName }?.display ?? settings.fontName
    }

    @ViewBuilder
    private func sectionLabel(_ title: String) -> some View {
        Text(title)
            .font(.system(size: 9, weight: .semibold))
            .foregroundColor(.secondary).kerning(1.1)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 4)
    }

    var body: some View {
        VStack(spacing: 0) {
            // ── Header ─────────────────────────────────────────────────
            HStack(spacing: 8) {
                Image(systemName: settings.isEnabled ? "textformat" : "textformat")
                    .font(.system(size: 12))
                    .foregroundColor(settings.isEnabled ? .accentColor : .secondary)
                    .frame(width: 16)
                Toggle("", isOn: $settings.isEnabled)
                    .toggleStyle(.switch).scaleEffect(0.7).labelsHidden()
                Text("Text")
                    .font(.caption.weight(.medium))
                    .foregroundColor(settings.isEnabled ? .primary : .secondary)
                Spacer()
                // Random all settings
                Button(action: randomizeAll) {
                    Image(systemName: "dice")
                        .font(.system(size: 10)).foregroundColor(.purple)
                }
                .buttonStyle(.plain).help("Randomize all text settings")
                // Reset
                Button(action: { settings = TextLayerSettings() }) {
                    Image(systemName: "arrow.counterclockwise")
                        .font(.system(size: 10)).foregroundColor(.secondary)
                }
                .buttonStyle(.plain).help("Reset text layer")
                // Expand
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
                    // ── Text input ────────────────────────────────────────
                    HStack(spacing: 6) {
                        Text("Text")
                            .font(.system(size: 10)).foregroundColor(.secondary)
                            .frame(width: 52, alignment: .leading)
                        TextEditor(text: $settings.text)
                            .font(.system(size: 11))
                            .frame(minHeight: 52, maxHeight: 80)
                            .scrollContentBackground(.hidden)
                            .background(Color(NSColor.textBackgroundColor).opacity(0.5))
                            .cornerRadius(5)
                        VStack(spacing: 6) {
                            Button(action: insertRandomQuote) {
                                Image(systemName: "quote.bubble")
                                    .font(.system(size: 12))
                                    .foregroundColor(.orange)
                            }
                            .buttonStyle(.plain)
                            .help("Insert random quote")
                            Button(action: { showQuotePicker.toggle() }) {
                                Image(systemName: "list.bullet")
                                    .font(.system(size: 11))
                                    .foregroundColor(.secondary)
                            }
                            .buttonStyle(.plain)
                            .help("Choose quote")
                            .popover(isPresented: $showQuotePicker, arrowEdge: .trailing) {
                                QuotePickerPopover(
                                    myQuotes: appState.myQuotes,
                                    onPick: { useQuote($0); showQuotePicker = false },
                                    onRemove: { appState.removeMyQuote($0) }
                                )
                            }
                            Button(action: saveToMyQuotes) {
                                Image(systemName: isInMyQuotes ? "bookmark.fill" : "bookmark")
                                    .font(.system(size: 11))
                                    .foregroundColor(isInMyQuotes ? .orange : .secondary)
                            }
                            .buttonStyle(.plain)
                            .disabled(settings.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isInMyQuotes)
                            .help(isInMyQuotes ? "In your quotes" : "Save to my quotes")
                        }
                    }
                    HStack(spacing: 6) {
                        Text("Author")
                            .font(.system(size: 10)).foregroundColor(.secondary)
                            .frame(width: 52, alignment: .leading)
                        TextField("auto-detect or type name", text: $settings.customAuthor)
                            .font(.system(size: 10))
                            .textFieldStyle(.roundedBorder)
                    }

                    // ── Font ─────────────────────────────────────────────
                    HStack(spacing: 6) {
                        Text("Font")
                            .font(.system(size: 10)).foregroundColor(.secondary)
                            .frame(width: 52, alignment: .leading)
                        Menu(selectedFontDisplay) {
                            ForEach(QuoteDatabase.availableFonts, id: \.name) { entry in
                                Button(action: { settings.fontName = entry.name }) {
                                    Text(entry.display)
                                        .font(.custom(entry.name, size: 13))
                                }
                            }
                        }
                        .font(.system(size: 10))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        Spacer()
                    }

                    // ── Typography ────────────────────────────────────────
                    sectionLabel("TYPOGRAPHY")
                    SceneSlider(label: "Size",    value: $settings.fontSize,  range: 0.02...0.105, color: .cyan, reset: defaults.fontSize)
                    SceneSlider(label: "Width",   value: $settings.fontWidth, range: 0.1...1.0,    color: .cyan, reset: defaults.fontWidth)
                    SceneSlider(label: "Tracking",value: $settings.tracking,  range: -0.5...1.0,   color: .mint, reset: defaults.tracking)

                    // ── Position ──────────────────────────────────────────
                    sectionLabel("POSITION")
                    SceneSlider(label: "Pos X", value: $settings.offsetX, color: .blue, reset: defaults.offsetX)
                    SceneSlider(label: "Pos Y", value: $settings.offsetY, color: .blue, reset: defaults.offsetY)

                    // ── Color ─────────────────────────────────────────────
                    sectionLabel("COLOR")
                    HueSlider(value: $settings.hue, reset: defaults.hue)
                    SceneSlider(label: "Sat",  value: $settings.saturation, color: .pink, reset: defaults.saturation)
                    SceneSlider(label: "Bri",  value: $settings.brightness, range: 0...2, color: .yellow, reset: defaults.brightness)
                    SceneSlider(label: "Glow",   value: $settings.glow,    color: .purple, reset: defaults.glow)

                    // ── Shadow ────────────────────────────────────────────
                    Button(action: { withAnimation(.easeInOut(duration: 0.18)) { shadowExpanded.toggle() } }) {
                        HStack(spacing: 4) {
                            Text("SHADOW")
                                .font(.system(size: 9, weight: .semibold))
                                .foregroundColor(.secondary).kerning(1.1)
                            Spacer()
                            Image(systemName: shadowExpanded ? "chevron.up" : "chevron.down")
                                .font(.system(size: 8)).foregroundColor(.secondary)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.top, 4)
                    }
                    .buttonStyle(.plain)
                    if shadowExpanded {
                        SceneSlider(label: "Opacity", value: $settings.shadowOpacity, color: .gray, reset: defaults.shadowOpacity)
                        SceneSlider(label: "Blur",    value: $settings.shadowBlur,       color: .secondary, reset: defaults.shadowBlur)
                        SceneSlider(label: "X",       value: $settings.shadowOffsetX,    range: -2...2, color: .orange, reset: defaults.shadowOffsetX)
                        SceneSlider(label: "Y",       value: $settings.shadowOffsetY,    range: -2...2, color: .orange, reset: defaults.shadowOffsetY)
                        HueSlider(value: $settings.shadowHue, reset: defaults.shadowHue)
                        SceneSlider(label: "Sat",     value: $settings.shadowSaturation, color: .pink, reset: defaults.shadowSaturation)
                        SceneSlider(label: "Bri",     value: $settings.shadowBrightness, color: .white, reset: defaults.shadowBrightness)
                    }

                    // ── Cloud ─────────────────────────────────────────────
                    Button(action: { withAnimation(.easeInOut(duration: 0.18)) { cloudExpanded.toggle() } }) {
                        HStack(spacing: 4) {
                            Text("CLOUD")
                                .font(.system(size: 9, weight: .semibold))
                                .foregroundColor(.secondary).kerning(1.1)
                            Spacer()
                            Image(systemName: cloudExpanded ? "chevron.up" : "chevron.down")
                                .font(.system(size: 8)).foregroundColor(.secondary)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.top, 4)
                    }
                    .buttonStyle(.plain)
                    if cloudExpanded {
                        SceneSlider(label: "Opacity", value: $settings.cloudOpacity, color: .teal, reset: defaults.cloudOpacity)
                        SceneSlider(label: "Size",    value: $settings.cloudScale,       color: .teal, reset: defaults.cloudScale)
                        SceneSlider(label: "Blur",    value: $settings.cloudRadius,      color: .cyan, reset: defaults.cloudRadius)
                        HueSlider(value: $settings.cloudHue, reset: defaults.cloudHue)
                        SceneSlider(label: "Sat",     value: $settings.cloudSaturation,  color: .pink, reset: defaults.cloudSaturation)
                        SceneSlider(label: "Bri",     value: $settings.cloudBrightness,  color: .white, reset: defaults.cloudBrightness)
                    }

                    // ── Layer ─────────────────────────────────────────────
                    sectionLabel("LAYER")
                    SceneSlider(label: "Blur",   value: $settings.blur,    color: .indigo, reset: defaults.blur)
                    SceneSlider(label: "Opacity",value: $settings.opacity,  color: .white, reset: defaults.opacity)

                    Divider()

                    // ── Blend & Author ────────────────────────────────────
                    HStack(spacing: 6) {
                        Text("Blend")
                            .font(.system(size: 10)).foregroundColor(.secondary)
                            .frame(width: 52, alignment: .leading)
                        Picker("", selection: $settings.blendMode) {
                            ForEach(LayerBlendMode.allCases) { mode in
                                Text(mode.displayName).tag(mode)
                            }
                        }
                        .pickerStyle(.menu).labelsHidden().fixedSize()
                        Spacer()
                    }

                    HStack(spacing: 6) {
                        Color.clear.frame(width: 52, height: 1)
                        Toggle("Show author on image", isOn: $settings.showAuthor)
                            .font(.system(size: 10))
                            .toggleStyle(.checkbox)
                        Spacer()
                    }
                    if settings.showAuthor {
                        SceneSlider(label: "Aut. Size", value: $settings.authorScale,
                                    range: 0.3...1.5, color: .orange, reset: defaults.authorScale)
                        HStack(spacing: 6) {
                            Text("Italic")
                                .font(.system(size: 10)).foregroundColor(.secondary)
                                .frame(width: 52, alignment: .leading)
                            Toggle("Italic author line", isOn: $settings.authorItalic)
                                .font(.system(size: 10))
                                .toggleStyle(.checkbox)
                            Spacer()
                        }
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
        .onAppear { syncAuthorIfNeeded() }
        .onChange(of: settings.text) { syncAuthorIfNeeded() }
    }

    private func insertRandomQuote() {
        useQuote(QuoteDatabase.randomQuote(including: appState.myQuotes))
    }

    private func useQuote(_ q: Quote) {
        settings.text = q.text
        settings.customAuthor = q.author
        settings.isEnabled = true
    }

    private func saveToMyQuotes() {
        appState.addMyQuote(Quote(text: settings.text, author: settings.customAuthor))
    }

    private func syncAuthorIfNeeded() {
        guard settings.customAuthor.isEmpty, !settings.text.isEmpty else { return }
        if let match = QuoteDatabase.quotes.first(where: {
            $0.text == settings.text || settings.text.hasSuffix($0.text)
        }) {
            settings.customAuthor = match.author
        }
    }

    private func randomizeAll() {
        let fonts = QuoteDatabase.availableFonts.map(\.name)
        settings.fontName      = fonts.randomElement() ?? "Georgia"
        settings.fontSize      = Double.random(in: 0.04...0.15)
        settings.tracking      = Double.random(in: -0.1...0.4)
        settings.hue           = Double.random(in: 0...1)
        settings.saturation    = Double.random(in: 0...0.5)
        settings.brightness    = Double.random(in: 0.75...1.0)
        settings.glow          = Double.random(in: 0...0.6)
        settings.shadowOpacity = Double.random(in: 0...0.5)
        settings.shadowBlur    = Double.random(in: 0.2...0.8)
        settings.blur          = Double.random(in: 0...0.2)
        settings.opacity       = Double.random(in: 0.6...1.0)
        settings.blendMode     = .normal
        settings.isEnabled     = true
        if settings.text.isEmpty { insertRandomQuote() }
    }
}

// MARK: - Quote picker

/// Your own quotes first (with a remove button), then the built-in public-domain collection.
struct QuotePickerPopover: View {
    let myQuotes: [Quote]
    let onPick: (Quote) -> Void
    let onRemove: (Quote) -> Void

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                if !myQuotes.isEmpty {
                    sectionTitle("Your quotes")
                    ForEach(myQuotes, id: \.self) { q in
                        row(q) {
                            Button(action: { onRemove(q) }) {
                                Image(systemName: "xmark")
                                    .font(.system(size: 9)).foregroundColor(.secondary)
                            }
                            .buttonStyle(.plain)
                            .help("Remove quote")
                        }
                    }
                    sectionTitle("Collection").padding(.top, 8)
                }
                ForEach(QuoteDatabase.quotes, id: \.self) { q in
                    row(q) { EmptyView() }
                }
                Text("All quotes in the collection are in the public domain.")
                    .font(.system(size: 10)).foregroundColor(.secondary)
                    .padding(.top, 10)
            }
            .padding(12)
        }
        .frame(width: 320, height: 420)
    }

    private func sectionTitle(_ title: String) -> some View {
        Text(title)
            .font(.system(size: 11, weight: .semibold))
            .padding(.bottom, 4)
    }

    private func row<Trailing: View>(_ q: Quote, @ViewBuilder trailing: () -> Trailing) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Button(action: { onPick(q) }) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(q.text)
                        .font(.system(size: 11))
                        .fixedSize(horizontal: false, vertical: true)
                    if !q.author.isEmpty {
                        Text(q.author)
                            .font(.system(size: 10)).foregroundColor(.secondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            trailing()
        }
        .padding(.vertical, 5)
    }
}

// MARK: - Hue gradient slider

private struct HueSlider: View {
    @Binding var value: Double   // 0...1
    var reset: Double? = nil

    private static let hueGradient = LinearGradient(
        colors: (0...12).map { i in Color(hue: Double(i) / 12.0, saturation: 0.85, brightness: 0.9) },
        startPoint: .leading, endPoint: .trailing
    )

    var body: some View {
        HStack(spacing: 6) {
            Text("Hue")
                .font(.system(size: 10)).foregroundColor(.secondary)
                .frame(width: 52, alignment: .leading)
            GeometryReader { geo in
                let thumbX = max(0, min(geo.size.width - 14, value * (geo.size.width - 14)))
                ZStack(alignment: .leading) {
                    Self.hueGradient
                        .frame(height: 8).cornerRadius(4)
                        .frame(maxHeight: .infinity)
                    Circle()
                        .fill(Color(hue: value, saturation: 0.85, brightness: 0.9))
                        .frame(width: 14, height: 14)
                        .overlay(Circle().stroke(Color.white, lineWidth: 1.5))
                        .shadow(color: .black.opacity(0.25), radius: 1, x: 0, y: 1)
                        .offset(x: thumbX)
                }
                .contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance: 0).onChanged { drag in
                    value = max(0, min(1, drag.location.x / geo.size.width))
                })
            }
            .frame(height: 14)
            Text(String(format: "%.2f", value))
                .font(.system(size: 9)).foregroundColor(.secondary).monospacedDigit()
                .frame(width: 28, alignment: .trailing)
        }
        .contentShape(Rectangle())
        .simultaneousGesture(TapGesture(count: 2).onEnded { if let r = reset { value = r } })
        .help(reset == nil ? "" : "Double-click to reset")
    }
}

// MARK: - Compact slider for scene panel

struct SceneSlider: View {
    let label: String
    @Binding var value: Double
    var range: ClosedRange<Double> = 0...1
    var color: Color = .blue
    /// Value a double-click returns to; nil leaves the slider as it is.
    var reset: Double? = nil

    var body: some View {
        HStack(spacing: 6) {
            Text(label)
                .font(.system(size: 10)).foregroundColor(.secondary)
                .frame(width: 52, alignment: .leading)
            ResettableSlider(value: $value, range: range, tint: color, reset: reset)
            Text(String(format: "%.2f", value))
                .font(.system(size: 9)).foregroundColor(.secondary).monospacedDigit()
                .frame(width: 28, alignment: .trailing)
        }
        .contentShape(Rectangle())
        .simultaneousGesture(TapGesture(count: 2).onEnded { if let r = reset { value = r } })
        .help(reset == nil ? "" : "Double-click to reset")
    }
}
