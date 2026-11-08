package com.dharmachakra.fba_android.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dharmachakra.fba_android.R

/**
 * Draws a generated tile (SPEC "Tile artwork"): (1) the main colour blending into its partner
 * along the diagonal, (2) a broad glow of the third colour bottom right, (3) the main colour
 * again, lightly, bottom left behind the title, (4) a broad soft white wash placed clear of the
 * symbol and title, then one white symbol at 22% and the title. Mirrors iOS `TileArtwork`.
 */
@Composable
fun TileArtwork(
    colors: TileArt.Colors,
    modifier: Modifier = Modifier,
    symbol: TileArt.Symbol? = null,
    wash: TileArt.Wash = TileArt.WASHES[0],
    title: String? = null,
    caption: String? = null,
) {
    BoxWithConstraints(modifier.drawBehind { drawTile(colors, wash) }) {
        if (symbol != null) {
            val h = maxHeight * TileArt.SYMBOL_HEIGHT
            Image(
                painterResource(symbolRes(symbol)),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(h)
                    .offset(x = maxWidth * TileArt.SYMBOL_CENTRE_X - h / 2, y = maxHeight * TileArt.SYMBOL_CENTRE_Y - h / 2)
                    .alpha(TileArt.SYMBOL_ALPHA),
            )
        }
        if (caption != null) {
            Text(caption, fontSize = 11.sp, color = Color.White, modifier = Modifier.align(Alignment.TopStart).padding(14.dp))
        }
        if (title != null) {
            Text(
                title,
                fontSize = 16.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            )
        }
    }
}

private fun symbolRes(symbol: TileArt.Symbol) = when (symbol) {
    TileArt.Symbol.WHEEL -> R.drawable.tile_symbol_wheel
    TileArt.Symbol.LOTUS -> R.drawable.tile_symbol_lotus
    TileArt.Symbol.VIHARA -> R.drawable.tile_symbol_vihara
    TileArt.Symbol.BUDDHA -> R.drawable.tile_symbol_buddha
}

private fun DrawScope.drawTile(c: TileArt.Colors, wash: TileArt.Wash) {
    val main = Color(c.main)
    val partner = Color(c.partner)
    val glow = Color(c.glow)
    drawRect(
        Brush.linearGradient(
            0.143f to main, 0.571f to partner, 0.857f to partner,
            start = Offset.Zero, end = Offset(size.width, size.height),
        ),
    )
    // Elliptical glows: drawn in unit space scaled to the tile, as in the Figma gradients.
    fun glowAt(cx: Float, cy: Float, r: Float, vararg stops: Pair<Float, Color>) {
        withTransform({ scale(size.width, size.height, pivot = Offset.Zero) }) {
            drawRect(Brush.radialGradient(*stops, center = Offset(cx, cy), radius = r), size = Size(1f, 1f))
        }
    }
    glowAt(1f, 0.95f, 0.70f, 0f to glow, 0.35f to glow.copy(alpha = 0.85f), 0.7f to glow.copy(alpha = 0.35f), 1f to Color.Transparent)
    glowAt(0f, 1f, 0.45f, 0f to main.copy(alpha = 0.7f), 0.5f to main.copy(alpha = 0.35f), 1f to Color.Transparent)
    glowAt(wash.x, wash.y, 0.675f,
        0f to Color.White.copy(alpha = 0.85f), 0.3f to Color.White.copy(alpha = 0.765f),
        0.6f to Color.White.copy(alpha = 0.3825f), 1f to Color.Transparent)
}
