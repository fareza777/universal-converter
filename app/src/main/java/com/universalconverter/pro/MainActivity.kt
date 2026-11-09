package com.universalconverter.pro

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.universalconverter.pro.ads.BannerAd
import com.universalconverter.pro.core.formats.FormatFamily
import com.universalconverter.pro.core.formats.FormatRegistry
import com.universalconverter.pro.data.JobStatus
import com.universalconverter.pro.data.ThemeMode
import com.universalconverter.pro.ui.MainViewModel
import com.universalconverter.pro.ui.components.UCBottomBar
import com.universalconverter.pro.ui.onboarding.OnboardingScreen
import com.universalconverter.pro.ui.onboarding.SplashScreen
import com.universalconverter.pro.ui.screens.CapabilitiesScreen
import com.universalconverter.pro.ui.screens.ConvertScreen
import com.universalconverter.pro.ui.screens.DiagnosticsScreen
import com.universalconverter.pro.ui.screens.HistoryScreen
import com.universalconverter.pro.ui.screens.LibraryScreen
import com.universalconverter.pro.ui.screens.LicensesScreen
import com.universalconverter.pro.ui.screens.QueueScreen
import com.universalconverter.pro.ui.screens.SettingsScreen
import com.universalconverter.pro.ui.studio.StudioScreen
import com.universalconverter.pro.ui.theme.UC
import com.universalconverter.pro.ui.theme.UniversalConverterTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val splashStartedAt = SystemClock.uptimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition {
            SystemClock.uptimeMillis() - splashStartedAt < 350
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UniversalApp(viewModel)
        }
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return
        val uris = extractUris(intent)
        if (uris.isNotEmpty()) {
            viewModel.importUris(uris, navigate = true)
        }
        intent.removeExtra(Intent.EXTRA_STREAM)
        intent.clipData = null
    }

    @Suppress("DEPRECATION")
    private fun extractUris(intent: Intent): List<Uri> {
        val result = LinkedHashSet<Uri>()
        intent.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
                clip.getItemAt(index).uri?.let { if (it.scheme == "content" || it.scheme == "file") result += it }
            }
        }
        val single = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (single != null && (single.scheme == "content" || single.scheme == "file")) result += single
        val multiple = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        }
        multiple?.forEach { if (it.scheme == "content" || it.scheme == "file") result += it }
        return result.toList()
    }
}

@Composable
private fun UniversalApp(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    var showSplash by remember { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(500)
        showSplash = false
    }
    UniversalConverterTheme(
        darkTheme = darkTheme,
        accentTheme = settings.accentTheme,
        pureBlack = settings.pureBlack,
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.context as? ComponentActivity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
        when {
            showSplash -> SplashScreen()
            !settings.onboardingDone -> OnboardingScreen(
                viewModel = viewModel,
                onDone = viewModel::completeOnboarding,
            )
            else -> AppContent(viewModel, darkTheme)
        }
    }
}

@Composable
private fun AppContent(viewModel: MainViewModel, darkTheme: Boolean) {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val queue by viewModel.queueItems.collectAsStateWithLifecycle()
    val uiState by viewModel.ui.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val adState by viewModel.adState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? Activity
    val queueActive = queue.any { it.status == JobStatus.QUEUED || it.status == JobStatus.RUNNING }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route ?: "convert"
    val topLevel = route in setOf("convert", "library", "history", "library?path={path}")
    val inStudio = route.startsWith("studio")
    val showBanner = topLevel && !settings.adsFree() && adState.initialized && adState.canRequestAds

    LaunchedEffect(activity) {
        if (activity != null) viewModel.initializeMonetization(activity)
    }

    var completedJobs by remember { mutableIntStateOf(queue.count { it.status == JobStatus.DONE }) }
    LaunchedEffect(queue, route) {
        val done = queue.count { it.status == JobStatus.DONE }
        if (done > completedJobs) {
            completedJobs = done
            if (route == "queue") activity?.let { viewModel.maybeShowInterstitial(it) }
        } else if (done < completedJobs) {
            completedJobs = done
        }
    }

    val view = LocalView.current
    SideEffect {
        val window = (view.context as? ComponentActivity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        val lightIcons = darkTheme || inStudio
        controller.isAppearanceLightStatusBars = !lightIcons
        controller.isAppearanceLightNavigationBars = !lightIcons
        view.keepScreenOn = settings.keepScreenAwake && queueActive
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { message -> snackbar.showSnackbar(message) }
    }
    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { target ->
            when (target) {
                "convert" -> {
                    if (navController.currentDestination?.route != "convert") {
                        navController.navigate("convert") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                            launchSingleTop = true
                        }
                    }
                }
                "queue" -> navController.navigate("queue") { launchSingleTop = true }
            }
        }
    }

    fun goTop(routeName: String) {
        if (route == routeName) return
        navController.navigate(routeName) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openStudio(path: String? = null) {
        val target = if (path.isNullOrBlank()) "studio" else "studio?path=${Uri.encode(path)}"
        navController.navigate(target) { launchSingleTop = true }
    }

    fun openFolder(file: File) {
        navController.navigate("library/folder?path=${Uri.encode(file.absolutePath)}") { launchSingleTop = true }
    }

    Scaffold(
        containerColor = UC.palette.paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                snackbar,
                modifier = Modifier.padding(
                    bottom = if (route == "convert" && uiState.draft.isNotEmpty()) 84.dp else 0.dp,
                ),
            )
        },
        bottomBar = {
            if (topLevel) {
                Column {
                    BannerAd(visible = showBanner)
                    UCBottomBar(
                        currentRoute = route,
                        queueActive = queueActive,
                        onNavigate = { target ->
                            when (target) {
                                "studio" -> openStudio()
                                else -> goTop(target)
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        val contentModifier = if (topLevel) {
            Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())
        } else {
            Modifier.fillMaxSize()
        }
        NavHost(
            navController = navController,
            startDestination = "convert",
            modifier = contentModifier,
        ) {
            composable("convert") {
                ConvertScreen(
                    viewModel = viewModel,
                    onOpenQueue = { navController.navigate("queue") { launchSingleTop = true } },
                    onOpenSettings = { navController.navigate("settings") { launchSingleTop = true } },
                    onOpenStudio = { openStudio() },
                    onOpenLibrary = { goTop("library") },
                    onOpenRecent = { item ->
                        when {
                            item.isDirectory -> openFolder(item.file)
                            FormatRegistry.findForFileName(item.displayName)?.family == FormatFamily.MESH ->
                                openStudio(item.file.absolutePath)
                            else -> goTop("library")
                        }
                    },
                )
            }
            composable("library") {
                LibraryScreen(
                    viewModel = viewModel,
                    folderPath = null,
                    onBack = {},
                    onOpenFolder = { openFolder(it) },
                    onOpenStudio = { openStudio(it.absolutePath) },
                    onOpenConvert = { goTop("convert") },
                )
            }
            composable(
                route = "library/folder?path={path}",
                arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                val path = entry.arguments?.getString("path").orEmpty()
                LibraryScreen(
                    viewModel = viewModel,
                    folderPath = path,
                    onBack = { navController.popBackStack() },
                    onOpenFolder = { openFolder(it) },
                    onOpenStudio = { openStudio(it.absolutePath) },
                    onOpenConvert = { goTop("convert") },
                )
            }
            composable("history") {
                HistoryScreen(
                    viewModel = viewModel,
                    onOpenFolder = { openFolder(it) },
                    onOpenStudio = { openStudio(it.absolutePath) },
                )
            }
            composable(
                route = "studio?path={path}",
                arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                StudioScreen(
                    viewModel = viewModel,
                    filePath = entry.arguments?.getString("path")?.takeIf { it.isNotBlank() },
                    onBack = { navController.popBackStack() },
                    onOpenPath = { path ->
                        navController.navigate("studio?path=${Uri.encode(path)}") {
                            popUpTo("studio?path={path}") { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onOpenLibrary = { goTop("library") },
                    onConvertFile = { viewModel.addExistingFile(it) },
                )
            }
            composable("queue") {
                QueueScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenFolder = { openFolder(it) },
                    onOpenStudioFile = { openStudio(it.absolutePath) },
                )
            }
            composable("settings") {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenLibrary = { goTop("library") },
                    onOpenCapabilities = { navController.navigate("capabilities") },
                    onOpenLicenses = { navController.navigate("licenses") },
                    onOpenDiagnostics = { navController.navigate("diagnostics") },
                )
            }
            composable("capabilities") {
                CapabilitiesScreen(onBack = { navController.popBackStack() })
            }
            composable("licenses") {
                LicensesScreen(onBack = { navController.popBackStack() })
            }
            composable("diagnostics") {
                DiagnosticsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}