import SwiftUI

struct CanvasView: View {
    @EnvironmentObject private var appState: AppState

    @State private var zoomScale: CGFloat = 1.0
    @State private var panOffset: CGSize = .zero
    @State private var lastPanOffset: CGSize = .zero

    var body: some View {
        ZStack {
            Color(NSColor.underPageBackgroundColor)
                .ignoresSafeArea()

            if appState.isGenerating {
                VStack(spacing: 12) {
                    ProgressView()
                        .scaleEffect(1.4)
                    Text("Generating…")
                        .foregroundColor(.secondary)
                        .font(.headline)
                }
            } else if let image = appState.currentImage {
                imageView(image)
            } else {
                placeholderView
            }

            // Generation time overlay
            if !appState.isGenerating && appState.lastGenerationTime > 0 {
                VStack {
                    Spacer()
                    HStack {
                        Spacer()
                        Text(String(format: "Generated in %.1fs", appState.lastGenerationTime))
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .padding(6)
                            .background(Color(NSColor.windowBackgroundColor).opacity(0.8))
                            .cornerRadius(6)
                            .padding(12)
                    }
                }
            }

            // Top toolbar
            VStack {
                toolbar
                Spacer()
            }
        }
    }

    // MARK: - Toolbar

    private var toolbar: some View {
        HStack(spacing: 12) {
            if appState.isDrawingMode {
                Button(action: {
                    appState.isDrawingMode = false
                    Task { await appState.generate() }
                }) {
                    Label("Done Drawing", systemImage: "checkmark.circle.fill")
                }
                .buttonStyle(.borderedProminent)
                .tint(.green)

                Button(action: {
                    if !appState.parameters.drawingLayer.strokes.isEmpty {
                        appState.parameters.drawingLayer.strokes.removeLast()
                    }
                }) {
                    Image(systemName: "arrow.uturn.backward")
                }
                .buttonStyle(.bordered)
                .disabled(appState.parameters.drawingLayer.strokes.isEmpty)
                .help("Undo last stroke")

                Divider().frame(height: 20)
            }

            Button(action: { appState.randomizeAll() }) {
                Label("Randomize All", systemImage: "shuffle")
            }
            .buttonStyle(.bordered)
            .disabled(appState.isGenerating)
            .keyboardShortcut("r", modifiers: [.command, .shift])

            Button(action: { appState.saveSettings() }) {
                Label("Save Settings", systemImage: "doc.badge.arrow.up")
            }
            .buttonStyle(.bordered)
            .help("Save settings (⌘S)")
            .keyboardShortcut("s", modifiers: .command)

            Button(action: { appState.loadSettings() }) {
                Label("Load Settings", systemImage: "doc.badge.arrow.down")
            }
            .buttonStyle(.bordered)
            .help("Load settings (⌘O)")
            .keyboardShortcut("o", modifiers: .command)

            Divider().frame(height: 20)

            Button(action: { appState.goBack() }) {
                Image(systemName: "chevron.left")
            }
            .buttonStyle(.bordered)
            .disabled(!appState.canGoBack || appState.isGenerating)
            .help("Go back (⌘[)")
            .keyboardShortcut("[", modifiers: .command)

            Button(action: { appState.goForward() }) {
                Image(systemName: "chevron.right")
            }
            .buttonStyle(.bordered)
            .disabled(!appState.canGoForward || appState.isGenerating)
            .help("Go forward (⌘])")
            .keyboardShortcut("]", modifiers: .command)

            Divider().frame(height: 20)

            Picker("", selection: $appState.parameters.previewSize) {
                Text("512 px").tag(512)
                Text("800 px").tag(800)
                Text("1024 px").tag(1024)
                Text("1400 px").tag(1400)
                Text("2048 px").tag(2048)
            }
            .pickerStyle(.menu)
            .labelsHidden()
            .fixedSize()
            .help("Render resolution")

            Spacer()

            if zoomScale != 1.0 || panOffset != .zero {
                Button("Reset View") {
                    withAnimation(.spring()) {
                        zoomScale = 1.0
                        panOffset = .zero
                        lastPanOffset = .zero
                    }
                }
                .buttonStyle(.plain)
                .foregroundColor(.secondary)
            }

            Text(String(format: "%.0f%%", zoomScale * 100))
                .font(.caption)
                .foregroundColor(.secondary)
                .monospacedDigit()

            Divider().frame(height: 20)

            Button(action: { appState.copyToClipboard() }) {
                Image(systemName: "doc.on.clipboard")
            }
            .buttonStyle(.bordered)
            .disabled(appState.currentImage == nil)
            .help("Copy to Clipboard (⌘⇧C)")
            .keyboardShortcut("c", modifiers: [.command, .shift])

            Button(action: { appState.addFavorite() }) {
                Image(systemName: "star")
            }
            .buttonStyle(.bordered)
            .disabled(appState.currentImage == nil)
            .help("Add to Favorites")

            FavoritesButton()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(Color(NSColor.windowBackgroundColor).opacity(0.9))
    }

    // MARK: - Image view

    private func imageView(_ image: NSImage) -> some View {
        GeometryReader { geo in
            let availableSize = min(geo.size.width, geo.size.height) * 0.9
            Image(nsImage: image)
                .resizable()
                .interpolation(.high)
                .aspectRatio(contentMode: .fit)
                .frame(width: availableSize, height: availableSize)
                .mask(MandalaOutputShape(name: appState.parameters.outputShape))
                .overlay(
                    Group {
                        if appState.isDrawingMode {
                            DrawingOverlay(settings: $appState.parameters.drawingLayer)
                        }
                    }
                )
                .scaleEffect(appState.isDrawingMode ? 1.0 : zoomScale)
                .offset(appState.isDrawingMode ? .zero : panOffset)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .gesture(
                    DragGesture()
                        .onChanged { value in
                            guard !appState.isDrawingMode else { return }
                            panOffset = CGSize(
                                width: lastPanOffset.width + value.translation.width,
                                height: lastPanOffset.height + value.translation.height
                            )
                        }
                        .onEnded { _ in
                            guard !appState.isDrawingMode else { return }
                            lastPanOffset = panOffset
                        }
                )
                .onScrollWheel { delta in
                    guard !appState.isDrawingMode else { return }
                    let factor = 1.0 + delta * 0.1
                    withAnimation(.interactiveSpring()) {
                        zoomScale = max(0.1, min(10.0, zoomScale * CGFloat(factor)))
                    }
                }
                .contextMenu {
                    Button(action: { appState.saveImage() }) {
                        Label("Save Image…", systemImage: "square.and.arrow.down")
                    }
                    Button(action: { copyImageToClipboard(image) }) {
                        Label("Copy to Clipboard", systemImage: "doc.on.clipboard")
                    }
                    Divider()
                    Button(action: {
                        withAnimation(.spring()) {
                            zoomScale = 1.0
                            panOffset = .zero
                            lastPanOffset = .zero
                        }
                    }) {
                        Label("Reset View", systemImage: "arrow.counterclockwise")
                    }
                }
        }
    }

    // MARK: - Placeholder

    private var placeholderView: some View {
        VStack(spacing: 16) {
            Image(systemName: "sparkles")
                .font(.system(size: 48))
                .foregroundColor(.secondary.opacity(0.5))
            Text("Generating…")
                .foregroundColor(.secondary)
                .font(.title3)
        }
    }

    // MARK: - Helpers

    private func copyImageToClipboard(_ image: NSImage) {
        NSPasteboard.general.clearContents()
        NSPasteboard.general.writeObjects([image])
    }
}

// MARK: - Drawing overlay

struct DrawingOverlay: View {
    @Binding var settings: DrawingLayerSettings
    @State private var currentXs: [Double] = []
    @State private var currentYs: [Double] = []

    var body: some View {
        GeometryReader { geo in
            let size = geo.size
            Canvas { ctx, sz in
                drawGuides(ctx: ctx, size: sz)
                drawStoredStrokes(ctx: ctx, size: sz)
                drawCurrentStroke(ctx: ctx, size: sz)
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { v in
                        currentXs.append(max(0, min(1, Double(v.location.x / size.width))))
                        currentYs.append(max(0, min(1, Double(v.location.y / size.height))))
                    }
                    .onEnded { _ in
                        if currentXs.count >= 2 {
                            settings.strokes.append(DrawStroke(xs: currentXs, ys: currentYs))
                        }
                        currentXs = []
                        currentYs = []
                    }
            )
            .onHover { hovering in
                if hovering { NSCursor.crosshair.set() } else { NSCursor.arrow.set() }
            }
        }
    }

    private func symPoints(xs: [Double], ys: [Double], s: Int, sym: Int, size: CGSize) -> [CGPoint] {
        let cx = size.width * 0.5, cy = size.height * 0.5
        let angle = Double(s) * .pi * 2.0 / Double(sym)
        let ca = cos(angle), sa = sin(angle)
        return zip(xs, ys).map { (nx, ny) in
            let dx = nx * Double(size.width) - Double(cx)
            let dy = ny * Double(size.height) - Double(cy)
            return CGPoint(x: cx + dx * ca - dy * sa,
                           y: cy + dx * sa + dy * ca)
        }
    }

    private func makePath(points: [CGPoint]) -> Path {
        var path = Path()
        for (i, p) in points.enumerated() {
            if i == 0 { path.move(to: p) } else { path.addLine(to: p) }
        }
        return path
    }

    private func drawGuides(ctx: GraphicsContext, size: CGSize) {
        let cx = size.width * 0.5, cy = size.height * 0.5
        let radius = max(size.width, size.height)
        let sym = settings.symmetry
        for s in 0..<sym {
            let angle = Double(s) * .pi * 2.0 / Double(sym)
            var path = Path()
            path.move(to: CGPoint(x: cx, y: cy))
            path.addLine(to: CGPoint(x: cx + CGFloat(cos(angle)) * radius,
                                     y: cy + CGFloat(sin(angle)) * radius))
            ctx.stroke(path, with: .color(.white.opacity(0.12)),
                       style: StrokeStyle(lineWidth: 0.5, dash: [4, 4]))
        }
        // Center dot
        ctx.fill(Path(ellipseIn: CGRect(x: cx - 3, y: cy - 3, width: 6, height: 6)),
                 with: .color(.white.opacity(0.25)))
    }

    private func scaledCoords(xs: [Double], ys: [Double]) -> (xs: [Double], ys: [Double]) {
        let s = max(0.1, settings.scale)
        return (
            xs: xs.map { 0.5 + ($0 - 0.5) * s },
            ys: ys.map { 0.5 + ($0 - 0.5) * s }
        )
    }

    private func overlayLineWidth(canvasWidth: CGFloat) -> CGFloat {
        max(1.0, CGFloat(settings.strokeWeight) * canvasWidth * 0.015 + 1.5)
    }

    private func drawStoredStrokes(ctx: GraphicsContext, size: CGSize) {
        let palette = ColorPalettes.all[max(0, min(ColorPalettes.all.count - 1, settings.paletteIndex))]
        let sym = settings.symmetry
        let lw = overlayLineWidth(canvasWidth: size.width)
        let style = StrokeStyle(lineWidth: lw, lineCap: .round, lineJoin: .round)
        for (si, stroke) in settings.strokes.enumerated() {
            guard stroke.xs.count >= 2 else { continue }
            let t: Double = settings.strokes.count > 1
                ? (Double(si) / Double(settings.strokes.count - 1) * settings.colorDrift)
                    .truncatingRemainder(dividingBy: 1.0)
                : 0.0
            let swColor = Color(nsColor: palette.color(at: t)).opacity(0.85)
            let scaled = scaledCoords(xs: stroke.xs, ys: stroke.ys)
            for s in 0..<sym {
                let pts = symPoints(xs: scaled.xs, ys: scaled.ys, s: s, sym: sym, size: size)
                ctx.stroke(makePath(points: pts), with: .color(swColor), style: style)
            }
        }
    }

    private func drawCurrentStroke(ctx: GraphicsContext, size: CGSize) {
        guard currentXs.count >= 2 else { return }
        let sym = settings.symmetry
        let lw = overlayLineWidth(canvasWidth: size.width)
        let style = StrokeStyle(lineWidth: lw, lineCap: .round, lineJoin: .round)
        // Use the palette color the stroke will have once committed
        let palette = ColorPalettes.all[max(0, min(ColorPalettes.all.count - 1, settings.paletteIndex))]
        let nextIdx = settings.strokes.count
        let t: Double = nextIdx == 0 ? 0.0
            : settings.colorDrift.truncatingRemainder(dividingBy: 1.0)
        let strokeColor = Color(nsColor: palette.color(at: t)).opacity(0.9)
        // Apply scale so the preview exactly matches where it will land after commit
        let scaled = scaledCoords(xs: currentXs, ys: currentYs)
        for s in 0..<sym {
            let pts = symPoints(xs: scaled.xs, ys: scaled.ys, s: s, sym: sym, size: size)
            ctx.stroke(makePath(points: pts), with: .color(strokeColor), style: style)
        }
    }
}

// MARK: - Scroll wheel modifier

struct ScrollWheelModifier: ViewModifier {
    let handler: (Double) -> Void

    func body(content: Content) -> some View {
        content.background(
            ScrollWheelView(handler: handler)
        )
    }
}

struct ScrollWheelView: NSViewRepresentable {
    let handler: (Double) -> Void

    func makeNSView(context: Context) -> NSView {
        let view = InternalScrollView()
        view.handler = handler
        return view
    }

    func updateNSView(_ nsView: NSView, context: Context) {}

    class InternalScrollView: NSView {
        var handler: ((Double) -> Void)?

        override func scrollWheel(with event: NSEvent) {
            let delta = event.deltaY
            if delta != 0 {
                handler?(Double(delta))
            }
        }
    }
}

extension View {
    func onScrollWheel(_ handler: @escaping (Double) -> Void) -> some View {
        modifier(ScrollWheelModifier(handler: handler))
    }
}

// MARK: - Output shape mask (shared between preview and export)

struct MandalaOutputShape: Shape {
    let name: String

    func path(in rect: CGRect) -> Path {
        switch name {
        case "circle":
            return Path(ellipseIn: rect)
        case "squircle":
            return squirclePath(in: rect, exponent: 4.0)
        case "rounded":
            let r = min(rect.width, rect.height) * 0.08
            return Path(roundedRect: rect, cornerRadius: r)
        default:
            return Path(rect)
        }
    }

    private func squirclePath(in rect: CGRect, exponent: CGFloat) -> Path {
        var path = Path()
        let cx = rect.midX, cy = rect.midY
        let rx = rect.width * 0.5, ry = rect.height * 0.5
        let inv = 2.0 / exponent
        let steps = 512
        for i in 0...steps {
            let t = CGFloat(i) / CGFloat(steps) * 2 * .pi
            let cosT = cos(t), sinT = sin(t)
            let x = cx + rx * (cosT < 0 ? -1 : 1) * pow(abs(cosT), inv)
            let y = cy + ry * (sinT < 0 ? -1 : 1) * pow(abs(sinT), inv)
            if i == 0 { path.move(to: CGPoint(x: x, y: y)) }
            else       { path.addLine(to: CGPoint(x: x, y: y)) }
        }
        path.closeSubpath()
        return path
    }
}

// MARK: - Favorites Button

struct FavoritesButton: View {
    @EnvironmentObject private var appState: AppState
    @State private var showPopover = false

    var body: some View {
        Button(action: { showPopover.toggle() }) {
            Image(systemName: appState.favorites.isEmpty ? "star.slash" : "star.fill")
                .foregroundColor(appState.favorites.isEmpty ? .secondary : .yellow)
        }
        .buttonStyle(.bordered)
        .help("Favorites")
        .popover(isPresented: $showPopover, arrowEdge: .bottom) {
            FavoritesPopover(isPresented: $showPopover)
                .environmentObject(appState)
        }
    }
}

// MARK: - Favorites Popover

struct FavoritesPopover: View {
    @EnvironmentObject private var appState: AppState
    @Binding var isPresented: Bool

    private let columns = [GridItem(.adaptive(minimum: 120, maximum: 140), spacing: 10)]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("Favorites")
                    .font(.headline)
                Spacer()
                Button(action: { isPresented = false }) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.secondary)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 16)
            .padding(.top, 14)
            .padding(.bottom, 10)

            Divider()

            if appState.favorites.isEmpty {
                VStack(spacing: 10) {
                    Image(systemName: "star.slash")
                        .font(.system(size: 32))
                        .foregroundColor(.secondary.opacity(0.4))
                    Text("No favorites yet")
                        .foregroundColor(.secondary)
                        .font(.callout)
                    Text("Press ★ while viewing an image to save it.")
                        .foregroundColor(.secondary.opacity(0.7))
                        .font(.caption)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 40)
                .padding(.horizontal, 24)
            } else {
                ScrollView {
                    LazyVGrid(columns: columns, spacing: 10) {
                        ForEach(appState.favorites) { fav in
                            FavoriteThumbnailView(favorite: fav, onApply: {
                                appState.applyFavorite(fav)
                                isPresented = false
                            }, onDelete: {
                                appState.removeFavorite(id: fav.id)
                            })
                        }
                    }
                    .padding(14)
                }
                .frame(maxHeight: 380)
            }
        }
        .frame(width: 340)
        .background(Color(NSColor.windowBackgroundColor))
    }
}

// MARK: - Favorite Thumbnail

struct FavoriteThumbnailView: View {
    let favorite: Favorite
    let onApply: () -> Void
    let onDelete: () -> Void

    @State private var isHovered = false

    private var thumbnail: NSImage? {
        NSImage(data: favorite.thumbnailData)
    }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Button(action: onApply) {
                ZStack {
                    if let img = thumbnail {
                        Image(nsImage: img)
                            .resizable()
                            .aspectRatio(1, contentMode: .fill)
                            .clipped()
                    } else {
                        Color.secondary.opacity(0.15)
                        Image(systemName: "photo")
                            .foregroundColor(.secondary)
                    }
                }
                .frame(width: 120, height: 120)
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .strokeBorder(
                            isHovered ? Color.accentColor : Color.white.opacity(0.08),
                            lineWidth: isHovered ? 2 : 1
                        )
                )
                .scaleEffect(isHovered ? 1.03 : 1.0)
                .animation(.spring(response: 0.2), value: isHovered)
            }
            .buttonStyle(.plain)

            Button(action: onDelete) {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 16))
                    .foregroundColor(.white)
                    .background(Color.black.opacity(0.5).clipShape(Circle()))
            }
            .buttonStyle(.plain)
            .padding(5)
            .opacity(isHovered ? 1 : 0)
            .animation(.easeInOut(duration: 0.15), value: isHovered)
        }
        .frame(width: 120, height: 120)
        .onHover { isHovered = $0 }
    }
}
