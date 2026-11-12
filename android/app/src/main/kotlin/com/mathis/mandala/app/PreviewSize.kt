package com.mathis.mandala.app

/**
 * How large the phone preview stands above the controls, as a fraction of its full side (UX-1 11):
 * full, a middle size that leaves the controls more room while the picture still shows its detail,
 * and small, a thumbnail with what the open tab is about beside it.
 */
/**
 * The phone preview's side as a fraction of the full one (UX-1 11): a tap makes it small or full
 * again, a pull leaves it wherever the finger lets go, except close to an end.
 */
object PreviewSize {
    const val FULL = 1f
    const val SMALL = 0.32f

    /** Let go this close to an end and it takes the end, so a pull does not stop just short of full. */
    const val SNAP = 0.1f

    /** Above this the status shows on the image; the tab's title beside it is fully in by [HEADER_BELOW]. */
    const val STATUS_ABOVE = 0.8f
    const val HEADER_BELOW = 0.6f

    /** Below this the image is too small for the labels (and the Pro mark) on it. */
    const val LABELS_ABOVE = 0.46f

    fun snapped(fraction: Float): Float = when {
        fraction >= FULL - SNAP -> FULL
        fraction <= SMALL + SNAP -> SMALL
        else -> fraction
    }
}
