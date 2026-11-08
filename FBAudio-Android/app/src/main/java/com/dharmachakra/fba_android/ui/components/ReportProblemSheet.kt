package com.dharmachakra.fba_android.ui.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.dharmachakra.fba_android.BuildConfig
import com.dharmachakra.fba_android.domain.model.Talk
import com.dharmachakra.fba_android.ui.theme.FbaColors

/**
 * Report a problem (talk page More, player More): the talk, then the reasons; each opens an email
 * to support with the talk, its number, the playback position and the device filled in. Without
 * an email app the address is copied instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportProblemSheet(talk: Talk, positionSeconds: Int?, onDismiss: () -> Unit, signedIn: Boolean = false) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Text("Report a problem", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
            Text("${talk.title} · ${talk.speaker}", fontSize = 14.sp, color = FbaColors.meta,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
            ReportProblem.reasons.forEach { reason ->
                ListItem(
                    headlineContent = { Text(reason) },
                    modifier = Modifier.clickable(role = Role.Button) {
                        val email = ReportProblem.email(
                            reason = reason, talkTitle = talk.title, catNum = talk.catNum,
                            positionSeconds = positionSeconds,
                            device = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}",
                            appVersion = BuildConfig.VERSION_NAME, signedIn = signedIn,
                        )
                        val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                            putExtra(Intent.EXTRA_EMAIL, arrayOf(email.to))
                            putExtra(Intent.EXTRA_SUBJECT, email.subject)
                            putExtra(Intent.EXTRA_TEXT, email.body)
                        }
                        runCatching { context.startActivity(mail) }.onFailure { clipboard.setText(AnnotatedString(email.to)) }
                        onDismiss()
                    },
                )
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancel") }
        }
    }
}
