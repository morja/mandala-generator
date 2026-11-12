import CoreGraphics
import Foundation
@preconcurrency import MandalaCore
import Observation

// WP-11: the wallpaper page and the daily page of the menu. The page shows the pair as it will be saved (Android's
// `PairSheet`): lock and home screen side by side, drawn small once and only re-finished while the slider moves. The
// daily page ticks the favourites that take turns, the choice the widgets and the Shortcuts action share.

/// The wallpaper page's preview of the pair.
@MainActor
@Observable
final class WallpaperState {
    var lock: CGImage?
    var home: CGImage?
    var rendering = false
    /// The page is open; the preview is drawn only then.
    var open = false
    @ObservationIgnored var drawn: (params: MandalaParameters, aspect: Double, frame: DrawnFrame)?
    @ObservationIgnored var ticket: RenderTicket?
    @ObservationIgnored var task: Task<Void, Never>?

    /// Width of the preview in pixels; the height follows the screen.
    static let previewWidth = 360
}

extension EditorModel {
    func setWallpaperOpen(_ open: Bool) {
        wallpaper.open = open
        if open {
            WallpaperFiles.rememberScreen()
            refreshWallpaperPreview()
        } else {
            wallpaper.ticket?.cancel()
            wallpaper.task?.cancel()
            wallpaper.drawn = nil
            wallpaper.lock = nil
            wallpaper.home = nil
        }
    }

    /// Draws the small pair for the current mandala, or re-finishes the last drawing when only the finish changed.
    func refreshWallpaperPreview() {
        guard wallpaper.open else { return }
        wallpaper.ticket?.cancel()
        wallpaper.task?.cancel()
        let p = params, aspect = SaveState.currentScreenAspect()
        let finish = proOwned ? save.choice.finish : IosSave.shared.identityFinish()
        let home = save.choice.homeBrightness
        let ticket = RenderTicket()
        wallpaper.ticket = ticket
        wallpaper.rendering = true
        wallpaper.task = Task {
            let frame: DrawnFrame
            if let last = wallpaper.drawn, last.params == p, last.aspect == aspect {
                frame = last.frame
            } else {
                let width = WallpaperState.previewWidth
                let size = PixelSize(width: Int32(width), height: Int32((Double(width) * aspect).rounded()))
                guard let fresh = await engine.wallpaper(p, size: size, ticket: ticket), !ticket.isCancelled() else { return }
                frame = fresh
                wallpaper.drawn = (p, aspect, fresh)
            }
            let pair = await engine.pair(frame, finish: finish, homeBrightness: home)
            guard !ticket.isCancelled() else { return }
            wallpaper.lock = pair.lock.cgImage
            wallpaper.home = pair.home.cgImage
            wallpaper.rendering = false
        }
    }

    // ── The favourites that take turns (ADR-0014 since UX-1 13, the widgets' daily turn, the Shortcuts action) ──

    /// One tap in the picker, by the shared rule (`DailyTurn.toggled`): from "all" the first tap leaves the tapped one
    /// out; leaving out the last one, or ticking every one, means all again.
    func toggleDaily(_ id: String) {
        let next = DailyTurn.shared.toggled(chosen: dailyChosen, all: favorites.map(\.id), id: id)
        setDailyChosen(next)
    }

    func chooseAllDaily() { setDailyChosen(nil) }

    private func setDailyChosen(_ chosen: Set<String>?) {
        DailyChoiceStore.save(chosen)
        dailyChosen = chosen
        widgetsChanged()
    }

    /// Today's favourite of the daily turn (with Pro; the first one without), for the daily page.
    var dailyToday: Favorite? {
        DailyWallpaper.favourite(favorites, chosen: dailyChosen, pro: proOwned, day: WidgetDay.number(of: Date()))
    }
}
