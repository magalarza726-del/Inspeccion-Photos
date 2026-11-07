package ec.espol.inspeccionphotos.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.espol.inspeccionphotos.*
import java.io.File
import java.util.UUID

/** Opciones visuales exclusivamente de la vista previa, nunca del reporte real. */
data class PhotoPreviewSamples(
    val examplePhotos: Boolean = true,
    val folderSeparators: Boolean = true,
    val mainImage: Boolean = true,
    val generalObservations: Boolean = true,
    val photoObservations: Boolean = true,
    val pages: Int = 1
)

@Composable
fun PhotoPreviewOptionsEditor(
    palette: LegacyPalette,
    options: PhotoPreviewSamples,
    onChange: (PhotoPreviewSamples) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text("Contenido de ejemplo", color = palette.text, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        listOf(
            Triple("Fotos de ejemplo", options.examplePhotos, { checked: Boolean ->
                onChange(options.copy(examplePhotos = checked))
            }),
            Triple("Separadores de carpetas", options.folderSeparators, { checked: Boolean ->
                onChange(options.copy(folderSeparators = checked))
            }),
            Triple("Imagen principal de ejemplo", options.mainImage, { checked: Boolean ->
                onChange(options.copy(mainImage = checked))
            }),
            Triple("Observaciones generales", options.generalObservations, { checked: Boolean ->
                onChange(options.copy(generalObservations = checked))
            }),
            Triple("Observaciones de cada foto", options.photoObservations, { checked: Boolean ->
                onChange(options.copy(photoObservations = checked))
            })
        ).forEach { (label, checked, onChecked) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 42.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = palette.text, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Switch(checked = checked, onCheckedChange = onChecked)
            }
        }
        Spacer(Modifier.height(9.dp))
        Text("Páginas de vista previa", color = palette.text, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        LegacySegmentedChoice(listOf("1 página", "2 páginas", "3 páginas"),
            "${options.pages} ${if (options.pages == 1) "página" else "páginas"}", palette) {
            onChange(options.copy(pages = it.substringBefore(" ").toInt().coerceIn(1, 3)))
        }
        Spacer(Modifier.height(7.dp))
        Text(
            "Muestra hasta ${options.pages} página(s) reales del PDF. Si los ejemplos seleccionados generan menos, no se inventan páginas vacías. Las observaciones de fotos necesitan fotos activadas.",
            color = palette.muted, fontSize = 10.sp
        )
    }
}

/** Fabrica contenido de demostración y lo pasa por el exportador PDF verdadero. */
object PhotoPreviewSampleFactory {
    private fun photoFile(context: Context): File {
        val dir = File(context.cacheDir, "report_previews").apply { mkdirs() }
        val file = File(dir, "imagen_ejemplo.png")
        if (file.exists() && file.length() > 0) return file
        val image = Bitmap.createBitmap(720, 460, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 720f, 460f,
                Color.rgb(62, 96, 126), Color.rgb(197, 210, 215), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, 720f, 460f, fill)
        fill.shader = null
        fill.color = Color.rgb(52, 63, 73)
        canvas.drawRect(60f, 220f, 660f, 350f, fill)
        fill.color = Color.rgb(224, 167, 67)
        canvas.drawRect(140f, 95f, 250f, 335f, fill)
        canvas.drawRect(320f, 120f, 430f, 335f, fill)
        canvas.drawRect(510f, 65f, 610f, 335f, fill)
        fill.color = Color.WHITE
        fill.textSize = 29f
        fill.isFakeBoldText = true
        canvas.drawText("FOTO DE EJEMPLO", 175f, 410f, fill)
        file.outputStream().use { output ->
            image.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        image.recycle()
        return file
    }

    fun create(context: Context, config: CustomReportConfig, sample: PhotoPreviewSamples): File {
        val photoPath = if (sample.mainImage || sample.examplePhotos) photoFile(context).absolutePath else ""
        val sections = config.sections.flatMap { it.lines }.flatMap { it.cells }
            .filter { it.mode == SectionCellMode.VALUE }
            .associate { it.id to "Dato de ejemplo" }
        fun buildFolder(label: String, photoCount: Int): Inspection {
            val rows = List(config.tableRows) { row ->
                List(config.tableColumns) { column -> "Dato ${row + 1}.${column + 1}" }
            }
            val notes = if (sample.generalObservations) {
                "Ejemplo de observación general: se revisaron el estado de los equipos, la identificación y las condiciones del área de inspección."
            } else ""
            return Inspection(
                id = UUID.randomUUID().toString(),
                name = label,
                created = "2026-11-06",
                reportMainImagePath = if (sample.mainImage) photoPath else "",
                reportTableEnabled = true,
                reportTableValues = rows,
                reportSectionValues = sections,
                generalObs = notes,
                photos = if (sample.examplePhotos) List(photoCount) { index ->
                    PhotoItem(
                        name = "Foto de ejemplo ${index + 1}",
                        path = photoPath,
                        observation = if (sample.photoObservations) {
                            "Ejemplo: inspección visual completada."
                        } else ""
                    )
                } else emptyList()
            )
        }
        val pages = sample.pages.coerceIn(1, 3)
        val photoCount = if (sample.examplePhotos)
            (pages * 6 + 2) * config.evidenceColumns.coerceIn(1, 4) else 0
        val root = buildFolder("INSPECCIÓN DE EJEMPLO", if (sample.folderSeparators) photoCount / 2 else photoCount)
        val children = if (sample.folderSeparators) {
            List(pages) { index ->
                buildFolder("Sección ${index + 1} de ejemplo",
                    if (sample.examplePhotos) photoCount / (2 * pages) else 0)
            }
        } else emptyList()
        return CustomReportExporter.createPdf(
            context, root.copy(children = children), config,
            showRouteBands = sample.folderSeparators
        )
    }
}
