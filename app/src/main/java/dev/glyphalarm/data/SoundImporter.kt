package dev.glyphalarm.data

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import dev.glyphalarm.glyph.GlyphtoneParser
import java.io.File
import java.util.UUID

data class ImportedSound(val path: String, val name: String)

/** Copies a picked sound into private storage so the alarm never depends on a file permission or a moved file. */
object SoundImporter {
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
        ImportedSound(out.absolutePath, displayName(ctx, uri, fileName, out))
    }.getOrNull()

    /**
     * The name the phone's own sound list shows for this sound. The tags inside the file are only a last resort:
     * they often hold the pack or the author instead of the melody.
     */
    private fun displayName(ctx: Context, uri: Uri, fileName: String?, copy: File): String {
        val candidates = sequence {
            yield(runCatching { RingtoneManager.getRingtone(ctx, uri)?.getTitle(ctx) }.getOrNull())
            yield(column(ctx, uri, MediaStore.MediaColumns.TITLE))
            yield(fileName?.substringBeforeLast('.'))
            yield(runCatching { GlyphtoneParser.parse(copy.inputStream().use { it.readNBytes(PEEK) })?.title }.getOrNull())
        }
        return candidates.mapNotNull { it?.trim() }.firstOrNull { usable(it, uri) } ?: "Мелодия"
    }

    private fun usable(name: String, uri: Uri): Boolean =
        name.isNotBlank() && !name.startsWith("<") && name != uri.lastPathSegment && !name.all(Char::isDigit)

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
