package com.fl0w.speye.utils

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.fl0w.speye.data.settings.BillingSettingsManager
import com.fl0w.speye.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object SpeyeBillingManager : PurchasesUpdatedListener {
    private const val TAG = "SpeyeBillingManager"
    private const val PRO_PRODUCT_ID = "pro_version"

    private lateinit var billingClient: BillingClient
    private var billingSettings: BillingSettingsManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun init(context: Context) {
        billingSettings = BillingSettingsManager(context)
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()

        connectToBilling()
    }

    private fun connectToBilling() {
        if (billingClient.isReady) return
        
        SpeyeLogger.d(TAG, "Connecting to billing...")
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                SpeyeLogger.d(TAG, "onBillingSetupFinished: ${billingResult.responseCode}")
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    SpeyeLogger.d(TAG, "Billing client setup finished")
                    queryPurchases()
                } else {
                    SpeyeLogger.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                SpeyeLogger.d(TAG, "Billing service disconnected, retrying...")
                connectToBilling()
            }
        })
    }

    fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val isPro = purchases.any { purchase ->
                    purchase.products.contains(PRO_PRODUCT_ID) && purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                scope.launch {
                    billingSettings?.setProUser(isPro)
                }
                
                // Acknowledge if needed
                purchases.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                        acknowledgePurchase(purchase)
                    }
                }
            }
        }
    }

    fun restorePurchases(callback: (Boolean) -> Unit) {
        if (!billingClient.isReady) {
            connectToBilling()
            callback(false)
            return
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            val isPro = if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.any { purchase ->
                    purchase.products.contains(PRO_PRODUCT_ID) && purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
            } else {
                false
            }
            scope.launch {
                billingSettings?.setProUser(isPro)
                callback(isPro)
            }
            if (isPro) {
                purchases.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                        acknowledgePurchase(purchase)
                    }
                }
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                SpeyeLogger.d(TAG, "Purchase acknowledged")
                queryPurchases()
            }
        }
    }

    fun launchBillingFlow(activity: Activity) {
        SpeyeLogger.d(TAG, "launchBillingFlow requested")
        if (!billingClient.isReady) {
            SpeyeLogger.e(TAG, "Billing client is not ready. Reconnecting...")
            connectToBilling()
            return
        }

        scope.launch {
            val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(PRO_PRODUCT_ID)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    )
                )
                .build()

            val result = billingClient.queryProductDetails(queryProductDetailsParams)
            val productDetailsList = result.productDetailsList
            
            SpeyeLogger.d(TAG, "queryProductDetails finished. Found ${productDetailsList?.size ?: 0} products. Response code: ${result.billingResult.responseCode}")

            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList != null && productDetailsList.isNotEmpty()) {
                val productDetails = productDetailsList[0]
                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productDetails)
                                .build()
                        )
                    )
                    .build()
                billingClient.launchBillingFlow(activity, billingFlowParams)
            } else {
                val error = "Failed to find product '$PRO_PRODUCT_ID' in Play Store. Ensure it's created in Console and app is on a testing track."
                SpeyeLogger.e(TAG, error)
                scope.launch {
                    android.widget.Toast.makeText(activity, activity.getString(R.string.billing_product_not_found), android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            queryPurchases()
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            SpeyeLogger.d(TAG, "User cancelled the purchase")
        } else {
            SpeyeLogger.e(TAG, "Purchases updated error: ${billingResult.debugMessage}")
        }
    }
}
