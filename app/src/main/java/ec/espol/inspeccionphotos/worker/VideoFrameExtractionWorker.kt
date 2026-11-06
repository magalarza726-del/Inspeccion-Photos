package ec.espol.inspeccionphotos.worker

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ec.espol.inspeccionphotos.scan.FrameImageScorer
import ec.espol.inspeccionphotos.scan.VideoFrameSelectionPolicy
import ec.espol.inspeccionphotos.storage.Phase4Repository
import ec.espol.inspeccionphotos.storage.SurveyRepository
import ec.espol.inspeccionphotos.survey.VideoFrameCandidate
import ec.espol.inspeccionphotos.survey.VideoScanStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class VideoFrameExtractionWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val surveyId = inputData.getString(KEY_SURVEY_ID) ?: return@withContext Result.failure()
        val scanId = inputData.getString(KEY_SCAN_ID) ?: return@withContext Result.failure()
        val surveys = SurveyRepository(applicationContext)
        val phase4 = Phase4Repository(surveys)
        val scan = phase4.loadVideoScan(surveyId, scanId) ?: return@withContext Result.failure()
        val video = File(surveys.surveyDir(surveyId), scan.relativeVideoPath)
        if (!video.exists() || video.length() == 0L) {
            phase4.saveVideoScan(scan.copy(status = VideoScanStatus.ERROR, error = "Vídeo original ausente"))
            return@withContext Result.failure()
        }
        val poses = phase4.loadVideoPoses(scan)
        val candidates = VideoFrameSelectionPolicy.selectPoseCandidates(poses)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(video.absolutePath)
            val selected = mutableListOf<VideoFrameCandidate>()
            var lastHash: Long? = null
            candidates.forEachIndexed { index, pose ->
                val offset = (pose.timestampEpochMs - scan.startedAtEpochMs).coerceAtLeast(0L)
                val bitmap = retriever.getFrameAtTime(offset * 1_000, MediaMetadataRetriever.OPTION_CLOSEST) ?: return@forEachIndexed
                try {
                    val score = FrameImageScorer.score(bitmap)
                    // Low score means little high-frequency detail; exact scale is device-independent enough for a coarse filter.
                    if (score.sharpness < 8.0) return@forEachIndexed
                    if (VideoFrameSelectionPolicy.isRedundant(lastHash, score.perceptualHash)) return@forEachIndexed
                    val file = File(phase4.videoFramesDir(surveyId), "${scan.id}_${selected.size.toString().padStart(4, '0')}.jpg")
                    FileOutputStream(file).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
                    selected += VideoFrameCandidate(
                        timestampOffsetMs = offset,
                        relativePath = "video_frames/${file.name}",
                        sharpnessScore = score.sharpness,
                        perceptualHash = score.perceptualHash,
                        location = pose.location,
                        orientation = pose.orientation,
                        interiorPose = pose.interiorPose,
                        selectedReason = when {
                            pose.interiorPose != null -> "desplazamiento/ángulo interior + nitidez + no redundancia"
                            else -> "desplazamiento/ángulo GPS + nitidez + no redundancia"
                        }
                    )
                    lastHash = score.perceptualHash
                } finally {
                    bitmap.recycle()
                }
                if (isStopped) return@forEachIndexed
            }
            phase4.saveVideoScan(scan.copy(status = VideoScanStatus.READY, extractedFrames = selected.size, frames = selected, error = null))
            surveys.writeManifest(surveyId)
            Result.success()
        } catch (t: Throwable) {
            phase4.saveVideoScan(scan.copy(status = VideoScanStatus.ERROR, error = t.message ?: t.javaClass.simpleName))
            Result.retry()
        } finally {
            runCatching { retriever.release() }
        }
    }

    companion object {
        const val KEY_SURVEY_ID = "survey_id"
        const val KEY_SCAN_ID = "scan_id"
    }
}
