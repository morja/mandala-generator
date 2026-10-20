import Foundation

struct Quote: Equatable {
    let text: String
    let author: String
}

// MARK: - Quote Database

// Quotes offered by the text layer. Only public-domain quotes are shipped, because the app is sold
// and quotes by living authors or in modern translations are not clearly fine to include. Users
// can still type any text and author of their own.

enum QuoteDatabase {
    // Public domain only: authors and translators who died more than 70 years ago, quoted word for
    // word from the Project Gutenberg editions named above each group (checked 20 October 2026).
    // Same set as the Android port.
    static let quotes: [Quote] = [
        // Stray Birds, 1916 (Tagore's own English)
        Quote(text: "If you shed tears when you miss the sun, you also miss the stars.", author: "Rabindranath Tagore"),
        Quote(text: "The stars are not afraid to appear like fireflies.", author: "Rabindranath Tagore"),
        Quote(text: "I cannot choose the best. The best chooses me.", author: "Rabindranath Tagore"),
        Quote(text: "That I exist is a perpetual surprise which is life.", author: "Rabindranath Tagore"),
        Quote(text: "Let life be beautiful like summer flowers and death like autumn leaves.", author: "Rabindranath Tagore"),
        Quote(text: "He who wants to do good knocks at the gate; he who loves finds the gate open.", author: "Rabindranath Tagore"),
        Quote(text: "The world has kissed my soul with its pain, asking for its return in songs.", author: "Rabindranath Tagore"),
        Quote(text: "Clouds come floating into my life from other days no longer to shed rain or usher storm but to give colour to my sunset sky.", author: "Rabindranath Tagore"),
        // Gitanjali, 1912
        Quote(text: "Time is endless in thy hands, my lord. There is none to count thy minutes.", author: "Rabindranath Tagore"),
        // Songs of Kabir, translated by Rabindranath Tagore, 1915
        Quote(text: "The river and its waves are one surf: where is the difference between the river and its waves?", author: "Kabir"),
        Quote(text: "I laugh when I hear that the fish in the water is thirsty.", author: "Kabir"),
        Quote(text: "The flute of the Infinite is played without ceasing, and its sound is love.", author: "Kabir"),
        Quote(text: "Between the poles of the conscious and the unconscious, there has the mind made a swing.", author: "Kabir"),
        // The Prophet, 1923
        Quote(text: "Your joy is your sorrow unmasked.", author: "Kahlil Gibran"),
        Quote(text: "Work is love made visible.", author: "Kahlil Gibran"),
        Quote(text: "Beauty is eternity gazing at itself in a mirror.", author: "Kahlil Gibran"),
        Quote(text: "Say not, “I have found the truth,” but rather, “I have found a truth.”", author: "Kahlil Gibran"),
        Quote(text: "The soul unfolds itself, like a lotus of countless petals.", author: "Kahlil Gibran"),
        Quote(text: "Your pain is the breaking of the shell that encloses your understanding.", author: "Kahlil Gibran"),
        Quote(text: "Let there be spaces in your togetherness, and let the winds of the heavens dance between you.", author: "Kahlil Gibran"),
        // Tao Te Ching, translated by James Legge, 1891 (his bracketed insertions left out)
        Quote(text: "He who knows other men is discerning; he who knows himself is intelligent.", author: "Lao Tzu"),
        Quote(text: "The highest excellence is like water.", author: "Lao Tzu"),
        Quote(text: "The journey of a thousand li commenced with a single step.", author: "Lao Tzu"),
        Quote(text: "The softest thing in the world dashes against and overcomes the hardest.", author: "Lao Tzu"),
        // Dhammapada, translated by F. Max Müller, 1881
        Quote(text: "All that we are is the result of what we have thought.", author: "Dhammapada"),
        Quote(text: "Hatred does not cease by hatred at any time: hatred ceases by love, this is an old rule.", author: "Dhammapada"),
        Quote(text: "As a solid rock is not shaken by the wind, wise people falter not amidst blame and praise.", author: "Dhammapada"),
        Quote(text: "Wise people, after they have listened to the laws, become serene, like a deep, smooth, and still lake.", author: "Dhammapada"),
        // Walden, 1854
        Quote(text: "Heaven is under our feet as well as over our heads.", author: "Henry David Thoreau"),
        Quote(text: "Time is but the stream I go a-fishing in.", author: "Henry David Thoreau"),
        Quote(text: "Simplify, simplify.", author: "Henry David Thoreau"),
        Quote(text: "I went to the woods because I wished to live deliberately, to front only the essential facts of life.", author: "Henry David Thoreau"),
        // Leaves of Grass
        Quote(text: "I am large, I contain multitudes.", author: "Walt Whitman"),
        Quote(text: "I exist as I am, that is enough.", author: "Walt Whitman"),
        // Self-Reliance, 1841
        Quote(text: "Trust thyself: every heart vibrates to that iron string.", author: "Ralph Waldo Emerson"),
        Quote(text: "Nothing can bring you peace but yourself.", author: "Ralph Waldo Emerson"),
        Quote(text: "Nothing is at last sacred but the integrity of your own mind.", author: "Ralph Waldo Emerson"),
        // The Marriage of Heaven and Hell, 1790
        Quote(text: "If the doors of perception were cleansed everything would appear to man as it is, infinite.", author: "William Blake"),
        Quote(text: "Energy is Eternal Delight.", author: "William Blake"),
        Quote(text: "Exuberance is Beauty.", author: "William Blake"),
        // Poems, 1891
        Quote(text: "Hope is the thing with feathers that perches in the soul.", author: "Emily Dickinson"),
    ]

    static func randomQuote() -> Quote {
        quotes.randomElement() ?? quotes[0]
    }

    // Fonts well-suited for spiritual/contemplative text overlays
    static let availableFonts: [(name: String, display: String)] = [
        ("Zapfino",            "Zapfino"),
        ("Palatino-Roman",     "Palatino"),
        ("Didot",              "Didot"),
        ("Baskerville",        "Baskerville"),
        ("Georgia",            "Georgia"),
        ("Optima-Regular",     "Optima"),
        ("GillSans",           "Gill Sans"),
        ("Futura-Medium",      "Futura"),
        ("HoeflerText-Regular","Hoefler Text"),
        ("Copperplate",        "Copperplate"),
        ("AmericanTypewriter", "Typewriter"),
        ("Papyrus",            "Papyrus"),
        ("TimesNewRomanPSMT",  "Times New Roman"),
        ("HelveticaNeue",      "Helvetica Neue"),
    ]
}
