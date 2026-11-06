package com.mathis.mandala.app

import one.tearoom.unlock.AppUnlock
import one.tearoom.unlock.UnlockConfig
import one.tearoom.unlock.UnlockTexts

/** Base64 RSA public key of the app (Play Console, Monetisation setup, Licensing); public, not a secret. */
private const val LICENCE_KEY =
    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAst9NnLLPwngiEAADvgg3RgOmni2Vm83GGs329K+Bw0MgGCe7X/j/rwSxvRkeM5bhXudWu5vc+jjDLodX3y+mXk9Ultqlwe0JHi8Ri7GyHHEgTFLJGxSf8FEGIiupq/9KNpHJXg/vyAM+JZZ2nr8UPo0pemXMC61vG9K+0ue0lPT+GnGXcomaQ+cKelIWWsMJvaFrS6ZbXJOXDsbWQTAfTj4LtguQFIwPj+14CA1tkopdtE4/gOGXcOxsfgJZ5uXkuq2tVNgAQfLTAdhIHrDVAsPAvdAn0h/llGbVZFxhxiIzlIHEEdlZeHOlA6UBLV0XI1zvjvumiLLj0HuOJJv6PQIDAQAB"

/**
 * The one-time "Pro" purchase through Google Play (ADR-0007), with the shared flow of tearoom-android-kit
 * (ADR-0022): one `AppUnlock` for the whole app. Ownership is cached so the app knows it offline (also
 * the daily wallpaper and the widget, through `Pro.isUnlocked`), and checked with Google Play on every
 * resume. Test builds (debug, fast) count as Pro through `pro_for_testing`, so Pro can be tried without
 * a Play install; the free build does not.
 */
val Pro = AppUnlock(
    UnlockConfig(
        // The one-time product in the Play Console; its purchase option is `buy`.
        productId = "pro",
        // The cache of the earlier ProPurchases, kept so nothing changes for a phone that owns Pro.
        prefsName = "pro",
        prefsKey = "owned",
        // Play licence key copied from the Play Console on 30 October 2026; the signature check is on (ADR-0022).
        publicKeyBase64 = LICENCE_KEY,
        debugUnlocked = { it.resources.getBoolean(R.bool.pro_for_testing) },
    ),
    // The library reports codes; the texts are Dahlia's, in every app language (ADR-0019).
    UnlockTexts(
        unlocked = R.string.pro_unlocked,
        pending = R.string.pro_pending,
        activeHere = R.string.pro_restore_active,
        notFound = R.string.pro_restore_not_found,
        playUnreachable = R.string.pro_restore_unreachable,
        priceRefreshing = R.string.pro_price_refreshing,
        purchaseNotStarted = R.string.pro_purchase_not_started,
        purchaseIncomplete = R.string.pro_purchase_incomplete,
        billingUnavailable = R.string.editor_pro_unavailable,
        productUnavailable = R.string.pro_product_unavailable,
        playUnavailable = R.string.pro_play_unavailable,
        buy = R.string.editor_pro_buy_for,
        buyWithoutPrice = R.string.editor_pro_buy,
        loadingPrice = R.string.pro_loading_price,
        retry = R.string.editor_pro_retry,
        restore = R.string.editor_pro_restore,
    ),
)
