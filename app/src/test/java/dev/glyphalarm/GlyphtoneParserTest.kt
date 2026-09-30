package dev.glyphalarm

import dev.glyphalarm.glyph.GlyphtoneParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater

class GlyphtoneParserTest {
    private val CRLF = "\r\n"
    private fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    private fun page(type: Int, seq: Int, lacing: IntArray, data: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        o.write("OggS".toByteArray()); o.write(0); o.write(type)
        o.write(ByteArray(8)); o.write(le32(1)); o.write(le32(seq)); o.write(ByteArray(4))
        o.write(lacing.size); lacing.forEach { o.write(it) }; o.write(data)
        return o.toByteArray()
    }

    /** Splits a packet into Ogg lacing values. */
    private fun lace(len: Int): IntArray {
        val l = ArrayList<Int>(); var r = len
        while (r >= 255) { l.add(255); r -= 255 }
        l.add(r); return l.toIntArray()
    }

    private fun commentPacket(author: String): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(3); o.write("vorbis".toByteArray())
        val vendor = "test".toByteArray(); o.write(le32(vendor.size)); o.write(vendor)
        val tags = listOf("TITLE=My Show", "COMPOSER=v1-Asteroids Glyph Composer", "CUSTOM2=36cols", "AUTHOR=$author")
        o.write(le32(tags.size))
        tags.forEach { val b = it.toByteArray(); o.write(le32(b.size)); o.write(b) }
        o.write(1)
        return o.toByteArray()
    }

    private fun csv(frames: Int, cols: Int): String {
        val sb = StringBuilder()
        for (f in 0 until frames) { for (c in 0 until cols) sb.append(if (c == f % cols) 4095 else 0).append(','); sb.append("\r\n") }
        return sb.toString()
    }

    private fun author(csv: String): String {
        val d = Deflater(); d.setInput(csv.toByteArray()); d.finish()
        val buf = ByteArray(1 shl 16); val out = ByteArrayOutputStream()
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        return Base64.getEncoder().encodeToString(out.toByteArray())
    }

    private fun ogg(author: String, splitAcrossPages: Boolean): ByteArray {
        val ident = ByteArray(30) { 1 }
        val p1 = page(2, 0, lace(ident.size), ident)
        val c = commentPacket(author)
        val out = ByteArrayOutputStream(); out.write(p1)
        if (!splitAcrossPages) {
            out.write(page(0, 1, lace(c.size), c))
        } else {
            val cut = 255 * 10 // first page holds 10 full segments, packet continues on the next page
            out.write(page(0, 1, IntArray(10) { 255 }, c.copyOfRange(0, cut)))
            val rest = c.copyOfRange(cut, c.size)
            out.write(page(1, 2, lace(rest.size), rest))
        }
        out.write(ByteArray(100)) // pretend audio follows
        return out.toByteArray()
    }

    @Test fun parsesNativeTrack() {
        val t = GlyphtoneParser.parse(ogg(author(csv(120, 36)), false))
        assertNotNull(t)
        assertEquals(36, t!!.sourceColumns); assertEquals(120, t.rows.size)
        assertEquals(4095, t.rows[5][5]); assertEquals(0, t.rows[5][6]); assertEquals(2000L, t.durationMs)
        assertEquals(false, t.mapped)
    }

    @Test fun parsesCommentSplitAcrossPages() {
        val rnd = java.util.Random(1)
        val noisy = StringBuilder()
        for (f in 0 until 600) { for (c in 0 until 36) noisy.append(rnd.nextInt(4096)).append(','); noisy.append(CRLF) }
        val big = author(noisy.toString()) // poorly compressible: forces a long, multi-page comment
        val t = GlyphtoneParser.parse(ogg(big, true))
        assertNotNull(t); assertEquals(600, t!!.rows.size)
    }

    @Test fun foreignLayoutIsMapped() {
        val t = GlyphtoneParser.parse(ogg(author(csv(60, 33)), false))
        assertNotNull(t); assertEquals(33, t!!.sourceColumns); assertEquals(true, t.mapped)
        assertEquals(36, t.rows[0].size); assertEquals(4095, t.rows[0][10])
    }

    @Test fun plainAudioHasNoGlyphData() {
        assertNull(GlyphtoneParser.parse(ogg("", false)))
        assertNull(GlyphtoneParser.parse(ByteArray(200)))
    }
}
