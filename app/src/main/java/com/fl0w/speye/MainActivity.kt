package com.fl0w.speye

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import android.os.PowerManager
import android.net.Uri
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.fl0w.speye.ui.screens.AppListScreen
import com.fl0w.speye.ui.screens.DebugLogScreen
import com.fl0w.speye.ui.screens.FaqScreen
import com.fl0w.speye.ui.screens.PrivacyPolicyScreen
import com.fl0w.speye.ui.screens.NotificationListScreen
import com.fl0w.speye.ui.screens.SettingsScreen
import com.fl0w.speye.ui.screens.WelcomeScreen
import com.fl0w.speye.ui.viewmodel.AppListViewModel
import com.fl0w.speye.ui.viewmodel.NotificationViewModel
import com.fl0w.speye.utils.SpeyeLogger
import com.fl0w.speye.data.settings.DebugSettingsManager
import kotlinx.coroutines.launch

@Immutable
data class SpeyeColors(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val secondary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val error: Color,
    val divider: Color
)

val LocalSpeyeColors = staticCompositionLocalOf {
    SpeyeColors(
        background = Color.Unspecified,
        surface = Color.Unspecified,
        primary = Color.Unspecified,
        secondary = Color.Unspecified,
        textPrimary = Color.Unspecified,
        textSecondary = Color.Unspecified,
        error = Color.Unspecified,
        divider = Color.Unspecified
    )
}

class MainActivity : ComponentActivity() {
    private val viewModel: NotificationViewModel by viewModels()
    private val appListViewModel: AppListViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val debugManager = DebugSettingsManager(this)
        FlavorDelegateImpl.initFlavor(this)
        
        lifecycleScope.launch {
            debugManager.isLoggingEnabled.collect { enabled ->
                SpeyeLogger.setLoggingEnabled(enabled)
            }
        }

        setContent {
            var hasNotificationAccess by remember { mutableStateOf(isNotificationServiceEnabled()) }
            var hasPostNotification by remember {
                mutableStateOf(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    } else {
                        true
                    }
                )
            }
            var hasUnrestrictedBackground by remember { 
                mutableStateOf((getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)) 
            }
            
            // Onboarding state: true if permissions are missing or if we're currently in the welcome flow
            var showWelcomeScreen by rememberSaveable { 
                mutableStateOf(!hasNotificationAccess || !hasPostNotification) 
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        hasNotificationAccess = isNotificationServiceEnabled()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            hasPostNotification = ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                        }
                        hasUnrestrictedBackground = (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
                        
                        if (!hasNotificationAccess || !hasPostNotification) {
                            showWelcomeScreen = true
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val appSettings = remember { com.fl0w.speye.data.settings.AppSettingsManager(this) }
            val textScale by appSettings.textScale.collectAsState(initial = 1.0f)
            val isHighContrast by appSettings.highContrast.collectAsState(initial = false)
            val reduceMotion by appSettings.reduceAnimations.collectAsState(initial = false)

            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            SpeyeTheme(
                isHighContrast = isHighContrast,
                textScale = textScale,
                reduceMotion = reduceMotion
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SpeyeTheme.colors.background)
                ) {
                    if (!showWelcomeScreen) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f)) {
                                NavHost(
                                    navController = navController,
                                    startDestination = "notifications",
                                    enterTransition = {
                                        if (reduceMotion) androidx.compose.animation.EnterTransition.None
                                        else androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(200))
                                    },
                                    exitTransition = {
                                        if (reduceMotion) androidx.compose.animation.ExitTransition.None
                                        else androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(200))
                                    },
                                    popEnterTransition = {
                                        if (reduceMotion) androidx.compose.animation.EnterTransition.None
                                        else androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(200))
                                    },
                                    popExitTransition = {
                                        if (reduceMotion) androidx.compose.animation.ExitTransition.None
                                        else androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(200))
                                    }
                                ) {
                                    composable("notifications") {
                                        NotificationListScreen(
                                            viewModel = viewModel,
                                            onTestStandard = { sendStandardTest() },
                                            onTestLongText = { sendLongTextTest() },
                                            onTestImage = { sendImageTest() },
                                            onTestProgress = { sendProgressTest() },
                                            onTestIndeterminateProgress = { sendIndeterminateProgressTest() }
                                        )
                                    }
                                    composable("apps") {
                                        AppListScreen(viewModel = appListViewModel)
                                    }
                                    composable("settings") {
                                        SettingsScreen(
                                            onNavigateToAccessibility = { navController.navigate("accessibility") },
                                            onNavigateToLogs = { navController.navigate("debug_logs") },
                                            onNavigateToGoogleDrive = { navController.navigate("google_drive") },
                                            onNavigateToFaq = { navController.navigate("faq") },
                                            onNavigateToPrivacyPolicy = { navController.navigate("privacy_policy") }
                                        )
                                    }
                                    composable("accessibility") {
                                        com.fl0w.speye.ui.screens.AccessibilityScreen(onBack = { navController.popBackStack() })
                                    }
                                    composable("faq") {
                                        FaqScreen(onBack = { navController.popBackStack() })
                                    }
                                    composable("privacy_policy") {
                                        PrivacyPolicyScreen(onBack = { navController.popBackStack() })
                                    }
                                    composable("debug_logs") {
                                        DebugLogScreen(onBack = { navController.popBackStack() })
                                    }
                                    FlavorDelegateImpl.addFlavorRoutes(this, navController)
                                }
                            }
                            SpeyeBottomBar(
                                currentRoute = currentRoute,
                                onNavigate = { route ->
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.startDestinationId)
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                    } else {
                        WelcomeScreen(
                            hasPostNotification = hasPostNotification,
                            hasNotificationAccess = hasNotificationAccess,
                            hasUnrestrictedBackground = hasUnrestrictedBackground,
                            onGrantPostNotification = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onGrantBackgroundUsage = {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:$packageName")
                                    }
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    startActivity(intent)
                                }
                            },
                            onStart = { showWelcomeScreen = false }
                        )
                    }
                }
            }
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null && TextUtils.equals(pkgName, cn.packageName)) return true
            }
        }
        return false
    }

    private var testNotifyId = 1001
    private var testStep = 0

    fun sendStandardTest() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "test_channel"
        createChannel(notificationManager, channelId)

        when (testStep % 3) {
            0 -> {
                testNotifyId = (1000..9000).random()
                val notification = NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("${getString(R.string.test)} [A]")
                    .setContentText("Status: Initializing...")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .build()
                notificationManager.notify(testNotifyId, notification)
            }
            1 -> {
                val notification = NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("${getString(R.string.test)} [B]")
                    .setContentText("Status: Updated!")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .build()
                notificationManager.notify(testNotifyId, notification)
            }
            2 -> {
                notificationManager.cancel(testNotifyId)
            }
        }
        testStep++
    }

    fun sendLongTextTest() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "test_channel"
        createChannel(notificationManager, channelId)

        val longText = """
            This is a very long message to test if Speye can capture and display long content properly.
            It spans multiple lines and contains quite a bit of detail.
            Notifications often truncate this in the collapsed view, but the 'BigTextStyle' allows the system and notification listeners to see the full context.
            Speye should store all of this and show it in the history list without losing any important information for the user to read later.
            The goal is to ensure that even large updates or long chat messages are perfectly preserved in the local Room database.
        """.trimIndent()

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Long Context Test")
            .setContentText("This text is long...")
            .setStyle(NotificationCompat.BigTextStyle().bigText(longText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        notificationManager.notify((1000..9000).random(), notification)
    }

    fun sendImageTest() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "test_channel"
        createChannel(notificationManager, channelId)

        val bitmap = createTestBitmap()

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Attachment Test")
            .setContentText("This notification has an image!")
            .setLargeIcon(bitmap)
            .setStyle(NotificationCompat.BigPictureStyle()
                .bigPicture(bitmap)
                .bigLargeIcon(null as Bitmap?))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        notificationManager.notify((1000..9000).random(), notification)
    }

    fun sendProgressTest() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "test_channel"
        createChannel(notificationManager, channelId)
        val testId = 8888

        lifecycleScope.launch {
            for (i in 0..100 step 20) {
                val notification = NotificationCompat.Builder(this@MainActivity, channelId)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("Download Progress Test")
                    .setContentText("Downloading test asset ($i%)...")
                    .setProgress(100, i, false)
                    .setOngoing(i < 100)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .build()
                notificationManager.notify(testId, notification)
                if (i < 100) kotlinx.coroutines.delay(400)
            }
        }
    }

    fun sendIndeterminateProgressTest() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "test_channel"
        createChannel(notificationManager, channelId)
        val testId = 8889

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Processing Asset")
            .setContentText("Optimizing local database...")
            .setProgress(0, 0, true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        notificationManager.notify(testId, notification)
    }

    private fun createChannel(manager: NotificationManager, id: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(id, "Test Channel", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createTestBitmap(): Bitmap {
        val b = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(b)
        val paint = Paint()
        paint.color = android.graphics.Color.DKGRAY
        canvas.drawRect(0f, 0f, 400f, 200f, paint)
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 40f
        canvas.drawText("SPEYE ATTACHMENT", 30f, 110f, paint)
        return b
    }
}

@Composable
fun SpeyeBottomBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(SpeyeTheme.colors.surface)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavItem(
            icon = Icons.Default.Notifications,
            label = stringResource(R.string.nav_history),
            isSelected = currentRoute == "notifications",
            onClick = { onNavigate("notifications") }
        )
        BottomNavItem(
            icon = Icons.AutoMirrored.Filled.List,
            label = stringResource(R.string.nav_apps),
            isSelected = currentRoute == "apps",
            onClick = { onNavigate("apps") }
        )
        BottomNavItem(
            icon = Icons.Default.Settings,
            label = stringResource(R.string.nav_settings),
            isSelected = currentRoute == "settings",
            onClick = { onNavigate("settings") }
        )
    }
}

@Composable
fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) SpeyeTheme.colors.primary else SpeyeTheme.colors.textSecondary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            color = if (isSelected) SpeyeTheme.colors.primary else SpeyeTheme.colors.textSecondary,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

val LocalReduceMotion = staticCompositionLocalOf { false }

object SpeyeTheme {
    val colors: SpeyeColors
        @Composable
        get() = LocalSpeyeColors.current

    val reduceMotion: Boolean
        @Composable
        get() = LocalReduceMotion.current
}

@Composable
fun SpeyeTheme(
    isHighContrast: Boolean = false,
    textScale: Float = 1.0f,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (isHighContrast) {
        SpeyeColors(
            background = Color(0xFF000000),
            surface = Color(0xFF161616),
            primary = Color(0xFFFF9FF6),
            secondary = Color(0xFF00FFE0),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFFCCCCCC),
            error = Color(0xFFFF6B81),
            divider = Color(0xFF444444)
        )
    } else {
        SpeyeColors(
            background = Color(0xFF000000),
            surface = Color(0xFF111111),
            primary = Color(0xFFFF8FF5),
            secondary = Color(0xFF03DAC6),
            textPrimary = Color.White,
            textSecondary = Color(0xFF888888),
            error = Color(0xFFCF6679),
            divider = Color(0xFF222222)
        )
    }

    val currentDensity = androidx.compose.ui.platform.LocalDensity.current
    val customDensity = androidx.compose.ui.unit.Density(
        density = currentDensity.density,
        fontScale = currentDensity.fontScale * textScale
    )

    CompositionLocalProvider(
        LocalSpeyeColors provides colors,
        LocalReduceMotion provides reduceMotion,
        androidx.compose.ui.platform.LocalDensity provides customDensity
    ) {
        content()
    }
}
