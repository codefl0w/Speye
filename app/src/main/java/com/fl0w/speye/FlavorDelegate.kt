package com.fl0w.speye

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController

interface FlavorDelegate {
    fun initFlavor(context: android.content.Context)
    fun addFlavorRoutes(navGraphBuilder: NavGraphBuilder, navController: NavHostController)
    
    @Composable
    fun GoogleDriveSettingsItem(onNavigate: () -> Unit)

    @Composable
    fun ProVersionHeader()

    @Composable
    fun ProCrownBadge()

    @Composable
    fun DebugProToggle()

    @Composable
    fun RestorePurchasesItem()

    @Composable
    fun AdBanner()
}
