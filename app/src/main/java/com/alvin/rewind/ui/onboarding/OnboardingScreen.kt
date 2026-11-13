package com.alvin.rewind.ui.onboarding

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alvin.rewind.domain.access.AccessStatus
import com.alvin.rewind.ui.theme.RewindTheme

/**
 * Permissions are the product's main friction, so each one is explained by what it unlocks,
 * only usage access is required, and "explore with sample data" lets people decide before granting.
 */
@Composable
fun OnboardingScreen(
    access: AccessStatus,
    onGrantUsageAccess: () -> Unit,
    onGrantNotificationAccess: () -> Unit,
    onGrantPostNotifications: () -> Unit,
    onContinue: () -> Unit,
    onTryDemo: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Rewind", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(
                "A weekly look back at what you did on your phone: where your time went, " +
                    "what interrupted you, and what changed.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(
                    "🔒 Everything stays on this phone. Rewind has no internet permission, " +
                        "never reads notification content, and keeps about 3 months of history.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            PermissionCard(
                title = "Usage access",
                badge = "Required",
                description = "Screen time per app, how often you open apps, unlocks, and the hours you use your phone.",
                granted = access.usageAccess,
                onGrant = onGrantUsageAccess,
            )
            PermissionCard(
                title = "Notification access",
                badge = "Optional",
                description = "Count notifications per app. Only the app name and time are recorded.",
                granted = access.notificationAccess,
                onGrant = onGrantNotificationAccess,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionCard(
                    title = "Weekly digest",
                    badge = "Optional",
                    description = "A single notification on Sunday evening when your week is ready.",
                    granted = access.postNotifications,
                    onGrant = onGrantPostNotifications,
                )
            }

            Spacer(Modifier.height(8.dp))
            Button(onClick = onContinue, enabled = access.usageAccess, modifier = Modifier.fillMaxWidth()) {
                Text(if (access.usageAccess) "See my week" else "Grant usage access to continue")
            }
            TextButton(onClick = onTryDemo, modifier = Modifier.fillMaxWidth()) {
                Text("Explore with sample data")
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    badge: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(badge, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium)
            if (granted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("  Granted", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            } else {
                FilledTonalButton(onClick = onGrant) { Text("Allow") }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingPreview() {
    RewindTheme {
        OnboardingScreen(AccessStatus(usageAccess = true), {}, {}, {}, {}, {})
    }
}
