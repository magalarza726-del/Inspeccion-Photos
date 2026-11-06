package ec.espol.inspeccionphotos.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import ec.espol.inspeccionphotos.survey.*
import ec.espol.inspeccionphotos.sync.SupabaseConfig
import ec.espol.inspeccionphotos.storage.SurveyRepository
import ec.espol.inspeccionphotos.storage.Phase4Repository

private val previewSurvey = SurveySession(
    id = "preview",
    place = "FIMCP",
    createdAtEpochMs = 1_777_000_000_000,
    startedAtEpochMs = 1_777_000_000_000,
    photoCount = 37,
    lastSequence = 37
)

@Preview(name = "Inicio - horizontal", widthDp = 1280, heightDp = 720, showBackground = true)
@Composable
private fun HomePreview() = PreviewFrame {
    HomeScreen(
        activeSurvey = previewSurvey,
        onResumeActive = {}, onNewSurvey = {}, onSurveys = {}, onSync = {}, onSettings = {}
    )
}

@Preview(name = "Nuevo levantamiento - horizontal", widthDp = 1280, heightDp = 720, showBackground = true)
@Composable
private fun NewSurveyPreview() = PreviewFrame { NewSurveyScreen({}, {}) }

@Preview(name = "Captura activa - horizontal", widthDp = 1280, heightDp = 720, showBackground = true)
@Composable
private fun ActiveSurveyPreview() = PreviewFrame {
    ActiveSurveyScreen(
        survey = previewSurvey,
        location = LocationSnapshot(System.currentTimeMillis(), -2.147, -79.967, 8.2, 2.1f, 3.8f, "gps", 1.2f),
        orientation = OrientationSnapshot(System.currentTimeMillis(), 247f, 2f, -1f, 3),
        photoCount = 37,
        routeDistanceMeters = 860.0,
        elapsedMillis = 18 * 60_000L,
        onCapture = {}, onAddPoi = {}, onToggleEnvironment = {}, onMap = {}, onFinishRequest = {}, onHome = {}
    )
}

@Preview(name = "Auditoría final - horizontal", widthDp = 1280, heightDp = 720, showBackground = true)
@Composable
private fun AuditPreview() = PreviewFrame {
    AuditScreen(
        survey = previewSurvey,
        audit = AuditSummary(
            photos = 284,
            originalsPresent = 284,
            routePoints = 1680,
            distanceMeters = 1370.0,
            durationMillis = 34 * 60_000L,
            gpsExcellent = 174,
            gpsGood = 83,
            gpsAcceptable = 12,
            gpsLow = 12,
            gpsVeryLow = 3,
            gpsUnavailable = 0,
            orientationAvailable = 284,
            warnings = listOf("15 fotografías tienen GPS de baja precisión.", "Fachada oeste de 24E incompleta."),
            spatialCoveragePercent = 87,
            gisBuildings = 6,
            identifiedPhotos = 251
        ),
        onContinue = {}, onFinalize = {}
    )
}

@Preview(name = "Sincronización Fase 2 - horizontal", widthDp = 1280, heightDp = 720, showBackground = true)
@Composable
private fun SyncPreview() = PreviewFrame {
    SyncScreen(
        summary = SyncSummary(totalPhotos = 400, queued = 8, uploaded = 6, verified = 386, errors = 0, queueItems = 14),
        config = SupabaseConfig(projectUrl = "https://example.supabase.co", publishableKey = "sb_publishable_demo"),
        signedInEmail = "levantamiento@espol.edu.ec",
        statusMessage = "386/400 verificadas. 14 unidades pendientes.",
        onSignIn = { _, _ -> }, onSignUp = { _, _ -> }, onSignOut = {}, onSyncNow = {}, onBack = {}
    )
}

@Preview(name = "Configuración Supabase - horizontal", widthDp = 1280, heightDp = 720, showBackground = true)
@Composable
private fun SettingsPreview() = PreviewFrame {
    SettingsScreen(
        profile = CaptureProfile.HIGH_ACCURACY,
        storagePath = "/Android/data/ec.espol.inspeccionphotos/files/Pictures/InspeccionPhotos/surveys",
        supabaseConfig = SupabaseConfig(projectUrl = "https://example.supabase.co", publishableKey = "sb_publishable_demo"),
        onProfileChange = {}, onSupabaseSave = {}, onBack = {}
    )
}


@Preview(name = "Mapa espacial Fase 3 - horizontal", widthDp = 1280, heightDp = 720, showBackground = true)
@Composable
private fun SpatialMapPreview() = PreviewFrame {
    val route = listOf(
        RoutePoint(1, LocationSnapshot(1, -2.1474, -79.9674, 8.0, 2.0f, null, "gps", 1f), OrientationSnapshot(1, 90f, 0f, 0f, 3)),
        RoutePoint(2, LocationSnapshot(2, -2.1472, -79.9670, 8.0, 2.5f, null, "gps", 1f), OrientationSnapshot(2, 110f, 0f, 0f, 3)),
        RoutePoint(3, LocationSnapshot(3, -2.1469, -79.9666, 8.0, 3.0f, null, "gps", 1f), OrientationSnapshot(3, 150f, 0f, 0f, 3))
    )
    val photo = PhotoRecord(
        id = "p1", surveyId = "preview", sequence = 37, fileName = "FIMCP_0037.jpg", relativeOriginalPath = "originals/FIMCP_0037.jpg",
        capturedAtEpochMs = 2, place = "FIMCP", location = route[1].location, orientation = route[1].orientation, camera = null,
        gpsQuality = GpsQuality.EXCELLENT, poseConfidence = "ALTA"
    )
    val building = BuildingFootprint(
        "24E", "24E", "FIMCP", listOf(
            GeoPoint(-2.14715, -79.9668), GeoPoint(-2.14715, -79.96645), GeoPoint(-2.14685, -79.96645), GeoPoint(-2.14685, -79.9668)
        )
    )
    SurveyMapScreen(
        survey = previewSurvey,
        route = route,
        photos = listOf(photo),
        pois = emptyList(),
        spatial = SpatialAnalysisBundle(
            buildings = listOf(building),
            inferences = listOf(BuildingInference("p1", 37, "24E", "24E", .91, 17.2, 110.0, 3.0, "GIS + dirección de cámara + distancia + precisión GPS")),
            coverage = listOf(BuildingCoverage("24E", "24E", 76, listOf(
                FacadeCoverage(FacadeSide.NORTH, 100, 8, 5, 0, 70.0),
                FacadeCoverage(FacadeSide.EAST, 82, 6, 3, 1, 58.0),
                FacadeCoverage(FacadeSide.SOUTH, 31, 2, 0, 1, 15.0),
                FacadeCoverage(FacadeSide.WEST, 93, 7, 4, 0, 66.0)
            ), listOf("Falta documentar fachada sur de 24E."))),
            summary = SpatialSummary(surveyId = "preview", analyzedAtEpochMs = 3, gisBuildingsAvailable = 1, identifiedPhotos = 1, unknownPhotos = 0, buildingsWithCoverage = 1, averageCoveragePercent = 76)
        ),
        onBack = {}, onImportGis = {}, onAnalyze = {}
    )
}


@Preview(name = "Interior ARCore Fase 4 - horizontal", widthDp = 1280, heightDp = 720, showBackground = true)
@Composable
private fun InteriorPhase4Preview() = PreviewFrame {
    InteriorTrackingScreen(
        survey = previewSurvey.copy(environmentMode = EnvironmentMode.INTERIOR),
        pose = InteriorPoseSample(
            surveyId = "preview", anchorId = "a1", timestampEpochMs = 1, eastMeters = 14.2, northMeters = 27.8, upMeters = 1.1,
            headingDegrees = 132f, stepCount = 61, uncertaintyMeters = 3.8, confidence = InteriorConfidence.HIGH,
            source = InteriorTrackingSource.ARCORE_VIO, arTracking = true
        ),
        onArPose = {}, onBack = {}, arCoreEnabled = false
    )
}

@Preview(name = "Ráfaga espacial Fase 4 - horizontal", widthDp = 1280, heightDp = 720, showBackground = true)
@Composable
private fun BurstPhase4Preview() = PreviewFrame {
    val context = LocalContext.current
    val repo = remember { SurveyRepository(context) }
    SpatialBurstScreen(
        survey = previewSurvey,
        location = LocationSnapshot(1, -2.147, -79.967, 8.0, 2.3f, null, "gps", 1f),
        orientation = OrientationSnapshot(1, 214f, 0f, 0f, 3),
        repository = repo, phase4 = remember { Phase4Repository(repo) }, onPhotoCommitted = {}, onBack = {}, cameraEnabled = false
    )
}

@Preview(name = "Escaneo caminando Fase 4 - horizontal", widthDp = 1280, heightDp = 720, showBackground = true)
@Composable
private fun VideoPhase4Preview() = PreviewFrame {
    val context = LocalContext.current
    val repo = remember { SurveyRepository(context) }
    VideoScanScreen(
        survey = previewSurvey.copy(environmentMode = EnvironmentMode.INTERIOR),
        location = null, orientation = OrientationSnapshot(1, 88f, 0f, 0f, 3),
        inertial = InertialSnapshot(1, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1012f, 84),
        interiorPose = InteriorPoseSample("preview", "a1", 1, 4.0, 9.0, .2, 88f, 84, 4.6, InteriorConfidence.MEDIUM, InteriorTrackingSource.STEP_IMU),
        repository = repo, phase4 = remember { Phase4Repository(repo) }, onBack = {}, cameraEnabled = false
    )
}

@Composable
private fun PreviewFrame(content: @Composable () -> Unit) {
    InspeccionPhotosTheme {
        Surface(color = SpatialBg) {
            Column(Modifier) {
                AppTopBar()
                content()
            }
        }
    }
}
