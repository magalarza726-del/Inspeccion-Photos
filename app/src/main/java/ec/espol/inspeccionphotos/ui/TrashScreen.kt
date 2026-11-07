package ec.espol.inspeccionphotos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import ec.espol.inspeccionphotos.TrashEntry
import ec.espol.inspeccionphotos.TrashType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TrashScreen(
    palette: LegacyPalette,
    entries: List<TrashEntry>,
    onRestore: (List<TrashEntry>) -> Unit,
    onDeleteForever: (List<TrashEntry>) -> Unit
) {
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDeletion by remember { mutableStateOf(false) }
    val chosen = entries.filter { it.id in selected }
    val formatter = remember { SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()) }
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Papelera", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.weight(1f))
            Text("${entries.size} elemento(s)", color = palette.muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text("Los archivos se conservan hasta que los elimines definitivamente.", color = palette.muted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        if (chosen.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onRestore(chosen); selected = emptySet() }) { Text("Restaurar (${chosen.size})") }
                OutlinedButton(onClick = { confirmDeletion = true }) { Text("Eliminar definitivamente") }
            }
            Spacer(Modifier.height(10.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            val groups = listOf(
                TrashType.PHOTO to "Fotos", TrashType.FOLDER to "Carpetas",
                TrashType.FORM to "Formularios", TrashType.TEMPLATE to "Formatos de formularios",
                TrashType.PHOTO_TEMPLATE to "Formatos de fotos"
            )
            groups.forEach { (type, title) ->
                val group = entries.filter { it.type == type }.sortedByDescending { it.deletedAt }
                item(key = "head-$title") {
                    Spacer(Modifier.height(7.dp))
                    Text("$title (${group.size})", color = palette.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                items(group, key = { it.id }) { entry ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selected = if (entry.id in selected) selected - entry.id else selected + entry.id
                        },
                        color = palette.surface, border = BorderStroke(1.dp, palette.border),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = entry.id in selected, onCheckedChange = {
                                selected = if (it) selected + entry.id else selected - entry.id
                            })
                            Column(Modifier.weight(1f)) {
                                Text(entry.name, color = palette.text, fontWeight = FontWeight.SemiBold)
                                Text(formatter.format(Date(entry.deletedAt)), color = palette.muted, fontSize = 11.sp)
                            }
                            TextButton(onClick = { onRestore(listOf(entry)); selected = selected - entry.id }) { Text("Restaurar") }
                        }
                    }
                }
            }
        }
    }
    if (confirmDeletion) {
        AlertDialog(
            onDismissRequest = { confirmDeletion = false },
            title = { Text("Eliminar definitivamente") },
            text = { Text("Se eliminarán ${chosen.size} elemento(s) y sus archivos. Esta acción no se puede deshacer.") },
            confirmButton = { TextButton(onClick = {
                onDeleteForever(chosen)
                selected = emptySet()
                confirmDeletion = false
            }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { confirmDeletion = false }) { Text("Cancelar") } }
        )
    }
}
