package work.temp1209.kakeibo.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@Composable
fun CameraScreen(
    contentPadding: PaddingValues,
    onCaptured: (Uri) -> Unit,
    onOpenList: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val previewView = remember { PreviewView(context) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    LaunchedEffect(Unit) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
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
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
        )

        if (errorMessage != null) {
            Text(
                text = "Camera error: $errorMessage",
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(onClick = { onOpenList() }) {
            Text("一覧を開く")
        }

        Button(
            onClick = {
                captureToInternalFile(
                    context = context,
                    imageCapture = imageCapture,
                    onSuccess = onCaptured,
                    onError = { errorMessage = it },
                )
            }
        ) {
            Text("撮影")
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
    val rawFile = File(dir, "${UUID.randomUUID()}_raw.jpg")
    val finalFile = File(dir, "${UUID.randomUUID()}.jpg")

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
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(inputFile.absolutePath, bounds)
    val srcW = bounds.outWidth.coerceAtLeast(1)
    val srcH = bounds.outHeight.coerceAtLeast(1)
    val longSide = maxOf(srcW, srcH)
    val scale = if (longSide > maxLongSidePx) maxLongSidePx.toFloat() / longSide.toFloat() else 1f
    val dstW = (srcW * scale).toInt().coerceAtLeast(1)
    val dstH = (srcH * scale).toInt().coerceAtLeast(1)

    val decodeOpts = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val src = BitmapFactory.decodeFile(inputFile.absolutePath, decodeOpts)
        ?: throw IllegalStateException("Failed to decode JPEG")
    return if (scale == 1f) src else Bitmap.createScaledBitmap(src, dstW, dstH, true).also { src.recycle() }
}

private fun saveJpeg(bitmap: Bitmap, outFile: File, quality: Int) {
    FileOutputStream(outFile).use { out ->
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
            throw IllegalStateException("Failed to compress JPEG")
        }
    }
    bitmap.recycle()
}

