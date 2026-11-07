package ec.espol.inspeccionphotos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import ec.espol.inspeccionphotos.Inspection
import ec.espol.inspeccionphotos.InspectionTree

/**
 * Selector de destino de tipo administrador de archivos:
 * navegar abre carpetas; "Elegir esta carpeta" confirma el destino actual.
 */
@Composable
fun DestinationFolderPicker(
    roots: List<Inspection>,
    palette: LegacyPalette,
    title: String,
    excludedId: String?,
    includeRoot: Boolean,
    onDismiss: () -> Unit,
    onChoose: (String?) -> Unit
) {
    var currentId by remember { mutableStateOf<String?>(null) }
    val current = currentId?.let { InspectionTree.find(roots, it) }
    val path = current?.let { InspectionTree.ancestors(roots, it.id) + it }.orEmpty()
    val allShown = current?.children ?: roots
    val available = allShown.filter { candidate ->
        excludedId == null || (candidate.id != excludedId &&
            InspectionTree.find(roots, excludedId)?.let { !InspectionTree.contains(it, candidate.id) } != false)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { Text(title, color = palette.text) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 470.dp)) {
                Text(
                    if (path.isEmpty()) "Raíz" else "Raíz / " + path.joinToString(" / ") { it.name },
                    color = palette.blue, fontWeight = FontWeight.SemiBold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                if (currentId != null) {
                    Text("‹ Carpeta anterior", color = palette.blue,
                        modifier = Modifier.fillMaxWidth().clickable {
                            currentId = currentId?.let { InspectionTree.parentId(roots, it) }
                        }.padding(vertical = 13.dp))
                }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    available.forEach { candidate ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).clickable { currentId = candidate.id },
                            color = palette.surface2, border = BorderStroke(1.dp, palette.border),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("📁", fontSize = 21.sp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(candidate.name, color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${candidate.children.size} subcarpetas", color = palette.muted, fontSize = 10.sp)
                                }
                                Text("›", color = palette.muted)
                            }
                        }
                    }
                    if (available.isEmpty()) Text("No hay más subcarpetas.", color = palette.muted, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = (includeRoot || currentId != null) && (currentId == null ||
                    (currentId != excludedId && InspectionTree.find(roots, currentId)?.allowSubfolders != false)),
                onClick = { onChoose(currentId) }
            ) { Text("Elegir esta carpeta") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun CameraCaptureSettingsDialog(
    palette: LegacyPalette,
    addPhotoAction: AddPhotoAction,
    editorPromptMode: EditorPromptMode,
    sessionMode: CameraSessionMode,
    askPhotoConfirmation: Boolean,
    onDismiss: () -> Unit,
    onAddPhotoActionChange: (AddPhotoAction) -> Unit,
    onEditorPromptModeChange: (EditorPromptMode) -> Unit,
    onSessionModeChange: (CameraSessionMode) -> Unit,
    onConfirmationChange: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = palette.surface,
        title = { Text("Configuración de cámara", color = palette.text) },
        text = {
            Column(Modifier.heightIn(max = 490.dp).verticalScroll(rememberScrollState())) {
                Text("Al tocar el icono de cámara", color = palette.text, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                listOf(
                    AddPhotoAction.CAMERA_DIRECT to "Cámara directa",
                    AddPhotoAction.ASK to "Panel de cámara",
                    AddPhotoAction.ATTACH to "Adjuntar archivos"
                ).forEach { (action, title) ->
                    Row(Modifier.fillMaxWidth().clickable { onAddPhotoActionChange(action) },
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = addPhotoAction == action, onClick = { onAddPhotoActionChange(action) })
                        Text(title, color = palette.text)
                    }
                }
                Divider(color = palette.border)
                Spacer(Modifier.height(7.dp))
                Text("Después de tomar una foto", color = palette.text, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Preguntar si deseo editar", color = palette.text, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    Switch(checked = editorPromptMode == EditorPromptMode.ASK_AFTER_CAPTURE,
                        onCheckedChange = { onEditorPromptModeChange(if (it) EditorPromptMode.ASK_AFTER_CAPTURE else EditorPromptMode.NEVER_ASK) })
                }
                Divider(color = palette.border)
                Spacer(Modifier.height(7.dp))
                Text("Sesión fotográfica", color = palette.text, fontWeight = FontWeight.SemiBold)
                listOf(CameraSessionMode.ONE_BY_ONE to "Una por una",
                    CameraSessionMode.BATCH to "Sesión continua").forEach { (mode, label) ->
                    Row(Modifier.fillMaxWidth().clickable { onSessionModeChange(mode) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = sessionMode == mode, onClick = { onSessionModeChange(mode) })
                        Text(label, color = palette.text)
                    }
                }
                Divider(color = palette.border)
                Spacer(Modifier.height(7.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Revisar foto antes de continuar", color = palette.text, fontSize = 13.sp)
                        Text("Permite repetir la captura si no quedó bien.", color = palette.muted, fontSize = 11.sp)
                    }
                    Switch(checked = askPhotoConfirmation, onCheckedChange = onConfirmationChange)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } }
    )
}
