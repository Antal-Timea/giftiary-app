package app.giftiary.presentation.gifts

import android.content.ActivityNotFoundException
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.net.toUri
import app.giftiary.R
import app.giftiary.presentation.components.GiftiarySecondaryButton
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme

/** A photo's image as read from storage; compared by identity, so a new read is a new value. */
class LoadedPhoto(val bytes: ByteArray)

/**
 * Taking a photo with the system camera app: [newTarget] gives the content URI it writes into
 * (null if there is none), and exactly one of the others is called with that URI afterwards.
 */
class GiftPhotoCamera(
    val newTarget: () -> String?,
    val onCaptured: (String) -> Unit,
    val onCancelled: (String) -> Unit,
    val onUnavailable: (String) -> Unit,
)

/**
 * The editor's photo: add one by taking it with the camera app or choosing it in the system
 * photo picker, replace or remove it. Neither needs a permission: the camera app writes into a
 * file this app grants it, and the picker hands over only the chosen image. [hasPhoto] with no
 * [photo] is a photo that is not available right now.
 */
@Composable
fun GiftPhotoField(
    hasPhoto: Boolean,
    photo: LoadedPhoto?,
    busy: Boolean,
    enabled: Boolean,
    onPick: (String) -> Unit,
    camera: GiftPhotoCamera,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onPick(it.toString()) }
    }
    // Saved, so a result that arrives after the process was recreated still finds its file.
    var captureTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraApp = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val target = captureTarget ?: return@rememberLauncherForActivityResult
        captureTarget = null
        if (taken) camera.onCaptured(target) else camera.onCancelled(target)
    }
    var choosing by rememberSaveable { mutableStateOf(false) }
    if (choosing) {
        PhotoSourceDialog(
            onTakePhoto = {
                choosing = false
                val target = camera.newTarget() ?: return@PhotoSourceDialog
                captureTarget = target
                try {
                    cameraApp.launch(target.toUri())
                } catch (_: ActivityNotFoundException) {
                    captureTarget = null
                    camera.onUnavailable(target)
                }
            },
            onChooseFromGallery = {
                choosing = false
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onDismiss = { choosing = false },
        )
    }
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
                onClick = { choosing = true },
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

@Composable
private fun PhotoSourceDialog(onTakePhoto: () -> Unit, onChooseFromGallery: () -> Unit, onDismiss: () -> Unit) {
    val colors = GiftiaryTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gift_photo)) },
        text = {
            Column {
                PhotoSource(stringResource(R.string.gift_photo_take), onTakePhoto)
                PhotoSource(stringResource(R.string.gift_photo_choose), onChooseFromGallery)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = colors.textSecondary)) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        containerColor = colors.surfaceElevated,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textPrimary,
    )
}

@Composable
private fun PhotoSource(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = GiftiarySpacing.m),
    )
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

/**
 * The whole photo, never cropped: sized to its own aspect ratio, as wide as the column allows
 * but no taller than [PHOTO_MAX_HEIGHT], so a tall portrait does not fill the screen.
 */
@Composable
private fun PhotoOrPlaceholder(photo: LoadedPhoto?) {
    val image = photo?.let { rememberDecoded(it) }
    if (image != null) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val ratio = image.width.toFloat() / image.height
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.gift_photo_description),
                modifier = Modifier
                    .testTag(PHOTO_TAG)
                    .width(min(maxWidth, PHOTO_MAX_HEIGHT * ratio))
                    .aspectRatio(ratio)
                    .clip(GiftiaryShapes.container),
                contentScale = ContentScale.Fit,
            )
        }
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

internal val PHOTO_MAX_HEIGHT = 360.dp
internal const val PHOTO_TAG = "gift_photo"
