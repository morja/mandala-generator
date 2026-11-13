import Foundation
import Observation
import StoreKit

/// The one Pro purchase through StoreKit 2 (PLAN-4 section 3.4, WP-9), behaving as tearoom-android-kit's `AppUnlock`
/// does on Android (ADR-0022): only a verified transaction unlocks; a refund or a family share taken back removes Pro
/// once Apple reports it; a store that cannot be reached never does, the cached purchase stays. Started once at app
/// launch (`DahliaApp.init`), so `Transaction.updates` is heard before anything can be bought.
@MainActor
@Observable
final class ProStore {
    /// The non-consumable in App Store Connect (STORE-2); Play's product is `pro`.
    static let productID = "one.tearoom.mandala.pro"

    static let shared = ProStore(cache: .standard, flag: AppGroup.defaults, testUnlocked: ProStore.debugTestPro)

    /// A verified purchase is known: from StoreKit or, until StoreKit says otherwise, from the cache.
    private(set) var purchased: Bool
    /// Debug builds count as Pro, as Android's test builds do (`pro_for_testing`); `-proOwned NO` shows the free app.
    /// Always false in release builds.
    let testUnlocked: Bool
    /// The localised price of the product, kept while it loads again.
    private(set) var price: String?
    private(set) var loadingPrice = false
    private(set) var priceProblem: ProPriceProblem?
    /// A purchase waits for approval (Ask to Buy) or a payment; buying again is blocked meanwhile.
    private(set) var pending = false
    /// The App Store's purchase sheet is open or a restore runs.
    private(set) var busy = false
    /// Purchase and restore feedback for the Pro sheet; the editor shows it as a message while the sheet is closed.
    var message: ProMessage?

    /// Unlocked for the app: purchased, or a debug build's test Pro.
    var unlocked: Bool { purchased || testUnlocked }

    var canBuy: Bool { ProPolicy.canBuy(hasPrice: price != nil, busy: busy, pending: pending, purchased: purchased) }

    /// Called with each message as it is posted (the editor's routing, Android's `Pro.messages`).
    @ObservationIgnored var onMessage: (@MainActor (ProMessage) -> Void)?
    @ObservationIgnored private var product: Product?
    @ObservationIgnored private var listener: Task<Void, Never>?
    /// Counts grants from transactions, so a check that started before one cannot take it back (`ProPolicy.mayRemove`).
    @ObservationIgnored private(set) var grants = 0
    @ObservationIgnored private let cache: ProCache
    @ObservationIgnored private let flag: UserDefaults?

    init(cache: ProCache, flag: UserDefaults?, testUnlocked: Bool) {
        self.cache = cache
        self.flag = flag
        self.testUnlocked = testUnlocked
        purchased = cache.load()
    }

    /// Debug builds: Pro unless `-proOwned NO` (the seam of IOS-7 finding 9). Release builds: never.
    static var debugTestPro: Bool {
        #if DEBUG
        let d = UserDefaults.standard
        return d.object(forKey: "proOwned") == nil ? true : d.bool(forKey: "proOwned")
        #else
        return false
        #endif
    }

    // ── Start and checks ──

    /// Listens to `Transaction.updates` first (an approved Ask to Buy, a purchase on another device, a refund, also those
    /// that happened while the app was closed), then finishes what is left unfinished, asks StoreKit for the
    /// entitlement and loads the price.
    func start() {
        guard listener == nil else { return }
        listener = Task { [weak self] in
            for await result in Transaction.updates {
                await self?.handle(result)
            }
        }
        writeFlag()
        Task {
            for await result in Transaction.unfinished { await handle(result) }
            await check()
            await loadPrice()
        }
    }

    /// Each return to the app, as Android asks Play on every resume: the entitlement again, and the price if it is missing.
    func resumed() async {
        await check()
        await loadPriceIfMissing()
    }

    /// One transaction from `Transaction.updates`, `Transaction.unfinished` or a purchase.
    func handle(_ result: VerificationResult<Transaction>) async {
        switch ProPolicy.effect(of: Self.describe(result), productID: Self.productID) {
        case .grant:
            let was = purchased
            grant()
            if case .verified(let transaction) = result { await transaction.finish() }
            // An approved Ask to Buy or a purchase on another device: say so once.
            if !was { post(.unlocked) }
        case .recheck:
            // A refund or a family share taken back: gone unless another purchase still counts.
            if case .verified(let transaction) = result { await transaction.finish() }
            await check(reported: Self.describe(result))
        case .ignore:
            break
        }
    }

    /// Reads StoreKit's entitlement and the latest transaction (both work offline from StoreKit's own signed cache),
    /// with the revoked transaction an update `reported`; `confirmed` after a sync that went through. A removal is
    /// applied only if no grant arrived meanwhile.
    func check(confirmed: Bool = false, reported: ProTransaction? = nil) async {
        let startedAt = grants
        var entitlements: [ProTransaction] = []
        for await result in Transaction.currentEntitlements(for: Self.productID) {
            entitlements.append(Self.describe(result))
        }
        let latest = await Transaction.latest(for: Self.productID).map(Self.describe)
        let next = ProPolicy.purchasedAfterCheck(cached: purchased, entitlements: entitlements, reported: [latest, reported].compactMap { $0 },
                                                 confirmed: confirmed, productID: Self.productID)
        apply(next, checkStartedAt: startedAt)
    }

    /// The result of a check that started when `grants` was `checkStartedAt`.
    func apply(_ next: Bool, checkStartedAt: Int) {
        if next {
            pending = false
            if !purchased { setPurchased(true) }
        } else if purchased, ProPolicy.mayRemove(grantsWhenCheckStarted: checkStartedAt, grantsNow: grants) {
            setPurchased(false)
        }
    }

    // ── Price ──

    func loadPrice() async {
        guard !loadingPrice else { return }
        guard AppStore.canMakePayments else {
            priceProblem = .purchasesNotAllowed
            return
        }
        loadingPrice = true
        defer { loadingPrice = false }
        do {
            if let found = try await Product.products(for: [Self.productID]).first {
                product = found
                price = found.displayPrice
                priceProblem = nil
            } else if price == nil {
                priceProblem = .productUnavailable
            }
        } catch {
            // A known price stays (the kit keeps it while it reloads).
            if price == nil { priceProblem = .storeUnreachable }
        }
    }

    func loadPriceIfMissing() async {
        if product == nil { await loadPrice() }
    }

    // ── Buy and restore ──

    func buy() async {
        guard let product, canBuy else { return }
        busy = true
        defer { busy = false }
        do {
            switch try await product.purchase() {
            case .success(let result):
                if ProPolicy.effect(of: Self.describe(result), productID: Self.productID) == .grant {
                    grant()
                    if case .verified(let transaction) = result { await transaction.finish() }
                    post(.unlocked)
                } else {
                    // Not signed by Apple (or already taken back): never unlocks.
                    post(.purchaseIncomplete)
                }
            case .pending:
                pending = true
                post(.pending)
            case .userCancelled:
                // A cancel is the user's choice; no message, as on Android.
                break
            @unknown default:
                post(.purchaseIncomplete)
            }
        } catch let error as StoreKitError {
            switch error {
            case .userCancelled: break
            case .networkError, .systemError, .notAvailableInStorefront: post(.purchaseNotStarted)
            default: post(.purchaseIncomplete)
            }
        } catch let error as Product.PurchaseError {
            switch error {
            case .purchaseNotAllowed: priceProblem = .purchasesNotAllowed
            case .productUnavailable: post(.purchaseNotStarted)
            default: post(.purchaseIncomplete)
            }
        } catch {
            post(.purchaseIncomplete)
        }
    }

    /// "Restore purchase": asks the App Store (it may ask to sign in), then reads the entitlement as confirmed.
    func restore() async {
        guard !busy else { return }
        busy = true
        defer { busy = false }
        do {
            try await AppStore.sync()
        } catch {
            if case StoreKitError.userCancelled = error { return }
            // Nothing was checked; the cached purchase stays.
            post(.storeUnreachable)
            return
        }
        await check(confirmed: true)
        post(purchased ? .activeHere : .notFound)
    }

    #if DEBUG
    /// Debug only (`ShotSetup`): a price for screenshots of the sheet; buying still needs the product from StoreKit.
    func showPriceForScreenshots(_ shown: String) {
        price = shown
        priceProblem = nil
    }
    #endif

    // ── Parts ──

    private func grant() {
        grants += 1
        pending = false
        if !purchased { setPurchased(true) }
    }

    #if DEBUG
    /// `-shotBuy YES` with `-shotSheet pro`: what a purchase that went through does to the screen (Pro for this run, then
    /// the thanks), without StoreKit and without writing the cache, to check when the message shows (QA-10 item 5).
    func simulatePurchaseForScreenshots() {
        grants += 1
        purchased = true
        post(.unlocked)
    }
    #endif

    private func setPurchased(_ value: Bool) {
        purchased = value
        cache.save(value)
        writeFlag()
    }

    /// The flag widgets will read (WP-10): unlocked, as Android's widget reads `Pro.isUnlocked`.
    private func writeFlag() {
        flag?.set(unlocked, forKey: AppGroup.proUnlockedKey)
    }

    private func post(_ message: ProMessage) {
        self.message = message
        onMessage?(message)
    }

    static func describe(_ result: VerificationResult<Transaction>) -> ProTransaction {
        switch result {
        case .verified(let t):
            ProTransaction(productID: t.productID, verified: true, revoked: t.revocationDate != nil,
                           familyShared: t.ownershipType == .familyShared, originalID: t.originalID)
        case .unverified(let t, _):
            ProTransaction(productID: t.productID, verified: false, revoked: t.revocationDate != nil,
                           familyShared: t.ownershipType == .familyShared, originalID: t.originalID)
        }
    }
}

/// The cached purchase, so Pro is known offline and before StoreKit answers. A file in Application Support rather than
/// UserDefaults: launch arguments can set any UserDefaults key, and they must not unlock a release build.
struct ProCache: Sendable {
    let url: URL

    static let standard = ProCache(url: FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        .appendingPathComponent("pro-purchase.json"))

    func load() -> Bool {
        guard let data = try? Data(contentsOf: url), let stored = try? JSONDecoder().decode(Stored.self, from: data) else { return false }
        return stored.purchased
    }

    func save(_ purchased: Bool) {
        try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? JSONEncoder().encode(Stored(purchased: purchased)).write(to: url, options: .atomic)
    }

    private struct Stored: Codable { let purchased: Bool }
}
