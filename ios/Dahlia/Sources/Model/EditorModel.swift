import CoreGraphics
import Foundation
@preconcurrency import MandalaCore
import Observation

/// A short message at the bottom of the editor (Dahlia's own part; iOS has no snackbar).
struct Message: Identifiable, Equatable {
    let id = UUID()
    let text: String
    var actionLabel: String? = nil
    var action: (@MainActor () -> Void)? = nil

    static func == (a: Message, b: Message) -> Bool { a.id == b.id }
}

/// The save sheet's state while a picture is made and added to Photos.
struct SaveProgress: Equatable {
    /// 0…1 while drawing; nil while Photos is at work.
    var share: Double?
}

/// Holds the settings, the selected layer and the favourites; renders the preview after each change
/// (latest wins), keeps the settings across restarts and saves pictures, as Android's `EditorViewModel`
/// does for these parts (PLAN-4 WP-1).
@MainActor
@Observable
final class EditorModel {
    var params: MandalaParameters
    var selected = 0
    var preview: CGImage?
    var rendering = true
    private(set) var previewError: String?
    /// Saved on every change (the list rules are the shared module's `Favorites`).
    var favorites: [Favorite] { didSet { FavoritesStore.save(favorites) } }
    /// History, dice, play field and the picture's size (WP-7); its operations are in EditorModel+Shell.swift.
    let shell = ShellState()
    var message: Message?
    private(set) var saving: SaveProgress?
    /// The user's own palettes in the order made (ADR-0021).
    private(set) var myPalettes: [CustomPalette]
    /// The preview without the scene effects, while it matches the settings (hold the picture to see it).
    private(set) var plainPreview: CGImage?
    /// Android's fast preview (on by default) and the small pictures beside the styles; kept across starts.
    private(set) var fastPreview: Bool
    private(set) var chipPreviews: Bool
    /// Whether Pro is owned: the purchase comes in WP-9, so Pro marks show and nothing is unlocked.
    let proOwned = false

    @ObservationIgnored let engine = Engine()

    // ── Preview pipeline state ──
    @ObservationIgnored private var previewTask: Task<Void, Never>?
    @ObservationIgnored private var ticket: RenderTicket?
    @ObservationIgnored var recent = RecentPictures(capacity: Timing.recentPreviews)
    /// What the last finished preview was drawn from, and how long it took per layer it drew.
    @ObservationIgnored private var renderedParams: MandalaParameters?
    @ObservationIgnored private var millisPerLayer: Int64 = 0
    /// When the settings last changed, for the latency log while a slider is dragged.
    @ObservationIgnored private var changedAt = Date()
    @ObservationIgnored private var saveTask: Task<Void, Never>?
    @ObservationIgnored private var plainTask: Task<Void, Never>?
    @ObservationIgnored private var plainTicket: RenderTicket?
    @ObservationIgnored private var plainFor: (params: MandalaParameters, fast: Bool)?

    private static let fastPreviewKey = "fastPreview"
    private static let chipPreviewsKey = "chipPreviews"

    init() {
        // Saved settings, else the starter the icon was made from, else the first starter.
        params = SettingsStore.load() ?? Starters.firstMandala ?? Starters.all.first?.parameters ?? IosBridge.shared.decode(json: "{}")
        favorites = FavoritesStore.loadOrSeed()
        myPalettes = PalettesStore.load()
        fastPreview = UserDefaults.standard.object(forKey: Self.fastPreviewKey) as? Bool ?? true
        chipPreviews = UserDefaults.standard.object(forKey: Self.chipPreviewsKey) as? Bool ?? true
        renderPreview()
        shellStart()
    }

    // ── Editing (the rules are the shared module's, android/editing, through IosEditing) ──

    func select(_ index: Int) {
        selected = max(0, min(index, params.layers.count - 1))
    }

    var layer: StyleLayer { params.layers[min(selected, params.layers.count - 1)] }

    func setStyle(_ style: MandalaStyle) { change(IosEditing.shared.withStyle(params: params, layer: Int32(selected), style: style)) }

    func setPalette(_ index: Int) { change(IosEditing.shared.withPalette(params: params, layer: Int32(selected), index: Int32(index))) }

    func setVisible(_ index: Int, _ visible: Bool) { change(IosEditing.shared.withVisible(params: params, layer: Int32(index), visible: visible)) }

    func value(_ slider: LayerSlider) -> Double { IosEditing.shared.value(params: params, layer: Int32(selected), slider: slider) }

    func setValue(_ slider: LayerSlider, _ value: Double) {
        change(IosEditing.shared.withValue(params: params, layer: Int32(selected), slider: slider, value: value))
    }

    /// Any change of the settings from a control; nothing happens when they stay the same.
    func apply(_ next: MandalaParameters) { change(next) }

    func setBlend(_ mode: LayerBlendMode) { change(IosEditing.shared.withBlend(params: params, layer: Int32(selected), mode: mode)) }

    // Layers: the shared rules give the new settings and the layer to select.

    func addLayer() { layerOperation(IosEditing.shared.addLayer(params: params)) }

    func duplicateSelected() { layerOperation(IosEditing.shared.duplicateLayer(params: params, layer: Int32(selected))) }

    func removeSelected() { layerOperation(IosEditing.shared.removeLayer(params: params, layer: Int32(selected))) }

    func moveLayer(_ index: Int, by delta: Int) { layerOperation(IosEditing.shared.moveLayer(params: params, layer: Int32(index), delta: Int32(delta))) }

    private func layerOperation(_ edited: Edited) {
        let before = params
        selected = max(0, min(Int(edited.selected), edited.params.layers.count - 1))
        if edited.params != before { params = edited.params; settingsChanged() }
    }

    /// The layer die: a new look for the selected layer.
    func randomizeSelected() { change(IosEditing.shared.randomLayer(params: params, layer: Int32(selected))) }

    /// The Style group's variation: same style, other values.
    func varySelected() { change(IosEditing.shared.randomVariant(params: params, layer: Int32(selected))) }

    /// Same look, a new arrangement of the selected layer.
    func reseedSelected() { change(IosEditing.shared.reseedLayer(params: params, layer: Int32(selected))) }

    func change(_ next: MandalaParameters) {
        guard next != params else { return }
        params = next
        settingsChanged()
    }

    /// Opens other settings and selects the first layer, as Android's `replace`.
    func replace(_ next: MandalaParameters) {
        params = next
        selected = 0
        settingsChanged()
    }

    func settingsChanged() {
        changedAt = Date()
        shellSettingsChanged()
        dropPlainPreview()
        renderPreview()
        // Saved once the settings rest a moment (Android: 400 ms).
        saveTask?.cancel()
        let snapshot = params
        saveTask = Task.detached(priority: .utility) {
            try? await Task.sleep(for: Timing.saveDelay)
            guard !Task.isCancelled else { return }
            SettingsStore.save(snapshot)
        }
    }

    // ── Preview: latest wins (PLAN-4 section 3.2) ──

    /// Every change cancels the running render through its ticket; a state shown recently comes back at
    /// once; anything else is drawn once the settings rest for 150 ms, with a small rough picture first
    /// when it is expected to take more than a second (Android's `collectLatest` pipeline).
    func renderPreview() {
        ticket?.cancel()
        previewTask?.cancel()
        let p = params
        if let known = recent[p] {
            preview = known
            rendering = false
            previewError = nil
            return
        }
        rendering = true
        let ticket = RenderTicket()
        self.ticket = ticket
        previewTask = Task {
            try? await Task.sleep(for: Timing.debounce)
            guard !Task.isCancelled else { return }
            let redrawn = redrawnLayers(p)
            if redrawn >= Timing.quickMinLayers && millisPerLayer * Int64(redrawn) > Timing.quickAfterMs,
               let quick = await engine.quick(p, ticket: ticket), !ticket.isCancelled() {
                preview = quick.cgImage
            }
            guard let result = await engine.preview(p, fast: fastPreview, ticket: ticket), !ticket.isCancelled(),
                  let image = result.cgImage else { return }
            preview = image
            rendering = false
            previewError = nil
            recent[p] = image
            savePreviewLater(p, image)
            renderedParams = p
            millisPerLayer = result.millis / Int64(max(1, redrawn))
            let latency = Int(Date().timeIntervalSince(changedAt) * 1000)
            Engine.trace("preview render=\(result.millis)ms shownAfterChange=\(latency)ms redrawn=\(redrawn)")
        }
    }

    /// Layers the next preview must draw again: those not in the last drawn one (the cache keeps the others).
    private func redrawnLayers(_ p: MandalaParameters) -> Int {
        let before = renderedParams.flatMap { $0.seed == p.seed ? $0.layers : nil } ?? []
        return p.layers.filter { $0.isEnabled && !before.contains($0) }.count
    }

    func memoryWarning() {
        recent.removeAll()
        Task { await engine.dropCache() }
    }

    // ── Hold to compare: the picture without the scene effects ──

    private func dropPlainPreview() {
        plainTicket?.cancel()
        plainTask?.cancel()
        plainFor = nil
        plainPreview = nil
    }

    /// Draws the preview without the effects once, when a finger starts holding the picture; kept until the
    /// settings change. The layer images come from the engine's cache, so only the picture is put together again.
    func preparePlainPreview() {
        let p = params
        guard p.effectsLayer.isEnabled, plainPreview == nil else { return }
        if let plainFor, plainFor.params == p, plainFor.fast == fastPreview { return }
        plainTicket?.cancel()
        plainTask?.cancel()
        let ticket = RenderTicket()
        plainTicket = ticket
        plainFor = (p, fastPreview)
        let fast = fastPreview
        plainTask = Task {
            guard let result = await engine.preview(IosEditing.shared.withoutEffects(params: p), fast: fast, ticket: ticket),
                  !ticket.isCancelled(), let image = result.cgImage else {
                // Not drawn: allow another try for the same settings.
                if !ticket.isCancelled() { plainFor = nil }
                return
            }
            // Only while it still belongs to the settings shown.
            if params == p, fastPreview == fast { plainPreview = image }
        }
    }

    // ── Appearance of the editor ──

    func setFastPreview(_ on: Bool) {
        guard on != fastPreview else { return }
        fastPreview = on
        UserDefaults.standard.set(on, forKey: Self.fastPreviewKey)
        // The kept previews were drawn with the other glow.
        recent.removeAll()
        dropPlainPreview()
        renderPreview()
    }

    func setChipPreviews(_ on: Bool) {
        chipPreviews = on
        UserDefaults.standard.set(on, forKey: Self.chipPreviewsKey)
    }

    // ── Own palettes (ADR-0021) ──

    nonisolated static func newPaletteID() -> String { UUID().uuidString.uppercased() }

    private func changeMyPalettes(_ next: [CustomPalette]) {
        myPalettes = next
        Task.detached(priority: .utility) { PalettesStore.save(next) }
    }

    /// Saves an own palette, new or changed; layers and the emblem using it take the new version.
    func saveMyPalette(_ edited: CustomPalette) {
        // The Mac's palettes.json needs an id on every stop.
        let stops = edited.stops.map { $0.id.isEmpty ? CustomPaletteStop(id: Self.newPaletteID(), position: $0.position, hue: $0.hue, saturation: $0.saturation, brightness: $0.brightness) : $0 }
        let palette = CustomPalette(id: edited.id, name: edited.name, stops: stops)
        changeMyPalettes(IosEditing.shared.putPalette(list: myPalettes, palette: palette))
        change(IosEditing.shared.withPaletteSaved(params: params, saved: palette))
    }

    /// Deletes an own palette; layers using it go back to the built-in palette that looks most like it.
    func deleteMyPalette(_ id: String) {
        changeMyPalettes(IosEditing.shared.removePalette(list: myPalettes, id: id))
        change(IosEditing.shared.withPaletteDeleted(params: params, id: id))
    }

    /// Keeps a palette that came with a favourite or a file; a new id if one of the user's has its id.
    func keepPalette(_ palette: CustomPalette) {
        let known = myPalettes.contains { $0.id == palette.id } || palette.id.isEmpty
        let id = known ? Self.newPaletteID() : palette.id
        let stops = palette.stops.map { $0.id.isEmpty ? CustomPaletteStop(id: Self.newPaletteID(), position: $0.position, hue: $0.hue, saturation: $0.saturation, brightness: $0.brightness) : $0 }
        let kept = CustomPalette(id: id, name: palette.name, stops: stops)
        changeMyPalettes(IosEditing.shared.putPalette(list: myPalettes, palette: kept))
        change(IosEditing.shared.withPaletteKept(params: params, old: palette, kept: kept))
    }

    /// Colours from the picture as it is shown now, dark to bright; nil while there is no picture.
    func coloursFromPicture() async -> [CustomPaletteStop]? {
        guard let image = preview else { return nil }
        return await Task.detached(priority: .userInitiated) { () -> [CustomPaletteStop]? in
            guard let rgba = Pictures.rgba(image) else { return nil }
            return IosEditing.shared.coloursFromPicture(rgba: rgba).map {
                CustomPaletteStop(id: EditorModel.newPaletteID(), position: $0.position, hue: $0.hue, saturation: $0.saturation, brightness: $0.brightness)
            }
        }.value
    }

    // ── Background picture ──

    /// Uses a picked photo or file as the background (copied into the app's folder).
    func useBackgroundPicture(_ data: Data) {
        Task {
            do {
                let url = try await Task.detached(priority: .userInitiated) { try BackgroundPictures.store(data) }.value
                change(IosEditing.shared.withBackgroundPicture(params: params, url: url))
            } catch {
                message = Message(text: Texts.pictureFailed(error.localizedDescription))
            }
        }
    }

    // ── Saving to Photos ──

    /// The square picture at 1024 px, as Android's free export, added to Photos with add-only access.
    func saveToPhotos(onDone: @escaping @MainActor () -> Void) {
        guard saving == nil else { return }
        let snapshot = params
        saving = SaveProgress(share: 0)
        Task {
            defer { saving = nil }
            // Asked before drawing, so a refusal costs no render.
            guard await PhotoLibrary.mayAdd() else {
                message = Message(text: Texts.photosDenied, actionLabel: Texts.openSettings) { Links.openSettings() }
                return
            }
            let ticket = RenderTicket()
            let size = Int(IosBridge.shared.EXPORT_SIZE)
            let result = await engine.square(snapshot, size: size, ticket: ticket) { percent in
                Task { @MainActor in
                    if self.saving != nil { self.saving = SaveProgress(share: Double(percent) / 100) }
                }
            }
            guard let image = result?.cgImage, let png = Pictures.png(image) else {
                message = Message(text: Texts.saveFailed)
                return
            }
            saving = SaveProgress(share: nil)
            do {
                try await PhotoLibrary.addPNG(png, fileName: IosEditing.shared.fileName(params: snapshot))
                message = Message(text: Texts.savedToPhotos(size))
                onDone()
            } catch PhotoLibrary.Failure.denied {
                message = Message(text: Texts.photosDenied, actionLabel: Texts.openSettings) { Links.openSettings() }
            } catch {
                message = Message(text: Texts.saveFailed)
            }
        }
    }
}

/// Timings and limits of the preview, as Android's `EditorViewModel`; the rough-first limits come from the shared `Editing`.
enum Timing {
    static let debounce = Duration.milliseconds(150)
    static let saveDelay = Duration.milliseconds(400)
    /// How long the settings rest before they go into the history (Android's `HISTORY_DELAY_MS`).
    static let historyDelay = Duration.milliseconds(700)
    static let recentPreviews = 30
    /// How long the picture is held before it shows without effects (Android's long press).
    static let holdSeconds = 0.4
    static let quickMinLayers = Int(Editing.shared.QUICK_MIN_LAYERS)
    static let quickAfterMs: Int64 = Editing.shared.QUICK_AFTER_MS
}

/// Pictures rendered recently, by the settings they show; the oldest goes first.
struct RecentPictures {
    let capacity: Int
    private var order: [MandalaParameters] = []
    private var images: [MandalaParameters: CGImage] = [:]

    init(capacity: Int) { self.capacity = capacity }

    subscript(key: MandalaParameters) -> CGImage? {
        get { images[key] }
        set {
            order.removeAll { $0 == key }
            if let newValue {
                images[key] = newValue
                order.append(key)
                if order.count > capacity { images[order.removeFirst()] = nil }
            } else {
                images[key] = nil
            }
        }
    }

    mutating func removeAll() {
        order.removeAll()
        images.removeAll()
    }
}
