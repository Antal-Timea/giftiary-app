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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.net.toUri
import app.giftiary.R
import app.giftiary.presentation.components.GiftiarySecondaryButton
import app.giftiary.presentation.components.giftiaryDashedOutline
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * Adding a photo by taking it with the camera app or choosing it in the system photo picker, for
 * a form whose [content] calls `choose` to ask which. Neither needs a permission: the camera app
 * writes into a file this app grants it, and the picker hands over only the chosen image.
 *
 * Kept above any layout switch in the form: the launchers and the capture target are restored by
 * their place in the composition, so a camera result that arrives after a rotation still finds them.
 */
@Composable
fun GiftPhotoSources(
    onPick: (String) -> Unit,
    camera: GiftPhotoCamera,
    content: @Composable (choose: () -> Unit) -> Unit,
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
    content { choosing = true }
}

/**
 * The photo in the form: a compact dashed tile while there is none, as on the approved board;
 * once there is one, the whole photo, uncropped, with Replace and Remove under it. [hasPhoto] with
 * no [photo] is a photo that is not available right now.
 */
@Composable
fun GiftPhotoTile(
    hasPhoto: Boolean,
    photo: LoadedPhoto?,
    busy: Boolean,
    enabled: Boolean,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GiftiaryTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.s)) {
        Text(text = stringResource(R.string.gift_photo), style = GiftiaryType.label, color = colors.textLabel)
        if (!hasPhoto) {
            AddPhotoTile(busy = busy, enabled = enabled && !busy, onClick = onChoose)
            return@Column
        }
        PhotoOrPlaceholder(photo)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.s)) {
            GiftiarySecondaryButton(
                text = stringResource(R.string.gift_photo_replace),
                onClick = onChoose,
                enabled = enabled && !busy,
            )
            TextButton(
                onClick = onRemove,
                modifier = Modifier.heightIn(min = 48.dp),
                enabled = enabled && !busy,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.primaryActionText),
            ) {
                Text(stringResource(R.string.gift_photo_remove), style = GiftiaryType.actionSmall)
            }
            if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.primaryAction, strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun AddPhotoTile(busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = GiftiaryTheme.colors
    val content = if (enabled || busy) colors.primaryActionText else colors.textMuted
    Column(
        modifier = Modifier
            .size(ADD_PHOTO_TILE)
            .clip(GiftiaryShapes.input)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .giftiaryDashedOutline(content, cornerRadius = 16.dp)
            .padding(GiftiarySpacing.xs),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.primaryAction, strokeWidth = 2.dp)
        } else {
            Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp), tint = content)
        }
        Text(
            text = stringResource(R.string.gift_photo_add),
            style = GiftiaryType.overline.copy(letterSpacing = TextUnit.Unspecified),
            color = content,
            textAlign = TextAlign.Center,
        )
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
        style = GiftiaryType.body,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = GiftiarySpacing.m),
    )
}

/**
 * The whole photo, never cropped: sized to its own aspect ratio, as wide as the column allows
 * but no taller than [PHOTO_MAX_HEIGHT], so a tall portrait does not fill the screen.
 */
@Composable
private fun PhotoOrPlaceholder(photo: LoadedPhoto?) {
    val decoded = photo?.let { rememberDecoded(it) }
    // A moment while it decodes: nothing yet, rather than "not available".
    if (decoded == DecodedPhoto.Decoding) return
    val image = (decoded as? DecodedPhoto.Ready)?.image
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
                    .clip(GiftiaryShapes.photoCard),
                contentScale = ContentScale.Fit,
            )
        }
    } else {
        Text(
            text = stringResource(R.string.gift_photo_unavailable),
            style = GiftiaryType.bodySmall,
            color = GiftiaryTheme.colors.textSecondary,
        )
    }
}

/** A photo on its way to the screen: [Decoding] until its pixels are ready, or [Unreadable]. */
internal sealed interface DecodedPhoto {
    data object Decoding : DecodedPhoto
    class Ready(val image: ImageBitmap) : DecodedPhoto
    data object Unreadable : DecodedPhoto
}

/**
 * The [photo]'s pixels, decoded on a background thread rather than while composing. With a
 * [target], only as many as a box of that size shows when cropped: a 56dp thumbnail of a 1600px
 * photo decodes a quarter of it per side instead of 7 MB of pixels. Without one, the whole photo,
 * for the pages that show it whole. Decoded once per photo and size; a row that leaves the
 * composition cancels its decode.
 */
@Composable
internal fun rememberDecoded(photo: LoadedPhoto, target: Dp? = null): DecodedPhoto {
    val targetPx = target?.let { with(LocalDensity.current) { it.roundToPx() } }
    return produceState<DecodedPhoto>(DecodedPhoto.Decoding, photo, targetPx) {
        value = withContext(Dispatchers.Default) { decodePhoto(photo.bytes, targetPx) }
    }.value
}

/** The image [rememberDecoded] would show, or null while decoding or if it cannot be read. */
@Composable
internal fun rememberDecodedImage(photo: LoadedPhoto?, target: Dp? = null): ImageBitmap? =
    photo?.let { (rememberDecoded(it, target) as? DecodedPhoto.Ready)?.image }

internal fun decodePhoto(bytes: ByteArray, targetPx: Int?): DecodedPhoto {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return DecodedPhoto.Unreadable
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, targetPx) }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return DecodedPhoto.Unreadable
    return DecodedPhoto.Ready(bitmap.asImageBitmap())
}

/**
 * The largest power-of-two reduction that keeps the shorter side at least [targetPx], so a box
 * of that size, cropped, stays sharp; 1 without a target.
 */
internal fun sampleSizeFor(width: Int, height: Int, targetPx: Int?): Int {
    if (targetPx == null || targetPx <= 0) return 1
    var sample = 1
    while (minOf(width, height) / (sample * 2) >= targetPx) sample *= 2
    return sample
}

internal val PHOTO_MAX_HEIGHT = 280.dp
private val ADD_PHOTO_TILE = 84.dp
internal const val PHOTO_TAG = "gift_photo"
