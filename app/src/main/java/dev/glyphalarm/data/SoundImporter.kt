package dev.glyphalarm.data

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import dev.glyphalarm.glyph.GlyphtoneParser
import java.io.File
import java.util.UUID

data class ImportedSound(val path: String, val name: String, val uri: String)

/** Copies a picked sound into private storage so the alarm never depends on a file permission or a moved file. */
object SoundImporter {
    private const val TAG = "SoundImporter"
    private const val MAX_BYTES = 40L * 1024 * 1024
    private const val PEEK = 512 * 1024

    fun import(ctx: Context, uri: Uri): ImportedSound? = runCatching {
        val dir = File(ctx.filesDir, "sounds").apply { mkdirs() }
        val fileName = column(ctx, uri, OpenableColumns.DISPLAY_NAME)
        val ext = fileName?.substringAfterLast('.', "")?.take(5)?.lowercase()?.takeIf { it.isNotBlank() && it.all(Char::isLetterOrDigit) } ?: "ogg"
        val out = File(dir, "${UUID.randomUUID()}.$ext")
        ctx.contentResolver.openInputStream(uri)!!.use { input ->
            out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) { out.delete(); return null }
                    o.write(buf, 0, n)
                }
            }
        }
        ImportedSound(out.absolutePath, displayName(ctx, uri, fileName, out), uri.toString())
    }.getOrNull()

    /**
     * The name the phone's own sound list shows for this sound. Sources, best first: the provider's title column,
     * the system's ringtone title, then the tags inside the file. Names that are only a file name ("output_1752…")
     * are skipped, as are artist/pack tags, which are not the melody's name.
     */
    private fun displayName(ctx: Context, uri: Uri, fileName: String?, copy: File): String {
        val stem = fileName?.substringBeforeLast('.')
        val columns = allColumns(ctx, uri)
        val tags = runCatching { GlyphtoneParser.tags(copy.inputStream().use { it.readNBytes(PEEK) }) }.getOrNull().orEmpty()
        Log.i(TAG, "uri=$uri file=$fileName columns=$columns tags=${tags.filterKeys { it != "AUTHOR" }}")

        val skipTags = setOf("AUTHOR", "ALBUM", "ARTIST", "COMPOSER", "ENCODER", "CUSTOM2", "DATE", "GENRE", "TRACKNUMBER", "COMMENT")
        val candidates = sequence {
            yield(columns["title"] ?: columns[MediaStore.MediaColumns.TITLE])
            yield(runCatching { RingtoneManager.getRingtone(ctx, uri)?.getTitle(ctx) }.getOrNull())
            yield(tags["TITLE"])
            tags.forEach { (k, v) -> if (k !in skipTags && k != "TITLE") yield(v) }
            yield(columns["_display_name"]?.substringBeforeLast('.'))
            yield(stem)
        }
        return candidates.mapNotNull { it?.trim() }.firstOrNull { usable(it, uri, stem) } ?: tr("Мелодия", "Melody")
    }

    private val FILE_LIKE = Regex("^(output|audio|sound|record|composition|file)[_\\- ]?\\d+$", RegexOption.IGNORE_CASE)

    private fun usable(name: String, uri: Uri, stem: String?): Boolean =
        name.isNotBlank() && !name.startsWith("<") && name != uri.lastPathSegment && !name.all(Char::isDigit) &&
            !FILE_LIKE.matches(name) && name.length <= 80 && !name.contains("=") && !(name.startsWith("[") && name.endsWith("]"))

    private fun allColumns(ctx: Context, uri: Uri): Map<String, String> = runCatching {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use emptyMap()
            buildMap {
                for (i in 0 until c.columnCount) {
                    val v = runCatching { c.getString(i) }.getOrNull() ?: continue
                    if (v.isNotBlank() && v.length < 200) put(c.getColumnName(i).lowercase(), v)
                }
            }
        }
    }.getOrNull().orEmpty()

    private fun column(ctx: Context, uri: Uri, column: String): String? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    /** Removes the private copy when an alarm stops using it. */
    fun discard(path: String?) {
        if (path != null) runCatching { File(path).delete() }
    }

    /** Deletes copies nothing refers to any more (left behind by a cancelled edit or a killed app). */
    fun sweep(ctx: Context, keep: Set<String>) {
        runCatching { File(ctx.filesDir, "sounds").listFiles()?.forEach { if (it.absolutePath !in keep) it.delete() } }
    }
}
