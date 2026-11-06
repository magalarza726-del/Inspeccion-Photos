package ec.espol.inspeccionphotos.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ec.espol.inspeccionphotos.spatial.SpatialAnalysisEngine
import ec.espol.inspeccionphotos.storage.SurveyRepository
import ec.espol.inspeccionphotos.storage.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SpatialAnalysisWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val repository = SurveyRepository(applicationContext)
            val engine = SpatialAnalysisEngine(repository)
            val surveyId = inputData.getString(KEY_SURVEY_ID)
            if (surveyId != null) {
                engine.analyzeSurvey(surveyId)
            } else {
                repository.listSurveys().forEach { engine.analyzeSurvey(it.id) }
            }
            val settings = SettingsStore(applicationContext)
            if (settings.supabaseConfig.configured && settings.supabaseConfig.autoSync) {
                Phase2Scheduler.enqueueSync(applicationContext, manual = false)
            }
        }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object { const val KEY_SURVEY_ID = "survey_id" }
}
