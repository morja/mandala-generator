import Foundation
@preconcurrency import MandalaCore

// Texts of the shell (WP-7): menu, history, favourites, play field, files. Keys are Android's (strings*.xml) or those
// of ios/Dahlia/Strings; ios/scripts/check-sources fails when one is missing from the catalogue.
extension Texts {
    static let select = shellText("ios_select")
    static let undo = shellText("editor_undo")
    static let back = shellText("editor_back")
    static let forward = shellText("editor_forward")
    static let favouritesCancel = shellText("editor_favourites_cancel")
    static let favouriteDelete = shellText("editor_favourite_delete")
    static let fileUnreadable = shellText("message_file_unreadable")
    static let fileNoLayers = shellText("message_file_no_layers")
    static let fileUnknown = shellText("message_file_unknown")
    static let settingsSaved = shellText("message_settings_saved")
    static let settingsFailed = shellText("message_settings_failed")

    static let menuNoLayers = shellText("menu_no_layers")
    static let menuFavourite = shellText("menu_favourite")
    static let menuNewMandala = shellText("menu_new_mandala")
    static let menuTheme = shellText("menu_theme")
    static let menuThemeDark = shellText("menu_theme_dark")
    static let menuThemeLight = shellText("menu_theme_light")
    static let menuMore = shellText("menu_more")
    static let menuBack = shellText("menu_back")
    static let menuNow = shellText("menu_now")
    static let menuTryAnother = shellText("menu_try_another")
    static let menuUseNew = shellText("menu_use_new")
    static let menuOpenFile = shellText("menu_open_file")
    static let menuOpenFileDetail = shellText("menu_open_file_detail")
    static let menuSaveSettings = shellText("menu_save_settings")
    static let menuSaveSettingsDetail = shellText("menu_save_settings_detail")

    static let playTitle = shellText("play_title")
    static let playMap = shellText("play_map")
    static let playCompass = shellText("play_compass")
    static let playDone = shellText("play_done")
    static let playHint = shellText("play_hint")
    static let playNewMap = shellText("play_new_map")
    static let playEffects = shellText("play_effects")
    static let playFromHere = shellText("play_from_here")
    static let playKeep = shellText("play_keep")
    static let playLight = shellText("play_light")
    static let playShape = shellText("play_shape")
    static let playDepth = shellText("play_depth")
    static let playColour = shellText("play_colour")
    static let playFieldDescription = shellText("play_field_description")

    static let showLarge = shellText("editor_show_large")
    static let makeSmall = shellText("editor_make_small")
    static let largerImage = shellText("editor_larger_image")
    static let smallerImage = shellText("editor_smaller_image")

    static let headerNoLayers = shellText("controls_header_no_layers")
    static let headerNoBackground = shellText("controls_header_no_background")
    static let off = shellText("controls_off")
    static let on = shellText("controls_on")

    static func newKind(_ kind: NewKind) -> String {
        switch kind {
        case .variation: shellText("menu_die_variation")
        case .layers: shellText("menu_die_layers")
        default: shellText("menu_die_everything")
        }
    }

    static func newKindDetail(_ kind: NewKind) -> String {
        switch kind {
        case .variation: shellText("menu_die_variation_detail")
        case .layers: shellText("menu_die_layers_detail")
        default: shellText("menu_die_everything_detail")
        }
    }

    static func favouritesSelected(_ n: Int) -> String { shellFormat("editor_favourites_selected", n) }
    static func favouritesDeleted(_ n: Int) -> String { shellFormat("message_favourites_deleted", n) }
    static func favouritesImportedNote(_ imported: String, _ already: String) -> String { shellFormat("message_favourites_imported_note", imported, already) }
    static func layersSummary(_ n: Int, emblem: Bool) -> String { plural(emblem ? "menu_layers_emblem_on" : "menu_layers_emblem_off", n) }

    /// A plural from the catalogue (its variations are chosen by the number).
    static func plural(_ key: String, _ n: Int) -> String {
        String.localizedStringWithFormat(NSLocalizedString(key, comment: ""), n)
    }

    private static func shellText(_ key: String) -> String { String(localized: String.LocalizationValue(key)) }

    private static func shellFormat(_ key: String, _ args: CVarArg...) -> String {
        String(format: shellText(key), locale: nil, arguments: args)
    }
}
