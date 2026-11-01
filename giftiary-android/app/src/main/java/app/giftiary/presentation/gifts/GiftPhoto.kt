package app.giftiary.presentation.gifts

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.giftiary.R
import app.giftiary.presentation.components.GiftiarySecondaryButton
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme

/** A photo's image as read from storage; compared by identity, so a new read is a new value. */
class LoadedPhoto(val bytes: ByteArray)

/**
 * The editor's photo: add one with the system photo picker (no storage permission needed),
 * replace or remove it. [hasPhoto] with no [photo] is a photo that is not available right now.
 */
@Composable
fun GiftPhotoField(
    hasPhoto: Boolean,
    photo: LoadedPhoto?,
    busy: Boolean,
    enabled: Boolean,
    onPick: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onPick(it.toString()) }
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.s)) {
        Text(
            text = stringResource(R.string.gift_photo),
            style = MaterialTheme.typography.labelMedium,
            color = GiftiaryTheme.colors.textSecondary,
        )
        if (hasPhoto) PhotoOrPlaceholder(photo)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.s)) {
            GiftiarySecondaryButton(
                text = stringResource(if (hasPhoto) R.string.gift_photo_replace else R.string.gift_photo_add),
                onClick = pick,
                enabled = enabled && !busy,
            )
            if (hasPhoto) {
                TextButton(
                    onClick = onRemove,
                    enabled = enabled && !busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = GiftiaryTheme.colors.primaryActionText),
                ) {
                    Text(stringResource(R.string.gift_photo_remove))
                }
            }
            if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = GiftiaryTheme.colors.primaryAction, strokeWidth = 2.dp)
        }
    }
}

/** The detail screen's photo, with a quiet note while it is on this device only. */
@Composable
fun GiftPhotoView(photo: LoadedPhoto?, loading: Boolean, uploadPending: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.xs)) {
        if (!loading) PhotoOrPlaceholder(photo)
        if (uploadPending) {
            Text(
                text = stringResource(R.string.gift_photo_pending),
                style = MaterialTheme.typography.bodySmall,
                color = GiftiaryTheme.colors.textMuted,
            )
        }
    }
}

@Composable
private fun PhotoOrPlaceholder(photo: LoadedPhoto?) {
    val image = photo?.let { rememberDecoded(it) }
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = stringResource(R.string.gift_photo_description),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .clip(GiftiaryShapes.container),
            contentScale = ContentScale.Fit,
        )
    } else {
        Text(
            text = stringResource(R.string.gift_photo_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = GiftiaryTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun rememberDecoded(photo: LoadedPhoto): ImageBitmap? =
    remember(photo) { BitmapFactory.decodeByteArray(photo.bytes, 0, photo.bytes.size)?.asImageBitmap() }
