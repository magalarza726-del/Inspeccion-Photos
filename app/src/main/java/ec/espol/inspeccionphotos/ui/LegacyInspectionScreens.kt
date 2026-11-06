package ec.espol.inspeccionphotos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ec.espol.inspeccionphotos.CodeItem
import ec.espol.inspeccionphotos.R
import ec.espol.inspeccionphotos.Inspection
import ec.espol.inspeccionphotos.ImageOrientationUtils
import ec.espol.inspeccionphotos.PhotoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private enum class FolderSearchMode { FOLDER, CODE }

@Composable
fun LegacyFoldersScreen(
    inspections: List<Inspection>,
    palette: LegacyPalette,
    compact: Boolean,
    showOt: Boolean = true,
    onOpen: (Inspection) -> Unit,
    onNewFolder: () -> Unit,
    onEditOt: (Inspection) -> Unit,
    onRename: (Inspection) -> Unit,
    onClone: (Inspection) -> Unit,
    onExport: (Inspection) -> Unit,
    onDelete: (Inspection) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(FolderSearchMode.FOLDER) }
    var menuFor by remember { mutableStateOf<Inspection?>(null) }

    val filtered = remember(inspections, search, mode) {
        val q = search.trim()
        if (q.isBlank()) inspections
        else inspections.filter { inspection ->
            when (mode) {
                FolderSearchMode.FOLDER -> inspection.name.contains(q, ignoreCase = true)
                FolderSearchMode.CODE -> inspection.codes.any { it.name.contains(q, ignoreCase = true) }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (compact) 12.dp else 24.dp, vertical = 14.dp)
    ) {
        LegacySubtitle("Gestiona tus carpetas de inspección de campo.", palette)
        Spacer(Modifier.height(18.dp))

        if (compact) {
            LegacySearchField(search, { search = it }, palette, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Buscar por:", color = palette.text, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                LegacySegmentedChoice(
                    listOf("Carpeta", "Código"),
                    if (mode == FolderSearchMode.FOLDER) "Carpeta" else "Código",
                    palette
                ) { mode = if (it == "Carpeta") FolderSearchMode.FOLDER else FolderSearchMode.CODE }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegacySearchField(search, { search = it }, palette, Modifier.widthIn(min = 360.dp, max = 560.dp).weight(1f, fill = false))
                Spacer(Modifier.width(20.dp))
                Text("Buscar por:", color = palette.text, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(14.dp))
                LegacySegmentedChoice(
                    listOf("Carpeta", "Código"),
                    if (mode == FolderSearchMode.FOLDER) "Carpeta" else "Código",
                    palette
                ) { mode = if (it == "Carpeta") FolderSearchMode.FOLDER else FolderSearchMode.CODE }
            }
        }

        Spacer(Modifier.height(20.dp))
        LegacyTitle("Carpetas registradas (${filtered.size})", palette, 20)
        Spacer(Modifier.height(14.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth().height(238.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(end = 20.dp)
        ) {
            items(filtered, key = { it.id }) { inspection ->
                LegacyFolderCard(
                    inspection = inspection,
                    palette = palette,
                    showOt = showOt,
                    onOpen = { onOpen(inspection) },
                    onMenu = { menuFor = inspection }
                )
            }
            item {
                LegacyNewFolderCard(palette, onNewFolder)
            }
        }
    }

    menuFor?.let { inspection ->
        LegacyMenuDialog(
            title = inspection.name,
            options = buildList {
                add("Abrir carpeta" to { onOpen(inspection) })
                if (showOt) add("Agregar/editar OT" to { onEditOt(inspection) })
                add("Renombrar carpeta" to { onRename(inspection) })
                add("Exportar y compartir .ZIP" to { onExport(inspection) })
                add("Eliminar carpeta" to { onDelete(inspection) })
            },
            onDismiss = { menuFor = null }
        )
    }
}

@Composable
private fun LegacySearchField(value: String, onValueChange: (String) -> Unit, palette: LegacyPalette, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = 56.dp),
        singleLine = true,
        placeholder = { Text("Buscar carpetas o códigos...", color = palette.muted) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search_original), contentDescription = "Buscar", tint = palette.muted) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = palette.text,
            unfocusedTextColor = palette.text,
            focusedBorderColor = palette.blue,
            unfocusedBorderColor = palette.border,
            cursorColor = palette.cyan,
            focusedContainerColor = palette.surface,
            unfocusedContainerColor = palette.surface
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun LegacyFolderCard(inspection: Inspection, palette: LegacyPalette, showOt: Boolean, onOpen: () -> Unit, onMenu: () -> Unit) {
    Card(
        modifier = Modifier.width(305.dp).fillMaxHeight().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(1.dp, palette.border),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(64.dp).background(palette.blue.copy(alpha = .16f), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painterResource(R.drawable.ic_folder_original), contentDescription = null, tint = palette.blue, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.weight(1f))
                LegacyMoreButton(palette, onMenu)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                inspection.name,
                color = palette.text,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Text("Creada: ${inspection.created}", color = palette.muted, fontSize = 14.sp)
            Text("${inspection.codes.size} código(s)", color = palette.muted, fontSize = 14.sp)
            if (showOt && inspection.ot.isNotBlank()) Text("OT: ${inspection.ot}", color = palette.muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun LegacyNewFolderCard(palette: LegacyPalette, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(230.dp).fillMaxHeight().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = palette.surface.copy(alpha = .72f)),
        border = BorderStroke(1.dp, palette.border),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(painterResource(R.drawable.ic_plus_original), contentDescription = null, tint = palette.blue, modifier = Modifier.size(50.dp))
            Text("Nueva carpeta", color = palette.blue, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Crear una nueva carpeta de inspección", color = palette.muted, fontSize = 13.sp)
        }
    }
}

@Composable
fun LegacyInspectionDetailScreen(
    inspection: Inspection,
    palette: LegacyPalette,
    compact: Boolean,
    showOt: Boolean = true,
    onBack: () -> Unit,
    onSaveOt: (String) -> Unit,
    onAddCode: () -> Unit,
    onEditCodeObservation: (Int) -> Unit,
    onRenameCode: (Int) -> Unit,
    onDeleteCode: (Int) -> Unit,
    onTakePhoto: (Int) -> Unit,
    onPickPhoto: (Int) -> Unit,
    onRenamePhoto: (Int, Int) -> Unit,
    onEditPhotoObservation: (Int, Int) -> Unit,
    onDownloadPhoto: (Int, Int) -> Unit,
    onDeletePhoto: (Int, Int) -> Unit,
    onExport: () -> Unit
) {
    var ot by remember(inspection.id, inspection.ot) { mutableStateOf(inspection.ot) }
    var codeMenu by remember { mutableStateOf<Int?>(null) }
    var addPhotoMenu by remember { mutableStateOf<Int?>(null) }
    var photoMenu by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var review by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (compact) 12.dp else 22.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = palette.muted, fontSize = 40.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 10.dp))
            Column {
                LegacyTitle(inspection.name, palette, if (compact) 21 else 25)
                LegacySubtitle("Carpeta de inspección  •  Creada el ${inspection.created}  •  ${inspection.codes.size} código(s)", palette)
            }
        }
        Spacer(Modifier.height(14.dp))

        if (showOt) {
            if (compact) {
                Column(Modifier.fillMaxWidth().background(palette.surface, RoundedCornerShape(14.dp)).padding(12.dp)) {
                    Text("OT:", color = palette.text, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    LegacyOtField(ot, { ot = it }, palette, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    LegacyPrimaryButton("Guardar OT", palette, Modifier.fillMaxWidth()) { onSaveOt(ot) }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().background(palette.surface, RoundedCornerShape(14.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("OT:", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.width(12.dp))
                    LegacyOtField(ot, { ot = it }, palette, Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    LegacyPrimaryButton("Guardar OT", palette) { onSaveOt(ot) }
                }
            }

        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LegacyPrimaryButton("+ Código", palette, onClick = onAddCode)
            LegacySecondaryButton("Exportar y compartir .ZIP", palette, onClick = onExport)
            LegacyHintBox("Desliza para ver más fotos.", palette, Modifier.width(330.dp))
        }
        Spacer(Modifier.height(16.dp))

        if (inspection.codes.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().height(160.dp).background(palette.surface, RoundedCornerShape(16.dp)).border(1.dp, palette.border, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("No hay códigos registrados. Presiona + Código para iniciar.", color = palette.muted, fontSize = 15.sp)
            }
        } else {
            inspection.codes.forEachIndexed { codeIndex, code ->
                LegacyCodeRow(
                    code = code,
                    codeIndex = codeIndex,
                    palette = palette,
                    onCodeMenu = { codeMenu = codeIndex },
                    onAddPhoto = { addPhotoMenu = codeIndex },
                    onPhotoMenu = { photoIndex -> photoMenu = codeIndex to photoIndex },
                    onOpenPhoto = { photoIndex -> review = codeIndex to photoIndex }
                )
                Spacer(Modifier.height(14.dp))
            }
        }
    }

    codeMenu?.let { idx ->
        val code = inspection.codes.getOrNull(idx)
        if (code != null) {
            LegacyMenuDialog(
                title = "Código ${code.name}",
                options = listOf(
                    "Observación general" to { onEditCodeObservation(idx) },
                    "Renombrar código" to { onRenameCode(idx) },
                    "Eliminar código" to { onDeleteCode(idx) }
                ),
                onDismiss = { codeMenu = null }
            )
        }
    }

    addPhotoMenu?.let { idx ->
        LegacyMenuDialog(
            title = "Agregar foto",
            options = listOf(
                "Tomar foto" to { onTakePhoto(idx) },
                "Seleccionar imagen" to { onPickPhoto(idx) }
            ),
            onDismiss = { addPhotoMenu = null }
        )
    }

    photoMenu?.let { (codeIndex, photoIndex) ->
        val photo = inspection.codes.getOrNull(codeIndex)?.photos?.getOrNull(photoIndex)
        if (photo != null) {
            LegacyMenuDialog(
                title = photo.name,
                options = listOf(
                    "Mostrar imagen" to { review = codeIndex to photoIndex },
                    "Renombrar foto" to { onRenamePhoto(codeIndex, photoIndex) },
                    "Observación de la foto" to { onEditPhotoObservation(codeIndex, photoIndex) },
                    "Descargar foto" to { onDownloadPhoto(codeIndex, photoIndex) },
                    "Eliminar foto" to { onDeletePhoto(codeIndex, photoIndex) }
                ),
                onDismiss = { photoMenu = null }
            )
        }
    }

    review?.let { (codeIndex, photoIndex) ->
        val code = inspection.codes.getOrNull(codeIndex)
        if (code != null) {
            LegacyImageReviewDialog(
                codeName = code.name,
                photos = code.photos,
                initialIndex = photoIndex,
                palette = palette,
                onDismiss = { review = null }
            )
        }
    }
}

@Composable
private fun LegacyOtField(value: String, onValueChange: (String) -> Unit, palette: LegacyPalette, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = palette.text,
            unfocusedTextColor = palette.text,
            focusedBorderColor = palette.blue,
            unfocusedBorderColor = palette.border,
            focusedContainerColor = palette.surface2,
            unfocusedContainerColor = palette.surface2,
            cursorColor = palette.cyan
        ),
        shape = RoundedCornerShape(10.dp)
    )
}

@Composable
private fun LegacyCodeRow(
    code: CodeItem,
    codeIndex: Int,
    palette: LegacyPalette,
    onCodeMenu: () -> Unit,
    onAddPhoto: () -> Unit,
    onPhotoMenu: (Int) -> Unit,
    onOpenPhoto: (Int) -> Unit
) {
    val accent = listOf(palette.blue, palette.violet, palette.cyan, palette.green)[codeIndex % 4]
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.width(250.dp).height(218.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            border = BorderStroke(1.dp, accent),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row {
                    Box(
                        Modifier.background(accent.copy(alpha = .11f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(code.name, color = accent, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.weight(1f))
                    LegacyMoreButton(palette, onCodeMenu)
                }
                Spacer(Modifier.height(14.dp))
                Text("Código ${code.name}", color = palette.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    code.generalObs.ifBlank { "Sin observación general" },
                    color = palette.muted,
                    fontSize = 14.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.weight(1f))
                Text("${code.photos.size} foto(s)", color = palette.muted, fontSize = 12.sp)
            }
        }

        Card(
            modifier = Modifier.width(165.dp).height(218.dp).clickable(onClick = onAddPhoto),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            border = BorderStroke(1.dp, palette.border),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(painterResource(R.drawable.ic_plus_original), contentDescription = null, tint = palette.blue, modifier = Modifier.size(50.dp))
                Text("Agregar foto", color = palette.muted, fontSize = 16.sp)
            }
        }

        code.photos.forEachIndexed { photoIndex, photo ->
            LegacyPhotoCard(photo, palette, { onPhotoMenu(photoIndex) }, { onOpenPhoto(photoIndex) })
        }
    }
}

@Composable
private fun LegacyPhotoCard(photo: PhotoItem, palette: LegacyPalette, onMenu: () -> Unit, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.width(210.dp).height(218.dp).clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(1.dp, palette.border),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Box(Modifier.fillMaxWidth().height(105.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black)) {
                val image = rememberLocalImage(photo.path)
                if (image != null) {
                    Image(image, contentDescription = photo.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Text("Imagen", color = Color.White.copy(alpha = .5f), modifier = Modifier.align(Alignment.Center))
                }
                Box(Modifier.align(Alignment.TopEnd).background(palette.surface2.copy(alpha = .92f), RoundedCornerShape(8.dp))) {
                    LegacyMoreButton(palette, onMenu)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(photo.name, color = palette.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (photo.observation.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(photo.observation, color = palette.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun rememberLocalImage(path: String): androidx.compose.ui.graphics.ImageBitmap? {
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

@Composable
private fun LegacyImageReviewDialog(
    codeName: String,
    photos: List<PhotoItem>,
    initialIndex: Int,
    palette: LegacyPalette,
    onDismiss: () -> Unit
) {
    if (photos.isEmpty()) return
    var index by remember(initialIndex, photos.size) { mutableIntStateOf(initialIndex.coerceIn(0, photos.lastIndex)) }
    var scale by remember(index) { mutableFloatStateOf(1f) }
    var offsetX by remember(index) { mutableFloatStateOf(0f) }
    var offsetY by remember(index) { mutableFloatStateOf(0f) }
    val transformState = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offsetX += pan.x
        offsetY += pan.y
    }
    val current = photos[index]

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(.92f),
            color = palette.bg,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, palette.border)
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Código $codeName", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(current.name, color = palette.muted, fontSize = 13.sp)
                    }
                    Text("Cerrar", color = palette.blue, modifier = Modifier.clickable(onClick = onDismiss).padding(10.dp))
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.weight(1f).fillMaxWidth().background(Color.Black, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val image = rememberLocalImage(current.path)
                    if (image != null) {
                        Image(
                            image,
                            contentDescription = current.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                )
                                .transformable(transformState)
                        )
                    } else Text("No se pudo cargar la imagen", color = Color.White.copy(alpha = .7f))
                    if (index > 0) {
                        Text("‹", color = Color.White, fontSize = 48.sp, modifier = Modifier.align(Alignment.CenterStart).clickable { index-- }.padding(14.dp))
                    }
                    if (index < photos.lastIndex) {
                        Text("›", color = Color.White, fontSize = 48.sp, modifier = Modifier.align(Alignment.CenterEnd).clickable { index++ }.padding(14.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(current.observation.ifBlank { "Sin observación" }, color = palette.muted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(photos.indices.toList()) { i ->
                        val p = photos[i]
                        Box(
                            Modifier
                                .size(72.dp, 54.dp)
                                .border(2.dp, if (i == index) palette.blue else palette.border, RoundedCornerShape(6.dp))
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { index = i }
                                .background(Color.Black)
                        ) {
                            rememberLocalImage(p.path)?.let { img ->
                                Image(img, contentDescription = p.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                        }
                    }
                }
            }
        }
    }
}
