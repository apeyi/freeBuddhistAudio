package com.fba.app.ui.legacy

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fba.app.data.local.AppSettings
import com.fba.app.data.repository.ContentRepository
import com.fba.app.domain.model.DigitalLegacy
import com.fba.app.ui.components.LoadingIndicator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DigitalLegacyViewModel @Inject constructor(
    private val content: ContentRepository,
    private val settings: AppSettings,
) : ViewModel() {
    private val _page = MutableStateFlow<DigitalLegacy?>(null)
    val page: StateFlow<DigitalLegacy?> = _page
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    init {
        viewModelScope.launch {
            _page.value = content.getDigitalLegacy()
            _loading.value = false
        }
    }

    /** The sample plays in the normal player; pick the version before starting it. */
    fun chooseVersion(catNum: String, remastered: Boolean) = settings.setRemasterChoice(catNum, remastered)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DigitalLegacyScreen(
    onPlaySample: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    onDonateClick: () -> Unit,
    onBack: () -> Unit,
    viewModel: DigitalLegacyViewModel = hiltViewModel(),
) {
    val page by viewModel.page.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

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
        if (loading) {
            LoadingIndicator(Modifier.padding(padding))
            return@Scaffold
        }
        val legacy = page
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

            val sample = legacy?.sampleCatNum.orEmpty()
            if (sample.isNotBlank()) {
                Text("Hear the difference", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { viewModel.chooseVersion(sample, remastered = false); onPlaySample(sample) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Original") }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = { viewModel.chooseVersion(sample, remastered = true); onPlaySample(sample) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Remastered") }
                }
                Spacer(Modifier.height(20.dp))
            }

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
