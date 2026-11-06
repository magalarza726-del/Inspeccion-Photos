package ec.espol.inspeccionphotos

object InspectionTree {
    const val MAX_SUBFOLDER_LEVELS = 7

    data class FolderRef(val id: String, val name: String, val depth: Int, val path: String)

    fun find(nodes: List<Inspection>, id: String?): Inspection? {
        if (id == null) return null
        nodes.forEach { node ->
            if (node.id == id) return node
            find(node.children, id)?.let { return it }
        }
        return null
    }

    fun parentId(nodes: List<Inspection>, id: String): String? {
        nodes.forEach { node ->
            if (node.children.any { it.id == id }) return node.id
            parentId(node.children, id)?.let { return it }
        }
        return null
    }

    fun depth(nodes: List<Inspection>, id: String): Int? {
        fun walk(list: List<Inspection>, d: Int): Int? {
            list.forEach { node ->
                if (node.id == id) return d
                walk(node.children, d + 1)?.let { return it }
            }
            return null
        }
        return walk(nodes, 0)
    }

    fun ancestors(nodes: List<Inspection>, id: String): List<Inspection> {
        fun walk(list: List<Inspection>, trail: List<Inspection>): List<Inspection>? {
            list.forEach { node ->
                if (node.id == id) return trail + node
                walk(node.children, trail + node)?.let { return it }
            }
            return null
        }
        return walk(nodes, emptyList()).orEmpty()
    }

    fun update(nodes: List<Inspection>, id: String, transform: (Inspection) -> Inspection): List<Inspection> =
        nodes.map { node ->
            when {
                node.id == id -> transform(node)
                else -> node.copy(children = update(node.children, id, transform))
            }
        }

    fun remove(nodes: List<Inspection>, id: String): Pair<List<Inspection>, Inspection?> {
        var removed: Inspection? = null
        fun walk(list: List<Inspection>): List<Inspection> = buildList {
            list.forEach { node ->
                if (node.id == id && removed == null) {
                    removed = node
                } else {
                    add(node.copy(children = walk(node.children)))
                }
            }
        }
        return walk(nodes) to removed
    }

    fun insert(nodes: List<Inspection>, parentId: String?, child: Inspection): List<Inspection> {
        if (parentId == null) return nodes + child
        return update(nodes, parentId) { parent -> parent.copy(children = parent.children + child) }
    }

    fun contains(root: Inspection, id: String): Boolean =
        root.id == id || root.children.any { contains(it, id) }

    fun subtreeDepth(root: Inspection): Int =
        if (root.children.isEmpty()) 0 else 1 + (root.children.maxOfOrNull(::subtreeDepth) ?: 0)

    fun countPhotos(root: Inspection): Int = root.photos.size + root.children.sumOf(::countPhotos)

    fun flatten(nodes: List<Inspection>): List<FolderRef> {
        val out = mutableListOf<FolderRef>()
        fun walk(list: List<Inspection>, depth: Int, parentPath: String) {
            list.forEach { node ->
                val path = if (parentPath.isBlank()) node.name else "$parentPath / ${node.name}"
                out += FolderRef(node.id, node.name, depth, path)
                walk(node.children, depth + 1, path)
            }
        }
        walk(nodes, 0, "")
        return out
    }

    fun canAddChild(nodes: List<Inspection>, parentId: String): Boolean {
        val d = depth(nodes, parentId) ?: return false
        return d < MAX_SUBFOLDER_LEVELS
    }

    data class MoveResult(val nodes: List<Inspection>, val moved: Boolean, val message: String)

    fun move(nodes: List<Inspection>, selectedIds: Set<String>, destinationId: String?): MoveResult {
        if (selectedIds.isEmpty()) return MoveResult(nodes, false, "No hay carpetas seleccionadas")
        if (destinationId != null && destinationId in selectedIds) {
            return MoveResult(nodes, false, "Una carpeta no puede moverse dentro de sí misma")
        }
        val selectedRoots = buildList {
            fun collect(list: List<Inspection>) {
                list.forEach { node ->
                    if (node.id in selectedIds) add(node) else collect(node.children)
                }
            }
            collect(nodes)
        }
        if (selectedRoots.isEmpty()) return MoveResult(nodes, false, "No se encontraron las carpetas seleccionadas")

        if (destinationId != null) {
            val destination = find(nodes, destinationId) ?: return MoveResult(nodes, false, "Destino no encontrado")
            if (!destination.allowSubfolders) return MoveResult(nodes, false, "La carpeta destino no permite subcarpetas")
            if (selectedRoots.any { contains(it, destinationId) }) {
                return MoveResult(nodes, false, "No puedes mover una carpeta dentro de uno de sus descendientes")
            }
            val destinationDepth = depth(nodes, destinationId) ?: 0
            val deepestIncoming = selectedRoots.maxOfOrNull(::subtreeDepth) ?: 0
            if (destinationDepth + 1 + deepestIncoming > MAX_SUBFOLDER_LEVELS) {
                return MoveResult(nodes, false, "El movimiento superaría el límite de 7 subniveles")
            }
        } else {
            val deepestIncoming = selectedRoots.maxOfOrNull(::subtreeDepth) ?: 0
            if (deepestIncoming > MAX_SUBFOLDER_LEVELS) {
                return MoveResult(nodes, false, "La jerarquía seleccionada supera el límite permitido")
            }
        }

        var working = nodes
        val removed = mutableListOf<Inspection>()
        selectedRoots.forEach { selected ->
            val (newTree, node) = remove(working, selected.id)
            working = newTree
            if (node != null) removed += node
        }
        removed.forEach { node -> working = insert(working, destinationId, node) }
        return MoveResult(working, true, "${removed.size} carpeta(s) movida(s)")
    }
}
