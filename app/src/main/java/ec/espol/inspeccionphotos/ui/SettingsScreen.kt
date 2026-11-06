package ec.espol.inspeccionphotos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ec.espol.inspeccionphotos.survey.CaptureProfile
import ec.espol.inspeccionphotos.sync.SupabaseConfig

@Composable
fun SettingsScreen(
    profile: CaptureProfile,
    storagePath: String,
    supabaseConfig: SupabaseConfig,
    autoInteriorSwitch: Boolean = true,
    onProfileChange: (CaptureProfile) -> Unit,
    onAutoInteriorSwitchChange: (Boolean) -> Unit = {},
    onSupabaseSave: (SupabaseConfig) -> Unit,
    onBack: () -> Unit
) {
    var projectUrl by remember(supabaseConfig.projectUrl) { mutableStateOf(supabaseConfig.projectUrl) }
    var publishableKey by remember(supabaseConfig.publishableKey) { mutableStateOf(supabaseConfig.publishableKey) }
    var autoSync by remember(supabaseConfig.autoSync) { mutableStateOf(supabaseConfig.autoSync) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 760.dp
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(if (compact) 14.dp else 24.dp)
        ) {
            ScreenTitle("Configuración del levantamiento", "Precisión, almacenamiento y backend privado.", onBack)
            Spacer(Modifier.height(18.dp))
            if (compact) {
                CaptureSettingsCard(
                    profile, storagePath, autoInteriorSwitch,
                    onProfileChange, onAutoInteriorSwitchChange,
                    Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                SupabaseSettingsCard(
                    projectUrl, publishableKey, autoSync, supabaseConfig,
                    onProjectUrl = { projectUrl = it },
                    onPublishableKey = { publishableKey = it },
                    onAutoSync = { autoSync = it },
                    onSave = onSupabaseSave,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CaptureSettingsCard(
                        profile, storagePath, autoInteriorSwitch,
                        onProfileChange, onAutoInteriorSwitchChange,
                        Modifier.weight(1f)
                    )
                    SupabaseSettingsCard(
                        projectUrl, publishableKey, autoSync, supabaseConfig,
                        onProjectUrl = { projectUrl = it },
                        onPublishableKey = { publishableKey = it },
                        onAutoSync = { autoSync = it },
                        onSave = onSupabaseSave,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CaptureSettingsCard(
    profile: CaptureProfile,
    storagePath: String,
    autoInteriorSwitch: Boolean,
    onProfileChange: (CaptureProfile) -> Unit,
    onAutoInteriorSwitchChange: (Boolean) -> Unit,
    modifier: Modifier
) {
    Card(colors = CardDefaults.cardColors(containerColor = SpatialCard), modifier = modifier) {
        Column(Modifier.padding(18.dp)) {
            Text("Perfil de captura", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            CaptureProfile.entries.forEach { item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    RadioButton(selected = profile == item, onClick = { onProfileChange(item) })
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (item == CaptureProfile.HIGH_ACCURACY) "PRECISIÓN ALTA" else "BALANCEADO")
                        Text(
                            if (item == CaptureProfile.HIGH_ACCURACY) "GPS aproximado cada 1 s durante la sesión." else "GPS aproximado cada 3 s para ahorrar batería.",
                            color = SpatialMuted
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Interiores", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Top) {
                Switch(checked = autoInteriorSwitch, onCheckedChange = onAutoInteriorSwitchChange)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Cambio automático Exterior / Interior")
                    Text("Usa histéresis: GPS muy degradado para entrar y varias lecturas buenas para volver.", color = SpatialMuted)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "ARCore es opcional y funciona mediante Google Play Services for AR, provisto por Google LLC.",
                color = SpatialMuted
            )
            Spacer(Modifier.height(14.dp))
            Text("Almacenamiento local", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(storagePath, color = SpatialMuted)
            Spacer(Modifier.height(8.dp))
            Text("Los originales no se eliminan automáticamente.", color = SpatialGood)
        }
    }
}

@Composable
private fun SupabaseSettingsCard(
    projectUrl: String,
    publishableKey: String,
    autoSync: Boolean,
    supabaseConfig: SupabaseConfig,
    onProjectUrl: (String) -> Unit,
    onPublishableKey: (String) -> Unit,
    onAutoSync: (Boolean) -> Unit,
    onSave: (SupabaseConfig) -> Unit,
    modifier: Modifier
) {
    Card(colors = CardDefaults.cardColors(containerColor = SpatialCard), modifier = modifier) {
        Column(Modifier.padding(18.dp)) {
            Text("Supabase privado", style = MaterialTheme.typography.titleMedium)
            Text("Nube · Storage + Database + Auth + análisis espacial", color = SpatialMuted)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = projectUrl,
                onValueChange = onProjectUrl,
                label = { Text("Project URL") },
                placeholder = { Text("https://xxxx.supabase.co") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = publishableKey,
                onValueChange = onPublishableKey,
                label = { Text("Publishable / anon key") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (publishableKey.trim().startsWith("sb_secret_", ignoreCase = true)) {
                Text("No uses una secret/service-role key dentro de la app.", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = autoSync, onCheckedChange = onAutoSync)
                Spacer(Modifier.width(8.dp))
                Text("Sincronización automática")
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    onSave(
                        SupabaseConfig(
                            projectUrl = projectUrl,
                            publishableKey = publishableKey,
                            autoSync = autoSync,
                            storageBucket = supabaseConfig.storageBucket
                        )
                    )
                },
                enabled = !publishableKey.trim().startsWith("sb_secret_", ignoreCase = true),
                modifier = Modifier.fillMaxWidth()
            ) { Text("GUARDAR CONFIGURACIÓN") }
            Spacer(Modifier.height(8.dp))
            Text("La sesión de usuario se cifra con Android Keystore.", color = SpatialMuted)
        }
    }
}
