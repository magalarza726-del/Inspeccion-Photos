package ec.espol.inspeccionphotos.interior

import ec.espol.inspeccionphotos.survey.EnvironmentMode
import ec.espol.inspeccionphotos.survey.LocationSnapshot

class EnvironmentModeAdvisor {
    private var badSamples = 0
    private var goodSamples = 0

    fun observe(current: EnvironmentMode, location: LocationSnapshot?): EnvironmentMode? {
        val accuracy = location?.horizontalAccuracyMeters
        val bad = accuracy == null || accuracy > 25f
        val good = accuracy != null && accuracy <= 8f
        badSamples = if (bad) badSamples + 1 else 0
        goodSamples = if (good) goodSamples + 1 else 0
        return when {
            current == EnvironmentMode.EXTERIOR && badSamples >= 5 -> EnvironmentMode.INTERIOR
            current == EnvironmentMode.INTERIOR && goodSamples >= 4 -> EnvironmentMode.EXTERIOR
            else -> null
        }
    }

    fun reset() {
        badSamples = 0
        goodSamples = 0
    }
}
