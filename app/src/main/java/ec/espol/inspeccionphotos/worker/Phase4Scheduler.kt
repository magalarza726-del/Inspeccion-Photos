package ec.espol.inspeccionphotos.worker

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

object Phase4Scheduler {
    fun extractRepresentativeFrames(context: Context, surveyId: String, scanId: String) {
        val request = OneTimeWorkRequestBuilder<VideoFrameExtractionWorker>()
            .setInputData(Data.Builder().putString(VideoFrameExtractionWorker.KEY_SURVEY_ID, surveyId).putString(VideoFrameExtractionWorker.KEY_SCAN_ID, scanId).build())
            .addTag("inspeccion_photos-phase4-video-$scanId")
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
