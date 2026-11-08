package com.dharmachakra.fba_android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dharmachakra.fba_android.ui.theme.FbaColors
import com.dharmachakra.fba_android.ui.theme.FbaDimens

/**
 * The design's filled button: 48 high, accent-deep fill (gold on remastered pages, Order blue
 * for Order-only actions, both via the page theme), bold white label. [shape] defaults to the
 * M3 fully rounded button; the talk page's Play uses radius 12 as in the Android frame.
 */
@Composable
fun FbaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    shape: Shape = RoundedCornerShape(24.dp),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = FbaColors.white, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = FbaColors.white, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** "Support FBA": the last item on scrolling pages. */
@Composable
fun SupportFbaButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    FbaPrimaryButton("Support FBA", onClick, modifier)

/**
 * A tool in the grey-circle icon row (talk page, player): 52 circle in grey-100, 24 icon,
 * 12 Medium label underneath. [content] replaces the icon (e.g. a progress ring).
 */
@Composable
fun ToolCircle(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = FbaColors.black,
    contentDescription: String = label,
    content: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(FbaDimens.toolCircle)
                .clip(CircleShape)
                .background(FbaColors.circle),
            contentAlignment = Alignment.Center,
        ) {
            if (content != null) content()
            else if (icon != null) Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF3D3D3D))
    }
}

/**
 * Text tabs under a page's description ("Tracks (n) | Related talks"): equal widths, a grey
 * hairline under the row, and a 3dp underline in the page colour under the active tab.
 */
@Composable
fun FbaTextTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(FbaColors.divider, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx())
            },
    ) {
        titles.forEachIndexed { index, title ->
            val isSelected = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .semantics { this.selected = isSelected }
                    .drawBehind {
                        if (isSelected) {
                            val h = 3.dp.toPx()
                            drawRect(accent, topLeft = Offset(0f, size.height - h), size = size.copy(height = h))
                        }
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) FbaColors.black else Color(0xFF737373),
                )
            }
        }
    }
}
