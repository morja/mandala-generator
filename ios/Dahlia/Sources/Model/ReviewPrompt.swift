import Foundation
import StoreKit
import UIKit

/// The rating prompt (tearoom-android-kit ADR-0006 rule, `ReviewPolicy`): the system's review request once Dahlia was
/// opened on 10 different days over at least 7, at most once per build, while the editor is calm for about two
/// seconds; the menu's Dahlia page has the permanent row. No own pre-question, no routing of unhappy users, no
/// reward. The system decides whether it shows the dialog (at most three times a year) and never says whether it did.
@MainActor
enum ReviewPrompt {
    /// Dahlia's App Store id. It leads nowhere before the app is released.
    static let appStoreID = "6833903967"
    /// How long the editor stays calm before the request.
    static let calmFor = Duration.seconds(2)

    private static let key = "reviewRecord"

    static var reviewPageURL: URL { URL(string: "https://apps.apple.com/app/id\(appStoreID)?action=write-review")! }

    /// Counts today as an opening day; the app came to the foreground.
    static func opened() {
        save(ReviewPolicy.opened(load(), today: today()))
    }

    static func isDue() -> Bool { ReviewPolicy.due(load(), today: today(), build: build()) }

    /// Waits while the editor stays calm and asks once the rule is met. Run it from a `.task(id: calm)` that only
    /// starts when calm: leaving the calm state cancels the wait.
    static func askIfDue() async {
        guard isDue() else { return }
        try? await Task.sleep(for: calmFor)
        guard !Task.isCancelled, isDue(),
              let scene = UIApplication.shared.connectedScenes.first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene
        else { return }
        // Marked right away: the system answers without saying whether it showed the dialog.
        save(ReviewPolicy.asked(load(), build: build(), today: today()))
        AppStore.requestReview(in: scene)
    }

    /// The permanent "Rate the app" row: the App Store review page.
    static func openReviewPage() {
        UIApplication.shared.open(reviewPageURL)
    }

    // ── Clock and store ──

    /// The local calendar day as a number.
    static func today(_ now: Date = Date(), calendar: Calendar = .current) -> Int {
        let start = calendar.startOfDay(for: now)
        // Days since 1970 by the calendar on the phone, not by 24-hour spans.
        let epoch = calendar.startOfDay(for: Date(timeIntervalSince1970: 0))
        return calendar.dateComponents([.day], from: epoch, to: start).day ?? 0
    }

    private static func build() -> Int { Int(Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "") ?? 0 }

    private static func load() -> ReviewRecord {
        guard let data = UserDefaults.standard.data(forKey: key), let r = try? JSONDecoder().decode(ReviewRecord.self, from: data) else { return ReviewRecord() }
        return r
    }

    private static func save(_ r: ReviewRecord) {
        if let data = try? JSONEncoder().encode(r) { UserDefaults.standard.set(data, forKey: key) }
    }

    #if DEBUG
    /// Debug builds: `-reviewForce YES` makes the next calm moment ask regardless of the rule (the system dialog
    /// shows only in builds from TestFlight or the App Store, not in the simulator), `-reviewReset YES` forgets the record.
    static func applyLaunchArguments() {
        let d = UserDefaults.standard
        if d.bool(forKey: "reviewReset") { UserDefaults.standard.removeObject(forKey: key) }
        if d.bool(forKey: "reviewForce") {
            save(ReviewRecord(firstDay: today() - ReviewPolicy.minDays, days: Set((0..<ReviewPolicy.minOpeningDays).map { today() - $0 })))
        }
    }
    #endif
}
