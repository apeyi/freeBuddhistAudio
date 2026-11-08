package com.dharmachakra.fba_android.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import com.dharmachakra.fba_android.R
import com.dharmachakra.fba_android.domain.model.Talk

/**
 * An artwork image following the design rule: [url] if usable, else the "Various" Standing
 * Buddha. Pre-cut circular images are cropped to the largest rectangle of [aspect] inside the
 * circle. Callers clip to the rounded shape (radius 16 artwork, 8 thumbnails), never a circle.
 */
@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    aspect: Float = 1f,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    val fallback = painterResource(R.drawable.various)
    if (!ArtworkRule.usable(url)) {
        Image(fallback, contentDescription, modifier, contentScale = contentScale)
        return
    }
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .transformations(CircleSafeCrop(aspect))
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        placeholder = null,
        error = fallback,
        contentScale = contentScale,
        modifier = modifier,
    )
}

/** Talk artwork: banner, else speaker image, else list image, else "Various". */
@Composable
fun TalkArtwork(
    talk: Talk,
    modifier: Modifier = Modifier,
    aspect: Float = 1f,
    contentScale: ContentScale = ContentScale.Crop,
) = Artwork(
    url = ArtworkRule.talkImage(talk.bannerUrl, talk.speakerImageUrl, talk.imageUrl),
    modifier = modifier,
    aspect = aspect,
    contentScale = contentScale,
)

/** Crops pre-cut circular images (transparent or white corners) to a rectangle inside the circle. */
private class CircleSafeCrop(private val aspect: Float) : Transformation {
    override val cacheKey: String = "circle-safe-crop-$aspect"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (!ArtworkRule.looksPreCutCircle(input.width, input.height) { x, y -> input.getPixel(x, y) }) return input
        val c = ArtworkRule.inscribedCrop(input.width, input.height, aspect)
        return Bitmap.createBitmap(input, c.left, c.top, c.width, c.height)
    }
}
