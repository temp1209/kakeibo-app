package work.temp1209.kakeibo.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@Composable
fun CameraScreen(
    contentPadding: PaddingValues,
    /** false のときプレビューを隠しカメラを unbind（タブ切替前など） */
    previewActive: Boolean = true,
    onCaptured: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var bindAttempt by remember { mutableStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val shutterInteraction = remember { MutableInteractionSource() }

    val previewView = remember { PreviewView(context) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    LaunchedEffect(previewActive, bindAttempt) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        if (!previewActive) {
            runCatching { cameraProvider.unbindAll() }
            return@LaunchedEffect
        }

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
            )
        } catch (e: Exception) {
            errorMessage = e.message ?: e.javaClass.simpleName
        }
    }

    LaunchedEffect(errorMessage) {
        val msg = errorMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = msg,
            actionLabel = "Retry",
            withDismissAction = true,
        )
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            errorMessage = null
            bindAttempt++
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                cameraProvider.unbindAll()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        SnackbarHost(hostState = snackbarHostState)

        if (previewActive) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            val ringColor = Color(0xFF1A1A1A).copy(alpha = 0.22f)
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White, CircleShape)
                    .border(width = 4.dp, color = ringColor, shape = CircleShape)
                    .semantics { contentDescription = "撮影" }
                    .clickable(
                        interactionSource = shutterInteraction,
                        indication = null,
                        enabled = previewActive,
                        onClick = {
                            errorMessage = null
                            captureToInternalFile(
                                context = context,
                                imageCapture = imageCapture,
                                onSuccess = onCaptured,
                                onError = { errorMessage = it },
                            )
                        },
                    ),
            )
        }
    }
}

private fun captureToInternalFile(
    context: Context,
    imageCapture: ImageCapture,
    onSuccess: (Uri) -> Unit,
    onError: (String) -> Unit,
) {
    val dir = File(context.filesDir, "receipts").apply { mkdirs() }
    val id = UUID.randomUUID().toString()
    val rawFile = File(dir, "${id}_raw.jpg")
    val finalFile = File(dir, "${id}.jpg")

    val outputOptions = ImageCapture.OutputFileOptions.Builder(rawFile).build()
    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                // 要件: JPEG品質85 + 長辺1600pxへリサイズして保存
                runCatching {
                    val resized = resizeJpegLongSide(
                        inputFile = rawFile,
                        maxLongSidePx = 1600,
                    )
                    saveJpeg(resized, finalFile, quality = 85)
                    rawFile.delete()
                }.onSuccess {
                    onSuccess(Uri.fromFile(finalFile))
                }.onFailure {
                    // 失敗時も掃除（容量リーク防止）
                    runCatching { rawFile.delete() }
                    runCatching { finalFile.delete() }
                    onError(it.message ?: it.javaClass.simpleName)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                onError(exception.message ?: exception.javaClass.simpleName)
            }
        }
    )
}

private fun resizeJpegLongSide(inputFile: File, maxLongSidePx: Int): Bitmap {
    val rotationDegrees = runCatching { readExifRotationDegrees(inputFile) }.getOrElse { 0 }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(inputFile.absolutePath, bounds)
    val srcW = bounds.outWidth.coerceAtLeast(1)
    val srcH = bounds.outHeight.coerceAtLeast(1)
    val (effectiveW, effectiveH) =
        if (rotationDegrees == 90 || rotationDegrees == 270) srcH to srcW else srcW to srcH
    val longSide = maxOf(effectiveW, effectiveH)
    val scale = if (longSide > maxLongSidePx) maxLongSidePx.toFloat() / longSide.toFloat() else 1f
    val dstW = (effectiveW * scale).toInt().coerceAtLeast(1)
    val dstH = (effectiveH * scale).toInt().coerceAtLeast(1)

    val decodeOpts = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = BitmapFactory.decodeFile(inputFile.absolutePath, decodeOpts)
        ?: throw IllegalStateException("Failed to decode JPEG")

    val rotated = if (rotationDegrees == 0) {
        decoded
    } else {
        Bitmap.createBitmap(
            decoded,
            0,
            0,
            decoded.width,
            decoded.height,
            Matrix().apply { postRotate(rotationDegrees.toFloat()) },
            true,
        ).also { decoded.recycle() }
    }

    return if (scale == 1f) rotated else Bitmap.createScaledBitmap(rotated, dstW, dstH, true).also { rotated.recycle() }
}

private fun saveJpeg(bitmap: Bitmap, outFile: File, quality: Int) {
    FileOutputStream(outFile).use { out ->
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
            throw IllegalStateException("Failed to compress JPEG")
        }
    }
    bitmap.recycle()
}

private fun readExifRotationDegrees(file: File): Int {
    val exif = ExifInterface(file)
    return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
}
