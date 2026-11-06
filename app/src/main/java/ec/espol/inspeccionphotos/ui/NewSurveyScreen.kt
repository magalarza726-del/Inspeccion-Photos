package ec.espol.inspeccionphotos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val DefaultPlaces = listOf("FIMCP", "FIEC", "Rectorado", "FCNM", "FICT", "FADCOM", "FCSH", "FCV", "FIMCM", "Otro")

@Composable
fun NewSurveyScreen(onBack: () -> Unit, onStart: (String) -> Unit) {
    var selected by remember { mutableStateOf(DefaultPlaces.first()) }
    var expanded by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf("") }
    val finalPlace = if (selected == "Otro") custom.trim() else selected

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenTitle("Nuevo levantamiento", "Sólo necesitas elegir el lugar.", onBack)
        Spacer(Modifier.height(36.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = SpatialCard),
            modifier = Modifier.widthIn(max = 560.dp).align(Alignment.CenterHorizontally)
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Seleccionar zona / facultad", fontSize = 16.sp, color = SpatialMuted)
                Spacer(Modifier.height(10.dp))
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().height(58.dp)) {
                        Text(selected, fontSize = 18.sp)
                        Spacer(Modifier.weight(1f))
                        Text("▼")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DefaultPlaces.forEach { place ->
                            DropdownMenuItem(text = { Text(place) }, onClick = { selected = place; expanded = false })
                        }
                    }
                }
                if (selected == "Otro") {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nombre del lugar") },
                        singleLine = true
                    )
                }
                Spacer(Modifier.height(28.dp))
                PrimaryWideButton("INICIAR LEVANTAMIENTO", enabled = finalPlace.isNotBlank()) { onStart(finalPlace) }
                Spacer(Modifier.height(10.dp))
                Text(
                    "La app guardará automáticamente posición, orientación, trayectoria y metadata disponible.",
                    color = SpatialMuted,
                    fontSize = 13.sp
                )
            }
        }
    }
}
