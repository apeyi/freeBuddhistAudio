package com.dharmachakra.fba_android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dharmachakra.fba_android.ui.theme.FbaColors

/**
 * "Remastered" marker used on talks and series with digitally remastered audio.
 * Gold-deep on gold-tint: white on gold is only ~2:1 contrast (an open item in the design).
 */
@Composable
fun RemasterBadge(modifier: Modifier = Modifier) {
    Text(
        text = "REMASTERED",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = FbaColors.gold.deep,
        modifier = modifier
            .background(FbaColors.gold.tint, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
