package dev.glyphalarm.glyph

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.zip.Inflater

/**
 * A light show recorded by the official Nothing Glyph Composer.
 * [rows] holds one frame per 1/60 s, already normalised to [GlyphLayout.CHANNELS] values (0..4095).
 */
class GlyphTrack(
    val rows: Array<IntArray>,
    /** Number of zone columns the file was recorded with (36 for Phone (3a)/(3a) Pro). */
    val sourceColumns: Int,
    val title: String?,
    val album: String? = null,
) {
    /** True when the file was made for another phone and had to be collapsed to a brightness envelope. */
    val mapped: Boolean get() = sourceColumns != GlyphLayout.CHANNELS
    val durationMs: Long get() = (rows.size * 1000L) / FPS

    companion object { const val FPS = 60 }
}

object GlyphLayout {
    /** Phone (3a) / (3a) Pro: C1–C20 = 0..19, A1–A11 = 20..30, B1–B5 = 31..35. */
    const val CHANNELS = 36
    const val MAX = 4095
}

/**
 * Glyph Composer exports an Ogg Vorbis file whose `AUTHOR` comment holds
 * base64( zlib( CSV ) ): one CRLF-terminated line per 16.67 ms frame, one 0..4095 value per zone.
 */
object GlyphtoneParser {

    fun parseFile(file: File): GlyphTrack? = runCatching { parse(file.readBytes()) }.getOrNull()

    fun parse(bytes: ByteArray): GlyphTrack? = try {
        val tags = readComments(bytes)
        val author = tags?.get("AUTHOR")
        if (author.isNullOrBlank()) null else decodeTrack(author, tags["TITLE"], tags["ALBUM"])
    } catch (t: Throwable) {
        null
    }

    private fun decodeTrack(author: String, title: String?, album: String?): GlyphTrack? {
        val raw = Base64.getMimeDecoder().decode(author.trim())
        val text = (inflate(raw) ?: raw).toString(Charsets.ISO_8859_1)

        val parsed = ArrayList<IntArray>()
        var cols = 0
        for (line in text.split('\n')) {
            val t = line.trim()
            if (t.isEmpty()) continue
            val values = t.split(',').filter { it.isNotBlank() }.map { it.trim().toInt().coerceIn(0, GlyphLayout.MAX) }
            if (values.isEmpty()) continue
            cols = maxOf(cols, values.size)
            parsed.add(values.toIntArray())
        }
        if (parsed.isEmpty()) return null

        val rows = Array(parsed.size) { i ->
            val src = parsed[i]
            if (cols == GlyphLayout.CHANNELS) {
                IntArray(GlyphLayout.CHANNELS) { c -> src.getOrElse(c) { 0 } }
            } else {
                // Another phone's layout: keep the rhythm by driving every zone with the frame's peak brightness.
                val peak = src.max()
                IntArray(GlyphLayout.CHANNELS) { peak }
            }
        }
        return GlyphTrack(rows, cols, title, album)
    }

    private fun inflate(data: ByteArray): ByteArray? {
        val inflater = Inflater()
        return try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream(data.size * 6)
            val buf = ByteArray(16 * 1024)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buf, 0, n)
            }
            out.toByteArray().takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        } finally {
            inflater.end()
        }
    }

    /** Walks the first Ogg packets until the comment header and returns its KEY=value pairs (keys upper-cased). */
    private fun readComments(b: ByteArray): Map<String, String>? {
        var pos = 0
        val packets = ArrayList<ByteArray>(2)
        val cur = ByteArrayOutputStream()
        outer@ while (packets.size < 2 && pos + 27 <= b.size) {
            if (b[pos] != 'O'.code.toByte() || b[pos + 1] != 'g'.code.toByte() || b[pos + 2] != 'g'.code.toByte() || b[pos + 3] != 'S'.code.toByte()) return null
            val nseg = b[pos + 26].toInt() and 0xFF
            var dataPos = pos + 27 + nseg
            if (dataPos > b.size) return null
            for (i in 0 until nseg) {
                val lace = b[pos + 27 + i].toInt() and 0xFF
                if (dataPos + lace > b.size) return null
                cur.write(b, dataPos, lace)
                dataPos += lace
                if (lace < 255) {
                    packets.add(cur.toByteArray())
                    cur.reset()
                    if (packets.size >= 2) break@outer
                }
            }
            pos = dataPos
        }
        val p = packets.getOrNull(1) ?: return null

        var off = when {
            p.size > 7 && p[0].toInt() == 3 && String(p, 1, 6, Charsets.US_ASCII) == "vorbis" -> 7
            p.size > 8 && String(p, 0, 8, Charsets.US_ASCII) == "OpusTags" -> 8
            else -> return null
        }
        fun int32(): Int {
            val v = (p[off].toInt() and 0xFF) or ((p[off + 1].toInt() and 0xFF) shl 8) or
                ((p[off + 2].toInt() and 0xFF) shl 16) or ((p[off + 3].toInt() and 0xFF) shl 24)
            off += 4
            return v
        }
        val vendorLen = int32()      // read first: `off += int32()` would use the stale offset
        off += vendorLen
        val count = int32()
        val map = HashMap<String, String>()
        repeat(count) {
            val len = int32()
            if (len < 0 || off + len > p.size) return map
            val kv = String(p, off, len, Charsets.UTF_8)
            off += len
            val eq = kv.indexOf('=')
            if (eq > 0) map[kv.substring(0, eq).uppercase()] = kv.substring(eq + 1)
        }
        return map
    }
}
