import CoreGraphics
import Foundation
@preconcurrency import MandalaCore

// WP-7: history, the dice, the menu's comparison, favourites with Undo, files and the play field. The rules are the
// shared module's (`IosShell`, from android/editing); what is written here is the iPhone's state around them and the
// parts Android keeps in `EditorViewModel` (messages with Undo, the order a deleted favourite comes back in).
extension EditorModel {
    // ── Start ──

    /// Called at the end of `init`: the history starts with the settings of the start, and the last picture stands in.
    func shellStart() {
        scheduleHistory()
        let p = params
        Task {
            let last = await Task.detached(priority: .userInitiated) { PreviewStore.load(for: p) }.value
            if preview == nil, params == p, let last { preview = last }
        }
    }

    /// Called whenever the settings changed.
    func shellSettingsChanged() { scheduleHistory() }

    // ── History: every state that settles goes in, 700 ms after the last change (Android's `HISTORY_DELAY_MS`) ──

    private func scheduleHistory() {
        shell.historyTask?.cancel()
        let snapshot = params
        shell.historyTask = Task {
            try? await Task.sleep(for: Timing.historyDelay)
            guard !Task.isCancelled else { return }
            recordHistory(snapshot)
        }
    }

    private func recordHistory(_ state: MandalaParameters) {
        shell.history.record(state: state)
        shell.refreshHistoryState()
        // One save at a time, the newest state last (Android writes in turn).
        let entries = shell.history.entries
        let previous = shell.historySaveTask
        shell.historySaveTask = Task.detached(priority: .utility) {
            await previous?.value
            HistoryStore.save(entries)
        }
    }

    func goBack() { navigate { shell.history.back() } }

    func goForward() { navigate { shell.history.forward() } }

    private func navigate(_ step: () -> MandalaParameters?) {
        // Another mandala: the text colour sheet closes and keeps its colours, so Cancel never paints old ones on it.
        closeTextColourSheet(keep: true)
        shell.historyTask?.cancel()
        recordHistory(params) // a change younger than the history delay is kept too
        guard let target = step() else { return }
        params = target
        selected = max(0, min(selected, target.layers.count - 1))
        settingsChanged()
        shell.refreshHistoryState()
    }

    /// Favourites are saved in turn off the main thread, the newest list last (Android: `collectLatest` on IO).
    func saveFavoritesLater() {
        let list = favorites
        let previous = favoritesSaveTask
        favoritesSaveTask = Task.detached(priority: .utility) {
            await previous?.value
            FavoritesStore.save(list)
        }
    }

    // ── The dice ──

    /// The top bar's die: new layers and background (Android's `surprise`).
    func surprise() {
        closeTextColourSheet(keep: true)
        change(IosShell.shared.surprise(current: params, pro: proOwned))
        selected = 0
    }

    /// Makes a new mandala of this kind and draws it small; the current one stays.
    func tryNew(_ kind: NewKind) {
        let next = IosShell.shared.nextMandala(current: params, kind: kind, pro: proOwned)
        // The last picture stays while the next one is drawn.
        shell.candidate = Candidate(kind: kind, params: next, image: shell.candidate?.image)
        shell.candidateTicket?.cancel()
        shell.candidateTask?.cancel()
        let ticket = RenderTicket()
        shell.candidateTicket = ticket
        shell.candidateTask = Task {
            guard let result = await engine.candidate(next, fast: fastPreview, ticket: ticket), !ticket.isCancelled() else { return }
            guard shell.candidate?.params == next else { return }
            // A picture that cannot be made keeps the old one (Android: the previous image stays).
            if let image = result.cgImage { shell.candidate?.image = image }
            shell.candidate?.rendering = false
        }
    }

    /// Replaces the mandala with the new one; its small picture stands in until the preview is drawn.
    func useCandidate() {
        guard let c = shell.candidate else { return }
        closeCandidate()
        if recent[c.params] == nil, let image = c.image {
            preview = image
            rendering = true
        }
        replace(c.params)
    }

    func closeCandidate() {
        shell.candidateTicket?.cancel()
        shell.candidateTask?.cancel()
        shell.candidate = nil
    }

    // ── Favourites ──

    var canAddFavorite: Bool { preview != nil && !rendering }

    /// Whether the mandala as it is now is already a favourite (the star then stays filled).
    var isFavorite: Bool { favorites.contains { $0.parameters == params } }

    /// From the menu: adds the mandala and says so.
    func addFavorite() { _ = keepFavorite(announce: true) }

    /// From the favourites sheet, where the new favourite shows at once.
    func addFavoriteQuietly() { _ = keepFavorite(announce: false) }

    /// The new favourite's id, or nil without a preview to make its thumbnail from. Newest first, as on Android and the Mac.
    @discardableResult
    func keepFavorite(announce: Bool) -> String? {
        // The thumbnail must be of the settings as they are now, so not while a preview is being drawn.
        guard let image = preview, !rendering else { return nil }
        let favorite = IosBridge.shared.makeFavorite(
            id: UUID().uuidString.uppercased(), params: params, thumbnailBase64: Pictures.thumbnail(image),
            nowMillis: Int64(Date().timeIntervalSince1970 * 1000)
        )
        favorites = IosShell.shared.addFavorite(list: favorites, favorite: favorite)
        if announce { message = Message(text: Texts.favouriteAdded) }
        return favorite.id
    }

    func applyFavorite(_ favorite: Favorite) {
        // Its thumbnail stands in until the preview is drawn.
        if recent[favorite.parameters] == nil, let thumb = Pictures.decodeThumbnail(favorite.thumbnailData) {
            preview = thumb
        }
        replace(favorite.parameters)
    }

    /// Deletes several favourites at once; one "Undo" brings them all back in their places.
    func removeFavorites(_ ids: Set<String>) {
        let before = favorites
        let count = before.filter { ids.contains($0.id) }.count
        guard count > 0 else { return }
        favorites = before.filter { !ids.contains($0.id) }
        message = Message(text: Texts.favouritesDeleted(count), actionLabel: Texts.undo) { [self] in
            let kept = Set(favorites.map(\.id))
            let beforeIDs = Set(before.map(\.id))
            // The old order, with favourites added since then kept at the end.
            favorites = before.filter { ids.contains($0.id) || kept.contains($0.id) } + favorites.filter { !beforeIDs.contains($0.id) }
        }
    }

    // ── Files of the macOS app ──

    /// Opens a settings file or a favourites file saved by the macOS app (or by this app).
    func openFile(_ url: URL) {
        Task {
            let text = await Task.detached(priority: .userInitiated) { () -> String? in
                let scoped = url.startAccessingSecurityScopedResource()
                defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                // Lossy, as Android's decodeToString: a stray byte does not reject the file.
                return (try? Data(contentsOf: url)).map { String(decoding: $0, as: UTF8.self) }
            }.value
            guard let text else {
                message = Message(text: Texts.fileUnreadable)
                return
            }
            let opened = await Task.detached(priority: .userInitiated) { IosShell.shared.decodeFile(text: text) }.value
            guard let opened else {
                message = Message(text: Texts.fileUnknown)
                return
            }
            if let settings = opened.settings {
                if settings.layers.isEmpty {
                    message = Message(text: Texts.fileNoLayers)
                } else {
                    replace(settings)
                }
            } else if let imported = opened.favorites {
                let current = favorites
                let merged = await Task.detached(priority: .userInitiated) { IosShell.shared.mergeFavorites(list: current, imported: imported) }.value
                favorites = merged.list
                let added = Int(merged.added)
                let already = imported.count - added
                let done = Texts.plural("message_favourites_imported", added)
                message = Message(text: already > 0 ? Texts.favouritesImportedNote(done, Texts.plural("message_favourites_already_there", already)) : done)
            }
        }
    }

    /// The settings as the file "Save settings" writes, and the name it suggests.
    var settingsFileText: String { IosBridge.shared.encode(params: params) }

    var suggestedSettingsName: String {
        let name = IosEditing.shared.fileName(params: params)
        return (name.hasSuffix(".png") ? String(name.dropLast(4)) : name)
    }

    func settingsSaved(_ ok: Bool) {
        message = Message(text: ok ? Texts.settingsSaved : Texts.settingsFailed)
    }

    // ── Play field (UX-1 19) ──

    func openPlay() {
        shell.playBase = params
        shell.play = PlayState()
        newPlayLandscape()
    }

    /// Closes the field; the mandala stays as played, and Back in the editor undoes it.
    func closePlay() {
        shell.play = nil
        shell.playBase = nil
        shell.playLand = nil
    }

    func setPlayMode(_ mode: PlayMode) {
        guard var state = shell.play else { return }
        state.mode = mode
        state.keptID = nil
        shell.play = state
        applyPlay()
    }

    /// One finger on the field, as fractions of its width and height.
    func playTouch(x: Double, y: Double) {
        guard var state = shell.play else { return }
        state.x = min(1, max(0, x))
        state.y = min(1, max(0, y))
        state.keptID = nil
        shell.play = state
        applyPlay()
    }

    /// Back to the middle: the mandala as it was when the field opened (without its effects while they are off).
    func resetPlay() {
        guard let base = shell.playBase, var state = shell.play else { return }
        state.x = 0.5
        state.y = 0.5
        state.keptID = nil
        shell.play = state
        change(IosShell.shared.playMiddle(base: base, effects: state.effects))
    }

    /// The Effects switch beside New landscape: the scene effects on or off, for the picture as it is now.
    func setPlayEffects(_ on: Bool) {
        guard var state = shell.play, state.effects != on else { return }
        state.effects = on
        state.keptID = nil
        shell.play = state
        applyPlay()
    }

    /// The star: adds the picture as played to the favourites and fills; tapped again it takes that favourite out again.
    func playKeep() {
        guard var state = shell.play else { return }
        if let id = state.keptID {
            favorites = IosShell.shared.removeFavorite(list: favorites, id: id)
            state.keptID = nil
            shell.play = state
        } else if let id = keepFavorite(announce: false) {
            state.keptID = id
            shell.play = state
        }
    }

    /// The landscape: a new one around the same middle; the point hops a step out of the middle, where it would
    /// show nothing. The compass: start from here.
    func playAgain() {
        guard var state = shell.play else { return }
        if state.mode == .landscape {
            let hop = IosShell.shared.playMiddleHop(touchX: state.x, touchY: state.y).map(\.doubleValue)
            state.x = hop[0]
            state.y = hop[1]
            state.keptID = nil
            shell.play = state
            newPlayLandscape()
            applyPlay()
        } else {
            guard let base = shell.playBase, let land = shell.playLand else { return }
            shell.playBase = IosShell.shared.playMiddleFrom(base: base, compass: true, land: land, x: state.x, y: state.y, effects: state.effects)
            state.x = 0.5
            state.y = 0.5
            state.keptID = nil
            shell.play = state
        }
    }

    /// A new landscape around the mandala as it is now; it is explored by moving, not shown in advance.
    private func newPlayLandscape() {
        guard let base = shell.playBase else { return }
        shell.playLand = IosShell.shared.makeLandscape(base: base)
    }

    private func applyPlay() {
        guard let base = shell.playBase, let land = shell.playLand, let state = shell.play else { return }
        change(IosShell.shared.play(base: base, compass: state.mode == .compass, land: land, x: state.x, y: state.y, effects: state.effects))
    }

    // ── The last picture at the next start ──

    func savePreviewLater(_ p: MandalaParameters, _ image: CGImage) {
        shell.previewSaveTask?.cancel()
        let previous = shell.previewSaveTask
        shell.previewSaveTask = Task.detached(priority: .utility) {
            await previous?.value
            try? await Task.sleep(for: .seconds(1))
            guard !Task.isCancelled else { return }
            PreviewStore.save(p, image)
        }
    }
}
