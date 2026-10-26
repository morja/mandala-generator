package com.mathis.mandala.core.quotes

import com.mathis.mandala.core.Quote
import com.mathis.mandala.core.QuoteCollection
import com.mathis.mandala.core.QuoteGroup

// Word for word, in the spelling of the editions named in each group.
internal val quotesDe = QuoteCollection("de", "Deutsch", listOf(
    QuoteGroup("Novalis, Blüthenstaub, 1798 (Projekt Gutenberg-DE, modern spelling)", listOf(
        Quote("Nach Innen geht der geheimnisvolle Weg.", "Novalis"),
    )),
    QuoteGroup("Rainer Maria Rilke, Herbsttag, Das Buch der Bilder, 1906", listOf(
        Quote("Herr: es ist Zeit. Der Sommer war sehr groß.", "Rainer Maria Rilke"),
    )),
    QuoteGroup("Rainer Maria Rilke, Das Stunden-Buch, 1905 (Project Gutenberg)", listOf(
        Quote("Ich glaube an alles noch nie Gesagte.", "Rainer Maria Rilke"),
    )),
    QuoteGroup("Rainer Maria Rilke, Buddha in der Glorie, Der Neuen Gedichte anderer Teil, 1908 (Project Gutenberg)", listOf(
        Quote("Mitte aller Mitten, Kern der Kerne", "Rainer Maria Rilke"),
    )),
    QuoteGroup("Angelus Silesius, Cherubinischer Wandersmann, Insel selection in modern spelling (Project Gutenberg)", listOf(
        Quote("Halt an, wo läufst du hin, der Himmel ist in dir", "Angelus Silesius"),
    )),
    QuoteGroup("Laotse, Tao te king, übersetzt von Richard Wilhelm, 1911", listOf(
        Quote("Wer andre kennt, ist klug, wer sich selber kennt, ist weise.", "Laotse"),
        Quote("Eine Reise von tausend Meilen beginnt mit dem ersten Schritt.", "Laotse"),
        Quote("Er wandert nicht und kommt doch ans Ziel.", "Laotse"),
    )),
    QuoteGroup("Rumi, Aus den Ghaselen des Mewlana Dschelaleddin Rumi, übersetzt von Friedrich Rückert, 1819 (Projekt Gutenberg-DE)", listOf(
        Quote("Ich bin der Morgenschimmer, ich bin der Abendhauch.", "Rumi"),
    )),
    QuoteGroup("Meister Eckhart, Mystische Schriften, übersetzt von Gustav Landauer, 1903 (archive.org scan)", listOf(
        Quote("Gott ist mir näher als ich mir selber bin", "Meister Eckhart"),
    )),
))
