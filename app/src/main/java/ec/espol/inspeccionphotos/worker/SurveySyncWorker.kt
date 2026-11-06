package ec.espol.inspeccionphotos.worker

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import ec.espol.inspeccionphotos.storage.SettingsStore
import ec.espol.inspeccionphotos.storage.SurveyRepository
import ec.espol.inspeccionphotos.sync.SupabaseSessionVault
import ec.espol.inspeccionphotos.sync.SyncEngine

class SurveySyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        val settings = SettingsStore(applicationContext)
        val config = settings.supabaseConfig
        if (!config.configured) return Result.success()
        if (!manual && !config.autoSync) return Result.success()
        val vault = SupabaseSessionVault(applicationContext)
        if (vault.load() == null) return Result.success()

        return try {
            val syncResult = SyncEngine(SurveyRepository(applicationContext), config, vault).sync()
            if (syncResult.errors > 0) Result.retry() else Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object { const val KEY_MANUAL = "manual" }
}
