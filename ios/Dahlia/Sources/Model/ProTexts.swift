import Foundation

// Texts of the Pro sheet and the Pro page of the menu (WP-9), Android's keys of `Pro.kt` and the kit's sheet. The keys
// that name Google Play have iOS texts in ios/Dahlia/Strings (App Store, Apple Account, Screen Time).
extension Texts {
    static let proMarkAction = shellText("editor_pro_mark_action")
    static let proUses = shellText("editor_pro_uses")
    static func proFeatureItem(_ name: String) -> String { shellFormat("editor_pro_feature_item", name) }
    static let proAddsMaking = shellText("editor_pro_adds_making")
    static let proAddsSaving = shellText("editor_pro_adds_saving")
    static let proAddsWallpapers = shellText("editor_pro_adds_wallpapers")
    static var proSummary: String {
        shellFormat("editor_pro_summary", proAddsMaking, proAddsSaving, proAddsWallpapers, shellText("editor_pro_try"), shellText("editor_pro_once"))
    }
    static func proBuyFor(_ price: String) -> String { shellFormat("editor_pro_buy_for", price) }
    static let proBuy = shellText("editor_pro_buy")
    static let proLoadingPrice = shellText("pro_loading_price")
    static let proRemove = shellText("editor_pro_remove")
    static let proRetry = shellText("editor_pro_retry")
    static let proRestore = shellText("editor_pro_restore")
    static let proUnlocked = shellText("pro_unlocked")
    static let proPending = shellText("pro_pending")

    static let menuProDetail = shellText("menu_pro_detail")
    static let menuProActive = shellText("menu_pro_active")
    static let menuProThanks = shellText("menu_pro_thanks")
    static let menuProOnce = shellText("menu_pro_once")

    static func proPriceProblem(_ problem: ProPriceProblem) -> String {
        switch problem {
        case .purchasesNotAllowed: shellText("editor_pro_unavailable")
        case .productUnavailable: shellText("pro_product_unavailable")
        case .storeUnreachable: shellText("pro_play_unavailable")
        }
    }

    static func proMessage(_ message: ProMessage) -> String {
        switch message {
        case .unlocked: proUnlocked
        case .pending: proPending
        case .activeHere: shellText("pro_restore_active")
        case .notFound: shellText("pro_restore_not_found")
        case .storeUnreachable: shellText("pro_restore_unreachable")
        case .purchaseNotStarted: shellText("pro_purchase_not_started")
        case .purchaseIncomplete: shellText("pro_purchase_incomplete")
        }
    }

    private static func shellText(_ key: String) -> String { String(localized: String.LocalizationValue(key)) }

    private static func shellFormat(_ key: String, _ args: CVarArg...) -> String {
        String(format: shellText(key), locale: nil, arguments: args)
    }
}
