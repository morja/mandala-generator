import XCTest

/// The rules of `ProPolicy` without StoreKit, as tearoom-android-kit's `UnlockPolicyTest` has them for Play.
final class ProPolicyTests: XCTestCase {
    private let id = "one.tearoom.mandala.pro"

    /// A transaction; `purchase` is its `originalID` (own purchase 1, a family member's 2 by default).
    private func t(_ verified: Bool = true, revoked: Bool = false, family: Bool = false, product: String? = nil, purchase: UInt64? = nil) -> ProTransaction {
        ProTransaction(productID: product ?? id, verified: verified, revoked: revoked, familyShared: family, originalID: purchase ?? (family ? 2 : 1))
    }

    private func check(cached: Bool, _ entitlements: [ProTransaction], latest: ProTransaction? = nil, confirmed: Bool = false) -> Bool {
        ProPolicy.purchasedAfterCheck(cached: cached, entitlements: entitlements, reported: [latest].compactMap { $0 }, confirmed: confirmed, productID: id)
    }

    /// StoreKit's entitlement list can still hold a purchase whose revocation it has just reported.
    func testARevocationWinsOverAStaleEntitlementOfTheSamePurchase() {
        XCTAssertFalse(check(cached: true, [t()], latest: t(revoked: true)))
    }

    func testOnlyAVerifiedEntitlementOfTheProductUnlocks() {
        XCTAssertTrue(check(cached: false, [t()]))
        XCTAssertFalse(check(cached: false, [t(false)]))
        XCTAssertFalse(check(cached: false, [t(product: "other")]))
        XCTAssertFalse(check(cached: false, [t(revoked: true)]))
    }

    func testAFamilyShareCountsLikeAnOwnPurchase() {
        XCTAssertTrue(check(cached: false, [t(family: true)]))
    }

    func testAFamilyShareTakenBackRemovesPro() {
        XCTAssertFalse(check(cached: true, [], latest: t(revoked: true, family: true)))
    }

    func testAnOwnPurchaseStaysWhenTheFamilyShareIsTakenBack() {
        XCTAssertTrue(check(cached: true, [t()], latest: t(revoked: true, family: true)))
    }

    func testARefundReportedByAppleRemovesTheCache() {
        XCTAssertFalse(check(cached: true, [], latest: t(revoked: true)))
    }

    func testAnUnverifiedRefundDoesNotRemove() {
        XCTAssertTrue(check(cached: true, [], latest: t(false, revoked: true)))
    }

    func testNothingKnownKeepsTheCache() {
        XCTAssertTrue(check(cached: true, []))
        XCTAssertFalse(check(cached: false, []))
    }

    func testAConfirmedSyncWithoutThePurchaseRemovesIt() {
        XCTAssertFalse(check(cached: true, [], confirmed: true))
        XCTAssertTrue(check(cached: true, [t()], confirmed: true))
    }

    func testUpdates() {
        XCTAssertEqual(ProPolicy.effect(of: t(), productID: id), .grant)
        XCTAssertEqual(ProPolicy.effect(of: t(family: true), productID: id), .grant)
        XCTAssertEqual(ProPolicy.effect(of: t(revoked: true), productID: id), .recheck)
        XCTAssertEqual(ProPolicy.effect(of: t(false), productID: id), .ignore)
        XCTAssertEqual(ProPolicy.effect(of: t(false, revoked: true), productID: id), .ignore)
        XCTAssertEqual(ProPolicy.effect(of: t(product: "other"), productID: id), .ignore)
    }

    func testAnOlderCheckCannotRemoveANewerGrant() {
        XCTAssertFalse(ProPolicy.mayRemove(grantsWhenCheckStarted: 0, grantsNow: 1))
        XCTAssertTrue(ProPolicy.mayRemove(grantsWhenCheckStarted: 1, grantsNow: 1))
    }

    func testCanBuy() {
        XCTAssertTrue(ProPolicy.canBuy(hasPrice: true, busy: false, pending: false, purchased: false))
        XCTAssertFalse(ProPolicy.canBuy(hasPrice: false, busy: false, pending: false, purchased: false))
        XCTAssertFalse(ProPolicy.canBuy(hasPrice: true, busy: true, pending: false, purchased: false))
        XCTAssertFalse(ProPolicy.canBuy(hasPrice: true, busy: false, pending: true, purchased: false))
        XCTAssertFalse(ProPolicy.canBuy(hasPrice: true, busy: false, pending: false, purchased: true))
    }
}
