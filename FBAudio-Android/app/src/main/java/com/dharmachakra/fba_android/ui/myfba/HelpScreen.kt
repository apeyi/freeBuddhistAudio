package com.dharmachakra.fba_android.ui.myfba

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dharmachakra.fba_android.BuildConfig
import com.dharmachakra.fba_android.ui.components.ReportProblem
import com.dharmachakra.fba_android.ui.components.ReportProblemSheet
import com.dharmachakra.fba_android.ui.components.deviceDescription
import com.dharmachakra.fba_android.ui.components.sendEmail
import com.dharmachakra.fba_android.ui.player.PlayerViewModel
import com.dharmachakra.fba_android.ui.theme.FbaColors

const val HELP_PAGES_URL = "https://www.freebuddhistaudio.com/page/help-and-info"

/** Draft copy for Dharmachakra to edit (SPEC "My FBA, Help and support"). */
private val QUESTIONS = listOf(
    "How do I keep my place on another device?" to
        "Sign in on My FBA. Your listening history and the place you reached in each talk then follow you to any phone, tablet or freebuddhistaudio.com.",
    "Can I listen without a connection?" to
        "Yes. Tap Download on a talk's page and it's saved to this phone. You'll find it in My FBA, Downloads. To save mobile data, turn on Download over Wi-Fi only in Settings.",
    "How do I hear Order talks and seminar transcripts?" to
        "Order talks and Sangharakshita's seminar transcripts are for Order members. Sign in with your Triratna account and they appear in the app.",
    "A talk won't play. What can I try?" to
        "Check your connection and try again. Where a talk has a Remastered and an Original version, try the other one, or download the talk. If it still won't play, please report the problem below and we'll look into it.",
)

/** Help and support: common questions, Email us, Report a problem with a talk, the site's help pages. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    playerViewModel: PlayerViewModel? = null,
    viewModel: MyFbaViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val auth by viewModel.authState.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val player = playerViewModel?.uiState?.collectAsStateWithLifecycle()?.value
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    var pickTalk by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf<Triple<String, String, String>?>(null) } // title, speaker, catNum
    var copied by remember { mutableStateOf(false) }

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
            Text("Help and support", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = FbaColors.black,
                modifier = Modifier.padding(horizontal = PageMargin).padding(bottom = 12.dp))

            GroupHeader("Common questions")
            QUESTIONS.forEachIndexed { i, (q, a) ->
                Column(
                    Modifier.fillMaxWidth().animateContentSize()
                        .clickable(role = Role.Button) { open = if (open == i) null else i }
                        .padding(horizontal = PageMargin, vertical = 14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(q, fontSize = 17.sp, color = FbaColors.black, modifier = Modifier.weight(1f))
                        Icon(if (open == i) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null, tint = FbaColors.grey500)
                    }
                    if (open == i) Text(a, fontSize = 15.sp, lineHeight = 22.sp, color = FbaColors.black, modifier = Modifier.padding(top = 8.dp))
                }
                HorizontalDivider(color = FbaColors.divider, modifier = Modifier.padding(start = PageMargin))
            }

            GroupHeader("Get in touch")
            NavRow("Email us", ReportProblem.SUPPORT_EMAIL, {
                val email = ReportProblem.supportEmail(deviceDescription(), BuildConfig.VERSION_NAME, auth.loggedIn)
                sendEmail(context, email) { clipboard.setText(AnnotatedString(email.to)); copied = true }
            })
            if (copied) {
                Text("No email app found, so the address has been copied.", fontSize = 13.sp, color = FbaColors.meta,
                    modifier = Modifier.padding(horizontal = PageMargin, vertical = 6.dp))
            }
            NavRow("Report a problem with a talk", null, { pickTalk = true })
            NavRow("Help pages on freebuddhistaudio.com", null, { onOpenUrl(HELP_PAGES_URL) })

            Text("Emails include the app version and your device, so we can help sooner.", fontSize = 13.sp, lineHeight = 18.sp,
                color = FbaColors.meta, modifier = Modifier.padding(horizontal = PageMargin).padding(top = 16.dp))
            Text("Version ${BuildConfig.VERSION_NAME}", fontSize = 13.sp, color = FbaColors.meta,
                modifier = Modifier.padding(horizontal = PageMargin, vertical = 8.dp))
        }
    }

    if (pickTalk) {
        // Which talk? The one playing first, then recent ones.
        val playing = player?.currentTalk
        val choices = buildList {
            if (playing != null) add(Triple(playing.title, playing.speaker, playing.catNum))
            state.recentlyListened.filter { it.catNum != playing?.catNum }.take(8)
                .forEach { add(Triple(it.title, it.speaker, it.catNum)) }
        }
        ModalBottomSheet(onDismissRequest = { pickTalk = false }, containerColor = Color.White) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
                Text("Which talk?", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
                if (choices.isEmpty()) {
                    Text("Play the talk first, then report the problem from its page or here.", fontSize = 15.sp,
                        color = FbaColors.meta, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                }
                choices.forEach { c ->
                    Column(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { pickTalk = false; reporting = c }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                    ) {
                        Text(c.first, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = FbaColors.black)
                        val meta = listOfNotNull(c.second.ifBlank { null }, if (c.third == playing?.catNum) "Playing" else null).joinToString(" · ")
                        if (meta.isNotBlank()) Text(meta, fontSize = 13.sp, color = FbaColors.meta)
                    }
                }
                TextButton(onClick = { pickTalk = false }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancel") }
            }
        }
    }

    reporting?.let { (title, speaker, catNum) ->
        val position = if (player?.currentTalk?.catNum == catNum) (player.currentPosition / 1000).toInt() else null
        ReportProblemSheet(title, speaker, catNum, position, onDismiss = { reporting = null }, signedIn = auth.loggedIn)
    }
}

@Composable
internal fun GroupHeader(title: String) {
    Text(title.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = FbaColors.meta, letterSpacing = 0.5.sp,
        modifier = Modifier.padding(start = PageMargin, end = PageMargin, top = 20.dp, bottom = 4.dp))
}
