package ec.espol.inspeccionphotos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.espol.inspeccionphotos.FolderViewMode
import ec.espol.inspeccionphotos.Inspection
import ec.espol.inspeccionphotos.ImageOrientationUtils
import ec.espol.inspeccionphotos.InspectionTree
import ec.espol.inspeccionphotos.PhotoItem
import ec.espol.inspeccionphotos.R
import ec.espol.inspeccionphotos.SectionDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FolderAmber = Color(0xFFFFC83D)

@Composable
fun HierarchyRootScreen(
    folders: List<Inspection>,
    palette: LegacyPalette,
    compact: Boolean,
    rootViewMode: FolderViewMode,
    showOt: Boolean,
    onRootViewMode: (FolderViewMode) -> Unit,
    onOpen: (String) -> Unit,
    onNewFolder: () -> Unit,
    onEditOt: (Inspection) -> Unit,
    onRename: (Inspection) -> Unit,
    onDuplicate: (Inspection) -> Unit,
    onCloneStructure: (Inspection) -> Unit,
    onConfigure: (Inspection) -> Unit,
    onToggleCollapsed: (String) -> Unit,
    onSetAllCollapsed: (Boolean) -> Unit,
    onMoveFolders: (Set<String>, String?) -> Unit,
    onExport: (Inspection) -> Unit,
    onDelete: (Inspection) -> Unit,
    onFolderMenu: (Inspection) -> Unit,
    sortMode: String = "Fecha de creación",
    onSortChange: (String) -> Unit = {}
) {
    var search by remember { mutableStateOf("") }
    var showRootSort by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Inspection?>(null) }
    var organize by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showMove by remember { mutableStateOf(false) }

    val filtered = remember(folders, search, sortMode) {
        val q = search.trim()
        orderedFolders(if (q.isBlank()) folders else folders.filter { containsName(it, q) }, sortMode)
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (compact) 12.dp else 24.dp, vertical = if (compact) 12.dp else 16.dp)
                .padding(bottom = if (compact) 88.dp else 20.dp)
        ) {
            if (!compact) {
                LegacyTitle("Carpetas", palette, 26)
                Spacer(Modifier.height(3.dp))
                LegacySubtitle("Gestiona tu estructura de carpetas y fotografías.", palette)
                Spacer(Modifier.height(16.dp))
            }

            if (organize) {
                ReorganizeHeader(
                    count = selected.size,
                    palette = palette,
                    onCancel = { organize = false; selected = emptySet() },
                    onMove = { if (selected.isNotEmpty()) showMove = true },
                    onCollapse = { onSetAllCollapsed(true) },
                    onExpand = { onSetAllCollapsed(false) }
                )
                Spacer(Modifier.height(12.dp))
                ReorganizeFolderList(
                    folders = filtered,
                    selected = selected,
                    palette = palette,
                    onToggle = { selected = selected.toggle(it) }
                )
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Buscar carpetas...", color = palette.muted) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_search_original), null, tint = palette.muted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = palette.text,
                            unfocusedTextColor = palette.text,
                            focusedBorderColor = palette.blue,
                            unfocusedBorderColor = palette.border,
                            focusedContainerColor = palette.surface,
                            unfocusedContainerColor = palette.surface,
                            cursorColor = palette.blue
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    LegacyIconActionButton("⇅", palette) { showRootSort = true }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${folders.size} carpeta(s)",
                        color = palette.muted,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { organize = true; selected = emptySet() }) {
                        Text("Reorganizar", color = palette.cyan, fontWeight = FontWeight.SemiBold)
                    }
                    if (!compact) {
                        Spacer(Modifier.width(6.dp))
                        IconButton(onClick = onNewFolder) {
                            Icon(painterResource(R.drawable.ic_folder_original), "Nueva carpeta", tint = palette.blue)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                if (filtered.isEmpty()) {
                    EmptyFolderMessage(palette, if (folders.isEmpty()) "No hay carpetas. Crea la primera para comenzar." else "No hay coincidencias.")
                } else {
                    FolderGallery(
                        folders = filtered,
                        palette = palette,
                        compact = compact,
                        showOt = showOt,
                        organize = false,
                        selected = emptySet(),
                        onSelect = {},
                        onOpen = onOpen,
                        onMenu = onFolderMenu,
                        onToggleCollapsed = onToggleCollapsed
                    )

            }
        }

        if (compact && !organize) {
            FloatingActionButton(
                onClick = onNewFolder,
                modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
                containerColor = palette.blue,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(painterResource(R.drawable.ic_plus_original), contentDescription = "Nueva carpeta", modifier = Modifier.size(26.dp))
            }
        }
    }

    if (showRootSort) {
        LegacyMenuDialog(
            title = "Ordenar carpetas",
            options = folderSortOptions.map { option -> option to { onSortChange(option) } },
            onDismiss = { showRootSort = false },
            palette = palette
        )
    }

    if (showMove) {
        MoveFoldersDialog(
            allFolders = folders,
            selectedIds = selected,
            palette = palette,
            onDismiss = { showMove = false },
            onMove = { destination ->
                showMove = false
                organize = false
                onMoveFolders(selected, destination)
                selected = emptySet()
            }
        )
    }
}

@Composable
fun HierarchyFolderScreen(
    folder: Inspection,
    ancestors: List<Inspection>,
    depth: Int,
    allRoots: List<Inspection>,
    palette: LegacyPalette,
    compact: Boolean,
    showOt: Boolean,
    onBack: () -> Unit,
    onSaveOt: (String) -> Unit,
    onEditGeneralObservation: () -> Unit,
    onAddChild: () -> Unit,
    onConfigureFolder: () -> Unit,
    onSetViewMode: (FolderViewMode) -> Unit,
    onOpenChild: (String) -> Unit,
    onChildMenu: (Inspection, String) -> Unit,
    onToggleChildCollapsed: (String) -> Unit,
    onSetAllChildrenCollapsed: (Boolean) -> Unit,
    onMoveFolders: (Set<String>, String?) -> Unit,
    onAddPhoto: () -> Unit,
    onOpenPhoto: (PhotoItem) -> Unit,
    onRenamePhoto: (PhotoItem) -> Unit,
    onEditPhoto: (PhotoItem) -> Unit,
    onEditPhotoObservation: (PhotoItem) -> Unit,
    onDownloadPhoto: (PhotoItem) -> Unit,
    onDeletePhoto: (PhotoItem) -> Unit,
    onDuplicateFolder: () -> Unit,
    onCloneStructure: () -> Unit,
    onEditReportTable: () -> Unit,
    onEditMainImage: () -> Unit,
    reportSections: List<SectionDefinition> = emptyList(),
    onEditReportSections: () -> Unit = {},
    onExport: () -> Unit,
    onFolderMenu: () -> Unit,
    photoSortMode: String = "Fecha de creación",
    folderSortMode: String = "Fecha de creación",
    onSortPhotos: (String) -> Unit = {},
    onMovePhoto: (PhotoItem) -> Unit = {},
    onCopyPhoto: (PhotoItem) -> Unit = {},
    onToggleMainPhoto: (PhotoItem) -> Unit = {},
    onCameraSettings: () -> Unit = {}
) {
    var ot by remember(folder.id, folder.ot) { mutableStateOf(folder.ot) }
    var childMenu by remember { mutableStateOf<Inspection?>(null) }
    var photoMenu by remember { mutableStateOf<PhotoItem?>(null) }
    var photoToolsFor by remember { mutableStateOf<PhotoItem?>(null) }
    var photoReportFor by remember { mutableStateOf<PhotoItem?>(null) }
    var photoSortVisible by remember { mutableStateOf(false) }
    var folderActions by remember { mutableStateOf(false) }
    var organize by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showMove by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (compact) 12.dp else 22.dp, vertical = 10.dp)
            .padding(bottom = 22.dp)
    ) {
        if (compact) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("‹", color = palette.muted, fontSize = 34.sp, modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 5.dp))
                Text(
                    (ancestors + folder).joinToString(" / ") { it.name },
                    color = palette.muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("‹", color = palette.muted, fontSize = 40.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 10.dp))
                Column(Modifier.weight(1f)) {
                    LegacyTitle(folder.name, palette, 25)
                    LegacySubtitle("Nivel ${depth + 1} · ${(ancestors + folder).joinToString(" / ") { it.name }}", palette)
                }
            }
        }

        if (showOt) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().background(palette.surface, RoundedCornerShape(12.dp)).padding(9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("OT", color = palette.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = ot,
                    onValueChange = { ot = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = palette.text,
                        unfocusedTextColor = palette.text,
                        focusedBorderColor = palette.blue,
                        unfocusedBorderColor = palette.border,
                        focusedContainerColor = palette.surface2,
                        unfocusedContainerColor = palette.surface2,
                        cursorColor = palette.blue
                    ),
                    shape = RoundedCornerShape(9.dp)
                )
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = { onSaveOt(ot) }) { Text("Guardar", color = palette.blue) }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (folder.allowPhotos) {
                IconButton(onClick = onAddPhoto) {
                    Icon(painterResource(R.drawable.ic_camera_original), "Agregar foto", tint = palette.blue)
                }
            }
            if (folder.allowSubfolders && depth < InspectionTree.MAX_SUBFOLDER_LEVELS) {
                IconButton(onClick = onAddChild) {
                    Icon(painterResource(R.drawable.ic_folder_original), "Agregar carpeta", tint = palette.blue)
                }
            }
            Spacer(Modifier.weight(1f))
            LegacyIconActionButton("⋮", palette) { onFolderMenu() }
        }

        if (folder.reportMainImagePath.isNotBlank()) {
            val mainImageFile = remember(folder.reportMainImagePath) { File(folder.reportMainImagePath) }
            val mainBitmap = remember(folder.reportMainImagePath) { ImageOrientationUtils.decodeOrientedBitmap(mainImageFile, 1600) }
            if (mainBitmap != null) {
                Spacer(Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = palette.surface),
                    border = BorderStroke(1.dp, palette.border),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(9.dp)) {
                        Text("Imagen principal", color = palette.text, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        Spacer(Modifier.height(6.dp))
                        Image(
                            bitmap = mainBitmap.asImageBitmap(),
                            contentDescription = "Imagen principal del reporte",
                            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 260.dp).clip(RoundedCornerShape(9.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }


        if (folder.generalObs.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            LegacyHintBox(folder.generalObs, palette, Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Subcarpetas", color = palette.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${folder.children.size}", color = palette.muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))

        if (organize) {
            ReorganizeHeader(
                count = selected.size,
                palette = palette,
                onCancel = { organize = false; selected = emptySet() },
                onMove = { if (selected.isNotEmpty()) showMove = true },
                onCollapse = { onSetAllChildrenCollapsed(true) },
                onExpand = { onSetAllChildrenCollapsed(false) }
            )
            Spacer(Modifier.height(8.dp))
            ReorganizeFolderList(folder.children, selected, palette) { selected = selected.toggle(it) }
        } else if (folder.children.isEmpty()) {
            LegacySubtitle(if (folder.allowSubfolders) "No hay subcarpetas." else "La creación de subcarpetas está desactivada.", palette)
        } else {
            FolderGallery(
                folders = orderedFolders(folder.children, folderSortMode),
                palette = palette,
                compact = compact,
                showOt = showOt,
                organize = false,
                selected = emptySet(),
                onSelect = {},
                onOpen = onOpenChild,
                onMenu = { onChildMenu(it, "more") },
                onToggleCollapsed = onToggleChildCollapsed
            )
        }

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Fotos", color = palette.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${folder.photos.size}", color = palette.muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        if (folder.photos.isEmpty()) {
            LegacySubtitle(if (folder.allowPhotos) "No hay fotos en esta carpeta." else "La toma de fotos está desactivada.", palette)
        } else {
            PhotoGallery(orderedPhotos(folder.photos, photoSortMode), palette, compact, onOpenPhoto,
                mainPhotoPath = folder.mainPhotoPath, onMenu = { photoMenu = it })
        }
    }

    photoMenu?.let { photo ->
        LegacyMenuDialog(
            title = photo.name,
            options = listOf(
                "Herramientas de fotos" to { photoToolsFor = photo },
                "Herramientas de reporte" to { photoReportFor = photo },
                "Configuración de cámara" to onCameraSettings
            ),
            onDismiss = { photoMenu = null },
            palette = palette
        )
    }

    photoToolsFor?.let { photo ->
        LegacyMenuDialog(
            title = "Herramientas de fotos",
            options = listOf(
                "Ordenar por" to { photoSortVisible = true },
                "Editar foto" to { onEditPhoto(photo) },
                "Mover a" to { onMovePhoto(photo) },
                "Copiar en" to { onCopyPhoto(photo) },
                "Cambiar nombre" to { onRenamePhoto(photo) },
                "Mover a la Papelera" to { onDeletePhoto(photo) }
            ),
            onDismiss = { photoToolsFor = null },
            palette = palette
        )
    }

    photoReportFor?.let { photo ->
        LegacyMenuDialog(
            title = "Herramientas de reporte",
            options = listOf(
                "Añadir observación" to { onEditPhotoObservation(photo) },
                (if (folder.mainPhotoPath == photo.path) "Quitar imagen principal" else "Usar como imagen principal") to {
                    onToggleMainPhoto(photo)
                }
            ),
            onDismiss = { photoReportFor = null },
            palette = palette
        )
    }

    if (photoSortVisible) {
        LegacyMenuDialog(
            title = "Ordenar fotos",
            options = folderSortOptions.map { option -> option to { onSortPhotos(option) } },
            onDismiss = { photoSortVisible = false },
            palette = palette
        )
    }

    if (showMove) {
        MoveFoldersDialog(
            allFolders = allRoots,
            selectedIds = selected,
            palette = palette,
            onDismiss = { showMove = false },
            onMove = { destination ->
                showMove = false
                organize = false
                onMoveFolders(selected, destination)
                selected = emptySet()
            }
        )
    }
}

@Composable
private fun ReorganizeHeader(
    count: Int,
    palette: LegacyPalette,
    onCancel: () -> Unit,
    onMove: () -> Unit,
    onCollapse: () -> Unit,
    onExpand: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(1.dp, palette.border),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Reorganizar carpetas", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("Seleccionadas: $count", color = palette.muted, fontSize = 12.sp)
                }
                TextButton(onClick = onCancel) { Text("Cancelar", color = palette.muted) }
            }
            Spacer(Modifier.height(8.dp))
            LegacyPrimaryButton("Mover a...", palette, Modifier.fillMaxWidth(), onMove)
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                LegacySecondaryButton("Contraer todas", palette, Modifier.weight(1f), onCollapse)
                LegacySecondaryButton("Expandir todas", palette, Modifier.weight(1f), onExpand)
            }
        }
    }
}

@Composable
private fun ReorganizeFolderList(
    folders: List<Inspection>,
    selected: Set<String>,
    palette: LegacyPalette,
    onToggle: (String) -> Unit
) {
    folders.forEach { folder ->
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onToggle(folder.id) }
                .padding(vertical = 7.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = folder.id in selected, onCheckedChange = { _ -> onToggle(folder.id) })
            Spacer(Modifier.width(5.dp))
            Icon(painterResource(R.drawable.ic_folder_original), null, tint = FolderAmber, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(folder.name, color = palette.text, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text("${folder.children.size} subcarpetas · ${InspectionTree.countPhotos(folder)} fotos", color = palette.muted, fontSize = 11.sp)
            }
        }
        Divider(color = palette.border.copy(alpha = .55f))
    }
}

@Composable
private fun FolderGallery(
    folders: List<Inspection>,
    palette: LegacyPalette,
    compact: Boolean,
    showOt: Boolean,
    organize: Boolean,
    selected: Set<String>,
    onSelect: (String) -> Unit,
    onOpen: (String) -> Unit,
    onMenu: (Inspection) -> Unit,
    onToggleCollapsed: (String) -> Unit
) {
    val cols = if (compact) 2 else 3
    folders.chunked(cols).forEach { rowItems ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            rowItems.forEach { folder ->
                FolderCard(
                    folder,
                    palette,
                    showOt,
                    organize,
                    folder.id in selected,
                    Modifier.weight(1f),
                    onSelect,
                    onOpen,
                    onMenu,
                    onToggleCollapsed
                )
            }
            repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(9.dp))
    }
}

@Composable
private fun FolderList(
    folders: List<Inspection>,
    palette: LegacyPalette,
    showOt: Boolean,
    organize: Boolean,
    selected: Set<String>,
    onSelect: (String) -> Unit,
    onOpen: (String) -> Unit,
    onMenu: (Inspection) -> Unit,
    onToggleCollapsed: (String) -> Unit
) {
    folders.forEach { folder ->
        Card(
            modifier = Modifier.fillMaxWidth().clickable { if (organize) onSelect(folder.id) else onOpen(folder.id) },
            colors = CardDefaults.cardColors(containerColor = if (folder.id in selected) palette.blue.copy(alpha = .18f) else palette.surface),
            border = BorderStroke(1.dp, if (folder.id in selected) palette.blue else palette.border),
            shape = RoundedCornerShape(13.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (organize) Checkbox(checked = folder.id in selected, onCheckedChange = { _ -> onSelect(folder.id) })
                Icon(painterResource(R.drawable.ic_folder_original), null, tint = FolderAmber, modifier = Modifier.size(29.dp))
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(folder.name, color = palette.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!folder.collapsed) {
                        Text("${folder.children.size} subcarpetas · ${InspectionTree.countPhotos(folder)} fotos", color = palette.muted, fontSize = 11.sp)
                        if (showOt && folder.ot.isNotBlank()) Text("OT ${folder.ot}", color = palette.muted, fontSize = 10.sp)
                    }
                }
                Text(if (folder.collapsed) "▸" else "▾", color = palette.cyan, fontSize = 17.sp, modifier = Modifier.clickable { onToggleCollapsed(folder.id) }.padding(7.dp))
                Text("›", color = palette.muted, fontSize = 28.sp, modifier = Modifier.padding(horizontal = 2.dp))
                LegacyMoreButton(palette) { onMenu(folder) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun FolderCard(
    folder: Inspection,
    palette: LegacyPalette,
    showOt: Boolean,
    organize: Boolean,
    selected: Boolean,
    modifier: Modifier,
    onSelect: (String) -> Unit,
    onOpen: (String) -> Unit,
    onMenu: (Inspection) -> Unit,
    onToggleCollapsed: (String) -> Unit
) {
    Card(
        modifier = modifier.height(if (folder.collapsed) 112.dp else 164.dp).clickable { if (organize) onSelect(folder.id) else onOpen(folder.id) },
        colors = CardDefaults.cardColors(containerColor = if (selected) palette.blue.copy(alpha = .18f) else palette.surface),
        border = BorderStroke(1.dp, if (selected) palette.blue else palette.border),
        shape = RoundedCornerShape(15.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (organize) Checkbox(selected, { _ -> onSelect(folder.id) })
                Icon(painterResource(R.drawable.ic_folder_original), null, tint = FolderAmber, modifier = Modifier.size(31.dp))
                Spacer(Modifier.weight(1f))
                Text(if (folder.collapsed) "▸" else "▾", color = palette.cyan, fontSize = 18.sp, modifier = Modifier.clickable { onToggleCollapsed(folder.id) }.padding(5.dp))
                LegacyMoreButton(palette) { onMenu(folder) }
            }
            Spacer(Modifier.height(4.dp))
            Text(folder.name, color = palette.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!folder.collapsed) {
                Spacer(Modifier.height(5.dp))
                Text("${folder.children.size} subcarpetas", color = palette.muted, fontSize = 11.sp)
                Text("${InspectionTree.countPhotos(folder)} fotos", color = palette.muted, fontSize = 11.sp)
                if (showOt && folder.ot.isNotBlank()) Text("OT ${folder.ot}", color = palette.muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun PhotoGallery(
    photos: List<PhotoItem>,
    palette: LegacyPalette,
    compact: Boolean,
    onOpen: (PhotoItem) -> Unit,
    onMenu: (PhotoItem) -> Unit,
    mainPhotoPath: String = ""
) {
    val cols = if (compact) 2 else 4
    photos.chunked(cols).forEach { rowItems ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowItems.forEach { photo -> PhotoCard(photo, palette, Modifier.weight(1f), onOpen, onMenu, isMain = photo.path == mainPhotoPath) }
            repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PhotoList(
    photos: List<PhotoItem>,
    palette: LegacyPalette,
    onOpen: (PhotoItem) -> Unit,
    onMenu: (PhotoItem) -> Unit
) {
    photos.forEach { photo ->
        Card(
            modifier = Modifier.fillMaxWidth().clickable { onOpen(photo) },
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            border = BorderStroke(1.dp, palette.border),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                val image = rememberHierarchyImage(photo.path)
                Box(Modifier.size(74.dp, 56.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                    if (image != null) Image(image, photo.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(photo.name, color = palette.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(photoFileMeta(photo.path), color = palette.muted, fontSize = 10.sp)
                    if (photo.observation.isNotBlank()) Text(photo.observation, color = palette.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                LegacyMoreButton(palette) { onMenu(photo) }
            }
        }
        Spacer(Modifier.height(7.dp))
    }
}

@Composable
private fun PhotoCard(
    photo: PhotoItem,
    palette: LegacyPalette,
    modifier: Modifier,
    onOpen: (PhotoItem) -> Unit,
    onMenu: (PhotoItem) -> Unit,
    isMain: Boolean = false
) {
    Card(
        modifier = modifier.height(168.dp).clickable { onOpen(photo) },
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(if (isMain) 2.dp else 1.dp, if (isMain) palette.blue else palette.border),
        shape = RoundedCornerShape(13.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(6.dp)) {
            Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(9.dp)).background(Color.Black)) {
                val image = rememberHierarchyImage(photo.path)
                if (image != null) Image(image, photo.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                if (isMain) Text("PRINCIPAL", color = Color.White, fontSize = 9.sp,
                    modifier = Modifier.align(Alignment.BottomStart).background(palette.blue, RoundedCornerShape(6.dp)).padding(4.dp))
                Box(Modifier.align(Alignment.TopEnd).background(palette.surface.copy(alpha = .88f), RoundedCornerShape(8.dp))) {
                    LegacyMoreButton(palette) { onMenu(photo) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(photo.name, color = palette.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(photoFileMeta(photo.path), color = palette.muted, fontSize = 9.sp, maxLines = 1)
        }
    }
}

@Composable
private fun MoveFoldersDialog(
    allFolders: List<Inspection>,
    selectedIds: Set<String>,
    palette: LegacyPalette,
    onDismiss: () -> Unit,
    onMove: (String?) -> Unit
) {
    val refs = remember(allFolders, selectedIds) {
        InspectionTree.flatten(allFolders).filter { ref ->
            ref.id !in selectedIds && selectedIds.none { sid -> InspectionTree.find(allFolders, sid)?.let { InspectionTree.contains(it, ref.id) } == true }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { Text("Mover carpetas", color = palette.text) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text("Raíz", color = palette.text, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable { onMove(null) }.padding(vertical = 12.dp))
                refs.forEach { ref ->
                    Text(ref.path, color = palette.text, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().clickable { onMove(ref.id) }.padding(vertical = 11.dp))
                    Divider(color = palette.border.copy(alpha = .55f))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = palette.blue) } }
    )
}

@Composable
fun ReportQuickAction(
    iconRes: Int,
    label: String,
    active: Boolean,
    palette: LegacyPalette,
    onClick: () -> Unit
) {
    val tint = if (active) palette.green else palette.blue
    Surface(
        modifier = Modifier.size(42.dp).clickable(onClick = onClick),
        color = if (active) palette.green.copy(alpha = .12f) else palette.surface,
        contentColor = tint,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (active) palette.green.copy(alpha = .75f) else palette.border)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(iconRes), contentDescription = label, tint = tint, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
fun FolderConfigurationDialog(
    folder: Inspection,
    palette: LegacyPalette,
    onDismiss: () -> Unit,
    onSave: (Boolean, Boolean, FolderViewMode) -> Unit
) {
    var allowPhotos by remember(folder.id) { mutableStateOf(folder.allowPhotos) }
    var allowSubfolders by remember(folder.id) { mutableStateOf(folder.allowSubfolders) }
    var viewMode by remember(folder.id) { mutableStateOf(folder.viewMode) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { Text("Configurar ${folder.name}", color = palette.text) },
        text = {
            Column {
                SettingSwitchRow("Permitir fotos", "Tomar o importar fotografías en este nivel.", allowPhotos, palette) { allowPhotos = it }
                Spacer(Modifier.height(8.dp))
                SettingSwitchRow("Permitir subcarpetas", "Crear otro nivel dentro de esta carpeta.", allowSubfolders, palette) { allowSubfolders = it }
                Spacer(Modifier.height(12.dp))
                Text("Vista del contenido", color = palette.text, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(7.dp))
                LegacySegmentedChoice(listOf("Galería", "Lista"), if (viewMode == FolderViewMode.GALLERY) "Galería" else "Lista", palette) {
                    viewMode = if (it == "Galería") FolderViewMode.GALLERY else FolderViewMode.LIST
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onSave(allowPhotos, allowSubfolders, viewMode) }) { Text("Guardar", color = palette.blue) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = palette.muted) } }
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    palette: LegacyPalette,
    onChecked: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = palette.muted, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun EmptyFolderMessage(palette: LegacyPalette, text: String) {
    Box(
        Modifier.fillMaxWidth().height(130.dp).background(palette.surface, RoundedCornerShape(15.dp)).border(1.dp, palette.border, RoundedCornerShape(15.dp)),
        contentAlignment = Alignment.Center
    ) { Text(text, color = palette.muted) }
}

private fun containsName(folder: Inspection, query: String): Boolean =
    folder.name.contains(query, true) || folder.children.any { containsName(it, query) }

private fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id

private fun photoFileMeta(path: String): String {
    val file = File(path)
    if (!file.exists()) return "Archivo no disponible"
    val date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(file.lastModified()))
    val mb = file.length() / (1024.0 * 1024.0)
    return "$date · %.1f MB".format(mb)
}

@Composable
private fun rememberHierarchyImage(path: String): androidx.compose.ui.graphics.ImageBitmap? {
    val state = produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(path)
                if (!file.exists() || file.length() == 0L) return@runCatching null
                ImageOrientationUtils.decodeOrientedBitmap(file, 1200)?.asImageBitmap()
            }.getOrNull()
        }
    }
    return state.value
}

private val folderSortOptions = listOf("A-Z", "Z-A", "Fecha de creación", "Fecha de modificación")

private fun orderedFolders(source: List<Inspection>, mode: String): List<Inspection> = when (mode) {
    "A-Z" -> source.sortedBy { it.name.lowercase() }
    "Z-A" -> source.sortedByDescending { it.name.lowercase() }
    "Fecha de modificación" -> source.sortedByDescending { it.modifiedAtMillis }
    else -> source.sortedByDescending { it.createdAtMillis }
}

private fun orderedPhotos(source: List<PhotoItem>, mode: String): List<PhotoItem> = when (mode) {
    "A-Z" -> source.sortedBy { it.name.lowercase() }
    "Z-A" -> source.sortedByDescending { it.name.lowercase() }
    "Fecha de modificación" -> source.sortedByDescending { it.modifiedAtMillis }
    else -> source.sortedByDescending { it.createdAtMillis }
}
