package ec.espol.inspeccionphotos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.espol.inspeccionphotos.R

/** Pantalla principal simplificada; el logotipo es el acceso oculto a desarrollo. */
@Composable
fun MainMenuScreen(
    palette: LegacyPalette,
    compact: Boolean,
    developerModeEnabled: Boolean,
    onDeveloperUnlocked: () -> Unit,
    onPhotos: () -> Unit,
    onForms: () -> Unit,
    onTrash: () -> Unit,
    onPanel: () -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit
) {
    var logoTaps by remember { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = if (compact) 16.dp else 40.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        Image(
            painterResource(R.drawable.inspeccion_photos_logo),
            contentDescription = "Logotipo de Inspeccion Photos",
            modifier = Modifier.size(if (compact) 82.dp else 104.dp).clickable {
                if (!developerModeEnabled) {
                    logoTaps++
                    if (logoTaps >= 7) {
                        logoTaps = 0
                        onDeveloperUnlocked()
                    }
                }
            }
        )
        Spacer(Modifier.height(8.dp))
        Text("Inspeccion Photos", color = palette.text, fontSize = if (compact) 26.sp else 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Column(Modifier.widthIn(max = 780.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MainMenuCard(Modifier.weight(1f), palette, R.drawable.ic_camera_original, "Cámara", onPhotos)
                MainMenuCard(Modifier.weight(1f), palette, R.drawable.ic_forms, "Documentos", onForms)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MainMenuCard(Modifier.weight(1f), palette, R.drawable.ic_folder_original, "Papelera", onTrash)
                MainMenuCard(Modifier.weight(1f), palette, R.drawable.ic_panel_control, "Formatos", onPanel)
            }
            MainMenuCard(Modifier.fillMaxWidth(), palette, R.drawable.ic_settings_original, "Configuración", onSettings, horizontal = true)
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = onSearch,
            modifier = Modifier.widthIn(max = 780.dp).fillMaxWidth().heightIn(min = 56.dp),
            shape = RoundedCornerShape(15.dp),
            border = BorderStroke(1.dp, palette.border),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.text)
        ) {
            Icon(painterResource(R.drawable.ic_search_original), contentDescription = null, tint = palette.blue, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text("Buscar", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MainMenuCard(
    modifier: Modifier,
    palette: LegacyPalette,
    icon: Int,
    title: String,
    onClick: () -> Unit,
    horizontal: Boolean = false
) {
    Card(
        modifier = modifier.heightIn(min = if (horizontal) 78.dp else 136.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(1.dp, palette.border),
        shape = RoundedCornerShape(19.dp)
    ) {
        if (horizontal) {
            Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(painterResource(icon), null, tint = palette.blue, modifier = Modifier.size(29.dp))
                Spacer(Modifier.width(14.dp))
                Text(title, color = palette.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(vertical = 19.dp, horizontal = 7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(55.dp).background(palette.blue.copy(alpha = .14f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(icon), null, tint = palette.blue, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(title, color = palette.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
            }
        }
    }
}
