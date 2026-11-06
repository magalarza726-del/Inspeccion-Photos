package ec.espol.inspeccionphotos.storage

import android.content.Context
import android.os.Environment
import ec.espol.inspeccionphotos.survey.*
import ec.espol.inspeccionphotos.gis.BuildingGeoJson
import ec.espol.inspeccionphotos.util.SurveyMath
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID

class SurveyRepository(private val context: Context) {
    private val rootDir: File = File(
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir,
        "InspeccionPhotos/surveys"
    ).apply { mkdirs() }

    fun storageRoot(): File = rootDir

    fun createSurvey(place: String): SurveySession {
        val now = System.currentTimeMillis()
        val survey = SurveySession(
            id = UUID.randomUUID().toString(),
            place = place,
            createdAtEpochMs = now,
            startedAtEpochMs = now
        )
        ensureDirs(survey.id)
        saveSession(survey)
        writeManifest(survey.id)
        return survey
    }

    fun listSurveys(): List<SurveySession> = rootDir.listFiles()
        .orEmpty()
        .filter { it.isDirectory }
        .mapNotNull { dir ->
            val session = File(dir, "session.json")
            runCatching { SurveyJson.decodeSession(JSONObject(session.readText())) }.getOrNull()
        }
        .sortedByDescending { it.startedAtEpochMs }

    fun activeSurvey(): SurveySession? = listSurveys().firstOrNull { it.status == SurveyStatus.ACTIVE }

    fun loadSession(id: String): SurveySession? = runCatching {
        SurveyJson.decodeSession(JSONObject(File(surveyDir(id), "session.json").readText()))
    }.getOrNull()

    fun saveSession(session: SurveySession, refreshManifest: Boolean = true) {
        ensureDirs(session.id)
        atomicWrite(File(surveyDir(session.id), "session.json"), SurveyJson.session(session).toString(2))
        if (refreshManifest) writeManifest(session.id)
    }

    fun updateSession(id: String, refreshManifest: Boolean = true, transform: (SurveySession) -> SurveySession): SurveySession? {
        val current = loadSession(id) ?: return null
        val updated = transform(current)
        saveSession(updated, refreshManifest)
        return updated
    }

    fun surveyDir(id: String): File = File(rootDir, id)
    fun originalsDir(id: String): File = File(surveyDir(id), "originals").apply { mkdirs() }
    fun referenceDir(id: String): File = File(surveyDir(id), "reference").apply { mkdirs() }
    fun previewsDir(id: String): File = File(surveyDir(id), "previews").apply { mkdirs() }
    fun thumbnailsDir(id: String): File = File(surveyDir(id), "thumbnails").apply { mkdirs() }
    fun metadataDir(id: String): File = File(surveyDir(id), "metadata").apply { mkdirs() }
    fun syncQueueDir(id: String): File = File(surveyDir(id), "sync/queue").apply { mkdirs() }
    fun gisDir(id: String): File = File(surveyDir(id), "gis").apply { mkdirs() }
    fun analysisDir(id: String): File = File(surveyDir(id), "analysis").apply { mkdirs() }
    fun buildingsGeoJsonFile(id: String): File = File(gisDir(id), "buildings.geojson")
    fun routeGeoJsonFile(id: String): File = File(surveyDir(id), "route.geojson")

    fun importBuildingsGeoJson(surveyId: String, input: InputStream): Int {
        ensureDirs(surveyId)
        return BuildingGeoJson.import(input, buildingsGeoJsonFile(surveyId))
    }

    fun loadBuildings(surveyId: String): List<BuildingFootprint> = BuildingGeoJson.load(buildingsGeoJsonFile(surveyId))

    fun prepareCapture(
        survey: SurveySession,
        location: LocationSnapshot?,
        orientation: OrientationSnapshot?
    ): Pair<PendingCapture, File> {
        ensureDirs(survey.id)
        val sequence = survey.lastSequence + 1
        val photoId = UUID.randomUUID().toString()
        val fileName = "${sanitize(survey.place)}_${sequence.toString().padStart(4, '0')}.jpg"
        val relative = "originals/$fileName"
        val file = File(surveyDir(survey.id), relative)
        if (file.exists()) file.delete()
        file.parentFile?.mkdirs()
        file.createNewFile()
        val pending = PendingCapture(
            surveyId = survey.id,
            photoId = photoId,
            sequence = sequence,
            fileName = fileName,
            relativeOriginalPath = relative,
            startedAtEpochMs = System.currentTimeMillis(),
            place = survey.place,
            fallbackLocation = location,
            fallbackOrientation = orientation
        )
        atomicWrite(File(surveyDir(survey.id), "pending_capture.json"), SurveyJson.pending(pending).toString(2))
        return pending to file
    }

    fun loadPendingCapture(surveyId: String): PendingCapture? = runCatching {
        val file = File(surveyDir(surveyId), "pending_capture.json")
        if (!file.exists()) null else SurveyJson.decodePending(JSONObject(file.readText()))
    }.getOrNull()

    fun clearPendingCapture(surveyId: String, deletePendingOriginal: Boolean = true) {
        val pending = loadPendingCapture(surveyId)
        if (deletePendingOriginal && pending != null) {
            val original = File(surveyDir(surveyId), pending.relativeOriginalPath)
            if (original.exists()) original.delete()
        }
        File(surveyDir(surveyId), "pending_capture.json").delete()
    }

    fun recoverPendingCapture(surveyId: String): PendingCapture? {
        val pending = loadPendingCapture(surveyId) ?: return null
        val original = File(surveyDir(surveyId), pending.relativeOriginalPath)
        return if (original.exists() && original.length() > 0L) pending else null
    }

    @Synchronized
    fun savePhoto(record: PhotoRecord) {
        ensureDirs(record.surveyId)
        // Ruta crítica de captura: metadata + contador de sesión + cierre del pending.
        // Hash, previews, manifest y red ocurren después fuera del flujo de cámara.
        val target = metadataFile(record.surveyId, record.sequence, record.id)
        val alreadyExisted = target.exists()
        writePhoto(record.copy(schemaVersion = CURRENT_SCHEMA_VERSION))
        updateSession(record.surveyId, refreshManifest = false) {
            it.copy(
                schemaVersion = CURRENT_SCHEMA_VERSION,
                photoCount = maxOf(it.photoCount + if (alreadyExisted) 0 else 1, record.sequence),
                lastSequence = maxOf(it.lastSequence, record.sequence)
            )
        }
        clearPendingCapture(record.surveyId, deletePendingOriginal = false)
    }

    @Synchronized
    fun updatePhoto(record: PhotoRecord, refreshManifest: Boolean = true) {
        ensureDirs(record.surveyId)
        writePhoto(record.copy(schemaVersion = CURRENT_SCHEMA_VERSION))
        if (refreshManifest) writeManifest(record.surveyId)
    }

    private fun writePhoto(record: PhotoRecord) {
        val file = metadataFile(record.surveyId, record.sequence, record.id)
        atomicWrite(file, SurveyJson.photo(record).toString(2))
    }

    fun loadPhotos(surveyId: String): List<PhotoRecord> = metadataDir(surveyId).listFiles()
        .orEmpty()
        .filter { it.extension.equals("json", true) }
        .mapNotNull { file -> runCatching { SurveyJson.decodePhoto(JSONObject(file.readText())) }.getOrNull() }
        .sortedBy { it.sequence }

    fun loadAllPhotos(): List<PhotoRecord> = listSurveys().flatMap { loadPhotos(it.id) }

    fun findPhoto(surveyId: String, photoId: String): PhotoRecord? = loadPhotos(surveyId).firstOrNull { it.id == photoId }

    fun originalFile(photo: PhotoRecord): File = File(surveyDir(photo.surveyId), photo.relativeOriginalPath)
    fun relativeFile(surveyId: String, relativePath: String?): File? = relativePath?.let { File(surveyDir(surveyId), it) }

    fun enqueueSync(photo: PhotoRecord) {
        val now = System.currentTimeMillis()
        val existing = loadSyncTask(photo.surveyId, photo.id)
        val task = existing ?: SyncTask(photo.id, photo.surveyId, now)
        saveSyncTask(task)
        if (photo.syncState != SyncState.QUEUED && photo.syncState != SyncState.UPLOADED && photo.syncState != SyncState.VERIFIED) {
            updatePhoto(photo.copy(syncState = SyncState.QUEUED, lastSyncError = null), refreshManifest = false)
        }
    }

    fun loadSyncTasks(nowEpochMs: Long = Long.MAX_VALUE): List<SyncTask> = listSurveys()
        .flatMap { survey ->
            syncQueueDir(survey.id).listFiles().orEmpty()
                .filter { it.extension.equals("json", true) }
                .mapNotNull { file -> runCatching { SurveyJson.decodeSyncTask(JSONObject(file.readText())) }.getOrNull() }
        }
        .filter { it.nextAttemptEpochMs <= nowEpochMs }
        .sortedWith(compareBy<SyncTask> { it.nextAttemptEpochMs }.thenBy { it.createdAtEpochMs })

    fun loadSyncTask(surveyId: String, photoId: String): SyncTask? = runCatching {
        val file = File(syncQueueDir(surveyId), "$photoId.json")
        if (!file.exists()) null else SurveyJson.decodeSyncTask(JSONObject(file.readText()))
    }.getOrNull()

    fun saveSyncTask(task: SyncTask) {
        atomicWrite(File(syncQueueDir(task.surveyId), "${task.photoId}.json"), SurveyJson.syncTask(task).toString(2))
    }

    fun deleteSyncTask(surveyId: String, photoId: String) {
        File(syncQueueDir(surveyId), "$photoId.json").delete()
    }

    fun syncSummary(): SyncSummary {
        val photos = loadAllPhotos()
        val counts = photos.groupingBy { it.syncState }.eachCount()
        return SyncSummary(
            totalPhotos = photos.size,
            localOnly = counts.getOrDefault(SyncState.LOCAL_ONLY, 0),
            processing = counts.getOrDefault(SyncState.PROCESSING, 0),
            queued = counts.getOrDefault(SyncState.QUEUED, 0),
            uploaded = counts.getOrDefault(SyncState.UPLOADED, 0),
            verified = counts.getOrDefault(SyncState.VERIFIED, 0),
            errors = counts.getOrDefault(SyncState.ERROR, 0),
            queueItems = loadSyncTasks(Long.MAX_VALUE).size
        )
    }

    fun migrateCurrentSchema() {
        listSurveys().forEach { session ->
            if (session.schemaVersion != CURRENT_SCHEMA_VERSION) {
                saveSession(session.copy(schemaVersion = CURRENT_SCHEMA_VERSION), refreshManifest = false)
            }
            loadPhotos(session.id).forEach { photo ->
                if (photo.schemaVersion != CURRENT_SCHEMA_VERSION) {
                    updatePhoto(photo.copy(schemaVersion = CURRENT_SCHEMA_VERSION), refreshManifest = false)
                }
                if (photo.syncState in setOf(SyncState.QUEUED, SyncState.UPLOADED, SyncState.ERROR) && loadSyncTask(photo.surveyId, photo.id) == null) {
                    saveSyncTask(SyncTask(photo.id, photo.surveyId, System.currentTimeMillis()))
                }
            }
            writeManifest(session.id)
        }
    }

    @Synchronized
    fun appendRoutePoint(surveyId: String, point: RoutePoint, routePointCount: Int? = null) {
        ensureDirs(surveyId)
        File(surveyDir(surveyId), "route.jsonl").appendText(SurveyJson.route(point).toString() + "\n")
        val count = routePointCount
        if (count != null && count > 0 && count % 10 == 0) {
            updateSession(surveyId, refreshManifest = false) { it.copy(routePointCount = count) }
        }
    }

    fun loadRoute(surveyId: String): List<RoutePoint> {
        val file = File(surveyDir(surveyId), "route.jsonl")
        if (!file.exists()) return emptyList()
        return file.useLines { lines ->
            lines.mapNotNull { raw ->
                if (raw.isBlank()) null else runCatching { SurveyJson.decodeRoute(JSONObject(raw)) }.getOrNull()
            }.toList().sortedBy { it.timestampEpochMs }
        }
    }

    @Synchronized
    fun appendPoi(record: PoiRecord) {
        ensureDirs(record.surveyId)
        File(surveyDir(record.surveyId), "pois.jsonl").appendText(SurveyJson.poi(record).toString() + "\n")
        writeManifest(record.surveyId)
    }

    fun loadPois(surveyId: String): List<PoiRecord> {
        val file = File(surveyDir(surveyId), "pois.jsonl")
        if (!file.exists()) return emptyList()
        return file.useLines { lines ->
            lines.mapNotNull { raw ->
                if (raw.isBlank()) null else runCatching { SurveyJson.decodePoi(JSONObject(raw)) }.getOrNull()
            }.toList()
        }
    }

    fun saveSpatialAnalysis(bundle: SpatialAnalysisBundle) {
        val summary = bundle.summary ?: return
        ensureDirs(summary.surveyId)
        atomicWrite(
            File(analysisDir(summary.surveyId), "building_inferences.json"),
            JSONArray().apply { bundle.inferences.forEach { put(SpatialJson.inference(it)) } }.toString(2)
        )
        atomicWrite(
            File(analysisDir(summary.surveyId), "coverage.json"),
            JSONArray().apply { bundle.coverage.forEach { put(SpatialJson.buildingCoverage(it)) } }.toString(2)
        )
        atomicWrite(File(analysisDir(summary.surveyId), "summary.json"), SpatialJson.summary(summary).toString(2))
        writeManifest(summary.surveyId)
    }

    fun loadSpatialAnalysis(surveyId: String): SpatialAnalysisBundle {
        val inferences = runCatching {
            val arr = JSONArray(File(analysisDir(surveyId), "building_inferences.json").readText())
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(SpatialJson::decodeInference) }
        }.getOrDefault(emptyList())
        val coverage = runCatching {
            val arr = JSONArray(File(analysisDir(surveyId), "coverage.json").readText())
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(SpatialJson::decodeBuildingCoverage) }
        }.getOrDefault(emptyList())
        val summary = runCatching { SpatialJson.decodeSummary(JSONObject(File(analysisDir(surveyId), "summary.json").readText())) }.getOrNull()
        return SpatialAnalysisBundle(loadBuildings(surveyId), inferences, coverage, summary)
    }

    fun finalizeSurvey(id: String): SurveySession? = updateSession(id, refreshManifest = true) {
        it.copy(status = SurveyStatus.COMPLETED, endedAtEpochMs = System.currentTimeMillis())
    }

    fun discardSurveyKeepFiles(id: String): SurveySession? = updateSession(id, refreshManifest = true) {
        it.copy(
            status = SurveyStatus.DISCARDED,
            endedAtEpochMs = System.currentTimeMillis(),
            note = "Descartado por el usuario. Los originales se conservaron localmente."
        )
    }

    fun setEnvironment(id: String, mode: EnvironmentMode): SurveySession? = updateSession(id, refreshManifest = false) { it.copy(environmentMode = mode) }

    fun audit(id: String): AuditSummary {
        val session = loadSession(id)
        val photos = loadPhotos(id)
        val route = loadRoute(id)
        val distance = SurveyMath.routeDistanceMeters(route)
        val end = session?.endedAtEpochMs ?: System.currentTimeMillis()
        val duration = if (session != null) end - session.startedAtEpochMs else 0L
        val absoluteGnssPhotos = photos.filter { it.location?.positionReference == PositionReference.GNSS_ABSOLUTE }
        val counts = absoluteGnssPhotos.groupingBy { it.gpsQuality }.eachCount()
        val interiorRelativePhotos = photos.count { it.location?.positionReference == PositionReference.INTERIOR_RELATIVE }
        val noPositionPhotos = photos.count { it.location == null || it.location.positionReference == PositionReference.UNKNOWN }
        val originalsPresent = photos.count { originalFile(it).exists() && originalFile(it).length() > 0L }
        val orientationCount = photos.count { it.orientation != null }
        val spatial = loadSpatialAnalysis(id)
        val phase4Repo = Phase4Repository(this)
        val phase4 = phase4Repo.summary(id)
        val interiorPoses = phase4Repo.loadInteriorPoses(id)
        val videoScans = phase4Repo.loadVideoScans(id)
        val warnings = buildList {
            val low = counts.getOrDefault(GpsQuality.LOW, 0) + counts.getOrDefault(GpsQuality.VERY_LOW, 0)
            if (low > 0) add("$low fotografía(s) tienen GPS de baja precisión.")
            val noGps = counts.getOrDefault(GpsQuality.UNAVAILABLE, 0)
            if (noGps > 0) add("$noGps fotografía(s) tienen GNSS absoluto sin precisión reportada.")
            if (noPositionPhotos > 0) add("$noPositionPhotos fotografía(s) no tienen posición utilizable asociada.")
            if (interiorRelativePhotos > 0) add("$interiorRelativePhotos fotografía(s) usan pose interior relativa; su incertidumbre NO se contabiliza como precisión GPS/GNSS.")
            if (orientationCount < photos.size) add("${photos.size - orientationCount} fotografía(s) no tienen orientación disponible.")
            if (originalsPresent < photos.size) add("Faltan ${photos.size - originalsPresent} original(es) locales. No finalices hasta revisar esto.")
            val noHash = photos.count { it.sha256.isNullOrBlank() }
            if (noHash > 0) add("$noHash fotografía(s) todavía están calculando/verificando su SHA-256 local.")
            val syncErrors = photos.count { it.syncState == SyncState.ERROR }
            if (syncErrors > 0) add("$syncErrors fotografía(s) tienen un error de sincronización pendiente de reintento.")
            if (route.size < 2) add("La trayectoria es insuficiente para calcular distancia.")
            spatial.summary?.warnings?.forEach { if (it !in this) add(it) }
            if (interiorPoses.lastOrNull()?.confidence == InteriorConfidence.VERY_LOW) add("La trayectoria interior terminó con confianza muy baja; úsala como referencia relativa, no como posición absoluta.")
            val interruptedBursts = phase4Repo.loadBursts(id).count { it.status == BurstStatus.INTERRUPTED }
            if (interruptedBursts > 0) add("$interruptedBursts ráfaga(s) fueron interrumpidas; las fotografías ya confirmadas se conservaron.")
            val videoErrors = videoScans.count { it.status == VideoScanStatus.ERROR }
            if (videoErrors > 0) add("$videoErrors escaneo(s) de vídeo tienen error de procesamiento.")
            val videoProcessing = videoScans.count { it.status == VideoScanStatus.PROCESSING }
            if (videoProcessing > 0) add("$videoProcessing escaneo(s) todavía están extrayendo frames representativos en segundo plano.")
        }
        return AuditSummary(
            photos = photos.size,
            originalsPresent = originalsPresent,
            routePoints = route.size,
            distanceMeters = distance,
            durationMillis = duration,
            gpsExcellent = counts.getOrDefault(GpsQuality.EXCELLENT, 0),
            gpsGood = counts.getOrDefault(GpsQuality.GOOD, 0),
            gpsAcceptable = counts.getOrDefault(GpsQuality.ACCEPTABLE, 0),
            gpsLow = counts.getOrDefault(GpsQuality.LOW, 0),
            gpsVeryLow = counts.getOrDefault(GpsQuality.VERY_LOW, 0),
            gpsUnavailable = counts.getOrDefault(GpsQuality.UNAVAILABLE, 0) + noPositionPhotos,
            orientationAvailable = orientationCount,
            warnings = warnings,
            hashesReady = photos.count { !it.sha256.isNullOrBlank() },
            previewsReady = photos.count { !it.previewPath.isNullOrBlank() && !it.thumbnailPath.isNullOrBlank() },
            syncVerified = photos.count { it.syncState == SyncState.VERIFIED },
            syncPending = photos.count { it.syncState != SyncState.VERIFIED },
            syncErrors = photos.count { it.syncState == SyncState.ERROR },
            spatialCoveragePercent = spatial.summary?.averageCoveragePercent,
            gisBuildings = spatial.summary?.gisBuildingsAvailable ?: spatial.buildings.size,
            identifiedPhotos = spatial.summary?.identifiedPhotos ?: 0,
            interiorPoints = phase4.interiorPoints,
            interiorArCorePoints = phase4.interiorArCorePoints,
            spatialBursts = phase4.bursts,
            burstPhotos = phase4.burstPhotos,
            videoScans = phase4.videoScans,
            extractedVideoFrames = phase4.extractedVideoFrames,
            interiorRelativePhotos = interiorRelativePhotos
        )
    }

    fun writeManifest(surveyId: String): File? {
        val sessionFile = File(surveyDir(surveyId), "session.json")
        if (!sessionFile.exists()) return null
        val session = loadSession(surveyId) ?: return null
        val photos = loadPhotos(surveyId)
        val route = loadRoute(surveyId)
        val pois = loadPois(surveyId)
        val avgGps = photos.mapNotNull { it.location?.horizontalAccuracyMeters?.toDouble() }.takeIf { it.isNotEmpty() }?.average()
        val root = JSONObject().apply {
            put("schemaVersion", CURRENT_SCHEMA_VERSION)
            put("format", "inspeccion_photos-spatial-survey")
            put("surveyId", session.id)
            put("place", session.place)
            put("createdAtEpochMs", session.createdAtEpochMs)
            put("startedAtEpochMs", session.startedAtEpochMs)
            put("endedAtEpochMs", session.endedAtEpochMs ?: JSONObject.NULL)
            put("status", session.status.name)
            put("environmentMode", session.environmentMode.name)
            put("photoCount", photos.size)
            put("routePointCount", route.size)
            put("distanceMeters", SurveyMath.routeDistanceMeters(route))
            put("averageGpsAccuracyMeters", avgGps ?: JSONObject.NULL)
            put("sensors", JSONObject().apply {
                put("orientation", photos.any { it.orientation != null } || route.any { it.orientation != null })
                put("gps", photos.any { it.location != null } || route.isNotEmpty())
            })
            put("photos", JSONArray().apply {
                photos.forEach { photo ->
                    put(JSONObject().apply {
                        put("id", photo.id)
                        put("sequence", photo.sequence)
                        put("fileName", photo.fileName)
                        put("metadata", "metadata/${photo.sequence.toString().padStart(4, '0')}_${photo.id}.json")
                        put("original", photo.relativeOriginalPath)
                        put("reference", photo.referencePath ?: JSONObject.NULL)
                        put("preview", photo.previewPath ?: JSONObject.NULL)
                        put("thumbnail", photo.thumbnailPath ?: JSONObject.NULL)
                        put("sha256", photo.sha256 ?: JSONObject.NULL)
                        put("syncState", photo.syncState.name)
                        put("remoteOriginal", photo.remoteOriginalPath ?: JSONObject.NULL)
                        put("remoteVerifiedSha256", photo.remoteVerifiedSha256 ?: JSONObject.NULL)
                    })
                }
            })
            put("sync", JSONObject().apply {
                val summary = syncSummaryForPhotos(photos)
                put("verified", summary.verified)
                put("pending", summary.pending)
                put("errors", summary.errors)
            })
            put("route", "route.jsonl")
            put("routeGeoJson", if (routeGeoJsonFile(surveyId).exists()) "route.geojson" else JSONObject.NULL)
            put("poiCount", pois.size)
            put("pois", "pois.jsonl")
            val spatial = loadSpatialAnalysis(surveyId)
            put("spatial", JSONObject().apply {
                put("gisBuildings", spatial.buildings.size)
                put("identifiedPhotos", spatial.summary?.identifiedPhotos ?: 0)
                put("averageCoveragePercent", spatial.summary?.averageCoveragePercent ?: JSONObject.NULL)
                put("analysisSummary", if (spatial.summary != null) "analysis/summary.json" else JSONObject.NULL)
                put("coverage", if (spatial.coverage.isNotEmpty()) "analysis/coverage.json" else JSONObject.NULL)
                put("buildingInferences", if (spatial.inferences.isNotEmpty()) "analysis/building_inferences.json" else JSONObject.NULL)
                put("buildingsGeoJson", if (buildingsGeoJsonFile(surveyId).exists()) "gis/buildings.geojson" else JSONObject.NULL)
            })
            val phase4Repo = Phase4Repository(this@SurveyRepository)
            val phase4 = phase4Repo.summary(surveyId)
            val phase4Videos = phase4Repo.loadVideoScans(surveyId)
            put("phase4", JSONObject().apply {
                put("interiorAnchors", if (File(phase4Repo.interiorDir(surveyId), "anchors.jsonl").exists()) "interior/anchors.jsonl" else JSONObject.NULL)
                put("interiorRoute", if (phase4.interiorPoints > 0) "interior/route.jsonl" else JSONObject.NULL)
                put("interiorPoints", phase4.interiorPoints)
                put("arCoreVioPoints", phase4.interiorArCorePoints)
                put("spatialBursts", phase4.bursts)
                put("burstPhotos", phase4.burstPhotos)
                put("videoScans", phase4.videoScans)
                put("extractedVideoFrames", phase4.extractedVideoFrames)
                put("videos", JSONArray().apply {
                    phase4Videos.forEach { scan ->
                        put(JSONObject().apply {
                            put("id", scan.id)
                            put("status", scan.status.name)
                            put("video", scan.relativeVideoPath)
                            put("poses", scan.relativePosePath)
                            put("frames", JSONArray().apply { scan.frames.forEach { put(it.relativePath) } })
                        })
                    }
                })
                put("rawVideoCloudTransport", "LOCAL_ONLY_UNTIL_RESUMABLE_UPLOAD")
                put("positionSemanticsVersion", "1.3.1")
                put("note", "Interior coordinates are relative estimates anchored to exterior GNSS fixes. relativePoseUncertaintyMeters is NOT GNSS accuracy and must remain separate downstream.")
            })
            put("legacyZipBackup", "optional")
        }
        val out = File(surveyDir(surveyId), "manifest.json")
        atomicWrite(out, root.toString(2))
        return out
    }

    private fun syncSummaryForPhotos(photos: List<PhotoRecord>): SyncSummary {
        val counts = photos.groupingBy { it.syncState }.eachCount()
        return SyncSummary(
            totalPhotos = photos.size,
            localOnly = counts.getOrDefault(SyncState.LOCAL_ONLY, 0),
            processing = counts.getOrDefault(SyncState.PROCESSING, 0),
            queued = counts.getOrDefault(SyncState.QUEUED, 0),
            uploaded = counts.getOrDefault(SyncState.UPLOADED, 0),
            verified = counts.getOrDefault(SyncState.VERIFIED, 0),
            errors = counts.getOrDefault(SyncState.ERROR, 0)
        )
    }

    private fun ensureDirs(id: String) {
        surveyDir(id).mkdirs()
        originalsDir(id)
        referenceDir(id)
        previewsDir(id)
        thumbnailsDir(id)
        metadataDir(id)
        syncQueueDir(id)
        gisDir(id)
        analysisDir(id)
    }

    private fun metadataFile(surveyId: String, sequence: Int, photoId: String): File =
        File(metadataDir(surveyId), "${sequence.toString().padStart(4, '0')}_${photoId}.json")

    private fun atomicWrite(target: File, text: String) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(text)
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            target.writeText(text)
            temp.delete()
        }
    }

    private fun sanitize(value: String): String = value.trim().replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifBlank { "SURVEY" }
}
