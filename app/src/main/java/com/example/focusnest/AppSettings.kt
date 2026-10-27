package com.example.focusnest

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** User settings, subject list and focus points. Call [init] once, from the Application class. */
object AppSettings {
    private const val PREFS = "app_settings"
    private const val KEY_FOCUS = "focus_minutes"
    private const val KEY_GOAL = "daily_goal_minutes"
    private const val KEY_WALKTHROUGH = "walkthrough_seen"
    private const val KEY_POINTS_TOTAL = "points_total"
    private const val KEY_POINTS_DAY = "points_day"
    private const val KEY_POINTS_TODAY = "points_today"
    private const val KEY_SUBJECT = "selected_subject"
    private const val KEY_CUSTOM_SUBJECTS = "custom_subjects"

    const val DEFAULT_FOCUS_MINUTES = 25
    const val MIN_FOCUS_MINUTES = 1
    const val MAX_FOCUS_MINUTES = 180

    private lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    var focusMinutes: Int
        get() = prefs.getInt(KEY_FOCUS, DEFAULT_FOCUS_MINUTES)
        set(value) {
            prefs.edit().putInt(KEY_FOCUS, value.coerceIn(MIN_FOCUS_MINUTES, MAX_FOCUS_MINUTES)).apply()
        }

    const val MINUTES_PER_POINT = 5
    const val DEFAULT_GOAL_MINUTES = 240
    const val MIN_GOAL_MINUTES = 15
    const val MAX_GOAL_MINUTES = 960

    /** Daily study goal in minutes, shown in Today's status. */
    var dailyGoalMinutes: Int
        get() = prefs.getInt(KEY_GOAL, DEFAULT_GOAL_MINUTES)
        set(value) {
            prefs.edit().putInt(KEY_GOAL, value.coerceIn(MIN_GOAL_MINUTES, MAX_GOAL_MINUTES)).apply()
        }

    /** True once the first-run walkthrough has been shown. */
    var walkthroughSeen: Boolean
        get() = prefs.getBoolean(KEY_WALKTHROUGH, false)
        set(value) {
            prefs.edit().putBoolean(KEY_WALKTHROUGH, value).apply()
        }

    /** Subject picked last time. */
    var selectedSubject: String
        get() = prefs.getString(KEY_SUBJECT, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_SUBJECT, value).apply()
        }

    /** Subjects the user added, in the order they were added. */
    private var customSubjects: List<String>
        get() {
            val array = JSONArray(prefs.getString(KEY_CUSTOM_SUBJECTS, "[]") ?: "[]")
            return (0 until array.length()).map { array.getString(it) }
        }
        set(value) {
            prefs.edit().putString(KEY_CUSTOM_SUBJECTS, JSONArray(value).toString()).apply()
        }

    /** Built-in subjects followed by the ones the user added. */
    fun allSubjects(): List<String> = DEFAULT_SUBJECTS + customSubjects

    /** Adds a subject. Returns false if the name is empty or already in the list, ignoring case. */
    fun addSubject(name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty() || allSubjects().any { it.equals(clean, ignoreCase = true) }) return false
        customSubjects = customSubjects + clean
        return true
    }

    /** Focus minutes ever recorded. The stored value is kept in minutes; points are derived from it. */
    private val totalMinutes: Int
        get() = prefs.getInt(KEY_POINTS_TOTAL, 0)

    private fun todayMinutes(): Int =
        if (prefs.getString(KEY_POINTS_DAY, null) == todayKey()) prefs.getInt(KEY_POINTS_TODAY, 0) else 0

    /** One point for every [MINUTES_PER_POINT] minutes of focus. */
    val totalPoints: Int
        get() = totalMinutes / MINUTES_PER_POINT

    fun todayPoints(): Int = todayMinutes() / MINUTES_PER_POINT

    /**
     * Adds finished focus minutes. Returns the points this added, overall and today,
     * so the account can be updated with just the change.
     */
    fun addFocusMinutes(minutes: Int): Pair<Int, Int> {
        val totalBefore = totalPoints
        val todayBefore = todayPoints()
        prefs.edit()
            .putInt(KEY_POINTS_TOTAL, totalMinutes + minutes)
            .putString(KEY_POINTS_DAY, todayKey())
            .putInt(KEY_POINTS_TODAY, todayMinutes() + minutes)
            .apply()
        return (totalPoints - totalBefore) to (todayPoints() - todayBefore)
    }

    fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
