package com.example.focusnest

import android.content.Context
import android.media.MediaPlayer

/** Plays one bundled sound on loop while a session is running. */
object MusicPlayer {
    private var player: MediaPlayer? = null

    fun play(ctx: Context, music: Music) {
        stop()
        val resId = music.resId ?: return
        player = MediaPlayer.create(ctx.applicationContext, resId)?.apply {
            isLooping = true
            setVolume(0.6f, 0.6f)
            start()
        }
    }

    fun stop() {
        player?.run {
            if (isPlaying) stop()
            release()
        }
        player = null
    }
}
