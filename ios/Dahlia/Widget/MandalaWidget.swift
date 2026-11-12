import ImageIO
import SwiftUI
import WidgetKit

// "Mandala of the day" on the home screen (ADR-0020 and its iPhone amendment, PLAN-4 WP-10). Small and large show the
// picture alone, edge to edge; medium (Android's wide widget) shows the picture without its words at the left and the
// words beside it. The extension never renders: it reads the pictures and the index the app wrote into the App Group
// and hands WidgetKit one entry per day, each starting at 4:00, so the daily turn moves on without the app.

@main
struct DahliaWidgets: WidgetBundle {
    var body: some Widget { MandalaWidget() }
}

struct MandalaWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: WidgetKind.mandala, intent: MandalaWidgetIntent.self, provider: MandalaProvider()) { entry in
            MandalaWidgetView(entry: entry)
        }
        .configurationDisplayName(WidgetTexts.label)
        .description(WidgetTexts.description)
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
        // The picture goes to the edges, as on Android; the words keep their own padding.
        .contentMarginsDisabled()
    }
}

/// One day of a widget: what it shows from `date` on.
struct MandalaEntry: TimelineEntry {
    enum Content {
        /// The app has not written anything yet: the card alone (Android's loading layout).
        case loading
        /// No favourite at all.
        case empty
        case picture(URL?, words: WidgetIndex.Words?, favourite: String)
    }

    let date: Date
    let content: Content
    let shape: WidgetShape
    let light: Bool
    let mark: Bool
}

struct MandalaProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> MandalaEntry {
        MandalaEntry(date: Date(), content: .loading, shape: Self.shape(context.family), light: false, mark: false)
    }

    func snapshot(for configuration: MandalaWidgetIntent, in context: Context) async -> MandalaEntry {
        entries(for: configuration, in: context, now: Date()).entries.first ?? placeholder(in: context)
    }

    func timeline(for configuration: MandalaWidgetIntent, in context: Context) async -> Timeline<MandalaEntry> {
        let shape = Self.shape(context.family)
        // The app renders the next pictures at the size this phone's widgets have.
        WidgetSizes.save(shape, context.displaySize)
        let (entries, next) = entries(for: configuration, in: context, now: Date())
        WidgetDebug.recordFootprint("timeline")
        return Timeline(entries: entries, policy: next.map { .after($0) } ?? .never)
    }

    /// Today's entry and one for each later day the app has prepared; after them the widget stays on the last
    /// picture (F8 a) and asks again the day after, in case the app ran in the background meanwhile.
    private func entries(for configuration: MandalaWidgetIntent, in context: Context, now: Date) -> (entries: [MandalaEntry], next: Date?) {
        let shape = Self.shape(context.family)
        let look = configuration.appearance
        guard let index = WidgetFiles.readIndex() else {
            // The app writes the index at its first start and reloads the widgets then.
            return ([MandalaEntry(date: now, content: .loading, shape: shape, light: false, mark: false)], nil)
        }
        let light = WidgetRules.isLight(look, appLight: index.appLight)
        let fixed = configuration.mandala?.fixedID

        func entry(at date: Date, day: WidgetIndex.Day?) -> MandalaEntry {
            let content: MandalaEntry.Content
            if let favourite = WidgetRules.favourite(in: index, fixed: fixed, day: day) ?? index.favourites.first {
                content = .picture(WidgetRules.picture(favourite, shape: shape).flatMap(WidgetFiles.url),
                                   words: shape == .wide ? WidgetRules.words(favourite, day: day) : nil, favourite: favourite.id)
            } else {
                content = .empty
            }
            return MandalaEntry(date: date, content: content, shape: shape, light: light, mark: index.mark)
        }

        let today = WidgetRules.day(in: index, at: now)
        var entries = [entry(at: now, day: today)]
        for day in index.days where day.start > now { entries.append(entry(at: day.start, day: day)) }
        let last = index.days.last?.number ?? WidgetDay.number(of: now)
        return (entries, WidgetDay.start(of: max(last, WidgetDay.number(of: now)) + 1))
    }

    static func shape(_ family: WidgetFamily) -> WidgetShape {
        switch family {
        case .systemMedium: .wide
        case .systemLarge, .systemExtraLarge: .large
        default: .small
        }
    }
}

struct MandalaWidgetView: View {
    let entry: MandalaEntry

    private var t: Tokens { entry.light ? .light : .dark }

    var body: some View {
        content
            .overlay(alignment: .bottomTrailing) { if entry.mark { DebugMark().padding(Spacing.md) } }
            .containerBackground(for: .widget) { t.surface }
            .widgetURL(WidgetLink.url(favourite: favourite))
    }

    private var favourite: String? {
        if case .picture(_, _, let id) = entry.content { return id }
        return nil
    }

    @ViewBuilder private var content: some View {
        switch entry.content {
        case .loading:
            // The card alone while the app has not written the first pictures.
            Rectangle().fill(t.surface)
        case .empty:
            Text(WidgetTexts.empty).textRole(.note).foregroundStyle(t.textMuted).multilineTextAlignment(.center).padding(Spacing.lg)
        case .picture(let url, let words, _):
            if entry.shape == .wide {
                GeometryReader { geo in
                    HStack(spacing: 0) {
                        Picture(url: url).frame(width: geo.size.height, height: geo.size.height)
                        if let words { Words(words: words, t: t) }
                        Spacer(minLength: 0)
                    }
                }
            } else {
                Picture(url: url)
            }
        }
    }
}

/// The picture filling its frame, decoded through ImageIO at the size it is shown, so the extension stays small.
private struct Picture: View {
    let url: URL?
    @Environment(\.displayScale) private var scale

    var body: some View {
        GeometryReader { geo in
            if let url, let image = WidgetImage.load(url, maxPixels: max(geo.size.width, geo.size.height) * scale) {
                Image(decorative: image, scale: 1).resizable().scaledToFill()
                    .frame(width: geo.size.width, height: geo.size.height).clipped()
            }
        }
    }
}

/// The favourite's words or the quote of the day, beside the picture of the wide widget.
private struct Words: View {
    let words: WidgetIndex.Words
    let t: Tokens

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(words.text).textRole(.body).foregroundStyle(t.text).minimumScaleFactor(0.8)
            if !words.author.isEmpty {
                Text("— " + words.author).textRole(.note).foregroundStyle(t.textMuted).lineLimit(1)
            }
        }
        .padding(Spacing.lg)
    }
}

/// The local-build mark of debug builds (global rule): a dark ring with an amber dot, bottom right, as on the icon.
private struct DebugMark: View {
    var body: some View {
        ZStack {
            Circle().fill(Tokens.dark.background).frame(width: Sizes.swatchSmall / 2, height: Sizes.swatchSmall / 2)
            Circle().fill(Tokens.dark.accent).frame(width: Sizes.icon / 3, height: Sizes.icon / 3)
        }
    }
}

/// Downsampling with ImageIO: never more pixels than shown.
enum WidgetImage {
    static func load(_ url: URL, maxPixels: CGFloat) -> CGImage? {
        guard maxPixels > 0, let source = CGImageSourceCreateWithURL(url as CFURL, [kCGImageSourceShouldCache: false] as CFDictionary) else { return nil }
        // The app writes each picture at the size it is shown: then it is decoded once as it is, with no scaled copy
        // beside it (the thumbnail call keeps the full decode and the copy at the same time).
        let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any]
        let side = max(properties?[kCGImagePropertyPixelWidth] as? Int ?? .max, properties?[kCGImagePropertyPixelHeight] as? Int ?? .max)
        if CGFloat(side) <= maxPixels.rounded(.up) {
            let image = CGImageSourceCreateImageAtIndex(source, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary)
            WidgetDebug.recordFootprint("picture \(side)px as it is")
            return image
        }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: Int(maxPixels.rounded(.up)),
        ]
        let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary)
        WidgetDebug.recordFootprint("picture \(Int(maxPixels))px")
        return image
    }
}

/// The tap: opens the app as it was, with the widget's favourite to offer (ADR-0020, amended 5 November 2026).
enum WidgetLink {
    static func url(favourite: String?) -> URL? {
        let scheme = Bundle.main.object(forInfoDictionaryKey: "DahliaURLScheme") as? String ?? "dahlia"
        var parts = URLComponents()
        parts.scheme = scheme
        parts.host = "widget"
        if let favourite { parts.queryItems = [URLQueryItem(name: "favourite", value: favourite)] }
        return parts.url
    }
}

/// Debug builds keep the extension's peak memory in the App Group, for the measurement in IOS-10.
enum WidgetDebug {
    static let footprintKey = "debugWidgetFootprintKB"

    static func recordFootprint(_ moment: String) {
        #if DEBUG
        var info = task_vm_info_data_t()
        var count = mach_msg_type_number_t(MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<natural_t>.size)
        let result = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) { task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), $0, &count) }
        }
        guard result == KERN_SUCCESS, let defaults = AppGroup.defaults else { return }
        let kb = Int(info.phys_footprint / 1024)
        if kb > defaults.integer(forKey: footprintKey) {
            defaults.set(kb, forKey: footprintKey)
            defaults.set(moment, forKey: footprintKey + ".moment")
        }
        #endif
    }
}
