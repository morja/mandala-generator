import Foundation

// The one rating rule of every app (tearoom-android-kit ADR-0006, user decisions of 11 and 12 November 2026), as the
// kit's `ReviewPolicy.kt` has it. Pure: `ReviewPrompt` reads the clock and the defaults and calls StoreKit.

/// What is remembered about the openings. Days are local calendar days as numbers (days since 1970 by the calendar
/// on the phone), so time zones and clock changes count by the calendar, not by 24-hour spans.
struct ReviewRecord: Equatable, Codable {
    /// The day of the first counted opening.
    var firstDay: Int?
    /// The different days the app came to the foreground in the current round; a day seen again does not count twice.
    var days: Set<Int> = []
    /// The day of the last real prompt; starts the longer wait.
    var askedDay: Int?
    /// How many real prompts went to the system; decides how long the next one waits.
    var prompts = 0
    /// The build number the prompt was last asked for; at most once per build.
    var askedBuild: Int?

    var openingDays: Int { days.count }
}

enum ReviewPolicy {
    static let minDays = 7
    static let minOpeningDays = 10
    static let secondDays = 90
    static let laterDays = 365
    static let againOpeningDays = 30

    /// The days to wait in the current round: since the first opening, or since the last prompt.
    static func daysNeeded(_ r: ReviewRecord) -> Int {
        switch r.prompts {
        case 0: minDays
        case 1: secondDays
        default: laterDays
        }
    }

    /// The opening days to count in the current round.
    static func openingDaysNeeded(_ r: ReviewRecord) -> Int { r.prompts == 0 ? minOpeningDays : againOpeningDays }

    /// The days since the start of the current round on `today`.
    static func daysWaited(_ r: ReviewRecord, today: Int) -> Int {
        (r.askedDay ?? r.firstDay).map { today - $0 } ?? 0
    }

    /// The app came to the foreground on `today`; counts each day once.
    static func opened(_ r: ReviewRecord, today: Int) -> ReviewRecord {
        var next = r
        if r.firstDay == nil {
            next.firstDay = today
            next.days = [today]
        } else if !r.days.contains(today) && r.openingDays < openingDaysNeeded(r) {
            // Enough days counted: the set stops growing, the rule only asks for the threshold.
            next.days.insert(today)
        }
        return next
    }

    /// The rule is met for `build` on `today`.
    static func due(_ r: ReviewRecord, today: Int, build: Int) -> Bool {
        guard r.firstDay != nil else { return false }
        return daysWaited(r, today: today) >= daysNeeded(r) && r.openingDays >= openingDaysNeeded(r) && r.askedBuild != build
    }

    /// The prompt went to the system for `build` on `today`: starts the longer wait and a new count.
    static func asked(_ r: ReviewRecord, build: Int, today: Int) -> ReviewRecord {
        var next = r
        next.askedBuild = build
        next.askedDay = today
        next.prompts += 1
        next.days = []
        return next
    }
}
