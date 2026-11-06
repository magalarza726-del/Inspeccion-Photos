package ec.espol.inspeccionphotos

import ec.espol.inspeccionphotos.coverage.CoverageAnalyzer
import ec.espol.inspeccionphotos.survey.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverageAnalyzerTest {
    @Test
    fun unseenRelevantBuildingProducesZeroCoverageWarning() {
        val building = BuildingFootprint(
            "b", "Edificio B", "FIMCP",
            listOf(GeoPoint(-2.0, -79.0), GeoPoint(-2.0, -78.9999), GeoPoint(-1.9999, -78.9999))
        )
        val result = CoverageAnalyzer.analyze(emptyList(), listOf(building), emptyList()).single()
        assertEquals(0, result.overallPercent)
        assertTrue(result.warnings.any { it.contains("No se identificaron") })
    }
}
