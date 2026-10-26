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
    QuoteGroup("Laotse, Tao te king, übersetzt von Richard Wilhelm, 1911", listOf(
        Quote("Wer andre kennt, ist klug, wer sich selber kennt, ist weise.", "Laotse"),
    )),
))
