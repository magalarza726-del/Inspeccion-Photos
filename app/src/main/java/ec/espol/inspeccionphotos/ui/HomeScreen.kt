package ec.espol.inspeccionphotos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.espol.inspeccionphotos.survey.SurveySession

@Composable
fun HomeScreen(
    activeSurvey: SurveySession?,
    onResumeActive: () -> Unit,
    onNewSurvey: () -> Unit,
    onSurveys: () -> Unit,
    onSync: () -> Unit,
    onSettings: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 680.dp
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(if (compact) 14.dp else 24.dp)
        ) {
            Text("Levantamiento espacial", fontSize = if (compact) 23.sp else 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Selecciona un lugar, inicia y toma fotos. La aplicación registra el resto.", color = SpatialMuted)
            if (activeSurvey != null) {
                Spacer(Modifier.height(16.dp))
                Card(colors = CardDefaults.cardColors(containerColor = SpatialCard), modifier = Modifier.fillMaxWidth()) {
                    if (compact) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Levantamiento activo", color = SpatialGood, fontWeight = FontWeight.Bold)
                            Text(activeSurvey.place, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = onResumeActive, modifier = Modifier.fillMaxWidth()) { Text("CONTINUAR") }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth().padding(16.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Levantamiento activo", color = SpatialGood, fontWeight = FontWeight.Bold)
                                Text(activeSurvey.place, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(onClick = onResumeActive) { Text("CONTINUAR") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigActionCard("＋", "Nuevo levantamiento", "Selecciona una zona y empieza a capturar.", onNewSurvey)
                    BigActionCard("▣", "Levantamientos", "Revisa sesiones, fotos y datos guardados.", onSurveys)
                    BigActionCard("↻", "Sincronización", "Estado local y cola de subida.", onSync)
                    BigActionCard("⚙", "Configuración", "Precisión, batería y almacenamiento.", onSettings)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        BigActionCard("＋", "Nuevo levantamiento", "Selecciona una zona y empieza a capturar.", onNewSurvey)
                        BigActionCard("↻", "Sincronización", "Estado local y cola de subida.", onSync)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        BigActionCard("▣", "Levantamientos", "Revisa sesiones, fotos y datos guardados.", onSurveys)
                        BigActionCard("⚙", "Configuración", "Precisión, batería y almacenamiento.", onSettings)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Seguimiento interior ARCore opcional · usa Google Play Services for AR, provisto por Google LLC.",
                color = SpatialMuted, fontSize = 11.sp
            )
        }
    }
}
