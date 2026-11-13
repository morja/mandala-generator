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
    /// The stores a test made; each stops listening at the end, so no listener of an earlier test takes an update.
    private var stores: [ProStore] = []

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
        for store in stores { store.stop() }
        stores = []
        try await session.setSimulatedError(nil, forAPI: .loadProducts)
        try await session.setSimulatedError(nil, forAPI: .purchase)
        try await session.setSimulatedError(nil, forAPI: .appStoreSync)
        try await session.setSimulatedError(nil, forAPI: .verification)
        // What the store left unfinished (the unverified transaction of the bad signature) is finished, so no test
        // starts with a transaction waiting; a fresh install has none.
        for await result in Transaction.unfinished { await result.unsafePayloadValue.finish() }
        session.clearTransactions()
        try? FileManager.default.removeItem(at: cacheURL)
    }

    private func makeStore(flag: UserDefaults? = nil) -> ProStore {
        let store = ProStore(cache: ProCache(url: cacheURL), flag: flag, testUnlocked: false)
        stores.append(store)
        return store
    }

    /// Waits until `condition` holds. StoreKit's updates arrive asynchronously; the store is observable, so the wait
    /// wakes on each change of what `condition` reads instead of polling at fixed intervals. `timeout` only bounds a
    /// failure, it is never waited out when the update comes.
    private func eventually(_ timeout: TimeInterval = 30, _ condition: @escaping @MainActor () -> Bool) async -> Bool {
        let deadline = ContinuousClock.now + .seconds(timeout)
        while !condition() {
            guard ContinuousClock.now < deadline else { return false }
            await nextChange(of: condition, before: deadline)
        }
        return true
    }

    /// Returns once anything `condition` reads has changed, or at the deadline.
    private func nextChange(of condition: @escaping @MainActor () -> Bool, before deadline: ContinuousClock.Instant) async {
        let once = ResumeOnce()
        let sleeper = Task { @MainActor in
            try? await Task.sleep(until: deadline)
            once.resume()
        }
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            once.wait(with: continuation)
            withObservationTracking { _ = condition() } onChange: { Task { @MainActor in once.resume() } }
        }
        sleeper.cancel()
    }

    /// One round trip through StoreKit's own connection, so StoreKitTest has taken in what the store did (a purchase
    /// and its finish) before the test acts on the session's separate connection. Without it a refund right after a
    /// purchase was sometimes lost: no revoked transaction, no update, the test waited out its time (IOS-13).
    private func storeKitCaughtUp() async {
        _ = await Transaction.latest(for: ProStore.productID)
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
        // The start's own check first, so the purchase can only come through the update (a check would unlock silently).
        await store.startup?.value
        // StoreKitTest records the purchase but may answer the call itself with an error; the update is what counts.
        _ = try? await session.buyProduct(identifier: ProStore.productID)
        // The thanks comes after the store has finished the transaction, a moment after Pro is on.
        let unlocked = await eventually { store.purchased && store.message == .unlocked }
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
        await store.startup?.value
        await store.loadPrice()
        await store.buy()
        XCTAssertTrue(store.pending)
        XCTAssertFalse(store.purchased)
        XCTAssertEqual(store.message, .pending)
        XCTAssertFalse(store.canBuy, "a second purchase is blocked while one is pending")

        await storeKitCaughtUp()
        let waiting = try XCTUnwrap(proTransaction())
        XCTAssertTrue(waiting.pendingAskToBuyConfirmation)
        try session.approveAskToBuyTransaction(identifier: waiting.identifier)
        let unlocked = await eventually { store.purchased && store.message == .unlocked }
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
        await store.startup?.value
        await store.loadPrice()
        await store.buy()
        XCTAssertTrue(store.purchased)
        await storeKitCaughtUp()
        try session.refundTransaction(identifier: try XCTUnwrap(proTransaction()).identifier)
        // StoreKitTest applies the refund a moment later and then sends the update; the store hears it while running.
        let removed = await eventually { !store.purchased }
        XCTAssertTrue(removed)
        XCTAssertFalse(ProCache(url: cacheURL).load())
    }

    /// Refunded while the app was closed: the next start reads the revoked transaction and removes the cached Pro.
    func testARefundRemovesTheCachedProAtTheNextStart() async throws {
        let first = makeStore()
        await first.loadPrice()
        await first.buy()
        await storeKitCaughtUp()
        try session.refundTransaction(identifier: try XCTUnwrap(proTransaction()).identifier)

        let next = makeStore()
        XCTAssertTrue(next.purchased, "cached")
        // The next start: StoreKit reports the refund through the updates (or already in the entitlement check), and the
        // cached Pro goes then. Until StoreKit knows, the cache stays.
        next.start()
        let removed = await eventually { !next.purchased }
        XCTAssertTrue(removed)
        XCTAssertFalse(ProCache(url: cacheURL).load())
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
        // The purchase's update is what the app would hear; waiting for it (instead of asking `latest` right away,
        // which raced StoreKitTest and sometimes found nothing yet) gives the transaction once it exists.
        let arrived = Task { @MainActor () -> VerificationResult<Transaction>? in
            for await result in Transaction.updates where result.unsafePayloadValue.productID == ProStore.productID { return result }
            return nil
        }
        _ = try? await session.buyProduct(identifier: ProStore.productID)
        // The grant arrives (as from Transaction.updates) while an older check is still running.
        let update = await arrived.value
        let transaction = try XCTUnwrap(update)
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

/// A continuation resumed by whichever comes first, the change or the deadline; a resume before the wait began counts.
@MainActor
private final class ResumeOnce {
    private var continuation: CheckedContinuation<Void, Never>?
    private var resumed = false

    func wait(with continuation: CheckedContinuation<Void, Never>) {
        if resumed { continuation.resume() } else { self.continuation = continuation }
    }

    func resume() {
        resumed = true
        continuation?.resume()
        continuation = nil
    }
}
