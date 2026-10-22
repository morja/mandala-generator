package com.mathis.mandala.app

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the app knows about the Pro purchase. */
data class ProState(
    val owned: Boolean,
    /** Localised price from Google Play, null until known. */
    val price: String? = null,
    /** False when Google Play cannot sell it here (no Play Store, a build not installed from Play, offline). */
    val available: Boolean = false,
)

/**
 * The one-time "Pro" purchase through Google Play Billing (ADR-0007). Ownership is cached so the app
 * knows it offline, and checked with Google Play on every start. Test builds (debug, fast) count as
 * Pro, so the Pro features can be tried without a Play install.
 */
class ProPurchases(context: Context, private val onEvent: (String) -> Unit) {
    private val prefs = context.getSharedPreferences("pro", Context.MODE_PRIVATE)
    private val testBuild = context.resources.getBoolean(R.bool.pro_for_testing)
    private val _state = MutableStateFlow(ProState(owned = testBuild || prefs.getBoolean(KEY_OWNED, false)))
    val state: StateFlow<ProState> = _state.asStateFlow()

    private var details: ProductDetails? = null

    private val client = BillingClient.newBuilder(context)
        .setListener { result, purchases ->
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach(::handle)
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refreshPurchases()
                BillingClient.BillingResponseCode.USER_CANCELED -> Unit
                else -> onEvent("The purchase did not go through (${result.debugMessage.ifEmpty { "code ${result.responseCode}" }}).")
            }
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun connect() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) return
                refreshPurchases()
                loadProduct()
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    fun close() = client.endConnection()

    /** Opens Google Play's purchase sheet. */
    fun buy(activity: Activity) {
        val product = details
        if (product == null) {
            onEvent("Pro cannot be bought here right now. It is sold through Google Play.")
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build()))
            .build()
        client.launchBillingFlow(activity, params)
    }

    private fun loadProduct() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        client.queryProductDetailsAsync(params) { result, found ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val pro = found.productDetailsList.firstOrNull { it.productId == PRODUCT_ID } ?: return@queryProductDetailsAsync
            details = pro
            _state.update { it.copy(price = pro.oneTimePurchaseOfferDetails?.formattedPrice, available = true) }
        }
    }

    private fun refreshPurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val owned = purchases.any { PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
            setOwned(owned)
            purchases.forEach(::acknowledge)
        }
    }

    private fun handle(purchase: Purchase) {
        if (PRODUCT_ID !in purchase.products) return
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                if (!_state.value.owned) onEvent("Thank you! Pro is unlocked.")
                setOwned(true)
                acknowledge(purchase)
            }
            Purchase.PurchaseState.PENDING -> onEvent("The purchase is pending. Pro unlocks once it is paid.")
            else -> Unit
        }
    }

    /** Google refunds purchases that are not acknowledged within three days. */
    private fun acknowledge(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED || purchase.isAcknowledged) return
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { }
    }

    private fun setOwned(owned: Boolean) {
        prefs.edit().putBoolean(KEY_OWNED, owned).apply()
        _state.update { it.copy(owned = owned || testBuild) }
    }

    companion object {
        /** The one-time product to create in the Play Console. */
        const val PRODUCT_ID = "pro"
        private const val KEY_OWNED = "owned"
    }
}
