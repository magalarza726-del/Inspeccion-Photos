package ec.espol.inspeccionphotos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import ec.espol.inspeccionphotos.*

private data class SearchHit(val key: String, val title: String, val subtitle: String, val folderId: String? = null, val formId: String? = null)

@Composable
fun SearchScreen(
    palette: LegacyPalette,
    folders: List<Inspection>,
    submissions: List<FormSubmission>,
    templates: List<FormTemplate>,
    onOpenFolder: (String) -> Unit,
    onOpenForm: (String) -> Unit,
    onOpenFormats: () -> Unit
) {
    var mode by remember { mutableStateOf("Cámara") }
    var query by remember { mutableStateOf("") }
    val hits = remember(mode, query, folders, submissions, templates) {
        val q = query.trim()
        if (q.isBlank()) emptyList() else if (mode == "Cámara") buildList {
            fun walk(folders: List<Inspection>, path: String) {
                folders.forEach { folder ->
                    val here = if (path.isEmpty()) folder.name else "$path / ${folder.name}"
                    if (here.contains(q, true) || folder.generalObs.contains(q, true)) {
                        add(SearchHit("folder-${folder.id}", folder.name, "Carpeta · $here", folderId = folder.id))
                    }
                    folder.photos.forEach { photo ->
                        if (photo.name.contains(q, true) || photo.observation.contains(q, true)) {
                            add(SearchHit("photo-${photo.path}", photo.name, "Foto · $here", folderId = folder.id))
                        }
                    }
                    walk(folder.children, here)
                }
            }
            walk(folders, "")
        } else buildList {
            submissions.forEach { submission ->
                if (submission.template.name.contains(q, true) || submission.cells.any { row -> row.any { it.contains(q, true) } } ||
                    submission.sectionValues.values.any { it.contains(q, true) }) {
                    add(SearchHit("form-${submission.id}", submission.template.name,
                        if (submission.completed) "Formulario · Completado" else "Formulario · Borrador", formId = submission.id))
                }
            }
            templates.forEach { template ->
                if (template.name.contains(q, true) || template.title.contains(q, true)) {
                    add(SearchHit("template-${template.id}", template.name, "Formato disponible"))
                }
            }
        }.take(80)
    }
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        LegacySegmentedChoice(listOf("Cámara", "Documentos"), mode, palette) { mode = it }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Buscar en ${mode.lowercase()}") },
            placeholder = { Text("Escribe para ver sugerencias...") },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = palette.text, unfocusedTextColor = palette.text),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(if (query.isBlank()) "Escribe un nombre o palabra clave" else "${hits.size} coincidencias", color = palette.muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(hits, key = { it.key }) { hit ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        when {
                            hit.folderId != null -> onOpenFolder(hit.folderId)
                            hit.formId != null -> onOpenForm(hit.formId)
                            else -> onOpenFormats()
                        }
                    },
                    color = palette.surface,
                    border = BorderStroke(1.dp, palette.border),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(13.dp)) {
                        Text(hit.title, color = palette.text, fontWeight = FontWeight.SemiBold)
                        Text(hit.subtitle, color = palette.muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
