package com.fba.app.ui.join

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val benefits = listOf(
    "Support us keeping over 7,000 Dharma talks available to all",
    "Support the development of this app and help us reach even more people",
    "Access to downloads — take any talk or series with you on retreat or on the road",
    "Access to transcript search, as it becomes available",
)

/**
 * Membership page. Purchases go through the app stores' subscription systems;
 * until FBA has set those up the buttons explain that subscriptions open at launch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinScreen(
    onDonateClick: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Join") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("Join Free Buddhist Audio", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Free Buddhist Audio is free for everyone, and always will be. Members help us keep it that way — and get a little extra in return.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(20.dp))
            Text("Benefits of joining", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            for (benefit in benefits) {
                Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.height(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(benefit, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Join monthly") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Join yearly") }
            Spacer(Modifier.height(8.dp))
            Text(
                "Subscriptions open at launch.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Text("Prefer to give once?", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onDonateClick, modifier = Modifier.fillMaxWidth()) { Text("Donate") }
        }
    }
}
