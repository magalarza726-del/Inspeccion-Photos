package ec.espol.inspeccionphotos.camera

import ec.espol.inspeccionphotos.storage.SurveyRepository
import ec.espol.inspeccionphotos.survey.*
import java.io.File

object PhotoCaptureCommitter {
    fun commit(repository: SurveyRepository, survey: SurveySession, pending: PendingCapture): PhotoRecord? {
        val original = File(repository.surveyDir(survey.id), pending.relativeOriginalPath)
        if (!original.exists() || original.length() == 0L) return null
        val camera = PhotoMetadataReader.read(original)
        val route = repository.loadRoute(survey.id)
        val pose = PoseResolver.resolve(camera?.exifTimestampEpochMs, route, pending)
        val capturedAt = camera?.exifTimestampEpochMs ?: System.currentTimeMillis()
        return PhotoRecord(
            id = pending.photoId,
            surveyId = survey.id,
            sequence = pending.sequence,
            fileName = pending.fileName,
            relativeOriginalPath = pending.relativeOriginalPath,
            capturedAtEpochMs = capturedAt,
            place = pending.place,
            location = pose.location,
            orientation = pose.orientation,
            camera = camera,
            gpsQuality = pose.location?.quality ?: GpsQuality.UNAVAILABLE,
            poseConfidence = pose.confidence,
            syncState = SyncState.LOCAL_ONLY
        ).also(repository::savePhoto)
    }
}
