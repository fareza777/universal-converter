package com.universalconverter.pro.billing

import android.app.Activity
import android.content.Context
import android.util.Base64
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.universalconverter.pro.BuildConfig
import com.universalconverter.pro.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

data class BillingUiState(
    val loading: Boolean = true,
    val available: Boolean = false,
    val owned: Boolean = false,
    val busy: Boolean = false,
    val price: String = "$4.99",
    val message: String? = null,
)

class BillingManager(
    context: Context,
    private val settingsStore: SettingsStore,
) {
    private val appContext = context.applicationContext
    private val productId = BuildConfig.REMOVE_ADS_PRODUCT_ID
    private val _state = MutableStateFlow(
        BillingUiState(owned = settingsStore.settings.value.removeAdsPurchased),
    )
    val state: StateFlow<BillingUiState> = _state.asStateFlow()

    private var client: BillingClient? = null
    private var product: ProductDetails? = null

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                _state.update { it.copy(busy = false) }
                purchases?.forEach(::handlePurchase)
            }
            BillingClient.BillingResponseCode.USER_CANCELED ->
                _state.update { it.copy(busy = false, message = "Purchase cancelled.") }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                _state.update { it.copy(busy = false) }
                queryPurchases()
            }
            else -> _state.update {
                it.copy(busy = false, message = "Purchase failed (${result.responseCode}).")
            }
        }
    }

    fun start() {
        if (client != null) return
        _state.update { it.copy(loading = true) }
        client = BillingClient.newBuilder(appContext)
            .setListener(purchasesListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
            )
            .enableAutoServiceReconnection()
            .build()
        connect()
    }

    private fun connect() {
        val billing = client ?: return
        billing.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.update {
                        it.copy(loading = false, available = true, message = null)
                    }
                    queryProduct()
                    queryPurchases()
                } else {
                    _state.update {
                        it.copy(
                            loading = false,
                            available = false,
                            message = "Google Play billing is unavailable on this device.",
                        )
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                // Auto-reconnection is enabled; the library retries on the next API call.
                Log.i("Billing", "Play Billing service disconnected; waiting for auto-reconnect.")
            }
        })
    }

    private fun queryProduct() {
        val billing = client ?: return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        billing.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val details = queryResult.productDetailsList
                product = details.firstOrNull()
                val price = product?.oneTimePurchaseOfferDetails?.formattedPrice ?: "$4.99"
                if (queryResult.unfetchedProductList.isNotEmpty()) {
                    Log.w(
                        "Billing",
                        "Unfetched products: ${queryResult.unfetchedProductList.joinToString { it.productId }}",
                    )
                }
                _state.update {
                    it.copy(
                        available = product != null,
                        price = price,
                        message = if (product == null) "Remove Ads product is not configured in Play Console." else it.message,
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        available = false,
                        message = "Could not load the Remove Ads product (${result.responseCode}).",
                    )
                }
            }
        }
    }

    private fun queryPurchases() {
        val billing = client ?: return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billing.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach(::handlePurchase)
                if (purchases.none { productId in it.products }) {
                    _state.update { it.copy(owned = settingsStore.settings.value.removeAdsPurchased) }
                }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (productId !in purchase.products) return
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                if (!verifySignature(purchase)) {
                    _state.update { it.copy(busy = false, message = "Purchase signature could not be verified.") }
                    return
                }
                grant(purchase)
            }
            Purchase.PurchaseState.PENDING ->
                _state.update { it.copy(busy = false, message = "Purchase pending. Google Play will complete it soon.") }
            else ->
                _state.update { it.copy(busy = false, message = "Purchase was not completed.") }
        }
    }

    private fun grant(purchase: Purchase) {
        settingsStore.update { it.copy(removeAdsPurchased = true) }
        _state.update {
            it.copy(owned = true, busy = false, message = "Ads removed. Thank you for supporting the studio.")
        }
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            client?.acknowledgePurchase(params) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w("Billing", "Acknowledge failed: ${result.responseCode}")
                }
            }
        }
    }

    private fun verifySignature(purchase: Purchase): Boolean {
        val publicKey = BuildConfig.BILLING_PUBLIC_KEY
        if (publicKey.isBlank()) {
            // Production builds should set billingPublicKey in ads.properties.
            Log.w("Billing", "No licensing key configured; accepting Play-reported purchase.")
            return true
        }
        return runCatching {
            val key = KeyFactory.getInstance("RSA").generatePublic(
                X509EncodedKeySpec(Base64.decode(publicKey, Base64.DEFAULT)),
            )
            val data = purchase.originalJson.toByteArray(Charsets.UTF_8)
            val signature = Base64.decode(purchase.signature, Base64.DEFAULT)
            listOf("SHA1withRSA", "SHA256withRSA").any { algorithm ->
                runCatching {
                    val verifier = Signature.getInstance(algorithm)
                    verifier.initVerify(key)
                    verifier.update(data)
                    verifier.verify(signature)
                }.getOrDefault(false)
            }
        }.getOrDefault(false)
    }

    fun purchase(activity: Activity) {
        val billing = client
        val details = product
        if (billing == null || !billing.isReady || details == null) {
            _state.update { it.copy(message = "Remove Ads is not available right now. Try again in a moment.") }
            return
        }
        _state.update { it.copy(busy = true, message = null) }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build(),
                ),
            )
            .build()
        val result = billing.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.update {
                it.copy(busy = false, message = "Could not open Google Play billing (${result.responseCode}).")
            }
        }
    }

    fun restore() {
        if (client?.isReady != true) {
            start()
            return
        }
        _state.update { it.copy(busy = true, message = null) }
        queryPurchases()
        _state.update {
            if (it.owned) it.copy(busy = false, message = "Purchase restored.")
            else it.copy(busy = false, message = "No previous purchase found.")
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}
