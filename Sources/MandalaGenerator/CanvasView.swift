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
            Button(action: {
                Task { await appState.generate() }
            }) {
                Label("Generate", systemImage: "sparkles")
            }
            .buttonStyle(.borderedProminent)
            .tint(.blue)
            .disabled(appState.isGenerating)
            .keyboardShortcut("r", modifiers: .command)

            Button(action: { appState.randomizeAll() }) {
                Label("Randomize All", systemImage: "shuffle")
            }
            .buttonStyle(.bordered)
            .disabled(appState.isGenerating)

            Button(action: { appState.saveImage() }) {
                Label("Save", systemImage: "square.and.arrow.down")
            }
            .buttonStyle(.bordered)
            .disabled(appState.currentImage == nil || appState.isGenerating)

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

            Picker("Size", selection: $appState.parameters.outputSize) {
                Text("512 px").tag(512)
                Text("800 px").tag(800)
                Text("1024 px").tag(1024)
                Text("1400 px").tag(1400)
                Text("2048 px").tag(2048)
            }
            .pickerStyle(.menu)
            .fixedSize()
            .help("Output size")

            Picker("Format", selection: $appState.parameters.outputFormat) {
                Text("PNG").tag("png")
                Text("JPG").tag("jpg")
            }
            .pickerStyle(.menu)
            .fixedSize()
            .help("Output format")

            Picker("Shape", selection: $appState.parameters.outputShape) {
                Text("Square").tag("square")
                Text("Circle").tag("circle")
                Text("Squircle").tag("squircle")
                Text("Rounded").tag("rounded")
            }
            .pickerStyle(.menu)
            .fixedSize()
            .disabled(appState.parameters.outputFormat != "png")
            .help("Crop shape — PNG only (uses transparency)")

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
                .scaleEffect(zoomScale)
                .offset(panOffset)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .gesture(
                    DragGesture()
                        .onChanged { value in
                            panOffset = CGSize(
                                width: lastPanOffset.width + value.translation.width,
                                height: lastPanOffset.height + value.translation.height
                            )
                        }
                        .onEnded { _ in
                            lastPanOffset = panOffset
                        }
                )
                .onScrollWheel { delta in
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
            Text("Press Generate to create a mandala")
                .foregroundColor(.secondary)
                .font(.title3)
            Text("Cmd+R or click Generate")
                .foregroundColor(.secondary.opacity(0.6))
                .font(.caption)
        }
    }

    // MARK: - Helpers

    private func copyImageToClipboard(_ image: NSImage) {
        NSPasteboard.general.clearContents()
        NSPasteboard.general.writeObjects([image])
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
