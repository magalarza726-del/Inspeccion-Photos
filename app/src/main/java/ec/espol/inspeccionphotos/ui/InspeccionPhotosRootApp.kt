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
    val zip: File,
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
    val formStore = remember { FormTemplateStore(context) }
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
    var reportConfig by remember { mutableStateOf(reportStore.load()) }
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
        persist(InspectionTree.update(folders, id, transform))
    }

    fun selectedFolder(): Inspection? = InspectionTree.find(folders, selectedFolderId)

    fun updatePhoto(folderId: String, photoPath: String, transform: (PhotoItem) -> PhotoItem) {
        updateFolder(folderId) { folder ->
            folder.copy(photos = folder.photos.map { if (it.path == photoPath) transform(it) else it })
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
        if (addPhotoAction == AddPhotoAction.CAMERA_DIRECT) requestTakePhoto(folderId) else addPhotoChoiceFolderId = folderId
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
            val rootName = uniqueSiblingName(source, "_copia")
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
        val rootName = uniqueSiblingName(source, "_estructura")
        val copy = cloneStructureNode(source, rootName)
        persist(InspectionTree.insert(folders, parentId, copy))
        Toast.makeText(context, "Estructura clonada sin fotos", Toast.LENGTH_SHORT).show()
    }

    fun deleteFilesRecursive(folder: Inspection) {
        folder.photos.forEach(mediaStore::deletePhoto)
        folder.children.forEach(::deleteFilesRecursive)
        mediaStore.deleteInspectionFiles(folder.id)
    }

    fun requestDeleteFolder(folder: Inspection) {
        confirmDialog = LegacyConfirmState("Eliminar carpeta", "¿Eliminar ${folder.name}, sus subcarpetas y sus fotos? Esta acción no puede deshacerse.") {
            deleteFilesRecursive(folder)
            val parent = InspectionTree.parentId(folders, folder.id)
            val (updated, _) = InspectionTree.remove(folders, folder.id)
            persist(updated)
            if (selectedFolderId == folder.id) selectedFolderId = parent
        }
    }

    fun exportFolder(folder: Inspection) {
        exportDialogFolderId = folder.id
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
                        CustomReportExporter.createAll(context, folder, reportConfig)
                    } else emptyList()
                    val backup = if (selection.photosAndStructure) repository.backupFile(folders) else null
                    val zip = ExportUtils.createInspectionZipSelected(
                        context = context,
                        inspection = folder,
                        selection = selection,
                        selectedPdfs = standardReports,
                        spatialReports = spatialReports,
                        customReports = customReports,
                        backup = backup
                    )
                    PreparedFolderExport(zip, standardReports, spatialReports, customReports)
                }
            }
            result.onSuccess { prepared ->
                ExportUtils.shareFile(context, prepared.zip, "application/zip")
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
                        ExportUtils.copyToDownloads(context, prepared.zip, "application/zip")
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
        confirmDialog = LegacyConfirmState("Eliminar foto", "¿Eliminar ${photo.name}?") {
            mediaStore.deletePhoto(photo)
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
                onSettings = { rootSectionName = LegacyRootSection.SETTINGS.name },
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
                        onDelete = ::requestDeleteFolder
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
                        onExport = { exportFolder(current) }
                    )
                }
            }
            LegacyRootSection.FORMS -> FormsScreen(
                palette = palette,
                compact = compact,
                templates = formTemplates,
                store = formStore,
                initialOpenSubmissionId = requestedFormId
            )
            LegacyRootSection.CONTROL_PANEL -> PanelControlScreen(
                palette = palette, compact = compact, config = reportConfig, store = reportStore,
                formTemplates = formTemplates, formStore = formStore,
                onConfigChange = { updated -> reportConfig = updated.normalized(); reportStore.save(reportConfig) },
                onFormTemplatesChange = { updated -> formTemplates = updated.map { it.normalized() }; formStore.saveTemplates(formTemplates) }
            )
            LegacyRootSection.TRASH -> Text("Papelera", color = palette.text)
            LegacyRootSection.SEARCH -> SearchScreen(
                palette = palette,
                folders = folders,
                submissions = formStore.loadSubmissions(),
                templates = formTemplates,
                onOpenFolder = { selectedFolderId = it; rootSectionName = LegacyRootSection.FOLDERS.name },
                onOpenForm = { requestedFormId = it; rootSectionName = LegacyRootSection.FORMS.name },
                onOpenFormats = { rootSectionName = LegacyRootSection.CONTROL_PANEL.name }
            )
            LegacyRootSection.SETTINGS -> LegacySettingsScreen(
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

    folderMoreMenuId?.let { id ->
        InspectionTree.find(folders, id)?.let { folder ->
            LegacyMenuDialog(
                title = folder.name,
                options = buildList {
                    add("Abrir carpeta" to { selectedFolderId = folder.id })
                    if (showOt) add("Agregar/editar OT" to { editOt(folder) })
                    add("Configurar carpeta" to { configureFolderId = folder.id })
                    add("Renombrar carpeta" to { renameFolder(folder) })
                    add("Duplicar carpeta" to { duplicateFolder(folder) })
                    add("Clonar estructura" to { cloneStructure(folder) })
                    add((if (folder.collapsed) "Expandir carpeta" else "Contraer carpeta") to { updateFolder(folder.id) { it.copy(collapsed = !it.collapsed) } })
                    add("Mover carpeta" to { singleMoveFolderId = folder.id })
                    add("Eliminar carpeta" to { requestDeleteFolder(folder) })
                }, onDismiss = { folderMoreMenuId = null }, palette = palette
            )
        }
    }

    exportDialogFolderId?.let { id ->
        InspectionTree.find(folders, id)?.let { folder ->
            ExportSelectionDialog(
                initial = prefs.exportSelection,
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
                        result.onSuccess { file -> updateFolder(id) { it.copy(reportMainImagePath = file.absolutePath) } }
                            .onFailure { Toast.makeText(context, it.message ?: "No se pudo copiar la imagen principal", Toast.LENGTH_LONG).show() }
                    }
                },
                onTakePhoto = { mainImageFolderId = null; requestMainImageCamera(id) },
                onChooseExternal = { mainImageFolderId = null; pendingMainImageFolderId = id; reportMainImagePickerLauncher.launch("image/*") },
                onClear = { mainImageFolderId = null; updateFolder(id) { it.copy(reportMainImagePath = "") } }
            )
        }
    }

    singleMoveFolderId?.let { id ->
        MoveSingleFolderDialog(folders, id, palette, { singleMoveFolderId = null }) { destination ->
            val result = InspectionTree.move(folders, setOf(id), destination)
            if (result.moved) persist(result.nodes)
            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
            singleMoveFolderId = null
        }
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
