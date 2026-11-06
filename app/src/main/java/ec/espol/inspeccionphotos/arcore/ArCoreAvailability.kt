package ec.espol.inspeccionphotos.arcore

import android.content.Context
import com.google.ar.core.ArCoreApk

object ArCoreAvailability {
    fun label(context: Context): String = runCatching {
        val availability = ArCoreApk.getInstance().checkAvailability(context)
        when {
            availability.isSupported -> "Disponible"
            availability.isUnsupported -> "No compatible"
            availability.isTransient -> "Comprobando…"
            else -> "Desconocido"
        }
    }.getOrElse { "No disponible" }
}
