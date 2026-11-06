package ec.espol.inspeccionphotos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.espol.inspeccionphotos.survey.SurveySession
import ec.espol.inspeccionphotos.survey.SurveyStatus
import ec.espol.inspeccionphotos.util.TimeUtils

@Composable
fun SurveyListScreen(
    surveys: List<SurveySession>,
    onBack: () -> Unit,
    onOpen: (SurveySession) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenTitle("Levantamientos", "Sesiones guardadas localmente.", onBack)
        Spacer(Modifier.height(18.dp))
        if (surveys.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = SpatialCard), modifier = Modifier.fillMaxWidth()) {
                Text("Todavía no hay levantamientos.", modifier = Modifier.padding(24.dp), color = SpatialMuted)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(surveys, key = { it.id }) { survey ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(survey) },
                        colors = CardDefaults.cardColors(containerColor = SpatialCard),
                        border = BorderStroke(1.dp, SpatialBorder),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(18.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(survey.place, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text(TimeUtils.date(survey.startedAtEpochMs), color = SpatialMuted)
                            }
                            Column {
                                Text("${survey.photoCount} fotos", fontWeight = FontWeight.Bold)
                                Text(
                                    when (survey.status) {
                                        SurveyStatus.ACTIVE -> "● activo"
                                        SurveyStatus.COMPLETED -> "✓ finalizado"
                                        SurveyStatus.DISCARDED -> "⚠ descartado"
                                    },
                                    color = when (survey.status) {
                                        SurveyStatus.ACTIVE -> SpatialGood
                                        SurveyStatus.COMPLETED -> SpatialBlue
                                        SurveyStatus.DISCARDED -> SpatialWarn
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
