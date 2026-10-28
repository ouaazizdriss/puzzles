package com.driss.puzzles.ui.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.driss.puzzles.R

/** Short game sound effects. Plays regardless of the system touch-sound setting, unlike view clicks. */
class Sounds(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val snap = pool.load(context, R.raw.snap, 1)
    private val join = pool.load(context, R.raw.join, 1)
    private val win = pool.load(context, R.raw.win, 1)

    fun snap() = play(snap)
    fun join() = play(join)
    fun win() = play(win)

    private fun play(id: Int) {
        pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun release() = pool.release()
}
