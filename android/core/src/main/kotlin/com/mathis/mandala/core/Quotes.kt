package com.mathis.mandala.core

// Quotes offered by the text layer. The macOS app's collection is not shipped: most of its wordings
// are modern translations or by living authors (ADR-0007). Users add their own in the app.

@kotlinx.serialization.Serializable
data class Quote(val text: String, val author: String = "")

object Quotes {
    // Public domain only: authors and translators who died more than 70 years ago, quoted word for
    // word from the Project Gutenberg editions named above each group (checked 20 October 2026).
    val all: List<Quote> = listOf(
        // Stray Birds, 1916 (Tagore's own English)
        Quote("If you shed tears when you miss the sun, you also miss the stars.", "Rabindranath Tagore"),
        Quote("The stars are not afraid to appear like fireflies.", "Rabindranath Tagore"),
        Quote("I cannot choose the best. The best chooses me.", "Rabindranath Tagore"),
        Quote("That I exist is a perpetual surprise which is life.", "Rabindranath Tagore"),
        Quote("Let life be beautiful like summer flowers and death like autumn leaves.", "Rabindranath Tagore"),
        Quote("He who wants to do good knocks at the gate; he who loves finds the gate open.", "Rabindranath Tagore"),
        Quote("The world has kissed my soul with its pain, asking for its return in songs.", "Rabindranath Tagore"),
        Quote("Clouds come floating into my life from other days no longer to shed rain or usher storm but to give colour to my sunset sky.", "Rabindranath Tagore"),
        // Gitanjali, 1912
        Quote("Time is endless in thy hands, my lord. There is none to count thy minutes.", "Rabindranath Tagore"),
        // Songs of Kabir, translated by Rabindranath Tagore, 1915
        Quote("The river and its waves are one surf: where is the difference between the river and its waves?", "Kabir"),
        Quote("I laugh when I hear that the fish in the water is thirsty.", "Kabir"),
        Quote("The flute of the Infinite is played without ceasing, and its sound is love.", "Kabir"),
        Quote("Between the poles of the conscious and the unconscious, there has the mind made a swing.", "Kabir"),
        // The Prophet, 1923
        Quote("Your joy is your sorrow unmasked.", "Kahlil Gibran"),
        Quote("Work is love made visible.", "Kahlil Gibran"),
        Quote("Beauty is eternity gazing at itself in a mirror.", "Kahlil Gibran"),
        Quote("Say not, \u201cI have found the truth,\u201d but rather, \u201cI have found a truth.\u201d", "Kahlil Gibran"),
        Quote("The soul unfolds itself, like a lotus of countless petals.", "Kahlil Gibran"),
        Quote("Your pain is the breaking of the shell that encloses your understanding.", "Kahlil Gibran"),
        Quote("Let there be spaces in your togetherness, and let the winds of the heavens dance between you.", "Kahlil Gibran"),
        // Tao Te Ching, translated by James Legge, 1891 (his bracketed insertions left out)
        Quote("He who knows other men is discerning; he who knows himself is intelligent.", "Lao Tzu"),
        Quote("The highest excellence is like water.", "Lao Tzu"),
        Quote("The journey of a thousand li commenced with a single step.", "Lao Tzu"),
        Quote("The softest thing in the world dashes against and overcomes the hardest.", "Lao Tzu"),
        // Divani Shamsi Tabriz, translated by R. A. Nicholson, 1898, as quoted in The Persian Mystics: Jal\u00e1lu'd-d\u00edn R\u00fam\u00ed, 1907
        Quote("Every moment the voice of Love is coming from left and right.", "Rumi"),
        Quote("This is the House of Love, which has no bound or end.", "Rumi"),
        Quote("Embrace the Soul which is embraced by nothing.", "Rumi"),
        Quote("Do not think of the Water failing, for this Water is without end.", "Rumi"),
        // Dhammapada, translated by F. Max M\u00fcller, 1881
        Quote("All that we are is the result of what we have thought.", "Dhammapada"),
        Quote("Hatred does not cease by hatred at any time: hatred ceases by love, this is an old rule.", "Dhammapada"),
        Quote("As a solid rock is not shaken by the wind, wise people falter not amidst blame and praise.", "Dhammapada"),
        Quote("Wise people, after they have listened to the laws, become serene, like a deep, smooth, and still lake.", "Dhammapada"),
        // Walden, 1854
        Quote("Heaven is under our feet as well as over our heads.", "Henry David Thoreau"),
        Quote("Time is but the stream I go a-fishing in.", "Henry David Thoreau"),
        Quote("Simplify, simplify.", "Henry David Thoreau"),
        Quote("I went to the woods because I wished to live deliberately, to front only the essential facts of life.", "Henry David Thoreau"),
        // Leaves of Grass
        Quote("I am large, I contain multitudes.", "Walt Whitman"),
        Quote("I exist as I am, that is enough.", "Walt Whitman"),
        // Self-Reliance, 1841
        Quote("Trust thyself: every heart vibrates to that iron string.", "Ralph Waldo Emerson"),
        Quote("Nothing can bring you peace but yourself.", "Ralph Waldo Emerson"),
        Quote("Nothing is at last sacred but the integrity of your own mind.", "Ralph Waldo Emerson"),
        // The Marriage of Heaven and Hell, 1790
        Quote("If the doors of perception were cleansed everything would appear to man as it is, infinite.", "William Blake"),
        Quote("Energy is Eternal Delight.", "William Blake"),
        Quote("Exuberance is Beauty.", "William Blake"),
        // Poems, 1891
        Quote("Hope is the thing with feathers that perches in the soul.", "Emily Dickinson"),
    )

    /** Fonts the macOS app offers for text: PostScript or family name to display name. */
    val fonts: List<Pair<String, String>> = listOf(
        "Zapfino" to "Zapfino",
        "Palatino-Roman" to "Palatino",
        "Didot" to "Didot",
        "Baskerville" to "Baskerville",
        "Georgia" to "Georgia",
        "Optima-Regular" to "Optima",
        "GillSans" to "Gill Sans",
        "Futura-Medium" to "Futura",
        "HoeflerText-Regular" to "Hoefler Text",
        "Copperplate" to "Copperplate",
        "AmericanTypewriter" to "Typewriter",
        "Papyrus" to "Papyrus",
        "TimesNewRomanPSMT" to "Times New Roman",
        "HelveticaNeue" to "Helvetica Neue",
    )

    /** The author of a known quote, matched like the macOS app (same text, or text ending with it). */
    fun authorOf(text: String): String? = all.firstOrNull { it.text == text || text.endsWith(it.text) }?.author
}
