import XCTest

/// The tour's steps and rules, as Android's `TourTest` has them (WP-12).
final class TourTests: XCTestCase {
    /// A gate over a flag in memory, as the defaults would hold it.
    private final class Flag {
        var seen: Bool
        init(seen: Bool = false) { self.seen = seen }
        var gate: TourGate { TourGate(isSeen: { self.seen }, markSeen: { self.seen = true }) }
    }

    func testANewInstallGetsTheTourOnce() {
        let flag = Flag()
        XCTAssertTrue(flag.gate.startsByItself(hadSavedState: false))
        XCTAssertTrue(flag.seen)
        // The next start, even before anything was saved.
        XCTAssertFalse(flag.gate.startsByItself(hadSavedState: false))
    }

    func testAnExistingUserNeverGetsItByItself() {
        let flag = Flag()
        XCTAssertFalse(flag.gate.startsByItself(hadSavedState: true))
        // Marked as seen, so it does not come later when the saved state is gone.
        XCTAssertTrue(flag.seen)
        XCTAssertFalse(flag.gate.startsByItself(hadSavedState: false))
    }

    func testASeenTourStaysAway() {
        XCTAssertFalse(Flag(seen: true).gate.startsByItself(hadSavedState: false))
    }

    func testEightStepsInTheirOrder() {
        XCTAssertEqual(TourStep.allCases, [.play, .favourites, .save, .layers, .styles, .colours, .effects, .die])
        XCTAssertEqual(TourState.count, 8)
    }

    func testNextWalksThroughAndEndsAfterTheLast() {
        var state: TourState? = .start(automatic: true)
        var seen: [Int] = []
        while let s = state {
            seen.append(s.number)
            XCTAssertTrue(s.automatic)
            state = s.next()
        }
        XCTAssertEqual(seen, Array(1...8))
    }

    func testBackStopsAtTheFirstStep() {
        let first = TourState.start(automatic: false)
        XCTAssertTrue(first.isFirst)
        XCTAssertEqual(first, first.back())
        let third = first.next()!.next()!
        XCTAssertEqual(third.back().step, .favourites)
        XCTAssertTrue(TourState(step: .die).isLast)
    }

    func testStepsNeedTheirTabsAndRows() {
        // The first three point at the top bar and keep the tab that is open.
        for step in [TourStep.play, .favourites, .save] { XCTAssertNil(step.tab) }
        XCTAssertEqual(TourStep.layers.marks.map(\.target), [.layersTab, .addLayer])
        XCTAssertEqual(TourStep.layers.scrollTo, .layerRow)
        XCTAssertEqual(TourStep.layers.open, [.layerRow])
        XCTAssertEqual(TourStep.styles.tab, .layers)
        XCTAssertEqual(TourStep.styles.scrollTo, .styleRow)
        XCTAssertEqual(TourStep.colours.scrollTo, .paletteRow)
        XCTAssertEqual(TourStep.effects.tab, .scene)
        // The die step marks both dice in view; only the lower one gets a line.
        XCTAssertEqual(TourStep.die.marks, [TourMark(target: .topDie, line: false), TourMark(target: .layerDie)])
        // iPhone: no Share row in the menu yet, so Save and share rings the save arrow only.
        XCTAssertEqual(TourStep.save.marks.map(\.target), [.save])
    }

    func testEveryStepHasItsTexts() throws {
        // The texts come from the catalogue by `tour_<stem>_title` and `_body`; the stems are unique.
        XCTAssertEqual(Set(TourStep.allCases.map(\.stem)).count, 8)
    }

    func testTheCardDocksAtTheBottom() {
        let top = TourLayout.cardTop(rings: [.init(top: 44, bottom: 68)], cardHeight: 150, top: 36, bottom: 756, gap: 8)
        XCTAssertEqual(top, 606)
    }

    func testTheCardMovesUpPastARingItWouldCover() {
        let top = TourLayout.cardTop(rings: [.init(top: 600, bottom: 650)], cardHeight: 150, top: 36, bottom: 756, gap: 8)
        XCTAssertEqual(top, 442)
    }

    func testTheCardNeverGoesAboveTheTop() {
        let top = TourLayout.cardTop(rings: [.init(top: 100, bottom: 700)], cardHeight: 150, top: 36, bottom: 756, gap: 8)
        XCTAssertEqual(top, 36)
    }
}

/// The rating rule of the kit (ADR-0006), as its `ReviewPolicyTest`.
final class ReviewPolicyTests: XCTestCase {
    private func opened(_ days: [Int], from r: ReviewRecord = ReviewRecord()) -> ReviewRecord {
        days.reduce(r) { ReviewPolicy.opened($0, today: $1) }
    }

    func testNothingBeforeTheFirstOpening() {
        XCTAssertFalse(ReviewPolicy.due(ReviewRecord(), today: 100, build: 1))
    }

    func testTenDaysOverAtLeastSevenAsksTheFirstTime() {
        let r = opened(Array(0..<10))
        XCTAssertEqual(r.openingDays, 10)
        XCTAssertFalse(ReviewPolicy.due(opened(Array(0..<9)), today: 20, build: 1))
        XCTAssertTrue(ReviewPolicy.due(r, today: 9, build: 1))
    }

    func testADaySeenAgainCountsOnce() {
        XCTAssertEqual(opened([5, 5, 5]).openingDays, 1)
    }

    func testTenDaysWithinFewerThanSevenDaysWait() {
        // Not possible by the calendar, but the floor stays if the count changes: 10 days set, 6 waited.
        var r = opened(Array(0..<10))
        r.firstDay = 4
        XCTAssertFalse(ReviewPolicy.due(r, today: 9, build: 1))
    }

    func testAtMostOncePerBuild() {
        let r = ReviewPolicy.asked(opened(Array(0..<10)), build: 3, today: 10)
        XCTAssertEqual(r.prompts, 1)
        XCTAssertEqual(r.days, [])
        // Even long after, the same build never asks again.
        let later = opened(Array(100..<130), from: r)
        XCTAssertFalse(ReviewPolicy.due(later, today: 200, build: 3))
        XCTAssertTrue(ReviewPolicy.due(later, today: 200, build: 4))
    }

    func testSecondPromptNeedsNinetyDaysAndThirtyOpeningDays() {
        let r = ReviewPolicy.asked(opened(Array(0..<10)), build: 1, today: 10)
        let thirty = opened(Array(11..<41), from: r)
        XCTAssertEqual(thirty.openingDays, 30)
        XCTAssertFalse(ReviewPolicy.due(thirty, today: 99, build: 2))
        XCTAssertTrue(ReviewPolicy.due(thirty, today: 100, build: 2))
        XCTAssertFalse(ReviewPolicy.due(opened(Array(11..<40), from: r), today: 200, build: 2))
    }

    func testLaterPromptsWaitAYear() {
        var r = ReviewPolicy.asked(opened(Array(0..<10)), build: 1, today: 10)
        r = ReviewPolicy.asked(opened(Array(11..<41), from: r), build: 2, today: 100)
        XCTAssertEqual(r.prompts, 2)
        let thirty = opened(Array(101..<131), from: r)
        XCTAssertFalse(ReviewPolicy.due(thirty, today: 464, build: 3))
        XCTAssertTrue(ReviewPolicy.due(thirty, today: 465, build: 3))
    }

    func testTheCountStopsGrowingAtTheThreshold() {
        XCTAssertEqual(opened(Array(0..<50)).openingDays, 10)
    }
}
