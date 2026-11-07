package ec.espol.inspeccionphotos.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import ec.espol.inspeccionphotos.*
import ec.espol.inspeccionphotos.location.LocationTracker
import ec.espol.inspeccionphotos.sensors.OrientationTracker
import ec.espol.inspeccionphotos.sensors.InertialTracker
import ec.espol.inspeccionphotos.survey.CaptureProfile
import ec.espol.inspeccionphotos.survey.LocationSnapshot
import ec.espol.inspeccionphotos.survey.InertialSnapshot
import ec.espol.inspeccionphotos.survey.OrientationSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private data class LegacyInputState(
    val title: String,
    val label: String,
    val initial: String = "",
    val multiline: Boolean = false,
    val onConfirm: (String) -> Unit
)

private data class LegacyConfirmState(
    val title: String,
    val message: String,
    val confirmText: String = "Eliminar",
    val onConfirm: () -> Unit
)

private data class PreparedFolderExport(
    val zip: File?,
    val standardReports: List<File>,
    val spatialReports: List<File>,
    val customReports: List<File>
)

@Composable
fun InspeccionPhotosApp() {
    val context = LocalContext.current
    val repository = remember { InspectionRepository(context) }
    val mediaStore = remember { InspectionMediaStore(context) }
    val prefs = remember { LegacyUiPreferences(context) }
    val reportStore = remember { CustomReportConfigStore(context) }
    val photoPresetStore = remember { PhotoReportPresetStore(context, reportStore) }
    val formStore = remember { FormTemplateStore(context) }
    val trashStore = remember { TrashStore(context) }
    var trashEntries by remember { mutableStateOf(trashStore.load()) }
    val archiveImporter = remember { InspectionArchiveImporter(context) }
    val configurationBackup = remember { AppConfigurationBackup(context) }
    val locationTracker = remember { LocationTracker(context) }
    val orientationTracker = remember { OrientationTracker(context) }
    val inertialTracker = remember { InertialTracker(context) }
    val scope = rememberCoroutineScope()

    var splashVisible by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(700)
        splashVisible = false
    }

    var darkMode by remember { mutableStateOf(prefs.darkMode) }
    var developerModeEnabled by remember { mutableStateOf(prefs.developerModeEnabled) }
    var spatialFunctionsEnabled by remember { mutableStateOf(prefs.spatialFunctionsEnabled) }
    var showOt by remember { mutableStateOf(prefs.showOt) }
    var companyName by remember { mutableStateOf(prefs.companyName) }
    var pdfLogoPath by remember { mutableStateOf(prefs.pdfLogoPath) }
    var rootViewMode by remember { mutableStateOf(prefs.rootViewMode) }
    var addPhotoAction by remember { mutableStateOf(prefs.addPhotoAction) }
    var editorPromptMode by remember { mutableStateOf(prefs.editorPromptMode) }
    var cameraSessionMode by remember { mutableStateOf(prefs.cameraSessionMode) }
    var askPhotoConfirmation by remember { mutableStateOf(prefs.askPhotoConfirmation) }
    var hiddenLegacyPdfs by remember { mutableStateOf(prefs.hiddenLegacyPdfs) }
    var googleDriveConnected by remember { mutableStateOf(prefs.googleDriveConnected) }
    var googleDriveConnectedAt by remember { mutableStateOf(prefs.googleDriveConnectedAt) }
    var photoPresets by remember {
        mutableStateOf(photoPresetStore.load().also { photoPresetStore.save(it) })
    }
    var activePhotoPresetId by remember {
        mutableStateOf(
            prefs.activePhotoPresetId.takeIf { id -> photoPresets.any { it.id == id } }
                ?: photoPresets.firstOrNull()?.id.orEmpty()
        )
    }
    var defaultPhotoReport by remember { mutableStateOf(prefs.defaultPhotoReport) }
    var defaultFormTemplateId by remember { mutableStateOf(prefs.defaultFormTemplateId) }
    var reportConfig by remember {
        mutableStateOf(photoPresets.firstOrNull { it.id == activePhotoPresetId }?.config ?: reportStore.load())
    }
    var formTemplates by remember { mutableStateOf(formStore.loadTemplates()) }
    val palette = remember(darkMode) { legacyPalette(darkMode) }
    var latestLocation by remember { mutableStateOf<LocationSnapshot?>(null) }
    var latestOrientation by remember { mutableStateOf<OrientationSnapshot?>(null) }
    var latestInertial by remember { mutableStateOf<InertialSnapshot?>(null) }
    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    DisposableEffect(spatialFunctionsEnabled, locationPermissionGranted) {
        if (spatialFunctionsEnabled) {
            if (locationPermissionGranted) {
                locationTracker.start(CaptureProfile.BALANCED) { latestLocation = it }
            } else {
                locationTracker.stop()
                latestLocation = null
            }
            orientationTracker.start { latestOrientation = it }
            inertialTracker.start(onSnapshot = { latestInertial = it })
        } else {
            locationTracker.stop()
            orientationTracker.stop()
            inertialTracker.stop()
            latestLocation = null
            latestOrientation = null
            latestInertial = null
        }
        onDispose {
            locationTracker.stop()
            orientationTracker.stop()
            inertialTracker.stop()
        }
    }

    fun spatialObservation(): String = if (spatialFunctionsEnabled) {
        SpatialObservationFormatter.build(latestLocation, latestOrientation, latestInertial)
    } else ""

    var rootSectionName by rememberSaveable { mutableStateOf(LegacyRootSection.HOME.name) }
    val rootSection = runCatching { LegacyRootSection.valueOf(rootSectionName) }.getOrDefault(LegacyRootSection.HOME)
    var folders by remember { mutableStateOf(repository.load()) }
    var selectedFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var requestedFormId by remember { mutableStateOf<String?>(null) }

    var inputDialog by remember { mutableStateOf<LegacyInputState?>(null) }
    var confirmDialog by remember { mutableStateOf<LegacyConfirmState?>(null) }
    var configureFolderId by remember { mutableStateOf<String?>(null) }
    var folderMoreMenuId by remember { mutableStateOf<String?>(null) }
    var folderToolsId by remember { mutableStateOf<String?>(null) }
    var folderSortPickerId by remember { mutableStateOf<String?>(null) }
    var folderPreviewId by remember { mutableStateOf<String?>(null) }
    var sectionHeadingFolderId by remember { mutableStateOf<String?>(null) }
    var copyFolderId by remember { mutableStateOf<String?>(null) }
    var cloneFolderId by remember { mutableStateOf<String?>(null) }
    var cameraSettingsVisible by remember { mutableStateOf(false) }
    var advancedSettingsVisible by remember { mutableStateOf(false) }
    var sortRevision by remember { mutableIntStateOf(0) }
    var photoTransferFolderId by remember { mutableStateOf<String?>(null) }
    var photoTransferPath by remember { mutableStateOf<String?>(null) }
    var photoTransferCopy by remember { mutableStateOf(false) }
    var singleMoveFolderId by remember { mutableStateOf<String?>(null) }
    var exportDialogFolderId by remember { mutableStateOf<String?>(null) }
    var tableEditorFolderId by remember { mutableStateOf<String?>(null) }
    var sectionEditorFolderId by remember { mutableStateOf<String?>(null) }
    var mainImageFolderId by remember { mutableStateOf<String?>(null) }
    var pendingMainImageFolderId by remember { mutableStateOf<String?>(null) }
    var pendingMainImageCameraFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingMainImageCapturePath by rememberSaveable { mutableStateOf<String?>(null) }

    if (splashVisible) {
        InspeccionPhotosSplash(palette)
        return
    }

    var addPhotoChoiceFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPhotoFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCapturePath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var batchCameraFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var batchCameraSingleShot by rememberSaveable { mutableStateOf(false) }
    var promptEditorFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var promptEditorPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var editorFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }

    val spatialPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        locationPermissionGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (spatialFunctionsEnabled && !locationPermissionGranted) {
            Toast.makeText(context, "Funciones espaciales activadas sin ubicación; se guardará la orientación disponible.", Toast.LENGTH_LONG).show()
        }
    }

    fun persist(updated: List<Inspection>) {
        folders = updated
        repository.save(updated)
        if (selectedFolderId != null && InspectionTree.find(updated, selectedFolderId) == null) selectedFolderId = null
    }

    val zipImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                Toast.makeText(context, "Importando proyecto ZIP...", Toast.LENGTH_SHORT).show()
                val result = withContext(Dispatchers.IO) { runCatching { archiveImporter.importArchive(uri) } }
                result.onSuccess { imported ->
                    persist(folders + imported)
                    selectedFolderId = imported.id
                    rootSectionName = LegacyRootSection.FOLDERS.name
                    Toast.makeText(context, "Proyecto importado: ${imported.name}", Toast.LENGTH_LONG).show()
                }.onFailure { error ->
                    Toast.makeText(context, error.message ?: "No se pudo importar el ZIP", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val configurationExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { configurationBackup.exportToUri(uri) } }
                result.onSuccess {
                    Toast.makeText(context, "Configuración total exportada", Toast.LENGTH_LONG).show()
                }.onFailure { error ->
                    Toast.makeText(context, error.message ?: "No se pudo exportar la configuración", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val configurationImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { configurationBackup.importFromUri(uri) } }
                result.onSuccess { imported ->
                    darkMode = prefs.darkMode
                    spatialFunctionsEnabled = prefs.spatialFunctionsEnabled
                    showOt = prefs.showOt
                    companyName = prefs.companyName
                    pdfLogoPath = prefs.pdfLogoPath
                    rootViewMode = prefs.rootViewMode
                    addPhotoAction = prefs.addPhotoAction
                    editorPromptMode = prefs.editorPromptMode
                    cameraSessionMode = prefs.cameraSessionMode
                    askPhotoConfirmation = prefs.askPhotoConfirmation
                    hiddenLegacyPdfs = prefs.hiddenLegacyPdfs
                    googleDriveConnected = false
                    googleDriveConnectedAt = 0L
                    reportConfig = reportStore.load()
                    formTemplates = formStore.loadTemplates()
                    Toast.makeText(
                        context,
                        "Configuración importada: ${imported.formTemplateCount} formato(s) de formulario",
                        Toast.LENGTH_LONG
                    ).show()
                    if (spatialFunctionsEnabled && !locationPermissionGranted) {
                        spatialPermissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    }
                }.onFailure { error ->
                    Toast.makeText(context, error.message ?: "No se pudo importar la configuración", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun updateFolder(id: String, transform: (Inspection) -> Inspection) {
        persist(InspectionTree.update(folders, id) { transform(it).copy(modifiedAtMillis = System.currentTimeMillis()) })
    }

    fun selectedFolder(): Inspection? = InspectionTree.find(folders, selectedFolderId)

    fun updatePhoto(folderId: String, photoPath: String, transform: (PhotoItem) -> PhotoItem) {
        updateFolder(folderId) { folder ->
            folder.copy(photos = folder.photos.map {
                if (it.path == photoPath) transform(it).copy(modifiedAtMillis = System.currentTimeMillis()) else it
            })
        }
    }

    fun addCommittedPhoto(folderId: String, file: File, promptEdit: Boolean, includeSpatial: Boolean = false) {
        val item = PhotoItem(
            name = file.name,
            path = file.absolutePath,
            observation = if (includeSpatial) spatialObservation() else ""
        )
        updateFolder(folderId) { folder -> folder.copy(photos = folder.photos + item) }
        Toast.makeText(context, "Foto guardada: ${file.name}", Toast.LENGTH_SHORT).show()
        if (promptEdit && editorPromptMode == EditorPromptMode.ASK_AFTER_CAPTURE) {
            promptEditorFolderId = folderId
            promptEditorPhotoPath = file.absolutePath
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val folderId = pendingPhotoFolderId
        val file = pendingCapturePath?.let(::File)
        pendingPhotoFolderId = null; pendingCapturePath = null
        if (ok && folderId != null && file != null && file.exists() && file.length() > 0L) {
            addCommittedPhoto(folderId, file, promptEdit = true, includeSpatial = true)
        } else {
            runCatching { file?.delete() }
            if (!ok) Toast.makeText(context, "Captura cancelada", Toast.LENGTH_SHORT).show()
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val folderId = pendingPhotoFolderId
        pendingPhotoFolderId = null
        if (uri == null || folderId == null) return@rememberLauncherForActivityResult
        val folder = InspectionTree.find(folders, folderId) ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val name = mediaStore.nextPhotoName(folder.name, folder.photos)
                    mediaStore.copyPickedImage(folder.id, name, uri)
                }
            }
            result.onSuccess { addCommittedPhoto(folderId, it, promptEdit = false, includeSpatial = false) }
                .onFailure { Toast.makeText(context, it.message ?: "No se pudo importar la imagen", Toast.LENGTH_LONG).show() }
        }
    }

    val pdfLogoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { prefs.importPdfLogo(uri) } }
            result.onSuccess { path -> pdfLogoPath = path; Toast.makeText(context, "Logo del PDF actualizado", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, it.message ?: "No se pudo guardar el logo", Toast.LENGTH_LONG).show() }
        }
    }

    val reportMainImagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val folderId = pendingMainImageFolderId
        pendingMainImageFolderId = null
        if (uri != null && folderId != null) scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { mediaStore.copyReportMainImage(folderId, uri) } }
            result.onSuccess { file -> updateFolder(folderId) { it.copy(reportMainImagePath = file.absolutePath) }; Toast.makeText(context, "Imagen principal actualizada", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, it.message ?: "No se pudo guardar la imagen principal", Toast.LENGTH_LONG).show() }
        }
    }

    val mainImageCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val folderId = pendingMainImageCameraFolderId
        val file = pendingMainImageCapturePath?.let(::File)
        pendingMainImageCameraFolderId = null
        pendingMainImageCapturePath = null
        if (ok && folderId != null && file != null && file.exists() && file.length() > 0L) {
            updateFolder(folderId) { it.copy(reportMainImagePath = file.absolutePath) }
            Toast.makeText(context, "Imagen principal capturada", Toast.LENGTH_SHORT).show()
        } else {
            runCatching { file?.delete() }
            if (!ok) Toast.makeText(context, "Captura cancelada", Toast.LENGTH_SHORT).show()
        }
    }

    fun openMainImageCamera(folderId: String) {
        runCatching {
            val file = mediaStore.createReportMainCaptureFile(folderId)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingMainImageCameraFolderId = folderId
            pendingMainImageCapturePath = file.absolutePath
            mainImageCameraLauncher.launch(uri)
        }.onFailure { error ->
            pendingMainImageCameraFolderId = null
            pendingMainImageCapturePath?.let { runCatching { File(it).delete() } }
            pendingMainImageCapturePath = null
            Toast.makeText(context, "No se pudo abrir la cámara: ${error.message ?: "error desconocido"}", Toast.LENGTH_LONG).show()
        }
    }

    val mainImageCameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val folderId = pendingMainImageCameraFolderId
        if (granted && folderId != null) {
            pendingMainImageCameraFolderId = null
            openMainImageCamera(folderId)
        } else if (folderId != null) {
            pendingMainImageCameraFolderId = null
            Toast.makeText(context, "Permiso de cámara denegado", Toast.LENGTH_LONG).show()
        }
    }

    fun requestMainImageCamera(folderId: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openMainImageCamera(folderId)
        } else {
            pendingMainImageCameraFolderId = folderId
            mainImageCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun openSingleCamera(folderId: String) {
        val folder = InspectionTree.find(folders, folderId) ?: return
        runCatching {
            val name = mediaStore.nextPhotoName(folder.name, folder.photos)
            val file = mediaStore.createCaptureFile(folder.id, name)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingPhotoFolderId = folderId
            pendingCapturePath = file.absolutePath
            cameraLauncher.launch(uri)
        }.onFailure { error ->
            pendingPhotoFolderId = null
            pendingCapturePath?.let { runCatching { File(it).delete() } }
            pendingCapturePath = null
            Toast.makeText(context, "No se pudo abrir la cámara: ${error.message ?: "error desconocido"}", Toast.LENGTH_LONG).show()
        }
    }

    fun openConfiguredCamera(folderId: String) {
        // Ambas modalidades usan ahora la cámara integrada para conservar la interfaz
        // de Inspeccion Photos. En modo una-por-una se cierra automáticamente tras capturar.
        batchCameraSingleShot = cameraSessionMode == CameraSessionMode.ONE_BY_ONE
        batchCameraFolderId = folderId
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val folderId = pendingCameraFolderId
        pendingCameraFolderId = null
        if (granted && folderId != null) openConfiguredCamera(folderId)
        else if (folderId != null) Toast.makeText(context, "Permiso de cámara denegado", Toast.LENGTH_LONG).show()
    }

    fun requestTakePhoto(folderId: String) {
        val folder = InspectionTree.find(folders, folderId) ?: return
        if (!folder.allowPhotos) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openConfiguredCamera(folderId)
        } else {
            pendingCameraFolderId = folderId
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun requestPickPhoto(folderId: String) {
        val folder = InspectionTree.find(folders, folderId) ?: return
        if (!folder.allowPhotos) return
        pendingPhotoFolderId = folderId
        runCatching { pickerLauncher.launch("image/*") }.onFailure {
            pendingPhotoFolderId = null
            Toast.makeText(context, "No se pudo abrir el selector de imágenes", Toast.LENGTH_LONG).show()
        }
    }

    fun requestAddPhoto(folderId: String) {
        when (addPhotoAction) {
            AddPhotoAction.CAMERA_DIRECT -> requestTakePhoto(folderId)
            AddPhotoAction.ATTACH -> requestPickPhoto(folderId)
            AddPhotoAction.ASK -> addPhotoChoiceFolderId = folderId
        }
    }

    fun createRootFolderDialog() {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        inputDialog = LegacyInputState("Nueva carpeta", "Nombre de la carpeta", "${date}_") { raw ->
            val base = raw.trim().ifBlank { date }
            var name = base; var suffix = 2
            while (folders.any { it.name.equals(name, true) }) { name = "${base}_$suffix"; suffix++ }
            persist(folders + Inspection(UUID.randomUUID().toString(), name, date))
        }
    }

    fun createChildFolderDialog(parentId: String) {
        val parent = InspectionTree.find(folders, parentId) ?: return
        val depth = InspectionTree.depth(folders, parentId) ?: return
        if (!parent.allowSubfolders || depth >= InspectionTree.MAX_SUBFOLDER_LEVELS) {
            Toast.makeText(context, "Este nivel no permite más subcarpetas", Toast.LENGTH_SHORT).show(); return
        }
        val used = parent.children.mapNotNull { it.name.toIntOrNull() }.toSet()
        var n = 1; while (n in used) n++
        val suggested = n.toString().padStart(3, '0')
        inputDialog = LegacyInputState("Nueva subcarpeta", "Nombre", suggested) { raw ->
            val name = raw.trim().ifBlank { suggested }
            if (parent.children.any { it.name.equals(name, true) }) Toast.makeText(context, "Ese nombre ya existe en este nivel", Toast.LENGTH_SHORT).show()
            else updateFolder(parentId) { it.copy(children = it.children + Inspection(UUID.randomUUID().toString(), name, parent.created)) }
        }
    }

    fun editOt(folder: Inspection) {
        inputDialog = LegacyInputState("Agregar/editar OT", "Orden de trabajo (OT)", folder.ot) { value ->
            updateFolder(folder.id) { it.copy(ot = value.trim()) }; Toast.makeText(context, "OT guardada", Toast.LENGTH_SHORT).show()
        }
    }

    fun renameFolder(folder: Inspection) {
        inputDialog = LegacyInputState("Renombrar carpeta", "Nuevo nombre", folder.name) { value ->
            value.trim().takeIf { it.isNotBlank() }?.let { newName -> updateFolder(folder.id) { it.copy(name = newName) } }
        }
    }

    fun editGeneralObservation(folder: Inspection) {
        inputDialog = LegacyInputState("Observación general", "Observación de la carpeta", folder.generalObs, multiline = true) { value ->
            updateFolder(folder.id) { it.copy(generalObs = value) }
        }
    }

    fun uniqueSiblingName(source: Inspection, suffix: String): String {
        val parentId = InspectionTree.parentId(folders, source.id)
        val siblings = parentId?.let { InspectionTree.find(folders, it)?.children }.orEmpty().ifEmpty { if (parentId == null) folders else emptyList() }
        val base = "${source.name}$suffix"
        var candidate = base
        var n = 2
        while (siblings.any { it.name.equals(candidate, ignoreCase = true) }) {
            candidate = "${base}_$n"
            n++
        }
        return candidate
    }

    suspend fun duplicateFolderFiles(source: Inspection, rootName: String? = null): Inspection {
        val newId = UUID.randomUUID().toString()
        val copiedPhotos = source.photos.map { photo ->
            val src = File(photo.path)
            if (src.exists()) {
                val dest = File(mediaStore.photosDir(newId), src.name)
                src.copyTo(dest, overwrite = true)
                photo.copy(path = dest.absolutePath, originalPath = "")
            } else photo.copy(path = "", originalPath = "")
        }
        val copiedChildren = source.children.map { duplicateFolderFiles(it) }
        val copiedMainImage = source.reportMainImagePath.takeIf { it.isNotBlank() }?.let { path ->
            val src = File(path)
            if (src.exists()) {
                val dir = File(mediaStore.inspectionDir(newId), "report").apply { mkdirs() }
                val dest = File(dir, "main_image.${src.extension.ifBlank { "jpg" }}")
                src.copyTo(dest, overwrite = true)
                dest.absolutePath
            } else ""
        }.orEmpty()
        return source.copy(
            id = newId,
            name = rootName ?: source.name,
            photos = copiedPhotos,
            children = copiedChildren,
            collapsed = false,
            reportMainImagePath = copiedMainImage
        )
    }

    fun duplicateFolder(source: Inspection) {
        scope.launch {
            val parentId = InspectionTree.parentId(folders, source.id)
            val parentDepth = parentId?.let { InspectionTree.depth(folders, it) } ?: -1
            if (parentDepth + 1 + InspectionTree.subtreeDepth(source) > InspectionTree.MAX_SUBFOLDER_LEVELS) {
                Toast.makeText(context, "La copia superaría los 7 subniveles", Toast.LENGTH_LONG).show(); return@launch
            }
            val rootName = uniqueSiblingName(source, " - Copia")
            val copy = withContext(Dispatchers.IO) { duplicateFolderFiles(source, rootName) }
            persist(InspectionTree.insert(folders, parentId, copy))
            Toast.makeText(context, "Carpeta duplicada", Toast.LENGTH_SHORT).show()
        }
    }

    fun cloneStructureNode(source: Inspection, rootName: String? = null): Inspection = Inspection(
        id = UUID.randomUUID().toString(),
        name = rootName ?: source.name,
        created = source.created,
        ot = "",
        codes = emptyList(),
        generalObs = "",
        photos = emptyList(),
        children = source.children.map { cloneStructureNode(it) },
        allowPhotos = source.allowPhotos,
        allowSubfolders = source.allowSubfolders,
        viewMode = source.viewMode,
        collapsed = false,
        reportMainImagePath = "",
        reportTableEnabled = false,
        reportTableValues = emptyList(),
        reportSectionValues = emptyMap()
    )

    fun cloneStructure(source: Inspection) {
        val parentId = InspectionTree.parentId(folders, source.id)
        val parentDepth = parentId?.let { InspectionTree.depth(folders, it) } ?: -1
        if (parentDepth + 1 + InspectionTree.subtreeDepth(source) > InspectionTree.MAX_SUBFOLDER_LEVELS) {
            Toast.makeText(context, "La estructura clonada superaría los 7 subniveles", Toast.LENGTH_LONG).show()
            return
        }
        val rootName = uniqueSiblingName(source, " - Estructura")
        val copy = cloneStructureNode(source, rootName)
        persist(InspectionTree.insert(folders, parentId, copy))
        Toast.makeText(context, "Estructura clonada sin fotos", Toast.LENGTH_SHORT).show()
    }

    fun sendToTrash(type: TrashType, name: String, payload: String, parentId: String? = null) {
        trashStore.put(type, name, payload, parentId)
        trashEntries = trashStore.load()
    }

    fun cloneStructureTo(source: Inspection, destinationId: String?) {
        val target = destinationId?.let { InspectionTree.find(folders, it) }
        val destinationDepth = destinationId?.let { InspectionTree.depth(folders, it) } ?: -1
        if (destinationDepth + 1 + InspectionTree.subtreeDepth(source) > InspectionTree.MAX_SUBFOLDER_LEVELS ||
            (destinationId != null && target?.allowSubfolders != true)) {
            Toast.makeText(context, "La estructura no cabe en este destino", Toast.LENGTH_LONG).show()
            return
        }
        val siblings = target?.children ?: folders
        val base = "${source.name} - Estructura"
        var title = base
        var number = 2
        while (siblings.any { it.name.equals(title, true) }) {
            title = "$base ($number)"
            number++
        }
        persist(InspectionTree.insert(folders, destinationId, cloneStructureNode(source, title)))
        Toast.makeText(context, "Estructura clonada", Toast.LENGTH_SHORT).show()
    }

    fun deleteFilesRecursive(folder: Inspection) {
        folder.photos.forEach(mediaStore::deletePhoto)
        folder.children.forEach(::deleteFilesRecursive)
        mediaStore.deleteInspectionFiles(folder.id)
    }

    fun requestDeleteFolder(folder: Inspection) {
        confirmDialog = LegacyConfirmState(
            "Mover a Papelera", "¿Mover ${folder.name} y su contenido a la Papelera?", "Mover"
        ) {
            val parent = InspectionTree.parentId(folders, folder.id)
            sendToTrash(TrashType.FOLDER, folder.name, repository.encodeTrashFolder(folder), parent)
            val (updated, _) = InspectionTree.remove(folders, folder.id)
            persist(updated)
            if (selectedFolderId == folder.id) selectedFolderId = parent
        }
    }

    fun exportFolder(folder: Inspection) {
        exportDialogFolderId = folder.id
    }

    fun shareQuick(folder: Inspection) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val option = prefs.defaultPhotoReport
                    if (option == "Personalizado" || option.startsWith("Preset:")) {
                        val presetConfig = photoPresets.firstOrNull {
                            it.id == option.substringAfter("Preset:")
                        }?.config ?: reportConfig
                        CustomReportExporter.createPdf(context, folder, presetConfig)
                    } else {
                        val columns = option.substringAfter("PDF ").toIntOrNull()?.coerceIn(1, 4) ?: 3
                        ExportUtils.createObservationPdf(context, folder, columns)
                    }
                }
            }
            result.onSuccess { ExportUtils.shareFile(context, it, "application/pdf") }
                .onFailure { Toast.makeText(context, "No se pudo compartir: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    fun copyFolderTo(source: Inspection, destinationId: String?) {
        val target = destinationId?.let { InspectionTree.find(folders, it) }
        val destDepth = destinationId?.let { InspectionTree.depth(folders, it) } ?: -1
        if (destDepth + 1 + InspectionTree.subtreeDepth(source) > InspectionTree.MAX_SUBFOLDER_LEVELS ||
            (destinationId != null && target?.allowSubfolders != true)) {
            Toast.makeText(context, "La carpeta destino no admite este nivel", Toast.LENGTH_LONG).show()
            return
        }
        scope.launch {
            val siblings = target?.children ?: folders
            val base = "${source.name} - Copia"
            var candidate = base
            var n = 2
            while (siblings.any { it.name.equals(candidate, true) }) {
                candidate = "$base ($n)"
                n++
            }
            val copied = withContext(Dispatchers.IO) { duplicateFolderFiles(source, candidate) }
            persist(InspectionTree.insert(folders, destinationId, copied))
            Toast.makeText(context, "Copia creada", Toast.LENGTH_SHORT).show()
        }
    }

    fun transferPhoto(sourceFolderId: String, photoPath: String, destinationId: String, copy: Boolean) {
        val source = InspectionTree.find(folders, sourceFolderId) ?: return
        val target = InspectionTree.find(folders, destinationId) ?: return
        val photo = source.photos.firstOrNull { it.path == photoPath } ?: return
        if (!target.allowPhotos || (!copy && sourceFolderId == destinationId)) return
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val original = File(photo.path)
                    require(original.exists()) { "La fotografía ya no existe" }
                    val desired = mediaStore.nextPhotoName(target.name, target.photos)
                    val dest = mediaStore.createCaptureFile(target.id, desired)
                    original.copyTo(dest, overwrite = true)
                    val backup = photo.originalPath.takeIf { it.isNotBlank() }?.let(::File)
                    val backupCopy = backup?.takeIf { it.exists() }?.let {
                        val backupDir = File(mediaStore.photosDir(target.id), "originals").apply { mkdirs() }
                        File(backupDir, dest.name).also { file -> it.copyTo(file, overwrite = true) }
                    }
                    val moved = photo.copy(name = dest.name, path = dest.absolutePath,
                        originalPath = if (copy) "" else backupCopy?.absolutePath.orEmpty(),
                        modifiedAtMillis = System.currentTimeMillis())
                    moved to dest
                }
            }
            result.onSuccess { (newPhoto, _) ->
                var next = folders
                if (!copy) next = InspectionTree.update(next, sourceFolderId) { it.copy(
                    photos = it.photos.filterNot { p -> p.path == photoPath },
                    mainPhotoPath = if (it.mainPhotoPath == photoPath) "" else it.mainPhotoPath
                ) }
                next = InspectionTree.update(next, destinationId) { it.copy(photos = it.photos + newPhoto) }
                persist(next)
                if (!copy) {
                    runCatching { File(photo.path).delete() }
                    photo.originalPath.takeIf { it.isNotBlank() }?.let { runCatching { File(it).delete() } }
                }
                Toast.makeText(context, if (copy) "Foto copiada" else "Foto movida", Toast.LENGTH_SHORT).show()
            }.onFailure { Toast.makeText(context, "No se pudo mover/copiar: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    fun toggleMainPhoto(folderId: String, photo: PhotoItem) {
        val folder = InspectionTree.find(folders, folderId) ?: return
        if (folder.mainPhotoPath == photo.path) {
            updateFolder(folderId) { it.copy(mainPhotoPath = "", reportMainImagePath = "") }
        } else {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { mediaStore.copyReportMainImageFromFile(folderId, File(photo.path)) }
                }
                result.onSuccess { file ->
                    updateFolder(folderId) { it.copy(mainPhotoPath = photo.path, reportMainImagePath = file.absolutePath) }
                }.onFailure { Toast.makeText(context, it.message ?: "No se pudo seleccionar la imagen", Toast.LENGTH_LONG).show() }
            }
        }
    }

    fun executeExport(folder: Inspection, rawSelection: ExportSelection) {
        val selection = rawSelection.normalized().copy(
            spatialReport = rawSelection.spatialReport && spatialFunctionsEnabled
        )
        prefs.exportSelection = selection
        Toast.makeText(context, "Preparando exportación...", Toast.LENGTH_SHORT).show()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val standardReports = if (selection.standardReport) {
                        listOf(ExportUtils.createObservationPdf(context, folder, selection.standardPdfColumns))
                    } else emptyList()
                    val spatialReports = if (selection.spatialReport) {
                        listOf(ExportUtils.createSpatialReportPdf(context, folder))
                    } else emptyList()
                    val customReports = if (selection.customReport) {
                        CustomReportExporter.createAll(
                            context, folder,
                            photoPresets.firstOrNull {
                                it.id == selection.customReportPresetId
                            }?.config ?: reportConfig
                        )
                    } else emptyList()
                    val zip = if (selection.photosAndStructure) {
                        val backup = repository.backupFile(folders)
                        ExportUtils.createInspectionZipSelected(
                            context = context,
                            inspection = folder,
                            selection = selection,
                            selectedPdfs = standardReports,
                            spatialReports = spatialReports,
                            customReports = customReports,
                            backup = backup
                        )
                    } else null
                    PreparedFolderExport(zip, standardReports, spatialReports, customReports)
                }
            }
            result.onSuccess { prepared ->
                val reports = prepared.standardReports + prepared.spatialReports + prepared.customReports
                val filesToShare = prepared.zip?.let(::listOf) ?: reports
                when (filesToShare.size) {
                    0 -> Toast.makeText(context, "No se seleccionaron archivos para compartir", Toast.LENGTH_SHORT).show()
                    1 -> {
                        val file = filesToShare.first()
                        val mime = when (file.extension.lowercase()) {
                            "zip" -> "application/zip"
                            "pdf" -> "application/pdf"
                            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            else -> "application/octet-stream"
                        }
                        ExportUtils.shareFile(context, file, mime)
                    }
                    else -> {
                        val uris = ArrayList(filesToShare.map {
                            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
                        })
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "application/octet-stream"
                            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Compartir reportes"))
                    }
                }
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        prepared.standardReports.forEach { ExportUtils.copyToDownloads(context, it, "application/pdf") }
                        prepared.spatialReports.forEach { ExportUtils.copyToDownloads(context, it, "application/pdf") }
                        prepared.customReports.forEach { file ->
                            val mime = when (file.extension.lowercase()) {
                                "pdf" -> "application/pdf"
                                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                else -> "application/octet-stream"
                            }
                            ExportUtils.copyToDownloads(context, file, mime)
                        }
                        prepared.zip?.let { ExportUtils.copyToDownloads(context, it, "application/zip") }
                    }
                }
            }.onFailure {
                Toast.makeText(context, "No se pudo exportar: ${it.message ?: "error desconocido"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun renamePhoto(folderId: String, photo: PhotoItem) {
        inputDialog = LegacyInputState("Renombrar foto", "Nuevo nombre", photo.name) { value ->
            val renamed = runCatching { mediaStore.renamePhoto(photo, value) }.getOrNull()
            if (renamed == null) Toast.makeText(context, "No se pudo renombrar la foto", Toast.LENGTH_SHORT).show()
            else updatePhoto(folderId, photo.path) { renamed }
        }
    }

    fun editPhotoObservation(folderId: String, photo: PhotoItem) {
        inputDialog = LegacyInputState("Observación de la foto", "Observación", photo.observation, multiline = true) { value ->
            updatePhoto(folderId, photo.path) { it.copy(observation = value) }
        }
    }

    fun deletePhoto(folderId: String, photo: PhotoItem) {
        confirmDialog = LegacyConfirmState("Mover a Papelera", "¿Mover ${photo.name} a la Papelera?", "Mover") {
            val payload = org.json.JSONObject().apply {
                put("name", photo.name)
                put("path", photo.path)
                put("observation", photo.observation)
                put("originalPath", photo.originalPath)
            }.toString()
            sendToTrash(TrashType.PHOTO, photo.name, payload, folderId)
            updateFolder(folderId) { it.copy(photos = it.photos.filterNot { p -> p.path == photo.path }) }
        }
    }

    fun openEditor(folderId: String, photo: PhotoItem) {
        editorFolderId = folderId; editorPhotoPath = photo.path
    }

    fun showFolderMoreMenu(folder: Inspection) { folderMoreMenuId = folder.id }

    LegacyAppShell(
        palette = palette,
        selected = rootSection,
        compactTitle = when (rootSection) {
            LegacyRootSection.HOME -> "Inspeccion Photos"
            LegacyRootSection.FOLDERS -> selectedFolder()?.name ?: "Cámara"
            LegacyRootSection.FORMS -> "Documentos"
            LegacyRootSection.CONTROL_PANEL -> "Formatos"
            LegacyRootSection.SETTINGS -> "Configuración"
            LegacyRootSection.TRASH -> "Papelera"
            LegacyRootSection.SEARCH -> "Buscar"
        },
        compactSubtitle = when (rootSection) {
            LegacyRootSection.HOME -> ""
            LegacyRootSection.FOLDERS -> selectedFolder()?.let { "${it.children.size} subcarpetas · ${it.photos.size} fotos" } ?: "Organiza fotografías y carpetas"
            LegacyRootSection.FORMS -> "Selecciona y llena un formato"
            LegacyRootSection.CONTROL_PANEL -> "Configura reportes y formatos de formularios"
            LegacyRootSection.SETTINGS -> "Preferencias de la aplicación"
            LegacyRootSection.TRASH -> "Elementos eliminados recuperables"
            LegacyRootSection.SEARCH -> "Buscar carpetas, fotos y documentos"
        },
        onHome = { rootSectionName = LegacyRootSection.HOME.name }
    ) { compact ->
        when (rootSection) {
            LegacyRootSection.HOME -> MainMenuScreen(
                palette = palette,
                compact = compact,
                developerModeEnabled = developerModeEnabled,
                onDeveloperUnlocked = {
                    developerModeEnabled = true
                    prefs.developerModeEnabled = true
                    Toast.makeText(context, "Modo desarrollador desbloqueado", Toast.LENGTH_LONG).show()
                },
                onPhotos = { rootSectionName = LegacyRootSection.FOLDERS.name },
                onForms = { rootSectionName = LegacyRootSection.FORMS.name },
                onTrash = { rootSectionName = LegacyRootSection.TRASH.name },
                onPanel = { rootSectionName = LegacyRootSection.CONTROL_PANEL.name },
                onSettings = { advancedSettingsVisible = false; rootSectionName = LegacyRootSection.SETTINGS.name },
                onSearch = { rootSectionName = LegacyRootSection.SEARCH.name }
            )
            LegacyRootSection.FOLDERS -> {
                val current = selectedFolder()
                if (current == null) {
                    HierarchyRootScreen(
                        folders = folders, palette = palette, compact = compact, rootViewMode = rootViewMode, showOt = showOt,
                        onRootViewMode = { rootViewMode = it; prefs.rootViewMode = it },
                        onOpen = { selectedFolderId = it }, onNewFolder = ::createRootFolderDialog,
                        onEditOt = ::editOt, onRename = ::renameFolder, onDuplicate = ::duplicateFolder, onCloneStructure = ::cloneStructure,
                        onConfigure = { configureFolderId = it.id },
                        onToggleCollapsed = { id -> updateFolder(id) { it.copy(collapsed = !it.collapsed) } },
                        onSetAllCollapsed = { collapsed -> folders.forEach { f -> updateFolder(f.id) { it.copy(collapsed = collapsed) } } },
                        onMoveFolders = { ids, dest ->
                            val result = InspectionTree.move(folders, ids, dest); if (result.moved) persist(result.nodes)
                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        },
                        onExport = ::exportFolder,
                        onDelete = ::requestDeleteFolder,
                        onFolderMenu = ::showFolderMoreMenu,
                        sortMode = prefs.folderSort("_root_").also { sortRevision },
                        onSortChange = { prefs.setFolderSort("_root_", it); sortRevision++ }
                    )
                } else {
                    val ancestors = InspectionTree.ancestors(folders, current.id)
                    val depth = InspectionTree.depth(folders, current.id) ?: 0
                    HierarchyFolderScreen(
                        folder = current, ancestors = ancestors, depth = depth, allRoots = folders,
                        palette = palette, compact = compact, showOt = showOt,
                        onBack = { selectedFolderId = InspectionTree.parentId(folders, current.id) },
                        onSaveOt = { value -> updateFolder(current.id) { it.copy(ot = value) } },
                        onEditGeneralObservation = { editGeneralObservation(current) },
                        onAddChild = { createChildFolderDialog(current.id) },
                        onConfigureFolder = { configureFolderId = current.id },
                        onSetViewMode = { mode -> updateFolder(current.id) { it.copy(viewMode = mode) } },
                        onOpenChild = { selectedFolderId = it },
                        onChildMenu = { child, _ -> showFolderMoreMenu(child) },
                        onToggleChildCollapsed = { id -> updateFolder(id) { it.copy(collapsed = !it.collapsed) } },
                        onSetAllChildrenCollapsed = { collapsed -> current.children.forEach { child -> updateFolder(child.id) { it.copy(collapsed = collapsed) } } },
                        onMoveFolders = { ids, dest ->
                            val result = InspectionTree.move(folders, ids, dest); if (result.moved) persist(result.nodes)
                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        },
                        onAddPhoto = { requestAddPhoto(current.id) },
                        onOpenPhoto = { reviewFolderId = current.id; reviewPhotoPath = it.path },
                        onRenamePhoto = { renamePhoto(current.id, it) },
                        onEditPhoto = { openEditor(current.id, it) },
                        onEditPhotoObservation = { editPhotoObservation(current.id, it) },
                        onDownloadPhoto = { photo ->
                            val uri = ExportUtils.downloadPhoto(context, photo)
                            Toast.makeText(context, if (uri != null) "Foto copiada" else "No se pudo descargar", Toast.LENGTH_SHORT).show()
                        },
                        onDeletePhoto = { deletePhoto(current.id, it) },
                        onDuplicateFolder = { duplicateFolder(current) },
                        onCloneStructure = { cloneStructure(current) },
                        onEditReportTable = { tableEditorFolderId = current.id },
                        onEditMainImage = { mainImageFolderId = current.id },
                        reportSections = reportConfig.sections,
                        onEditReportSections = { sectionEditorFolderId = current.id },
                        onExport = { exportFolder(current) },
                        onFolderMenu = { showFolderMoreMenu(current) },
                        folderSortMode = prefs.folderSort(current.id).also { sortRevision },
                        photoSortMode = prefs.photoSort(current.id).also { sortRevision },
                        onSortPhotos = { prefs.setPhotoSort(current.id, it); sortRevision++ },
                        onMovePhoto = { photo ->
                            photoTransferFolderId = current.id
                            photoTransferPath = photo.path
                            photoTransferCopy = false
                        },
                        onCopyPhoto = { photo ->
                            photoTransferFolderId = current.id
                            photoTransferPath = photo.path
                            photoTransferCopy = true
                        },
                        onToggleMainPhoto = { photo -> toggleMainPhoto(current.id, photo) },
                        onCameraSettings = { cameraSettingsVisible = true }
                    )
                }
            }
            LegacyRootSection.FORMS -> FormsScreen(
                palette = palette,
                compact = compact,
                templates = formTemplates,
                store = formStore,
                initialOpenSubmissionId = requestedFormId,
                onMoveToTrash = { form ->
                    sendToTrash(TrashType.FORM, form.template.name, formStore.encodeTrashSubmission(form))
                    formStore.deleteSubmission(form.id)
                }
            )
            LegacyRootSection.CONTROL_PANEL -> PanelControlScreen(
                palette = palette,
                compact = compact,
                config = reportConfig,
                store = reportStore,
                formTemplates = formTemplates,
                formStore = formStore,
                onConfigChange = { updated ->
                    reportConfig = updated.normalized()
                    reportStore.save(reportConfig)
                    photoPresets = photoPresets.map { preset ->
                        if (preset.id == activePhotoPresetId)
                            preset.copy(config = reportConfig, updatedAt = System.currentTimeMillis())
                        else preset
                    }
                    photoPresetStore.save(photoPresets)
                },
                onFormTemplatesChange = { updated ->
                    formTemplates = updated.map { it.normalized() }
                    formStore.saveTemplates(formTemplates)
                },
                onTemplateTrash = { template ->
                    sendToTrash(TrashType.TEMPLATE, template.name, formStore.encodeTrashTemplate(template))
                },
                defaultFormTemplateId = defaultFormTemplateId,
                onDefaultFormTemplateChange = { id ->
                    defaultFormTemplateId = id
                    prefs.defaultFormTemplateId = id
                },
                photoPresets = photoPresets,
                activePhotoPresetId = activePhotoPresetId,
                defaultPhotoReport = defaultPhotoReport,
                onPhotoPresetSelect = { id ->
                    photoPresets.firstOrNull { it.id == id }?.let { preset ->
                        activePhotoPresetId = id
                        prefs.activePhotoPresetId = id
                        reportConfig = preset.config
                    }
                },
                onPhotoPresetNew = {
                    val preset = photoPresetStore.newPreset(
                        "Reporte fotográfico ${photoPresets.size + 1}", reportConfig
                    )
                    photoPresets = photoPresets + preset
                    photoPresetStore.save(photoPresets)
                    activePhotoPresetId = preset.id
                    prefs.activePhotoPresetId = preset.id
                    reportConfig = preset.config
                },
                onPhotoPresetDuplicate = {
                    photoPresets.firstOrNull { it.id == activePhotoPresetId }?.let { source ->
                        val preset = photoPresetStore.newPreset("${source.name} - Copia", source.config)
                        photoPresets = photoPresets + preset
                        photoPresetStore.save(photoPresets)
                        activePhotoPresetId = preset.id
                        prefs.activePhotoPresetId = preset.id
                        reportConfig = preset.config
                    }
                },
                onPhotoPresetRename = {
                    photoPresets.firstOrNull { it.id == activePhotoPresetId }?.let { preset ->
                        inputDialog = LegacyInputState(
                            "Renombrar formato", "Nombre", preset.name
                        ) { raw ->
                            val name = raw.trim().take(70)
                            if (name.isNotEmpty()) {
                                photoPresets = photoPresets.map {
                                    if (it.id == preset.id) it.copy(name = name) else it
                                }
                                photoPresetStore.save(photoPresets)
                            }
                        }
                    }
                },
                onPhotoPresetTrash = {
                    if (photoPresets.size > 1) {
                        photoPresets.firstOrNull { it.id == activePhotoPresetId }?.let { preset ->
                            sendToTrash(
                                TrashType.PHOTO_TEMPLATE, preset.name,
                                photoPresetStore.encodePreset(preset)
                            )
                            photoPresets = photoPresets.filterNot { it.id == preset.id }
                            photoPresetStore.save(photoPresets)
                            val next = photoPresets.first()
                            activePhotoPresetId = next.id
                            prefs.activePhotoPresetId = next.id
                            reportConfig = next.config
                            if (defaultPhotoReport == "Preset:${preset.id}") {
                                defaultPhotoReport = "PDF 3"
                                prefs.defaultPhotoReport = "PDF 3"
                            }
                        }
                    }
                },
                onDefaultPhotoReportChange = { option ->
                    defaultPhotoReport = option
                    prefs.defaultPhotoReport = option
                }
            )
            LegacyRootSection.TRASH -> TrashScreen(
                palette = palette,
                entries = trashEntries,
                onRestore = { entries ->
                    val restoredIds = mutableSetOf<String>()
                    val sorted = entries.sortedBy { if (it.type == TrashType.FOLDER) 0 else 1 }
                    sorted.forEach { entry ->
                        runCatching {
                            when (entry.type) {
                                TrashType.FOLDER -> {
                                    val folder = repository.decodeTrashFolder(entry.payload) ?: error("Carpeta inválida")
                                    if (InspectionTree.find(folders, folder.id) == null) {
                                        val parent = entry.parentId?.takeIf { InspectionTree.find(folders, it) != null }
                                        persist(InspectionTree.insert(folders, parent, folder))
                                    }
                                }
                                TrashType.PHOTO -> {
                                    val json = org.json.JSONObject(entry.payload)
                                    val photo = PhotoItem(
                                        json.getString("name"), json.getString("path"),
                                        json.optString("observation"), json.optString("originalPath")
                                    )
                                    val parent = entry.parentId?.let { InspectionTree.find(folders, it) }
                                    if (parent != null) {
                                        updateFolder(parent.id) { folder ->
                                            if (folder.photos.any { it.path == photo.path }) folder
                                            else folder.copy(photos = folder.photos + photo)
                                        }
                                    } else {
                                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                        val recovered = folders.firstOrNull { it.name == "Fotos recuperadas" }
                                        if (recovered != null) updateFolder(recovered.id) { it.copy(photos = it.photos + photo) }
                                        else persist(folders + Inspection(UUID.randomUUID().toString(), "Fotos recuperadas", date, photos = listOf(photo)))
                                    }
                                }
                                TrashType.FORM -> formStore.upsertSubmission(formStore.decodeTrashSubmission(entry.payload))
                                TrashType.TEMPLATE -> {
                                    val template = formStore.decodeTrashTemplate(entry.payload)
                                    if (formTemplates.none { it.id == template.id }) {
                                        formTemplates = formTemplates + template
                                        formStore.saveTemplates(formTemplates)
                                    }
                                }
                                TrashType.PHOTO_TEMPLATE -> {
                                    val preset = photoPresetStore.decodePreset(entry.payload)
                                    if (photoPresets.none { it.id == preset.id }) {
                                        photoPresets = photoPresets + preset
                                        photoPresetStore.save(photoPresets)
                                    }
                                }
                            }
                            restoredIds.add(entry.id)
                        }.onFailure {
                            Toast.makeText(context, "No se pudo restaurar: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                    trashStore.remove(restoredIds)
                    trashEntries = trashStore.load()
                },
                onDeleteForever = { entries ->
                    val removedIds = mutableSetOf<String>()
                    entries.forEach { entry ->
                        runCatching {
                            when (entry.type) {
                                TrashType.FOLDER -> repository.decodeTrashFolder(entry.payload)?.let(::deleteFilesRecursive)
                                TrashType.PHOTO -> {
                                    val json = org.json.JSONObject(entry.payload)
                                    mediaStore.deletePhoto(PhotoItem(
                                        json.getString("name"), json.getString("path"),
                                        json.optString("observation"), json.optString("originalPath")
                                    ))
                                }
                                else -> Unit
                            }
                            removedIds.add(entry.id)
                        }.onFailure {
                            Toast.makeText(context, "No se pudo eliminar: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                    trashStore.remove(removedIds)
                    trashEntries = trashStore.load()
                }
            )
            LegacyRootSection.SEARCH -> SearchScreen(
                palette = palette,
                folders = folders,
                submissions = formStore.loadSubmissions(),
                templates = formTemplates,
                onOpenFolder = { selectedFolderId = it; rootSectionName = LegacyRootSection.FOLDERS.name },
                onOpenForm = { requestedFormId = it; rootSectionName = LegacyRootSection.FORMS.name },
                onOpenFormats = { rootSectionName = LegacyRootSection.CONTROL_PANEL.name }
            )
            LegacyRootSection.SETTINGS -> if (advancedSettingsVisible) {
                Column(Modifier.fillMaxSize()) {
                    TextButton(onClick = { advancedSettingsVisible = false }) {
                        Text("‹ Configuración básica")
                    }
                    Box(Modifier.weight(1f)) {
                        LegacySettingsScreen(
                palette = palette,
                compact = compact,
                developerMode = developerModeEnabled,
                darkMode = darkMode,
                spatialFunctionsEnabled = spatialFunctionsEnabled,
                showOt = showOt,
                companyName = companyName,
                pdfLogoConfigured = pdfLogoPath.isNotBlank() && File(pdfLogoPath).exists(),
                addPhotoAction = addPhotoAction,
                editorPromptMode = editorPromptMode,
                cameraSessionMode = cameraSessionMode,
                askPhotoConfirmation = askPhotoConfirmation,
                hiddenLegacyPdfs = hiddenLegacyPdfs,
                googleDriveConnected = googleDriveConnected,
                googleDriveConnectedAt = googleDriveConnectedAt,
                onDarkModeChange = { darkMode = it; prefs.darkMode = it },
                onSpatialFunctionsChange = { enabled ->
                    spatialFunctionsEnabled = enabled
                    prefs.spatialFunctionsEnabled = enabled
                    if (enabled && !locationPermissionGranted) {
                        spatialPermissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    }
                },
                onShowOtChange = { showOt = it; prefs.showOt = it },
                onCompanyNameChange = { companyName = it.trim(); prefs.companyName = companyName; Toast.makeText(context, "Nombre de empresa guardado", Toast.LENGTH_SHORT).show() },
                onChoosePdfLogo = { pdfLogoPickerLauncher.launch("image/*") },
                onClearPdfLogo = { prefs.clearPdfLogo(); pdfLogoPath = ""; Toast.makeText(context, "Logo eliminado", Toast.LENGTH_SHORT).show() },
                onAddPhotoActionChange = { addPhotoAction = it; prefs.addPhotoAction = it },
                onEditorPromptModeChange = { editorPromptMode = it; prefs.editorPromptMode = it },
                onCameraSessionModeChange = { cameraSessionMode = it; prefs.cameraSessionMode = it },
                onAskPhotoConfirmationChange = { askPhotoConfirmation = it; prefs.askPhotoConfirmation = it },
                onHiddenLegacyPdfsChange = { hiddenLegacyPdfs = it; prefs.hiddenLegacyPdfs = it },
                onGoogleDriveConnectionChange = { connected, connectedAt ->
                    googleDriveConnected = connected
                    googleDriveConnectedAt = connectedAt
                    prefs.googleDriveConnected = connected
                    prefs.googleDriveConnectedAt = connectedAt
                },
                onExportAllConfiguration = {
                    val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.getDefault()).format(Date())
                    configurationExportLauncher.launch("Inspeccion_Photos_configuracion_$stamp.json")
                },
                onImportAllConfiguration = {
                    configurationImportLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
                }
                        )
                    }
                }
            } else {
                SimpleAppSettingsScreen(
                    palette = palette,
                    darkMode = darkMode,
                    companyName = companyName,
                    logoConfigured = pdfLogoPath.isNotBlank() && File(pdfLogoPath).exists(),
                    developerMode = developerModeEnabled,
                    spatialFunctionsEnabled = spatialFunctionsEnabled,
                    onDarkModeChange = { darkMode = it; prefs.darkMode = it },
                    onCompanyNameChange = {
                        companyName = it.trim()
                        prefs.companyName = companyName
                        Toast.makeText(context, "Nombre de empresa guardado", Toast.LENGTH_SHORT).show()
                    },
                    onChooseLogo = { pdfLogoPickerLauncher.launch("image/*") },
                    onClearLogo = { prefs.clearPdfLogo(); pdfLogoPath = "" },
                    onSpatialChange = { enabled ->
                        spatialFunctionsEnabled = enabled
                        prefs.spatialFunctionsEnabled = enabled
                        if (enabled && !locationPermissionGranted) {
                            spatialPermissionLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            )
                        }
                    },
                    onAdvanced = { advancedSettingsVisible = true }
                )
            }
        }
    }

    addPhotoChoiceFolderId?.let { folderId ->
        LegacyMenuDialog(
            title = "Agregar foto",
            options = listOf("Tomar foto" to { requestTakePhoto(folderId) }, "Seleccionar imagen" to { requestPickPhoto(folderId) }),
            onDismiss = { addPhotoChoiceFolderId = null },
            palette = palette
        )
    }

    batchCameraFolderId?.let { folderId ->
        InspectionTree.find(folders, folderId)?.let { folder ->
            LegacyBatchCameraScreen(
                folder = folder, mediaStore = mediaStore, palette = palette,
                singleShot = batchCameraSingleShot,
                askBeforeSaving = askPhotoConfirmation,
                observationProvider = { spatialObservation() },
                onFinish = { captures ->
                    batchCameraFolderId = null
                    updateFolder(folderId) { current ->
                        current.copy(
                            photos = current.photos + captures.map { capture ->
                                PhotoItem(
                                    name = capture.file.name,
                                    path = capture.file.absolutePath,
                                    observation = capture.observation
                                )
                            }
                        )
                    }
                    Toast.makeText(context, "${captures.size} foto(s) añadidas", Toast.LENGTH_SHORT).show()
                    if (captures.isNotEmpty() && editorPromptMode == EditorPromptMode.ASK_AFTER_CAPTURE) {
                        promptEditorFolderId = folderId; promptEditorPhotoPath = captures.first().file.absolutePath
                    }
                },
                onCancel = { batchCameraFolderId = null }
            )
        }
    }

    promptEditorPhotoPath?.let { path ->
        val folderId = promptEditorFolderId
        PostCaptureEditPrompt(
            photoPath = path,
            palette = palette,
            onEdit = {
                if (folderId != null) {
                    editorFolderId = folderId
                    editorPhotoPath = path
                }
                promptEditorFolderId = null
                promptEditorPhotoPath = null
            },
            onKeep = {
                promptEditorFolderId = null
                promptEditorPhotoPath = null
            }
        )
    }

    val editorFolder = InspectionTree.find(folders, editorFolderId)
    val editorPhoto = editorFolder?.photos?.firstOrNull { it.path == editorPhotoPath }
    if (editorFolder != null && editorPhoto != null) {
        PhotoEditorDialog(
            photo = editorPhoto, palette = palette,
            onDismiss = { editorFolderId = null; editorPhotoPath = null },
            onSave = { params ->
                val folderId = editorFolder.id; val oldPath = editorPhoto.path
                editorFolderId = null; editorPhotoPath = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { PhotoEditorEngine.saveEdited(editorPhoto, params) } }
                    result.onSuccess { updated -> updatePhoto(folderId, oldPath) { updated }; Toast.makeText(context, "Edición guardada", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(context, it.message ?: "No se pudo editar la foto", Toast.LENGTH_LONG).show() }
                }
            },
            onRestoreOriginal = if (editorPhoto.originalPath.isNotBlank()) ({
                val folderId = editorFolder.id; val oldPath = editorPhoto.path
                editorFolderId = null; editorPhotoPath = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { PhotoEditorEngine.restoreOriginal(editorPhoto) } }
                    result.onSuccess { restored -> updatePhoto(folderId, oldPath) { restored }; Toast.makeText(context, "Original restaurado", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(context, it.message ?: "No se pudo restaurar", Toast.LENGTH_LONG).show() }
                }
            }) else null
        )
    }

    val reviewFolder = InspectionTree.find(folders, reviewFolderId)
    val reviewPhoto = reviewFolder?.photos?.firstOrNull { it.path == reviewPhotoPath }
    if (reviewPhoto != null) HierarchyImageReviewDialog(reviewPhoto, palette) { reviewFolderId = null; reviewPhotoPath = null }

    configureFolderId?.let { id ->
        InspectionTree.find(folders, id)?.let { folder ->
            FolderConfigurationDialog(folder, palette, { configureFolderId = null }) { allowPhotos, allowSubfolders, viewMode ->
                updateFolder(id) { it.copy(allowPhotos = allowPhotos, allowSubfolders = allowSubfolders, viewMode = viewMode) }
            }
        }
    }

    folderPreviewId?.let { id ->
        InspectionTree.find(folders, id)?.let { folder ->
            FolderSectionPreviewDialog(
                folder = folder,
                config = reportConfig,
                palette = palette,
                onDismiss = { folderPreviewId = null },
                onEditTable = { tableEditorFolderId = id },
                onEditSections = { sectionEditorFolderId = id },
                onEditSectionHeading = { sectionHeadingFolderId = id },
                onEditMainImage = { mainImageFolderId = id },
                onEditObservation = { editGeneralObservation(folder) },
                onToggleTable = { updateFolder(id) { it.copy(reportTableVisible = !it.reportTableVisible) } },
                onToggleMainImage = { updateFolder(id) { it.copy(reportMainImageVisible = !it.reportMainImageVisible) } },
                onToggleSections = { updateFolder(id) { it.copy(reportSectionVisible = !it.reportSectionVisible) } }
            )
        }
    }

    sectionHeadingFolderId?.let { id ->
        val folder = InspectionTree.find(folders, id)
        if (folder != null) {
            LegacyMenuDialog(
                title = "Elegir encabezado",
                options = reportConfig.sections.map { section ->
                    section.title to {
                        inputDialog = LegacyInputState(
                            "Editar encabezado de sección",
                            "Título",
                            folder.reportSectionTitles[section.id] ?: section.title
                        ) { newTitle ->
                            updateFolder(id) { current ->
                                current.copy(reportSectionTitles = current.reportSectionTitles +
                                    (section.id to newTitle.trim().take(120)))
                            }
                        }
                    }
                },
                onDismiss = { sectionHeadingFolderId = null },
                palette = palette
            )
        }
    }

    folderMoreMenuId?.let { id ->
        InspectionTree.find(folders, id)?.let { folder ->
            LegacyMenuDialog(
                title = folder.name,
                options = listOf(
                    "Herramientas de carpeta" to { folderToolsId = id },
                    "Vista previa de esta sección" to { folderPreviewId = id },
                    "Compartir rápido" to { shareQuick(folder) },
                    "Compartir completo" to { exportFolder(folder) }
                ),
                onDismiss = { folderMoreMenuId = null },
                palette = palette
            )
        }
    }

    folderToolsId?.let { id ->
        InspectionTree.find(folders, id)?.let { folder ->
            LegacyMenuDialog(
                title = "Herramientas de carpeta",
                options = listOf(
                    "Ordenar por" to { folderSortPickerId = id },
                    "Clonar estructura en" to { cloneFolderId = id },
                    "Mover a" to { singleMoveFolderId = id },
                    "Copiar en" to { copyFolderId = id },
                    "Cambiar nombre" to { renameFolder(folder) },
                    "Mover a la Papelera" to { requestDeleteFolder(folder) },
                    "Configuración de carpeta" to { configureFolderId = id }
                ),
                onDismiss = { folderToolsId = null },
                palette = palette
            )
        }
    }

    folderSortPickerId?.let { id ->
        LegacyMenuDialog(
            title = "Ordenar por",
            options = listOf("A-Z", "Z-A", "Fecha de creación", "Fecha de modificación").map { option ->
                option to { prefs.setFolderSort(id, option); sortRevision++ }
            },
            onDismiss = { folderSortPickerId = null },
            palette = palette
        )
    }

    exportDialogFolderId?.let { id ->
        InspectionTree.find(folders, id)?.let { folder ->
            ExportSelectionDialog(
                initial = prefs.exportSelection,
                photoPresets = photoPresets,
                hiddenLegacyPdfs = hiddenLegacyPdfs,
                spatialFunctionsEnabled = spatialFunctionsEnabled,
                palette = palette,
                onDismiss = { exportDialogFolderId = null },
                onExport = { selection -> exportDialogFolderId = null; executeExport(folder, selection) }
            )
        }
    }

    tableEditorFolderId?.let { id ->
        InspectionTree.find(folders, id)?.let { folder ->
            ReportTableEditorDialog(
                folder = folder, config = reportConfig, palette = palette,
                onDismiss = { tableEditorFolderId = null },
                onSave = { values -> tableEditorFolderId = null; updateFolder(id) { it.copy(reportTableEnabled = true, reportTableValues = values) } },
                onRemove = { tableEditorFolderId = null; updateFolder(id) { it.copy(reportTableEnabled = false, reportTableValues = emptyList()) } }
            )
        }
    }

    sectionEditorFolderId?.let { id ->
        InspectionTree.find(folders, id)?.let { folder ->
            ReportSectionsEditorDialog(
                folder = folder,
                sections = reportConfig.sections,
                palette = palette,
                onDismiss = { sectionEditorFolderId = null },
                onSave = { values ->
                    sectionEditorFolderId = null
                    updateFolder(id) { it.copy(reportSectionValues = values.mapValues { entry -> entry.value.take(500) }) }
                },
                onClear = {
                    sectionEditorFolderId = null
                    updateFolder(id) { it.copy(reportSectionValues = emptyMap()) }
                }
            )
        }
    }

    mainImageFolderId?.let { id ->
        InspectionTree.find(folders, id)?.let { folder ->
            MainImageSourceDialog(
                folder = folder, palette = palette, onDismiss = { mainImageFolderId = null },
                onUsePhoto = { photo ->
                    mainImageFolderId = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { mediaStore.copyReportMainImageFromFile(id, File(photo.path)) } }
                        result.onSuccess { file -> updateFolder(id) { it.copy(reportMainImagePath = file.absolutePath, mainPhotoPath = photo.path) } }
                            .onFailure { Toast.makeText(context, it.message ?: "No se pudo copiar la imagen principal", Toast.LENGTH_LONG).show() }
                    }
                },
                onTakePhoto = { mainImageFolderId = null; requestMainImageCamera(id) },
                onChooseExternal = { mainImageFolderId = null; pendingMainImageFolderId = id; reportMainImagePickerLauncher.launch("image/*") },
                onClear = { mainImageFolderId = null; updateFolder(id) { it.copy(reportMainImagePath = "", mainPhotoPath = "") } }
            )
        }
    }

    singleMoveFolderId?.let { id ->
        DestinationFolderPicker(
            roots = folders, palette = palette, title = "Mover carpeta a",
            excludedId = id, includeRoot = true,
            onDismiss = { singleMoveFolderId = null },
            onChoose = { destination ->
                val result = InspectionTree.move(folders, setOf(id), destination)
                if (result.moved) persist(result.nodes)
                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                singleMoveFolderId = null
            }
        )
    }

    cloneFolderId?.let { id ->
        DestinationFolderPicker(
            roots = folders, palette = palette, title = "Clonar estructura en",
            excludedId = null, includeRoot = true,
            onDismiss = { cloneFolderId = null },
            onChoose = { destination ->
                InspectionTree.find(folders, id)?.let { cloneStructureTo(it, destination) }
                cloneFolderId = null
            }
        )
    }
    copyFolderId?.let { id ->
        DestinationFolderPicker(
            roots = folders, palette = palette, title = "Copiar carpeta en",
            excludedId = null, includeRoot = true,
            onDismiss = { copyFolderId = null },
            onChoose = { destination ->
                InspectionTree.find(folders, id)?.let { copyFolderTo(it, destination) }
                copyFolderId = null
            }
        )
    }

    photoTransferPath?.let { path ->
        val sourceId = photoTransferFolderId
        if (sourceId != null) {
            DestinationFolderPicker(
                roots = folders, palette = palette,
                title = if (photoTransferCopy) "Copiar foto en" else "Mover foto a",
                includeRoot = false, excludedId = null, requirePhotos = true,
                onDismiss = { photoTransferPath = null; photoTransferFolderId = null },
                onChoose = { destination ->
                    if (destination != null) transferPhoto(sourceId, path, destination, photoTransferCopy)
                    photoTransferPath = null; photoTransferFolderId = null
                }
            )
        }
    }

    if (cameraSettingsVisible) {
        CameraCaptureSettingsDialog(
            palette = palette,
            addPhotoAction = addPhotoAction,
            editorPromptMode = editorPromptMode,
            sessionMode = cameraSessionMode,
            askPhotoConfirmation = askPhotoConfirmation,
            onDismiss = { cameraSettingsVisible = false },
            onAddPhotoActionChange = { addPhotoAction = it; prefs.addPhotoAction = it },
            onEditorPromptModeChange = { editorPromptMode = it; prefs.editorPromptMode = it },
            onSessionModeChange = { cameraSessionMode = it; prefs.cameraSessionMode = it },
            onConfirmationChange = { askPhotoConfirmation = it; prefs.askPhotoConfirmation = it }
        )
    }

    inputDialog?.let { state -> LegacyInputDialog(state) { inputDialog = null } }
    confirmDialog?.let { state ->
        AlertDialog(
            onDismissRequest = { confirmDialog = null }, containerColor = Color.White,
            title = { Text(state.title, color = Color.Black) }, text = { Text(state.message, color = Color.Black) },
            confirmButton = { TextButton(onClick = { confirmDialog = null; state.onConfirm() }) { Text(state.confirmText) } },
            dismissButton = { TextButton(onClick = { confirmDialog = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun MoveSingleFolderDialog(roots: List<Inspection>, selectedId: String, palette: LegacyPalette, onDismiss: () -> Unit, onMove: (String?) -> Unit) {
    val selected = InspectionTree.find(roots, selectedId)
    val refs = InspectionTree.flatten(roots).filter { ref -> ref.id != selectedId && (selected == null || !InspectionTree.contains(selected, ref.id)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { Text("Mover carpeta", color = palette.text) },
        text = {
            Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
                Text("Raíz", color = palette.text, modifier = Modifier.fillMaxWidth().clickable { onMove(null) }.padding(vertical = 12.dp))
                Divider(color = palette.border.copy(alpha = .55f))
                refs.forEach { ref ->
                    Text(ref.path, color = palette.text, modifier = Modifier.fillMaxWidth().clickable { onMove(ref.id) }.padding(vertical = 11.dp))
                    Divider(color = palette.border.copy(alpha = .55f))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = palette.muted) } }
    )
}

@Composable
private fun LegacyInputDialog(state: LegacyInputState, onDismiss: () -> Unit) {
    var value by remember(state.title, state.initial) { mutableStateOf(state.initial) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color.White,
        title = { Text(state.title, color = Color.Black) },
        text = {
            OutlinedTextField(
                value = value, onValueChange = { value = it }, label = { Text(state.label, color = Color.DarkGray) },
                singleLine = !state.multiline, minLines = if (state.multiline) 4 else 1, maxLines = if (state.multiline) 8 else 1,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black, unfocusedTextColor = Color.Black,
                    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                    cursorColor = Color(0xFF1565C0), focusedBorderColor = Color(0xFF1565C0), unfocusedBorderColor = Color.Gray
                )
            )
        },
        confirmButton = { TextButton(onClick = { onDismiss(); state.onConfirm(value) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
