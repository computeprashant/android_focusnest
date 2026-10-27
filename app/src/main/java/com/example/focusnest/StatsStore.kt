package com.example.focusnest

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One focus session: a subject studied from [start] to [end], in epoch milliseconds. */
data class StudySession(val subject: String, val start: Long, val end: Long) {
    val durationMs: Long get() = end - start
}

/** The full study log, kept per day on the device. */
object StatsStore {
    private const val PREFS = "focus_log"
    private const val KEY = "log_json"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun load(ctx: Context): JSONObject = JSONObject(prefs(ctx).getString(KEY, "{}") ?: "{}")

    /** Day key such as "2026-10-25", for the given time in milliseconds. */
    fun dayKey(millis: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

    fun addSession(ctx: Context, session: StudySession) {
        if (session.durationMs < 1_000L) return // ignore accidental taps
        val all = load(ctx)
        val key = dayKey(session.start)
        val day = all.optJSONArray(key) ?: JSONArray().also { all.put(key, it) }
        day.put(JSONObject().put("s", session.subject).put("a", session.start).put("b", session.end))
        prefs(ctx).edit().putString(KEY, all.toString()).apply()
    }

    /** Days that have a log, newest first. */
    fun days(ctx: Context): List<String> = load(ctx).keys().asSequence().toList().sortedDescending()

    /** Sessions for one day, in the order they started. */
    fun sessions(ctx: Context, day: String): List<StudySession> {
        val array = load(ctx).optJSONArray(day) ?: return emptyList()
        return (0 until array.length())
            .map { i ->
                val item = array.getJSONObject(i)
                StudySession(item.getString("s"), item.getLong("a"), item.getLong("b"))
            }
            .sortedBy { it.start }
    }
}
