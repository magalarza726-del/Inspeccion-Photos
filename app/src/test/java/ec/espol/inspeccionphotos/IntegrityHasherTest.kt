package ec.espol.inspeccionphotos

import ec.espol.inspeccionphotos.integrity.IntegrityHasher
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class IntegrityHasherTest {
    @Test
    fun sha256_matchesKnownVector() {
        val file = File.createTempFile("inspeccion_photos_hash", ".txt")
        file.writeText("abc")
        try {
            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                IntegrityHasher.sha256(file)
            )
        } finally {
            file.delete()
        }
    }
}
