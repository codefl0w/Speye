package com.fl0w.speye

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController

object FlavorDelegateImpl : FlavorDelegate {
    override fun initFlavor(context: android.content.Context) {}

    override fun addFlavorRoutes(navGraphBuilder: NavGraphBuilder, navController: NavHostController) {
        // No extra routes for FOSS
    }

    @Composable
    override fun GoogleDriveSettingsItem(onNavigate: () -> Unit) {
        // No-op for FOSS
    }

    @Composable
    override fun SupportDeveloperItem() {
        val context = androidx.compose.ui.platform.LocalContext.current
        com.fl0w.speye.ui.components.SettingsItem(
            icon = com.fl0w.speye.ui.SpeyeIcons.Heart,
            title = androidx.compose.ui.res.stringResource(com.fl0w.speye.R.string.support_developer),
            subtitle = "buymeacoffee.com/fl0w",
            onClick = {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://buymeacoffee.com/fl0w"))
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        )
    }

    @Composable
    override fun ProVersionHeader() {
        // No-op for FOSS
    }

    @Composable
    override fun ProCrownBadge() {
        // No-op for FOSS
    }

    @Composable
    override fun DebugProToggle() {
        // No-op for FOSS
    }

    @Composable
    override fun RestorePurchasesItem() {
        // No-op for FOSS
    }

    @Composable
    override fun AdBanner() {
        // No-op for FOSS
    }
}
