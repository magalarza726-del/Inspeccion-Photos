package ec.espol.inspeccionphotos

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FormSubmissionExporter {
    fun export(context: Context, submission: FormSubmission): List<File> {
        val formats = submission.template.exportFormats.ifEmpty { setOf(FormExportFormat.PDF) }
        return buildList {
            if (FormExportFormat.PDF in formats) add(createPdf(context, submission))
            if (FormExportFormat.WORD in formats) add(createDocx(context, submission))
            if (FormExportFormat.EXCEL in formats) add(createXlsx(context, submission))
        }
    }

    fun share(context: Context, files: List<File>) {
        if (files.isEmpty()) return
        val uris = ArrayList(files.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) })
        val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply {
            type = mime(files.first())
            putExtra(Intent.EXTRA_STREAM, uris.first())
        } else Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/octet-stream"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Compartir formulario"))
    }

    fun createPdf(context: Context, submission: FormSubmission): File {
        val s = submission.normalized(); val t = s.template
        val reportColumns = t.columns.withIndex().filter { !it.value.hiddenInReport }
        val landscape = reportColumns.size > 5
        val pageW = if (landscape) 842 else 595
        val pageH = if (landscape) 595 else 842
        val margin = 28f
        val out = File(exportDir(context), "${safe(t.name)}_${shortId(s.id)}.pdf")
        val doc = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(95, 105, 118); style = Paint.Style.STROKE; strokeWidth = 1f }
        val headerH = 82f
        val rowH = 26f
        val tableW = pageW - 2 * margin
        val itemW = 38f
        val colW = if (reportColumns.isEmpty()) 0f else (tableW - itemW) / reportColumns.size
        var pageNumber = 0
        var sourceRow = 0
        var firstPage = true
        var finished = false

        while (!finished) {
            pageNumber++
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNumber).create())
            val c = page.canvas
            c.drawColor(Color.WHITE)
            drawHeader(c, t, margin, margin, tableW, headerH, paint, border)
            var y = margin + headerH + 14f

            if (firstPage) {
                paint.color = Color.rgb(25, 35, 48); paint.textSize = 11f; paint.isFakeBoldText = true
                c.drawText(t.name, margin, y, paint)
                paint.isFakeBoldText = false; paint.textSize = 8.5f; paint.color = Color.DKGRAY
                c.drawText(if (s.completed) "COMPLETADO" else "BORRADOR", margin, y + 14f, paint)
                y += 34f
                t.sections.forEach { section ->
                    val required = 24f + section.lines.size * 28f
                    if (y + required < pageH - margin - 70f) {
                        y = drawSection(c, section, s.sectionValues, margin, y, tableW, paint, border)
                        y += 8f
                    }
                }
                firstPage = false
            }

            if (reportColumns.isNotEmpty() && sourceRow < s.cells.size) {
                drawTableHeader(c, reportColumns, margin, y, itemW, colW, rowH, paint, border)
                y += rowH
                while (sourceRow < s.cells.size && y + rowH < pageH - margin - 70f) {
                    drawFormRow(c, sourceRow, s.cells[sourceRow], reportColumns, margin, y, itemW, colW, rowH, paint, border)
                    sourceRow++
                    y += rowH
                }
            } else if (reportColumns.isEmpty() && sourceRow < s.cells.size) {
                sourceRow = s.cells.size
            }

            val rowsDone = sourceRow >= s.cells.size
            if (rowsDone && t.signatures.isNotEmpty()) {
                val needed = t.signatures.size * 54f + 10f
                if (y + needed < pageH - margin - 22f) {
                    drawSignatures(c, t.signatures, margin, y + 10f, tableW, paint, border)
                    finished = true
                } else {
                    paint.textSize = 7f; paint.color = Color.GRAY
                    c.drawText("Inspeccion Photos · $pageNumber", margin, pageH - 12f, paint)
                    doc.finishPage(page)
                    pageNumber++
                    val signaturePage = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNumber).create())
                    signaturePage.canvas.drawColor(Color.WHITE)
                    drawHeader(signaturePage.canvas, t, margin, margin, tableW, headerH, paint, border)
                    drawSignatures(signaturePage.canvas, t.signatures, margin, margin + headerH + 35f, tableW, paint, border)
                    paint.textSize = 7f; paint.color = Color.GRAY
                    signaturePage.canvas.drawText("Inspeccion Photos · $pageNumber", margin, pageH - 12f, paint)
                    doc.finishPage(signaturePage)
                    break
                }
            } else if (rowsDone) {
                finished = true
            }

            paint.textSize = 7f; paint.color = Color.GRAY
            c.drawText("Inspeccion Photos · $pageNumber", margin, pageH - 12f, paint)
            doc.finishPage(page)
        }
        FileOutputStream(out).use(doc::writeTo); doc.close(); return out
    }

    private fun drawSection(c: android.graphics.Canvas, section: SectionDefinition, values: Map<String, String>, x: Float, y: Float, w: Float, p: Paint, b: Paint): Float {
        var cy = y
        cell(c, section.title, x, cy, w, 24f, p, b, true)
        cy += 24f
        section.lines.forEach { raw ->
            val line = raw.normalized()
            val bounds = listOf(0) + line.separators + listOf(100)
            line.cells.forEachIndexed { index, definition ->
                val left = x + w * bounds[index] / 100f
                val right = x + w * bounds[index + 1] / 100f
                val text = if (definition.mode == SectionCellMode.LABEL) definition.text else values[definition.id].orEmpty().ifBlank { definition.text }
                cell(c, text, left, cy, right - left, 28f, p, b, definition.mode == SectionCellMode.LABEL)
            }
            cy += 28f
        }
        return cy
    }

    private fun drawSignatures(c: android.graphics.Canvas, signatures: List<SignatureDefinition>, x: Float, y: Float, w: Float, p: Paint, b: Paint) {
        var cy = y
        signatures.take(3).forEach { signature ->
            val center = when (signature.alignment) {
                SignatureAlignment.LEFT -> x + w * .18f
                SignatureAlignment.CENTER -> x + w * .50f
                SignatureAlignment.RIGHT -> x + w * .82f
            }
            val lineW = (w * .28f).coerceAtMost(190f)
            c.drawLine(center - lineW / 2f, cy + 25f, center + lineW / 2f, cy + 25f, b)
            p.color = Color.rgb(25,35,48); p.textSize = 8f; p.textAlign = Paint.Align.CENTER
            c.drawText(signature.name, center, cy + 40f, p)
            p.textAlign = Paint.Align.LEFT
            cy += 54f
        }
    }

    private fun drawHeader(c: android.graphics.Canvas, t: FormTemplate, x: Float, y: Float, w: Float, h: Float, p: Paint, b: Paint) {
        val side = w * .18f; val center = w - side * 2
        c.drawRect(x, y, x + w, y + h, b); c.drawLine(x + side, y, x + side, y + h, b); c.drawLine(x + side + center, y, x + side + center, y + h, b)
        val split = y + h * .68f; c.drawLine(x + side, split, x + side + center, split, b)
        drawSlot(c, t.leftHeader, RectF(x, y, x + side, y + h), p)
        drawSlot(c, t.rightHeader, RectF(x + side + center, y, x + w, y + h), p)
        drawCentered(c, t.title, RectF(x + side, y, x + side + center, split), p, 13f, true, 3)
        drawCentered(c, t.subtitle, RectF(x + side, split, x + side + center, y + h), p, 8f, false, 2)
    }

    private fun drawSlot(c: android.graphics.Canvas, slot: ReportHeaderSlot, r: RectF, p: Paint) {
        when (slot.type) {
            ReportSlotType.EMPTY -> Unit
            ReportSlotType.TEXT -> drawCentered(c, slot.text, r, p, 8f, false, 5)
            ReportSlotType.IMAGE -> {
                val f = File(slot.imagePath); val bm = ImageOrientationUtils.decodeOrientedBitmap(f, 1600)
                if (bm != null) { val pad = 4f; c.drawBitmap(bm, null, RectF(r.left+pad,r.top+pad,r.right-pad,r.bottom-pad), p); bm.recycle() }
            }
        }
    }

    private fun drawCentered(c: android.graphics.Canvas, text: String, r: RectF, p: Paint, size: Float, bold: Boolean, maxLines: Int) {
        p.textSize = size; p.color = Color.rgb(25,35,48); p.textAlign = Paint.Align.CENTER; p.isFakeBoldText = bold
        val words = text.replace("\n", " \n ").split(" "); val lines = mutableListOf<String>(); var line = ""
        fun flush(){ if(line.isNotBlank()){ lines += line.trim(); line="" } }
        for (word in words) {
            if (word == "\n") { flush(); continue }
            val test = if (line.isBlank()) word else "$line $word"
            if (p.measureText(test) <= r.width()-8f) line=test else { flush(); line=word }
        }; flush()
        val shown = lines.take(maxLines); val lh = size * 1.25f; var yy = r.centerY() - (shown.size-1)*lh/2f - (p.ascent()+p.descent())/2f
        shown.forEach { c.drawText(it, r.centerX(), yy, p); yy += lh }
        p.textAlign = Paint.Align.LEFT; p.isFakeBoldText = false
    }

    private fun drawTableHeader(c: android.graphics.Canvas, columns: List<IndexedValue<FormColumnDefinition>>, x: Float, y: Float, itemW: Float, colW: Float, h: Float, p: Paint, b: Paint) {
        p.color = Color.rgb(232,239,247); p.style = Paint.Style.FILL; c.drawRect(x,y,x+itemW+columns.size*colW,y+h,p); p.style=Paint.Style.FILL
        cell(c,"ITEM",x,y,itemW,h,p,b,true)
        columns.forEachIndexed { i,indexed -> cell(c,indexed.value.name,x+itemW+i*colW,y,colW,h,p,b,true) }
    }
    private fun drawFormRow(c: android.graphics.Canvas, r: Int, values: List<String>, columns: List<IndexedValue<FormColumnDefinition>>, x: Float, y: Float, itemW: Float, colW: Float, h: Float, p: Paint, b: Paint) {
        cell(c,(r+1).toString(),x,y,itemW,h,p,b,false)
        columns.forEachIndexed { i,indexed -> cell(c,values.getOrNull(indexed.index).orEmpty(),x+itemW+i*colW,y,colW,h,p,b,false) }
    }
    private fun cell(c: android.graphics.Canvas, text: String, x: Float, y: Float, w: Float, h: Float, p: Paint, b: Paint, bold: Boolean) {
        c.drawRect(x,y,x+w,y+h,b); p.color=Color.rgb(25,35,48); p.textSize=7.5f; p.isFakeBoldText=bold
        val value = ellipsize(text,p,w-8f); c.drawText(value,x+4f,y+h/2f-(p.ascent()+p.descent())/2f,p); p.isFakeBoldText=false
    }
    private fun ellipsize(v:String,p:Paint,max:Float):String{ if(p.measureText(v)<=max)return v; var s=v; while(s.isNotEmpty()&&p.measureText("$s…")>max)s=s.dropLast(1); return "$s…" }

    fun createDocx(context: Context, submission: FormSubmission): File {
        val s = submission.normalized(); val t = s.template
        val out = File(exportDir(context), "${safe(t.name)}_${shortId(s.id)}.docx")
        val reportColumns = t.columns.withIndex().filter { !it.value.hiddenInReport }
        val rows = buildString {
            append(docxRow(listOf("ITEM") + reportColumns.map { it.value.name }, true))
            s.cells.forEachIndexed { i,row -> append(docxRow(listOf((i+1).toString()) + reportColumns.map { row.getOrNull(it.index).orEmpty() }, false)) }
        }
        val sectionXml = buildString {
            t.sections.forEach { section ->
                append("<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>").append(xml(section.title)).append("</w:t></w:r></w:p>")
                section.lines.forEach { line ->
                    append(docxRow(line.cells.map { cell -> if (cell.mode == SectionCellMode.LABEL) cell.text else s.sectionValues[cell.id].orEmpty() }, false))
                }
            }
        }
        val signatureXml = buildString {
            t.signatures.forEach { signature ->
                val jc = when(signature.alignment){ SignatureAlignment.LEFT -> "left"; SignatureAlignment.CENTER -> "center"; SignatureAlignment.RIGHT -> "right" }
                append("<w:p><w:pPr><w:jc w:val=\"").append(jc).append("\"/></w:pPr><w:r><w:t>____________________</w:t></w:r></w:p>")
                append("<w:p><w:pPr><w:jc w:val=\"").append(jc).append("\"/></w:pPr><w:r><w:t>").append(xml(signature.name)).append("</w:t></w:r></w:p>")
            }
        }
        val left = if(t.leftHeader.type==ReportSlotType.TEXT)t.leftHeader.text else ""
        val right = if(t.rightHeader.type==ReportSlotType.TEXT)t.rightHeader.text else ""
        val body = "<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:rPr><w:b/></w:rPr><w:t>${xml(t.title)}</w:t></w:r></w:p>"+
            "<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:t>${xml(t.subtitle)}</w:t></w:r></w:p>"+
            "<w:p><w:r><w:t>${xml(listOf(left,right).filter{it.isNotBlank()}.joinToString(" | "))}</w:t></w:r></w:p>"+
            sectionXml+
            "<w:tbl><w:tblPr><w:tblBorders><w:top w:val=\"single\" w:sz=\"4\"/><w:left w:val=\"single\" w:sz=\"4\"/><w:bottom w:val=\"single\" w:sz=\"4\"/><w:right w:val=\"single\" w:sz=\"4\"/><w:insideH w:val=\"single\" w:sz=\"4\"/><w:insideV w:val=\"single\" w:sz=\"4\"/></w:tblBorders></w:tblPr>$rows</w:tbl>"+signatureXml
        ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { z ->
            textEntry(z,"[Content_Types].xml","""<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""")
            textEntry(z,"_rels/.rels","""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
            textEntry(z,"word/document.xml","""<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgMar w:top="720" w:right="540" w:bottom="720" w:left="540"/></w:sectPr></w:body></w:document>""")
        }; return out
    }
    private fun docxRow(values: List<String>, bold:Boolean)=buildString{ append("<w:tr>"); values.forEach{v->append("<w:tc><w:p><w:r>");if(bold)append("<w:rPr><w:b/></w:rPr>");append("<w:t>").append(xml(v)).append("</w:t></w:r></w:p></w:tc>")};append("</w:tr>") }

    fun createXlsx(context: Context, submission: FormSubmission): File {
        val s=submission.normalized(); val t=s.template; val out=File(exportDir(context),"${safe(t.name)}_${shortId(s.id)}.xlsx")
        val reportColumns=t.columns.withIndex().filter{!it.value.hiddenInReport}
        val data=mutableListOf<List<String>>(); data+=listOf(t.title); data+=listOf(t.subtitle); data+=listOf("")
        t.sections.forEach { section ->
            data += listOf(section.title)
            section.lines.forEach { line -> data += line.cells.map { cell -> if(cell.mode==SectionCellMode.LABEL) cell.text else s.sectionValues[cell.id].orEmpty() } }
        }
        if(t.sections.isNotEmpty()) data+=listOf("")
        data+=listOf("ITEM")+reportColumns.map{it.value.name}; s.cells.forEachIndexed{i,row->data+=listOf((i+1).toString())+reportColumns.map{row.getOrNull(it.index).orEmpty()}}
        if(t.signatures.isNotEmpty()){ data+=listOf(""); t.signatures.forEach{ data+=listOf("____________________", it.name) } }
        val sheet=buildString{append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");data.forEachIndexed{ri,row->append("<row r=\"${ri+1}\">");row.forEachIndexed{ci,v->append("<c r=\"${col(ci)}${ri+1}\" t=\"inlineStr\"><is><t>").append(xml(v)).append("</t></is></c>")};append("</row>")};append("</sheetData></worksheet>")}
        ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { z ->
            textEntry(z,"[Content_Types].xml","""<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
            textEntry(z,"_rels/.rels","""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            textEntry(z,"xl/workbook.xml","""<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Formulario" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            textEntry(z,"xl/_rels/workbook.xml.rels","""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            textEntry(z,"xl/worksheets/sheet1.xml",sheet)
        };return out
    }

    private fun exportDir(context:Context)=File(context.cacheDir,"form_exports").apply{mkdirs()}
    private fun textEntry(z:ZipOutputStream,name:String,text:String){z.putNextEntry(ZipEntry(name));z.write(text.toByteArray());z.closeEntry()}
    private fun safe(v:String)=v.replace(Regex("[^A-Za-z0-9._-]"),"_").take(60).ifBlank{"Formulario"}
    private fun shortId(v:String)=v.take(8)
    private fun xml(v:String)=v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
    private fun col(i:Int):String{var n=i+1;val s=StringBuilder();while(n>0){val r=(n-1)%26;s.append(('A'.code+r).toChar());n=(n-1)/26};return s.reverse().toString()}
    private fun mime(f:File)=when(f.extension.lowercase()){ "pdf"->"application/pdf";"docx"->"application/vnd.openxmlformats-officedocument.wordprocessingml.document";"xlsx"->"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";else->"application/octet-stream"}
}
