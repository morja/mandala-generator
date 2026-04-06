import SwiftUI

// MARK: - Layers Panel (right side)

struct PalettePanel: View {
    @EnvironmentObject private var appState: AppState
    @State private var draggedIndex: Int? = nil
    @State private var selectedTab: String = "background"

    private let tabs = [
        ("background", "square.stack.fill",  "Background"),
        ("graphics",   "sparkles",           "Graphics"),
        ("manual",     "hand.draw",          "Manual"),
        ("text",       "textformat",         "Text"),
    ]

    var body: some View {
        VStack(spacing: 0) {
            // ── Tab bar ───────────────────────────────────────────────
            HStack(spacing: 0) {
                ForEach(tabs, id: \.0) { (id, icon, label) in
                    Button(action: { selectedTab = id }) {
                        VStack(spacing: 3) {
                            Image(systemName: icon)
                                .font(.system(size: 11))
                            Text(label)
                                .font(.system(size: 9, weight: .medium))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 7)
                        .foregroundColor(selectedTab == id ? .accentColor : .secondary)
                        .background(selectedTab == id
                            ? Color.accentColor.opacity(0.10)
                            : Color.clear)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 4)
            .background(Color(NSColor.controlBackgroundColor))

            Divider()

            // ── Tab content ───────────────────────────────────────────
            ScrollView(.vertical, showsIndicators: true) {
                VStack(spacing: 8) {
                    switch selectedTab {

                    case "background":
                        Text("BACKGROUND")
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundColor(.secondary).kerning(1.2)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 12)
                            .padding(.top, 12)

                        BaseLayerCard(settings: $appState.parameters.baseLayer)
                            .padding(.horizontal, 8)

                    case "graphics":
                        // ── Graphics sub-header ───────────────────────
                        HStack(spacing: 6) {
                            Text("LAYERS")
                                .font(.system(size: 10, weight: .semibold))
                                .foregroundColor(.secondary).kerning(1.2)
                            Spacer()
                            StyleSettingsMenu()
                            Button(action: { appState.randomizeLayers() }) {
                                Image(systemName: "shuffle")
                                    .font(.system(size: 11))
                                    .foregroundColor(.purple)
                            }
                            .buttonStyle(.plain)
                            .help("Randomize all layers")
                            Button(action: addLayer) {
                                Image(systemName: "plus.circle.fill")
                                    .font(.system(size: 15))
                                    .foregroundColor(appState.parameters.layers.count >= 5 ? .secondary.opacity(0.3) : .accentColor)
                            }
                            .buttonStyle(.plain)
                            .disabled(appState.parameters.layers.count >= 5)
                            .help("Add layer")
                        }
                        .padding(.horizontal, 12)
                        .padding(.top, 4)

                        ForEach(appState.parameters.layers.indices, id: \.self) { i in
                            LayerCard(
                                layer: $appState.parameters.layers[i],
                                index: i,
                                canDelete: appState.parameters.layers.count > 1,
                                canDuplicate: appState.parameters.layers.count < 5,
                                onDelete: { appState.parameters.layers.remove(at: i) },
                                onDuplicate: { appState.duplicateLayer(at: i) },
                                onRandomize: { appState.randomizeLayer(at: i) },
                                onCopy: { appState.copiedLayer = appState.parameters.layers[i] },
                                onPaste: {
                                    if var pasted = appState.copiedLayer {
                                        pasted.seed = UInt64.random(in: 1...UInt64.max)
                                        appState.parameters.layers[i] = pasted
                                    }
                                },
                                hasCopied: appState.copiedLayer != nil
                            )
                            .padding(.horizontal, 8)
                            .opacity(draggedIndex == i ? 0.4 : 1.0)
                            .onDrag {
                                draggedIndex = i
                                return NSItemProvider(object: "\(i)" as NSString)
                            }
                            .onDrop(of: [.plainText], delegate: LayerDropDelegate(
                                toIndex: i,
                                layers: $appState.parameters.layers,
                                draggedIndex: $draggedIndex
                            ))
                        }

                    case "manual":
                        VStack(spacing: 8) {
                            GraffitiCard(
                                settings: $appState.parameters.graffitiLayer,
                                isGraffitiMode: $appState.isGraffitiMode
                            )
                            DrawingCard(
                                settings: $appState.parameters.drawingLayer,
                                isDrawingMode: $appState.isDrawingMode
                            )
                        }
                        .padding(.horizontal, 8)
                        .padding(.top, 4)

                    case "text":
                        TextLayerCard(settings: $appState.parameters.textLayer)
                            .padding(.horizontal, 8)
                            .padding(.top, 8)

                    default:
                        EmptyView()
                    }

                    Spacer(minLength: 20)
                }
                .padding(.top, 8)
            }
        }
        .background(Color(NSColor.controlBackgroundColor))
    }

    private func addLayer() {
        let nextStyle = appState.addLayerStyle()
        let nextPalette = (appState.parameters.layers.last.map { ($0.paletteIndex + 3) % ColorPalettes.all.count }) ?? 0
        appState.parameters.layers.append(StyleLayer(
            style: nextStyle,
            scale: 0.65,
            paletteIndex: nextPalette,
            colorOffset: Double(appState.parameters.layers.count) * 0.25,
            complexity: 0.6,
            density: 0.5,
            glowIntensity: 0.5,
            colorDrift: 0.4,
            ripple: 0.0,
            wash: 0.0
        ))
    }
}

// MARK: - Layer Card

private struct LayerCard: View {
    @EnvironmentObject private var appState: AppState
    @Binding var layer: StyleLayer
    let index: Int
    let canDelete: Bool
    let canDuplicate: Bool
    let onDelete: () -> Void
    let onDuplicate: () -> Void
    let onRandomize: () -> Void
    let onCopy: () -> Void
    let onPaste: () -> Void
    let hasCopied: Bool
    @State private var showPaletteEditor = false
    @State private var editingPaletteId: String? = nil

    private var isExpanded: Bool {
        appState.cardExpandedStates["layer_\(index)"] ?? true
    }
    private func toggleExpanded() {
        withAnimation(.easeInOut(duration: 0.18)) {
            appState.cardExpandedStates["layer_\(index)"] = !isExpanded
        }
    }

    private var palette: ColorPalette {
        let all = appState.allPalettes
        return all[max(0, min(all.count - 1, layer.paletteIndex))]
    }

    var body: some View {
        VStack(spacing: 0) {
            // ── Header ──────────────────────────────────────────────
            HStack(spacing: 8) {
                DragHandle()

                Toggle("", isOn: $layer.isEnabled)
                    .toggleStyle(.switch).scaleEffect(0.75).labelsHidden()
                    .frame(width: 34)

                // Thumbnail preview
                if let preview = appState.layerPreviews[index] {
                    Image(nsImage: preview)
                        .resizable().interpolation(.high)
                        .frame(width: 32, height: 32)
                        .cornerRadius(5)
                        .opacity(layer.isEnabled ? 1 : 0.4)
                } else {
                    RoundedRectangle(cornerRadius: 5)
                        .fill(Color.secondary.opacity(0.12))
                        .frame(width: 32, height: 32)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(layer.style.displayName)
                        .font(.caption.weight(.medium))
                        .foregroundColor(layer.isEnabled ? .primary : .secondary)
                        .lineLimit(1)
                    // Colour strip
                    LinearGradient(
                        gradient: Gradient(stops: palette.stops.map {
                            Gradient.Stop(color: Color(nsColor: $0.1), location: $0.0)
                        }),
                        startPoint: .leading, endPoint: .trailing
                    )
                    .frame(height: 4).cornerRadius(2)
                    .opacity(layer.isEnabled ? 0.9 : 0.3)
                }

                Spacer()

                Button(action: onRandomize) {
                    Image(systemName: "dice")
                        .font(.system(size: 10))
                        .foregroundColor(.blue)
                }
                .buttonStyle(.plain)
                .help("Randomize this layer")

                if canDuplicate {
                    Button(action: onDuplicate) {
                        Image(systemName: "plus.square.on.square")
                            .font(.system(size: 10))
                            .foregroundColor(.secondary)
                    }
                    .buttonStyle(.plain)
                    .help("Duplicate layer")
                }

                Button(action: toggleExpanded) {
                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .font(.system(size: 10))
                        .foregroundColor(.secondary)
                }
                .buttonStyle(.plain)

                if canDelete {
                    Button(action: onDelete) {
                        Image(systemName: "xmark")
                            .font(.system(size: 10))
                            .foregroundColor(.secondary.opacity(0.6))
                    }
                    .buttonStyle(.plain)
                }
            }
            .contextMenu {
                Button(action: onCopy) { Label("Copy Layer", systemImage: "doc.on.doc") }
                Button(action: onPaste) { Label("Paste Layer Settings", systemImage: "doc.on.clipboard") }
                    .disabled(!hasCopied)
                Divider()
                Button(action: onDuplicate) { Label("Duplicate Layer", systemImage: "plus.square.on.square") }
                    .disabled(!canDuplicate)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 10)
            .background(Color(NSColor.windowBackgroundColor))
            .cornerRadius(isExpanded ? 0 : 8)
            .cornerRadius(8, corners: [.topLeft, .topRight])

            // ── Body (expanded) ──────────────────────────────────────
            if isExpanded {
                VStack(spacing: 8) {
                    // Style + blend mode row
                    HStack(spacing: 6) {
                        Picker("", selection: $layer.style) {
                            ForEach(appState.styleOptions(including: layer.style)) { s in
                                let isEnabled = appState.isStyleEnabled(s)
                                Label(isEnabled ? s.displayName : "\(s.displayName) (Disabled)",
                                      systemImage: s.sfSymbol).tag(s)
                            }
                        }
                        .pickerStyle(.menu).labelsHidden()

                        Picker("", selection: $layer.blendMode) {
                            ForEach(LayerBlendMode.allCases) { m in
                                Text(m.displayName).tag(m)
                            }
                        }
                        .pickerStyle(.menu).labelsHidden()
                        .frame(width: 80)
                    }

                    Divider()

                    // Palette — 3-column compact grid
                    Text("PALETTE")
                        .font(.system(size: 9, weight: .semibold))
                        .foregroundColor(.secondary).kerning(1.0)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 3), spacing: 4) {
                        ForEach(Array(appState.allPalettes.enumerated()), id: \.offset) { idx, pal in
                            PaletteSwatch(palette: pal, isSelected: layer.paletteIndex == idx)
                                .onTapGesture { layer.paletteIndex = idx }
                                .overlay(
                                    Group {
                                        if idx >= ColorPalettes.all.count {
                                            Image(systemName: "star.fill")
                                                .font(.system(size: 6))
                                                .foregroundColor(.white.opacity(0.7))
                                                .offset(x: 6, y: -6)
                                        }
                                    },
                                    alignment: .topTrailing
                                )
                        }
                    }

                    HStack {
                        Spacer()
                        Button(action: { editingPaletteId = nil; showPaletteEditor = true }) {
                            Label("New Palette", systemImage: "plus")
                                .font(.system(size: 9))
                        }
                        .buttonStyle(.plain)
                        .foregroundColor(.accentColor)

                        if layer.paletteIndex >= ColorPalettes.all.count {
                            let customIdx = layer.paletteIndex - ColorPalettes.all.count
                            if customIdx < appState.customPalettes.count {
                                Button(action: {
                                    editingPaletteId = appState.customPalettes[customIdx].id
                                    showPaletteEditor = true
                                }) {
                                    Label("Edit", systemImage: "pencil")
                                        .font(.system(size: 9))
                                }
                                .buttonStyle(.plain)
                                .foregroundColor(.secondary)
                            }
                        }
                    }
                    .sheet(isPresented: $showPaletteEditor) {
                        PaletteEditorSheet(isPresented: $showPaletteEditor, editingId: editingPaletteId)
                            .environmentObject(appState)
                    }

                    Divider()

                    // Symmetry + Seed row
                    HStack(spacing: 6) {
                        Text("Symmetry")
                            .font(.system(size: 10))
                            .foregroundColor(.secondary)
                            .frame(width: 70, alignment: .leading)
                        Spacer()
                        Stepper("\(layer.symmetry)×", value: $layer.symmetry, in: 1...8)
                            .font(.system(size: 10))
                            .fixedSize()
                    }

                    HStack(spacing: 6) {
                        Text("Seed")
                            .font(.system(size: 10))
                            .foregroundColor(.secondary)
                            .frame(width: 70, alignment: .leading)
                        TextField("", value: $layer.seed, format: .number)
                            .textFieldStyle(.roundedBorder)
                            .font(.system(size: 10, design: .monospaced))
                        Button(action: { layer.seed = UInt64.random(in: 1...UInt64.max) }) {
                            Image(systemName: "dice")
                                .font(.system(size: 10))
                                .foregroundColor(.blue)
                        }
                        .buttonStyle(.plain)
                    }

                    Divider()

                    // Sliders
                    CardSlider(label: "Scale",       value: $layer.scale,        range: 0...1,     color: .blue)
                    CardSlider(label: "Complexity",  value: $layer.complexity,   range: 0...1,     color: .indigo)
                    CardSlider(label: "Density",     value: $layer.density,      range: 0...1,     color: .blue)
                    CardSlider(label: "Glow",        value: $layer.glowIntensity,range: 0...1,     color: .yellow)
                    CardSlider(label: "Color Drift", value: $layer.colorDrift,   range: 0...1,     color: .purple)
                    CardSlider(label: "Ripple",      value: $layer.ripple,       range: 0...1,     color: .cyan)
                    CardSlider(label: "Wash",        value: $layer.wash,         range: 0...1,     color: .teal)

                    Divider()

                    CardSlider(label: "Abstract",    value: $layer.abstractLevel,range: 0...1,     color: .purple)
                    CardSlider(label: "Saturation",  value: $layer.saturation,   range: 0...1,     color: .pink)
                    CardSlider(label: "Brightness",  value: $layer.brightness,   range: 0...1,     color: .yellow)
                    CardSlider(label: "Rotation",    value: $layer.rotation,     range: 0...1,     color: .mint)
                    CardSlider(label: "Opacity",     value: $layer.opacity,      range: 0...1,     color: .white)
                }
                .padding(10)
                .background(Color(NSColor.controlBackgroundColor).opacity(0.7))
                .cornerRadius(8, corners: [.bottomLeft, .bottomRight])
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.white.opacity(layer.isEnabled ? 0.08 : 0.03), lineWidth: 1))
        .opacity(layer.isEnabled ? 1 : 0.55)
    }
}

private struct StyleSettingsMenu: View {
    @EnvironmentObject private var appState: AppState

    var body: some View {
        Menu {
            Picker("Sort", selection: $appState.styleSortOrder) {
                ForEach(LayerStyleSortOrder.allCases) { order in
                    Text(order.displayName).tag(order)
                }
            }

            Divider()

            ForEach(appState.allStyleOptions()) { style in
                Toggle(isOn: Binding(
                    get: { appState.isStyleEnabled(style) },
                    set: { appState.setStyleEnabled(style, isEnabled: $0) }
                )) {
                    Label(style.displayName, systemImage: style.sfSymbol)
                }
                .disabled(!appState.canDisableStyle(style))
            }
        } label: {
            Image(systemName: "slider.horizontal.3")
                .font(.system(size: 11))
                .foregroundColor(.secondary)
        }
        .menuStyle(.borderlessButton)
        .help("Style settings")
    }
}

// MARK: - Drawing Card

private struct DrawingCard: View {
    @EnvironmentObject private var appState: AppState
    @Binding var settings: DrawingLayerSettings
    @Binding var isDrawingMode: Bool

    private var isExpanded: Bool {
        appState.cardExpandedStates["drawing"] ?? true
    }
    private func toggleExpanded() {
        withAnimation(.easeInOut(duration: 0.18)) {
            appState.cardExpandedStates["drawing"] = !isExpanded
        }
    }

    private var currentColorBinding: Binding<Color> {
        Binding(
            get: { Color(hue: settings.currentHue, saturation: settings.currentSaturation, brightness: settings.currentBrightness) },
            set: { newColor in
                let nsColor = NSColor(newColor)
                if let rgb = nsColor.usingColorSpace(.deviceRGB) {
                    var h: CGFloat = 0, s: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
                    rgb.getHue(&h, saturation: &s, brightness: &b, alpha: &a)
                    settings.currentHue = Double(h)
                    settings.currentSaturation = Double(s)
                    settings.currentBrightness = Double(b)
                }
            }
        )
    }

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack(spacing: 8) {
                Image(systemName: settings.isEnabled ? "pencil.tip.crop.circle.fill" : "pencil.tip.crop.circle")
                    .font(.system(size: 12))
                    .foregroundColor(settings.isEnabled ? .accentColor : .secondary)
                    .frame(width: 16)
                Toggle("", isOn: $settings.isEnabled)
                    .toggleStyle(.switch).scaleEffect(0.7).labelsHidden()
                    .onChange(of: settings.isEnabled) { _, enabled in
                        if !enabled { isDrawingMode = false }
                    }
                Text("Drawing")
                    .font(.caption.weight(.medium))
                    .foregroundColor(settings.isEnabled ? .primary : .secondary)
                Spacer()
                if !settings.strokes.isEmpty {
                    Button(action: { settings.strokes.removeLast() }) {
                        Image(systemName: "arrow.uturn.backward")
                            .font(.system(size: 9)).foregroundColor(.orange)
                    }
                    .buttonStyle(.plain).help("Undo last stroke")
                    Button(action: { settings.strokes.removeAll() }) {
                        Image(systemName: "trash")
                            .font(.system(size: 9)).foregroundColor(.red.opacity(0.7))
                    }
                    .buttonStyle(.plain).help("Clear all strokes")
                }
                Button(action: toggleExpanded) {
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
                    // Symmetry
                    HStack(spacing: 6) {
                        Text("Symmetry")
                            .font(.system(size: 10)).foregroundColor(.secondary)
                            .frame(width: 70, alignment: .leading)
                        Spacer()
                        Stepper("\(settings.symmetry)×", value: $settings.symmetry, in: 1...16)
                            .font(.system(size: 10)).fixedSize()
                    }

                    Divider()

                    // Color picker
                    HStack(spacing: 8) {
                        Text("Stroke Color")
                            .font(.system(size: 10)).foregroundColor(.secondary)
                        Spacer()
                        ColorPicker("", selection: currentColorBinding, supportsOpacity: false)
                            .labelsHidden()
                            .frame(width: 44, height: 26)
                    }

                    Divider()

                    // Blend mode + opacity
                    HStack(spacing: 6) {
                        Picker("", selection: $settings.blendMode) {
                            ForEach(LayerBlendMode.allCases) { m in
                                Text(m.displayName).tag(m)
                            }
                        }
                        .pickerStyle(.menu).labelsHidden()
                        .frame(width: 90)
                        Spacer()
                    }

                    CardSlider(label: "Opacity", value: $settings.opacity,       range: 0...1,   color: .white)
                    CardSlider(label: "Weight",  value: $settings.strokeWeight,  range: 0...1,   color: .white)
                    CardSlider(label: "Glow",    value: $settings.glowIntensity, range: 0...1,   color: .yellow)

                    Divider()

                    // Draw mode toggle button
                    Button(action: { isDrawingMode.toggle() }) {
                        Label(isDrawingMode ? "Exit Draw Mode" : "Draw on Canvas",
                              systemImage: isDrawingMode ? "checkmark.circle.fill" : "pencil.and.outline")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .tint(isDrawingMode ? .green : .accentColor)

                    if !settings.strokes.isEmpty {
                        Text("\(settings.strokes.count) stroke\(settings.strokes.count == 1 ? "" : "s")")
                            .font(.system(size: 9)).foregroundColor(.secondary)
                    }
                }
                .padding(10)
                .background(Color(NSColor.controlBackgroundColor).opacity(0.7))
                .cornerRadius(8, corners: [.bottomLeft, .bottomRight])
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(isDrawingMode ? Color.green.opacity(0.5) : Color.accentColor.opacity(settings.isEnabled ? 0.35 : 0.08),
                        lineWidth: isDrawingMode ? 1.5 : 1)
        )
    }
}

// MARK: - Graffiti Card

private struct GraffitiCard: View {
    @EnvironmentObject private var appState: AppState
    @Binding var settings: GraffitiLayerSettings
    @Binding var isGraffitiMode: Bool

    private var isExpanded: Bool {
        appState.cardExpandedStates["graffiti"] ?? true
    }
    private func toggleExpanded() {
        withAnimation(.easeInOut(duration: 0.18)) {
            appState.cardExpandedStates["graffiti"] = !isExpanded
        }
    }

    private var currentColorBinding: Binding<Color> {
        Binding(
            get: { Color(hue: settings.currentHue, saturation: settings.currentSaturation, brightness: settings.currentBrightness) },
            set: { newColor in
                let nsColor = NSColor(newColor)
                if let rgb = nsColor.usingColorSpace(.deviceRGB) {
                    var h: CGFloat = 0, s: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
                    rgb.getHue(&h, saturation: &s, brightness: &b, alpha: &a)
                    settings.currentHue = Double(h)
                    settings.currentSaturation = Double(s)
                    settings.currentBrightness = Double(b)
                }
            }
        )
    }

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack(spacing: 8) {
                Image(systemName: settings.isEnabled ? "paintbrush.fill" : "paintbrush")
                    .font(.system(size: 12))
                    .foregroundColor(settings.isEnabled ? .purple : .secondary)
                    .frame(width: 16)
                Toggle("", isOn: $settings.isEnabled)
                    .toggleStyle(.switch).scaleEffect(0.7).labelsHidden()
                    .onChange(of: settings.isEnabled) { _, enabled in
                        if !enabled { isGraffitiMode = false }
                    }
                Text("Spray")
                    .font(.caption.weight(.medium))
                    .foregroundColor(settings.isEnabled ? .primary : .secondary)
                Spacer()
                if !settings.strokes.isEmpty {
                    Button(action: { settings.strokes.removeLast() }) {
                        Image(systemName: "arrow.uturn.backward")
                            .font(.system(size: 9)).foregroundColor(.orange)
                    }
                    .buttonStyle(.plain).help("Undo last stroke")
                    Button(action: { settings.strokes.removeAll() }) {
                        Image(systemName: "trash")
                            .font(.system(size: 9)).foregroundColor(.red.opacity(0.7))
                    }
                    .buttonStyle(.plain).help("Clear all strokes")
                }
                Button(action: toggleExpanded) {
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
                    // Symmetry
                    HStack(spacing: 6) {
                        Text("Symmetry")
                            .font(.system(size: 10)).foregroundColor(.secondary)
                            .frame(width: 70, alignment: .leading)
                        Spacer()
                        Stepper("\(settings.symmetry)×", value: $settings.symmetry, in: 1...16)
                            .font(.system(size: 10)).fixedSize()
                    }

                    Divider()

                    // Color picker
                    HStack(spacing: 8) {
                        Text("Spray Color")
                            .font(.system(size: 10)).foregroundColor(.secondary)
                        Spacer()
                        ColorPicker("", selection: currentColorBinding, supportsOpacity: false)
                            .labelsHidden()
                            .frame(width: 44, height: 26)
                    }

                    // Brush size
                    CardSlider(label: "Brush Size", value: $settings.currentBrushSize, range: 0.01...0.20, color: .purple)
                    CardSlider(label: "Opacity",    value: $settings.currentOpacity,   range: 0...1,      color: .white)
                    CardSlider(label: "Softness",   value: $settings.softness,          range: 0...1,      color: .cyan)

                    Divider()

                    // Blend mode + layer opacity
                    HStack(spacing: 6) {
                        Picker("", selection: $settings.blendMode) {
                            ForEach(LayerBlendMode.allCases) { m in
                                Text(m.displayName).tag(m)
                            }
                        }
                        .pickerStyle(.menu).labelsHidden()
                        .frame(width: 90)
                        Spacer()
                    }
                    CardSlider(label: "Layer Opacity", value: $settings.opacity, range: 0...1, color: .white)

                    Divider()

                    Button(action: { isGraffitiMode.toggle() }) {
                        Label(isGraffitiMode ? "Exit Spray Mode" : "Spray on Canvas",
                              systemImage: isGraffitiMode ? "checkmark.circle.fill" : "paintbrush.pointed")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .tint(isGraffitiMode ? .green : .purple)

                    if !settings.strokes.isEmpty {
                        Text("\(settings.strokes.count) spray stroke\(settings.strokes.count == 1 ? "" : "s")")
                            .font(.system(size: 9)).foregroundColor(.secondary)
                    }
                }
                .padding(10)
                .background(Color(NSColor.controlBackgroundColor).opacity(0.7))
                .cornerRadius(8, corners: [.bottomLeft, .bottomRight])
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(isGraffitiMode ? Color.purple.opacity(0.6) : Color.purple.opacity(settings.isEnabled ? 0.25 : 0.08),
                        lineWidth: isGraffitiMode ? 1.5 : 1)
        )
    }
}

// MARK: - Drag handle (2×3 dot grid)

private struct DragHandle: View {
    var body: some View {
        VStack(spacing: 3) {
            ForEach(0..<3) { _ in
                HStack(spacing: 3) {
                    ForEach(0..<2) { _ in
                        Circle()
                            .fill(Color.secondary.opacity(0.4))
                            .frame(width: 2.5, height: 2.5)
                    }
                }
            }
        }
    }
}

// MARK: - Compact slider for layer cards

private struct CardSlider: View {
    let label: String
    @Binding var value: Double
    var range: ClosedRange<Double> = 0...1
    var color: Color = .blue

    var body: some View {
        HStack(spacing: 6) {
            Text(label)
                .font(.system(size: 10))
                .foregroundColor(.secondary)
                .frame(width: 70, alignment: .leading)
            Slider(value: $value, in: range)
                .accentColor(color)
            Text(String(format: "%.2f", value))
                .font(.system(size: 9)).foregroundColor(.secondary).monospacedDigit()
                .frame(width: 28, alignment: .trailing)
        }
    }
}

// MARK: - Drag-to-reorder

import UniformTypeIdentifiers

private struct LayerDropDelegate: DropDelegate {
    let toIndex: Int
    @Binding var layers: [StyleLayer]
    @Binding var draggedIndex: Int?

    func performDrop(info: DropInfo) -> Bool {
        guard let from = draggedIndex, from != toIndex else { draggedIndex = nil; return false }
        withAnimation { layers.move(fromOffsets: IndexSet(integer: from), toOffset: toIndex > from ? toIndex + 1 : toIndex) }
        draggedIndex = nil
        return true
    }

    func dropUpdated(info: DropInfo) -> DropProposal? {
        DropProposal(operation: .move)
    }
}

// MARK: - Rounded corners helper

extension View {
    func cornerRadius(_ radius: CGFloat, corners: RectCorner) -> some View {
        clipShape(PartialRoundedRect(radius: radius, corners: corners))
    }
}

struct RectCorner: OptionSet {
    let rawValue: Int
    static let topLeft     = RectCorner(rawValue: 1 << 0)
    static let topRight    = RectCorner(rawValue: 1 << 1)
    static let bottomLeft  = RectCorner(rawValue: 1 << 2)
    static let bottomRight = RectCorner(rawValue: 1 << 3)
    static let all: RectCorner = [.topLeft, .topRight, .bottomLeft, .bottomRight]
}

struct PartialRoundedRect: Shape {
    var radius: CGFloat
    var corners: RectCorner

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let tl = corners.contains(.topLeft)     ? radius : 0
        let tr = corners.contains(.topRight)    ? radius : 0
        let bl = corners.contains(.bottomLeft)  ? radius : 0
        let br = corners.contains(.bottomRight) ? radius : 0

        path.move(to: CGPoint(x: rect.minX + tl, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX - tr, y: rect.minY))
        if tr > 0 { path.addArc(center: CGPoint(x: rect.maxX - tr, y: rect.minY + tr), radius: tr, startAngle: .degrees(-90), endAngle: .degrees(0), clockwise: false) }
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY - br))
        if br > 0 { path.addArc(center: CGPoint(x: rect.maxX - br, y: rect.maxY - br), radius: br, startAngle: .degrees(0), endAngle: .degrees(90), clockwise: false) }
        path.addLine(to: CGPoint(x: rect.minX + bl, y: rect.maxY))
        if bl > 0 { path.addArc(center: CGPoint(x: rect.minX + bl, y: rect.maxY - bl), radius: bl, startAngle: .degrees(90), endAngle: .degrees(180), clockwise: false) }
        path.addLine(to: CGPoint(x: rect.minX, y: rect.minY + tl))
        if tl > 0 { path.addArc(center: CGPoint(x: rect.minX + tl, y: rect.minY + tl), radius: tl, startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false) }
        path.closeSubpath()
        return path
    }
}
