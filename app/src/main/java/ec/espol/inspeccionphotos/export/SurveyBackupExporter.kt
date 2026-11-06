package ec.espol.inspeccionphotos.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import ec.espol.inspeccionphotos.storage.SurveyRepository
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SurveyBackupExporter {
    fun createOptionalFullZip(context: Context, repository: SurveyRepository, surveyId: String): File {
        repository.writeManifest(surveyId)
        val source = repository.surveyDir(surveyId)
        val outDir = File(context.cacheDir, "survey_backups").apply { mkdirs() }
        val out = File(outDir, "${source.name}_manual_backup.zip")
        ZipOutputStream(FileOutputStream(out)).use { zip ->
            source.walkTopDown().filter { it.isFile }.forEach { file ->
                val relative = file.relativeTo(source).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry(relative))
                FileInputStream(file).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return out
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir backup manual"))
    }
}
