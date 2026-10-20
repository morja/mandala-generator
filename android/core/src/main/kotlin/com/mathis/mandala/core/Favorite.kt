package com.mathis.mandala.core

import kotlinx.serialization.Serializable

/**
 * A saved favourite, field for field like the macOS app's `Favorite`, so its favourites file can be
 * imported unchanged and written back.
 */
@Serializable
data class Favorite(
    /** UUID string. */
    val id: String,
    val parameters: MandalaParameters,
    /** JPEG thumbnail (about 128 px), base64 encoded as Swift's JSONEncoder writes `Data`. */
    val thumbnailData: String = "",
    val name: String = "",
    /** Seconds since 1 January 2001, Swift's default encoding of `Date`. */
    val createdAt: Double = 0.0,
) {
    companion object {
        /** Seconds between 1 January 1970 and 1 January 2001. */
        const val REFERENCE_DATE_OFFSET = 978_307_200.0

        fun createdNow(nowMillis: Long): Double = nowMillis / 1000.0 - REFERENCE_DATE_OFFSET
    }
}
