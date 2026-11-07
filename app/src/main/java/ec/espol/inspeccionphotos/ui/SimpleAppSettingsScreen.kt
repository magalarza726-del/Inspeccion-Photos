package ec.espol.inspeccionphotos.ui

import androidx.compose.foundation.BorderStroke
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

/** Ajustes esenciales; las opciones avanzadas se conservan sin saturar esta pantalla. */
@Composable
fun SimpleAppSettingsScreen(
    palette: LegacyPalette,
    darkMode: Boolean,
    companyName: String,
    logoConfigured: Boolean,
    developerMode: Boolean,
    spatialFunctionsEnabled: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onCompanyNameChange: (String) -> Unit,
    onChooseLogo: () -> Unit,
    onClearLogo: () -> Unit,
    onSpatialChange: (Boolean) -> Unit,
    onAdvanced: () -> Unit
) {
    var companyDraft by remember(companyName) { mutableStateOf(companyName) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Configuración", color = palette.text, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Surface(color = palette.surface, shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, palette.border)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Modo oscuro", color = palette.text, fontWeight = FontWeight.SemiBold)
                    Text(if (darkMode) "Oscuro" else "Claro", color = palette.muted, fontSize = 12.sp)
                }
                Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
            }
        }
        Surface(color = palette.surface, shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, palette.border)) {
            Column(Modifier.padding(14.dp)) {
                Text("Empresa", color = palette.text, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(
                    value = companyDraft,
                    onValueChange = { companyDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre de la empresa") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = palette.text, unfocusedTextColor = palette.text)
                )
                Spacer(Modifier.height(7.dp))
                Button(
                    enabled = companyDraft.trim() != companyName.trim(),
                    onClick = { onCompanyNameChange(companyDraft.trim()) }
                ) { Text("Guardar nombre") }
            }
        }
        Surface(color = palette.surface, shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, palette.border)) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text("Logo de la empresa", color = palette.text, fontWeight = FontWeight.SemiBold)
                Text(if (logoConfigured) "Logo configurado" else "Sin logo personalizado",
                    color = palette.muted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    FilledTonalButton(onClick = onChooseLogo) { Text("Subir logo") }
                    if (logoConfigured) OutlinedButton(onClick = onClearLogo) { Text("Quitar") }
                }
            }
        }
        if (developerMode) {
            Surface(color = palette.surface, shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, palette.border)) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Levantamiento", color = palette.text, fontWeight = FontWeight.SemiBold)
                            Text("Funciones espaciales del modo desarrollador",
                                color = palette.muted, fontSize = 11.sp)
                        }
                        Switch(checked = spatialFunctionsEnabled, onCheckedChange = onSpatialChange)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onAdvanced, modifier = Modifier.fillMaxWidth()) {
                        Text("Herramientas avanzadas y respaldos")
                    }
                }
            }
        }
    }
}
