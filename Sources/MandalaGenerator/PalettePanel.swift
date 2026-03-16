import SwiftUI

// MARK: - Layers Panel (right side)

struct PalettePanel: View {
    @EnvironmentObject private var appState: AppState
    @State private var draggedIndex: Int? = nil

    var body: some View {
        ScrollView(.vertical, showsIndicators: true) {
            VStack(spacing: 8) {
                HStack {
                    Text("LAYERS")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundColor(.secondary).kerning(1.2)
                    Spacer()
                    Button(action: addLayer) {
                        Image(systemName: "plus.circle.fill")
                            .foregroundColor(.accentColor)
                    }
                    .buttonStyle(.plain)
                    .disabled(appState.parameters.layers.count >= 5)
                    .help("Add layer")
                }
                .padding(.horizontal, 12)
                .padding(.top, 12)

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

                // ── Drawing (experimental) ───────────────────────────────
                if appState.showDrawingPanel {
                    Divider().padding(.horizontal, 8)

                    HStack {
                        Text("DRAWING")
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundColor(.secondary).kerning(1.2)
                        Text("experimental")
                            .font(.system(size: 8)).foregroundColor(.orange.opacity(0.7))
                        Spacer()
                    }
                    .padding(.horizontal, 12)

                    DrawingCard(
                        settings: $appState.parameters.drawingLayer,
                        isDrawingMode: $appState.isDrawingMode
                    )
                    .padding(.horizontal, 8)
                }

                Spacer(minLength: 20)
            }
        }
        .background(Color(NSColor.controlBackgroundColor))
    }

    private func addLayer() {
        let allStyles = MandalaStyle.allCases
        let nextStyle = allStyles[appState.parameters.layers.count % allStyles.count]
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
    @State private var isExpanded = true
    @State private var showPaletteEditor = false
    @State private var editingPaletteId: String? = nil

    private var palette: ColorPalette {
        let all = appState.allPalettes
        return all[max(0, min(all.count - 1, layer.paletteIndex))]
    }

    var body: some View {
        VStack(spacing: 0) {
            // ── Header ──────────────────────────────────────────────
            HStack(spacing: 6) {
                DragHandle()

                Toggle("", isOn: $layer.isEnabled)
                    .toggleStyle(.switch).scaleEffect(0.7).labelsHidden()
                    .frame(width: 32)

                // Thumbnail preview
                if let preview = appState.layerPreviews[index] {
                    Image(nsImage: preview)
                        .resizable().interpolation(.high)
                        .frame(width: 28, height: 28)
                        .cornerRadius(4)
                        .opacity(layer.isEnabled ? 1 : 0.4)
                } else {
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Color.secondary.opacity(0.12))
                        .frame(width: 28, height: 28)
                }

                // Colour strip (shorter)
                LinearGradient(
                    gradient: Gradient(stops: palette.stops.map {
                        Gradient.Stop(color: Color(nsColor: $0.1), location: $0.0)
                    }),
                    startPoint: .leading, endPoint: .trailing
                )
                .frame(height: 5).cornerRadius(2.5)
                .opacity(layer.isEnabled ? 1 : 0.3)

                Text(layer.style.displayName)
                    .font(.caption.weight(.medium))
                    .foregroundColor(layer.isEnabled ? .primary : .secondary)
                    .lineLimit(1)

                Spacer()

                Button(action: onRandomize) {
                    Image(systemName: "dice")
                        .font(.system(size: 9))
                        .foregroundColor(.blue)
                }
                .buttonStyle(.plain)
                .help("Randomize this layer")

                if canDuplicate {
                    Button(action: onDuplicate) {
                        Image(systemName: "plus.square.on.square")
                            .font(.system(size: 9))
                            .foregroundColor(.secondary)
                    }
                    .buttonStyle(.plain)
                    .help("Duplicate layer")
                }

                Button(action: { withAnimation(.easeInOut(duration: 0.18)) { isExpanded.toggle() } }) {
                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .font(.system(size: 9))
                        .foregroundColor(.secondary)
                }
                .buttonStyle(.plain)

                if canDelete {
                    Button(action: onDelete) {
                        Image(systemName: "xmark")
                            .font(.system(size: 9))
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
            .padding(.vertical, 8)
            .background(Color(NSColor.windowBackgroundColor))
            .cornerRadius(isExpanded ? 0 : 8)
            .cornerRadius(8, corners: [.topLeft, .topRight])

            // ── Body (expanded) ──────────────────────────────────────
            if isExpanded {
                VStack(spacing: 8) {
                    // Style + blend mode row
                    HStack(spacing: 6) {
                        Picker("", selection: $layer.style) {
                            ForEach(MandalaStyle.allCases) { s in
                                Label(s.displayName, systemImage: s.sfSymbol).tag(s)
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
                    CardSlider(label: "Scale",       value: $layer.scale,        range: 0.1...1.0, color: .blue)
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

// MARK: - Drawing Card

private struct DrawingCard: View {
    @Binding var settings: DrawingLayerSettings
    @Binding var isDrawingMode: Bool
    @State private var isExpanded = true

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

                    // Palette
                    Text("PALETTE")
                        .font(.system(size: 9, weight: .semibold))
                        .foregroundColor(.secondary).kerning(1.0)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 3), spacing: 4) {
                        ForEach(Array(ColorPalettes.all.enumerated()), id: \.offset) { idx, pal in
                            PaletteSwatch(palette: pal, isSelected: settings.paletteIndex == idx)
                                .onTapGesture { settings.paletteIndex = idx }
                        }
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

                    CardSlider(label: "Opacity",     value: $settings.opacity,       range: 0...1, color: .white)

                    Divider()

                    CardSlider(label: "Weight",      value: $settings.strokeWeight,  range: 0...1, color: .white)
                    CardSlider(label: "Glow",        value: $settings.glowIntensity, range: 0...1, color: .yellow)
                    CardSlider(label: "Color Drift", value: $settings.colorDrift,    range: 0...1, color: .purple)
                    CardSlider(label: "Saturation",  value: $settings.saturation,    range: 0...1, color: .pink)
                    CardSlider(label: "Brightness",  value: $settings.brightness,    range: 0...1, color: .yellow)

                    Divider()

                    // Draw mode toggle button
                    Button(action: { isDrawingMode.toggle() }) {
                        Label(isDrawingMode ? "Exit Draw Mode" : "Draw on Canvas",
                              systemImage: isDrawingMode ? "checkmark.circle.fill" : "pencil.and.outline")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .tint(isDrawingMode ? .green : .accentColor)

                    // Stroke count
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
