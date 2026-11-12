import Foundation

// Texts of the wallpaper page, the daily page and the Shortcuts action (WP-11). Keys are Android's (strings*.xml) or
// those of ios/Dahlia/Strings; ios/scripts/check-sources fails when one is missing from the catalogue.
extension Texts {
    static let menuWallpaper = shellText("menu_wallpaper")
    static let wallpaperAbout = shellText("ios_wallpaper_about")
    static let pairLock = shellText("pair_lock_screen")
    static let pairHome = shellText("pair_home_screen")
    static func pairPreview(_ screen: String) -> String { shellFormat("pair_screen_preview", screen) }
    static let pairLockNote = shellText("pair_lock_note")
    static let pairPro = shellText("wallpaper_pair_pro")
    static let saveBoth = shellText("ios_wallpaper_save_both")
    static let setTitle = shellText("ios_wallpaper_set_title")
    static let setOne = shellText("ios_wallpaper_set_one")
    static let setPair = shellText("ios_wallpaper_set_pair")
    static let creatingWallpaper = shellText("export_creating_wallpaper")
    static let creatingWallpapers = shellText("export_creating_wallpapers")
    static let wallpaperSaved = shellText("ios_wallpaper_saved")
    static let wallpapersSaved = shellText("ios_wallpapers_saved")

    static let dailyWallpaper = shellText("menu_daily_wallpaper")
    static let dailyPro = shellText("editor_daily_pro")
    static func dailyToday(_ name: String) -> String { shellFormat("ios_daily_today", name) }
    static let dailyAbout = shellText("menu_daily_about")
    static let dailyFavourites = shellText("menu_daily_favourites")
    static let dailyAll = shellText("menu_daily_all")
    static func dailySome(_ count: Int, _ total: Int) -> String { shellFormat("menu_daily_some", count, total) }
    static let dailyPickAbout = shellText("menu_daily_pick_about")
    static let dailyChooseAll = shellText("menu_daily_choose_all")
    static let dailyWidgetNote = shellText("menu_daily_widget_note")
    static let dailyAutomationTitle = shellText("ios_daily_automation_title")
    static let dailyAutomationSteps = shellText("ios_daily_automation_steps")
    static let dailyOpenShortcuts = shellText("ios_daily_open_shortcuts")
    static let dailyReadyShortcut = shellText("ios_daily_ready_shortcut")
    static let dailyReadyNote = shellText("ios_daily_ready_note")
    static let dailyNeedsPro = shellText("ios_daily_needs_pro")
    static func dailyLastRunDebug(_ run: String) -> String { shellFormat("ios_debug_wallpaper_last_run", run) }

    private static func shellText(_ key: String) -> String { String(localized: String.LocalizationValue(key)) }

    private static func shellFormat(_ key: String, _ args: CVarArg...) -> String {
        String(format: shellText(key), locale: nil, arguments: args)
    }
}
