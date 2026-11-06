package ec.espol.inspeccionphotos.arcore

import android.app.Activity
import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import ec.espol.inspeccionphotos.survey.ArPoseSample
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

sealed class ArCoreRuntimeStatus {
    data object Starting : ArCoreRuntimeStatus()
    data object Tracking : ArCoreRuntimeStatus()
    data object Paused : ArCoreRuntimeStatus()
    data object Unsupported : ArCoreRuntimeStatus()
    data class Error(val message: String) : ArCoreRuntimeStatus()
}

class ArCorePoseTracker(
    private val context: Context,
    private val activity: Activity,
    private val onPose: (ArPoseSample) -> Unit,
    private val onStatus: (ArCoreRuntimeStatus) -> Unit
) : GLSurfaceView.Renderer {
    val surfaceView = GLSurfaceView(context).apply {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(this@ArCorePoseTracker)
        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var session: Session? = null
    private var cameraTextureId = -1
    private var resumed = false
    private var installRequested = false
    private var lastPoseEmissionMs = 0L
    private var lastStatus: ArCoreRuntimeStatus? = null

    fun resume() {
        if (resumed) return
        emitStatus(ArCoreRuntimeStatus.Starting)
        try {
            val availability = ArCoreApk.getInstance().checkAvailability(context)
            if (availability.isUnsupported) {
                emitStatus(ArCoreRuntimeStatus.Unsupported)
                return
            }
            when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    emitStatus(ArCoreRuntimeStatus.Paused)
                    return
                }
                ArCoreApk.InstallStatus.INSTALLED -> Unit
            }
            if (session == null) {
                session = Session(context).also { s ->
                    val config = Config(s).apply {
                        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        // Keep ARCore's device-optimized default focus mode for tracking reliability.
                    }
                    s.configure(config)
                }
            }
            session?.resume()
            surfaceView.onResume()
            resumed = true
        } catch (t: Throwable) {
            emitStatus(ArCoreRuntimeStatus.Error(t.message ?: t.javaClass.simpleName))
        }
    }

    fun pause() {
        if (!resumed) return
        surfaceView.onPause()
        runCatching { session?.pause() }
        resumed = false
        emitStatus(ArCoreRuntimeStatus.Paused)
    }

    fun close() {
        pause()
        runCatching { session?.close() }
        session = null
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        cameraTextureId = createExternalTexture()
        session?.setCameraTextureName(cameraTextureId)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val current = session ?: return
        if (!resumed) return
        if (cameraTextureId < 0) return
        try {
            current.setCameraTextureName(cameraTextureId)
            val frame = current.update()
            val camera = frame.camera
            val pose = camera.pose
            val t = pose.translation
            val q = pose.rotationQuaternion
            val tracking = camera.trackingState == TrackingState.TRACKING
            emitStatus(if (tracking) ArCoreRuntimeStatus.Tracking else ArCoreRuntimeStatus.Paused)
            val now = System.currentTimeMillis()
            // ARCore can render around 30–60 FPS. The survey route does not need that density:
            // persist at ~5 Hz to reduce disk writes, battery use and Compose updates.
            if (now - lastPoseEmissionMs >= 200L) {
                lastPoseEmissionMs = now
                emitPose(
                    ArPoseSample(
                        timestampEpochMs = now,
                        xMeters = t[0], yMeters = t[1], zMeters = t[2],
                        qx = q[0], qy = q[1], qz = q[2], qw = q[3],
                        tracking = tracking
                    )
                )
            }
            GLES20.glClearColor(0.025f, 0.035f, 0.055f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        } catch (t: Throwable) {
            emitStatus(ArCoreRuntimeStatus.Error(t.message ?: t.javaClass.simpleName))
        }
    }

    private fun emitStatus(value: ArCoreRuntimeStatus) {
        if (lastStatus == value) return
        lastStatus = value
        mainHandler.post { onStatus(value) }
    }
    private fun emitPose(value: ArPoseSample) { mainHandler.post { onPose(value) } }

    private fun createExternalTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val id = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, id)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return id
    }
}
