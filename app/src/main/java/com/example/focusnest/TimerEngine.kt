package com.example.focusnest

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.CountDownTimer

/**
 * Owns the timer for the whole app process, so the screen and the foreground service stay in sync.
 * Each focus run is logged as a session from when it starts until it pauses, finishes, or the subject changes.
 * A session can also be ended early, which returns its parts so a summary can be shown.
 */
object TimerEngine {

    interface Listener {
        fun onTimerChanged()

        /** Called when a focus session finishes, with the points it earned. */
        fun onFocusCompleted(points: Int) {}
    }

    private const val ALERT_ID = 2

    private val listeners = mutableListOf<Listener>()
    private var appCtx: Context? = null
    private var countDown: CountDownTimer? = null
    private var focusCount = 0
    private var segmentStart: Long? = null
    private val sessionParts = mutableListOf<StudySession>()

    var phase = Phase.FOCUS
        private set
    var remainingMs = Phase.FOCUS.minutes() * 60_000L
        private set
    var running = false
        private set
    var subject = DEFAULT_SUBJECTS.first()
        private set
    var music = Music.NONE
        private set

    fun addListener(listener: Listener) {
        if (listener !in listeners) listeners += listener
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    private fun notifyChanged() {
        listeners.toList().forEach { it.onTimerChanged() }
    }

    /** The study session in progress, running up to now, or null when no focus run is active. */
    fun liveSession(): StudySession? =
        segmentStart?.let { StudySession(subject, it, System.currentTimeMillis()) }

    /** True while a focus session is running, paused, or has study time recorded that hasn't been finished. */
    fun hasActiveSession(): Boolean = running || sessionParts.isNotEmpty()

    fun start(ctx: Context) {
        if (running) return
        appCtx = ctx.applicationContext
        running = true
        beginSegment()
        countDown = object : CountDownTimer(remainingMs, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMs = millisUntilFinished
                notifyChanged()
            }

            override fun onFinish() = finishPhase(ctx)
        }.start()
        ctx.startForegroundService(Intent(ctx, FocusService::class.java))
        MusicPlayer.play(ctx, music)
        notifyChanged()
    }

    fun pause() {
        if (!running) return
        endSegment()
        countDown?.cancel()
        countDown = null
        running = false
        MusicPlayer.stop()
        notifyChanged()
    }

    /** Stops the timer and returns it to the start. Use [endSession] when a session is in progress. */
    fun reset() {
        endSegment()
        countDown?.cancel()
        countDown = null
        running = false
        MusicPlayer.stop()
        sessionParts.clear()
        phase = Phase.FOCUS
        remainingMs = phase.minutes() * 60_000L
        notifyChanged()
    }

    /** What ending a session early produced: its parts (start, end, subject) and the points it earned. */
    class EndedSession(val parts: List<StudySession>, val points: Int)

    /**
     * Ends the session early. Study time so far stays in the log, and the full minutes studied
     * earn points, as they would when a session finishes. Parts are empty during a break.
     */
    fun endSession(): EndedSession {
        endSegment()
        countDown?.cancel()
        countDown = null
        running = false
        MusicPlayer.stop()
        val parts = sessionParts.toList()
        sessionParts.clear()
        // Whole minutes count toward points; leftover seconds are not carried over
        val minutes = (parts.sumOf { it.durationMs } / 60_000L).toInt()
        val points = if (minutes > 0) {
            val (earnedTotal, earnedToday) = AppSettings.addFocusMinutes(minutes)
            Account.recordPoints(earnedTotal, earnedToday)
            earnedTotal
        } else {
            0
        }
        phase = Phase.FOCUS
        remainingMs = phase.minutes() * 60_000L
        notifyChanged()
        return EndedSession(parts, points)
    }

    /** Changes the subject. If a focus run is in progress, it is split so each subject is logged separately. */
    fun setSubject(name: String) {
        if (name == subject) return
        val recording = segmentStart != null
        if (recording) endSegment()
        subject = name
        if (recording) beginSegment()
        notifyChanged()
    }

    /** Applies a new focus length. Only used while the timer is stopped. */
    fun refreshDuration() {
        if (!running && phase == Phase.FOCUS) remainingMs = phase.minutes() * 60_000L
        notifyChanged()
    }

    fun setMusic(ctx: Context, selected: Music) {
        music = selected
        if (running) MusicPlayer.play(ctx, selected)
        notifyChanged()
    }

    private fun beginSegment() {
        if (phase == Phase.FOCUS) segmentStart = System.currentTimeMillis()
    }

    private fun endSegment() {
        val start = segmentStart ?: return
        segmentStart = null
        val session = StudySession(subject, start, System.currentTimeMillis())
        if (session.durationMs < 1_000L) return
        appCtx?.let { StatsStore.addSession(it, session) }
        sessionParts += session
    }

    private fun finishPhase(ctx: Context) {
        countDown = null
        endSegment()
        running = false
        MusicPlayer.stop()
        val finished = phase
        if (finished == Phase.FOCUS) {
            // The session finished on its own, so there is nothing left to summarise
            sessionParts.clear()
            // One point per 5 focus minutes, so a short session may earn nothing yet
            val (earnedTotal, earnedToday) = AppSettings.addFocusMinutes(Phase.FOCUS.minutes())
            Account.recordPoints(earnedTotal, earnedToday)
            focusCount++
            phase = if (focusCount % 4 == 0) Phase.LONG_BREAK else Phase.SHORT_BREAK
            remainingMs = phase.minutes() * 60_000L
            val message = if (earnedTotal > 0) "Focus complete! +$earnedTotal points." else "Focus complete!"
            alert(ctx, "$message Time for a ${phase.label.lowercase()}.")
            listeners.toList().forEach { it.onFocusCompleted(earnedTotal) }
        } else {
            phase = Phase.FOCUS
            remainingMs = phase.minutes() * 60_000L
            alert(ctx, "Break over. Ready to focus?")
        }
        notifyChanged()
    }

    private fun alert(ctx: Context, text: String) {
        val notification = Notification.Builder(ctx, FocusApp.CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Focus Nest")
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ALERT_ID, notification)
    }
}
