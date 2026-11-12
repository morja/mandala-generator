import XCTest

/// The widget's day and choice rules (WP-10), shared by the app and the extension.
final class WidgetRulesTests: XCTestCase {
    private var berlin: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "Europe/Berlin")!
        return c
    }

    private func date(_ y: Int, _ m: Int, _ d: Int, _ h: Int, _ min: Int = 0) -> Date {
        berlin.date(from: DateComponents(year: y, month: m, day: d, hour: h, minute: min))!
    }

    func testTheDayStartsAtFour() {
        let before = WidgetDay.number(of: date(2026, 11, 13, 3, 59), calendar: berlin, length: nil)
        let at = WidgetDay.number(of: date(2026, 11, 13, 4, 0), calendar: berlin, length: nil)
        XCTAssertEqual(at, before + 1)
        // Numbered like Android's LocalDate.toEpochDay: 13 November 2026 is day 20770.
        XCTAssertEqual(at, 20770)
        XCTAssertEqual(WidgetDay.start(of: at, calendar: berlin, length: nil), date(2026, 11, 13, 4, 0))
    }

    func testDaylightSavingDaysStillStartAtFour() {
        // 29 March 2026 has 23 hours in Berlin, 25 October 2026 has 25.
        for (m, d) in [(3, 29), (3, 30), (10, 25), (10, 26)] {
            let n = WidgetDay.number(of: date(2026, m, d, 12), calendar: berlin, length: nil)
            XCTAssertEqual(WidgetDay.start(of: n, calendar: berlin, length: nil), date(2026, m, d, 4, 0))
        }
    }

    func testADebugDayIsShort() {
        let n = WidgetDay.number(of: Date(timeIntervalSince1970: 1_000), length: 120)
        XCTAssertEqual(n, 8)
        XCTAssertEqual(WidgetDay.start(of: n, length: 120), Date(timeIntervalSince1970: 960))
    }

    private let index = WidgetIndex(
        favourites: [
            .init(id: "a", name: "A", words: nil, thumb: "ta", square: "sa", plain: "sa", large: nil),
            .init(id: "b", name: "B", words: .init(text: "Own", author: ""), thumb: "tb", square: nil, plain: nil, large: "lb"),
        ],
        days: [
            .init(number: 1, start: Date(timeIntervalSince1970: 100), favourite: "a", quote: .init(text: "Q1", author: "X")),
            .init(number: 2, start: Date(timeIntervalSince1970: 200), favourite: "b", quote: .init(text: "Q2", author: "Y")),
        ],
        appLight: false, mark: true
    )

    func testTheDayStaysOnTheLastOneAfterTheIndexRunsOut() {
        XCTAssertEqual(WidgetRules.day(in: index, at: Date(timeIntervalSince1970: 150))?.number, 1)
        XCTAssertEqual(WidgetRules.day(in: index, at: Date(timeIntervalSince1970: 9_999))?.number, 2)
        XCTAssertEqual(WidgetRules.day(in: index, at: Date(timeIntervalSince1970: 0))?.number, 1)
    }

    func testAFixedFavouriteWinsWhileItExists() {
        let day = index.days[0]
        XCTAssertEqual(WidgetRules.favourite(in: index, fixed: "b", day: day)?.id, "b")
        // Deleted: back to the daily turn.
        XCTAssertEqual(WidgetRules.favourite(in: index, fixed: "gone", day: day)?.id, "a")
        XCTAssertEqual(WidgetRules.favourite(in: index, fixed: nil, day: day)?.id, "a")
    }

    func testLooks() {
        XCTAssertTrue(WidgetRules.isLight(.app, appLight: true))
        XCTAssertFalse(WidgetRules.isLight(.app, appLight: false))
        XCTAssertTrue(WidgetRules.isLight(.light, appLight: false))
        XCTAssertFalse(WidgetRules.isLight(.dark, appLight: true))
    }

    func testWordsAndPictures() {
        XCTAssertEqual(WidgetRules.words(index.favourites[0], day: index.days[0])?.text, "Q1")
        XCTAssertEqual(WidgetRules.words(index.favourites[1], day: index.days[0])?.text, "Own")
        XCTAssertEqual(WidgetRules.picture(index.favourites[0], shape: .large), "sa")
        XCTAssertEqual(WidgetRules.picture(index.favourites[1], shape: .small), "lb")
        XCTAssertEqual(WidgetRules.picture(index.favourites[1], shape: .wide), "lb")
        XCTAssertEqual(index.files, ["ta", "sa", "tb", "lb"])
    }
}
