package com.mathis.mandala.app.ui

import androidx.annotation.DrawableRes
import com.mathis.mandala.app.R
import com.mathis.mandala.core.EmblemMotif

/**
 * Stable examples of the motifs for the emblem chips, like the style previews: rendered once by the
 * core (`cli`, Silver palette, filled, a ring, no background, 192 px halved to 96) and shipped as
 * small bitmaps. Android first; the Mac app has no emblem pictures yet.
 */
@DrawableRes
internal fun emblemPreviewResource(motif: EmblemMotif): Int = when (motif) {
    EmblemMotif.OM -> R.drawable.emblem_preview_om
    EmblemMotif.TREE_OF_LIFE -> R.drawable.emblem_preview_tree_of_life
    EmblemMotif.FLOWER_OF_LIFE -> R.drawable.emblem_preview_flower_of_life
    EmblemMotif.LOTUS -> R.drawable.emblem_preview_lotus
    EmblemMotif.YIN_YANG -> R.drawable.emblem_preview_yin_yang
    EmblemMotif.DHARMA_WHEEL -> R.drawable.emblem_preview_dharma_wheel
    EmblemMotif.UNALOME -> R.drawable.emblem_preview_unalome
    EmblemMotif.TRIPLE_MOON -> R.drawable.emblem_preview_triple_moon
    EmblemMotif.SRI_YANTRA -> R.drawable.emblem_preview_sri_yantra
}
