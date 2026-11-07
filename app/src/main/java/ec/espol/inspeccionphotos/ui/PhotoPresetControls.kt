package ec.espol.inspeccionphotos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import ec.espol.inspeccionphotos.PhotoReportPreset

@Composable
fun PhotoPresetControls(
    palette: LegacyPalette,
    presets: List<PhotoReportPreset>,
    activeId: String,
    defaultReport: String,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: () -> Unit,
    onTrash: () -> Unit,
    onDefault: (String) -> Unit
) {
    var choosePreset by remember { mutableStateOf(false) }
    var chooseDefault by remember { mutableStateOf(false) }
    val current = presets.firstOrNull { it.id == activeId }
    Surface(color = palette.surface, shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.border)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Formato fotográfico", color = palette.muted, fontSize = 11.sp)
                    Text(current?.name ?: "Seleccionar formato", color = palette.text,
                        fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                TextButton(onClick = { choosePreset = true }) { Text("Cambiar ▾") }
            }
            Text("Edita varios formatos sin sobrescribir los anteriores.", color = palette.muted, fontSize = 10.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(onClick = onNew) { Text("+ Nuevo", fontSize = 12.sp) }
                OutlinedButton(onClick = onDuplicate) { Text("Duplicar", fontSize = 12.sp) }
                OutlinedButton(onClick = onRename) { Text("Renombrar", fontSize = 12.sp) }
                OutlinedButton(onClick = onTrash, enabled = presets.size > 1) {
                    Text("Papelera", fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { chooseDefault = true }, modifier = Modifier.fillMaxWidth()) {
                val name = if (defaultReport.startsWith("Preset:")) {
                    presets.firstOrNull { it.id == defaultReport.substringAfter("Preset:") }?.name ?: "PDF 3"
                } else defaultReport
                Text("Reporte rápido por defecto: $name ▾", fontSize = 12.sp)
            }
        }
    }
    if (choosePreset) {
        LegacyMenuDialog(
            title = "Seleccionar formato fotográfico",
            options = presets.map { preset -> preset.name to { onSelect(preset.id) } },
            onDismiss = { choosePreset = false },
            palette = palette
        )
    }
    if (chooseDefault) {
        LegacyMenuDialog(
            title = "Reporte por defecto",
            options = (1..4).map { cols ->
                "PDF $cols" to { onDefault("PDF $cols") }
            } + presets.map { preset ->
                "Personalizado · ${preset.name}" to { onDefault("Preset:${preset.id}") }
            },
            onDismiss = { chooseDefault = false },
            palette = palette
        )
    }
}
