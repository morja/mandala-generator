package com.mathis.mandala.app

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import one.tearoom.unlock.OneTimeUnlock
import one.tearoom.unlock.PriceProblem
import one.tearoom.unlock.UnlockConfig
import one.tearoom.unlock.UnlockMessage

/**
 * The one-time "Pro" purchase through Google Play (ADR-0007), with the shared hardened billing of
 * tearoom-android-kit (ADR-0022): one instance for the whole app. Ownership is cached so the app knows
 * it offline (also the daily wallpaper and the widget), and checked with Google Play on every resume.
 * Test builds (debug, fast) count as Pro through `pro_for_testing`, so Pro can be tried without a Play
 * install; the free build does not.
 */
object Pro {
    /** The one-time product in the Play Console; its purchase option is `buy`. */
    const val PRODUCT_ID = "pro"

    @Volatile private var instance: OneTimeUnlock? = null

    fun unlock(context: Context): OneTimeUnlock = instance ?: synchronized(this) {
        instance ?: OneTimeUnlock(
            context.applicationContext as Application,
            UnlockConfig(
                productId = PRODUCT_ID,
                // The cache of the earlier ProPurchases, kept so nothing changes for a phone that owns Pro.
                prefsName = "pro",
                prefsKey = "owned",
                // Play licence key for the local signature check (ADR-0022); off until it is added.
                publicKeyBase64 = null,
                debugUnlocked = { it.resources.getBoolean(R.bool.pro_for_testing) },
            ),
        ).also { instance = it }
    }

    /** Pro as last known, without asking Google Play; for work in the background (the daily wallpaper). */
    fun isUnlocked(context: Context): Boolean = unlock(context).isUnlocked(context)
}

/** The library reports codes; the texts are Dahlia's, in every app language (ADR-0019). */
@get:StringRes
val UnlockMessage.textRes: Int
    get() = when (this) {
        UnlockMessage.UNLOCKED -> R.string.pro_unlocked
        UnlockMessage.PENDING -> R.string.pro_pending
        UnlockMessage.ACTIVE_HERE -> R.string.pro_restore_active
        UnlockMessage.NOT_FOUND -> R.string.pro_restore_not_found
        UnlockMessage.PLAY_UNREACHABLE -> R.string.pro_restore_unreachable
        UnlockMessage.PRICE_REFRESHING -> R.string.pro_price_refreshing
        UnlockMessage.PURCHASE_NOT_STARTED -> R.string.pro_purchase_not_started
        UnlockMessage.PURCHASE_INCOMPLETE -> R.string.pro_purchase_incomplete
    }

@get:StringRes
val PriceProblem.textRes: Int
    get() = when (this) {
        PriceProblem.BILLING_UNAVAILABLE -> R.string.editor_pro_unavailable
        PriceProblem.PRODUCT_UNAVAILABLE -> R.string.pro_product_unavailable
        PriceProblem.PLAY_UNAVAILABLE -> R.string.pro_play_unavailable
    }
