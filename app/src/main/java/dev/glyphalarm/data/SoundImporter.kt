package dev.glyphalarm.data

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
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
        val name = displayName(ctx, uri)
        val ext = name.substringAfterLast('.', "ogg").take(5).lowercase().ifBlank { "ogg" }
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
        // a Glyph Composer file carries the name the author gave it: prefer that over the file name
        val tagged = runCatching { GlyphtoneParser.parse(out.inputStream().use { it.readNBytes(PEEK) })?.title }.getOrNull()
        ImportedSound(out.absolutePath, (tagged?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.')).trim())
    }.getOrNull()

    private fun displayName(ctx: Context, uri: Uri): String {
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0)?.let { n -> if (n.isNotBlank()) return n }
            }
        }
        runCatching { RingtoneManager.getRingtone(ctx, uri)?.getTitle(ctx)?.let { if (it.isNotBlank()) return it } }
        return "SOUND"
    }

    /** Removes the private copy when an alarm stops using it. */
    fun discard(path: String?) {
        if (path != null) runCatching { File(path).delete() }
    }
}
