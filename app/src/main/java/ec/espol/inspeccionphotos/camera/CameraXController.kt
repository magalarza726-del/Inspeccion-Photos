package ec.espol.inspeccionphotos.camera

import android.content.Context
import android.view.Surface
import android.view.OrientationEventListener
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File

enum class CameraXMode { PHOTO, VIDEO }

class CameraXController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val mode: CameraXMode
) {
    private var provider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    @Volatile private var released = false
    // Rotación física del dispositivo (independiente del bloqueo de rotación de pantalla).
    private var deviceRotation: Int? = null
    private var orientationListener: OrientationEventListener? = null

    fun bind(onReady: (Boolean, String?) -> Unit = { _, _ -> }) {
        released = false
        orientationListener?.disable()
        orientationListener = null
        if (mode == CameraXMode.PHOTO) {
            orientationListener = object : OrientationEventListener(context) {
                override fun onOrientationChanged(degrees: Int) {
                    if (released || degrees == ORIENTATION_UNKNOWN) return
                    // Correspondencia oficial CameraX: el sensor reporta grados horarios,
                    // mientras Surface usa la rotación necesaria para volver a la posición natural.
                    val rotation = when (degrees) {
                        in 45 until 135 -> Surface.ROTATION_270
                        in 135 until 225 -> Surface.ROTATION_180
                        in 225 until 315 -> Surface.ROTATION_90
                        else -> Surface.ROTATION_0
                    }
                    if (deviceRotation != rotation) {
                        deviceRotation = rotation
                        imageCapture?.targetRotation = rotation
                    }
                }
            }.also { if (it.canDetectOrientation()) it.enable() }
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val cameraProvider = future.get()
                if (released) {
                    cameraProvider.unbindAll()
                    error("Cámara cerrada")
                }
                val targetRotation = deviceRotation ?: previewView.display?.rotation ?: Surface.ROTATION_0
                val preview = Preview.Builder()
                    .setTargetRotation(targetRotation)
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }
                cameraProvider.unbindAll()
                when (mode) {
                    CameraXMode.PHOTO -> {
                        val still = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .setTargetRotation(targetRotation)
                            .build()
                        cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, still)
                        imageCapture = still
                        videoCapture = null
                    }
                    CameraXMode.VIDEO -> {
                        // Recorder's default QualitySelector is compatibility-oriented:
                        // FHD -> HD -> SD with an open fallback around FHD.
                        val recorder = Recorder.Builder().build()
                        val video = VideoCapture.withOutput(recorder)
                        cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, video)
                        imageCapture = null
                        videoCapture = video
                    }
                }
                provider = cameraProvider
            }.onSuccess { onReady(true, null) }
                .onFailure { onReady(false, it.message) }
        }, ContextCompat.getMainExecutor(context))
    }

    fun takePhoto(file: File, onResult: (Result<File>) -> Unit) {
        if (released) return onResult(Result.failure(IllegalStateException("Cámara cerrada")))
        val capture = imageCapture ?: return onResult(Result.failure(IllegalStateException("Cámara todavía no está lista")))
        // El sensor prevalece sobre la rotación visible de pantalla. Así se registra
        // la orientación correcta en EXIF incluso con rotación automática bloqueada.
        capture.targetRotation = deviceRotation ?: previewView.display?.rotation ?: capture.targetRotation
        file.parentFile?.mkdirs()
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) = onResult(Result.success(file))
            override fun onError(exception: ImageCaptureException) = onResult(Result.failure(exception))
        })
    }

    fun startVideo(file: File, onEvent: (VideoRecordEvent) -> Unit): Result<Unit> = runCatching {
        check(!released) { "Cámara cerrada" }
        check(recording == null) { "Ya existe una grabación activa" }
        val output = videoCapture?.output ?: error("Cámara todavía no está lista")
        file.parentFile?.mkdirs()
        recording = output.prepareRecording(context, FileOutputOptions.Builder(file).build())
            .start(ContextCompat.getMainExecutor(context)) { event ->
                onEvent(event)
                if (event is VideoRecordEvent.Finalize) recording = null
            }
    }

    fun stopVideo() {
        recording?.stop()
    }

    fun release() {
        released = true
        orientationListener?.disable()
        orientationListener = null
        deviceRotation = null
        recording?.stop()
        recording = null
        provider?.unbindAll()
        provider = null
        imageCapture = null
        videoCapture = null
    }
}
