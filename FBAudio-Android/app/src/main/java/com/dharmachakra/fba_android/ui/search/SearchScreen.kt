package com.dharmachakra.fba_android.ui.search

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dharmachakra.fba_android.domain.model.SearchResult
import com.dharmachakra.fba_android.ui.components.CompactTalkRow
import com.dharmachakra.fba_android.ui.components.EmptyState
import com.dharmachakra.fba_android.ui.components.ErrorMessage
import com.dharmachakra.fba_android.ui.components.FbaPrimaryButton
import com.dharmachakra.fba_android.ui.components.LoadingIndicator
import com.dharmachakra.fba_android.ui.components.metaLine
import com.dharmachakra.fba_android.ui.player.PlayerViewModel
import com.dharmachakra.fba_android.ui.theme.FbaColors

private val PageMargin = 20.dp
private const val OVERVIEW_ROWS = 3

/**
 * Search (v1.0 design): field, category chips (Audio | Speakers | Transcripts | Podcasts), the
 * language row with All languages pinned, then results grouped (Talks, Series, Speakers, ...)
 * with "See all n". Talk rows have the grey play circle. Transcript and podcast search aren't in
 * the app's API yet, so those chips offer the same search on freebuddhistaudio.com.
 */
@Composable
fun SearchScreen(
    onTalkClick: (String) -> Unit,
    /** Series, speaker, place and collection results, routed by the caller. */
    onItemClick: (SearchResult) -> Unit = {},
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit = {},
    playerViewModel: PlayerViewModel? = null,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val player = playerViewModel?.uiState?.collectAsStateWithLifecycle()?.value
    val focusManager = LocalFocusManager.current

    LaunchedEffect(state.navigateToCatNum) {
        state.navigateToCatNum?.let {
            onTalkClick(it)
            viewModel.clearNavigation()
        }
    }

    fun open(result: SearchResult) = if (result.isTalk) onTalkClick(result.catNum) else onItemClick(result)
    val play: (SearchResult) -> Unit = { r ->
        if (player?.currentTalk?.catNum == r.catNum) playerViewModel?.togglePlayPause() else playerViewModel?.playTalk(r.catNum)
    }

    Scaffold(contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Text("Search", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = FbaColors.black,
                    modifier = Modifier.padding(start = PageMargin, end = PageMargin, top = 16.dp, bottom = 14.dp))
            }
            item {
                // M3 search bar: 56 high, fully rounded.
                Row(
                    Modifier.padding(horizontal = PageMargin).fillMaxWidth().height(56.dp)
                        .clip(RoundedCornerShape(28.dp)).background(FbaColors.circle).padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = FbaColors.grey700)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (state.query.isEmpty()) Text("Search talks, series, speakers", fontSize = 16.sp, color = FbaColors.meta)
                        BasicTextField(
                            value = state.query,
                            onValueChange = { viewModel.onQueryChanged(it) },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 16.sp, color = FbaColors.black),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { viewModel.search(); focusManager.clearFocus() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChanged("") }) { Icon(Icons.Default.Clear, contentDescription = "Clear search") }
                    }
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = PageMargin, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SearchCategory.entries) { c ->
                        FbaChip(c.label, selected = state.category == c) { viewModel.setCategory(c) }
                    }
                }
            }
            if (state.category == SearchCategory.AUDIO && state.languages.isNotEmpty()) item {
                // All languages stays pinned on the left; the rest scroll sideways.
                Row(Modifier.padding(start = PageMargin, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FbaChip("All languages", selected = state.language == null) { viewModel.setLanguage(null) }
                    Box(Modifier.padding(horizontal = 8.dp).width(1.dp).height(20.dp).background(FbaColors.divider))
                    LazyRow(contentPadding = PaddingValues(end = PageMargin), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.languages) { l ->
                            FbaChip(l.nativeName, selected = state.language == l.code) { viewModel.setLanguage(l.code) }
                        }
                    }
                }
            }

            when (state.category) {
                SearchCategory.TRANSCRIPTS, SearchCategory.PODCASTS -> item {
                    val transcripts = state.category == SearchCategory.TRANSCRIPTS
                    WebSearchCard(
                        text = if (transcripts) "Searching inside transcripts is coming to the app. For now, you can search them on the website."
                               else "Podcast search is coming to the app. For now, you can search the podcasts on the website.",
                        enabled = state.query.isNotBlank(),
                    ) {
                        onOpenUrl("https://www.freebuddhistaudio.com/search?s=0&r=10&b=p&q=${Uri.encode(state.query.trim())}&t=${if (transcripts) "text" else "podcast"}")
                    }
                }
                else -> when {
                    state.isLoading && state.results.isEmpty -> item { LoadingIndicator() }
                    state.error != null && state.results.isEmpty -> item { ErrorMessage(state.error!!, onRetry = { viewModel.search() }) }
                    state.hasSearched && !state.isLoading && state.results.isEmpty -> item {
                        EmptyState(if (state.query.isBlank()) "No talks in this language yet" else "No results for \"${state.query}\"")
                    }
                    else -> {
                        val sections = when {
                            state.expanded != null -> listOf(state.expanded!!)
                            state.category == SearchCategory.SPEAKERS -> listOf(SearchFilter.SPEAKERS)
                            else -> listOf(SearchFilter.TALKS, SearchFilter.SERIES, SearchFilter.SPEAKERS, SearchFilter.PLACES, SearchFilter.COLLECTIONS)
                        }.filter { state.results.of(it).isNotEmpty() }
                        val overview = state.expanded == null && sections.size > 1
                        if (state.query.isBlank() && state.language != null && sections.isNotEmpty()) item {
                            val name = state.languages.firstOrNull { it.code == state.language }?.nativeName ?: ""
                            Text("Talks in $name", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FbaColors.black,
                                modifier = Modifier.padding(horizontal = PageMargin, vertical = 8.dp))
                        }
                        for (section in sections) {
                            val all = state.results.of(section)
                            val shown = if (overview) all.take(OVERVIEW_ROWS) else all
                            if (overview || state.expanded != null) item(key = "h:$section") {
                                SectionHeader(
                                    title = section.label,
                                    seeAll = if (overview && all.size > OVERVIEW_ROWS) "See all ${all.size}" else null,
                                    back = state.expanded != null,
                                    onSeeAll = { viewModel.setExpanded(section) },
                                    onBack = { viewModel.setExpanded(null) },
                                )
                            }
                            items(shown, key = { "$section|${it.path}|${it.catNum}" }) { r ->
                                Column(Modifier.padding(horizontal = PageMargin)) {
                                    CompactTalkRow(
                                        title = r.title.replaceFirstChar { it.uppercase() },
                                        meta = r.metaLine(),
                                        imageUrl = r.imageUrl,
                                        onClick = { focusManager.clearFocus(); open(r) },
                                        onPlay = if (r.isTalk && playerViewModel != null) ({ play(r) }) else null,
                                        isCurrent = r.isTalk && player?.currentTalk?.catNum == r.catNum,
                                        isPlaying = player?.isPlaying == true,
                                    )
                                    HorizontalDivider(color = FbaColors.divider)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** M3 filter chip (radius 8); selected = page tint with page-colour text. */
@Composable
private fun FbaChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
        shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = FbaColors.circle,
            labelColor = FbaColors.black,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.primary,
        ),
        border = null,
    )
}

@Composable
private fun SectionHeader(title: String, seeAll: String?, back: Boolean, onSeeAll: () -> Unit, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = PageMargin, end = PageMargin, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FbaColors.black, modifier = Modifier.weight(1f))
        val action = if (back) "All results" else seeAll
        if (action != null) {
            Text(action, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(role = Role.Button, onClick = if (back) onBack else onSeeAll).padding(vertical = 6.dp))
        }
    }
}

@Composable
private fun WebSearchCard(text: String, enabled: Boolean, onOpen: () -> Unit) {
    Column(
        Modifier.padding(horizontal = PageMargin, vertical = 8.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, fontSize = 15.sp, lineHeight = 21.sp, color = FbaColors.black)
        if (enabled) FbaPrimaryButton("Search on freebuddhistaudio.com", onOpen)
        else Text("Type what you're looking for above.", fontSize = 14.sp, color = FbaColors.meta)
    }
}
