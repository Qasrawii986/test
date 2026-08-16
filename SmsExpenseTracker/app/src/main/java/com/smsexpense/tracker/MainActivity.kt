package com.smsexpense.tracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smsexpense.tracker.service.sync.SyncScheduler
import com.smsexpense.tracker.ui.SimpleFactory
import com.smsexpense.tracker.ui.categories.CategoriesScreen
import com.smsexpense.tracker.ui.categories.CategoriesViewModel
import com.smsexpense.tracker.ui.dashboard.DashboardScreen
import com.smsexpense.tracker.ui.dashboard.DashboardViewModel
import com.smsexpense.tracker.ui.debug.DebugScreen
import com.smsexpense.tracker.ui.debug.DebugViewModel
import com.smsexpense.tracker.ui.onboarding.PermissionsScreen
import com.smsexpense.tracker.ui.onboarding.PermissionsState
import com.smsexpense.tracker.ui.payments.PaymentDetailScreen
import com.smsexpense.tracker.ui.payments.PaymentDetailViewModel
import com.smsexpense.tracker.ui.settings.SettingsScreen
import com.smsexpense.tracker.ui.settings.SettingsViewModel
import com.smsexpense.tracker.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    /** Set when launched from a payment notification, consumed once by AppRoot. */
    private var pendingPaymentId by mutableStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = appContainer()
        pendingPaymentId = intent?.getLongExtra(EXTRA_OPEN_PAYMENT_ID, 0L) ?: 0L

        // Re-arm the back-tap gesture: its foreground service does not survive a
        // reboot, and starting it from here (foreground) is always permitted.
        lifecycleScope.launch {
            if (container.settingsRepository.backTapEnabled.first()) {
                com.smsexpense.tracker.service.quicklaunch.BackTapService.start(this@MainActivity)
            }
        }

        setContent {
            AppTheme {
                AppRoot(container)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingPaymentId = intent.getLongExtra(EXTRA_OPEN_PAYMENT_ID, 0L)
    }

    companion object {
        const val EXTRA_OPEN_PAYMENT_ID = "open_payment_id"
    }

    private fun currentPermissions() = PermissionsState(
        smsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED,
        overlayGranted = Settings.canDrawOverlays(this),
        notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED,
    )

    @Composable
    private fun AppRoot(container: AppContainer) {
        var permissions by remember { mutableStateOf(currentPermissions()) }
        var onboardingDone by remember { mutableStateOf(permissions.allGranted) }

        // Re-check permissions whenever the activity resumes (e.g. returning from
        // the overlay settings page).
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    permissions = currentPermissions()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        if (!onboardingDone && !permissions.allGranted) {
            PermissionsScreen(
                state = permissions,
                onRefresh = { permissions = currentPermissions() },
                onSkip = { onboardingDone = true },
            )
            return
        }

        val navController = rememberNavController()
        val defaultCurrency by container.settingsRepository.defaultCurrency
            .collectAsState(initial = "JOD")

        // First-run setup wizard: shown once when nothing is configured yet.
        val setupNeeded by androidx.compose.runtime.produceState<Boolean?>(initialValue = null) {
            val done = container.settingsRepository.setupCompleted.first()
            val senders = container.settingsRepository.senderIds.first()
            value = !done && senders.isEmpty()
        }
        androidx.compose.runtime.LaunchedEffect(setupNeeded) {
            if (setupNeeded == true) navController.navigate("setup")
        }

        // Opened from a payment notification: jump straight to that payment.
        androidx.compose.runtime.LaunchedEffect(pendingPaymentId) {
            if (pendingPaymentId > 0) {
                navController.navigate("payment/$pendingPaymentId")
                pendingPaymentId = 0L
            }
        }

        NavHost(navController = navController, startDestination = "dashboard") {
            composable("dashboard") {
                val vm: DashboardViewModel = viewModel(
                    factory = SimpleFactory {
                        DashboardViewModel(container.paymentRepository, container.categoryRepository)
                    }
                )
                DashboardScreen(
                    viewModel = vm,
                    defaultCurrency = defaultCurrency,
                    onPaymentClick = { id -> navController.navigate("payment/$id") },
                    onCategoriesClick = { navController.navigate("categories") },
                    onSettingsClick = { navController.navigate("settings") },
                    onDebugClick = { navController.navigate("debug") },
                    debugVisible = BuildConfig.DEBUG,
                )
            }
            composable(
                route = "payment/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val paymentId = entry.arguments?.getLong("id") ?: return@composable
                val vm: PaymentDetailViewModel = viewModel(
                    key = "payment-$paymentId",
                    factory = SimpleFactory {
                        PaymentDetailViewModel(
                            paymentId = paymentId,
                            paymentRepository = container.paymentRepository,
                            categoryRepository = container.categoryRepository,
                            onCategorized = { SyncScheduler.scheduleIfEnabled(this@MainActivity) },
                        )
                    }
                )
                PaymentDetailScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable("categories") {
                val vm: CategoriesViewModel = viewModel(
                    factory = SimpleFactory { CategoriesViewModel(container.categoryRepository) }
                )
                CategoriesScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable("settings") {
                val vm: SettingsViewModel = viewModel(
                    factory = SimpleFactory { SettingsViewModel(container.settingsRepository) }
                )
                SettingsScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onChooseFromSms = { navController.navigate("senderPicker") },
                    onImportHistorical = { navController.navigate("historicalImport") },
                    onOpenUpdates = { navController.navigate("updates") },
                    onOpenQuickLaunch = { navController.navigate("quickLaunch") },
                    onDebugUnlocked = { navController.navigate("debug") },
                    versionLabel = "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                )
            }
            composable("quickLaunch") {
                val vm: com.smsexpense.tracker.ui.quicklaunch.QuickLaunchViewModel = viewModel(
                    factory = SimpleFactory {
                        com.smsexpense.tracker.ui.quicklaunch.QuickLaunchViewModel(
                            application = application,
                            settings = container.settingsRepository,
                        )
                    }
                )
                com.smsexpense.tracker.ui.quicklaunch.QuickLaunchScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("updates") {
                val vm: com.smsexpense.tracker.ui.update.UpdateViewModel = viewModel(
                    factory = SimpleFactory {
                        com.smsexpense.tracker.ui.update.UpdateViewModel(
                            application = application,
                            api = container.updateApi,
                            checkForUpdate = container.checkForUpdate,
                            currentVersionName = BuildConfig.VERSION_NAME,
                            currentVersionCode = BuildConfig.VERSION_CODE,
                        )
                    }
                )
                com.smsexpense.tracker.ui.update.UpdateScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("senderPicker") {
                val vm: com.smsexpense.tracker.ui.senderpicker.SenderPickerViewModel = viewModel(
                    factory = SimpleFactory {
                        com.smsexpense.tracker.ui.senderpicker.SenderPickerViewModel(
                            container.deviceSmsSource,
                            container.settingsRepository,
                        )
                    }
                )
                com.smsexpense.tracker.ui.senderpicker.SenderPickerScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("historicalImport") {
                val vm: com.smsexpense.tracker.ui.import_.HistoricalImportViewModel = viewModel(
                    factory = SimpleFactory {
                        com.smsexpense.tracker.ui.import_.HistoricalImportViewModel(
                            importUseCase = container.importHistoricalTransactions,
                            settingsRepository = container.settingsRepository,
                            categoryRepository = container.categoryRepository,
                            importHistoryRepository = container.importHistoryRepository,
                            onImported = { SyncScheduler.scheduleIfEnabled(this@MainActivity) },
                        )
                    }
                )
                com.smsexpense.tracker.ui.import_.HistoricalImportScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("setup") {
                val vm: com.smsexpense.tracker.ui.onboarding.SetupViewModel = viewModel(
                    factory = SimpleFactory {
                        com.smsexpense.tracker.ui.onboarding.SetupViewModel(container.settingsRepository)
                    }
                )
                com.smsexpense.tracker.ui.onboarding.SetupScreen(
                    viewModel = vm,
                    onChooseFromSms = { navController.navigate("senderPicker") },
                    onImportHistorical = { navController.navigate("historicalImport") },
                    onFinish = { navController.popBackStack("dashboard", inclusive = false) },
                )
            }
            composable("debug") {
                val vm: DebugViewModel = viewModel(
                    factory = SimpleFactory { DebugViewModel(application, container) }
                )
                DebugScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
        }
    }
}
