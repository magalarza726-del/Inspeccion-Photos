package ec.espol.inspeccionphotos.ui

import android.os.StatFs
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.espol.inspeccionphotos.R
import kotlinx.coroutines.launch
import java.io.File

@Immutable
data class LegacyPalette(
    val bg: Color,
    val panel: Color,
    val surface: Color,
    val surface2: Color,
    val header: Color,
    val text: Color,
    val muted: Color,
    val blue: Color,
    val green: Color,
    val violet: Color,
    val cyan: Color,
    val border: Color
)

fun legacyPalette(dark: Boolean): LegacyPalette = if (dark) {
    LegacyPalette(
        bg = Color(0xFF06111F),
        panel = Color(0xFF0B1A2D),
        surface = Color(0xFF172742),
        surface2 = Color(0xFF1E3150),
        header = Color(0xFF073A70),
        text = Color(0xFFF7F9FD),
        muted = Color(0xFFAAB7CB),
        blue = Color(0xFF258BFF),
        green = Color(0xFF61D69C),
        violet = Color(0xFF8E64FF),
        cyan = Color(0xFF38CFF0),
        border = Color(0xFF314A70)
    )
} else {
    LegacyPalette(
        bg = Color(0xFFF5F8FC),
        panel = Color(0xFFEAF0F8),
        surface = Color(0xFFFFFFFF),
        surface2 = Color(0xFFF0F4FA),
        header = Color(0xFF0A4B82),
        text = Color(0xFF122033),
        muted = Color(0xFF64748B),
        blue = Color(0xFF1F78EA),
        green = Color(0xFF159467),
        violet = Color(0xFF7B4FE8),
        cyan = Color(0xFF159CB5),
        border = Color(0xFFCBD5E1)
    )
}

enum class LegacyRootSection { HOME, FOLDERS, FORMS, TRASH, CONTROL_PANEL, SETTINGS, SEARCH }

/**
 * Contenedor principal sin barra lateral. La navegación entre módulos parte de la pantalla
 * principal y cada módulo ofrece un botón Inicio en la barra superior.
 */
@Composable
fun LegacyAppShell(
    palette: LegacyPalette,
    selected: LegacyRootSection,
    compactTitle: String = "Inspeccion Photos",
    compactSubtitle: String = "",
    onHome: () -> Unit,
    content: @Composable BoxScope.(compact: Boolean) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(palette.bg)) {
        val compact = maxWidth < 700.dp
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .background(palette.bg)
        ) {
            if (selected != LegacyRootSection.HOME) {
                LegacyCompactTopBar(
                    palette = palette,
                    title = compactTitle,
                    subtitle = compactSubtitle,
                    onHome = onHome
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth().background(palette.bg)) { content(compact) }
        }
    }
}

@Composable
private fun LegacyCompactTopBar(
    palette: LegacyPalette,
    title: String,
    subtitle: String,
    onHome: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (subtitle.isBlank()) 58.dp else 68.dp)
            .background(palette.header)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onHome),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_home),
                contentDescription = "Volver al inicio",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle, color = Color.White.copy(alpha = .72f), fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Image(
            painter = painterResource(R.drawable.inspeccion_photos_logo),
            contentDescription = null,
            modifier = Modifier.size(38.dp)
        )
    }
}

@Composable
fun LegacyTitle(text: String, palette: LegacyPalette, size: Int = 23) {
    Text(text, color = palette.text, fontSize = size.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun LegacySubtitle(text: String, palette: LegacyPalette) {
    Text(text, color = palette.muted, fontSize = 14.sp)
}

@Composable
fun LegacyPrimaryButton(text: String, palette: LegacyPalette, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 46.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = palette.blue, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 17.dp, vertical = 9.dp)
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
}

@Composable
fun LegacySecondaryButton(text: String, palette: LegacyPalette, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, palette.border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.text),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
}

@Composable
fun LegacyIconActionButton(
    label: String,
    palette: LegacyPalette,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (selected) palette.blue else palette.surface
    val fg = if (selected) Color.White else palette.text
    Surface(
        modifier = modifier.size(44.dp).clickable(onClick = onClick),
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, if (selected) palette.blue else palette.border)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun LegacyHintBox(text: String, palette: LegacyPalette, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .background(palette.surface, RoundedCornerShape(11.dp))
            .border(1.dp, palette.border, RoundedCornerShape(11.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) { Text(text, color = palette.muted, fontSize = 13.sp) }
}

@Composable
fun LegacyMoreButton(palette: LegacyPalette, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_more_vert_original),
            contentDescription = "Más opciones",
            tint = palette.text,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun LegacySegmentedChoice(
    options: List<String>,
    selected: String,
    palette: LegacyPalette,
    onSelected: (String) -> Unit
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).border(1.dp, palette.border, RoundedCornerShape(9.dp))
    ) {
        options.forEach { option ->
            val active = option == selected
            Box(
                Modifier
                    .background(if (active) palette.blue else Color.Transparent)
                    .clickable { onSelected(option) }
                    .padding(horizontal = 16.dp, vertical = 11.dp)
            ) {
                Text(
                    option,
                    color = if (active) Color.White else palette.text,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp
                )
            }
        }
    }
}

/** Menú oscuro tipo hoja inferior, inspirado en la vista de opciones aprobada. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegacyMenuDialog(
    title: String,
    options: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
    palette: LegacyPalette = legacyPalette(true)
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        contentColor = palette.text,
        dragHandle = { BottomSheetDefaults.DragHandle(color = palette.muted) }
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
            Text(title, color = palette.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            options.forEachIndexed { index, (label, action) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            action()
                        }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(optionGlyph(label), color = palette.cyan, fontSize = 18.sp, modifier = Modifier.width(34.dp))
                    Text(label, color = palette.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                }
                if (index != options.lastIndex) Divider(color = palette.border.copy(alpha = .7f))
            }
        }
    }
}

private fun optionGlyph(label: String): String = when {
    label.contains("imagen", true) || label.contains("Abrir", true) -> "▣"
    label.contains("Editar", true) -> "✎"
    label.contains("Renombrar", true) -> "✐"
    label.contains("Observación", true) -> "●"
    label.contains("Descargar", true) -> "⇩"
    label.contains("Eliminar", true) -> "⌫"
    label.contains("Mover", true) -> "↔"
    label.contains("Exportar", true) -> "⇧"
    label.contains("Configurar", true) -> "⚙"
    label.contains("Contraer", true) -> "▴"
    label.contains("Expandir", true) -> "▾"
    else -> "•"
}

@Composable
fun InspeccionPhotosSplash(palette: LegacyPalette) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(palette.header, palette.bg)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
            Image(
                painter = painterResource(R.drawable.inspeccion_photos_logo),
                contentDescription = "Inspeccion Photos",
                modifier = Modifier.size(142.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text("Inspeccion Photos", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                "Organiza, documenta y comparte tus inspecciones",
                color = Color.White.copy(alpha = .76f),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(38.dp))
            LinearProgressIndicator(
                modifier = Modifier.width(150.dp).height(5.dp).clip(RoundedCornerShape(99.dp)),
                color = palette.cyan,
                trackColor = Color.White.copy(alpha = .18f)
            )
        }
    }
}
