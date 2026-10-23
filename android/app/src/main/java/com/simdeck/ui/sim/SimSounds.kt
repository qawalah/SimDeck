package com.simdeck.ui.sim

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.staticCompositionLocalOf
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Optional control sounds (off by default, docs/THEMES.md Feel): a light tick, a firm click and a heavy thunk, generated
 * once as short WAV files (no audio assets shipped) and played through a SoundPool with the sonification usage, so they
 * follow the phone's touch-sound volume.
 */
class SimSounds(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = HashMap<Feel, Int>()

    init {
        val dir = File(context.cacheDir, "sounds").apply { mkdirs() }
        for (f in Feel.entries) {
            val file = File(dir, "${f.name.lowercase()}_v1.wav")
            if (!file.exists()) FileOutputStream(file).use { it.write(wav(samples(f))) }
            ids[f] = pool.load(file.absolutePath, 1)
        }
    }

    fun play(f: Feel) {
        ids[f]?.let { pool.play(it, VOLUME, VOLUME, 1, 0, 1f) }
    }

    fun release() = pool.release()

    companion object {
        private const val RATE = 44100
        private const val VOLUME = 0.6f

        /** The waveform of each feel, as 16-bit samples. */
        fun samples(f: Feel): ShortArray {
            val rnd = Random(f.ordinal + 1)
            val ms = when (f) { Feel.LIGHT -> 8; Feel.FIRM -> 16; Feel.HEAVY -> 55 }
            val n = RATE * ms / 1000
            return ShortArray(n) { i ->
                val t = i.toDouble() / RATE * 1000.0 // ms
                val v = when (f) {
                    Feel.LIGHT -> sin(2 * PI * 3200 * t / 1000) * exp(-t / 1.2) + (rnd.nextDouble() - 0.5) * 0.3 * exp(-t / 0.6)
                    Feel.FIRM -> sin(2 * PI * 1800 * t / 1000) * exp(-t / 2.5) * 0.8 + (rnd.nextDouble() - 0.5) * 0.6 * exp(-t / 1.0)
                    Feel.HEAVY -> sin(2 * PI * 140 * t / 1000) * exp(-t / 12.0) + sin(2 * PI * 420 * t / 1000) * exp(-t / 5.0) * 0.4 +
                        (rnd.nextDouble() - 0.5) * 0.5 * exp(-t / 2.0)
                }
                (v.coerceIn(-1.0, 1.0) * 0.8 * Short.MAX_VALUE).toInt().toShort()
            }
        }

        /** A mono 16-bit PCM WAV file holding [pcm]. */
        fun wav(pcm: ShortArray): ByteArray {
            val data = pcm.size * 2
            val b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
            b.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
            b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
            b.put("data".toByteArray()).putInt(data)
            for (s in pcm) b.putShort(s)
            return b.array()
        }
    }
}

/** Plays a control sound in the given weight, when sounds are on; null when off. */
val LocalSimSound = staticCompositionLocalOf<((Feel) -> Unit)?> { null }
