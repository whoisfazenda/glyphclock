package dev.glyphalarm.glyph

/** Produces one brightness value (0..4095) per glyph channel for a point in time. */
interface FrameSource {
    fun fill(tMs: Long, out: IntArray)
}

/** Plays back a Glyph Composer recording, looping together with the audio. */
class TrackSource(private val track: GlyphTrack) : FrameSource {
    override fun fill(tMs: Long, out: IntArray) {
        val rows = track.rows
        if (rows.isEmpty()) { out.fill(0); return }
        val frame = Math.floorMod((tMs * GlyphTrack.FPS / 1000).toInt(), rows.size)
        rows[frame].copyInto(out)
    }
}
