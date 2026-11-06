package ec.espol.inspeccionphotos.sync

import ec.espol.inspeccionphotos.integrity.IntegrityHasher
import ec.espol.inspeccionphotos.storage.SurveyJson
import ec.espol.inspeccionphotos.storage.SpatialJson
import ec.espol.inspeccionphotos.storage.Phase4Json
import ec.espol.inspeccionphotos.survey.*
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

class SupabaseException(message: String) : Exception(message)

class SupabaseRestClient {
    private fun root(config: SupabaseConfig): String = config.projectUrl.trim().trimEnd('/').removeSuffix("/rest/v1")

    private fun connection(
        url: String,
        method: String,
        config: SupabaseConfig,
        accessToken: String? = null
    ): HttpURLConnection {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 20_000
        c.readTimeout = 60_000
        c.doInput = true
        c.setRequestProperty("apikey", config.publishableKey.trim())
        c.setRequestProperty("Accept", "application/json")
        accessToken?.takeIf { it.isNotBlank() }?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        return c
    }

    private fun errorBody(c: HttpURLConnection): String = runCatching {
        c.errorStream?.use { it.readBytes().toString(Charsets.UTF_8).take(1500) }
    }.getOrNull().orEmpty()

    private fun ensureSuccess(c: HttpURLConnection) {
        if (c.responseCode !in 200..299) {
            throw SupabaseException("Supabase HTTP ${c.responseCode}: ${errorBody(c)}")
        }
    }

    private fun readText(c: HttpURLConnection): String {
        ensureSuccess(c)
        return c.inputStream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
    }

    fun signIn(config: SupabaseConfig, email: String, password: String): AuthSession {
        require(config.configured) { "Supabase no está configurado" }
        val c = connection("${root(config)}/auth/v1/token?grant_type=password", "POST", config).apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        val payload = JSONObject().put("email", email.trim()).put("password", password)
        c.outputStream.use { it.write(payload.toString().toByteArray()) }
        return authSession(JSONObject(readText(c)), email.trim())
    }

    fun signUp(config: SupabaseConfig, email: String, password: String): AuthSession? {
        require(config.configured) { "Supabase no está configurado" }
        val c = connection("${root(config)}/auth/v1/signup", "POST", config).apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        c.outputStream.use {
            it.write(JSONObject().put("email", email.trim()).put("password", password).toString().toByteArray())
        }
        val j = JSONObject(readText(c))
        if (j.optString("access_token").isBlank()) return null
        return authSession(j, email.trim())
    }

    fun refresh(config: SupabaseConfig, session: AuthSession): AuthSession {
        val c = connection("${root(config)}/auth/v1/token?grant_type=refresh_token", "POST", config).apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        c.outputStream.use { it.write(JSONObject().put("refresh_token", session.refreshToken).toString().toByteArray()) }
        return authSession(JSONObject(readText(c)), session.email, session)
    }

    fun ping(config: SupabaseConfig, session: AuthSession) {
        val c = connection("${root(config)}/rest/v1/inspeccion_photos_surveys?select=id&limit=1", "GET", config, session.accessToken)
        readText(c)
    }

    fun upsertSurvey(config: SupabaseConfig, session: AuthSession, survey: SurveySession) {
        val body = JSONObject().apply {
            put("id", survey.id)
            put("user_id", session.authUserId)
            put("place", survey.place)
            put("schema_version", survey.schemaVersion)
            put("started_at_epoch_ms", survey.startedAtEpochMs)
            put("ended_at_epoch_ms", survey.endedAtEpochMs ?: JSONObject.NULL)
            put("status", survey.status.name)
            put("payload", SurveyJson.session(survey))
            put("updated_at", Instant.now().toString())
        }
        upsert(config, session, "inspeccion_photos_surveys", body)
    }

    fun upsertPhoto(config: SupabaseConfig, session: AuthSession, photo: PhotoRecord) {
        val body = JSONObject().apply {
            put("id", photo.id)
            put("user_id", session.authUserId)
            put("survey_id", photo.surveyId)
            put("sequence", photo.sequence)
            put("file_name", photo.fileName)
            put("sha256", photo.sha256 ?: JSONObject.NULL)
            put("storage_path", photo.remoteOriginalPath ?: JSONObject.NULL)
            put("sync_state", photo.syncState.name)
            put("metadata", SurveyJson.photo(photo))
            put("updated_at", Instant.now().toString())
        }
        upsert(config, session, "inspeccion_photos_photos", body)
    }

    fun upsertSpatialAnalysis(
        config: SupabaseConfig,
        session: AuthSession,
        surveyId: String,
        bundle: SpatialAnalysisBundle,
        routeGeoJsonFile: File
    ) {
        val summary = bundle.summary ?: return
        val body = JSONObject().apply {
            put("survey_id", surveyId)
            put("user_id", session.authUserId)
            put("schema_version", summary.schemaVersion)
            put("route_geojson", if (routeGeoJsonFile.exists()) JSONObject(routeGeoJsonFile.readText()) else JSONObject.NULL)
            put("buildings", JSONArray().apply {
                bundle.buildings.forEach { building ->
                    put(JSONObject().apply {
                        put("id", building.id)
                        put("name", building.name)
                        put("faculty", building.faculty ?: JSONObject.NULL)
                        put("source", building.source)
                        put("polygon", JSONArray().apply {
                            building.polygon.forEach { point -> put(JSONArray().put(point.longitude).put(point.latitude)) }
                        })
                    })
                }
            })
            put("building_inferences", JSONArray().apply { bundle.inferences.forEach { put(SpatialJson.inference(it)) } })
            put("coverage", JSONArray().apply { bundle.coverage.forEach { put(SpatialJson.buildingCoverage(it)) } })
            put("summary", SpatialJson.summary(summary))
            put("updated_at", Instant.now().toString())
        }
        upsert(config, session, "inspeccion_photos_spatial_analysis", body, conflict = "survey_id")
    }

    fun upsertPhase4Data(
        config: SupabaseConfig,
        session: AuthSession,
        surveyId: String,
        summary: Phase4Summary,
        interior: List<InteriorPoseSample>,
        bursts: List<SpatialBurstRecord>,
        videos: List<VideoScanRecord>
    ) {
        val body = JSONObject().apply {
            put("survey_id", surveyId)
            put("user_id", session.authUserId)
            put("schema_version", CURRENT_SCHEMA_VERSION)
            put("interior_route", JSONArray().apply { interior.forEach { put(Phase4Json.interiorPose(it)) } })
            put("bursts", JSONArray().apply { bursts.forEach { put(Phase4Json.burst(it)) } })
            put("video_scans", JSONArray().apply { videos.forEach { put(Phase4Json.videoScan(it)) } })
            put("summary", JSONObject().apply {
                put("interiorPoints", summary.interiorPoints)
                put("interiorArCorePoints", summary.interiorArCorePoints)
                put("spatialBursts", summary.bursts)
                put("burstPhotos", summary.burstPhotos)
                put("videoScans", summary.videoScans)
                put("extractedVideoFrames", summary.extractedVideoFrames)
                put("rawVideoTransport", "LOCAL_ONLY_UNTIL_RESUMABLE_UPLOAD")
            })
            put("updated_at", Instant.now().toString())
        }
        upsert(config, session, "inspeccion_photos_phase4_data", body, conflict = "survey_id")
    }

    private fun upsert(config: SupabaseConfig, session: AuthSession, table: String, body: JSONObject, conflict: String = "id") {
        val c = connection("${root(config)}/rest/v1/$table?on_conflict=$conflict", "POST", config, session.accessToken).apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
        }
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        ensureSuccess(c)
        c.inputStream?.close()
    }

    fun uploadFile(
        config: SupabaseConfig,
        session: AuthSession,
        bucket: String,
        path: String,
        file: File,
        mime: String
    ) {
        require(file.exists() && file.length() > 0L) { "Archivo local no disponible: ${file.name}" }
        val c = connection("${root(config)}/storage/v1/object/$bucket/${encodedPath(path)}", "POST", config, session.accessToken).apply {
            doOutput = true
            setRequestProperty("Content-Type", mime)
            setRequestProperty("x-upsert", "true")
            setFixedLengthStreamingMode(file.length())
        }
        file.inputStream().buffered().use { input -> c.outputStream.buffered().use { output -> input.copyTo(output) } }
        ensureSuccess(c)
        c.inputStream?.close()
    }

    fun remoteSha256(
        config: SupabaseConfig,
        session: AuthSession,
        bucket: String,
        path: String
    ): String {
        val c = connection("${root(config)}/storage/v1/object/$bucket/${encodedPath(path)}", "GET", config, session.accessToken)
        ensureSuccess(c)
        return c.inputStream.buffered().use(IntegrityHasher::sha256)
    }

    private fun authSession(j: JSONObject, fallbackEmail: String, old: AuthSession? = null): AuthSession {
        val user = j.optJSONObject("user") ?: JSONObject()
        val access = j.optString("access_token")
        val refresh = j.optString("refresh_token", old?.refreshToken.orEmpty())
        val userId = user.optString("id", old?.authUserId.orEmpty())
        if (access.isBlank() || refresh.isBlank() || userId.isBlank()) throw SupabaseException("Supabase no devolvió una sesión utilizable")
        return AuthSession(
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000L + j.optLong("expires_in", 3600L) - 30L,
            email = user.optString("email", old?.email ?: fallbackEmail),
            authUserId = userId
        )
    }

    private fun encodedPath(path: String): String = path.split('/').joinToString("/") {
        URLEncoder.encode(it, StandardCharsets.UTF_8.toString()).replace("+", "%20")
    }
}
