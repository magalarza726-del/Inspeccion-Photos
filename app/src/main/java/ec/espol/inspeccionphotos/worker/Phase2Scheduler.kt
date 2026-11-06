package ec.espol.inspeccionphotos.worker

import android.content.Context
import androidx.work.*
import ec.espol.inspeccionphotos.storage.SettingsStore
import java.util.concurrent.TimeUnit

object Phase2Scheduler {
    private const val PROCESS_WORK = "inspeccion_photos-photo-postprocess"
    private const val SYNC_WORK = "inspeccion_photos-photo-sync"
    private const val PERIODIC_SYNC = "inspeccion_photos-photo-sync-periodic"

    fun bootstrap(context: Context) {
        enqueueLocalProcessing(context)
        configurePeriodicSync(context, SettingsStore(context).supabaseConfig.autoSync)
    }

    fun enqueueLocalProcessing(context: Context) {
        val request = OneTimeWorkRequestBuilder<PhotoProcessingWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(PROCESS_WORK, ExistingWorkPolicy.KEEP, request)
    }

    fun enqueueSync(context: Context, manual: Boolean = false) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<SurveySyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(SurveySyncWorker.KEY_MANUAL to manual))
            .build()
        if (manual) {
            WorkManager.getInstance(context).enqueue(request)
        } else {
            WorkManager.getInstance(context).enqueueUniqueWork(SYNC_WORK, ExistingWorkPolicy.KEEP, request)
        }
    }

    fun configurePeriodicSync(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(PERIODIC_SYNC)
            return
        }
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<SurveySyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC_SYNC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
