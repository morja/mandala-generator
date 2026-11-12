import Foundation

// Texts of the tour, the Dahlia page of the menu and About (WP-12). Keys are Android's (strings_tour.xml,
// strings_menu.xml) or those of ios/Dahlia/Strings; ios/scripts/check-sources fails when one is missing from the catalogue.
extension Texts {
    static let tourPane = tourText("tour_pane")
    static let tourNext = tourText("tour_next")
    static let tourBack = tourText("tour_back")
    static let tourSkip = tourText("tour_skip")
    static let tourDone = tourText("tour_done")
    static let tourAgain = tourText("tour_again")
    static let tourPlaceEffects = tourText("tour_place_effects")
    static let tourPlaceMenu = tourText("tour_place_menu")
    static let tourPlacePlay = tourText("tour_place_play")

    /// The app's name; not translated (Android's `menu_dahlia` is untranslatable too).
    static let menuDahlia = "Dahlia"
    static let menuGuide = tourText("menu_guide")
    static let menuTour = tourText("menu_tour")
    static let menuRate = tourText("menu_rate")
    static let menuAbout = tourText("menu_about")

    static let aboutTitle = tourText("about_title")
    static let aboutClose = tourText("about_close")
    static let aboutLicence = tourText("about_licence")
    static let aboutFreeSoftware = tourText("about_free_software")
    static let aboutSourceCode = tourText("about_source_code")
    static let aboutAdditionalPermission = tourText("about_additional_permission")
    static let aboutGplSection7 = tourText("about_gpl_section_7")
    static let aboutPrivacy = tourText("about_privacy")
    static let aboutPrivacyText = tourText("about_privacy_text")
    static let aboutPrivacyPolicy = tourText("about_privacy_policy")
    static let aboutLibraries = tourText("about_libraries")
    static let aboutFullText = tourText("about_full_text")

    /// "3 of 8".
    static func tourCount(_ n: Int, _ of: Int) -> String { tourFormat("tour_count", n, of) }

    static func tourTitle(_ step: TourStep) -> String { tourText("tour_\(step.stem)_title") }

    /// The step's sentences; the layers step names the free number of layers, the style step two real styles.
    static func tourBody(_ step: TourStep, freeLayers: Int) -> String {
        let key = "tour_\(step.stem)_body"
        switch step {
        case .layers: return tourFormat(key, freeLayers)
        case .styles: return tourFormat(key, style(.spirograph), style(.roseCurves))
        default: return tourText(key)
        }
    }

    /// "Version 0.1 (1)".
    static func aboutVersion(_ version: String, _ build: Int) -> String { tourFormat("about_version", version, build) }

    private static func tourText(_ key: String) -> String { String(localized: String.LocalizationValue(key)) }

    private static func tourFormat(_ key: String, _ args: CVarArg...) -> String {
        String(format: tourText(key), locale: nil, arguments: args)
    }
}

extension Texts {
    /// The Dahlia page's line: what the page holds ("Guide, Tour, About"), from the words of its rows.
    static var menuDahliaDetail: String { [menuGuide, menuTour, menuAbout].joined(separator: ", ") }
}
