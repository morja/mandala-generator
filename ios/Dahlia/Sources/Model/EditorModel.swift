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
    private(set) var params: MandalaParameters
    private(set) var selected = 0
    private(set) var preview: CGImage?
    private(set) var rendering = true
    private(set) var previewError: String?
    private(set) var favorites: [Favorite]
    var message: Message?
    private(set) var saving: SaveProgress?

    @ObservationIgnored private let engine = Engine()

    // ── Preview pipeline state ──
    @ObservationIgnored private var previewTask: Task<Void, Never>?
    @ObservationIgnored private var ticket: RenderTicket?
    @ObservationIgnored private var recent = RecentPictures(capacity: Timing.recentPreviews)
    /// What the last finished preview was drawn from, and how long it took per layer it drew.
    @ObservationIgnored private var renderedParams: MandalaParameters?
    @ObservationIgnored private var millisPerLayer: Int64 = 0
    /// When the settings last changed, for the latency log while a slider is dragged.
    @ObservationIgnored private var changedAt = Date()
    @ObservationIgnored private var saveTask: Task<Void, Never>?

    /// Android's fast preview, on by default; the switch comes with the menu (WP-7).
    let fastPreview = true

    init() {
        // Saved settings, else the starter the icon was made from, else the first starter.
        params = SettingsStore.load() ?? Starters.firstMandala ?? Starters.all.first?.parameters ?? IosBridge.shared.decode(json: "{}")
        favorites = FavoritesStore.loadOrSeed()
        renderPreview()
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

    private func change(_ next: MandalaParameters) {
        guard next != params else { return }
        params = next
        settingsChanged()
    }

    /// Opens other settings and selects the first layer, as Android's `replace`.
    private func replace(_ next: MandalaParameters) {
        params = next
        selected = 0
        settingsChanged()
    }

    private func settingsChanged() {
        changedAt = Date()
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
    private func renderPreview() {
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

    // ── Favourites ──

    /// Newest first, as on Android and the Mac; needs a preview for the thumbnail.
    func addFavorite() {
        guard let image = preview, !rendering else { return }
        let favorite = IosBridge.shared.makeFavorite(
            id: UUID().uuidString.uppercased(), params: params, thumbnailBase64: Pictures.thumbnail(image),
            nowMillis: Int64(Date().timeIntervalSince1970 * 1000)
        )
        favorites.insert(favorite, at: 0)
        FavoritesStore.save(favorites)
    }

    var canAddFavorite: Bool { preview != nil && !rendering }

    /// Whether the mandala as it is now is already a favourite (the star then stays filled).
    var isFavorite: Bool { favorites.contains { $0.parameters == params } }

    func applyFavorite(_ favorite: Favorite) {
        // Its thumbnail stands in until the preview is drawn.
        if recent[favorite.parameters] == nil, let thumb = Pictures.decodeThumbnail(favorite.thumbnailData) {
            preview = thumb
        }
        replace(favorite.parameters)
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
    static let recentPreviews = 30
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
