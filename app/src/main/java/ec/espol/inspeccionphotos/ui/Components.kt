package ec.espol.inspeccionphotos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.espol.inspeccionphotos.survey.GpsQuality
import ec.espol.inspeccionphotos.survey.LocationSnapshot

@Composable
fun AppTopBar(title: String = "", subtitle: String = "") {
    if (title.isBlank() && subtitle.isBlank()) {
        Spacer(Modifier.fillMaxWidth().height(10.dp).background(SpatialTop))
        return
    }
    Column(Modifier.fillMaxWidth().background(SpatialTop).padding(horizontal = 18.dp, vertical = 10.dp)) {
        if (title.isNotBlank()) Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        if (subtitle.isNotBlank()) Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = .72f))
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Text("‹", Modifier.clickable(onClick = onBack).padding(end = 12.dp), fontSize = 40.sp, color = SpatialMuted)
        }
        Column {
            Text(title, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, color = SpatialMuted, fontSize = 14.sp) }
        }
    }
}

@Composable
fun BigActionCard(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(122.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SpatialCard),
        border = BorderStroke(1.dp, SpatialBorder)
    ) {
        Row(Modifier.fillMaxSize().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).background(SpatialPanel, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Text(icon, fontSize = 30.sp, color = SpatialBlue)
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = SpatialMuted)
            }
        }
    }
}

@Composable
fun MetricChip(label: String, value: String, color: Color = SpatialMuted) {
    Row(
        Modifier.border(1.dp, SpatialBorder, RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = SpatialMuted, fontSize = 12.sp)
        Spacer(Modifier.width(6.dp))
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun GpsBadge(location: LocationSnapshot?) {
    if (location?.isInteriorRelative == true) {
        val u = location.relativePoseUncertaintyMeters
        val color = when {
            u == null -> SpatialMuted
            u <= 4f -> SpatialGood
            u <= 8f -> SpatialWarn
            else -> SpatialBad
        }
        MetricChip("Pose rel. ●", u?.let { "± %.1f m".format(it) } ?: "incertidumbre —", color)
        return
    }
    val quality = location?.quality ?: GpsQuality.UNAVAILABLE
    val color = when (quality) {
        GpsQuality.EXCELLENT, GpsQuality.GOOD -> SpatialGood
        GpsQuality.ACCEPTABLE -> SpatialWarn
        GpsQuality.LOW, GpsQuality.VERY_LOW -> SpatialBad
        GpsQuality.UNAVAILABLE -> SpatialMuted
    }
    val value = location?.gnssAccuracyMeters?.let { "± %.1f m".format(it) } ?: "sin señal"
    MetricChip("GPS ●", value, color)
}

@Composable
fun PrimaryWideButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(14.dp)
    ) { Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(48.dp), border = BorderStroke(1.dp, SpatialBorder)) {
        Text(text)
    }
}

@Composable
fun MetricLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = SpatialMuted)
        Spacer(Modifier.weight(1f))
        Text(value)
    }
}
