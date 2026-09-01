package com.fl0w.speye

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.filled.Stars
import com.fl0w.speye.data.settings.BillingSettingsManager
import com.fl0w.speye.ui.components.SettingsItem
import com.fl0w.speye.ui.components.SettingsToggleItem
import kotlinx.coroutines.launch
import com.fl0w.speye.ui.screens.GoogleDriveScreen
import com.fl0w.speye.utils.DriveAuthManager
import com.fl0w.speye.utils.SpeyeBillingManager
import com.fl0w.speye.utils.SpeyeLogger
import com.fl0w.speye.utils.SpeyeSyncManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

object FlavorDelegateImpl : FlavorDelegate {
    private var cachedAdView: AdView? = null

    override fun initFlavor(context: android.content.Context) {
        DriveAuthManager.init(context)
        val reqConfig = com.google.android.gms.ads.RequestConfiguration.Builder()
            .setTestDeviceIds(listOf(com.google.android.gms.ads.AdRequest.DEVICE_ID_EMULATOR))
            .build()
        MobileAds.setRequestConfiguration(reqConfig)
        MobileAds.initialize(context) {}
        SpeyeBillingManager.init(context)
        SpeyeSyncManager.init(context)
    }

    @Suppress("DEPRECATION")
    private fun getAdaptiveAdSize(activity: android.app.Activity): AdSize {
        val displayMetrics = activity.resources.displayMetrics
        val adWidthPixels = displayMetrics.widthPixels
        val density = displayMetrics.density
        val adWidth = (adWidthPixels / density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
    }

    private fun getOrCreateAdView(activity: android.app.Activity): AdView {
        val existing = cachedAdView
        if (existing != null) {
            (existing.parent as? android.view.ViewGroup)?.removeView(existing)
            return existing
        }

        SpeyeLogger.d("FlavorDelegate", "Creating persistent AdView instance for Activity: $activity")
        val adView = AdView(activity).apply {
            setAdSize(getAdaptiveAdSize(activity))
            adUnitId = if (com.fl0w.speye.BuildConfig.DEBUG) {
                "ca-app-pub-3940256099942544/6300978111"
            } else {
                activity.getString(R.string.ad_unit_id_banner)
            }
            adListener = object : com.google.android.gms.ads.AdListener() {
                override fun onAdLoaded() {
                    SpeyeLogger.d("FlavorDelegate", "Ad loaded successfully")
                }
                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    SpeyeLogger.e("FlavorDelegate", "Ad failed to load: ${error.message} (Code: ${error.code})")
                }
                override fun onAdOpened() {
                    SpeyeLogger.d("FlavorDelegate", "Ad opened")
                }
            }
            loadAd(AdRequest.Builder().build())
        }
        cachedAdView = adView
        return adView
    }

    private fun android.content.Context.findActivity(): android.app.Activity? {
        var ctx = this
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    override fun addFlavorRoutes(navGraphBuilder: NavGraphBuilder, navController: NavHostController) {
        navGraphBuilder.composable("google_drive") {
            GoogleDriveScreen(onBack = { navController.popBackStack() })
        }
    }

    @Composable
    override fun GoogleDriveSettingsItem(onNavigate: () -> Unit) {
        SettingsItem(
            icon = Icons.Default.CloudUpload,
            title = stringResource(R.string.drive_backup),
            subtitle = stringResource(R.string.drive_backup_sub),
            onClick = onNavigate
        )
    }

    @Composable
    override fun ProVersionHeader() {
        val context = LocalContext.current
        val billingSettings = remember { BillingSettingsManager(context) }
        val isPro by billingSettings.isProUser.collectAsState(initial = false)

        LaunchedEffect(Unit) {
            SpeyeLogger.d("FlavorDelegate", "ProVersionHeader composed. isPro: $isPro")
        }

        if (isPro) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SpeyeTheme.colors.primary.copy(alpha = 0.1f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Verified, null, tint = SpeyeTheme.colors.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.pro_active), color = SpeyeTheme.colors.primary, fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SpeyeTheme.colors.primary)
                    .clickable {
                        SpeyeLogger.d("FlavorDelegate", "REMOVE ADS clicked")
                        val activity = context.findActivity()
                        if (activity != null) {
                            SpeyeBillingManager.launchBillingFlow(activity)
                        } else {
                            SpeyeLogger.e("FlavorDelegate", "Could not find Activity context")
                        }
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.remove_ads), color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
        }
    }

    @Composable
    override fun RestorePurchasesItem() {
        val context = LocalContext.current
        val restoredMsg = stringResource(R.string.purchases_restored)
        val noPurchasesMsg = stringResource(R.string.no_purchases_found)

        SettingsItem(
            icon = Icons.Default.Refresh,
            title = stringResource(R.string.restore_purchases),
            subtitle = stringResource(R.string.restore_purchases_sub),
            onClick = {
                SpeyeBillingManager.restorePurchases { hasPro ->
                    val msg = if (hasPro) restoredMsg else noPurchasesMsg
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    @Composable
    override fun ProCrownBadge() {
        val context = LocalContext.current
        val billingSettings = remember { BillingSettingsManager(context) }
        val isPro by billingSettings.isProUser.collectAsState(initial = false)

        if (isPro) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = com.fl0w.speye.ui.SpeyeIcons.Crown,
                    contentDescription = "PRO",
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFFFFD700)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "PRO",
                    color = Color(0xFFFFD700),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }

    @Composable
    override fun DebugProToggle() {
        if (com.fl0w.speye.BuildConfig.DEBUG) {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val billingSettings = remember { BillingSettingsManager(context) }
            val isPro by billingSettings.isProUser.collectAsState(initial = false)

            SettingsToggleItem(
                icon = Icons.Default.Stars,
                title = "Simulate Pro User",
                subtitle = "Toggle Pro version state for testing UI & ads",
                checked = isPro,
                onCheckedChange = { scope.launch { billingSettings.setProUser(it) } }
            )
        }
    }

    @Composable
    override fun AdBanner() {
        val context = LocalContext.current
        val activity = remember(context) { context.findActivity() }
        val billingSettings = remember { BillingSettingsManager(context) }
        val isPro by billingSettings.isProUser.collectAsState(initial = false)

        if (!isPro && activity != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    factory = { _ ->
                        getOrCreateAdView(activity)
                    }
                )
            }
        }
    }
}
