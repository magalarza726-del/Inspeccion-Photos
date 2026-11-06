package ec.espol.inspeccionphotos.integrity

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object IntegrityHasher {
    fun sha256(file: File): String = file.inputStream().buffered().use(::sha256)

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
