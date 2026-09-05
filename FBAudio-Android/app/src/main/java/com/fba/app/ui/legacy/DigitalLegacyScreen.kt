package com.fba.app.ui.legacy

import android.content.Context
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DigitalLegacyUiState(
    val page: DigitalLegacy? = null,
    val isLoading: Boolean = true,
    /** Title/speaker/chapter of the sample, once known. */
    val sampleTitle: String = "",
    val sampleSpeaker: String = "",
    val sampleChapter: String = "",
    /** Both versions of the sample chapter are prepared. */
    val sampleReady: Boolean = false,
    val useRemaster: Boolean = true,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

@HiltViewModel
class DigitalLegacyViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val content: ContentRepository,
    private val talks: TalkRepository,
    private val settings: AppSettings,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DigitalLegacyUiState())
    val uiState: StateFlow<DigitalLegacyUiState> = _uiState
    val preferRemastered: StateFlow<Boolean> = settings.preferRemastered

    private val sample = SamplePlayer(context)
    private var ticker: Job? = null

    init {
        viewModelScope.launch {
            val page = content.getDigitalLegacy()
            _uiState.value = _uiState.value.copy(page = page, isLoading = false)
            val catNum = page?.sampleCatNum?.takeIf { it.isNotBlank() } ?: return@launch
            val talk = talks.getTalkDetail(catNum) ?: return@launch
            // Use the first chapter that exists in both versions; prepare both right away.
            val chapter = talk.tracks.firstOrNull { it.hasRemaster } ?: return@launch
            val startRemastered = settings.preferRemastered.value
            sample.load(chapter.audioUrl, chapter.remasterAudioUrl, startRemastered)
            _uiState.value = _uiState.value.copy(
                sampleTitle = talk.title,
                sampleSpeaker = talk.speaker,
                sampleChapter = chapter.title,
                sampleReady = true,
                useRemaster = startRemastered,
                durationMs = chapter.durationSeconds * 1000L,
            )
            startTicker()
        }
    }

    /** Swap the audible version; instant because both are already playing/buffered. */
    fun setVersion(useRemaster: Boolean) {
        sample.setVersion(useRemaster)
        _uiState.value = _uiState.value.copy(useRemaster = useRemaster)
    }

    fun togglePlayPause() {
        sample.togglePlayPause()
        tick()
    }

    fun seekToFraction(fraction: Float) {
        val duration = _uiState.value.durationMs.takeIf { it > 0 } ?: return
        sample.seekTo((fraction.safeFraction() * duration).toLong())
        tick()
    }

    /** The sample is a demo: stop it when the screen goes away. */
    fun stopSample() = sample.pause()

    fun setPreferRemastered(value: Boolean) = settings.setPreferRemastered(value)

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            var n = 0
            while (isActive) {
                tick()
                if (++n % 10 == 0) sample.resync()
                delay(500)
            }
        }
    }

    private fun tick() {
        _uiState.value = _uiState.value.copy(
            isPlaying = sample.isPlaying,
            isBuffering = sample.isBuffering,
            positionMs = sample.positionMs,
            durationMs = sample.durationMs.takeIf { it > 0 } ?: _uiState.value.durationMs,
        )
    }

    override fun onCleared() {
        ticker?.cancel()
        sample.release()
        super.onCleared()
    }
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

    // The sample stops when the page is left
    DisposableEffect(Unit) { onDispose { viewModel.stopSample() } }

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

            // --- Hear the difference: A/B player with both versions preloaded ---
            if (!legacy?.sampleCatNum.isNullOrBlank()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Hear the difference", style = MaterialTheme.typography.titleMedium)
                        val subtitle = listOf(state.sampleTitle, state.sampleChapter, state.sampleSpeaker)
                            .filter { it.isNotBlank() }.joinToString(" · ")
                        if (subtitle.isNotBlank()) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Original first, then Remastered — the same order as the player.
                            // Both versions play in step, so this swaps the sound instantly.
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                                SegmentedButton(
                                    selected = !state.useRemaster,
                                    onClick = { viewModel.setVersion(false) },
                                    enabled = state.sampleReady,
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                ) { Text("Original") }
                                SegmentedButton(
                                    selected = state.useRemaster,
                                    onClick = { viewModel.setVersion(true) },
                                    enabled = state.sampleReady,
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                ) { Text("Remastered") }
                            }
                            Spacer(Modifier.width(12.dp))
                            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                IconButton(
                                    onClick = {
                                        // One thing at a time: the demo pauses the main player
                                        if (!state.isPlaying) playerViewModel.pause()
                                        viewModel.togglePlayPause()
                                    },
                                    enabled = state.sampleReady,
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (state.isPlaying) "Pause" else "Play sample",
                                        modifier = Modifier.size(32.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (!state.sampleReady || state.isBuffering) {
                                    CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = (if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f).safeFraction(),
                            onValueChange = { viewModel.seekToFraction(it) },
                            enabled = state.sampleReady,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "${formatDuration((state.positionMs / 1000).toInt())} / ${formatDuration((state.durationMs / 1000).toInt())}" +
                                "  ·  ${if (state.useRemaster) "remastered" else "original"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
