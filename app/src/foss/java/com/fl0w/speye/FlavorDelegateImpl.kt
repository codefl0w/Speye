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
