package com.dharmachakra.fba_android.ui.myfba

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dharmachakra.fba_android.BuildConfig
import com.dharmachakra.fba_android.ui.theme.FbaColors

/**
 * Settings (SPEC "Settings"): Playback (Download over Wi-Fi only), Talks (Talk languages),
 * Listening history (Clear listening history), Storage (Downloads, Remove all downloads) and
 * About (Contact support, Version). No remastered setting: the switch is on each remastered talk.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onDownloadsClick: () -> Unit = {},
    onContactSupport: () -> Unit = {},
    viewModel: MyFbaViewModel = hiltViewModel(),
) {
    val englishOnly by viewModel.englishOnly.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.wifiOnlyDownloads.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val auth by viewModel.authState.collectAsStateWithLifecycle()
    var languages by remember { mutableStateOf(false) }
    var clearHistory by remember { mutableStateOf(false) }
    var removeDownloads by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.White,
        topBar = {
            Row(Modifier.fillMaxWidth().height(54.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = FbaColors.black)
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = FbaColors.black,
                modifier = Modifier.padding(horizontal = PageMargin))

            GroupHeader("Playback")
            SwitchRow("Download over Wi-Fi only", wifiOnly) { viewModel.setWifiOnlyDownloads(it) }
            Footnote("Downloads wait for Wi-Fi, so they don't use mobile data.")

            GroupHeader("Talks")
            NavRow("Talk languages", if (englishOnly) "English" else "All languages", { languages = true })

            GroupHeader("Listening history")
            NavRow("Clear listening history", null, { clearHistory = true }, titleColor = FbaColors.destructive)
            Footnote("Removes the talks you've played and your place in them from this phone.")

            GroupHeader("Storage")
            NavRow("Downloads", MyFbaRules.formatBytes(state.downloadBytes), onDownloadsClick)
            if (state.downloadCount > 0) {
                NavRow("Remove all downloads", null, { removeDownloads = true }, titleColor = FbaColors.destructive)
            }

            GroupHeader("About")
            NavRow("Contact support", null, onContactSupport)
            Row(Modifier.fillMaxWidth().padding(horizontal = PageMargin, vertical = 14.dp)) {
                Text("Version", fontSize = 17.sp, color = FbaColors.black, modifier = Modifier.weight(1f))
                Text(BuildConfig.VERSION_NAME, fontSize = 15.sp, color = FbaColors.meta)
            }
        }
    }

    if (languages) {
        ModalBottomSheet(onDismissRequest = { languages = false }, containerColor = Color.White) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                Text("Talk languages", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
                listOf(true to "English", false to "All languages").forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { viewModel.setEnglishOnly(value); languages = false }
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = englishOnly == value, onClick = null, modifier = Modifier.padding(12.dp))
                        Text(label, fontSize = 17.sp)
                    }
                }
                Text(
                    "Sets which talks appear in New on FBA, Popular and search results. Any language can still be browsed from Explore.",
                    fontSize = 13.sp, lineHeight = 18.sp, color = FbaColors.meta, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
    }

    if (clearHistory) {
        // Action sheet (SPEC): clear on this phone; when signed in, also from the FBA account; Cancel.
        ModalBottomSheet(onDismissRequest = { clearHistory = false }, containerColor = Color.White) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
                Text("Clear listening history?", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
                Text("This can't be undone.", fontSize = 15.sp, color = FbaColors.meta, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
                SheetAction("Clear on this phone", FbaColors.destructive) { viewModel.clearHistory(alsoAccount = false); clearHistory = false }
                if (auth.loggedIn) {
                    SheetAction("Also clear from my FBA account", FbaColors.destructive) { viewModel.clearHistory(alsoAccount = true); clearHistory = false }
                }
                SheetAction("Cancel", FbaColors.black) { clearHistory = false }
            }
        }
    }

    if (removeDownloads) {
        AlertDialog(
            onDismissRequest = { removeDownloads = false },
            title = { Text("Remove all downloads?") },
            text = { Text("${MyFbaRules.downloadsSummary(state.downloadCount, state.downloadBytes)} will be removed from this phone. You can download them again at any time.") },
            confirmButton = {
                TextButton(onClick = { viewModel.removeAllDownloads(); removeDownloads = false }) {
                    Text("Remove", color = FbaColors.destructive)
                }
            },
            dismissButton = { TextButton(onClick = { removeDownloads = false }) { Text("Cancel") } },
            containerColor = Color.White,
        )
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.padding(horizontal = PageMargin, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 17.sp, color = FbaColors.black, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = null)
        }
        HorizontalDivider(color = FbaColors.divider, modifier = Modifier.padding(start = PageMargin))
    }
}

@Composable
private fun Footnote(text: String) {
    Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = FbaColors.meta,
        modifier = Modifier.padding(horizontal = PageMargin).padding(top = 6.dp))
}

@Composable
private fun SheetAction(label: String, color: Color, onClick: () -> Unit) {
    Text(label, fontSize = 17.sp, color = color, modifier = Modifier.fillMaxWidth()
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp))
}
