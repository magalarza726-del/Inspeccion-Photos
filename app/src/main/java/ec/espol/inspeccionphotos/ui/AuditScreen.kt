package ec.espol.inspeccionphotos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.espol.inspeccionphotos.survey.AuditSummary
import ec.espol.inspeccionphotos.survey.SurveySession
import ec.espol.inspeccionphotos.util.SurveyMath
import ec.espol.inspeccionphotos.util.TimeUtils

@Composable
fun AuditScreen(
    survey: SurveySession,
    audit: AuditSummary,
    onContinue: () -> Unit,
    onFinalize: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenTitle("Auditoría de ${survey.place}", "Revisa la sesión antes de cerrarla.")
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AuditCard("Fotos", "${audit.originalsPresent}/${audit.photos}", if (audit.originalsPresent == audit.photos) SpatialGood else SpatialBad, Modifier.weight(1f))
            AuditCard("Recorrido", SurveyMath.formatDistance(audit.distanceMeters), SpatialBlue, Modifier.weight(1f))
            AuditCard("Duración", TimeUtils.duration(audit.durationMillis), SpatialBlue, Modifier.weight(1f))
            AuditCard("Trayectoria", "${audit.routePoints} puntos", if (audit.routePoints > 1) SpatialGood else SpatialWarn, Modifier.weight(1f))
            AuditCard(
                "Cobertura",
                audit.spatialCoveragePercent?.let { "$it%" } ?: "—",
                when {
                    audit.spatialCoveragePercent == null -> SpatialMuted
                    audit.spatialCoveragePercent >= 80 -> SpatialGood
                    audit.spatialCoveragePercent >= 55 -> SpatialWarn
                    else -> SpatialBad
                },
                Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = SpatialCard)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Calidad GPS", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    AuditLine("Excelente", audit.gpsExcellent, SpatialGood)
                    AuditLine("Buena", audit.gpsGood, SpatialGood)
                    AuditLine("Aceptable", audit.gpsAcceptable, SpatialWarn)
                    AuditLine("Baja", audit.gpsLow + audit.gpsVeryLow, SpatialBad)
                    AuditLine("GNSS/no posición", audit.gpsUnavailable, SpatialMuted)
                    AuditLine("Pose interior relativa", audit.interiorRelativePhotos, SpatialBlue)
                    Spacer(Modifier.height(8.dp))
                    Text("Orientación disponible: ${audit.orientationAvailable}/${audit.photos}", color = SpatialMuted)
                }
            }
            Card(Modifier.weight(1.35f), colors = CardDefaults.cardColors(containerColor = SpatialCard)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Advertencias", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    if (audit.warnings.isEmpty()) {
                        Text("✓ No se detectaron problemas críticos en esta fase.", color = SpatialGood)
                    } else {
                        audit.warnings.forEach { Text("⚠ $it", color = SpatialWarn, modifier = Modifier.padding(vertical = 4.dp)) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Integridad local: ${audit.hashesReady}/${audit.photos} hashes · ${audit.previewsReady}/${audit.photos} previews", color = SpatialMuted, fontSize = 13.sp)
                    Text("Supabase: ${audit.syncVerified}/${audit.photos} verificadas · ${audit.syncPending} pendientes · ${audit.syncErrors} errores", color = if (audit.syncErrors > 0) SpatialWarn else SpatialMuted, fontSize = 13.sp)
                    Text("Espacial: ${audit.identifiedPhotos}/${audit.photos} fotos identificadas · ${audit.gisBuildings} edificios GIS", color = SpatialMuted, fontSize = 13.sp)
                    Text("Interior: ${audit.interiorPoints} puntos · ${audit.interiorRelativePhotos} fotos relativas (${audit.interiorArCorePoints} ARCore VIO) · ${audit.spatialBursts} ráfagas/${audit.burstPhotos} fotos", color = SpatialMuted, fontSize = 13.sp)
                    Text("Escaneo caminando: ${audit.videoScans} vídeos · ${audit.extractedVideoFrames} frames representativos", color = SpatialMuted, fontSize = 13.sp)
                    Text("Finalizar no elimina originales ni obliga a que haya Internet; la cola puede continuar después.", color = SpatialMuted, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onContinue, modifier = Modifier.weight(1f).height(56.dp)) { Text("CONTINUAR LEVANTAMIENTO") }
            Button(onClick = onFinalize, modifier = Modifier.weight(1f).height(56.dp)) { Text("FINALIZAR DE TODAS FORMAS", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun AuditCard(title: String, value: String, color: Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = SpatialCard)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = SpatialMuted)
            Text(value, color = color, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AuditLine(label: String, count: Int, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = color)
        Spacer(Modifier.weight(1f))
        Text(count.toString(), fontWeight = FontWeight.Bold)
    }
}
