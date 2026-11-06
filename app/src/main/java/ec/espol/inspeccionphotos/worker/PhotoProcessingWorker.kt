package ec.espol.inspeccionphotos.worker

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import ec.espol.inspeccionphotos.storage.SurveyRepository
import ec.espol.inspeccionphotos.sync.Phase2Processor

class PhotoProcessingWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            val processed = Phase2Processor(SurveyRepository(applicationContext)).processPending()
            if (processed.queued > 0) Phase2Scheduler.enqueueSync(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
