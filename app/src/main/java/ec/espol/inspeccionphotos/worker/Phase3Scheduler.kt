package ec.espol.inspeccionphotos.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

object Phase3Scheduler {
    fun analyzeSurvey(context: Context, surveyId: String) {
        val request = OneTimeWorkRequestBuilder<SpatialAnalysisWorker>()
            .setInputData(Data.Builder().putString(SpatialAnalysisWorker.KEY_SURVEY_ID, surveyId).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "inspeccion_photos-spatial-$surveyId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
