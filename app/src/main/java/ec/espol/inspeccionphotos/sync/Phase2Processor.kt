package ec.espol.inspeccionphotos.sync

import ec.espol.inspeccionphotos.integrity.IntegrityHasher
import ec.espol.inspeccionphotos.previews.PreviewGenerator
import ec.espol.inspeccionphotos.storage.SurveyRepository
import ec.espol.inspeccionphotos.survey.CURRENT_SCHEMA_VERSION
import ec.espol.inspeccionphotos.survey.SyncState

class Phase2Processor(private val repository: SurveyRepository) {
    data class Result(val processed: Int, val queued: Int, val errors: Int)

    fun processPending(): Result {
        var processed = 0
        var queued = 0
        var errors = 0
        repository.migrateCurrentSchema()

        repository.loadAllPhotos().forEach { current ->
            if (current.syncState == SyncState.VERIFIED) return@forEach
            // processedAt también registra que ya se intentó crear derivados.
            // Una cámara/formato raro no debe provocar recomputación infinita.
            val needsProcessing = current.sha256.isNullOrBlank() || current.processedAtEpochMs == null

            if (!needsProcessing) {
                repository.enqueueSync(current)
                queued++
                return@forEach
            }

            val original = repository.originalFile(current)
            if (!original.exists() || original.length() <= 0L) {
                repository.updatePhoto(
                    current.copy(
                        schemaVersion = CURRENT_SCHEMA_VERSION,
                        syncState = SyncState.ERROR,
                        lastSyncError = "Original local no disponible; no se intentó subir."
                    )
                )
                errors++
                return@forEach
            }

            repository.updatePhoto(current.copy(syncState = SyncState.PROCESSING, lastSyncError = null), refreshManifest = false)
            runCatching {
                val sha = IntegrityHasher.sha256(original)
                val derived = PreviewGenerator.generate(repository, current)
                val ready = current.copy(
                    schemaVersion = CURRENT_SCHEMA_VERSION,
                    sha256 = sha,
                    referencePath = derived.referencePath,
                    previewPath = derived.previewPath,
                    thumbnailPath = derived.thumbnailPath,
                    processedAtEpochMs = System.currentTimeMillis(),
                    syncState = SyncState.QUEUED,
                    lastSyncError = null
                )
                repository.updatePhoto(ready)
                repository.enqueueSync(ready)
                processed++
                queued++
            }.onFailure { error ->
                val sha = runCatching { IntegrityHasher.sha256(original) }.getOrNull()
                val fallback = current.copy(
                    schemaVersion = CURRENT_SCHEMA_VERSION,
                    sha256 = sha ?: current.sha256,
                    processedAtEpochMs = if (sha != null) System.currentTimeMillis() else current.processedAtEpochMs,
                    syncState = if (sha != null) SyncState.QUEUED else SyncState.ERROR,
                    lastSyncError = if (sha != null) "Original íntegro; no se pudieron crear todas las previews: ${error.message}" else (error.message ?: "Error de procesamiento")
                )
                repository.updatePhoto(fallback)
                if (sha != null) {
                    repository.enqueueSync(fallback)
                    queued++
                } else errors++
            }
        }
        return Result(processed, queued, errors)
    }
}
