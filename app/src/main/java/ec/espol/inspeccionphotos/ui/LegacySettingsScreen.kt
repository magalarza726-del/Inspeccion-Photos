package ec.espol.inspeccionphotos.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import ec.espol.inspeccionphotos.R
import ec.espol.inspeccionphotos.sync.GoogleDriveRestClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LegacySettingsScreen(
    palette: LegacyPalette,
    compact: Boolean,
    developerMode: Boolean = false,
    driveOnly: Boolean = false,
    darkMode: Boolean,
    spatialFunctionsEnabled: Boolean,
    showOt: Boolean,
    companyName: String,
    pdfLogoConfigured: Boolean,
    addPhotoAction: AddPhotoAction,
    editorPromptMode: EditorPromptMode,
    cameraSessionMode: CameraSessionMode,
    askPhotoConfirmation: Boolean,
    hiddenLegacyPdfs: Set<Int>,
    googleDriveConnected: Boolean,
    googleDriveConnectedAt: Long,
    onDarkModeChange: (Boolean) -> Unit,
    onSpatialFunctionsChange: (Boolean) -> Unit,
    onShowOtChange: (Boolean) -> Unit,
    onCompanyNameChange: (String) -> Unit,
    onChoosePdfLogo: () -> Unit,
    onClearPdfLogo: () -> Unit,
    onAddPhotoActionChange: (AddPhotoAction) -> Unit,
    onEditorPromptModeChange: (EditorPromptMode) -> Unit,
    onCameraSessionModeChange: (CameraSessionMode) -> Unit,
    onAskPhotoConfirmationChange: (Boolean) -> Unit,
    onHiddenLegacyPdfsChange: (Set<Int>) -> Unit,
    onGoogleDriveConnectionChange: (Boolean, Long) -> Unit,
    onExportAllConfiguration: () -> Unit,
    onImportAllConfiguration: () -> Unit
) {
    var companyDraft by remember(companyName) { mutableStateOf(companyName) }
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val driveClient = remember(activity) { activity?.let { Identity.getAuthorizationClient(it) } }
    val coroutineScope = rememberCoroutineScope()
    var driveBusy by remember { mutableStateOf(false) }
    var driveMessage by remember { mutableStateOf<String?>(null) }

    fun verifyDriveToken(token: String?) {
        if (token.isNullOrBlank()) {
            driveBusy = false
            driveMessage = "Google no devolvió un token de acceso."
            return
        }
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) { GoogleDriveRestClient.verify(token) }
            driveBusy = false
            driveMessage = result.message
            if (result.ok) onGoogleDriveConnectionChange(true, System.currentTimeMillis())
        }
    }

    val driveAuthorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        val data = activityResult.data
        if (activityResult.resultCode != Activity.RESULT_OK || data == null || driveClient == null) {
            driveBusy = false
            driveMessage = "Conexión con Google Drive cancelada."
        } else {
            runCatching { driveClient.getAuthorizationResultFromIntent(data) }
                .onSuccess { authorizationResult -> verifyDriveToken(authorizationResult.accessToken) }
                .onFailure { error ->
                    driveBusy = false
                    driveMessage = error.message ?: "No se pudo completar la autorización de Google Drive."
                }
        }
    }

    fun connectGoogleDrive() {
        val client = driveClient
        if (client == null) {
            driveMessage = "No se encontró una Activity disponible para iniciar Google Drive."
            return
        }
        driveBusy = true
        driveMessage = null
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(GoogleDriveRestClient.DRIVE_FILE_SCOPE)))
            .setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
            .build()
        client.authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent == null) {
                        driveBusy = false
                        driveMessage = "Google Drive requiere autorización, pero no devolvió una pantalla de acceso."
                    } else {
                        driveAuthorizationLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                    }
                } else {
                    verifyDriveToken(result.accessToken)
                }
            }
            .addOnFailureListener { error ->
                driveBusy = false
                driveMessage = error.message ?: "No se pudo iniciar la autorización de Google Drive."
            }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (compact) 12.dp else 24.dp, vertical = 14.dp)
            .padding(bottom = 24.dp)
    ) {
        if (driveOnly) {
            SettingsSectionTitle("API de Google Drive", palette)
            SettingsSurface(palette) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Conexión de Google Drive", color = palette.text,
                            fontWeight = FontWeight.SemiBold)
                        Text(if (googleDriveConnected) "Conexión verificada" else "Sin conexión verificada",
                            color = if (googleDriveConnected) palette.green else palette.muted)
                        if (googleDriveConnectedAt > 0L) {
                            val formatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
                            Text("Última verificación: ${formatter.format(Date(googleDriveConnectedAt))}",
                                color = palette.muted, fontSize = 11.sp)
                        }
                    }
                    if (driveBusy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                Spacer(Modifier.height(12.dp))
                LegacyPrimaryButton(
                    if (googleDriveConnected) "Verificar / cambiar cuenta" else "Conectar Google Drive",
                    palette, Modifier.fillMaxWidth()
                ) { if (!driveBusy) connectGoogleDrive() }
                driveMessage?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(message, color = if (googleDriveConnected) palette.green else palette.muted)
                }
                Spacer(Modifier.height(10.dp))
                Text("Usa OAuth 2.0 y el permiso drive.file. Para iniciar sesión, la API de Drive y el cliente OAuth Android deben estar configurados con la huella SHA de esta app.",
                    color = palette.muted, fontSize = 11.sp)
            }
            return@Column
        }

        if (!compact) {
            LegacyTitle("Configuración", palette, 25)
            Spacer(Modifier.height(3.dp))
            LegacySubtitle("Ajusta visibilidad, cámara, empresa y documentos.", palette)
            Spacer(Modifier.height(18.dp))
        }

        if (developerMode) {
        SettingsSectionTitle("Levantamiento (desarrollador)", palette)
        SettingsSurface(palette) {
            SettingsSwitchRow(
                title = "Agregar funciones espaciales",
                subtitle = "Añade automáticamente ubicación y orientación a las observaciones de las fotos, sin cambiar la interfaz de Fotos + Carpetas.",
                checked = spatialFunctionsEnabled,
                palette = palette,
                onChecked = onSpatialFunctionsChange
            )
            Divider(color = palette.border.copy(alpha = .55f))
            SettingsSwitchRow(
                title = "OT (Orden de Trabajo)",
                subtitle = "Al ocultarla desaparece también de los cuatro PDFs.",
                checked = showOt,
                palette = palette,
                onChecked = onShowOtChange
            )
        }

        Spacer(Modifier.height(18.dp))
        }
        SettingsSectionTitle("Cámara y captura", palette)
        SettingsSurface(palette) {
            SettingsChoiceRow(
                label = "Al pulsar + Foto",
                value = if (addPhotoAction == AddPhotoAction.ASK) "Preguntar" else "Cámara directa",
                options = listOf("Preguntar", "Cámara directa"),
                palette = palette
            ) {
                onAddPhotoActionChange(if (it == "Preguntar") AddPhotoAction.ASK else AddPhotoAction.CAMERA_DIRECT)
            }
            Divider(color = palette.border.copy(alpha = .55f))
            SettingsChoiceRow(
                label = "Editor después de tomar foto",
                value = if (editorPromptMode == EditorPromptMode.ASK_AFTER_CAPTURE) "Preguntar" else "No preguntar",
                options = listOf("Preguntar", "No preguntar"),
                palette = palette
            ) {
                onEditorPromptModeChange(if (it == "Preguntar") EditorPromptMode.ASK_AFTER_CAPTURE else EditorPromptMode.NEVER_ASK)
            }
            Divider(color = palette.border.copy(alpha = .55f))
            SettingsChoiceRow(
                label = "Sesión de cámara",
                value = if (cameraSessionMode == CameraSessionMode.ONE_BY_ONE) "Una por una" else "Sesión continua",
                options = listOf("Una por una", "Sesión continua"),
                palette = palette
            ) {
                onCameraSessionModeChange(if (it == "Una por una") CameraSessionMode.ONE_BY_ONE else CameraSessionMode.BATCH)
            }
            Divider(color = palette.border.copy(alpha = .55f))
            SettingsSwitchRow(
                title = "Preguntar si está bien la foto",
                subtitle = "Muestra una vista previa antes de aceptar cada captura.",
                checked = askPhotoConfirmation,
                palette = palette,
                onChecked = onAskPhotoConfirmationChange
            )
        }

        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle("Empresa y PDF", palette)
        SettingsSurface(palette) {
            OutlinedTextField(
                value = companyDraft,
                onValueChange = { companyDraft = it },
                label = { Text("Nombre de la empresa") },
                placeholder = { Text("Ej. ACME Industrial S.A.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = palette.text,
                    unfocusedTextColor = palette.text,
                    focusedLabelColor = palette.blue,
                    unfocusedLabelColor = palette.muted,
                    focusedBorderColor = palette.blue,
                    unfocusedBorderColor = palette.border,
                    cursorColor = palette.blue,
                    focusedContainerColor = palette.surface2,
                    unfocusedContainerColor = palette.surface2
                ),
                shape = RoundedCornerShape(11.dp)
            )
            Spacer(Modifier.height(9.dp))
            LegacyPrimaryButton("Guardar nombre", palette, Modifier.fillMaxWidth()) { onCompanyNameChange(companyDraft) }
            Spacer(Modifier.height(15.dp))

            Row(
                Modifier.fillMaxWidth().background(palette.surface2, RoundedCornerShape(12.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(62.dp).background(palette.panel, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.inspeccion_photos_logo),
                        contentDescription = null,
                        modifier = Modifier.size(50.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Logo del PDF", color = palette.text, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (pdfLogoConfigured) "Logo personalizado configurado" else "Usará el logo de Inspeccion Photos por defecto",
                        color = if (pdfLogoConfigured) palette.green else palette.muted,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LegacySecondaryButton(if (pdfLogoConfigured) "Cambiar logo" else "Subir logo", palette, Modifier.weight(1f), onChoosePdfLogo)
                if (pdfLogoConfigured) LegacySecondaryButton("Quitar logo", palette, Modifier.weight(1f), onClearPdfLogo)
            }
            Spacer(Modifier.height(12.dp))
            Text("Ocultar PDF a 1, 2, 3 o 4 columnas", color = palette.text, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..4).forEach { number ->
                    FilterChip(
                        selected = number in hiddenLegacyPdfs,
                        onClick = {
                            val next = hiddenLegacyPdfs.toMutableSet()
                            if (!next.add(number)) next.remove(number)
                            onHiddenLegacyPdfsChange(next)
                        },
                        label = { Text("PDF $number", fontSize = 10.sp) }
                    )
                }
            }
            Text("Los PDFs marcados no aparecerán en la ventana de exportación.", color = palette.muted, fontSize = 10.sp)
            Spacer(Modifier.height(12.dp))
            Text("Al exportar puedes elegir Fotos + Carpetas, Reporte estándar, Reporte espacial (si está activo) y Reporte personalizado. La última selección queda guardada.", color = palette.muted, fontSize = 11.sp)
        }

        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle("Google Drive", palette)
        SettingsSurface(palette) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("API de Google Drive", color = palette.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(
                        if (googleDriveConnected) "Conexión verificada" else "Sin conexión verificada",
                        color = if (googleDriveConnected) palette.green else palette.muted,
                        fontSize = 11.sp
                    )
                    if (googleDriveConnectedAt > 0L) {
                        val formatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
                        Text("Última verificación: ${formatter.format(Date(googleDriveConnectedAt))}", color = palette.muted, fontSize = 9.sp)
                    }
                }
                if (driveBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            Spacer(Modifier.height(10.dp))
            LegacyPrimaryButton(
                if (googleDriveConnected) "Verificar / cambiar cuenta" else "Conectar Google Drive",
                palette,
                Modifier.fillMaxWidth()
            ) { if (!driveBusy) connectGoogleDrive() }
            driveMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(message, color = if (googleDriveConnected) palette.green else palette.muted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Usa OAuth 2.0 con el permiso drive.file: la app solo solicita acceso a archivos que cree o que el usuario comparta con ella. Para que funcione en una instalación firmada, habilita Drive API y configura un cliente OAuth de Android para el paquete ec.espol.inspeccionphotos y la huella SHA correspondiente.",
                color = palette.muted,
                fontSize = 10.sp
            )
        }

        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle("Respaldo de configuración", palette)
        SettingsSurface(palette) {
            Text(
                "Configuración total de la app",
                color = palette.text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Exporta o importa preferencias, empresa y logo, formato personalizado y formatos de formularios. No incluye proyectos, fotos ni formularios rellenados.",
                color = palette.muted,
                fontSize = 10.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LegacySecondaryButton(
                    "Exportar configuración",
                    palette,
                    Modifier.weight(1f),
                    onExportAllConfiguration
                )
                LegacySecondaryButton(
                    "Importar configuración",
                    palette,
                    Modifier.weight(1f),
                    onImportAllConfiguration
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "Al importar se reemplaza la configuración actual. Por seguridad, la autorización de Google Drive no se transfiere y deberá verificarse nuevamente.",
                color = palette.muted,
                fontSize = 9.sp
            )
        }

        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle("Interfaz", palette)
        SettingsSurface(palette) {
            Text("Apariencia", color = palette.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(7.dp))
            LegacySegmentedChoice(listOf("Claro", "Oscuro"), if (darkMode) "Oscuro" else "Claro", palette) {
                onDarkModeChange(it == "Oscuro")
            }
        }

    }
}


private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun SettingsSectionTitle(text: String, palette: LegacyPalette) {
    Text(text, color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(7.dp))
}

@Composable
private fun SettingsSurface(palette: LegacyPalette, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(1.dp, palette.border),
        shape = RoundedCornerShape(15.dp),
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    palette: LegacyPalette,
    onChecked: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = palette.muted, fontSize = 10.sp)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SettingsChoiceRow(
    label: String,
    value: String,
    options: List<String>,
    palette: LegacyPalette,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = palette.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                border = BorderStroke(1.dp, palette.border),
                shape = RoundedCornerShape(9.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.text)
            ) {
                Text(value, fontSize = 11.sp)
                Spacer(Modifier.width(5.dp))
                Text("⌄", color = palette.muted)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = palette.surface2
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = palette.text, fontSize = 12.sp) },
                        onClick = {
                            expanded = false
                            onSelected(option)
                        }
                    )
                }
            }
        }
    }
}
