import BackgroundTasks
import CoreGraphics
import CryptoKit
import Foundation
@preconcurrency import MandalaCore
import WidgetKit

// The app's side of "Mandala of the day" (PLAN-4 WP-10, ADR-0020 amended for the iPhone): whenever the app runs it
// renders the pictures of the next seven days of the daily turn and of every fixed favourite that is placed into the
// App Group, writes the index the extension reads, and asks WidgetKit for new timelines when the index changed
// (F8 a). Android draws each widget in a job; here the app prepares the pictures and the extension only shows them.

/// What the widgets are drawn from, taken from the editor on the main thread.
struct WidgetInputs: Sendable {
    let favourites: [Favorite]
    /// The daily wallpaper's ticked favourites; nil for all (Android's `DailyWallpaper` choice).
    let chosen: Set<String>?
    let pro: Bool
    let appLight: Bool
    let quoteLanguages: [String]
    let mark: Bool
    /// Pixels per point of this screen.
    let scale: CGFloat
    /// With Pro, once the Shortcuts action ran: today's and tomorrow's wallpaper pair are rendered ahead too (WP-11).
    var wallpaper: WallpaperSource? = nil
}

/// A widget on the home screen with its settings, for the menu's widget list and for rendering fixed favourites.
struct PlacedWidget: Identifiable, Sendable {
    let id: Int
    let shape: WidgetShape
    /// Its fixed favourite; nil for the daily turn.
    let fixed: String?
    let look: WidgetLook
}

@MainActor
final class WidgetSync {
    /// The background app refresh that renders ahead (approval A10); in `BGTaskSchedulerPermittedIdentifiers`.
    nonisolated static let refreshID = (Bundle.main.bundleIdentifier ?? "one.tearoom.mandala") + ".widgets"

    /// Days rendered ahead (F8 a); after them a widget stays on the last picture.
    nonisolated static let daysAhead = 7

    private let painter = WidgetPainter.shared
    private var waiting: Task<Void, Never>?
    private var running: Task<Void, Never>?
    private var ticket: RenderTicket?

    /// Asks for the widgets to be brought up to date after `delay`; a newer request replaces a waiting one and stops a
    /// running one (pictures already written are kept and found again by their names).
    func request(_ inputs: WidgetInputs, delay: Duration = .milliseconds(800)) {
        waiting?.cancel()
        waiting = Task {
            try? await Task.sleep(for: delay)
            guard !Task.isCancelled else { return }
            await run(inputs)
        }
    }

    /// Brings the widgets up to date and returns when done or cancelled (the background refresh awaits it).
    func run(_ inputs: WidgetInputs) async {
        ticket?.cancel()
        await running?.value
        let ticket = RenderTicket()
        self.ticket = ticket
        let painter = painter
        let task = Task.detached(priority: .utility) {
            await WidgetSync.sync(inputs, painter: painter, ticket: ticket)
        }
        running = task
        await withTaskCancellationHandler { await task.value } onCancel: { ticket.cancel() }
    }

    /// The placed widgets of this app with their settings, in WidgetKit's order; empty when WidgetKit cannot tell.
    nonisolated static func placed() async -> [PlacedWidget] {
        guard let infos = try? await WidgetCenter.shared.currentConfigurations() else { return [] }
        return infos.filter { $0.kind == WidgetKind.mandala }.enumerated().map { i, info in
            let intent = info.widgetConfigurationIntent(of: MandalaWidgetIntent.self)
            return PlacedWidget(id: i, shape: shape(info.family), fixed: intent?.mandala?.fixedID, look: intent?.appearance ?? .app)
        }
    }

    nonisolated static func shape(_ family: WidgetFamily) -> WidgetShape {
        switch family {
        case .systemMedium: .wide
        case .systemLarge, .systemExtraLarge: .large
        default: .small
        }
    }

    // ── The daily turn ──

    /// The favourite of day `number`: with Pro the ticked favourites in turn, without Pro always the first (F7 a).
    nonisolated static func turn(_ inputs: WidgetInputs, day number: Int) -> Favorite? {
        DailyWallpaper.favourite(inputs.favourites, chosen: inputs.chosen, pro: inputs.pro, day: number)
    }

    /// The days from today on: their number, start (4:00) and favourite.
    nonisolated static func days(_ inputs: WidgetInputs, from now: Date) -> [(number: Int, start: Date, favourite: Favorite?)] {
        let today = WidgetDay.number(of: now)
        return (0..<daysAhead).map { i in (today + i, WidgetDay.start(of: today + i), turn(inputs, day: today + i)) }
    }

    // ── The work, off the main thread ──

    private enum Variant: String { case square, plain, large }

    private struct Job {
        let favourite: String
        let variant: Variant
        let params: MandalaParameters
        let width: Int
        let height: Int
        let file: String
    }

    nonisolated private static func sync(_ inputs: WidgetInputs, painter: WidgetPainter, ticket: RenderTicket) async {
        guard let folder = WidgetFiles.folder else { return } // no App Group in this build (device builds for now, IOS-9)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let started = Date()
        let placed = await placed()
        let days = days(inputs, from: Date())
        let quotes = IosEditingParts.shared.quoteCollectionsFor(languages: inputs.quoteLanguages).flatMap(\.quotes)

        // Which pictures each favourite needs: the daily turn always square, so a widget placed later is sharp at once;
        // the placed widgets in their shapes, for their fixed favourite or the days they follow.
        let byID = Dictionary(inputs.favourites.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        let dailyIDs = days.compactMap { $0.favourite?.id }
        var needs: [String: Set<Variant>] = [:]
        for id in dailyIDs { needs[id, default: []].insert(.square) }
        for widget in placed {
            let targets = widget.fixed.flatMap { byID[$0] != nil ? [$0] : nil } ?? dailyIDs
            for id in targets {
                guard let favourite = byID[id] else { continue }
                switch widget.shape {
                case .small: needs[id, default: []].insert(.square)
                case .wide: needs[id, default: []].insert(favourite.parameters.textLayer.isEnabled ? .plain : .square)
                case .large: needs[id, default: []].insert(.large)
                }
            }
        }

        // Sizes in pixels from the widget sizes the extension saw on this phone.
        let square = pixels(WidgetSizes.load(.small), inputs.scale, square: true)
        let plain = pixels(WidgetSizes.load(.wide), inputs.scale, square: true)
        let large = pixels(WidgetSizes.load(.large), inputs.scale, square: false)

        // The favourites with what exists already; the missing pictures become jobs, today's first, then the fixed ones.
        var jobs: [Job] = []
        var favourites: [WidgetIndex.Favourite] = []
        for favourite in inputs.favourites {
            let p = favourite.parameters
            var entry = WidgetIndex.Favourite(id: favourite.id, name: name(favourite), words: words(p), thumb: thumbnail(favourite, in: folder))
            let wanted = needs[favourite.id] ?? []
            for variant in [Variant.square, .plain, .large] where wanted.contains(variant) {
                let size = variant == .square ? square : variant == .plain ? plain : large
                let drawn = variant == .plain ? IosEditingParts.shared.textSwitched(params: p, on: false) : p
                let file = fileName(variant, drawn, size)
                if !FileManager.default.fileExists(atPath: folder.appendingPathComponent(file).path) {
                    jobs.append(Job(favourite: favourite.id, variant: variant, params: drawn, width: size.0, height: size.1, file: file))
                } else {
                    set(&entry, variant, file)
                }
            }
            // A favourite without words shows the square picture in the wide widget.
            if entry.plain == nil, !p.textLayer.isEnabled { entry.plain = entry.square }
            favourites.append(entry)
        }
        let order = [dailyIDs.first].compactMap { $0 } + placed.compactMap(\.fixed) + dailyIDs
        jobs.sort { (order.firstIndex(of: $0.favourite) ?? .max) < (order.firstIndex(of: $1.favourite) ?? .max) }

        let indexDays = days.map { day in
            WidgetIndex.Day(number: day.number, start: day.start, favourite: day.favourite?.id, quote: quoteOfTheDay(quotes, day.number))
        }
        let old = WidgetFiles.readIndex()
        var index = WidgetIndex(favourites: favourites, days: indexDays, appLight: inputs.appLight, mark: inputs.mark)

        // What exists now goes out at once (thumbnails stand in for what is still to be drawn), then each picture.
        write(index, old: old)
        var drawn = 0
        for job in jobs {
            if ticket.isCancelled() { break }
            guard let image = await painter.render(job.params, width: job.width, height: job.height, ticket: ticket),
                  let png = Pictures.png(image) else { continue }
            do {
                try png.write(to: folder.appendingPathComponent(job.file), options: .atomic)
            } catch {
                continue
            }
            if let i = index.favourites.firstIndex(where: { $0.id == job.favourite }) {
                set(&index.favourites[i], job.variant, job.file)
                if job.variant == .square, !job.params.textLayer.isEnabled, index.favourites[i].plain == nil { index.favourites[i].plain = job.file }
            }
            drawn += 1
        }
        // The wallpaper pairs of today and tomorrow for the Shortcuts action, after the widgets (WP-11).
        var wallpapersDrawn = 0
        if let source = inputs.wallpaper, !ticket.isCancelled() {
            let today = WidgetDay.number(of: Date())
            let wallpapers = [today, today + 1].compactMap { source.job(day: $0) }
            for job in wallpapers where !WallpaperFiles.exists(job) {
                if ticket.isCancelled() { break }
                if await WallpaperFiles.ensure(job, painter: painter, ticket: ticket).drawn { wallpapersDrawn += 1 }
            }
            if !ticket.isCancelled() { WallpaperFiles.keepOnly(wallpapers) }
        }
        await painter.collect()
        let current = WidgetFiles.readIndex()
        write(index, old: current)
        removeUnused(in: folder, keep: index.files.union(old?.files ?? []).union(["index.json"]))
        if !placed.isEmpty || inputs.wallpaper != nil { scheduleRefresh(after: WidgetDay.start(of: (days.first?.number ?? WidgetDay.number(of: Date())) + 1)) }
        Engine.trace("widgets placed=\(placed.count) favourites=\(favourites.count) drawn=\(drawn)/\(jobs.count) wallpapers=\(wallpapersDrawn) " +
                     "cancelled=\(ticket.isCancelled()) in \(Int(Date().timeIntervalSince(started) * 1000)) ms")
    }

    nonisolated private static func set(_ entry: inout WidgetIndex.Favourite, _ variant: Variant, _ file: String) {
        switch variant {
        case .square: entry.square = file
        case .plain: entry.plain = file
        case .large: entry.large = file
        }
    }

    /// Pixels for a widget size: square on the shorter side, or in the widget's own shape, at most 1200 on the longer side.
    nonisolated private static func pixels(_ points: CGSize, _ scale: CGFloat, square: Bool) -> (Int, Int) {
        let w = points.width * scale, h = points.height * scale
        let limit: CGFloat = 1200
        if square {
            let side = min(limit, min(w, h)).rounded()
            return (Int(side), Int(side))
        }
        let shrink = min(1, limit / max(w, h))
        return (Int((w * shrink).rounded()), Int((h * shrink).rounded()))
    }

    /// Named after what it shows, so an unchanged favourite is never drawn twice.
    nonisolated private static func fileName(_ variant: Variant, _ params: MandalaParameters, _ size: (Int, Int)) -> String {
        let key = IosBridge.shared.encode(params: params) + "|\(size.0)x\(size.1)"
        let hash = SHA256.hash(data: Data(key.utf8)).prefix(12).map { String(format: "%02x", $0) }.joined()
        return "\(variant.rawValue)-\(hash).png"
    }

    /// The stored thumbnail as a file, for a favourite whose picture is not drawn yet.
    nonisolated private static func thumbnail(_ favourite: Favorite, in folder: URL) -> String? {
        guard let data = Data(base64Encoded: favourite.thumbnailData, options: .ignoreUnknownCharacters), !data.isEmpty else { return nil }
        let hash = SHA256.hash(data: data).prefix(12).map { String(format: "%02x", $0) }.joined()
        let file = "thumb-\(hash).jpg"
        let url = folder.appendingPathComponent(file)
        if !FileManager.default.fileExists(atPath: url.path) { try? data.write(to: url, options: .atomic) }
        return file
    }

    /// Unnamed favourites are called by their styles, as the menu calls the mandala.
    nonisolated static func name(_ favourite: Favorite) -> String {
        favourite.name.isEmpty ? favourite.parameters.layers.filter(\.isEnabled).map { Texts.style($0.style) }.joined(separator: ", ") : favourite.name
    }

    /// The favourite's own words (Android's `MandalaWidget.words`, before the quote of the day).
    nonisolated private static func words(_ params: MandalaParameters) -> WidgetIndex.Words? {
        let text = params.textLayer
        guard !text.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        return WidgetIndex.Words(text: text.text, author: text.showAuthor ? text.customAuthor : "")
    }

    /// The same quote all day, a different one the next day (Android's `quoteOfTheDay`).
    nonisolated private static func quoteOfTheDay(_ quotes: [Quote], _ day: Int) -> WidgetIndex.Words? {
        guard !quotes.isEmpty else { return nil }
        let q = quotes[((day % quotes.count) + quotes.count) % quotes.count]
        return WidgetIndex.Words(text: q.text, author: q.author)
    }

    /// Writes the index atomically and asks WidgetKit for new timelines, only when something changed.
    nonisolated private static func write(_ index: WidgetIndex, old: WidgetIndex?) {
        guard index != old, let url = WidgetFiles.index, let data = try? JSONEncoder().encode(index) else { return }
        do {
            try data.write(to: url, options: .atomic)
            WidgetCenter.shared.reloadTimelines(ofKind: WidgetKind.mandala)
        } catch {
            Engine.trace("widgets index not written: \(error.localizedDescription)")
        }
    }

    /// Pictures neither index points to; the previous index's stay one round, so a widget reading it finds them.
    nonisolated private static func removeUnused(in folder: URL, keep: Set<String>) {
        let names = (try? FileManager.default.contentsOfDirectory(atPath: folder.path)) ?? []
        for name in names where !keep.contains(name) {
            try? FileManager.default.removeItem(at: folder.appendingPathComponent(name))
        }
    }

    /// The next background refresh, from the next day's start on; iOS decides when it runs.
    nonisolated private static func scheduleRefresh(after date: Date) {
        let request = BGAppRefreshTaskRequest(identifier: refreshID)
        request.earliestBeginDate = date
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            Engine.trace("widgets refresh not scheduled: \(error.localizedDescription)")
        }
    }
}

/// Renders the widget pictures on its own utility queue, so the editor's previews on the engine's queue go first.
actor WidgetPainter {
    /// One painter for the widgets, the wallpapers rendered ahead and the Shortcuts action, so two of these never
    /// render at the same time and need the memory twice.
    static let shared = WidgetPainter()

    private let queue = DispatchSerialQueue(label: "one.tearoom.mandala.widgets", qos: .utility)
    nonisolated var unownedExecutor: UnownedSerialExecutor { queue.asUnownedSerialExecutor() }

    private let loader = ImageIOLoader()
    private let text = CoreTextRenderer()

    /// The whole mandala in the given shape, its background going on (Android's widget render); nil when cancelled.
    func render(_ params: MandalaParameters, width: Int, height: Int, ticket: RenderTicket) -> CGImage? {
        if ticket.isCancelled() { return nil }
        return IosSave.shared.renderFramed(
            params: params, width: Int32(width), height: Int32(height), framing: IosSave.shared.centred(), textFollows: false,
            supersample: false, finish: IosSave.shared.identityFinish(), threads: IosBridge.shared.processors(), ticket: ticket,
            imageLoader: loader, textRenderer: text
        ) { _ in }?.cgImage
    }

    /// The wallpaper pair filling the screen (Android's `DailyWallpaper.setNext`), drawn and written unless its files
    /// exist; the Kotlin buffers go right after. Whether the files exist now, and whether they were drawn here.
    func ensureWallpaper(_ job: WallpaperJob, ticket: RenderTicket) -> (ready: Bool, drawn: Bool) {
        if WallpaperFiles.exists(job) { return (true, false) }
        if ticket.isCancelled() { return (false, false) }
        defer { IosBridge.shared.collect() }
        let pngs: (lock: Data, home: Data)? = {
            guard let drawn = IosSave.shared.drawWallpaper(params: job.params, width: Int32(job.width), height: Int32(job.height),
                                                           threads: IosBridge.shared.processors(), ticket: ticket, imageLoader: loader,
                                                           textRenderer: text, progress: { _ in }),
                  !ticket.isCancelled() else { return nil }
            let pair = drawn.pair(finish: job.finish, homeBrightness: job.homeBrightness)
            guard let lock = pair.lock.cgImage.flatMap({ Pictures.png($0) }), let home = pair.home.cgImage.flatMap({ Pictures.png($0) }) else { return nil }
            return (lock, home)
        }()
        guard let pngs, !ticket.isCancelled() else { return (false, false) }
        return (WallpaperFiles.write(lock: pngs.lock, home: pngs.home, for: job), true)
    }

    /// The Kotlin buffers of the drawn pictures go at once.
    func collect() { IosBridge.shared.collect() }
}
