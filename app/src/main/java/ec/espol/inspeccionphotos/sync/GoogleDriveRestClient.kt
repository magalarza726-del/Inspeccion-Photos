package ec.espol.inspeccionphotos.sync

import java.net.HttpURLConnection
import java.net.URI

object GoogleDriveRestClient {
    const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

    data class CheckResult(
        val ok: Boolean,
        val message: String
    )

    fun verify(accessToken: String): CheckResult {
        if (accessToken.isBlank()) return CheckResult(false, "Google no devolvió un token de acceso.")
        val connection = (URI("https://www.googleapis.com/drive/v3/files?pageSize=1&fields=files(id,name)").toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = connection.responseCode
            if (code in 200..299) {
                CheckResult(true, "Conexión verificada con Google Drive.")
            } else {
                val detail = runCatching {
                    connection.errorStream?.bufferedReader()?.use { it.readText().take(600) }
                }.getOrNull().orEmpty()
                CheckResult(false, "Drive API respondió HTTP $code${if (detail.isBlank()) "." else ": $detail"}")
            }
        } catch (t: Throwable) {
            CheckResult(false, t.message ?: "No se pudo contactar a Google Drive.")
        } finally {
            connection.disconnect()
        }
    }
}
