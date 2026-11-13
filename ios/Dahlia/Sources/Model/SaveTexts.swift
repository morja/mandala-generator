import Foundation
@preconcurrency import MandalaCore

// Texts of the save sheet, sharing, the moving picture and the line drawing (WP-8). Keys are Android's (strings*.xml)
// or those of ios/Dahlia/Strings; ios/scripts/check-sources fails when one is missing from the catalogue.
extension Texts {
    static let actionSave = shellText("action_save")
    static let share = shellText("menu_share")
    static let shareAction = shellText("action_share")
    static let preparingShare = shellText("export_preparing_share")
    static let stoppedInBackground = shellText("ios_export_stopped_background")

    static let proTitle = shellText("editor_pro_title")
    static let proUsesKeep = shellText("editor_pro_uses_keep")
    static func proWithout(_ action: String) -> String { shellFormat("editor_pro_without", action) }
    static func proFeature(_ feature: ProFeature) -> String {
        switch feature {
        case .moreLayers: shellText("pro_feature_more_layers")
        case .emblem: shellText("pro_feature_emblem")
        case .text: shellText("pro_feature_text")
        default: shellText("pro_feature_moved_layers")
        }
    }

    static let still = shellText("save_still")
    static let moving = shellText("save_moving")
    static let formatTitle = shellText("save_format")
    static let fitWhole = shellText("save_fit_whole")
    static let fitFill = shellText("save_fit_fill")
    static let frameHint = shellText("save_frame_hint")
    static let placedByHand = shellText("save_placed_by_hand")
    static let framePro = shellText("save_frame_pro")
    static let aboutPro = shellText("save_about_pro")
    static let previewDescription = shellText("save_preview_description")
    static let adjust = shellText("save_adjust")
    static let adjustWithPro = shellText("save_adjust_with_pro")
    static let adjustUnchanged = shellText("save_adjust_unchanged")
    static func adjustSummary(_ b: String, _ c: String, _ s: String) -> String { shellFormat("save_adjust_summary", b, c, s) }
    static let adjustPro = shellText("save_adjust_pro")
    static let adjustProQuote = shellText("save_adjust_pro_quote")
    static let adjustQuoteMoves = shellText("save_adjust_quote_moves")
    static let quoteFollows = shellText("save_quote_follows")
    static let save = shellText("save_save")
    static let saveCancel = shellText("save_cancel")
    static let saveWallpaper = shellText("save_wallpaper")
    static let length = shellText("save_length")
    static let chooseSize = shellText("save_choose_size")
    static func saveSize(_ w: Int, _ h: Int) -> String { shellFormat("save_size", w, h) }
    static func saveSizePro(_ w: Int, _ h: Int) -> String { shellFormat("save_size_pro", w, h) }
    static func videoSeconds(_ s: Int) -> String { shellFormat("save_video_seconds", s) }
    static func progressStep(_ label: String) -> String { shellFormat("editor_progress_step", label) }

    static func formatLabel(_ format: Editing.SaveFormat) -> String {
        switch format {
        case .square: shellText("format_square")
        case .screen: shellText("format_screen")
        case .fourFive: shellText("format_4_5")
        case .threeFour: shellText("format_3_4")
        case .twoThree: shellText("format_2_3")
        case .nineSixteen: shellText("format_9_16")
        case .fiveFour: shellText("format_5_4")
        case .fourThree: shellText("format_4_3")
        case .threeTwo: shellText("format_3_2")
        default: shellText("format_16_9")
        }
    }

    /// The format as the menu lists it: the ratios say whether they stand upright or lie (Android's FormatMenu).
    static func formatMenuLabel(_ format: Editing.SaveFormat) -> String {
        if !format.isRatio { return formatLabel(format) }
        return shellFormat(format.isLandscape ? "save_ratio_landscape" : "save_ratio_portrait", formatLabel(format))
    }

    static let creatingVideo = shellText("export_creating_video")
    static let saveVideoPro = shellText("save_video_pro")
    static let videoUnderMinute = shellText("export_video_under_minute")
    static func videoMinutes(_ n: Int) -> String { shellFormat("export_video_minutes", n) }
    static func videoSaved(_ w: Int, _ h: Int) -> String { shellFormat("message_video_saved", w, h) } // iOS variant: Photos
    static func videoFailed(_ reason: String) -> String { shellFormat("message_video_failed", reason) }

    static let lineDrawing = shellText("save_line_drawing")
    static let lineDrawingPro = shellText("save_line_drawing_pro")
    static let lineDrawingDetail = shellText("save_line_drawing_detail")
    static let lineDrawingPoints = shellText("save_line_drawing_points")
    static let lineDrawingColour = shellText("save_line_drawing_colour")
    static let lineDrawingLines = shellText("save_line_drawing_lines")
    static let creatingLineDrawing = shellText("export_creating_line_drawing")
    static let lineDrawingSaved = shellText("message_line_drawing_saved")
    static func lineDrawingFailed(_ reason: String) -> String { shellFormat("message_line_drawing_failed", reason) }

    private static func shellText(_ key: String) -> String { String(localized: String.LocalizationValue(key)) }

    private static func shellFormat(_ key: String, _ args: CVarArg...) -> String {
        String(format: shellText(key), locale: nil, arguments: args)
    }
}
