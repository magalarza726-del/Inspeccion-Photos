package ec.espol.inspeccionphotos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.espol.inspeccionphotos.R

@Composable
fun MainMenuScreen(
    palette: LegacyPalette,
    compact: Boolean,
    onPhotos: () -> Unit,
    onForms: () -> Unit,
    onPanel: () -> Unit,
    onSettings: () -> Unit,
    onImportZip: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (compact) 18.dp else 40.dp, vertical = if (compact) 22.dp else 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.inspeccion_photos_logo),
            contentDescription = "Inspeccion Photos",
            modifier = Modifier.size(if (compact) 86.dp else 104.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text("Inspeccion Photos", color = palette.text, fontSize = if (compact) 27.sp else 34.sp, fontWeight = FontWeight.Bold)
        Text(
            "Organiza inspecciones, formularios y reportes desde un solo lugar.",
            color = palette.muted,
            fontSize = if (compact) 12.sp else 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 620.dp)
        )
        Spacer(Modifier.height(if (compact) 24.dp else 34.dp))

        Column(Modifier.widthIn(max = 860.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MainMenuCard(
                    modifier = Modifier.weight(1f), palette = palette,
                    icon = R.drawable.ic_camera_original,
                    title = "Fotos + Carpetas",
                    subtitle = "Organizador de fotos y estructura",
                    onClick = onPhotos
                )
                MainMenuCard(
                    modifier = Modifier.weight(1f), palette = palette,
                    icon = R.drawable.ic_forms,
                    title = "Formularios",
                    subtitle = "Llenar y administrar formularios",
                    onClick = onForms
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MainMenuCard(
                    modifier = Modifier.weight(1f), palette = palette,
                    icon = R.drawable.ic_panel_control,
                    title = "Formatos personalizados",
                    subtitle = "Panel de control y diseño de reportes",
                    onClick = onPanel
                )
                MainMenuCard(
                    modifier = Modifier.weight(1f), palette = palette,
                    icon = R.drawable.ic_settings_original,
                    title = "Configuración",
                    subtitle = "Preferencias, empresa y conexiones",
                    onClick = onSettings
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        OutlinedButton(
            onClick = onImportZip,
            modifier = Modifier.widthIn(max = 430.dp).fillMaxWidth(),
            shape = RoundedCornerShape(13.dp),
            border = BorderStroke(1.dp, palette.border),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.text)
        ) {
            Icon(painterResource(R.drawable.ic_folder_original), null, tint = palette.blue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(9.dp))
            Text("Importar proyecto ZIP", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Importa una estructura exportada por Inspeccion Photos con sus carpetas, fotos y observaciones.",
            color = palette.muted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 500.dp)
        )
    }
}

@Composable
private fun MainMenuCard(
    modifier: Modifier,
    palette: LegacyPalette,
    icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.heightIn(min = 154.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(1.dp, palette.border),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            Modifier.fillMaxSize().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(58.dp).background(palette.blue.copy(alpha = .14f), RoundedCornerShape(17.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(icon), null, tint = palette.blue, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(11.dp))
            Text(title, color = palette.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = palette.muted, fontSize = 10.sp, textAlign = TextAlign.Center, lineHeight = 13.sp)
        }
    }
}
