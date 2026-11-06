package ec.espol.inspeccionphotos.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import ec.espol.inspeccionphotos.camera.PhotoMetadataReader
import ec.espol.inspeccionphotos.camera.PoseResolver
import ec.espol.inspeccionphotos.export.SurveyBackupExporter
import ec.espol.inspeccionphotos.location.LocationTracker
import ec.espol.inspeccionphotos.sensors.OrientationTracker
import ec.espol.inspeccionphotos.sensors.InertialTracker
import ec.espol.inspeccionphotos.interior.InteriorDeadReckoner
import ec.espol.inspeccionphotos.interior.EnvironmentModeAdvisor
import ec.espol.inspeccionphotos.storage.SettingsStore
import ec.espol.inspeccionphotos.storage.SurveyRepository
import ec.espol.inspeccionphotos.storage.Phase4Repository
import ec.espol.inspeccionphotos.spatial.SpatialAnalysisEngine
import ec.espol.inspeccionphotos.survey.*
import ec.espol.inspeccionphotos.sync.SupabaseRestClient
import ec.espol.inspeccionphotos.sync.SupabaseSessionVault
import ec.espol.inspeccionphotos.util.SurveyMath
import ec.espol.inspeccionphotos.worker.Phase2Scheduler
import ec.espol.inspeccionphotos.worker.Phase3Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private enum class AppScreen { HOME, NEW_SURVEY, SURVEYS, SURVEY_DETAIL, MAP, ACTIVE, INTERIOR, BURST, VIDEO_SCAN, AUDIT, SYNC, SETTINGS }

@Composable
fun SurveyLayerApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { SurveyRepository(context) }
    val settings = remember { SettingsStore(context) }
    val sessionVault = remember { SupabaseSessionVault(context) }
    val supabaseClient = remember { SupabaseRestClient() }
    val locationTracker = remember { LocationTracker(context) }
    val orientationTracker = remember { OrientationTracker(context) }
    val inertialTracker = remember { InertialTracker(context) }
    val phase4 = remember { Phase4Repository(repository) }
    val interiorReckoner = remember { InteriorDeadReckoner() }
    val environmentAdvisor = remember { EnvironmentModeAdvisor() }
    val scope = rememberCoroutineScope()

    var screen by rememberSaveableCompat { mutableStateOf(AppScreen.HOME) }
    var surveys by remember { mutableStateOf(repository.listSurveys()) }
    var activeSurvey by remember { mutableStateOf<SurveySession?>(null) }
    var selectedSurvey by remember { mutableStateOf<SurveySession?>(null) }
    var spatialBundle by remember { mutableStateOf(SpatialAnalysisBundle()) }
    var gisImportSurveyId by remember { mutableStateOf<String?>(null) }
    var activePhotos by remember { mutableStateOf<List<PhotoRecord>>(emptyList()) }
    var routePoints by remember { mutableStateOf<List<RoutePoint>>(emptyList()) }
    var currentLocation by remember { mutableStateOf<LocationSnapshot?>(null) }
    var currentOrientation by remember { mutableStateOf<OrientationSnapshot?>(null) }
    var currentInertial by remember { mutableStateOf<InertialSnapshot?>(null) }
    var currentInteriorPose by remember { mutableStateOf<InteriorPoseSample?>(null) }
    var lastReliableExterior by remember { mutableStateOf<LocationSnapshot?>(null) }
    var captureProfile by remember { mutableStateOf(settings.captureProfile) }
    var autoInteriorSwitch by remember { mutableStateOf(settings.autoInteriorSwitch) }
    var supabaseConfig by remember { mutableStateOf(settings.supabaseConfig) }
    var signedInEmail by remember { mutableStateOf(sessionVault.load()?.email) }
    var syncSummary by remember { mutableStateOf(SyncSummary()) }
    var syncStatusMessage by remember { mutableStateOf<String?>(null) }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var audit by remember { mutableStateOf<AuditSummary?>(null) }
    var recoverySurvey by remember { mutableStateOf(repository.activeSurvey()) }
    var discardCandidate by remember { mutableStateOf<SurveySession?>(null) }
    var pendingStartPlace by remember { mutableStateOf<String?>(null) }

    fun refreshSurveys() { surveys = repository.listSurveys() }
    fun refreshSyncSummary() {
        scope.launch {
            syncSummary = withContext(Dispatchers.IO) { repository.syncSummary() }
        }
    }

    fun activate(session: SurveySession) {
        activeSurvey = repository.loadSession(session.id) ?: session
        activePhotos = repository.loadPhotos(session.id)
        routePoints = repository.loadRoute(session.id)
        currentLocation = routePoints.lastOrNull()?.location
        currentOrientation = routePoints.lastOrNull()?.orientation
        lastReliableExterior = routePoints.asReversed().firstOrNull { (it.location.horizontalAccuracyMeters ?: Float.MAX_VALUE) <= 12f }?.location
        phase4.loadInteriorAnchor(session.id)?.let(interiorReckoner::setAnchor) ?: interiorReckoner.clearAnchor()
        currentInteriorPose = phase4.loadInteriorPoses(session.id).lastOrNull()
        val phase4Recovery = phase4.recoverInterruptedRecords(session.id)
        phase4Recovery.videoScansToProcess.forEach { scanId ->
            ec.espol.inspeccionphotos.worker.Phase4Scheduler.extractRepresentativeFrames(context, session.id, scanId)
        }
        if (phase4Recovery.interruptedBursts > 0 || phase4Recovery.videoScansToProcess.isNotEmpty() || phase4Recovery.brokenVideoScans > 0) {
            Toast.makeText(context, "Fase 4 recuperada: fotos seguras conservadas; escaneos interrumpidos revisados", Toast.LENGTH_LONG).show()
        }
        screen = AppScreen.ACTIVE
    }

    fun startSurvey(place: String) {
        val session = repository.createSurvey(place)
        refreshSurveys()
        activate(session)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val place = pendingStartPlace
        pendingStartPlace = null
        val cameraGranted = result[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (place != null && cameraGranted) {
            startSurvey(place)
            val locationGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (!locationGranted) Toast.makeText(context, "GPS no autorizado: las fotos quedarán marcadas con baja confianza", Toast.LENGTH_LONG).show()
        } else if (place != null) {
            Toast.makeText(context, "La cámara es necesaria para iniciar el levantamiento", Toast.LENGTH_LONG).show()
        }
    }

    fun requestStart(place: String) {
        if (repository.activeSurvey() != null) {
            recoverySurvey = repository.activeSurvey()
            Toast.makeText(context, "Ya existe un levantamiento activo", Toast.LENGTH_SHORT).show()
            return
        }
        val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (cameraGranted && (fine || coarse)) startSurvey(place)
        else {
            pendingStartPlace = place
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    val gisLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val surveyId = gisImportSurveyId
        gisImportSurveyId = null
        if (uri == null || surveyId == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val count = repository.importBuildingsGeoJson(surveyId, input)
                        val bundle = SpatialAnalysisEngine(repository).analyzeSurvey(surveyId)
                        count to bundle
                    } ?: error("No se pudo abrir el GeoJSON")
                }
            }
            result.onSuccess { (count, bundle) ->
                spatialBundle = bundle
                Toast.makeText(context, "$count edificio(s) GIS importados y analizados", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(context, it.message ?: "No se pudo importar el GeoJSON", Toast.LENGTH_LONG).show()
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val survey = activeSurvey ?: return@rememberLauncherForActivityResult
        val pending = repository.loadPendingCapture(survey.id)
        if (!ok || pending == null) {
            repository.clearPendingCapture(survey.id)
            Toast.makeText(context, "Captura cancelada", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val original = File(repository.surveyDir(survey.id), pending.relativeOriginalPath)
                if (!original.exists() || original.length() == 0L) return@withContext null
                val camera = PhotoMetadataReader.read(original)
                val route = repository.loadRoute(survey.id)
                val pose = PoseResolver.resolve(camera?.exifTimestampEpochMs, route, pending)
                val capturedAt = camera?.exifTimestampEpochMs ?: System.currentTimeMillis()
                val record = PhotoRecord(
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
                )
                // Regla crítica: primero original + metadata local. Hash, previews y red van después.
                repository.savePhoto(record)
                record
            }
            if (result != null) {
                activeSurvey = repository.loadSession(survey.id)
                activePhotos = repository.loadPhotos(survey.id)
                routePoints = repository.loadRoute(survey.id)
                refreshSurveys()
                Phase2Scheduler.enqueueLocalProcessing(context)
                Toast.makeText(context, "Foto ${result.sequence.toString().padStart(3, '0')} segura localmente", Toast.LENGTH_SHORT).show()
            } else {
                repository.clearPendingCapture(survey.id)
                Toast.makeText(context, "No se pudo confirmar el original", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun effectivePosition(survey: SurveySession): LocationSnapshot? =
        if (survey.environmentMode == EnvironmentMode.INTERIOR) {
            currentInteriorPose?.let(interiorReckoner::toApproximateLocation) ?: currentLocation
        } else currentLocation

    fun capturePhoto() {
        val survey = activeSurvey ?: return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "Permiso de cámara requerido", Toast.LENGTH_SHORT).show()
            return
        }
        val (_, file) = repository.prepareCapture(survey, effectivePosition(survey), currentOrientation)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        runCatching { cameraLauncher.launch(uri) }
            .onFailure {
                repository.clearPendingCapture(survey.id)
                Toast.makeText(context, "No se pudo abrir la cámara", Toast.LENGTH_LONG).show()
            }
    }

    fun createInteriorAnchor(survey: SurveySession): InteriorAnchor? {
        val loc = lastReliableExterior ?: currentLocation ?: return null
        return InteriorAnchor(
            id = UUID.randomUUID().toString(),
            surveyId = survey.id,
            createdAtEpochMs = System.currentTimeMillis(),
            latitude = loc.latitude, longitude = loc.longitude, altitudeMeters = loc.altitudeMeters,
            horizontalAccuracyMeters = loc.horizontalAccuracyMeters,
            headingDegrees = currentOrientation?.azimuthDegrees,
            pressureHpa = currentInertial?.pressureHpa
        ).also { anchor ->
            interiorReckoner.setAnchor(anchor)
            scope.launch(Dispatchers.IO) { phase4.saveInteriorAnchor(anchor) }
        }
    }

    fun changeEnvironment(survey: SurveySession, next: EnvironmentMode, automatic: Boolean = false) {
        if (next == EnvironmentMode.INTERIOR) { interiorReckoner.clearAnchor(); createInteriorAnchor(survey) }
        activeSurvey = repository.setEnvironment(survey.id, next)
        environmentAdvisor.reset()
        if (automatic) Toast.makeText(context, if (next == EnvironmentMode.INTERIOR) "GPS degradado: modo Interior activado" else "GPS fiable recuperado: modo Exterior activado", Toast.LENGTH_LONG).show()
    }

    fun persistInteriorPose(survey: SurveySession, pose: InteriorPoseSample, orientation: OrientationSnapshot?) {
        currentInteriorPose = pose
        val approx = interiorReckoner.toApproximateLocation(pose) ?: return
        val point = RoutePoint(pose.timestampEpochMs, approx, orientation, EnvironmentMode.INTERIOR)
        routePoints = routePoints + point
        scope.launch(Dispatchers.IO) {
            phase4.appendInteriorPose(pose)
            repository.appendRoutePoint(survey.id, point, routePoints.size)
        }
    }

    DisposableEffect(activeSurvey?.id, activeSurvey?.environmentMode, captureProfile, autoInteriorSwitch) {
        val survey = activeSurvey
        if (survey != null) {
            var lastBarometerPoseAt = 0L
            orientationTracker.start { currentOrientation = it }
            inertialTracker.start(
                onSnapshot = { inertial ->
                    currentInertial = inertial
                    if (survey.environmentMode == EnvironmentMode.INTERIOR && inertial.pressureHpa != null && inertial.timestampEpochMs - lastBarometerPoseAt >= 2_000) {
                        lastBarometerPoseAt = inertial.timestampEpochMs
                        interiorReckoner.onBarometer(survey.id, inertial.timestampEpochMs, inertial.pressureHpa, currentOrientation?.azimuthDegrees, inertial.stepCount)
                            ?.let { persistInteriorPose(survey, it, currentOrientation) }
                    }
                },
                onStep = { totalSteps, inertial ->
                    currentInertial = inertial
                    if (survey.environmentMode == EnvironmentMode.INTERIOR) {
                        interiorReckoner.onStep(survey.id, System.currentTimeMillis(), currentOrientation?.azimuthDegrees, totalSteps)
                            ?.let { persistInteriorPose(survey, it, currentOrientation) }
                    }
                }
            )
            locationTracker.start(captureProfile) { loc ->
                currentLocation = loc
                if ((loc.horizontalAccuracyMeters ?: Float.MAX_VALUE) <= 12f) lastReliableExterior = loc
                if (survey.environmentMode == EnvironmentMode.EXTERIOR) {
                    val point = RoutePoint(loc.timestampEpochMs, loc, currentOrientation, EnvironmentMode.EXTERIOR)
                    val updatedRoute = routePoints + point
                    routePoints = updatedRoute
                    scope.launch(Dispatchers.IO) { repository.appendRoutePoint(survey.id, point, updatedRoute.size) }
                }
                if (autoInteriorSwitch) {
                    environmentAdvisor.observe(survey.environmentMode, loc)?.let { suggested ->
                        if (suggested != survey.environmentMode) changeEnvironment(survey, suggested, automatic = true)
                    }
                }
            }
        }
        onDispose {
            locationTracker.stop()
            orientationTracker.stop()
            inertialTracker.stop()
        }
    }

    LaunchedEffect(activeSurvey?.id) {
        val survey = activeSurvey ?: return@LaunchedEffect
        while (activeSurvey?.id == survey.id) {
            elapsedMillis = System.currentTimeMillis() - survey.startedAtEpochMs
            delay(1_000)
        }
    }

    // A complete loss of GNSS produces no Location callback at all. Treat prolonged staleness
    // as degraded positioning so automatic Interior mode still works inside buildings.
    LaunchedEffect(activeSurvey?.id, autoInteriorSwitch) {
        if (!autoInteriorSwitch) return@LaunchedEffect
        while (true) {
            val survey = activeSurvey ?: break
            if (survey.environmentMode == EnvironmentMode.EXTERIOR) {
                val loc = currentLocation
                val stale = loc == null || System.currentTimeMillis() - loc.timestampEpochMs > 8_000L
                if (stale) {
                    environmentAdvisor.observe(EnvironmentMode.EXTERIOR, null)?.let { suggested ->
                        if (suggested == EnvironmentMode.INTERIOR) changeEnvironment(survey, suggested, automatic = true)
                    }
                }
            }
            delay(1_500)
        }
    }

    LaunchedEffect(activeSurvey?.id) {
        val survey = activeSurvey ?: return@LaunchedEffect
        val pending = withContext(Dispatchers.IO) { repository.recoverPendingCapture(survey.id) }
        if (pending != null) {
            val recovered = withContext(Dispatchers.IO) {
                val original = File(repository.surveyDir(survey.id), pending.relativeOriginalPath)
                val camera = PhotoMetadataReader.read(original)
                val route = repository.loadRoute(survey.id)
                val pose = PoseResolver.resolve(camera?.exifTimestampEpochMs, route, pending)
                val record = PhotoRecord(
                    id = pending.photoId,
                    surveyId = survey.id,
                    sequence = pending.sequence,
                    fileName = pending.fileName,
                    relativeOriginalPath = pending.relativeOriginalPath,
                    capturedAtEpochMs = camera?.exifTimestampEpochMs ?: pending.startedAtEpochMs,
                    place = pending.place,
                    location = pose.location,
                    orientation = pose.orientation,
                    camera = camera,
                    gpsQuality = pose.location?.quality ?: GpsQuality.UNAVAILABLE,
                    poseConfidence = "RECUPERADA_${pose.confidence}",
                    syncState = SyncState.LOCAL_ONLY
                )
                repository.savePhoto(record)
                record
            }
            activeSurvey = repository.loadSession(survey.id)
            activePhotos = repository.loadPhotos(survey.id)
            refreshSurveys()
            Phase2Scheduler.enqueueLocalProcessing(context)
            Toast.makeText(context, "Se recuperó una captura pendiente tras el cierre", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(screen) {
        if (screen == AppScreen.SYNC) {
            while (screen == AppScreen.SYNC) {
                syncSummary = withContext(Dispatchers.IO) { repository.syncSummary() }
                signedInEmail = withContext(Dispatchers.IO) { sessionVault.load()?.email }
                delay(1_500)
            }
        }
    }

    fun signIn(email: String, password: String) {
        scope.launch {
            syncStatusMessage = "Iniciando sesión…"
            val result = withContext(Dispatchers.IO) { runCatching { supabaseClient.signIn(supabaseConfig, email, password) } }
            result.onSuccess { session ->
                sessionVault.save(session)
                signedInEmail = session.email
                syncStatusMessage = "Sesión iniciada. La contraseña no se guardó."
                Phase2Scheduler.enqueueLocalProcessing(context)
                Phase2Scheduler.enqueueSync(context, manual = true)
            }.onFailure { syncStatusMessage = it.message ?: "No se pudo iniciar sesión" }
        }
    }

    fun signUp(email: String, password: String) {
        scope.launch {
            syncStatusMessage = "Creando cuenta…"
            val result = withContext(Dispatchers.IO) { runCatching { supabaseClient.signUp(supabaseConfig, email, password) } }
            result.onSuccess { session ->
                if (session == null) {
                    syncStatusMessage = "Cuenta creada. Revisa tu correo si Supabase exige confirmación y luego inicia sesión."
                } else {
                    sessionVault.save(session)
                    signedInEmail = session.email
                    syncStatusMessage = "Cuenta creada y sesión iniciada."
                    Phase2Scheduler.enqueueSync(context, manual = true)
                }
            }.onFailure { syncStatusMessage = it.message ?: "No se pudo crear la cuenta" }
        }
    }

    fun analyzeSpatial(surveyId: String, openMap: Boolean = false) {
        spatialBundle = repository.loadSpatialAnalysis(surveyId)
        if (openMap) screen = AppScreen.MAP
        scope.launch {
            val analyzed = withContext(Dispatchers.IO) { SpatialAnalysisEngine(repository).analyzeSurvey(surveyId) }
            spatialBundle = analyzed
            if (supabaseConfig.configured && supabaseConfig.autoSync) Phase2Scheduler.enqueueSync(context, manual = false)
        }
    }

    fun requestGisImport(surveyId: String) {
        gisImportSurveyId = surveyId
        gisLauncher.launch(arrayOf("application/geo+json", "application/json", "text/plain", "*/*"))
    }

    Surface(modifier.fillMaxSize(), color = SpatialBg) {
        Column(Modifier.fillMaxSize()) {
            when (screen) {
                AppScreen.HOME -> HomeScreen(
                    activeSurvey = activeSurvey,
                    onResumeActive = { activeSurvey?.let(::activate) },
                    onNewSurvey = { screen = AppScreen.NEW_SURVEY },
                    onSurveys = { refreshSurveys(); screen = AppScreen.SURVEYS },
                    onSync = { refreshSyncSummary(); screen = AppScreen.SYNC },
                    onSettings = { screen = AppScreen.SETTINGS }
                )
                AppScreen.NEW_SURVEY -> NewSurveyScreen(onBack = { screen = AppScreen.HOME }, onStart = ::requestStart)
                AppScreen.SURVEYS -> SurveyListScreen(surveys, onBack = { screen = AppScreen.HOME }) { survey ->
                    selectedSurvey = survey
                    spatialBundle = repository.loadSpatialAnalysis(survey.id)
                    screen = AppScreen.SURVEY_DETAIL
                }
                AppScreen.SURVEY_DETAIL -> {
                    val survey = selectedSurvey
                    if (survey != null) {
                        val photos = repository.loadPhotos(survey.id)
                        val route = repository.loadRoute(survey.id)
                        SurveyDetailScreen(
                            survey = repository.loadSession(survey.id) ?: survey,
                            photos = photos,
                            distanceMeters = SurveyMath.routeDistanceMeters(route),
                            spatial = spatialBundle,
                            phase4 = phase4.summary(survey.id),
                            onBack = { screen = AppScreen.SURVEYS },
                            onContinue = if (survey.status == SurveyStatus.ACTIVE) ({ activate(survey) }) else null,
                            onManualZipBackup = {
                                scope.launch {
                                    val zip = withContext(Dispatchers.IO) {
                                        SpatialAnalysisEngine(repository).analyzeSurvey(survey.id)
                                        SurveyBackupExporter.createOptionalFullZip(context, repository, survey.id)
                                    }
                                    SurveyBackupExporter.share(context, zip)
                                }
                            },
                            onOpenMap = { analyzeSpatial(survey.id, openMap = true) },
                            onImportGis = { requestGisImport(survey.id) },
                            onAnalyzeSpatial = { analyzeSpatial(survey.id) }
                        )
                    }
                }
                AppScreen.MAP -> {
                    val survey = selectedSurvey ?: activeSurvey
                    if (survey != null) {
                        SurveyMapScreen(
                            survey = repository.loadSession(survey.id) ?: survey,
                            route = repository.loadRoute(survey.id),
                            photos = repository.loadPhotos(survey.id),
                            pois = repository.loadPois(survey.id),
                            spatial = spatialBundle,
                            previewPathResolver = { photo -> repository.relativeFile(survey.id, photo.previewPath)?.absolutePath },
                            onBack = {
                                screen = if (activeSurvey?.id == survey.id && survey.status == SurveyStatus.ACTIVE) AppScreen.ACTIVE else AppScreen.SURVEY_DETAIL
                            },
                            onImportGis = { requestGisImport(survey.id) },
                            onAnalyze = { analyzeSpatial(survey.id) }
                        )
                    }
                }
                AppScreen.ACTIVE -> activeSurvey?.let { survey ->
                    ActiveSurveyScreen(
                        survey = survey,
                        location = effectivePosition(survey),
                        orientation = currentOrientation,
                        photoCount = activePhotos.size,
                        routeDistanceMeters = SurveyMath.routeDistanceMeters(routePoints),
                        elapsedMillis = elapsedMillis,
                        onCapture = ::capturePhoto,
                        onAddPoi = { type ->
                            repository.appendPoi(
                                PoiRecord(UUID.randomUUID().toString(), survey.id, type, System.currentTimeMillis(), effectivePosition(survey), currentOrientation)
                            )
                            Toast.makeText(context, "$type registrado", Toast.LENGTH_SHORT).show()
                        },
                        interiorPose = currentInteriorPose,
                        onToggleEnvironment = {
                            val next = if (survey.environmentMode == EnvironmentMode.EXTERIOR) EnvironmentMode.INTERIOR else EnvironmentMode.EXTERIOR
                            changeEnvironment(survey, next)
                        },
                        onInteriorTracking = {
                            if (survey.environmentMode != EnvironmentMode.INTERIOR) changeEnvironment(survey, EnvironmentMode.INTERIOR)
                            interiorReckoner.beginArCoreSegment()
                            screen = AppScreen.INTERIOR
                        },
                        onSpatialBurst = { screen = AppScreen.BURST },
                        onVideoScan = { screen = AppScreen.VIDEO_SCAN },
                        onMap = {
                            selectedSurvey = survey
                            analyzeSpatial(survey.id, openMap = true)
                        },
                        onFinishRequest = {
                            Toast.makeText(context, "Analizando cobertura antes de finalizar…", Toast.LENGTH_SHORT).show()
                            scope.launch {
                                spatialBundle = withContext(Dispatchers.IO) { SpatialAnalysisEngine(repository).analyzeSurvey(survey.id) }
                                audit = withContext(Dispatchers.IO) { repository.audit(survey.id) }
                                screen = AppScreen.AUDIT
                            }
                        },
                        onHome = { screen = AppScreen.HOME }
                    )
                }
                AppScreen.INTERIOR -> activeSurvey?.let { survey ->
                    InteriorTrackingScreen(
                        survey = survey,
                        pose = currentInteriorPose,
                        onArPose = { arPose ->
                            scope.launch {
                                val fresh = activeSurvey ?: survey
                                if (fresh.environmentMode != EnvironmentMode.INTERIOR) return@launch
                                val pose = interiorReckoner.onArCore(fresh.id, arPose, currentOrientation?.azimuthDegrees, currentInertial?.stepCount ?: 0L)
                                if (pose != null) persistInteriorPose(fresh, pose, currentOrientation)
                            }
                        },
                        onBack = { screen = AppScreen.ACTIVE }
                    )
                }
                AppScreen.BURST -> activeSurvey?.let { survey ->
                    SpatialBurstScreen(
                        survey = survey, location = effectivePosition(survey), orientation = currentOrientation,
                        interiorPose = currentInteriorPose,
                        repository = repository, phase4 = phase4,
                        onPhotoCommitted = {
                            activeSurvey = repository.loadSession(survey.id)
                            activePhotos = repository.loadPhotos(survey.id)
                            refreshSurveys()
                        },
                        onBack = { screen = AppScreen.ACTIVE }
                    )
                }
                AppScreen.VIDEO_SCAN -> activeSurvey?.let { survey ->
                    VideoScanScreen(
                        survey = survey, location = effectivePosition(survey), orientation = currentOrientation,
                        inertial = currentInertial, interiorPose = currentInteriorPose,
                        repository = repository, phase4 = phase4,
                        onBack = { screen = AppScreen.ACTIVE }
                    )
                }
                AppScreen.AUDIT -> {
                    val survey = activeSurvey
                    val summary = audit
                    if (survey != null && summary != null) {
                        AuditScreen(
                            survey = survey,
                            audit = summary,
                            onContinue = { screen = AppScreen.ACTIVE },
                            onFinalize = {
                                repository.finalizeSurvey(survey.id)
                                Phase2Scheduler.enqueueLocalProcessing(context)
                                Phase3Scheduler.analyzeSurvey(context, survey.id)
                                activeSurvey = null
                                activePhotos = emptyList()
                                routePoints = emptyList()
                                refreshSurveys()
                                audit = null
                                screen = AppScreen.HOME
                            }
                        )
                    }
                }
                AppScreen.SYNC -> SyncScreen(
                    summary = syncSummary,
                    config = supabaseConfig,
                    signedInEmail = signedInEmail,
                    statusMessage = syncStatusMessage,
                    onSignIn = ::signIn,
                    onSignUp = ::signUp,
                    onSignOut = {
                        sessionVault.clear()
                        signedInEmail = null
                        syncStatusMessage = "Sesión cerrada. Los archivos locales permanecen intactos."
                    },
                    onSyncNow = {
                        Phase2Scheduler.enqueueLocalProcessing(context)
                        Phase2Scheduler.enqueueSync(context, manual = true)
                        syncStatusMessage = "Sincronización programada. WorkManager continuará cuando haya red."
                    },
                    onBack = { screen = AppScreen.HOME }
                )
                AppScreen.SETTINGS -> SettingsScreen(
                    profile = captureProfile,
                    storagePath = repository.storageRoot().absolutePath,
                    supabaseConfig = supabaseConfig,
                    autoInteriorSwitch = autoInteriorSwitch,
                    onProfileChange = { captureProfile = it; settings.captureProfile = it },
                    onAutoInteriorSwitchChange = { autoInteriorSwitch = it; settings.autoInteriorSwitch = it },
                    onSupabaseSave = { newConfig ->
                        val backendChanged = newConfig.projectUrl.trim().trimEnd('/') != supabaseConfig.projectUrl.trim().trimEnd('/') ||
                            newConfig.publishableKey.trim() != supabaseConfig.publishableKey.trim()
                        if (backendChanged) {
                            sessionVault.clear()
                            signedInEmail = null
                        }
                        settings.supabaseConfig = newConfig
                        supabaseConfig = settings.supabaseConfig
                        Phase2Scheduler.configurePeriodicSync(context, supabaseConfig.autoSync)
                        Toast.makeText(context, if (supabaseConfig.configured) "Configuración Supabase guardada" else "Configuración guardada; revisa URL/clave", Toast.LENGTH_SHORT).show()
                    },
                    onBack = { screen = AppScreen.HOME }
                )
            }
        }
    }

    recoverySurvey?.let { session ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Levantamiento incompleto encontrado") },
            text = { Text("Encontramos un levantamiento activo de ${session.place}. Los originales no serán eliminados.") },
            confirmButton = {
                TextButton(onClick = { recoverySurvey = null; activate(session) }) { Text("CONTINUAR") }
            },
            dismissButton = {
                TextButton(onClick = {
                    activeSurvey = repository.loadSession(session.id)
                    activePhotos = repository.loadPhotos(session.id)
                    routePoints = repository.loadRoute(session.id)
                    audit = repository.audit(session.id)
                    recoverySurvey = null
                    screen = AppScreen.AUDIT
                }) { Text("FINALIZAR") }
                TextButton(onClick = { discardCandidate = session }) { Text("DESCARTAR") }
            }
        )
    }

    discardCandidate?.let { session ->
        AlertDialog(
            onDismissRequest = { discardCandidate = null },
            title = { Text("¿Descartar levantamiento?") },
            text = { Text("La sesión se marcará como descartada, pero los originales locales se conservarán. No se borrarán automáticamente.") },
            confirmButton = {
                TextButton(onClick = {
                    repository.discardSurveyKeepFiles(session.id)
                    discardCandidate = null
                    recoverySurvey = null
                    refreshSurveys()
                }) { Text("DESCARTAR SIN BORRAR ORIGINALES") }
            },
            dismissButton = { TextButton(onClick = { discardCandidate = null }) { Text("CANCELAR") } }
        )
    }
}

@Composable
private fun <T> rememberSaveableCompat(calculation: () -> MutableState<T>): MutableState<T> = remember { calculation() }
