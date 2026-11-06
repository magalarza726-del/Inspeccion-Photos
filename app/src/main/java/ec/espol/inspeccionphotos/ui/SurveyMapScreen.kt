package ec.espol.inspeccionphotos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.espol.inspeccionphotos.ImageOrientationUtils
import ec.espol.inspeccionphotos.spatial.SpatialMath
import ec.espol.inspeccionphotos.survey.*
import kotlin.math.*
import java.io.File

@Composable
fun SurveyMapScreen(
    survey: SurveySession,
    route: List<RoutePoint>,
    photos: List<PhotoRecord>,
    pois: List<PoiRecord>,
    spatial: SpatialAnalysisBundle,
    previewPathResolver: (PhotoRecord) -> String? = { null },
    onBack: () -> Unit,
    onImportGis: () -> Unit,
    onAnalyze: () -> Unit
) {
    var selectedPhotoId by remember { mutableStateOf<String?>(null) }
    val inferenceByPhoto = remember(spatial.inferences) { spatial.inferences.associateBy { it.photoId } }
    val selectedPhoto = photos.firstOrNull { it.id == selectedPhotoId }
    val selectedPreview = remember(selectedPhoto?.id, selectedPhoto?.previewPath) {
        selectedPhoto?.let(previewPathResolver)?.let { path ->
            runCatching { ImageOrientationUtils.decodeOrientedBitmap(File(path), 1200)?.asImageBitmap() }.getOrNull()
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        ScreenTitle("Mapa · ${survey.place}", "Trayectoria, fotos, dirección de cámara, POIs y GIS", onBack)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricChip("Ruta", "${route.size} pts")
            MetricChip("Fotos mapa", "${photos.count { it.location != null }}")
            MetricChip("GIS", "${spatial.buildings.size} edificios")
            MetricChip("Identificadas", "${spatial.inferences.count { it.identified }}")
            MetricChip("Cobertura", spatial.summary?.averageCoveragePercent?.let { "$it%" } ?: "—")
            OutlinedButton(onClick = onImportGis) { Text("Importar GIS") }
            Button(onClick = onAnalyze) { Text("Reanalizar") }
        }
        Text("Ruta verde = exterior · ruta amarilla = interior · puntos azules = fotos · círculo tenue = precisión GNSS o incertidumbre relativa según fuente · cruces = POIs", color = SpatialMuted, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                Modifier.weight(1.7f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = SpatialPanel),
                border = BorderStroke(1.dp, SpatialBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                SurveyMapCanvas(
                    Modifier.fillMaxSize().padding(8.dp),
                    route = route,
                    photos = photos,
                    pois = pois,
                    buildings = spatial.buildings,
                    selectedPhotoId = selectedPhotoId,
                    onPhotoSelected = { selectedPhotoId = it }
                )
            }

            Column(Modifier.weight(0.9f).fillMaxHeight()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SpatialCard),
                    border = BorderStroke(1.dp, SpatialBorder),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("Fotografía seleccionada", fontWeight = FontWeight.Bold)
                        if (selectedPhoto == null) {
                            Text("Toca un punto azul del mapa.", color = SpatialMuted, fontSize = 12.sp)
                        } else {
                            val inf = inferenceByPhoto[selectedPhoto.id]
                            selectedPreview?.let { image ->
                                Image(
                                    bitmap = image,
                                    contentDescription = selectedPhoto.fileName,
                                    modifier = Modifier.fillMaxWidth().height(120.dp),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            Text(selectedPhoto.fileName, fontWeight = FontWeight.Bold, color = SpatialBlue)
                            val selectedLoc = selectedPhoto.location
                            Text(
                                if (selectedLoc?.isInteriorRelative == true)
                                    "Pose interior relativa ${selectedLoc.relativePoseUncertaintyMeters?.let { "± %.1f m".format(it) } ?: "sin incertidumbre"}"
                                else
                                    "GNSS ${selectedLoc?.gnssAccuracyMeters?.let { "± %.1f m".format(it) } ?: "no disponible"}",
                                color = SpatialMuted, fontSize = 12.sp
                            )
                            Text("Orientación ${selectedPhoto.orientation?.azimuthDegrees?.let { "%.0f°".format(it) } ?: "no disponible"}", color = SpatialMuted, fontSize = 12.sp)
                            Text(
                                if (inf?.identified == true) "Edificio probable: ${inf.buildingName}" else "Edificio probable: Desconocido",
                                fontWeight = FontWeight.SemiBold
                            )
                            inf?.let {
                                Text("Confianza ${(it.confidence * 100).roundToInt()}% · ${it.distanceMeters?.let { d -> "%.1f m".format(d) } ?: "—"}", color = if (it.identified) SpatialGood else SpatialWarn, fontSize = 12.sp)
                                Text(it.reason, color = SpatialMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Cobertura estimada", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                if (spatial.coverage.isEmpty()) {
                    Card(colors = CardDefaults.cardColors(containerColor = SpatialCard)) {
                        Text(
                            if (spatial.buildings.isEmpty()) "Importa un GeoJSON de huellas de edificios para activar cobertura e identificación." else "Aún no hay suficientes fotografías identificadas para estimar fachadas.",
                            Modifier.padding(12.dp), color = SpatialMuted, fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(spatial.coverage, key = { it.buildingId }) { item ->
                            CoverageCard(item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverageCard(item: BuildingCoverage) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SpatialCard),
        border = BorderStroke(1.dp, SpatialBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(11.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.buildingName, fontWeight = FontWeight.Bold)
                Text("${item.overallPercent}%", color = when {
                    item.overallPercent >= 80 -> SpatialGood
                    item.overallPercent >= 55 -> SpatialWarn
                    else -> SpatialBad
                }, fontWeight = FontWeight.Bold)
            }
            Text(
                item.facades.joinToString(" · ") { "${it.side.shortLabel()} ${it.percent}%" },
                color = SpatialMuted,
                fontSize = 11.sp
            )
            item.warnings.firstOrNull()?.let { Text("⚠ $it", color = SpatialWarn, fontSize = 11.sp, maxLines = 2) }
        }
    }
}

private fun FacadeSide.shortLabel() = when (this) {
    FacadeSide.NORTH -> "N"
    FacadeSide.EAST -> "E"
    FacadeSide.SOUTH -> "S"
    FacadeSide.WEST -> "O"
}

@Composable
private fun SurveyMapCanvas(
    modifier: Modifier,
    route: List<RoutePoint>,
    photos: List<PhotoRecord>,
    pois: List<PoiRecord>,
    buildings: List<BuildingFootprint>,
    selectedPhotoId: String?,
    onPhotoSelected: (String) -> Unit
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val geoPoints = remember(route, photos, pois, buildings) {
        buildList {
            route.forEach { add(GeoPoint(it.location.latitude, it.location.longitude)) }
            photos.mapNotNullTo(this) { it.location?.let { l -> GeoPoint(l.latitude, l.longitude) } }
            pois.mapNotNullTo(this) { it.location?.let { l -> GeoPoint(l.latitude, l.longitude) } }
            buildings.forEach { addAll(it.polygon) }
        }
    }
    val bounds = remember(geoPoints) { GeoBounds.from(geoPoints) }

    fun project(point: GeoPoint): Offset {
        if (size.width <= 0 || size.height <= 0) return Offset.Zero
        val pad = 28f
        val w = (size.width.toFloat() - pad * 2).coerceAtLeast(1f)
        val h = (size.height.toFloat() - pad * 2).coerceAtLeast(1f)
        val lonSpan = bounds.lonSpan.coerceAtLeast(1e-7)
        val latSpan = bounds.latSpan.coerceAtLeast(1e-7)
        val x = pad + ((point.longitude - bounds.minLon) / lonSpan * w).toFloat()
        val y = pad + ((bounds.maxLat - point.latitude) / latSpan * h).toFloat()
        return Offset(x, y)
    }

    Canvas(
        modifier
            .onSizeChanged { size = it }
            .pointerInput(photos, size, bounds) {
                detectTapGestures { tap ->
                    val nearest = photos.mapNotNull { photo ->
                        val loc = photo.location ?: return@mapNotNull null
                        val p = project(GeoPoint(loc.latitude, loc.longitude))
                        photo.id to (p - tap).getDistance()
                    }.minByOrNull { it.second }
                    if (nearest != null && nearest.second <= 34f) onPhotoSelected(nearest.first)
                }
            }
    ) {
        // Grid local para dar referencia espacial sin depender de tiles ni Internet.
        repeat(6) { i ->
            val x = size.width * i / 5f
            drawLine(SpatialBorder.copy(alpha = 0.28f), Offset(x, 0f), Offset(x, size.height.toFloat()), 1f)
            val y = size.height * i / 5f
            drawLine(SpatialBorder.copy(alpha = 0.28f), Offset(0f, y), Offset(size.width.toFloat(), y), 1f)
        }

        buildings.forEach { building ->
            if (building.polygon.size < 3) return@forEach
            val path = Path()
            building.polygon.forEachIndexed { index, gp ->
                val p = project(gp)
                if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            drawPath(path, SpatialWarn.copy(alpha = 0.13f))
            drawPath(path, SpatialWarn.copy(alpha = 0.65f), style = Stroke(width = 2f))
        }

        route.zipWithNext().forEach { (a, b) ->
            drawLine(
                (if (a.environmentMode == EnvironmentMode.INTERIOR) SpatialWarn else SpatialGood).copy(alpha = 0.9f),
                project(GeoPoint(a.location.latitude, a.location.longitude)),
                project(GeoPoint(b.location.latitude, b.location.longitude)),
                strokeWidth = 3f
            )
        }

        pois.forEach { poi ->
            val loc = poi.location ?: return@forEach
            val p = project(GeoPoint(loc.latitude, loc.longitude))
            drawCircle(SpatialWarn, 7f, p)
            drawLine(SpatialWarn, p + Offset(-9f, 0f), p + Offset(9f, 0f), 2f)
            drawLine(SpatialWarn, p + Offset(0f, -9f), p + Offset(0f, 9f), 2f)
        }

        val centerLon = (bounds.minLon + bounds.maxLon) / 2.0
        val verticalMeters = SpatialMath.distanceMeters(
            GeoPoint(bounds.minLat, centerLon),
            GeoPoint(bounds.maxLat, centerLon)
        ).coerceAtLeast(1.0)
        val metersPerPixel = verticalMeters / (size.height - 56).coerceAtLeast(1)

        photos.forEach { photo ->
            val loc = photo.location ?: return@forEach
            val p = project(GeoPoint(loc.latitude, loc.longitude))
            val selected = photo.id == selectedPhotoId
            loc.horizontalAccuracyMeters?.takeIf { it.isFinite() }?.let { accuracy ->
                val radiusPx = (accuracy / metersPerPixel).toFloat().coerceIn(4f, 38f)
                drawCircle(SpatialMuted.copy(alpha = 0.28f), radiusPx, p, style = Stroke(width = 1.3f))
            }
            drawCircle(if (selected) SpatialWarn else SpatialBlue, if (selected) 9f else 6f, p)
            photo.orientation?.azimuthDegrees?.let { heading ->
                val r = Math.toRadians(heading.toDouble())
                val length = if (selected) 34f else 23f
                val end = Offset(p.x + sin(r).toFloat() * length, p.y - cos(r).toFloat() * length)
                drawLine(if (selected) SpatialWarn else SpatialBlue.copy(alpha = 0.75f), p, end, if (selected) 3f else 2f)
            }
        }
    }
}

private data class GeoBounds(val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double) {
    val latSpan get() = maxLat - minLat
    val lonSpan get() = maxLon - minLon

    companion object {
        fun from(points: List<GeoPoint>): GeoBounds {
            if (points.isEmpty()) return GeoBounds(-2.15, -2.14, -79.97, -79.96)
            var minLat = points.minOf { it.latitude }
            var maxLat = points.maxOf { it.latitude }
            var minLon = points.minOf { it.longitude }
            var maxLon = points.maxOf { it.longitude }
            if (abs(maxLat - minLat) < 1e-7) { minLat -= 0.0001; maxLat += 0.0001 }
            if (abs(maxLon - minLon) < 1e-7) { minLon -= 0.0001; maxLon += 0.0001 }
            val latPad = (maxLat - minLat) * 0.08
            val lonPad = (maxLon - minLon) * 0.08
            return GeoBounds(minLat - latPad, maxLat + latPad, minLon - lonPad, maxLon + lonPad)
        }
    }
}
