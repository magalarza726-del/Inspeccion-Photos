package ec.espol.inspeccionphotos.storage

import ec.espol.inspeccionphotos.survey.*
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Phase4Recovery(
    val interruptedBursts: Int = 0,
    val videoScansToProcess: List<String> = emptyList(),
    val brokenVideoScans: Int = 0
)

class Phase4Repository(private val surveys: SurveyRepository) {
    fun interiorDir(surveyId: String) = File(surveys.surveyDir(surveyId), "interior").apply { mkdirs() }
    fun burstsDir(surveyId: String) = File(surveys.surveyDir(surveyId), "bursts").apply { mkdirs() }
    fun videoDir(surveyId: String) = File(surveys.surveyDir(surveyId), "video").apply { mkdirs() }
    fun videoFramesDir(surveyId: String) = File(surveys.surveyDir(surveyId), "video_frames").apply { mkdirs() }

    fun saveInteriorAnchor(anchor: InteriorAnchor) {
        val json = Phase4Json.anchor(anchor)
        File(interiorDir(anchor.surveyId), "anchors.jsonl").appendText(json.toString() + "\n")
        atomicWrite(File(interiorDir(anchor.surveyId), "current_anchor.json"), json.toString(2))
    }

    fun loadInteriorAnchor(surveyId: String): InteriorAnchor? = runCatching {
        Phase4Json.decodeAnchor(JSONObject(File(interiorDir(surveyId), "current_anchor.json").readText()))
    }.getOrNull()

    @Synchronized
    fun appendInteriorPose(sample: InteriorPoseSample) {
        File(interiorDir(sample.surveyId), "route.jsonl").appendText(Phase4Json.interiorPose(sample).toString() + "\n")
    }

    fun loadInteriorPoses(surveyId: String): List<InteriorPoseSample> {
        val f = File(interiorDir(surveyId), "route.jsonl")
        if (!f.exists()) return emptyList()
        return f.useLines { lines ->
            lines.mapNotNull { raw -> if (raw.isBlank()) null else runCatching { Phase4Json.decodeInteriorPose(JSONObject(raw)) }.getOrNull() }.toList()
        }
    }

    fun startBurst(surveyId: String): SpatialBurstRecord {
        val record = SpatialBurstRecord(UUID.randomUUID().toString(), surveyId, System.currentTimeMillis())
        saveBurst(record)
        return record
    }

    fun saveBurst(record: SpatialBurstRecord) {
        atomicWrite(File(burstsDir(record.surveyId), "${record.id}.json"), Phase4Json.burst(record).toString(2))
    }

    fun loadBursts(surveyId: String): List<SpatialBurstRecord> = burstsDir(surveyId).listFiles().orEmpty()
        .filter { it.extension == "json" }
        .mapNotNull { runCatching { Phase4Json.decodeBurst(JSONObject(it.readText())) }.getOrNull() }
        .sortedBy { it.startedAtEpochMs }

    fun createVideoScan(surveyId: String): Pair<VideoScanRecord, File> {
        val id = UUID.randomUUID().toString()
        val video = File(videoDir(surveyId), "scan_$id.mp4")
        val pose = File(videoDir(surveyId), "scan_${id}_poses.jsonl")
        val record = VideoScanRecord(
            id = id,
            surveyId = surveyId,
            startedAtEpochMs = System.currentTimeMillis(),
            relativeVideoPath = "video/${video.name}",
            relativePosePath = "video/${pose.name}"
        )
        saveVideoScan(record)
        return record to video
    }

    @Synchronized
    fun appendVideoPose(record: VideoScanRecord, sample: VideoPoseSample) {
        File(surveys.surveyDir(record.surveyId), record.relativePosePath).appendText(Phase4Json.videoPose(sample).toString() + "\n")
    }

    fun loadVideoPoses(record: VideoScanRecord): List<VideoPoseSample> {
        val f = File(surveys.surveyDir(record.surveyId), record.relativePosePath)
        if (!f.exists()) return emptyList()
        return f.useLines { lines -> lines.mapNotNull { raw -> if (raw.isBlank()) null else runCatching { Phase4Json.decodeVideoPose(JSONObject(raw)) }.getOrNull() }.toList() }
    }

    fun saveVideoScan(record: VideoScanRecord) {
        atomicWrite(File(videoDir(record.surveyId), "scan_${record.id}.json"), Phase4Json.videoScan(record).toString(2))
    }

    fun loadVideoScan(surveyId: String, scanId: String): VideoScanRecord? = runCatching {
        Phase4Json.decodeVideoScan(JSONObject(File(videoDir(surveyId), "scan_$scanId.json").readText()))
    }.getOrNull()

    fun loadVideoScans(surveyId: String): List<VideoScanRecord> = videoDir(surveyId).listFiles().orEmpty()
        .filter { it.name.startsWith("scan_") && it.extension == "json" }
        .mapNotNull { runCatching { Phase4Json.decodeVideoScan(JSONObject(it.readText())) }.getOrNull() }
        .sortedBy { it.startedAtEpochMs }

    fun recoverInterruptedRecords(surveyId: String): Phase4Recovery {
        var interruptedBursts = 0
        loadBursts(surveyId).filter { it.status == BurstStatus.ACTIVE }.forEach { burst ->
            saveBurst(burst.copy(status = BurstStatus.INTERRUPTED, endedAtEpochMs = burst.endedAtEpochMs ?: System.currentTimeMillis()))
            interruptedBursts++
        }

        val toProcess = mutableListOf<String>()
        var broken = 0
        loadVideoScans(surveyId).filter { it.status == VideoScanStatus.RECORDING }.forEach { scan ->
            val raw = File(surveys.surveyDir(surveyId), scan.relativeVideoPath)
            if (raw.exists() && raw.length() > 0L) {
                saveVideoScan(
                    scan.copy(
                        endedAtEpochMs = scan.endedAtEpochMs ?: System.currentTimeMillis(),
                        durationMillis = (scan.endedAtEpochMs ?: System.currentTimeMillis()) - scan.startedAtEpochMs,
                        status = VideoScanStatus.PROCESSING,
                        error = "Grabación interrumpida por cierre; intentando recuperar frames del MP4 local"
                    )
                )
                toProcess += scan.id
            } else {
                saveVideoScan(scan.copy(status = VideoScanStatus.ERROR, error = "Grabación interrumpida sin MP4 recuperable"))
                broken++
            }
        }
        if (interruptedBursts > 0 || toProcess.isNotEmpty() || broken > 0) surveys.writeManifest(surveyId)
        return Phase4Recovery(interruptedBursts, toProcess, broken)
    }

    fun summary(surveyId: String): Phase4Summary {
        val interior = loadInteriorPoses(surveyId)
        val bursts = loadBursts(surveyId)
        val videos = loadVideoScans(surveyId)
        return Phase4Summary(
            interiorPoints = interior.size,
            interiorArCorePoints = interior.count { it.source == InteriorTrackingSource.ARCORE_VIO },
            bursts = bursts.size,
            burstPhotos = bursts.sumOf { it.photoIds.size },
            videoScans = videos.size,
            extractedVideoFrames = videos.sumOf { it.extractedFrames }
        )
    }

    private fun atomicWrite(target: File, text: String) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(text)
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) { target.writeText(text); temp.delete() }
    }
}
