import CoreGraphics
import Foundation
@preconcurrency import MandalaCore

/// The save sheet (ADR-0010), sharing, the moving picture and the line drawing, as Android's `EditorViewModel`
/// does for these parts. Every rule (formats, sizes, framing, the choice's placements) is the shared module's,
/// through `IosSave`; the files go to Photos (add-only, approval A10), the share sheet or Files.
extension EditorModel {
    private var pro: Bool { proOwned }

    // ── Pro (ADR-0016); the purchase and its sheet come with WP-9 ──

    /// Opens the Pro sheet for `request`. Until WP-9 builds the sheet, the request is kept in `save.proRequest` and
    /// its note shows as a message; a mandala with Pro features is offered "… without them", as the sheet will.
    func requestPro(_ request: ProRequest) {
        save.proRequest = request
        if request.features.isEmpty {
            message = Message(text: request.note ?? Texts.proTitle)
        } else {
            let names = request.features.map { Texts.proFeature($0) }.joined(separator: ", ")
            message = Message(text: "\(Texts.proUsesKeep) \(names)", actionLabel: Texts.proWithout(request.action ?? Texts.actionSave)) { [weak self] in
                self?.continueWithoutPro()
            }
        }
    }

    /// Hides the Pro features (Back brings them back) and takes the step the request was for (Android's `continueWithoutPro`).
    func continueWithoutPro() {
        guard let request = save.proRequest else { return }
        save.proRequest = nil
        change(IosSave.shared.withoutPro(params: params))
        request.then?()
    }

    /// Saving, sharing or a wallpaper of a mandala that uses Pro features needs Pro; true when `step` may go
    /// ahead, otherwise the Pro sheet opens and can take it after removing them (Android's `mayKeep`).
    func mayKeep(_ action: String, then step: @escaping @MainActor () -> Void) -> Bool {
        let missing = pro ? [] : IosEditingParts.shared.proFeatures(params: params)
        if missing.isEmpty { return true }
        requestPro(ProRequest(features: missing, action: action, then: step))
        return false
    }

    // ── The save choice ──

    /// The quote moving with the mandala in exports: as chosen with Pro, never without.
    private var effectiveTextFollows: Bool { pro && save.choice.textFollowsMandala }

    /// The finish exports get: the chosen one with Pro, none without.
    private var effectiveFinish: Finish { pro ? save.choice.finish : IosSave.shared.identityFinish() }

    func aspect(of format: Editing.SaveFormat) -> Double { IosSave.shared.aspect(format: format, screenAspect: save.screenAspect) }

    func setSaveOpen(_ open: Bool) {
        save.open = open
        if open {
            save.screenAspect = SaveState.currentScreenAspect()
            refreshFrame()
        } else {
            save.frameTicket?.cancel()
            save.frameTask?.cancel()
            save.lastDrawn = nil
            save.recentFrames.removeAll()
            save.recentOrder.removeAll()
        }
    }

    /// The screen is known only once the window is up; the size of "This screen" is read again before each export.
    private func refreshScreenAspect() { save.screenAspect = SaveState.currentScreenAspect() }

    func setSaveFormat(_ format: Editing.SaveFormat) { changeChoice(IosSave.shared.withFormat(choice: save.choice, format: format)) }

    /// "Fill" or "Whole", free in every format; with Pro it ends placing by hand.
    func setFill(_ fill: Bool) { changeChoice(IosSave.shared.withFill(choice: save.choice, fill: fill)) }

    /// Moving and zooming the mandala is part of Pro; without it the mandala stays on its preset.
    func setFraming(_ framing: Framing) {
        guard pro else { return }
        let next = IosSave.shared.withHandFraming(choice: save.choice, framing: framing)
        save.shownFraming = next.framing
        changeChoice(next)
    }

    func setTextFollows(_ follows: Bool) {
        if pro { changeChoice(IosSave.shared.withTextFollows(choice: save.choice, follows: follows)) }
    }

    /// A picture or a video of the layers turning (UX-1 12); anyone may watch, keeping the video is Pro.
    func setMoving(_ moving: Bool) { changeChoice(IosSave.shared.withMoving(choice: save.choice, moving: moving)) }

    func setVideoSeconds(_ seconds: Int) { changeChoice(IosSave.shared.withVideoSeconds(choice: save.choice, seconds: Int32(seconds))) }

    /// The last adjustment of saved pictures (ADR-0014), part of Pro.
    func setFinish(_ finish: Finish) {
        if pro { changeChoice(IosSave.shared.withFinish(choice: save.choice, finish: finish)) }
    }

    private func changeChoice(_ next: SaveChoice) {
        guard next != save.choice else { return }
        save.choice = next
        refreshFrame()
    }

    /// The framing for `choice`; "Fill" measures the mandala once (about a tenth of a second) and keeps the zoom.
    func framing(for choice: SaveChoice, params p: MandalaParameters, aspect: Double, pro: Bool) async -> Framing {
        var zoom = 1.0
        if choice.fit == .fill {
            let key = SaveState.FillKey(params: p, aspect: aspect)
            if let known = save.fillZooms[key] {
                zoom = known
            } else {
                zoom = await engine.fillZoom(p, aspect: aspect)
                save.fillZooms[key] = zoom
                save.fillOrder.append(key)
                if save.fillOrder.count > Self.fillZoomsKept { save.fillZooms[save.fillOrder.removeFirst()] = nil }
            }
        }
        return IosSave.shared.framingFor(choice: choice, fillZoom: zoom, pro: pro)
    }

    // ── The frame preview (Android's `renderFramePreview` and `animateFramePreview`) ──

    /// Draws the frame for the current choice once it rests for 60 ms; a video's frame keeps turning.
    func refreshFrame() {
        save.frameTicket?.cancel()
        save.frameTask?.cancel()
        guard save.open else { return }
        let choice = save.choice, p = params, screen = save.screenAspect, pro = pro
        let key = FrameKey(params: p, choice: choice, aspect: screen, pro: pro)
        // Placed by hand, the framing is known at once, so a finger never waits for the next picture.
        if choice.fit == .hand && pro { save.shownFraming = choice.framing }
        if !choice.moving, let known = save.recentFrames[key] {
            save.frame = known
            save.shownFraming = known.framing
            return
        }
        save.frame.rendering = true
        let ticket = RenderTicket()
        save.frameTicket = ticket
        save.frameTask = Task {
            try? await Task.sleep(for: Self.frameDebounce)
            guard !Task.isCancelled else { return }
            let size = IosSave.shared.frameSize(format: choice.format, screenAspect: screen)
            let framing = await framing(for: choice, params: p, aspect: aspect(of: choice.format), pro: pro)
            guard !ticket.isCancelled() else { return }
            save.shownFraming = framing
            if choice.moving {
                await animateFrame(p, choice: choice, size: size, framing: framing, ticket: ticket)
            } else {
                await drawFrame(key, size: size, framing: framing, ticket: ticket)
            }
        }
    }

    private func drawFrame(_ key: FrameKey, size: PixelSize, framing: Framing, ticket: RenderTicket) async {
        let choice = key.choice
        // The finish is applied to the drawn frame, so its sliders follow the finger without a new render.
        let unfinished = FrameKey(params: key.params, choice: IosSave.shared.withFinish(choice: choice, finish: IosSave.shared.identityFinish()),
                                  aspect: key.aspect, pro: key.pro)
        let drawn: DrawnFrame
        if let last = save.lastDrawn, last.key == unfinished {
            drawn = last.drawn
        } else {
            guard let fresh = await engine.frame(key.params, size: size, framing: framing, textFollows: key.pro && choice.textFollowsMandala, ticket: ticket),
                  !ticket.isCancelled() else { return }
            drawn = fresh
            save.lastDrawn = (unfinished, fresh)
        }
        let finish = key.pro ? choice.finish : IosSave.shared.identityFinish()
        let rendered = await engine.finished(drawn, finish: finish)
        guard !ticket.isCancelled(), let image = rendered.cgImage else { return }
        let shown = FramePreview(image: image, framing: framing, width: Int(size.width), height: Int(size.height), rendering: false)
        save.frame = shown
        save.recentFrames[key] = shown
        save.recentOrder.removeAll { $0 == key }
        save.recentOrder.append(key)
        if save.recentOrder.count > Self.recentFramesKept { save.recentFrames[save.recentOrder.removeFirst()] = nil }
    }

    /// The layers turning as in the video, small and with the fast glow, until something changes or the sheet
    /// closes; it pauses while a video is made, which needs the processors more.
    private func animateFrame(_ p: MandalaParameters, choice: SaveChoice, size: PixelSize, framing: Framing, ticket: RenderTicket) async {
        let finish = pro ? choice.finish : IosSave.shared.identityFinish()
        let frames = await engine.movingFrames(p, size: size, framing: framing, textFollows: pro && choice.textFollowsMandala, finish: finish, ticket: ticket)
        let start = Date()
        let seconds = Double(choice.videoSeconds)
        while !Task.isCancelled && !ticket.isCancelled() {
            if save.makingVideo && save.frame.image != nil {
                try? await Task.sleep(for: Self.movingPause)
                continue
            }
            let t = (Date().timeIntervalSince(start) / seconds).truncatingRemainder(dividingBy: 1)
            guard let rendered = await engine.movingFrame(frames, at: t), !ticket.isCancelled(), let image = rendered.cgImage else { break }
            save.frame = FramePreview(image: image, framing: framing, width: Int(size.width), height: Int(size.height), rendering: false)
            await Task.yield()
        }
        await engine.close(frames)
    }

    // ── Pictures ──

    /// Saves in the chosen format and framing to Photos: 1024 px on the shorter side, or with Pro (`large`)
    /// 2048 px, drawn without supersampling so it needs no more memory than the normal export (Android's `saveImage`).
    func saveImage(large: Bool) {
        if large && !pro { return }
        refreshScreenAspect()
        guard !save.busy, mayKeep(Texts.actionSave, then: { [weak self] in self?.saveImage(large: large) }) else { return }
        let choice = save.choice, p = params, follows = effectiveTextFollows, finish = effectiveFinish
        let size = IosSave.shared.exportSize(format: choice.format, screenAspect: save.screenAspect, large: large)
        runPictureExport(Texts.creatingImage) { [self] ticket in
            guard await PhotoLibrary.mayAdd() else {
                photosDenied()
                return
            }
            let framing = await framing(for: choice, params: p, aspect: aspect(of: choice.format), pro: pro)
            guard let png = await renderPNG(p, size: size, framing: framing, textFollows: follows, supersample: !large, finish: finish, ticket: ticket) else { return }
            save.export = ExportProgress(label: Texts.addingToPhotos)
            do {
                try await PhotoLibrary.addPNG(png, fileName: IosSave.shared.fileName(params: p, width: size.width, height: size.height))
                message = Message(text: Texts.savedToPhotos(Int(size.width), Int(size.height)))
            } catch PhotoLibrary.Failure.denied {
                photosDenied()
            } catch {
                message = Message(text: Texts.saveFailed)
            }
        }
    }

    /// The square picture at 1024 px through the share sheet, from the menu (Android's `shareImage`).
    func shareImage() {
        guard !save.busy, mayKeep(Texts.share, then: { [weak self] in self?.shareImage() }) else { return }
        let p = params
        let side = Int32(Editing.shared.EXPORT_SIZE)
        let size = PixelSize(width: side, height: side)
        runPictureExport(Texts.creatingImage) { [self] ticket in
            guard let png = await renderPNG(p, size: size, framing: IosSave.shared.centred(), textFollows: false, supersample: true,
                                            finish: IosSave.shared.identityFinish(), ticket: ticket) else { return }
            save.export = ExportProgress(label: Texts.preparingShare)
            let url = ExportFiles.fresh(IosSave.shared.fileName(params: p, width: side, height: side))
            do {
                try png.write(to: url)
                ShareSheet.present(url)
            } catch {
                message = Message(text: Texts.saveFailed)
            }
        }
    }

    /// Runs a picture export with its progress, a ticket and the background assertion (about half a minute).
    private func runPictureExport(_ label: String, _ work: @escaping @MainActor (RenderTicket) async -> Void) {
        save.busy = true
        save.export = ExportProgress(label: label, share: 0)
        let ticket = RenderTicket()
        save.exportTicket = ticket
        var expired = false
        let background = BackgroundWork(name: "picture") {
            expired = true
            ticket.cancel()
        }
        Task {
            await work(ticket)
            // Only when the picture was not kept; a late expiry while Photos was at work changes nothing.
            if expired && ticket.isCancelled() && save.export?.share != nil { message = Message(text: Texts.stoppedInBackground) }
            background.end(success: !ticket.isCancelled())
            // The drawn picture's Kotlin buffers go at once, not at the collector's next turn.
            await engine.collect()
            save.export = nil
            save.busy = false
            save.exportTicket = nil
        }
    }

    /// Draws the picture with progress and encodes it as PNG off the main thread; nil (with a message) when it failed.
    private func renderPNG(_ p: MandalaParameters, size: PixelSize, framing: Framing, textFollows: Bool, supersample: Bool, finish: Finish,
                           ticket: RenderTicket) async -> Data? {
        #if DEBUG
        let peak = Footprint.watch()
        #endif
        let rendered = await engine.framed(p, size: size, framing: framing, textFollows: textFollows, supersample: supersample, finish: finish,
                                           ticket: ticket) { percent in
            Task { @MainActor [weak self] in
                guard let self, self.save.export?.share != nil else { return }
                self.save.export?.share = Double(percent) / 100
            }
        }
        guard let rendered else {
            #if DEBUG
            peak.cancel()
            #endif
            // Cancelled says nothing; a failed render says so (Android's message_image_failed).
            if !ticket.isCancelled() { message = Message(text: Texts.saveFailed) }
            return nil
        }
        save.export = ExportProgress(label: save.export?.label ?? Texts.creatingImage)
        let png = await Task.detached(priority: .userInitiated) { rendered.cgImage.flatMap { Pictures.png($0) } }.value
        #if DEBUG
        peak.cancel()
        Engine.trace("export \(size.width)x\(size.height) render=\(rendered.millis)ms peakFootprint=\(await peak.value)MB")
        #endif
        if png == nil { message = Message(text: Texts.saveFailed) }
        return png
    }

    private func photosDenied() {
        message = Message(text: Texts.photosDenied, actionLabel: Texts.openSettings) { Links.openSettings() }
    }

    // ── The moving picture (UX-1 12, ADR-0010 point 10) ──

    /// Makes a video of the layers turning (Pro) in the chosen format and framing, 720 or (`large`) 1080 on the shorter
    /// side, and adds it to Photos or (`share`) shares it (Android's `saveVideo`).
    func saveVideo(large: Bool, share: Bool) {
        guard pro else {
            requestPro(ProRequest(note: Texts.saveVideoPro))
            return
        }
        guard !save.busy else { return }
        refreshScreenAspect()
        let choice = save.choice, p = params, follows = effectiveTextFollows, finish = choice.finish
        let size = IosSave.shared.videoSize(format: choice.format, screenAspect: save.screenAspect, large: large)
        let seconds = Int(choice.videoSeconds)
        let frames = Int(IosSave.shared.frameCount(seconds: Int32(seconds)))
        let label = Texts.creatingVideo
        save.busy = true
        save.makingVideo = true
        save.export = ExportProgress(label: label, share: 0)
        let ticket = RenderTicket()
        save.exportTicket = ticket
        var expired = false
        let background = BackgroundWork(name: "video") {
            expired = true
            ticket.cancel()
        }
        let subtitle = "\(Texts.saveSize(Int(size.width), Int(size.height))) · \(Texts.videoSeconds(seconds))"
        Task {
            var success = false
            defer {
                if expired && !success { message = Message(text: Texts.stoppedInBackground) }
                background.end(success: success)
                Task { await engine.collect() }
                save.export = nil
                save.busy = false
                save.makingVideo = false
                save.exportTicket = nil
            }
            if !share {
                guard await PhotoLibrary.mayAdd() else {
                    photosDenied()
                    return
                }
            }
            // After the Photos question, so iOS's progress does not show for a refused video.
            background.continueInBackground(title: label, subtitle: subtitle, units: frames)
            #if DEBUG
            let peak = Footprint.watch()
            defer { peak.cancel() }
            #endif
            let framing = await framing(for: choice, params: p, aspect: aspect(of: choice.format), pro: true)
            let fileName = IosSave.shared.videoFileName(params: p, width: size.width, height: size.height, seconds: Int32(seconds))
            let url = ExportFiles.fresh(fileName)
            let writer: VideoWriter
            do {
                writer = try VideoWriter(url: url, width: Int(size.width), height: Int(size.height), fps: Int(IosSave.shared.fps()))
            } catch {
                message = Message(text: Texts.videoFailed(error.localizedDescription))
                return
            }
            let source = await engine.videoFrames(p, size: size, framing: framing, textFollows: follows, finish: finish, seconds: seconds, ticket: ticket)
            let start = Date()
            do {
                for f in 0..<frames {
                    let buffer = try await writer.nextBuffer()
                    guard await engine.writeFrame(source, index: f, into: FrameBuffer(buffer: buffer)), !ticket.isCancelled() else {
                        writer.cancel()
                        await engine.close(source)
                        // Not cancelled: core could not draw the frame.
                        if !ticket.isCancelled() { message = Message(text: Texts.videoFailed(Texts.saveFailed)) }
                        return
                    }
                    try writer.append(buffer, index: f)
                    let shown = timeLeft(frame: f, of: frames, since: start) ?? save.export?.label ?? label
                    save.export = ExportProgress(label: shown, share: Double(f + 1) / Double(frames))
                    background.progress(f + 1)
                }
                await engine.close(source)
                try await writer.finish(frames: frames)
            } catch {
                writer.cancel()
                await engine.close(source)
                message = Message(text: Texts.videoFailed(error.localizedDescription))
                return
            }
            // The file is complete; from here Cancel and the background's end no longer stop it.
            success = true
            save.export = ExportProgress(label: share ? Texts.preparingShare : Texts.addingToPhotos)
            #if DEBUG
            peak.cancel()
            Engine.trace("video \(size.width)x\(size.height) \(seconds)s frames=\(frames) took=\(Int(Date().timeIntervalSince(start)))s peakFootprint=\(await peak.value)MB")
            #endif
            if share {
                ShareSheet.present(url)
            } else {
                do {
                    try await PhotoLibrary.addVideo(url, fileName: fileName)
                    message = Message(text: Texts.videoSaved(Int(size.width), Int(size.height)))
                } catch PhotoLibrary.Failure.denied {
                    photosDenied()
                } catch {
                    message = Message(text: Texts.videoFailed(error.localizedDescription))
                }
            }
        }
    }

    /// Stops the video (or picture) being made.
    func cancelExport() { save.exportTicket?.cancel() }

    /// From a few frames on, the pace says how long is left (Android's `export_video_minutes`).
    private func timeLeft(frame f: Int, of frames: Int, since start: Date) -> String? {
        guard f >= Self.timeLeftAfterFrames else { return nil }
        let left = Date().timeIntervalSince(start) * Double(frames - f - 1) / Double(f + 1)
        if left < Self.underAMinute { return Texts.videoUnderMinute }
        return Texts.videoMinutes(max(1, Int((left + 30) / 60)))
    }

    // ── The line drawing (ADR-0013, Pro) ──

    /// The SVG text for "Save to Files", or nil (with the Pro sheet) without Pro.
    func lineDrawing(_ look: VectorExport.Look) async -> (text: String, name: String)? {
        guard pro else {
            requestPro(ProRequest(note: Texts.lineDrawingPro))
            return nil
        }
        guard !save.busy else { return nil }
        let p = params
        save.busy = true
        save.export = ExportProgress(label: Texts.creatingLineDrawing)
        defer {
            save.busy = false
            save.export = nil
        }
        let text = await Task.detached(priority: .userInitiated) { IosSave.shared.lineDrawing(params: p, look: look) }.value
        return (text, IosSave.shared.lineDrawingName(params: p, look: look))
    }

    func lineDrawingSaved(_ ok: Bool, reason: String? = nil) {
        message = Message(text: ok ? Texts.lineDrawingSaved : Texts.lineDrawingFailed(reason ?? ""))
    }

    private static let frameDebounce = Duration.milliseconds(60)
    private static let movingPause = Duration.milliseconds(300)
    private static let recentFramesKept = 6
    private static let fillZoomsKept = 8
    private static let timeLeftAfterFrames = 5
    private static let underAMinute: TimeInterval = 50
}
