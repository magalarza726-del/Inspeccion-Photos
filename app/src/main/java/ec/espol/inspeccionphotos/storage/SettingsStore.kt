package ec.espol.inspeccionphotos.storage

import android.content.Context
import ec.espol.inspeccionphotos.survey.CaptureProfile
import ec.espol.inspeccionphotos.sync.SupabaseConfig

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("inspeccion_photos_settings_v2", Context.MODE_PRIVATE)

    var captureProfile: CaptureProfile
        get() = runCatching { CaptureProfile.valueOf(prefs.getString("capture_profile", CaptureProfile.HIGH_ACCURACY.name)!!) }
            .getOrDefault(CaptureProfile.HIGH_ACCURACY)
        set(value) { prefs.edit().putString("capture_profile", value.name).apply() }

    var autoInteriorSwitch: Boolean
        get() = prefs.getBoolean("auto_interior_switch", true)
        set(value) { prefs.edit().putBoolean("auto_interior_switch", value).apply() }

    var driveBackupEnabled: Boolean
        get() = prefs.getBoolean("drive_backup_enabled", false)
        set(value) { prefs.edit().putBoolean("drive_backup_enabled", value).apply() }

    var supabaseConfig: SupabaseConfig
        get() = SupabaseConfig(
            projectUrl = prefs.getString("supabase_project_url", "").orEmpty(),
            publishableKey = prefs.getString("supabase_publishable_key", "").orEmpty(),
            autoSync = prefs.getBoolean("supabase_auto_sync", true),
            storageBucket = prefs.getString("supabase_storage_bucket", "inspeccion_photos-originals").orEmpty().ifBlank { "inspeccion_photos-originals" }
        )
        set(value) {
            prefs.edit()
                .putString("supabase_project_url", value.projectUrl.trim().trimEnd('/'))
                .putString("supabase_publishable_key", value.publishableKey.trim())
                .putBoolean("supabase_auto_sync", value.autoSync)
                .putString("supabase_storage_bucket", value.storageBucket.trim().ifBlank { "inspeccion_photos-originals" })
                .apply()
        }
}
