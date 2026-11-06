package ec.espol.inspeccionphotos

import org.junit.Assert.*
import org.junit.Test

class InspectionTreeTest {
    @Test fun moveSeveralRootsIntoAnotherFolder() {
        val a = Inspection("a", "A", "2026-09-08")
        val b = Inspection("b", "B", "2026-09-08")
        val month = Inspection("m", "Septiembre", "2026-09-08")
        val result = InspectionTree.move(listOf(a, b, month), setOf("a", "b"), "m")
        assertTrue(result.moved)
        val movedMonth = InspectionTree.find(result.nodes, "m")!!
        assertEquals(listOf("a", "b"), movedMonth.children.map { it.id })
    }

    @Test fun blocksCycles() {
        val child = Inspection("c", "Child", "2026-09-08")
        val root = Inspection("r", "Root", "2026-09-08", children = listOf(child))
        val result = InspectionTree.move(listOf(root), setOf("r"), "c")
        assertFalse(result.moved)
    }

    @Test fun blocksEighthSublevel() {
        fun chain(depth: Int, prefix: String): Inspection = if (depth == 0) {
            Inspection("$prefix-0", "N0", "2026-09-08")
        } else {
            Inspection("$prefix-$depth", "N$depth", "2026-09-08", children = listOf(chain(depth - 1, prefix)))
        }
        val deep = chain(7, "d")
        val destination = Inspection("dest", "Destino", "2026-09-08")
        val result = InspectionTree.move(listOf(deep, destination), setOf(deep.id), destination.id)
        assertFalse(result.moved)
    }
}
