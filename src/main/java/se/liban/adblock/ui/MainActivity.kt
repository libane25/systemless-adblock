package se.liban.adblock.ui

import android.Manifest
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import se.liban.adblock.ui.theme.AdBlockPalette
import se.liban.adblock.ui.theme.SystemlessAdBlockTheme
import se.liban.adblock.ui.theme.ThemeMode
import se.liban.adblock.vpn.AdBlockVpnService

class MainActivity : AppCompatActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        launchVpnConsent()
    }

    private val vpnConsent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            AdBlockVpnService.start(this)
        } else {
            Toast.makeText(this, "VPN permission denied", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRoot(onRequestVpn = ::beginVpn)
        }
    }

    private fun beginVpn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            launchVpnConsent()
        }
    }

    private fun launchVpnConsent() {
        val prepare: Intent? = VpnService.prepare(this)
        if (prepare == null) {
            AdBlockVpnService.start(this)
        } else {
            vpnConsent.launch(prepare)
        }
    }
}

@Composable
private fun AppRoot(
    onRequestVpn: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as ComponentActivity
    val dark = settings.themeMode.resolveDark(isSystemInDarkTheme())

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.vpnRequests.collect { onRequestVpn() }
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onAppResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(settings.themeMode, dark) {
        val transparent = android.graphics.Color.TRANSPARENT
        if (settings.themeMode == ThemeMode.SYSTEM) {
            activity.enableEdgeToEdge()
        } else if (dark) {
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(transparent),
                navigationBarStyle = SystemBarStyle.dark(transparent)
            )
        } else {
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(transparent, transparent),
                navigationBarStyle = SystemBarStyle.light(transparent, transparent)
            )
        }
        activity.window.decorView.setBackgroundColor(
            if (dark) AdBlockPalette.DARK_BACKGROUND.toInt() else AdBlockPalette.LIGHT_BACKGROUND.toInt()
        )
    }

    SystemlessAdBlockTheme(themeMode = settings.themeMode) {
        BackHandler(enabled = destination != AppDestination.HOME) {
            viewModel.goBack()
        }
        when (destination) {
            AppDestination.HOME -> AdBlockScreen(
                state = state,
                onEnable = viewModel::enableOrUpdate,
                onRevert = viewModel::revertToDefault,
                onMode = viewModel::setMode,
                onOpenSettings = viewModel::openSettings
            )
            AppDestination.SETTINGS -> SettingsScreen(
                state = state,
                settings = settings,
                onBack = viewModel::goBack,
                onTheme = viewModel::setThemeMode,
                onSettings = viewModel::updateSettings,
                onOpenLicenses = viewModel::openLicenses
            )
            AppDestination.LICENSES -> LicensesScreen(onBack = viewModel::goBack)
        }
    }
}
