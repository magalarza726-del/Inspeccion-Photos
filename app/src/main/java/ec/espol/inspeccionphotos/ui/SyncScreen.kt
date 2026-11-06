package ec.espol.inspeccionphotos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.espol.inspeccionphotos.survey.SyncSummary
import ec.espol.inspeccionphotos.sync.SupabaseConfig

@Composable
fun SyncScreen(
    summary: SyncSummary,
    config: SupabaseConfig,
    signedInEmail: String?,
    statusMessage: String?,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    onSignOut: () -> Unit,
    onSyncNow: () -> Unit,
    onBack: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 760.dp
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(if (compact) 14.dp else 24.dp)
        ) {
            ScreenTitle("Sincronización", "Offline-first: cada foto se procesa, sube y verifica por separado.", onBack)
            Spacer(Modifier.height(16.dp))
            if (compact) {
                LocalStatusCard(summary, Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                SupabaseStatusCard(
                    summary, config, signedInEmail, statusMessage,
                    email, password,
                    onEmail = { email = it }, onPassword = { password = it },
                    onSignIn = { onSignIn(email, password); password = "" },
                    onSignUp = { onSignUp(email, password); password = "" },
                    onSignOut = onSignOut, onSyncNow = onSyncNow,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LocalStatusCard(summary, Modifier.weight(1f))
                    SupabaseStatusCard(
                        summary, config, signedInEmail, statusMessage,
                        email, password,
                        onEmail = { email = it }, onPassword = { password = it },
                        onSignIn = { onSignIn(email, password); password = "" },
                        onSignUp = { onSignUp(email, password); password = "" },
                        onSignOut = onSignOut, onSyncNow = onSyncNow,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LocalStatusCard(summary: SyncSummary, modifier: Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = SpatialCard), modifier = modifier) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Estado local", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            StatRow("Fotografías", summary.totalPhotos.toString())
            StatRow("SHA/previews pendientes", (summary.localOnly + summary.processing).toString())
            StatRow("En cola", summary.queued.toString())
            StatRow("Subidas, por verificar", summary.uploaded.toString())
            StatRow("Verificadas SHA-256", summary.verified.toString(), good = summary.verified > 0)
            StatRow("Errores", summary.errors.toString(), warning = summary.errors > 0)
            HorizontalDivider()
            Text("${summary.queueItems} unidad(es) persistidas en la cola offline.", color = SpatialMuted)
            Text("Si una falla, se reintenta únicamente esa fotografía.", color = SpatialMuted)
        }
    }
}

@Composable
private fun SupabaseStatusCard(
    summary: SyncSummary,
    config: SupabaseConfig,
    signedInEmail: String?,
    statusMessage: String?,
    email: String,
    password: String,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSignIn: () -> Unit,
    onSignUp: () -> Unit,
    onSignOut: () -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier
) {
    Card(colors = CardDefaults.cardColors(containerColor = SpatialCard), modifier = modifier) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Supabase", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            if (!config.configured) {
                Text("No configurado", color = MaterialTheme.colorScheme.error)
                Text("Configura Project URL y una publishable/anon key en Configuración. Después ejecuta supabase/phase2_schema.sql.", color = SpatialMuted)
            } else if (signedInEmail == null) {
                Text("Backend configurado · falta iniciar sesión", color = SpatialMuted)
                OutlinedTextField(email, onEmail, label = { Text("Correo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    password,
                    onPassword,
                    label = { Text("Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = onSignIn, enabled = email.isNotBlank() && password.length >= 6, modifier = Modifier.fillMaxWidth()) { Text("INICIAR SESIÓN") }
                OutlinedButton(onClick = onSignUp, enabled = email.isNotBlank() && password.length >= 6, modifier = Modifier.fillMaxWidth()) { Text("CREAR CUENTA") }
            } else {
                Text("Sesión: $signedInEmail", color = SpatialGood)
                Text("Storage privado · ${config.storageBucket}", color = SpatialMuted)
                Button(onClick = onSyncNow, enabled = summary.pending > 0, modifier = Modifier.fillMaxWidth()) { Text("SINCRONIZAR AHORA") }
                OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("CERRAR SESIÓN") }
            }
            statusMessage?.takeIf { it.isNotBlank() }?.let {
                HorizontalDivider()
                Text(it, color = SpatialMuted)
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, good: Boolean = false, warning: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = SpatialMuted, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        val color = when {
            warning -> MaterialTheme.colorScheme.error
            good -> SpatialGood
            else -> MaterialTheme.colorScheme.onSurface
        }
        Text(value, fontWeight = FontWeight.Bold, color = color)
    }
}
