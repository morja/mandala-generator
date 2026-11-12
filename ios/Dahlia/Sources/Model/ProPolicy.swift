import Foundation

// The rules of the one Pro purchase on the iPhone (WP-9), the counterpart of tearoom-android-kit's `UnlockPolicy`
// (ADR-0022): only a verified purchase counts; Apple reporting a refund or a revoked family share removes Pro; a store
// that cannot be asked never does. Pure, so the unit tests check every case without StoreKit.

/// What the rules need to know about one StoreKit transaction for Pro, independent of StoreKit.
struct ProTransaction: Equatable, Sendable {
    let productID: String
    /// StoreKit checked Apple's signature (`VerificationResult.verified`).
    let verified: Bool
    /// Refunded, or the family share was taken back (`revocationDate` is set).
    let revoked: Bool
    /// Bought by a family member and shared (Family Sharing is on, F4 a); counts like an own purchase.
    var familyShared = false
    /// The purchase this transaction belongs to (`originalID`), so a revoked one is recognised in a stale entitlement list.
    var originalID: UInt64 = 0
}

/// Why there is no price (the kit's `PriceProblem`).
enum ProPriceProblem: Equatable, Sendable {
    /// Purchases are turned off on this iPhone (Screen Time), `AppStore.canMakePayments` is false.
    case purchasesNotAllowed
    /// The App Store answered without the product.
    case productUnavailable
    /// The App Store could not be reached.
    case storeUnreachable
}

/// Feedback for one purchase or restore (the kit's `UnlockMessage`).
enum ProMessage: Equatable, Sendable {
    case unlocked
    case pending
    /// Restore: the App Store confirms the purchase.
    case activeHere
    /// Restore: the App Store knows no purchase for this Apple Account.
    case notFound
    /// Restore: the App Store could not be asked; Pro stays as it was.
    case storeUnreachable
    case purchaseNotStarted
    case purchaseIncomplete
}

enum ProPolicy {
    /// A transaction that unlocks Pro: this product, signed by Apple, not taken back. Family shares count too.
    static func counts(_ t: ProTransaction, productID: String) -> Bool {
        t.productID == productID && t.verified && !t.revoked
    }

    /// Whether Pro is purchased after reading StoreKit: the current entitlements; the transactions Apple reported as
    /// revoked (the latest transaction for the product, and the update that asked for this check); and whether the App
    /// Store just answered a sync (`confirmed`, the restore button).
    ///
    /// - A verified entitlement unlocks, unless Apple reported that same purchase as revoked (StoreKit's entitlement
    ///   list can lag a moment behind the revocation it has just sent).
    /// - A verified revoked transaction otherwise removes Pro: a refund, or a family share taken back.
    /// - A sync that went through without either removes Pro (the purchase belongs to another Apple Account).
    /// - Otherwise nothing is known (offline, StoreKit's cache not filled yet): the cached value stays.
    static func purchasedAfterCheck(cached: Bool, entitlements: [ProTransaction], reported: [ProTransaction],
                                    confirmed: Bool, productID: String) -> Bool {
        let revoked = reported.filter { $0.productID == productID && $0.verified && $0.revoked }
        let revokedIDs = Set(revoked.map(\.originalID))
        if entitlements.contains(where: { counts($0, productID: productID) && !revokedIDs.contains($0.originalID) }) { return true }
        if !revoked.isEmpty { return false }
        if confirmed { return false }
        return cached
    }

    /// What one transaction from `Transaction.updates` or a purchase means: `.grant` unlocks, `.recheck` asks StoreKit
    /// again (a refund or revocation, which removes Pro unless another entitlement remains), `.ignore` changes nothing
    /// (another product, or a signature that does not check out: it neither unlocks nor removes).
    enum UpdateEffect: Equatable { case grant, recheck, ignore }

    static func effect(of t: ProTransaction, productID: String) -> UpdateEffect {
        guard t.productID == productID, t.verified else { return .ignore }
        return t.revoked ? .recheck : .grant
    }

    /// A check that started before the latest grant must not remove it: StoreKit's entitlements can lag behind a
    /// transaction that has just arrived (the race between the check at app start and `Transaction.updates`).
    static func mayRemove(grantsWhenCheckStarted: Int, grantsNow: Int) -> Bool {
        grantsWhenCheckStarted == grantsNow
    }

    /// Whether the buy button is enabled (the kit's `canBuy`): a price, nothing running, nothing pending, not owned.
    static func canBuy(hasPrice: Bool, busy: Bool, pending: Bool, purchased: Bool) -> Bool {
        hasPrice && !busy && !pending && !purchased
    }
}
