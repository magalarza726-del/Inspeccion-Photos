package ec.espol.inspeccionphotos.sync

import ec.espol.inspeccionphotos.integrity.IntegrityHasher
import ec.espol.inspeccionphotos.integrity.SyncPolicy
import ec.espol.inspeccionphotos.storage.SurveyRepository
import ec.espol.inspeccionphotos.storage.Phase4Repository
import ec.espol.inspeccionphotos.survey.PhotoRecord
import ec.espol.inspeccionphotos.survey.SyncState
import java.io.File

class SyncEngine(
    private val repository: SurveyRepository,
    private val config: SupabaseConfig,
    private val vault: SupabaseSessionVault,
    private val client: SupabaseRestClient = SupabaseRestClient()
) {
    fun sync(): SyncRunResult {
        require(config.configured) { "Supabase no está configurado" }
        var session = vault.load() ?: error("Inicia sesión en Supabase antes de sincronizar")
        if (session.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L + 30L) {
            session = client.refresh(config, session)
            vault.save(session)
        }

        var attempted = 0
        var verified = 0
        var errors = 0
        var skipped = 0
        var spatialUploaded = 0
        var spatialErrors = 0
        var phase4Uploaded = 0
        var phase4Errors = 0
        val uploadedSurveys = mutableSetOf<String>()
        val now = System.currentTimeMillis()

        repository.loadSyncTasks(now).forEach { task ->
            val photo = repository.findPhoto(task.surveyId, task.photoId)
            if (photo == null) {
                repository.deleteSyncTask(task.surveyId, task.photoId)
                skipped++
                return@forEach
            }
            if (photo.syncState == SyncState.VERIFIED && photo.remoteVerifiedSha256 == photo.sha256) {
                repository.deleteSyncTask(task.surveyId, task.photoId)
                skipped++
                return@forEach
            }
            val localHash = photo.sha256
            val original = repository.originalFile(photo)
            if (localHash.isNullOrBlank() || !original.exists() || original.length() <= 0L) {
                val message = "La foto no está lista localmente para sincronizar."
                repository.updatePhoto(photo.copy(syncState = SyncState.ERROR, lastSyncError = message))
                val attempts = task.attempts + 1
                repository.saveSyncTask(task.copy(
                    attempts = attempts,
                    nextAttemptEpochMs = SyncPolicy.nextAttemptEpochMs(System.currentTimeMillis(), attempts),
                    lastError = message
                ))
                skipped++
                return@forEach
            }

            attempted++
            runCatching {
                val currentLocalHash = IntegrityHasher.sha256(original)
                check(currentLocalHash.equals(localHash, ignoreCase = true)) {
                    "INTEGRIDAD LOCAL: el original cambió después de calcular su SHA-256"
                }
                val survey = repository.loadSession(photo.surveyId) ?: error("No se encontró la sesión ${photo.surveyId}")
                if (uploadedSurveys.add(survey.id)) client.upsertSurvey(config, session, survey)

                val base = "${session.authUserId}/${photo.surveyId}"
                val remoteOriginal = "$base/originals/${photo.id}_${photo.fileName}"
                val remoteReference = uploadDerived(photo.referencePath, "$base/reference/${photo.id}.webp", photo, session)
                val remotePreview = uploadDerived(photo.previewPath, "$base/previews/${photo.id}.webp", photo, session)
                val remoteThumbnail = uploadDerived(photo.thumbnailPath, "$base/thumbnails/${photo.id}.webp", photo, session)

                client.uploadFile(config, session, config.storageBucket, remoteOriginal, original, "image/jpeg")
                val uploaded = photo.copy(
                    remoteOriginalPath = remoteOriginal,
                    remoteReferencePath = remoteReference,
                    remotePreviewPath = remotePreview,
                    remoteThumbnailPath = remoteThumbnail,
                    syncState = SyncState.UPLOADED,
                    syncAttempts = task.attempts + 1,
                    lastSyncError = null
                )
                repository.updatePhoto(uploaded)

                var remoteHash = client.remoteSha256(config, session, config.storageBucket, remoteOriginal)
                if (!remoteHash.equals(localHash, ignoreCase = true)) {
                    // Reintento inmediato de una sola unidad. Nunca se vuelve a subir el levantamiento completo.
                    client.uploadFile(config, session, config.storageBucket, remoteOriginal, original, "image/jpeg")
                    remoteHash = client.remoteSha256(config, session, config.storageBucket, remoteOriginal)
                }
                check(remoteHash.equals(localHash, ignoreCase = true)) {
                    "ERROR DE INTEGRIDAD: SHA-256 remoto no coincide con el original local"
                }

                val done = uploaded.copy(
                    remoteVerifiedSha256 = remoteHash,
                    remoteVerifiedAtEpochMs = System.currentTimeMillis(),
                    syncState = SyncState.VERIFIED,
                    lastSyncError = null
                )
                repository.updatePhoto(done)
                client.upsertPhoto(config, session, done)
                repository.deleteSyncTask(task.surveyId, task.photoId)
                verified++
            }.onFailure { error ->
                errors++
                val attempts = task.attempts + 1
                val message = error.message ?: error.javaClass.simpleName
                val latest = repository.findPhoto(task.surveyId, task.photoId) ?: photo
                repository.updatePhoto(
                    latest.copy(
                        syncState = SyncState.ERROR,
                        syncAttempts = attempts,
                        lastSyncError = message
                    )
                )
                repository.saveSyncTask(
                    task.copy(
                        attempts = attempts,
                        nextAttemptEpochMs = SyncPolicy.nextAttemptEpochMs(System.currentTimeMillis(), attempts),
                        lastError = message
                    )
                )
            }
        }

        // Fase 3: el análisis espacial es independiente de la cola de fotos.
        // Si no hay fotos pendientes, un "Sincronizar ahora" todavía puede respaldar route.geojson, GIS, inferencias y cobertura.
        repository.listSurveys().forEach { survey ->
            val bundle = repository.loadSpatialAnalysis(survey.id)
            if (bundle.summary == null) return@forEach
            runCatching {
                client.upsertSurvey(config, session, survey)
                client.upsertSpatialAnalysis(config, session, survey.id, bundle, repository.routeGeoJsonFile(survey.id))
            }.onSuccess { spatialUploaded++ }.onFailure { spatialErrors++ }
        }

        // Fase 4: se sincronizan metadatos relativos (interior, ráfagas y selección de frames).
        // El MP4 bruto permanece local en esta fase: subir un vídeo de varios GB con un único POST rompería
        // la misma regla de resiliencia que eliminó los ZIP gigantes. Requiere transporte reanudable por chunks.
        val phase4Repository = Phase4Repository(repository)
        repository.listSurveys().forEach { survey ->
            val summary = phase4Repository.summary(survey.id)
            if (summary.interiorPoints == 0 && summary.bursts == 0 && summary.videoScans == 0) return@forEach
            runCatching {
                client.upsertSurvey(config, session, survey)
                val videos = phase4Repository.loadVideoScans(survey.id)
                videos.flatMap { it.frames }.forEach { frame ->
                    val file = repository.relativeFile(survey.id, frame.relativePath)
                    if (file != null && file.exists() && file.length() > 0L) {
                        client.uploadFile(
                            config, session, config.storageBucket,
                            "${session.authUserId}/${survey.id}/video_frames/${file.name}",
                            file, "image/jpeg"
                        )
                    }
                }
                client.upsertPhase4Data(
                    config, session, survey.id, summary,
                    phase4Repository.loadInteriorPoses(survey.id),
                    phase4Repository.loadBursts(survey.id),
                    videos
                )
            }.onSuccess { phase4Uploaded++ }.onFailure { phase4Errors++ }
        }

        return SyncRunResult(
            attempted = attempted,
            verified = verified,
            errors = errors,
            skipped = skipped,
            message = "Verificadas: $verified · Errores foto: $errors · Espacial: $spatialUploaded/$spatialErrors · Fase4: $phase4Uploaded/$phase4Errors · Omitidas: $skipped"
        )
    }

    private fun uploadDerived(
        relativePath: String?,
        remotePath: String,
        photo: PhotoRecord,
        session: AuthSession
    ): String? {
        val file: File = repository.relativeFile(photo.surveyId, relativePath)?.takeIf { it.exists() && it.length() > 0L } ?: return null
        client.uploadFile(config, session, config.storageBucket, remotePath, file, "image/webp")
        return remotePath
    }
}
