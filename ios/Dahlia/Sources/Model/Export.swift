import AVFoundation
import BackgroundTasks
import CoreVideo
import Darwin
import Foundation
import Photos
import UIKit

/// Writes the moving picture as an H.264 MP4 with AVFoundation (PLAN-4 section 3.7, approval A8), the iOS
/// counterpart of Android's `Mp4Writer`: same bit rate, frame rate and key-frame interval, and the same colours.
///
/// Colours: core converts each sRGB frame to Y′CbCr 4:2:0 with the BT.709 matrix in limited (video) range
/// (`VideoFrames`, as on Android); the frames go to the encoder as NV12 (`420YpCbCr8BiPlanarVideoRange`),
/// so the encoder converts nothing. The file is tagged BT.709 primaries, transfer and matrix, as Android's
/// `COLOR_STANDARD_BT709`, `COLOR_TRANSFER_SDR_VIDEO` and `COLOR_RANGE_LIMITED`: limited range because
/// that is what players, Photos and messengers assume for H.264 without asking.
final class VideoWriter: @unchecked Sendable {
    let url: URL
    private let writer: AVAssetWriter
    private let input: AVAssetWriterInput
    private let adaptor: AVAssetWriterInputPixelBufferAdaptor
    private let fps: Int32

    /// About 0.15 bits per pixel, as Android: clean turning lines, a few MB for ten seconds.
    private static let bitsPerPixel = 0.15

    init(url: URL, width: Int, height: Int, fps: Int) throws {
        self.url = url
        self.fps = Int32(fps)
        try? FileManager.default.removeItem(at: url)
        writer = try AVAssetWriter(outputURL: url, fileType: .mp4)
        let colour: [String: Any] = [
            AVVideoColorPrimariesKey: AVVideoColorPrimaries_ITU_R_709_2,
            AVVideoTransferFunctionKey: AVVideoTransferFunction_ITU_R_709_2,
            AVVideoYCbCrMatrixKey: AVVideoYCbCrMatrix_ITU_R_709_2,
        ]
        let settings: [String: Any] = [
            AVVideoCodecKey: AVVideoCodecType.h264,
            AVVideoWidthKey: width,
            AVVideoHeightKey: height,
            AVVideoColorPropertiesKey: colour,
            AVVideoCompressionPropertiesKey: [
                AVVideoAverageBitRateKey: Int(Double(width * height * fps) * Self.bitsPerPixel),
                AVVideoExpectedSourceFrameRateKey: fps,
                // One key frame a second, as Android's KEY_I_FRAME_INTERVAL 1.
                AVVideoMaxKeyFrameIntervalKey: fps,
                AVVideoProfileLevelKey: AVVideoProfileLevelH264HighAutoLevel,
            ] as [String: Any],
        ]
        input = AVAssetWriterInput(mediaType: .video, outputSettings: settings)
        input.expectsMediaDataInRealTime = false
        adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange,
            kCVPixelBufferWidthKey as String: width,
            kCVPixelBufferHeightKey as String: height,
        ])
        guard writer.canAdd(input) else { throw Failure.setup }
        writer.add(input)
        guard writer.startWriting() else { throw writer.error ?? Failure.setup }
        writer.startSession(atSourceTime: .zero)
    }

    enum Failure: Error { case setup, buffer }

    /// A buffer from the writer's pool for the next frame, tagged with the colours core wrote into it.
    func nextBuffer() async throws -> CVPixelBuffer {
        // The encoder's input fills up until it has encoded what it has; wait a moment and look again.
        while !input.isReadyForMoreMediaData {
            if writer.status == .failed { throw writer.error ?? Failure.buffer }
            try await Task.sleep(for: .milliseconds(5))
        }
        guard let pool = adaptor.pixelBufferPool else { throw writer.error ?? Failure.buffer }
        var buffer: CVPixelBuffer?
        guard CVPixelBufferPoolCreatePixelBuffer(nil, pool, &buffer) == kCVReturnSuccess, let buffer else { throw Failure.buffer }
        CVBufferSetAttachment(buffer, kCVImageBufferColorPrimariesKey, kCVImageBufferColorPrimaries_ITU_R_709_2, .shouldPropagate)
        CVBufferSetAttachment(buffer, kCVImageBufferTransferFunctionKey, kCVImageBufferTransferFunction_ITU_R_709_2, .shouldPropagate)
        CVBufferSetAttachment(buffer, kCVImageBufferYCbCrMatrixKey, kCVImageBufferYCbCrMatrix_ITU_R_709_2, .shouldPropagate)
        return buffer
    }

    /// Appends frame `index` (presentation time index / fps).
    func append(_ buffer: CVPixelBuffer, index: Int) throws {
        guard adaptor.append(buffer, withPresentationTime: CMTime(value: CMTimeValue(index), timescale: fps)) else {
            throw writer.error ?? Failure.buffer
        }
    }

    /// Ends the stream and closes the file; the video is complete only after this.
    func finish() async throws {
        input.markAsFinished()
        await writer.finishWriting()
        if writer.status != .completed { throw writer.error ?? Failure.buffer }
    }

    /// Stops and removes the unfinished file.
    func cancel() {
        if writer.status == .writing { writer.cancelWriting() }
        try? FileManager.default.removeItem(at: url)
    }
}

/// Where exports wait for Photos or the share sheet: the app's caches, emptied before each new one
/// (Android's `VideoFiles.workFile` and `ImageShare`).
enum ExportFiles {
    static func fresh(_ name: String) -> URL {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("export", isDirectory: true)
        try? FileManager.default.removeItem(at: dir)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent(name)
    }
}

extension PhotoLibrary {
    static func addVideo(_ url: URL, fileName: String) async throws {
        guard await mayAdd() else { throw Failure.denied }
        do {
            try await PHPhotoLibrary.shared().performChanges {
                let options = PHAssetResourceCreationOptions()
                options.originalFilename = fileName
                PHAssetCreationRequest.forAsset().addResource(with: .video, fileURL: url, options: options)
            }
        } catch {
            throw Failure.failed(error.localizedDescription)
        }
    }
}

/// The system share sheet for a file, shown above whatever is open (the save sheet, or the editor after the menu closed).
@MainActor
enum ShareSheet {
    static func present(_ url: URL) {
        guard let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first(where: { $0.activationState == .foregroundActive })
                ?? UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first,
              var top = scene.keyWindow?.rootViewController else { return }
        while let next = top.presentedViewController, !next.isBeingDismissed { top = next }
        let sheet = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        sheet.popoverPresentationController?.sourceView = top.view
        top.present(sheet, animated: true)
    }
}

/// Keeps an export running when the app leaves the screen for a moment (PLAN-4 section 3.2, way a).
///
/// Every export holds a background task assertion (`beginBackgroundTask`), which lets a picture finish
/// (iOS grants about half a minute). A video also asks for a continued processing task (iOS 26,
/// `BGContinuedProcessingTask`): iOS then shows the progress itself and lets the work go on while the app is
/// in the background, until the user stops it there or the system needs the resources. When iOS refuses the
/// request, the video goes on as long as the assertion lasts and stops cleanly when it runs out.
@MainActor
final class BackgroundWork {
    private var assertion: UIBackgroundTaskIdentifier = .invalid
    private var task: BGContinuedProcessingTask?
    private var identifier: String?
    private var total: Int64 = 0
    private var done: Int64 = 0
    private var finished = false
    private let expired: @MainActor () -> Void

    /// The identifier family in Info.plist (`BGTaskSchedulerPermittedIdentifiers`); each video adds its own suffix.
    nonisolated static var family: String { (Bundle.main.bundleIdentifier ?? "one.tearoom.mandala") + ".export" }

    /// The exports waiting for their continued processing task, by identifier.
    private static var waiting: [String: BackgroundWork] = [:]

    /// What happened to the last continued processing request, for the log and IOS-7's finding.
    private(set) static var lastOutcome = "none"

    /// `expired`: iOS ends the time it gave (or the user stopped the task in the system's progress); the export must stop.
    init(name: String, expired: @escaping @MainActor () -> Void) {
        self.expired = expired
        assertion = UIApplication.shared.beginBackgroundTask(withName: name) { [weak self] in
            MainActor.assumeIsolated { self?.expire(reason: "assertion") }
        }
    }

    /// Asks iOS for a continued processing task with its own progress (videos), `units` steps long.
    func continueInBackground(title: String, subtitle: String, units: Int) {
        total = Int64(units)
        let id = Self.family + "." + UUID().uuidString.prefix(8)
        identifier = id
        guard Self.register(id) else {
            Self.lastOutcome = "not registered"
            Engine.trace("background continued processing \(Self.lastOutcome)")
            return
        }
        Self.waiting[id] = self
        let request = BGContinuedProcessingTaskRequest(identifier: id, title: title, subtitle: subtitle)
        // Fail rather than wait: a queued task would only start after the video is done.
        request.strategy = .fail
        do {
            try BGTaskScheduler.shared.submit(request)
            Self.lastOutcome = "submitted"
        } catch {
            Self.waiting[id] = nil
            Self.lastOutcome = "refused: \(error.localizedDescription)"
        }
        Engine.trace("background continued processing \(Self.lastOutcome)")
    }

    func progress(_ completed: Int, title: String? = nil, subtitle: String? = nil) {
        done = Int64(completed)
        task?.progress.completedUnitCount = done
        if let title, let subtitle { task?.updateTitle(title, subtitle: subtitle) }
    }

    /// Ends the assertion and the continued task; call it once the export is done, failed or cancelled.
    func end(success: Bool) {
        guard !finished else { return }
        finished = true
        if let identifier { Self.waiting[identifier] = nil }
        task?.setTaskCompleted(success: success)
        task = nil
        if assertion != .invalid {
            UIApplication.shared.endBackgroundTask(assertion)
            assertion = .invalid
        }
    }

    private func attach(_ task: BGContinuedProcessingTask) {
        guard !finished else {
            task.setTaskCompleted(success: true)
            return
        }
        self.task = task
        task.progress.totalUnitCount = total
        task.progress.completedUnitCount = done
        task.expirationHandler = { [weak self] in
            Task { @MainActor in self?.expire(reason: "continued task") }
        }
        Self.lastOutcome = "running"
        Engine.trace("background continued processing started")
    }

    private func expire(reason: String) {
        // With a continued task running, the assertion's end does not stop the video.
        if reason == "assertion", task != nil {
            UIApplication.shared.endBackgroundTask(assertion)
            assertion = .invalid
            return
        }
        Engine.trace("background time ended (\(reason))")
        expired()
        end(success: false)
    }

    /// Continued processing tasks are registered per identifier, any time before the request (the wildcard in
    /// Info.plist only permits the family); a second registration of one identifier would end the app.
    private static func register(_ id: String) -> Bool {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: id, using: .main) { task in
            MainActor.assumeIsolated {
                guard let task = task as? BGContinuedProcessingTask else {
                    task.setTaskCompleted(success: false)
                    return
                }
                if let work = waiting[task.identifier] { work.attach(task) } else { task.setTaskCompleted(success: false) }
            }
        }
    }
}

/// The app's memory footprint as Xcode's gauge shows it (`phys_footprint`), sampled during exports so IOS-7
/// can name the peak; only in debug builds' log.
enum Footprint {
    static func current() -> Int {
        var info = task_vm_info_data_t()
        var count = mach_msg_type_number_t(MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<natural_t>.size)
        let result = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) { task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), $0, &count) }
        }
        return result == KERN_SUCCESS ? Int(info.phys_footprint) : 0
    }

    /// Samples every 50 ms until cancelled and returns the peak in MB.
    static func watch() -> Task<Int, Never> {
        Task.detached(priority: .utility) {
            var peak = current()
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(50))
                peak = max(peak, current())
            }
            return peak / 1_048_576
        }
    }
}
