package ec.espol.inspeccionphotos

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min

object CustomReportExporter {
    fun createAll(context: Context, root: Inspection, config: CustomReportConfig): List<File> {
        val cfg = config.normalized()
        val canonicalPdf = createPdf(context, root, cfg)
        return buildList {
            if (ReportExportFormat.PDF in cfg.exportFormats) add(canonicalPdf)
            if (ReportExportFormat.WORD in cfg.exportFormats) add(createDocx(context, root, cfg, canonicalPdf))
            if (ReportExportFormat.EXCEL in cfg.exportFormats) add(createXlsx(context, root, cfg, canonicalPdf))
        }
    }

    fun createPdf(
        context: Context,
        root: Inspection,
        config: CustomReportConfig,
        showRouteBands: Boolean = true
    ): File {
        val cfg = config.normalized()
        val out = File(File(context.cacheDir, "exports").apply { mkdirs() }, "${safe(root.name)}_REPORTE_PERSONALIZADO.pdf")
        val doc = PdfDocument()
        val pw = 595f
        val ph = 842f
        val margin = 28f
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.DKGRAY
            textSize = 9f
            textAlign = Paint.Align.CENTER
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.DKGRAY
            textSize = 7.8f
        }
        val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 8.3f
            typeface = Typeface.DEFAULT_BOLD
        }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(75, 75, 75)
            strokeWidth = .8f
            style = Paint.Style.STROKE
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(246, 248, 251)
            style = Paint.Style.FILL
        }

        var pageNum = 0
        lateinit var page: PdfDocument.Page
        lateinit var canvas: android.graphics.Canvas
        var y = 0f

        fun newPage() {
            if (pageNum > 0) doc.finishPage(page)
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pw.toInt(), ph.toInt(), pageNum).create())
            canvas = page.canvas
            y = margin
            drawHeader(canvas, cfg, margin, pw - margin, y, 82f, titlePaint, subtitlePaint, body, border)
            y += 93f
        }

        fun ensure(height: Float) {
            if (y + height > ph - margin) newPage()
        }

        newPage()

        fun drawFolder(folder: Inspection, route: String) {
            val hasImage = folder.reportMainImageVisible && folder.reportMainImagePath.isNotBlank() && File(folder.reportMainImagePath).exists()
            val hasTable = folder.reportTableEnabled && folder.reportTableVisible && folder.reportTableValues.any { row -> row.any { it.isNotBlank() } }
            val hasPhotos = folder.photos.isNotEmpty()
            val hasSections = folder.reportSectionVisible && cfg.sections.isNotEmpty() && folder.reportSectionValues.values.any { it.isNotBlank() }
            val hasDirectContent = hasImage || hasTable || hasPhotos || hasSections ||
                folder.generalObs.isNotBlank()

            // Do not create empty route bands for structural-only folders.
            if (hasDirectContent) {
                if (showRouteBands) {
                    ensure(32f)
                    canvas.drawRect(margin, y, pw - margin, y + 19f, fill)
                    canvas.drawRect(margin, y, pw - margin, y + 19f, border)
                    drawFittedLeft(
                        canvas,
                        route,
                        margin + 6f,
                        y + 12.5f,
                        pw - 2f * margin - 12f,
                        bold,
                        5.8f
                    )
                    y += 25f
                }

                if (hasSections && cfg.sectionPlacement == SectionPlacement.ABOVE_MAIN_BLOCK) {
                    cfg.sections.forEach { section ->
                        val sectionH = 22f + section.lines.size * 27f
                        ensure(sectionH + 8f)
                        y = drawSectionBlock(canvas, section.copy(
                            title = folder.reportSectionTitles[section.id]?.takeIf { it.isNotBlank() } ?: section.title
                        ), folder.reportSectionValues, margin, y, pw - 2f * margin, body, bold, border)
                        y += 7f
                    }
                }

                if (hasImage || hasTable) {
                    val layout = cfg.mainBlockLayout
                    val mainW = pw - 2f * margin
                    val imgPct = cfg.mainImageWidthPercent / 100f
                    val tableH = ((cfg.tableRows + 1) * 24f).coerceIn(90f, 210f)
                    val sideImageW = mainW * imgPct
                    val sideImageH = if (hasImage) naturalHeight(folder.reportMainImagePath, sideImageW, 220f) else 0f
                    val blockH = when {
                        hasImage && hasTable && (layout == MainBlockLayout.IMAGE_LEFT_TABLE || layout == MainBlockLayout.IMAGE_RIGHT_TABLE) -> max(sideImageH, tableH)
                        hasImage && hasTable -> {
                            val imageW = mainW * imgPct
                            naturalHeight(folder.reportMainImagePath, imageW, 220f) + 10f + tableH
                        }
                        hasImage -> naturalHeight(folder.reportMainImagePath, sideImageW, 240f)
                        else -> tableH
                    }

                    ensure(blockH + 14f)
                    when {
                        hasImage && hasTable && layout == MainBlockLayout.IMAGE_LEFT_TABLE -> {
                            val imageW = sideImageW
                            val tableW = mainW - imageW - 10f
                            val imageH = naturalHeight(folder.reportMainImagePath, imageW, blockH)
                            drawImageFit(canvas, folder.reportMainImagePath, margin, y + (blockH - imageH) / 2f, imageW, imageH)
                            drawTable(canvas, folder, cfg, margin + imageW + 10f, y + (blockH - tableH) / 2f, tableW, tableH, body, bold, border)
                        }
                        hasImage && hasTable && layout == MainBlockLayout.IMAGE_RIGHT_TABLE -> {
                            val imageW = sideImageW
                            val tableW = mainW - imageW - 10f
                            drawTable(canvas, folder, cfg, margin, y + (blockH - tableH) / 2f, tableW, tableH, body, bold, border)
                            val imageH = naturalHeight(folder.reportMainImagePath, imageW, blockH)
                            drawImageFit(canvas, folder.reportMainImagePath, margin + tableW + 10f, y + (blockH - imageH) / 2f, imageW, imageH)
                        }
                        hasImage && hasTable && layout == MainBlockLayout.IMAGE_ABOVE_TABLE -> {
                            val imageW = mainW * imgPct
                            val imageH = naturalHeight(folder.reportMainImagePath, imageW, 220f)
                            drawImageFit(canvas, folder.reportMainImagePath, margin + (mainW - imageW) / 2f, y, imageW, imageH)
                            drawTable(canvas, folder, cfg, margin, y + imageH + 10f, mainW, tableH, body, bold, border)
                        }
                        hasImage && hasTable -> {
                            drawTable(canvas, folder, cfg, margin, y, mainW, tableH, body, bold, border)
                            val imageW = mainW * imgPct
                            val imageH = naturalHeight(folder.reportMainImagePath, imageW, 220f)
                            drawImageFit(canvas, folder.reportMainImagePath, margin + (mainW - imageW) / 2f, y + tableH + 10f, imageW, imageH)
                        }
                        hasImage -> {
                            val imageW = sideImageW
                            val imageH = naturalHeight(folder.reportMainImagePath, imageW, 240f)
                            drawImageFit(canvas, folder.reportMainImagePath, margin + (mainW - imageW) / 2f, y, imageW, imageH)
                        }
                        hasTable -> {
                            val tableW = mainW * .84f
                            drawTable(canvas, folder, cfg, margin + (mainW - tableW) / 2f, y, tableW, tableH, body, bold, border)
                        }
                    }
                    y += blockH + 10f
                }

                if (hasSections && cfg.sectionPlacement == SectionPlacement.BELOW_MAIN_BLOCK) {
                    cfg.sections.forEach { section ->
                        val sectionH = 22f + section.lines.size * 27f
                        ensure(sectionH + 8f)
                        y = drawSectionBlock(canvas, section, folder.reportSectionValues, margin, y, pw - 2f * margin, body, bold, border)
                        y += 7f
                    }
                }

                if (folder.generalObs.isNotBlank()) {
                    ensure(28f)
                    canvas.drawText("Observación general", margin + 5f, y + 12f, bold)
                    y += 19f
                    folder.generalObs.trim().chunked(95).take(40).forEach { observationLine ->
                        ensure(13f)
                        drawFittedLeft(canvas, observationLine, margin + 5f,
                            y + 9f, pw - 2f * margin - 10f, body, 6.8f)
                        y += 13f
                    }
                    y += 7f
                }

                if (hasPhotos) {
                    val cols = cfg.evidenceColumns.coerceIn(1, 4)
                    val gap = 8f
                    val availableW = pw - 2f * margin
                    val cellW = (availableW - gap * (cols - 1)) / cols
                    val imageH = min(120f, max(58f, cellW * .68f))
                    val cellH = imageH + 28f

                    folder.photos.chunked(cols).forEach { row ->
                        ensure(cellH + 8f)
                        val rowW = row.size * cellW + max(0, row.size - 1) * gap
                        val startX = margin + (availableW - rowW) / 2f
                        row.forEachIndexed { index, photo ->
                            val x = startX + index * (cellW + gap)
                            if (File(photo.path).exists()) {
                                drawImage(canvas, photo.path, x, y, cellW, imageH, border)
                            } else {
                                canvas.drawRect(x, y, x + cellW, y + imageH, border)
                            }
                            drawFittedLeft(canvas, photo.name, x, y + imageH + 10f, cellW, bold, 5.2f)
                            drawFittedLeft(
                                canvas,
                                photo.observation.replace("\n", " ").ifBlank { "Sin observación" },
                                x,
                                y + imageH + 21f,
                                cellW,
                                body,
                                4.8f
                            )
                        }
                        y += cellH
                    }
                }
                y += 8f
            }

            folder.children.forEach { child -> drawFolder(child, "$route / ${child.name}") }
        }

        drawFolder(root, root.name)
        if (cfg.signatures.isNotEmpty()) {
            val needed = cfg.signatures.size * 56f + 8f
            ensure(needed)
            drawSignatureBlock(canvas, cfg.signatures, margin, y + 8f, pw - 2f * margin, body, border)
            y += needed
        }
        doc.finishPage(page)
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
        return out
    }

    private fun drawSectionBlock(
        canvas: android.graphics.Canvas,
        section: SectionDefinition,
        values: Map<String, String>,
        x: Float,
        y: Float,
        width: Float,
        body: Paint,
        bold: Paint,
        border: Paint
    ): Float {
        var cy = y
        canvas.drawRect(x, cy, x + width, cy + 22f, border)
        drawCellText(canvas, section.title, x, cy, width, 22f, bold, 5.2f)
        cy += 22f
        section.lines.forEach { raw ->
            val line = raw.normalized()
            val bounds = listOf(0) + line.separators + listOf(100)
            line.cells.forEachIndexed { index, cell ->
                val left = x + width * bounds[index] / 100f
                val right = x + width * bounds[index + 1] / 100f
                canvas.drawRect(left, cy, right, cy + 27f, border)
                val value = if (cell.mode == SectionCellMode.LABEL) cell.text else values[cell.id].orEmpty().ifBlank { cell.text }
                drawCellText(canvas, value, left, cy, right - left, 27f, if (cell.mode == SectionCellMode.LABEL) bold else body, 4.4f)
            }
            cy += 27f
        }
        return cy
    }

    private fun drawSignatureBlock(
        canvas: android.graphics.Canvas,
        signatures: List<SignatureDefinition>,
        x: Float,
        y: Float,
        width: Float,
        body: Paint,
        border: Paint
    ) {
        var cy = y
        signatures.take(3).forEach { signature ->
            val center = when (signature.alignment) {
                SignatureAlignment.LEFT -> x + width * .18f
                SignatureAlignment.CENTER -> x + width * .50f
                SignatureAlignment.RIGHT -> x + width * .82f
            }
            val lineW = min(190f, width * .30f)
            canvas.drawLine(center - lineW / 2f, cy + 25f, center + lineW / 2f, cy + 25f, border)
            val p = Paint(body).apply { textAlign = Paint.Align.CENTER; textSize = 8f }
            canvas.drawText(signature.name, center, cy + 41f, p)
            cy += 56f
        }
    }

    private fun drawHeader(
        canvas: android.graphics.Canvas,
        config: CustomReportConfig,
        left: Float,
        right: Float,
        top: Float,
        height: Float,
        titlePaint: Paint,
        subtitlePaint: Paint,
        bodyPaint: Paint,
        border: Paint
    ) {
        val sideW = (right - left) * .18f
        val centerL = left + sideW
        val centerR = right - sideW
        val centerW = centerR - centerL
        val titleSplitY = top + height * .66f

        canvas.drawRect(left, top, right, top + height, border)
        canvas.drawLine(centerL, top, centerL, top + height, border)
        canvas.drawLine(centerR, top, centerR, top + height, border)
        canvas.drawLine(centerL, titleSplitY, centerR, titleSplitY, border)

        // Cada celda del encabezado tiene clipping propio. Aunque una fuente o un texto
        // se comporte distinto entre dispositivos, nunca puede invadir la imagen lateral.
        canvas.save()
        canvas.clipRect(centerL + 2f, top + 2f, centerR - 2f, titleSplitY - 1f)
        drawCenteredWrapped(
            canvas = canvas,
            text = config.title,
            centerX = (centerL + centerR) / 2f,
            centerY = top + (titleSplitY - top) / 2f,
            maxWidth = centerW - 16f,
            maxHeight = titleSplitY - top - 8f,
            paint = titlePaint,
            minTextSize = 7f,
            maxLines = 3
        )
        canvas.restore()

        canvas.save()
        canvas.clipRect(centerL + 2f, titleSplitY + 1f, centerR - 2f, top + height - 2f)
        drawCenteredWrapped(
            canvas = canvas,
            text = config.subtitle,
            centerX = (centerL + centerR) / 2f,
            centerY = titleSplitY + (top + height - titleSplitY) / 2f,
            maxWidth = centerW - 16f,
            maxHeight = top + height - titleSplitY - 6f,
            paint = subtitlePaint,
            minTextSize = 5f,
            maxLines = 2
        )
        canvas.restore()

        canvas.save()
        canvas.clipRect(left + 1f, top + 1f, centerL - 1f, top + height - 1f)
        drawSlot(canvas, config.leftHeader, left, top, sideW, height, bodyPaint)
        canvas.restore()

        canvas.save()
        canvas.clipRect(centerR + 1f, top + 1f, right - 1f, top + height - 1f)
        drawSlot(canvas, config.rightHeader, centerR, top, sideW, height, bodyPaint)
        canvas.restore()
    }

    private fun drawSlot(
        canvas: android.graphics.Canvas,
        slot: ReportHeaderSlot,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        paint: Paint
    ) {
        when (slot.type) {
            ReportSlotType.EMPTY -> Unit
            ReportSlotType.IMAGE -> if (slot.imagePath.isNotBlank() && File(slot.imagePath).exists()) {
                drawImageFit(canvas, slot.imagePath, x + 4f, y + 4f, width - 8f, height - 8f)
            }
            ReportSlotType.TEXT -> drawCenteredWrapped(
                canvas = canvas,
                text = slot.text.take(70),
                centerX = x + width / 2f,
                centerY = y + height / 2f,
                maxWidth = width - 10f,
                maxHeight = height - 10f,
                paint = paint,
                minTextSize = 5.2f,
                maxLines = 5
            )
        }
    }

    private fun naturalHeight(path: String, width: Float, maxHeight: Float): Float {
        val file = File(path)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return min(maxHeight, width * .66f)
        val rotation = ImageOrientationUtils.exifRotationDegrees(file)
        val visualWidth = if (rotation == 90f || rotation == 270f) options.outHeight else options.outWidth
        val visualHeight = if (rotation == 90f || rotation == 270f) options.outWidth else options.outHeight
        return (width * visualHeight.toFloat() / visualWidth.toFloat()).coerceIn(55f, maxHeight)
    }

    private fun drawImageFit(canvas: android.graphics.Canvas, path: String, x: Float, y: Float, width: Float, height: Float) {
        val file = File(path)
        if (!file.exists()) return
        val bitmap = decode(file, (width * 3).toInt().coerceAtLeast(240), (height * 3).toInt().coerceAtLeast(180)) ?: return
        val scale = min(width / bitmap.width.toFloat(), height / bitmap.height.toFloat())
        val drawW = bitmap.width * scale
        val drawH = bitmap.height * scale
        val dx = x + (width - drawW) / 2f
        val dy = y + (height - drawH) / 2f
        canvas.drawBitmap(bitmap, null, android.graphics.RectF(dx, dy, dx + drawW, dy + drawH), null)
        bitmap.recycle()
    }

    private fun drawImage(
        canvas: android.graphics.Canvas,
        path: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        border: Paint?
    ) {
        val file = File(path)
        if (!file.exists()) return
        val bitmap = decode(file, (width * 3).toInt().coerceAtLeast(300), (height * 3).toInt().coerceAtLeast(240)) ?: return
        val source = ExportUtilsCenterCrop.source(bitmap.width, bitmap.height, width / height)
        canvas.drawBitmap(bitmap, source, android.graphics.RectF(x, y, x + width, y + height), null)
        bitmap.recycle()
        border?.let { canvas.drawRect(x, y, x + width, y + height, it) }
    }

    private fun drawTable(
        canvas: android.graphics.Canvas,
        folder: Inspection,
        config: CustomReportConfig,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        bodyPaint: Paint,
        boldPaint: Paint,
        border: Paint
    ) {
        val rows = config.tableRows
        val cols = config.tableColumns
        val rowHeaderW = min(56f, width * .24f)
        val cellW = (width - rowHeaderW) / cols
        val rowH = height / (rows + 1)

        canvas.drawRect(x, y, x + width, y + height, border)
        for (row in 0..rows) canvas.drawLine(x, y + row * rowH, x + width, y + row * rowH, border)
        canvas.drawLine(x + rowHeaderW, y, x + rowHeaderW, y + height, border)
        for (column in 1 until cols) {
            canvas.drawLine(x + rowHeaderW + column * cellW, y, x + rowHeaderW + column * cellW, y + height, border)
        }

        drawCellText(canvas, "ITEM", x, y, rowHeaderW, rowH, boldPaint, 4.8f)
        config.columnHeaders.take(cols).forEachIndexed { index, text ->
            drawCellText(canvas, text, x + rowHeaderW + index * cellW, y, cellW, rowH, boldPaint, 4.5f)
        }
        for (row in 0 until rows) {
            drawCellText(
                canvas,
                config.rowHeaders.getOrElse(row) { "F${row + 1}" },
                x,
                y + (row + 1) * rowH,
                rowHeaderW,
                rowH,
                boldPaint,
                4.5f
            )
            for (column in 0 until cols) {
                drawCellText(
                    canvas,
                    folder.reportTableValues.getOrNull(row)?.getOrNull(column).orEmpty().replace("\n", " "),
                    x + rowHeaderW + column * cellW,
                    y + (row + 1) * rowH,
                    cellW,
                    rowH,
                    bodyPaint,
                    4.2f
                )
            }
        }
    }

    private fun drawCellText(
        canvas: android.graphics.Canvas,
        text: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        sourcePaint: Paint,
        minTextSize: Float
    ) {
        val paint = Paint(sourcePaint)
        paint.textAlign = Paint.Align.CENTER
        val maxWidth = (width - 7f).coerceAtLeast(5f)
        val original = paint.textSize
        var size = original
        val clean = text.replace("\n", " ").trim()
        while (size > minTextSize && paint.measureText(clean) > maxWidth) {
            size -= .4f
            paint.textSize = size
        }
        val shown = if (paint.measureText(clean) <= maxWidth) clean else ellipsize(clean, paint, maxWidth)
        val baseline = y + height / 2f - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(shown, x + width / 2f, baseline, paint)
    }

    private fun drawFittedLeft(
        canvas: android.graphics.Canvas,
        text: String,
        x: Float,
        baseline: Float,
        maxWidth: Float,
        sourcePaint: Paint,
        minTextSize: Float
    ) {
        val paint = Paint(sourcePaint)
        paint.textAlign = Paint.Align.LEFT
        var size = paint.textSize
        while (size > minTextSize && paint.measureText(text) > maxWidth) {
            size -= .35f
            paint.textSize = size
        }
        val shown = if (paint.measureText(text) <= maxWidth) text else ellipsize(text, paint, maxWidth)
        canvas.drawText(shown, x, baseline, paint)
    }

    private fun drawCenteredWrapped(
        canvas: android.graphics.Canvas,
        text: String,
        centerX: Float,
        centerY: Float,
        maxWidth: Float,
        maxHeight: Float,
        paint: Paint,
        minTextSize: Float,
        maxLines: Int
    ) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val work = Paint(paint).apply { textAlign = Paint.Align.CENTER }
        var size = work.textSize
        var lines: List<String>
        while (true) {
            work.textSize = size
            lines = wrapText(clean, work, maxWidth, maxLines)
            val lineHeight = work.textSize * 1.18f
            val fitsHeight = lines.size * lineHeight <= maxHeight
            val fitsWidth = lines.all { work.measureText(it) <= maxWidth + .5f }
            if ((fitsHeight && fitsWidth) || size <= minTextSize) break
            size = (size - .5f).coerceAtLeast(minTextSize)
        }
        val lineHeight = work.textSize * 1.18f
        val totalH = lines.size * lineHeight
        val firstBaseline = centerY - totalH / 2f + lineHeight * .78f
        lines.forEachIndexed { index, line ->
            canvas.drawText(ellipsize(line, work, maxWidth), centerX, firstBaseline + index * lineHeight, work)
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
        val hardLines = text.split("\n")
        val result = mutableListOf<String>()
        for (hard in hardLines) {
            val words = hard.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) {
                if (result.size < maxLines) result += ""
                continue
            }
            var current = ""
            for (word in words) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) {
                    current = candidate
                } else {
                    result += current
                    current = word
                    if (result.size >= maxLines) break
                }
            }
            if (result.size < maxLines && current.isNotEmpty()) result += current
            if (result.size >= maxLines) break
        }
        if (result.isEmpty()) result += text
        if (result.size == maxLines) {
            val joined = result.joinToString(" ")
            if (joined.length < text.replace("\n", " ").trim().length) {
                result[result.lastIndex] = ellipsize(result.last(), paint, maxWidth)
            }
        }
        return result.take(maxLines)
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end).trimEnd() + ellipsis) > maxWidth) end--
        return if (end <= 0) ellipsis else text.substring(0, end).trimEnd() + ellipsis
    }

    private fun decode(file: File, tw: Int, th: Int): android.graphics.Bitmap? =
        ImageOrientationUtils.decodeOrientedBitmap(file, tw, th)

    fun createDocx(context: Context, root: Inspection, cfg: CustomReportConfig, canonicalPdf: File = createPdf(context, root, cfg)): File {
        val out = File(File(context.cacheDir, "exports").apply { mkdirs() }, "${safe(root.name)}_REPORTE_PERSONALIZADO.docx")
        val pages = renderPdfPages(context, canonicalPdf, "docx_pages_${safe(root.name)}")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { z ->
            fun entry(name: String, text: String) { z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray()); z.closeEntry() }
            entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""")
            entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
            entry("word/_rels/document.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${pages.mapIndexed { i, _ -> "<Relationship Id=\"rId${i+1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/page${i+1}.png\"/>" }.joinToString("")}</Relationships>""")
            val body = StringBuilder()
            pages.forEachIndexed { i, file ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(file.absolutePath, it) }
                val widthEmu = 6000000L
                val heightEmu = if (bounds.outWidth > 0) (widthEmu * bounds.outHeight / bounds.outWidth).coerceAtMost(8500000L) else 8200000L
                body.append("""<w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:drawing><wp:inline xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"><wp:extent cx="$widthEmu" cy="$heightEmu"/><wp:docPr id="${i+1}" name="Página ${i+1}"/><a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:nvPicPr><pic:cNvPr id="${i+1}" name="Página ${i+1}"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" r:embed="rId${i+1}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$widthEmu" cy="$heightEmu"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>""")
                if (i < pages.lastIndex) body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
            }
            entry("word/document.xml", """<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="360" w:right="360" w:bottom="360" w:left="360"/></w:sectPr></w:body></w:document>""")
            pages.forEachIndexed { i, file -> addBinary(z, file, "word/media/page${i+1}.png") }
        }
        return out
    }

    fun createXlsx(context: Context, root: Inspection, cfg: CustomReportConfig, canonicalPdf: File = createPdf(context, root, cfg)): File {
        val out = File(File(context.cacheDir, "exports").apply { mkdirs() }, "${safe(root.name)}_REPORTE_PERSONALIZADO.xlsx")
        val pages = renderPdfPages(context, canonicalPdf, "xlsx_pages_${safe(root.name)}")
        val dataRows = mutableListOf<List<String>>()
        dataRows += listOf(cfg.title)
        dataRows += listOf(cfg.subtitle)
        dataRows += listOf("")
        fun walk(folder: Inspection, route: String) {
            dataRows += listOf("RUTA", route)
            if (cfg.sections.isNotEmpty()) {
                cfg.sections.forEach { section ->
                    dataRows += listOf(section.title)
                    section.lines.forEach { line ->
                        dataRows += line.cells.map { cell -> if (cell.mode == SectionCellMode.LABEL) cell.text else folder.reportSectionValues[cell.id].orEmpty() }
                    }
                }
            }
            if (folder.reportTableEnabled) {
                dataRows += (listOf("ITEM") + cfg.columnHeaders)
                for (r in 0 until cfg.tableRows) dataRows += (listOf(cfg.rowHeaders.getOrElse(r) { "F${r+1}" }) + List(cfg.tableColumns) { c -> folder.reportTableValues.getOrNull(r)?.getOrNull(c).orEmpty() })
            }
            if (folder.photos.isNotEmpty()) {
                dataRows += listOf("EVIDENCIAS")
                folder.photos.forEach { dataRows += listOf(it.name, it.observation, it.path) }
            }
            dataRows += listOf("")
            folder.children.forEach { walk(it, "$route / ${it.name}") }
        }
        walk(root, root.name)
        if (cfg.signatures.isNotEmpty()) {
            dataRows += listOf("")
            cfg.signatures.forEach { signature -> dataRows += listOf("____________________", signature.name, signature.alignment.name) }
        }

        ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { z ->
            fun e(name: String, text: String) { z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray()); z.closeEntry() }
            e("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/drawings/drawing1.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/></Types>""")
            e("_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            e("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Reporte" sheetId="1" r:id="rId1"/><sheet name="Datos" sheetId="2" r:id="rId2"/></sheets></workbook>""")
            e("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/></Relationships>""")
            e("xl/worksheets/sheet1.xml", """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheetFormatPr defaultRowHeight="15"/><sheetData/>${if (pages.isNotEmpty()) "<drawing r:id=\"rId1\"/>" else ""}</worksheet>""")
            e("xl/worksheets/_rels/sheet1.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing1.xml"/></Relationships>""")
            val sheet2 = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
            dataRows.forEachIndexed { ri, row -> sheet2.append("<row r=\"${ri+1}\">"); row.forEachIndexed { ci, v -> sheet2.append("<c r=\"${col(ci)}${ri+1}\" t=\"inlineStr\"><is><t>").append(xml(v)).append("</t></is></c>") }; sheet2.append("</row>") }
            sheet2.append("</sheetData></worksheet>")
            e("xl/worksheets/sheet2.xml", sheet2.toString())
            val drawing = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><xdr:wsDr xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
            pages.forEachIndexed { i, _ ->
                val fromRow = i * 58
                val toRow = fromRow + 55
                drawing.append("""<xdr:twoCellAnchor editAs="oneCell"><xdr:from><xdr:col>0</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>$fromRow</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from><xdr:to><xdr:col>10</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>$toRow</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:to><xdr:pic><xdr:nvPicPr><xdr:cNvPr id="${i+1}" name="Página ${i+1}"/><xdr:cNvPicPr/></xdr:nvPicPr><xdr:blipFill><a:blip r:embed="rId${i+1}"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill><xdr:spPr><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic><xdr:clientData/></xdr:twoCellAnchor>""")
            }
            drawing.append("</xdr:wsDr>")
            e("xl/drawings/drawing1.xml", drawing.toString())
            e("xl/drawings/_rels/drawing1.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${pages.mapIndexed { i, _ -> "<Relationship Id=\"rId${i+1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"../media/page${i+1}.png\"/>" }.joinToString("")}</Relationships>""")
            pages.forEachIndexed { i, file -> addBinary(z, file, "xl/media/page${i+1}.png") }
        }
        return out
    }

    private fun renderPdfPages(context: Context, pdf: File, prefix: String): List<File> {
        val dir = File(context.cacheDir, "report_page_renders/$prefix").apply { deleteRecursively(); mkdirs() }
        val pfd = android.os.ParcelFileDescriptor.open(pdf, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = android.graphics.pdf.PdfRenderer(pfd)
        val files = mutableListOf<File>()
        try {
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val width = (page.width * 1.35f).toInt().coerceAtLeast(800)
                val height = (page.height * 1.35f).toInt().coerceAtLeast(1100)
                val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val file = File(dir, "page_${i+1}.png")
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, it) }
                bitmap.recycle(); page.close(); files += file
            }
        } finally {
            renderer.close(); pfd.close()
        }
        return files
    }

    fun createStructureManifest(root:Inspection):String {
        fun node(f:Inspection):JSONObject=JSONObject().apply {
            put("id", f.id); put("name", f.name); put("created", f.created); put("ot", f.ot)
            put("generalObs", f.generalObs); put("reportSectionTitles", JSONObject(f.reportSectionTitles)); put("collapsed", f.collapsed)
            put("viewMode", f.viewMode.name); put("allowPhotos", f.allowPhotos); put("allowSubfolders", f.allowSubfolders)
            put("reportTableEnabled", f.reportTableEnabled)
            put("reportTableValues", org.json.JSONArray().apply { f.reportTableValues.forEach { put(org.json.JSONArray(it)) } })
            put("reportSectionValues", JSONObject(f.reportSectionValues))
            put("reportMainImage", File(f.reportMainImagePath).takeIf { f.reportMainImagePath.isNotBlank() && it.exists() }?.name ?: "")
            put("photos", org.json.JSONArray().apply {
                f.photos.forEachIndexed { index, p ->
                    put(JSONObject().put("order", index).put("name", p.name).put("observation", p.observation))
                }
            })
            put("children", org.json.JSONArray().apply { f.children.forEachIndexed { index, child -> put(node(child).put("order", index)) } })
        }
        return JSONObject()
            .put("format","inspeccion-photos-folder-structure")
            .put("version",3)
            .put("root",node(root))
            .toString(2)
    }

    private fun collectImages(root:Inspection,cfg:CustomReportConfig):List<File>{val out=linkedMapOf<String,File>(); fun add(p:String){if(p.isNotBlank()){val f=File(p);if(f.exists()&&f.length()>0)out[f.absolutePath]=f}};add(cfg.leftHeader.imagePath);add(cfg.rightHeader.imagePath);fun walk(f:Inspection){add(f.reportMainImagePath);f.photos.forEach{add(it.path)};f.children.forEach(::walk)};walk(root);return out.values.toList()}
    private fun addBinary(z:ZipOutputStream,f:File,name:String){z.putNextEntry(ZipEntry(name));f.inputStream().use{it.copyTo(z,256*1024)};z.closeEntry()}
    private fun safe(v:String)=v.replace(Regex("[^A-Za-z0-9._-]"),"_")
    private fun xml(v:String)=v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
    private fun col(i:Int):String{var n=i+1;val s=StringBuilder();while(n>0){val r=(n-1)%26;s.append(('A'.code+r).toChar());n=(n-1)/26};return s.reverse().toString()}
}

private object ExportUtilsCenterCrop {
    fun source(width:Int,height:Int,targetAspect:Float):android.graphics.Rect { val a=width.toFloat()/height; return if(a>targetAspect){val cw=(height*targetAspect).toInt().coerceAtMost(width);val l=(width-cw)/2;android.graphics.Rect(l,0,l+cw,height)} else {val ch=(width/targetAspect).toInt().coerceAtMost(height);val t=(height-ch)/2;android.graphics.Rect(0,t,width,t+ch)} }
}
