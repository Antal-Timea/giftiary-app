package app.giftiary.presentation.people

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.giftiary.presentation.components.GiftiaryAvatar
import app.giftiary.presentation.gifts.LoadedPhoto
import app.giftiary.presentation.gifts.rememberDecodedImage

/**
 * A person's avatar: their [photo], cropped into the circle, or the monogram while there is none
 * or it is not on this device. Decorative either way: the name is always written beside it.
 */
@Composable
fun PersonAvatar(
    name: String,
    photo: LoadedPhoto?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    onContext: Boolean = false,
) {
    val image = rememberDecodedImage(photo, size)
    GiftiaryAvatar(
        name = name,
        modifier = modifier,
        size = size,
        onContext = onContext,
        photo = image?.let { bitmap ->
            {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().testTag(PERSON_PHOTO_TAG),
                    contentScale = ContentScale.Crop,
                )
            }
        },
    )
}

internal const val PERSON_PHOTO_TAG = "person_photo"
