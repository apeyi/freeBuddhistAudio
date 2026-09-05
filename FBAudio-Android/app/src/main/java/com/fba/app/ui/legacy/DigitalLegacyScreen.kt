package com.fba.app.ui.legacy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fba.app.data.local.AppSettings
import com.fba.app.data.repository.ContentRepository
import com.fba.app.data.repository.TalkRepository
import com.fba.app.domain.model.DigitalLegacy
import com.fba.app.ui.components.LoadingIndicator
import com.fba.app.ui.components.formatDuration
import com.fba.app.ui.components.safeFraction
import com.fba.app.ui.player.PlayerViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DigitalLegacyUiState(
    val page: DigitalLegacy? = null,
    val isLoading: Boolean = true,
    /** Title/speaker of the sample talk, once known. */
    val sampleTitle: String = "",
    val sampleSpeaker: String = "",
    /** Version chosen for the sample (before/while playing). */
    val sampleUseRemaster: Boolean = true,
)

@HiltViewModel
class DigitalLegacyViewModel @Inject constructor(
    private val content: ContentRepository,
    private val talks: TalkRepository,
    private val settings: AppSettings,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DigitalLegacyUiState())
    val uiState: StateFlow<DigitalLegacyUiState> = _uiState
    val preferRemastered: StateFlow<Boolean> = settings.preferRemastered

    init {
        viewModelScope.launch {
            val page = content.getDigitalLegacy()
            _uiState.value = _uiState.value.copy(
                page = page,
                isLoading = false,
                sampleUseRemaster = page?.sampleCatNum?.let { settings.useRemaster(it) } ?: true,
            )
            page?.sampleCatNum?.takeIf { it.isNotBlank() }?.let { catNum ->
                talks.getTalkDetail(catNum)?.let { talk ->
                    _uiState.value = _uiState.value.copy(sampleTitle = talk.title, sampleSpeaker = talk.speaker)
                }
            }
        }
    }

    /** Remember the version for the sample; the player picks it up when the sample starts. */
    fun chooseVersion(useRemaster: Boolean) {
        val catNum = _uiState.value.page?.sampleCatNum ?: return
        settings.setRemasterChoice(catNum, useRemaster)
        _uiState.value = _uiState.value.copy(sampleUseRemaster = useRemaster)
    }

    fun setPreferRemastered(value: Boolean) = settings.setPreferRemastered(value)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DigitalLegacyScreen(
    onSeriesClick: (String) -> Unit,
    onDonateClick: () -> Unit,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: DigitalLegacyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val preferRemastered by viewModel.preferRemastered.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("The Digital Legacy") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingIndicator(Modifier.padding(padding))
            return@Scaffold
        }
        val legacy = state.page
        val sample = legacy?.sampleCatNum.orEmpty()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                legacy?.description
                    ?: "Since 1967 the team at Dharmachakra has been sharing Sangharakshita's talks with the world. " +
                        "To celebrate 20 years of Free Buddhist Audio we are digitally remastering all of his talks " +
                        "for greatly enhanced listening.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(20.dp))

            // --- Hear the difference: inline A/B player on the sample talk ---
            if (sample.isNotBlank()) {
                val isSamplePlaying = playerState.currentTalk?.catNum == sample
                val useRemaster = if (isSamplePlaying) playerState.useRemaster else state.sampleUseRemaster
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Hear the difference", style = MaterialTheme.typography.titleMedium)
                        if (state.sampleTitle.isNotBlank()) {
                            Text(
                                listOf(state.sampleTitle, state.sampleSpeaker).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Original first, then Remastered — the same order as the player.
                            // Switching while the sample plays swaps the audio at the same position.
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                                SegmentedButton(
                                    selected = !useRemaster,
                                    onClick = {
                                        viewModel.chooseVersion(false)
                                        if (isSamplePlaying) playerViewModel.setUseRemaster(false)
                                    },
                                    enabled = !(isSamplePlaying && playerState.versionLocked),
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                ) { Text("Original") }
                                SegmentedButton(
                                    selected = useRemaster,
                                    onClick = {
                                        viewModel.chooseVersion(true)
                                        if (isSamplePlaying) playerViewModel.setUseRemaster(true)
                                    },
                                    enabled = !(isSamplePlaying && playerState.versionLocked),
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                ) { Text("Remastered") }
                            }
                            Spacer(Modifier.width(12.dp))
                            IconButton(
                                onClick = {
                                    if (isSamplePlaying) playerViewModel.togglePlayPause()
                                    else playerViewModel.playTalk(sample)
                                },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    if (isSamplePlaying && playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isSamplePlaying && playerState.isPlaying) "Pause" else "Play sample",
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        if (isSamplePlaying && playerState.duration > 0) {
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { (playerState.currentPosition.toFloat() / playerState.duration).safeFraction() },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${formatDuration((playerState.currentPosition / 1000).toInt())} / ${formatDuration((playerState.duration / 1000).toInt())}" +
                                    "  ·  ${if (useRemaster) "remastered" else "original"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // --- Default for all talks (same setting as in My FBA → Settings) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Prefer remastered audio", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Play the remastered version whenever a talk has one",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = preferRemastered, onCheckedChange = { viewModel.setPreferRemastered(it) })
            }
            Spacer(Modifier.height(20.dp))

            OutlinedButton(
                onClick = { onSeriesClick(legacy?.seriesPath ?: "/series/details?num=X16") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Listen: Buddhism for Today – and Tomorrow") }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onDonateClick, modifier = Modifier.fillMaxWidth()) {
                Text("Support the Digital Legacy")
            }
        }
    }
}
