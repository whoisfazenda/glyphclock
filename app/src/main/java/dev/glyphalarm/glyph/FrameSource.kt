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

/** Fallback for sounds without a recording (the phone's own tone, plain music): a soft chase on C and a breathing A and B. */
class PulseSource : FrameSource {
    override fun fill(tMs: Long, out: IntArray) {
        out.fill(0)
        val head = (tMs % 1400) / 1400.0 * 20.0
        for (i in 0 until 20) {
            val d = ((head - i) % 20.0 + 20.0) % 20.0
            if (d < 6.0) out[i] = (2800 * (1.0 - d / 6.0)).toInt()
        }
        val breath = (0.5 - 0.5 * Math.cos(tMs / 1000.0 * 2 * Math.PI * 0.8)).coerceIn(0.0, 1.0)
        val v = (400 + 2400 * breath).toInt()
        for (i in 20 until 36) out[i] = v
    }
}
