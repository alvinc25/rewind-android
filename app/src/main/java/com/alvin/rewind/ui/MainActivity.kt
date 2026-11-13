package com.alvin.rewind.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvin.rewind.RewindApp
import com.alvin.rewind.ui.onboarding.OnboardingScreen
import com.alvin.rewind.ui.report.ReportScreen
import com.alvin.rewind.ui.theme.RewindTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.factory((application as RewindApp).container::mainViewModel)
    }

    private val requestPostNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.onResume() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RewindTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                when (state.mode) {
                    Mode.ONBOARDING -> OnboardingScreen(
                        access = state.access,
                        onGrantUsageAccess = ::openUsageAccessSettings,
                        onGrantNotificationAccess = ::openNotificationListenerSettings,
                        onGrantPostNotifications = ::requestDigestPermission,
                        onContinue = viewModel::onContinueClicked,
                        onTryDemo = viewModel::onTryDemoClicked,
                    )
                    Mode.LIVE, Mode.DEMO -> ReportScreen(
                        state = state,
                        onPreviousWeek = viewModel::onPreviousWeekClicked,
                        onNextWeek = viewModel::onNextWeekClicked,
                        onRefresh = viewModel::onRefreshClicked,
                        onOpenSetup = viewModel::onOpenSetupClicked,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    private fun openUsageAccessSettings() {
        val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", packageName, null))
        startFirstAvailable(direct, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun openNotificationListenerSettings() {
        val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                (application as RewindApp).container.notificationListenerComponent.flattenToString(),
            )
            startFirstAvailable(detail, list)
        } else {
            startFirstAvailable(list)
        }
    }

    private fun requestDigestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startFirstAvailable(vararg intents: Intent) {
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (e: ActivityNotFoundException) {

            }
        }
    }
}
