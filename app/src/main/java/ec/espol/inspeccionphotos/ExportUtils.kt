package ec.espol.inspeccionphotos

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min

object ExportUtils {
    /** Compatibilidad: genera la versión clásica de 3 columnas. */
    fun createObservationPdf(context: Context, inspection: Inspection): File = createObservationPdf(context, inspection, 3)

    /** Genera automáticamente las cuatro variantes solicitadas: 1, 2, 3 y 4 figuras por fila. */
    fun createObservationPdfs(context: Context, inspection: Inspection): List<File> = (1..4).map { columns ->
        createObservationPdf(context, inspection, columns)
    }

    fun createObservationPdf(context: Context, inspection: Inspection, columns: Int): File =
        createPhotoReportPdf(context, inspection, columns, spatial = false)

    fun createSpatialReportPdf(context: Context, inspection: Inspection): File =
        createPhotoReportPdf(context, inspection, 2, spatial = true)

    private fun createPhotoReportPdf(context: Context, inspection: Inspection, columns: Int, spatial: Boolean): File {
        val cols = columns.coerceIn(1, 4)
        val safeName = sanitize(inspection.name)
        val outDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val pdfFile = File(outDir, if (spatial) "${safeName}_REPORTE_ESPACIAL.pdf" else "${safeName}_PDF_${cols}col.pdf")
        val preferences = context.getSharedPreferences("inspeccion_photos_prefs", Context.MODE_PRIVATE)
        val showOt = preferences.getBoolean("show_ot", true)
        val companyName = preferences.getString("company_name", "").orEmpty().trim()
        val pdfLogoPath = preferences.getString("pdf_logo_path", "").orEmpty()

        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 34f
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK; textSize = 15f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val h2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK; textSize = 10f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.DKGRAY; textSize = 8.5f }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.DKGRAY; textSize = 7.5f }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.LTGRAY; strokeWidth = 1f; style = Paint.Style.STROKE }
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(10, 75, 130); strokeWidth = 3f }

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = margin

        fun header() {
            val top = y
            var brandX = margin
            val logoFile = pdfLogoPath.takeIf { it.isNotBlank() }?.let(::File)
            val customLogo = logoFile?.takeIf { it.exists() && it.length() > 0L }?.let { decodeScaledBitmap(it, 600, 400) }
            val logo = customLogo ?: runCatching { BitmapFactory.decodeResource(context.resources, R.drawable.inspeccion_photos_logo) }.getOrNull()
            if (logo != null) {
                val maxLogoW = 42f
                val maxLogoH = 34f
                val scale = min(maxLogoW / logo.width, maxLogoH / logo.height)
                val w = logo.width * scale
                val h = logo.height * scale
                canvas.drawBitmap(logo, null, android.graphics.RectF(margin, top, margin + w, top + h), null)
                brandX = margin + w + 9f
                logo.recycle()
            }
            val brandName = companyName.ifBlank { "Inspeccion Photos" }.take(64)
            canvas.drawText(brandName, brandX, top + 13f, h2)
            canvas.drawText(if (spatial) "Reporte espacial de inspección" else "Informe fotográfico de inspección", brandX, top + 26f, small)

            val dateText = "Fecha: ${inspection.created}"
            canvas.drawText(dateText, pageWidth - margin - body.measureText(dateText), top + 10f, body)
            if (showOt) {
                val otText = "OT: ${inspection.ot.ifBlank { "—" }}"
                canvas.drawText(otText, pageWidth - margin - body.measureText(otText), top + 24f, body)
            }
            canvas.drawLine(margin, top + 40f, pageWidth - margin, top + 40f, accent)
            y = top + 59f
            canvas.drawText(inspection.name, margin, y, title)
            y += 16f
            canvas.drawText(
                if (spatial) "Datos espaciales almacenados como observación · ${cols} columnas"
                else "${cols} columna(s) · Fotos organizadas por carpeta",
                margin, y, small
            )
            y += 18f
        }

        fun nextPage() {
            document.finishPage(page)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas; y = margin; header()
        }

        fun ensure(height: Float) { if (y + height > pageHeight - margin) nextPage() }

        fun drawFolder(folder: Inspection, depth: Int, path: String) {
            ensure(58f)
            val indent = (depth * 10f).coerceAtMost(60f)
            val label = if (depth == 0) "Carpeta" else "Subcarpeta nivel ${depth}"
            canvas.drawText("$label: ${folder.name}", margin + indent, y, h2)
            y += 12f
            canvas.drawText("Ruta: $path", margin + indent, y, small)
            y += 13f
            if (depth > 0 && showOt && folder.ot.isNotBlank()) {
                canvas.drawText("OT: ${folder.ot}", margin + indent, y, small); y += 12f
            }
            if (folder.generalObs.isNotBlank()) {
                y = drawWrapped(canvas, folder.generalObs, margin + indent, y, pageWidth - margin * 2 - indent, body)
                y += 6f
            }

            if (folder.photos.isNotEmpty()) {
                val available = pageWidth - margin * 2
                val gap = 8f
                val cellW = (available - gap * (cols - 1)) / cols
                val imageH = (cellW * .64f).coerceIn(70f, 230f)
                folder.photos.chunked(cols).forEach { row ->
                    val spatialLinesByPhoto = if (spatial) {
                        row.map { photo ->
                            wrapTextLines(
                                photo.observation.ifBlank { "Sin datos espaciales" },
                                small,
                                cellW,
                                maxLines = 14
                            )
                        }
                    } else emptyList()
                    val metadataLines = if (spatial) spatialLinesByPhoto.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1 else 1
                    val rowH = if (spatial) imageH + 18f + metadataLines * 9f + 8f else imageH + 34f
                    ensure(rowH + 8f)
                    row.forEachIndexed { index, photo ->
                        val x = margin + index * (cellW + gap)
                        val file = File(photo.path)
                        val bitmap = if (file.exists()) decodeScaledBitmap(file, (cellW * 3).toInt().coerceAtLeast(320), (imageH * 3).toInt().coerceAtLeast(220)) else null
                        if (bitmap != null) {
                            val src = centerCropSource(bitmap.width, bitmap.height, cellW / imageH)
                            canvas.drawBitmap(bitmap, src, android.graphics.RectF(x, y, x + cellW, y + imageH), null)
                            bitmap.recycle()
                        } else canvas.drawRect(x, y, x + cellW, y + imageH, line)
                        canvas.drawText(photo.name.take(32), x, y + imageH + 11f, small)
                        if (spatial) {
                            var textY = y + imageH + 22f
                            spatialLinesByPhoto[index].forEach { textLine ->
                                canvas.drawText(textLine, x, textY, small)
                                textY += 9f
                            }
                        } else {
                            val obs = photo.observation.ifBlank { "Sin observación" }.replace("\n", " ")
                            canvas.drawText(obs.take(if (cols == 1) 90 else if (cols == 2) 55 else 30), x, y + imageH + 23f, small)
                        }
                    }
                    y += rowH
                }
                y += 4f
            }

            folder.children.forEach { child ->
                val childPath = if (path.isBlank()) child.name else "$path / ${child.name}"
                drawFolder(child, depth + 1, childPath)
            }
            ensure(18f)
            canvas.drawLine(margin + indent, y, pageWidth - margin, y, line)
            y += 16f
        }

        header()
        drawFolder(inspection, 0, inspection.name)
        document.finishPage(page)
        FileOutputStream(pdfFile).use { document.writeTo(it) }
        document.close()
        return pdfFile
    }

    private fun decodeScaledBitmap(file: File, targetWidth: Int, targetHeight: Int): android.graphics.Bitmap? =
        ImageOrientationUtils.decodeOrientedBitmap(file, targetWidth, targetHeight)

    private fun wrapTextLines(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
        if (text.isBlank()) return listOf("")
        val result = mutableListOf<String>()
        val hardLines = text.replace("\r", "").split("\n")
        for (hardLine in hardLines) {
            if (result.size >= maxLines) break
            if (hardLine.isBlank()) {
                result += ""
                continue
            }
            var current = ""
            for (word in hardLine.trim().split(Regex("\\s+"))) {
                val candidate = if (current.isBlank()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth || current.isBlank()) {
                    current = candidate
                } else {
                    result += current
                    if (result.size >= maxLines) break
                    current = word
                }
            }
            if (result.size < maxLines && current.isNotBlank()) result += current
        }
        return result.take(maxLines)
    }

    private fun drawWrapped(canvas: android.graphics.Canvas, text: String, x: Float, startY: Float, maxWidth: Float, paint: Paint): Float {
        var y = startY
        val words = text.replace("\n", " \n ").split(" ")
        val line = StringBuilder()
        words.forEach { word ->
            if (word == "\n") {
                canvas.drawText(line.toString(), x, y, paint); line.clear(); y += paint.textSize + 4
            } else {
                val test = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(test) > maxWidth && line.isNotEmpty()) {
                    canvas.drawText(line.toString(), x, y, paint); line.clear(); line.append(word); y += paint.textSize + 4
                } else {
                    if (line.isNotEmpty()) line.append(' '); line.append(word)
                }
            }
        }
        if (line.isNotEmpty()) { canvas.drawText(line.toString(), x, y, paint); y += paint.textSize + 4 }
        return y
    }

    fun createInspectionZip(
        context: Context,
        inspection: Inspection,
        allInspections: List<Inspection>,
        repository: InspectionRepository,
        preparedPdfs: List<File>? = null,
        preparedBackup: File? = null
    ): File {
        val outDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val zipFile = File(outDir, "${sanitize(inspection.name)}.zip")
        val pdfs = preparedPdfs ?: createObservationPdfs(context, inspection)
        val backup = preparedBackup ?: repository.backupFile(allInspections)
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile), 1024 * 1024)).use { zip ->
            zip.setLevel(Deflater.NO_COMPRESSION)
            addToZip(zip, backup, "respaldo_datos.json")
            pdfs.forEach { addToZip(zip, it, it.name) }
            addFolderPhotos(zip, inspection, "fotos/${sanitize(inspection.name)}")
        }
        return zipFile
    }

    /** Exportación selectiva. Conserva la estructura/orden mediante manifest y JSON cuando se solicita. */
    fun createInspectionZipSelected(
        context: Context,
        inspection: Inspection,
        selection: ExportSelection,
        selectedPdfs: List<File>,
        spatialReports: List<File>,
        customReports: List<File>,
        backup: File?
    ): File {
        val outDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val zipFile = File(outDir, "${sanitize(inspection.name)}.zip")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile), 1024 * 1024)).use { zip ->
            zip.setLevel(Deflater.NO_COMPRESSION)
            if (selection.photosAndStructure) {
                backup?.takeIf { it.exists() }?.let { addToZip(zip, it, "estructura/respaldo_datos.json") }
                val manifest = File(outDir, "estructura_carpetas.json").apply {
                    writeText(CustomReportExporter.createStructureManifest(inspection))
                }
                addToZip(zip, manifest, "estructura/estructura_carpetas.json")
                addFolderPhotos(zip, inspection, "fotos/${sanitize(inspection.name)}")
            }
            selectedPdfs.filter { it.exists() }.forEach { addToZip(zip, it, "reporte_estandar/${it.name}") }
            spatialReports.filter { it.exists() }.forEach { addToZip(zip, it, "reporte_espacial/${it.name}") }
            customReports.filter { it.exists() }.forEach { addToZip(zip, it, "reporte_personalizado/${it.name}") }
        }
        return zipFile
    }

    /** Sobrecarga para código legado. */
    fun createInspectionZip(
        context: Context,
        inspection: Inspection,
        allInspections: List<Inspection>,
        repository: InspectionRepository,
        preparedPdf: File?,
        preparedBackup: File?
    ): File = createInspectionZip(context, inspection, allInspections, repository, preparedPdf?.let(::listOf), preparedBackup)

    private fun addFolderPhotos(zip: ZipOutputStream, folder: Inspection, path: String) {
        folder.photos.forEachIndexed { index, photo ->
            val file = File(photo.path)
            if (file.exists()) {
                // El prefijo numérico conserva el orden incluso fuera de Inspeccion Photos.
                val orderedName = "%04d_%s".format(index + 1, file.name)
                addToZip(zip, file, "$path/$orderedName")
            }
        }
        val mainImage = folder.reportMainImagePath.takeIf { it.isNotBlank() }?.let(::File)
        if (mainImage != null && mainImage.exists()) {
            addToZip(zip, mainImage, "$path/_reporte/imagen_principal.${mainImage.extension.ifBlank { "jpg" }}")
        }
        folder.children.forEachIndexed { index, child ->
            val orderedFolder = "%04d_%s".format(index + 1, sanitize(child.name))
            addFolderPhotos(zip, child, "$path/$orderedFolder")
        }
    }

    private fun addToZip(zip: ZipOutputStream, file: File, entryName: String) {
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).buffered(1024 * 1024).use { it.copyTo(zip, 1024 * 1024) }
        zip.closeEntry()
    }

    fun shareFile(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir archivo"))
    }

    fun copyToDownloads(context: Context, file: File, mime: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name); put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS); put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out -> file.inputStream().use { input -> input.copyTo(out, 1024 * 1024) } }
                values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0); context.contentResolver.update(it, values, null, null)
            }
            uri
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
            val destination = File(dir, file.name); file.copyTo(destination, overwrite = true); Uri.fromFile(destination)
        }
    }

    fun downloadPhoto(context: Context, photo: PhotoItem): Uri? {
        val file = File(photo.path); if (!file.exists()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, photo.name); put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/InspeccionPhotos"); put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out -> file.inputStream().use { input -> input.copyTo(out) } }
                values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0); context.contentResolver.update(it, values, null, null)
            }
            uri
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
            val destination = File(dir, photo.name); file.copyTo(destination, overwrite = true); Uri.fromFile(destination)
        }
    }

    private fun centerCropSource(width: Int, height: Int, targetAspect: Float): Rect {
        val sourceAspect = width.toFloat() / height.toFloat()
        return if (sourceAspect > targetAspect) {
            val cropWidth = (height * targetAspect).toInt().coerceAtMost(width)
            val left = ((width - cropWidth) / 2).coerceAtLeast(0)
            Rect(left, 0, left + cropWidth, height)
        } else {
            val cropHeight = (width / targetAspect).toInt().coerceAtMost(height)
            val top = ((height - cropHeight) / 2).coerceAtLeast(0)
            Rect(0, top, width, top + cropHeight)
        }
    }

    private fun sanitize(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
