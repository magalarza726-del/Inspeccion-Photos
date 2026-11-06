package ec.espol.inspeccionphotos.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SupabaseSessionVault(context: Context) {
    private val prefs = context.getSharedPreferences("inspeccion_photos_secure_session_v1", Context.MODE_PRIVATE)
    private val alias = "inspeccion_photos_supabase_session_aes_v1"

    fun save(session: AuthSession) {
        val json = JSONObject().apply {
            put("accessToken", session.accessToken)
            put("refreshToken", session.refreshToken)
            put("expiresAtEpochSeconds", session.expiresAtEpochSeconds)
            put("email", session.email)
            put("authUserId", session.authUserId)
        }.toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(json)
        prefs.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("payload", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun load(): AuthSession? {
        val ivRaw = prefs.getString("iv", null) ?: return null
        val payloadRaw = prefs.getString("payload", null) ?: return null
        return runCatching {
            val iv = Base64.decode(ivRaw, Base64.NO_WRAP)
            val payload = Base64.decode(payloadRaw, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val o = JSONObject(cipher.doFinal(payload).toString(Charsets.UTF_8))
            AuthSession(
                accessToken = o.getString("accessToken"),
                refreshToken = o.getString("refreshToken"),
                expiresAtEpochSeconds = o.optLong("expiresAtEpochSeconds"),
                email = o.optString("email"),
                authUserId = o.optString("authUserId")
            ).takeIf { it.validIdentity }
        }.getOrNull()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
