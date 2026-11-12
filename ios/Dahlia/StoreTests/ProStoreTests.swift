import StoreKit
import StoreKitTest
import XCTest

/// The Pro purchase (WP-9) against the local StoreKit configuration `Config/Dahlia.storekit` (3.99 €, Germany, Family
/// Sharing on), the cases of QA-8: price, purchase, restore after a reinstall, cancel, Ask to Buy, refund, offline with
/// and without Pro, a signature that does not check out. Each test starts from an empty transaction list and its own
/// cache file, as a fresh install would.
@MainActor
final class ProStoreTests: XCTestCase {
    private var session: SKTestSession!
    private var cacheURL: URL!

    override func setUp() async throws {
        let url = try XCTUnwrap(Bundle(for: ProStoreTests.self).url(forResource: "Dahlia", withExtension: "storekit"))
        session = try SKTestSession(contentsOf: url)
        session.resetToDefaultState()
        session.clearTransactions()
        // Storefront Germany and locale German come from the configuration file.
        session.disableDialogs = true
        cacheURL = FileManager.default.temporaryDirectory.appendingPathComponent("pro-\(UUID().uuidString).json")
    }

    override func tearDown() async throws {
        try await session.setSimulatedError(nil, forAPI: .loadProducts)
        try await session.setSimulatedError(nil, forAPI: .purchase)
        try await session.setSimulatedError(nil, forAPI: .appStoreSync)
        try await session.setSimulatedError(nil, forAPI: .verification)
        session.clearTransactions()
        try? FileManager.default.removeItem(at: cacheURL)
    }

    private func makeStore(flag: UserDefaults? = nil) -> ProStore {
        ProStore(cache: ProCache(url: cacheURL), flag: flag, testUnlocked: false)
    }

    /// Waits until `condition` holds (StoreKit's updates arrive asynchronously), at most `seconds`.
    private func eventually(_ seconds: Double = 10, _ condition: () -> Bool) async -> Bool {
        let end = Date().addingTimeInterval(seconds)
        while !condition() && Date() < end { try? await Task.sleep(for: .milliseconds(100)) }
        return condition()
    }

    private func proTransaction() -> SKTestTransaction? {
        session.allTransactions().last { $0.productIdentifier == ProStore.productID }
    }

    // ── Price ──

    func testThePriceComesFromTheStoreInEuro() async {
        let store = makeStore()
        await store.loadPrice()
        let price = store.price ?? ""
        XCTAssertTrue(price.contains("3,99") && price.contains("€"), "price \(price)")
        XCTAssertNil(store.priceProblem)
        XCTAssertTrue(store.canBuy)
    }

    // ── Purchase ──

    func testAPurchaseUnlocksCachesAndSetsTheWidgetFlag() async throws {
        let flag = try XCTUnwrap(UserDefaults(suiteName: "pro-test-\(UUID().uuidString)"))
        let store = makeStore(flag: flag)
        XCTAssertFalse(store.unlocked)
        await store.loadPrice()
        await store.buy()
        XCTAssertTrue(store.purchased)
        XCTAssertEqual(store.message, .unlocked)
        XCTAssertFalse(store.canBuy, "owned: no second purchase")
        XCTAssertTrue(ProCache(url: cacheURL).load(), "cached for offline")
        XCTAssertTrue(flag.bool(forKey: AppGroup.proUnlockedKey), "the flag for the widget")
    }

    /// A reinstall: the cache is gone, StoreKit's entitlement brings Pro back without the restore button.
    func testAReinstallGetsProBackFromTheEntitlement() async {
        let first = makeStore()
        await first.loadPrice()
        await first.buy()
        XCTAssertTrue(first.purchased)
        try? FileManager.default.removeItem(at: cacheURL)

        let reinstalled = makeStore()
        XCTAssertFalse(reinstalled.purchased)
        await reinstalled.check()
        XCTAssertTrue(reinstalled.purchased)
    }

    func testRestoreConfirmsAnOwnedPurchase() async {
        let first = makeStore()
        await first.loadPrice()
        await first.buy()
        try? FileManager.default.removeItem(at: cacheURL)

        let reinstalled = makeStore()
        await reinstalled.restore()
        XCTAssertTrue(reinstalled.purchased)
        XCTAssertEqual(reinstalled.message, .activeHere)
    }

    /// A purchase made elsewhere (another device of the family or the account) arrives through `Transaction.updates`.
    func testAPurchaseFromElsewhereArrivesThroughTheUpdates() async throws {
        let store = makeStore()
        store.start()
        // StoreKitTest records the purchase but may answer the call itself with an error; the update is what counts.
        _ = try? await session.buyProduct(identifier: ProStore.productID)
        let unlocked = await eventually { store.purchased }
        XCTAssertTrue(unlocked)
        XCTAssertEqual(store.message, .unlocked)
    }

    // ── Cancel, pending, refused signature ──

    func testACancelChangesNothingAndSaysNothing() async throws {
        try await session.setSimulatedError(.generic(.userCancelled), forAPI: .purchase)
        let store = makeStore()
        await store.loadPrice()
        await store.buy()
        XCTAssertFalse(store.purchased)
        XCTAssertNil(store.message)
        XCTAssertTrue(store.canBuy, "the button works again")
    }

    func testAskToBuyIsPendingUntilApproved() async throws {
        session.askToBuyEnabled = true
        let store = makeStore()
        store.start()
        await store.loadPrice()
        await store.buy()
        XCTAssertTrue(store.pending)
        XCTAssertFalse(store.purchased)
        XCTAssertEqual(store.message, .pending)
        XCTAssertFalse(store.canBuy, "a second purchase is blocked while one is pending")

        let waiting = try XCTUnwrap(proTransaction())
        XCTAssertTrue(waiting.pendingAskToBuyConfirmation)
        try session.approveAskToBuyTransaction(identifier: waiting.identifier)
        let unlocked = await eventually { store.purchased }
        XCTAssertTrue(unlocked)
        XCTAssertFalse(store.pending)
        XCTAssertEqual(store.message, .unlocked)
    }

    func testAPurchaseWithABadSignatureNeverUnlocks() async throws {
        try await session.setSimulatedError(.verification(.invalidSignature), forAPI: .verification)
        let store = makeStore()
        await store.loadPrice()
        await store.buy()
        XCTAssertFalse(store.purchased)
        XCTAssertEqual(store.message, .purchaseIncomplete)
        await store.check()
        XCTAssertFalse(store.purchased, "an unverified entitlement does not count either")
        XCTAssertFalse(ProCache(url: cacheURL).load())
    }

    // ── Refund ──

    func testARefundRemovesProWhileTheAppRuns() async throws {
        let store = makeStore()
        store.start()
        await store.loadPrice()
        await store.buy()
        XCTAssertTrue(store.purchased)
        try session.refundTransaction(identifier: try XCTUnwrap(proTransaction()).identifier)
        let removed = await eventually { !store.purchased }
        XCTAssertTrue(removed)
        XCTAssertFalse(ProCache(url: cacheURL).load())
    }

    /// Refunded while the app was closed: the next start reads the revoked transaction and removes the cached Pro.
    func testARefundRemovesTheCachedProAtTheNextStart() async throws {
        let first = makeStore()
        await first.loadPrice()
        await first.buy()
        try session.refundTransaction(identifier: try XCTUnwrap(proTransaction()).identifier)

        let next = makeStore()
        XCTAssertTrue(next.purchased, "cached")
        // StoreKitTest applies the refund a moment later; until StoreKit knows, the cache stays.
        let end = Date().addingTimeInterval(10)
        while next.purchased && Date() < end {
            await next.check()
            try await Task.sleep(for: .milliseconds(200))
        }
        XCTAssertFalse(next.purchased)
    }

    // ── Offline ──

    /// Nothing from StoreKit and the App Store unreachable: the cached purchase stays, price and restore say why.
    func testOfflineWithProKeepsPro() async throws {
        ProCache(url: cacheURL).save(true)
        try await session.setSimulatedError(.generic(.networkError(URLError(.notConnectedToInternet))), forAPI: .loadProducts)
        try await session.setSimulatedError(.generic(.networkError(URLError(.notConnectedToInternet))), forAPI: .appStoreSync)
        let store = makeStore()
        await store.check()
        await store.loadPrice()
        XCTAssertTrue(store.purchased)
        XCTAssertEqual(store.priceProblem, .storeUnreachable)
        await store.restore()
        XCTAssertTrue(store.purchased, "an unreachable store never removes Pro")
        XCTAssertEqual(store.message, .storeUnreachable)
    }

    func testOfflineWithoutProHasNoPriceAndNoPro() async throws {
        try await session.setSimulatedError(.generic(.networkError(URLError(.notConnectedToInternet))), forAPI: .loadProducts)
        let store = makeStore()
        await store.check()
        await store.loadPrice()
        XCTAssertFalse(store.unlocked)
        XCTAssertNil(store.price)
        XCTAssertEqual(store.priceProblem, .storeUnreachable)
        XCTAssertFalse(store.canBuy)

        // Back online, "Try again" loads the price.
        try await session.setSimulatedError(nil, forAPI: .loadProducts)
        await store.loadPrice()
        XCTAssertNotNil(store.price)
        XCTAssertNil(store.priceProblem)
    }

    /// The restore button reached the App Store and it knows no purchase: a stale cache goes (Android's rule).
    func testRestoreWithoutAPurchaseRemovesAStaleCache() async {
        ProCache(url: cacheURL).save(true)
        let store = makeStore()
        await store.check()
        XCTAssertTrue(store.purchased, "nothing known yet: the cache stays")
        await store.restore()
        XCTAssertFalse(store.purchased)
        XCTAssertEqual(store.message, .notFound)
    }

    // ── The race between the check at start and the updates ──

    func testACheckThatStartedBeforeAGrantCannotTakeItBack() async throws {
        let store = makeStore()
        let before = store.grants
        _ = try? await session.buyProduct(identifier: ProStore.productID)
        // The grant arrives (as from Transaction.updates) while an older check is still running.
        let latest = await Transaction.latest(for: ProStore.productID)
        let transaction = try XCTUnwrap(latest)
        await store.handle(transaction)
        XCTAssertTrue(store.purchased)
        store.apply(false, checkStartedAt: before)
        XCTAssertTrue(store.purchased, "the older check's empty answer is ignored")
        store.apply(false, checkStartedAt: store.grants)
        XCTAssertFalse(store.purchased, "a check after the grant may remove it")
    }

    func testDebugTestProNeverCountsAsPurchased() {
        let store = ProStore(cache: ProCache(url: cacheURL), flag: nil, testUnlocked: true)
        XCTAssertTrue(store.unlocked)
        XCTAssertFalse(store.purchased)
    }
}
