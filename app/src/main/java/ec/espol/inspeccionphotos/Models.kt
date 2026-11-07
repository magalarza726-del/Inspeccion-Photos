package ec.espol.inspeccionphotos

enum class FolderViewMode { GALLERY, LIST }

/**
 * Nodo de carpeta de Inspeccion Photos.
 *
 * La lista [codes] se conserva únicamente para compatibilidad de código/preview legado.
 * Los datos persistidos nuevos usan [children] de forma recursiva hasta 7 subniveles.
 */
data class Inspection(
    val id: String,
    val name: String,
    val created: String,
    val ot: String = "",
    val codes: List<CodeItem> = emptyList(),
    val generalObs: String = "",
    val photos: List<PhotoItem> = emptyList(),
    val children: List<Inspection> = emptyList(),
    val allowPhotos: Boolean = true,
    val allowSubfolders: Boolean = true,
    val viewMode: FolderViewMode = FolderViewMode.GALLERY,
    val collapsed: Boolean = false,
    /** Imagen destacada usada exclusivamente por el reporte personalizado. */
    val reportMainImagePath: String = "",
    /** Indica que la tabla fue agregada explícitamente, aunque sus celdas estén vacías. */
    val reportTableEnabled: Boolean = false,
    /** Valores de tabla por filas; la forma visible la define el Panel de Control. */
    val reportTableValues: List<List<String>> = emptyList(),
    /** Valores capturados para las secciones configuradas en el Panel de Control, indexados por id de celda. */
    val reportSectionValues: Map<String, String> = emptyMap(),
    val reportSectionTitles: Map<String, String> = emptyMap(),
    val createdAtMillis: Long = System.currentTimeMillis(),
    val modifiedAtMillis: Long = createdAtMillis,
    val mainPhotoPath: String = "",
    val reportTableVisible: Boolean = true,
    val reportMainImageVisible: Boolean = true,
    val reportSectionVisible: Boolean = true
)

/** Formato histórico de "códigos" del APK original. Se migra a [Inspection.children]. */
data class CodeItem(
    val name: String,
    val generalObs: String = "",
    val photos: List<PhotoItem> = emptyList()
)

data class PhotoItem(
    val name: String,
    val path: String,
    val observation: String = "",
    /** Copia de seguridad del archivo original cuando se guarda una edición. */
    val originalPath: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val modifiedAtMillis: Long = createdAtMillis
)
