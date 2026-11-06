package ec.espol.inspeccionphotos.sync

data class SupabaseConfig(
    val projectUrl: String = "",
    val publishableKey: String = "",
    val autoSync: Boolean = true,
    val storageBucket: String = "inspeccion_photos-originals"
) {
    val clientKeyLooksSafe: Boolean
        get() = !publishableKey.trim().startsWith("sb_secret_", ignoreCase = true)

    val configured: Boolean
        get() = projectUrl.trim().startsWith("https://") && publishableKey.isNotBlank() && storageBucket.isNotBlank() && clientKeyLooksSafe
}

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val email: String,
    val authUserId: String
) {
    val validIdentity: Boolean get() = accessToken.isNotBlank() && refreshToken.isNotBlank() && authUserId.isNotBlank()
}

data class SyncRunResult(
    val attempted: Int,
    val verified: Int,
    val errors: Int,
    val skipped: Int,
    val message: String
)
