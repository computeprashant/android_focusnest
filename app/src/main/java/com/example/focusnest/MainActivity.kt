package com.example.focusnest

import android.Manifest
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Action for a button in a themed popup. It receives the popup so it can close it. */
private typealias PanelAction = (AlertDialog) -> Unit

/** One subject's sessions in the chosen log range, shown as a row in the daily log. */
private class SubjectTotal(val subject: String, val sessions: List<StudySession>) {
    val totalMs: Long = sessions.sumOf { it.durationMs }
}

/** Which days the daily log covers. */
private enum class LogRange(val label: String) {
    TODAY("Today"),
    WEEK("Last week"),
    MONTH("Last month"),
    TOTAL("Total time")
}

/** Steps of the first-run walkthrough: title and what the section does. */
private val WALKTHROUGH_STEPS = listOf(
    "Welcome to Focus Nest" to
        "Study in focused sessions, track your time by subject, and see how you compare with other students.",
    "Greeting and points" to
        "Your name and points appear here. Tap the Goal button to set how many hours you want to study each day.",
    "Background sound" to
        "Play rain or lofi music while you study. Choose No sound to turn it off.",
    "Timer" to
        "Set your focus length with Set timer, then tap Start. Pause or Reset at any time. Breaks are added for you.",
    "Subject" to
        "Pick the subject you are studying. Tap + Add subject to create your own. Your subjects stay in the list.",
    "Daily log" to
        "Every subject you studied, with its session count and total time. Use the filter to view Today, Last week, Last month or Total time. Tap a row to see each session's start and end time.",
    "Today's status and menu" to
        "Your progress toward today's goal, with a trophy when you reach it. Use the ⋮ menu for Dark mode, the leaderboard, sign-in, this walkthrough and About.",
)

class MainActivity : ComponentActivity(), TimerEngine.Listener {

    private lateinit var theme: AppTheme
    private lateinit var ring: RingView
    private lateinit var timeText: TextView
    private lateinit var phaseText: TextView
    private lateinit var startButton: Button
    private val countdownHandler = Handler(Looper.getMainLooper())
    private var countingDown = false
    private var countdownStep = 0
    private val countdownRunnable = Runnable { showCountdownStep() }
    private lateinit var greetingText: TextView
    private lateinit var hintText: TextView
    private lateinit var pointsText: TextView
    private lateinit var goalButton: Button
    private lateinit var subjectSpinner: Spinner
    private lateinit var rangeSpinner: Spinner
    private lateinit var logList: LinearLayout
    private lateinit var statusHolder: LinearLayout
    private var selectedRange = LogRange.TODAY
    private var ringAnim: ValueAnimator? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Account.handleSignInResult(result.data) { outcome ->
            outcome.onFailure { toast("Sign-in failed: ${it.message}") }
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        theme = ThemeStore.current(this)
        setContentView(buildUi())
        // Android 13+ needs permission before the end-of-session alert can show
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // First launch: explain each section once the screen is drawn
        if (!AppSettings.walkthroughSeen) {
            AppSettings.walkthroughSeen = true
            window.decorView.post { showWalkthrough(0) }
        }
    }

    override fun onStart() {
        super.onStart()
        TimerEngine.addListener(this)
        render()
    }

    override fun onStop() {
        TimerEngine.removeListener(this)
        super.onStop()
    }

    override fun onTimerChanged() = render()

    override fun onFocusCompleted(points: Int) = showSessionComplete(points)

    // ---------- UI ----------

    private fun buildUi(): FrameLayout {
        val t = theme
        val pad = dp(20)

        // Header: logo, app name, and the menu button on the right
        val logo = ImageView(this).apply { setImageResource(R.drawable.ic_logo) }
        val title = text(28f).apply {
            text = "Focus Nest"
            setTypeface(null, Typeface.BOLD)
        }
        val menuButton = TextView(this).apply {
            text = "⋮" // three vertical dots
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
            setTextColor(t.text)
            setPadding(dp(12), 0, dp(4), 0)
            setOnClickListener { showMenu(it) }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(logo, LinearLayout.LayoutParams(dp(56), dp(56)))
            addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(12)
            })
            addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 0, 1f))
            addView(menuButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        // Greeting card: personal hello, a hint, points and the goal button
        greetingText = text(26f).apply { setTypeface(null, Typeface.BOLD) }
        hintText = text(15f).apply { setTextColor(t.secondaryText) }
        pointsText = text(16f).apply { setTypeface(null, Typeface.BOLD) }
        goalButton = Button(this).apply { setOnClickListener { askDailyGoal() } }
        styleButton(goalButton, primary = false)
        val pointsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(pointsText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(goalButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val greetingCard = card().apply {
            addView(greetingText, lp())
            addView(hintText, lp())
            addView(pointsRow, lp())
        }

        // Background sound card, directly under the greeting
        val musicOptions = Music.values()
        val musicSpinner = spinner(musicOptions.map { it.label }, musicOptions.indexOf(TimerEngine.music)) { position ->
            TimerEngine.setMusic(applicationContext, musicOptions[position])
        }
        val soundCard = card().apply {
            addView(section("Background sound"))
            addView(musicSpinner, lp())
        }

        // Timer card: ring, Start/Reset, and the focus length setting
        ring = RingView(this).apply { trackColor = t.track }
        timeText = TextView(this).apply {
            textSize = 44f
            gravity = Gravity.CENTER
            setTextColor(t.text)
            setTypeface(null, Typeface.BOLD)
        }
        phaseText = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(t.secondaryText)
        }
        val timerArea = FrameLayout(this).apply {
            addView(ring, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    addView(phaseText)
                    addView(timeText)
                },
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
        startButton = Button(this).apply {
            setOnClickListener {
                when {
                    countingDown -> cancelCountdown()
                    TimerEngine.running -> TimerEngine.pause()
                    else -> startCountdown()
                }
            }
        }
        val resetButton = Button(this).apply {
            text = "End Session"
            setOnClickListener {
                when {
                    countingDown -> cancelCountdown()
                    TimerEngine.hasActiveSession() -> confirmEndSession()
                    else -> TimerEngine.reset()
                }
            }
        }
        styleButton(startButton, primary = true)
        styleButton(resetButton, primary = false)
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(startButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
            addView(resetButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6) })
        }
        val setTimerButton = Button(this).apply {
            text = "Set timer (${AppSettings.focusMinutes} min focus)"
            setOnClickListener { askFocusLength() }
        }
        styleButton(setTimerButton, primary = false)
        val timerCard = card().apply {
            addView(timerArea, LinearLayout.LayoutParams(dp(240), dp(240)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            })
            addView(buttons, lp())
            addView(setTimerButton, lp())
        }

        // Subject card: one list of subjects, plus a button to add your own
        val subjects = AppSettings.allSubjects()
        val restored = AppSettings.selectedSubject
        TimerEngine.setSubject(if (restored in subjects) restored else subjects.first())
        subjectSpinner = spinner(subjects, subjects.indexOf(TimerEngine.subject).coerceAtLeast(0)) { position ->
            pickSubject(position)
        }
        val addSubjectButton = Button(this).apply {
            text = "+ Add subject"
            setOnClickListener { showAddSubject() }
        }
        styleButton(addSubjectButton, primary = false)
        val subjectCard = card().apply {
            addView(section("Subject"))
            addView(subjectSpinner, lp())
            addView(addSubjectButton, lp())
        }

        // Daily log: a filter for the range, one row per subject, then the total
        rangeSpinner = spinner(LogRange.values().map { it.label }, selectedRange.ordinal) { position ->
            selectedRange = LogRange.values()[position]
            renderLog()
        }
        logList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val logCard = card().apply {
            addView(section("Daily log"))
            addView(rangeSpinner, lp())
            addView(logList, lp())
        }

        // Today's status sits at the end of the screen and holds the goal row
        statusHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val statusCard = card().apply {
            addView(section("Today's status"))
            addView(statusHolder, lp())
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            // Ignore taps while another app covers this screen (tapjacking protection)
            filterTouchesWhenObscured = true
            addView(header, lp())
            addView(greetingCard, lp())
            addView(soundCard, lp())
            addView(timerCard, lp())
            addView(subjectCard, lp())
            addView(logCard, lp())
            addView(statusCard, lp())
        }

        val scroll = ScrollView(this).apply { addView(column) }
        return FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(t.bgTop, t.bgBottom))
            addView(FrameLayout(this@MainActivity).apply {
                addView(BackdropView(this@MainActivity, t), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            })
        }
    }

    private fun pickSubject(position: Int) {
        val name = AppSettings.allSubjects().getOrNull(position) ?: return
        TimerEngine.setSubject(name)
        AppSettings.selectedSubject = name
        render()
    }

    /** Rebuilds the subject dropdown, for example after a subject is added. */
    private fun refreshSubjectSpinner() {
        val list = AppSettings.allSubjects()
        subjectSpinner.adapter = ThemedAdapter(this, list, theme.text, theme.surface)
        subjectSpinner.setSelection(list.indexOf(TimerEngine.subject).coerceAtLeast(0))
    }

    private fun render() {
        val phase = TimerEngine.phase
        val t = theme
        animateRing(TimerEngine.remainingMs / (phase.minutes() * 60_000f))
        // While the 3-2-1 countdown runs, the big number shows the countdown instead of the timer
        if (!countingDown) {
            timeText.text = formatTime(TimerEngine.remainingMs)
            phaseText.text = "${phase.label} · ${TimerEngine.subject}"
            startButton.text = if (TimerEngine.running) "Pause" else "Start"
        }
        ring.ringColor = if (phase == Phase.FOCUS) t.focusRing else t.breakRing

        val user = Account.currentUser()
        greetingText.text = if (user != null) {
            "Hello, ${user.displayName?.substringBefore(' ') ?: "Student"}!"
        } else {
            "Hello, friend!"
        }
        hintText.text = if (user != null) {
            "Ready for your next focus session?"
        } else {
            "Sign in from the ⋮ menu to join the leaderboard."
        }
        pointsText.text = "Points: ${AppSettings.totalPoints}  ·  Today: ${AppSettings.todayPoints()}"

        renderLog()

        // Today's status: studied time out of the goal, with a trophy once the goal is reached
        statusHolder.removeAllViews()
        statusHolder.addView(goalRow(todayStudiedMs()), lp())

        goalButton.text = "Goal ${formatDuration(AppSettings.dailyGoalMinutes * 60_000L)}"
    }

    /** Smooth one-second fill. With "Remove animations" turned on, the ring jumps instead. */
    private fun animateRing(to: Float) {
        ringAnim?.cancel()
        if (reduceMotion()) {
            ring.progress = to
            return
        }
        ringAnim = ValueAnimator.ofFloat(ring.progress, to).apply {
            duration = 900
            interpolator = LinearInterpolator()
            addUpdateListener { ring.progress = it.animatedValue as Float }
            start()
        }
    }

    // ---------- End session and summary ----------

    private fun confirmEndSession() {
        val t = theme
        showThemedPanel(
            content = listOf(
                bodyText("End session?").apply { textSize = 22f; setTypeface(null, Typeface.BOLD) },
                bodyText("Are you sure you want to end this session? Your study time so far will be saved.")
                    .apply { setTextColor(t.secondaryText) }
            ),
            buttons = listOf(
                "End session" to { dialog: AlertDialog ->
                    dialog.dismiss()
                    val ended = TimerEngine.endSession()
                    if (ended.parts.isEmpty()) toast("Session ended") else showSessionSummary(ended.parts, ended.points)
                },
                "Keep going" to { dialog: AlertDialog -> dialog.dismiss() }
            )
        )
    }

    /** A summary of the session that was ended early: subjects, time spent, and start and end times. */
    private fun showSessionSummary(parts: List<StudySession>, points: Int) {
        val t = theme
        val first = parts.minOf { it.start }
        val last = parts.maxOf { it.end }
        val total = parts.sumOf { it.durationMs }

        val subjectCards = parts.groupBy { it.subject }
            .map { (name, list) -> name to list.sumOf { it.durationMs } }
            .sortedByDescending { it.second }
            .map { (name, ms) ->
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = rounded(t.track, 16)
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                    addView(TextView(this@MainActivity).apply {
                        text = name
                        textSize = 18f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(t.text)
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = formatDuration(ms)
                        textSize = 26f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(t.accent)
                    })
                }
            }

        val times = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(t.track, 16)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            addView(timeBlock("Started", clock(first)), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@MainActivity).apply {
                text = "→"
                textSize = 22f
                setTextColor(t.secondaryText)
            })
            addView(timeBlock("Ended", clock(last)), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        val content = mutableListOf<View>(
            bodyText("Session summary").apply { textSize = 13f; setTextColor(t.secondaryText) },
            bodyText("Good work!").apply { textSize = 24f; setTypeface(null, Typeface.BOLD) },
            bodyText("Your progress is saved in today's log.").apply { textSize = 14f; setTextColor(t.secondaryText) },
            times
        )
        content += subjectCards
        content += bodyText("Total focus: ${formatDuration(total)}").apply {
            textSize = 18f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
        }
        content += bodyText(
            if (points > 0) "+$points points earned" else "No points yet. Every 5 minutes of focus earns one."
        ).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (points > 0) t.accent else t.secondaryText)
        }
        showThemedPanel(
            content = content,
            buttons = listOf("Done" to { dialog: AlertDialog -> dialog.dismiss() })
        )
    }

    private fun timeBlock(label: String, value: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(TextView(this@MainActivity).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(theme.secondaryText)
        })
        addView(TextView(this@MainActivity).apply {
            text = value
            textSize = 17f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(theme.text)
        })
    }

    // ---------- 3-2-1 countdown before starting ----------

    /** Shows 3, 2, 1 one second apart, then starts the timer. */
    private fun startCountdown() {
        countingDown = true
        countdownStep = 3
        showCountdownStep()
    }

    private fun showCountdownStep() {
        if (!countingDown) return
        if (countdownStep == 0) {
            countingDown = false
            TimerEngine.start(applicationContext)
            render()
            return
        }
        timeText.text = countdownStep.toString()
        phaseText.text = "Get ready"
        startButton.text = "Cancel"
        countdownStep--
        countdownHandler.postDelayed(countdownRunnable, 1_000L)
    }

    private fun cancelCountdown() {
        countingDown = false
        countdownHandler.removeCallbacks(countdownRunnable)
        render()
    }

    private fun reduceMotion(): Boolean =
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    // ---------- Daily log ----------

    /** Days of saved sessions covered by [range]. Today is always included. */
    private fun dayKeysFor(range: LogRange): List<String> {
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L
        val today = AppSettings.todayKey()
        return when (range) {
            LogRange.TODAY -> listOf(today)
            LogRange.WEEK -> (0 until 7).map { StatsStore.dayKey(now - it * dayMs) }
            LogRange.MONTH -> (0 until 30).map { StatsStore.dayKey(now - it * dayMs) }
            LogRange.TOTAL -> (StatsStore.days(this) + today).distinct()
        }
    }

    /** Sessions in [range], including the focus run in progress. */
    private fun sessionsIn(range: LogRange): List<StudySession> {
        val saved = dayKeysFor(range).flatMap { StatsStore.sessions(this, it) }
        return saved + listOfNotNull(TimerEngine.liveSession())
    }

    private fun todayStudiedMs(): Long {
        val saved = StatsStore.sessions(this, AppSettings.todayKey()).sumOf { it.durationMs }
        return saved + (TimerEngine.liveSession()?.durationMs ?: 0L)
    }

    private fun renderLog() {
        val sessions = sessionsIn(selectedRange)

        logList.removeAllViews()
        if (sessions.isEmpty()) {
            logList.addView(TextView(this).apply {
                text = "No study sessions in this period yet."
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(theme.secondaryText)
            }, lp())
            return
        }

        sessions.groupBy { it.subject }
            .map { (subject, list) -> SubjectTotal(subject, list.sortedBy { it.start }) }
            .sortedByDescending { it.totalMs }
            .forEach { logList.addView(subjectRow(it), lp()) }
        logList.addView(totalRow(sessions), lp())
    }

    /** Subject on the left with its session count below, total time in bold on the right. Tap for details. */
    private fun subjectRow(group: SubjectTotal): View {
        val t = theme
        val name = TextView(this).apply {
            text = group.subject
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(t.text)
        }
        val count = TextView(this).apply {
            val n = group.sessions.size
            text = if (n == 1) "1 session" else "$n sessions"
            textSize = 13f
            setTextColor(t.secondaryText)
        }
        val time = TextView(this).apply {
            text = formatDuration(group.totalMs)
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(t.text)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(t.track, 14)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isClickable = true
            setOnClickListener { showSessions(group) }
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(name)
                addView(count)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(time)
        }
    }

    /** The range total, in the accent colour so it stands apart from the rows above it. */
    private fun totalRow(sessions: List<StudySession>): View {
        val t = theme
        val total = sessions.sumOf { it.durationMs }
        val label = TextView(this).apply {
            text = "Total"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(t.onAccent)
        }
        val count = TextView(this).apply {
            text = if (sessions.size == 1) "1 session" else "${sessions.size} sessions"
            textSize = 13f
            setTextColor(t.onAccent)
        }
        val value = TextView(this).apply {
            text = formatDuration(total)
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(t.onAccent)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(t.accent, 14)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label)
                addView(count)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(value)
        }
    }

    /** Studied time out of the goal, with a progress bar and a trophy once the goal is reached. */
    private fun goalRow(studiedMs: Long): View {
        val t = theme
        val goalMinutes = AppSettings.dailyGoalMinutes
        val achieved = studiedMs >= goalMinutes * 60_000L
        val label = TextView(this).apply {
            text = "Studied ${formatDuration(studiedMs)} out of ${formatDuration(goalMinutes * 60_000L)}"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(t.text)
        }
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = goalMinutes
            progress = (studiedMs / 60_000).toInt().coerceAtMost(goalMinutes)
            progressTintList = ColorStateList.valueOf(t.accent)
            progressBackgroundTintList = ColorStateList.valueOf(t.surface)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(t.track, 14)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label)
                addView(bar, lp())
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (achieved) {
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.ic_trophy)
                    layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { leftMargin = dp(8) }
                })
            }
        }
    }

    /** Every session for one subject in the chosen range, with its date and start and end time. */
    private fun showSessions(group: SubjectTotal) {
        val t = theme
        val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())
        val rows = group.sessions.map { session ->
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(t.track, 14)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                addView(TextView(this@MainActivity).apply {
                    text = "${dateFormat.format(Date(session.start))} · ${clock(session.start)}  –  ${clock(session.end)}"
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(t.text)
                })
                addView(TextView(this@MainActivity).apply {
                    text = formatDuration(session.durationMs)
                    textSize = 13f
                    setTextColor(t.secondaryText)
                })
            }
        }
        val header = listOf(
            bodyText(group.subject).apply { setTypeface(null, Typeface.BOLD); textSize = 20f },
            bodyText("${group.sessions.size} sessions · ${formatDuration(group.totalMs)}")
        )
        showThemedPanel(
            content = header + rows,
            buttons = listOf("Close" to { dialog: AlertDialog -> dialog.dismiss() })
        )
    }

    private fun clock(millis: Long): String =
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))

    // ---------- Subjects ----------

    private fun showAddSubject() {
        val input = themedEditText("e.g. Geography", "")
        showThemedPanel(
            content = listOf(
                bodyText("Add a subject you study. It will stay in the list next time too."),
                input
            ),
            buttons = listOf(
                "Add subject" to { dialog: AlertDialog ->
                    val name = input.text.toString().trim()
                    if (AppSettings.addSubject(name)) {
                        TimerEngine.setSubject(name)
                        AppSettings.selectedSubject = name
                        refreshSubjectSpinner()
                        render()
                        dialog.dismiss()
                    } else {
                        toast("Enter a name that isn't already in the list")
                    }
                },
                "Cancel" to { dialog: AlertDialog -> dialog.dismiss() }
            )
        )
    }

    // ---------- Daily goal and focus length ----------

    private fun askDailyGoal() {
        val input = themedEditText(
            "${AppSettings.MIN_GOAL_MINUTES}-${AppSettings.MAX_GOAL_MINUTES} minutes",
            AppSettings.dailyGoalMinutes.toString(),
            numeric = true
        )
        showThemedPanel(
            content = listOf(bodyText("Study goal per day, in minutes (for example 240 = 4 hours)"), input),
            buttons = listOf(
                "Save" to { dialog: AlertDialog ->
                    val minutes = input.text.toString().toIntOrNull()
                    if (minutes == null || minutes !in AppSettings.MIN_GOAL_MINUTES..AppSettings.MAX_GOAL_MINUTES) {
                        toast("Enter ${AppSettings.MIN_GOAL_MINUTES} to ${AppSettings.MAX_GOAL_MINUTES} minutes")
                    } else {
                        AppSettings.dailyGoalMinutes = minutes
                        render()
                        dialog.dismiss()
                    }
                },
                "Cancel" to { dialog: AlertDialog -> dialog.dismiss() }
            )
        )
    }

    private fun askFocusLength() {
        if (TimerEngine.running) {
            toast("Pause or reset the timer before changing the length")
            return
        }
        val input = themedEditText(
            "${AppSettings.MIN_FOCUS_MINUTES}-${AppSettings.MAX_FOCUS_MINUTES} minutes",
            AppSettings.focusMinutes.toString(),
            numeric = true
        )
        showThemedPanel(
            content = listOf(bodyText("Minutes per focus session"), input),
            buttons = listOf(
                "Save" to { dialog: AlertDialog ->
                    val minutes = input.text.toString().toIntOrNull()
                    if (minutes == null || minutes !in AppSettings.MIN_FOCUS_MINUTES..AppSettings.MAX_FOCUS_MINUTES) {
                        toast("Enter ${AppSettings.MIN_FOCUS_MINUTES} to ${AppSettings.MAX_FOCUS_MINUTES} minutes")
                    } else {
                        AppSettings.focusMinutes = minutes
                        TimerEngine.refreshDuration()
                        render()
                        dialog.dismiss()
                    }
                },
                "Cancel" to { dialog: AlertDialog -> dialog.dismiss() }
            )
        )
    }

    // ---------- Menu, account, theme and leaderboard ----------

    /** The ⋮ menu, drawn in the app's theme colours. */
    private fun showMenu(anchor: View) {
        val t = theme
        val signedIn = Account.currentUser() != null
        val menuView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            background = rounded(t.surface, 16)
        }
        val popup = PopupWindow(menuView, dp(240), ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.elevation = dp(8).toFloat()

        // Dark mode switch: on is Night sky, off is Lovely mountains. Same padding as the other rows.
        val darkSwitch = Switch(this).apply {
            isChecked = theme == AppTheme.NIGHT_SKY
            thumbTintList = ColorStateList.valueOf(t.accent)
            trackTintList = ColorStateList.valueOf(t.track)
            setOnCheckedChangeListener { _, checked ->
                popup.dismiss()
                setDarkMode(checked)
            }
        }
        menuView.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
            addView(menuLabel("Dark mode").apply { setPadding(0, 0, 0, 0) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(darkSwitch, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })

        val items: List<Pair<String, () -> Unit>> = listOf(
            (if (signedIn) "Sign out" else "Sign in with Google") to { onAccountMenu() },
            "Leaderboard" to { showLeaderboard() },
            "Show walkthrough" to { showWalkthrough(0) },
            "About Focus Nest" to { showAbout() },
        )
        for ((label, action) in items) {
            menuView.addView(menuLabel(label).apply {
                setOnClickListener {
                    popup.dismiss()
                    action()
                }
            })
        }

        // Line the menu's right edge up with the ⋮ button
        popup.showAsDropDown(anchor, anchor.width - dp(240), 0)
    }

    private fun menuLabel(label: String): TextView = TextView(this).apply {
        text = label
        textSize = 17f
        setTypeface(null, Typeface.BOLD)
        setTextColor(theme.text)
        setPadding(dp(20), dp(14), dp(20), dp(14))
    }

    private fun setDarkMode(dark: Boolean) {
        ThemeStore.save(this, if (dark) AppTheme.NIGHT_SKY else AppTheme.MOUNTAINS)
        recreate()
    }

    private fun onAccountMenu() {
        if (Account.currentUser() != null) {
            Account.signOut(this)
            render()
            return
        }
        runCatching { signInLauncher.launch(Account.signInIntent(this)) }
            .onFailure { toast(it.message ?: "Sign-in is not available") }
    }

    private fun showLeaderboard() {
        if (Account.currentUser() == null) {
            toast("Sign in to see the leaderboard")
            return
        }
        Account.leaderboard { result ->
            val message = result.fold(
                onSuccess = { rows ->
                    if (rows.isEmpty()) "No points yet today. Finish a focus session to appear here."
                    else rows.mapIndexed { i, (name, points) -> "${i + 1}. $name · $points pts" }.joinToString("\n")
                },
                onFailure = { "Could not load the leaderboard: ${it.message}" }
            )
            runOnUiThread {
                showThemedPanel(
                    content = listOf(
                        bodyText("Today's leaderboard").apply { setTypeface(null, Typeface.BOLD); textSize = 20f },
                        bodyText(message)
                    ),
                    buttons = listOf("Close" to { dialog: AlertDialog -> dialog.dismiss() })
                )
            }
        }
    }

    // ---------- First-run walkthrough ----------

    /** Explains one section at a time. Opened on first launch, and from the ⋮ menu. */
    private fun showWalkthrough(step: Int) {
        val t = theme
        val total = WALKTHROUGH_STEPS.size
        val (title, body) = WALKTHROUGH_STEPS[step]
        val last = step == total - 1

        val dots = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            for (i in 0 until total) {
                addView(View(this@MainActivity).apply {
                    background = rounded(if (i == step) t.accent else t.track, 4)
                    layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                        leftMargin = dp(4)
                        rightMargin = dp(4)
                    }
                })
            }
        }

        val buttons = mutableListOf<Pair<String, PanelAction>>()
        if (last) {
            buttons += "Got it" to { dialog: AlertDialog -> dialog.dismiss() }
        } else {
            buttons += "Next" to { dialog: AlertDialog ->
                dialog.dismiss()
                showWalkthrough(step + 1)
            }
        }
        if (step > 0) {
            buttons += "Back" to { dialog: AlertDialog ->
                dialog.dismiss()
                showWalkthrough(step - 1)
            }
        }
        if (!last) {
            buttons += "Skip" to { dialog: AlertDialog -> dialog.dismiss() }
        }

        showThemedPanel(
            content = listOf(
                bodyText("Step ${step + 1} of $total").apply { textSize = 13f; setTextColor(t.secondaryText) },
                bodyText(title).apply { textSize = 22f; setTypeface(null, Typeface.BOLD) },
                bodyText(body).apply { textSize = 16f },
                dots
            ),
            buttons = buttons
        )
    }

    // ---------- About and session complete ----------

    private fun showSessionComplete(points: Int) {
        val t = theme

        val trophy = ImageView(this).apply {
            setImageResource(R.drawable.ic_trophy)
            layoutParams = LinearLayout.LayoutParams(dp(96), dp(96)).apply { gravity = Gravity.CENTER_HORIZONTAL }
        }
        val heading = TextView(this).apply {
            text = "Session complete!"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(t.text)
            setTypeface(null, Typeface.BOLD)
        }
        val pointsBig = TextView(this).apply {
            text = "+$points"
            textSize = 48f
            gravity = Gravity.CENTER
            setTextColor(t.accent)
            setTypeface(null, Typeface.BOLD)
        }
        val caption = TextView(this).apply {
            text = if (points > 0) {
                "focus points earned\nYour nest is growing!"
            } else {
                "Focus time saved.\nEvery 5 minutes of focus earns a point."
            }
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(t.secondaryText)
        }

        val dialog = AlertDialog.Builder(this).create()
        val nice = Button(this).apply {
            text = "Nice!"
            styleButton(this, primary = true)
            setOnClickListener { dialog.dismiss() }
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(24))
            background = rounded(t.surface, 28)
            addView(trophy)
            addView(heading, lp())
            addView(pointsBig, lp())
            addView(caption, lp())
            addView(nice, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(20)
            })
        }

        dialog.setView(panel)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun showAbout() {
        val t = theme

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_logo)
            layoutParams = LinearLayout.LayoutParams(dp(84), dp(84)).apply { gravity = Gravity.CENTER_HORIZONTAL }
        }
        val heading = TextView(this).apply {
            text = "Focus Nest"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(t.text)
            setTypeface(null, Typeface.BOLD)
        }
        val tagline = TextView(this).apply {
            text = "Study with focus, one session at a time."
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(t.secondaryText)
        }
        val body = TextView(this).apply {
            text = """
                Focus Nest helps students study in focused sessions.

                • Set your focus length and start the timer. Breaks are added for you.
                • Pick a subject, or add your own, so your time is tracked by topic.
                • Each study session is logged with its start and end time. Filter the daily log by today, last week, last month or total time.
                • Set a daily goal and earn a trophy when you reach it.
                • Play rain or lofi sounds in the background while you study.
                • Earn one point for every 5 minutes of focus. Sign in with Google to join the leaderboard and see how you rank today.

                Your timer, subjects, sounds, log and themes work offline. Sign-in and the leaderboard need an internet connection.
            """.trimIndent()
            textSize = 15f
            setTextColor(t.text)
            setLineSpacing(0f, 1.2f)
        }
        val version = TextView(this).apply {
            text = "Version ${packageManager.getPackageInfo(packageName, 0).versionName}"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(t.secondaryText)
        }

        val dialog = AlertDialog.Builder(this).create()
        val close = Button(this).apply {
            text = "Got it"
            styleButton(this, primary = true)
            setOnClickListener { dialog.dismiss() }
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(24), dp(24), dp(20))
            background = rounded(t.surface, 28)
            addView(logo)
            addView(heading, lp())
            addView(tagline, lp())
            addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(16)
            })
            addView(version, lp())
            addView(close, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(20)
            })
        }

        // Scroll in case the text is long on small screens
        val scroll = ScrollView(this).apply { addView(panel) }
        dialog.setView(scroll)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    // ---------- Themed popups and view helpers ----------

    /**
     * A popup card in the current theme. [content] is shown top to bottom, and [buttons] sit at the bottom.
     * The first button is the primary one.
     */
    private fun showThemedPanel(content: List<View>, buttons: List<Pair<String, PanelAction>>) {
        val t = theme
        val dialog = AlertDialog.Builder(this).create()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(20))
            background = rounded(t.surface, 28)
            content.forEach { addView(it, lp()) }
            buttons.forEachIndexed { index, (label, action) ->
                addView(Button(this@MainActivity).apply {
                    text = label
                    styleButton(this, primary = index == 0)
                    setOnClickListener { action(dialog) }
                }, lp())
            }
        }
        dialog.setView(ScrollView(this).apply { addView(panel) })
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun styleButton(button: Button, primary: Boolean) {
        val t = theme
        button.isAllCaps = false
        button.setTextColor(if (primary) t.onAccent else t.text)
        button.background = rounded(if (primary) t.accent else t.surface, 24)
    }

    /** Dropdown with a down arrow on the right. The padding keeps the text clear of the arrow. */
    private fun spinner(items: List<String>, selected: Int, onPick: (Int) -> Unit): Spinner {
        val t = theme
        return Spinner(this).apply {
            adapter = ThemedAdapter(this@MainActivity, items, t.text, t.surface)
            background = dropdownBackground()
            setPadding(dp(16), dp(12), dp(48), dp(12))
            // Popup list is as wide as the dropdown button
            dropDownWidth = ViewGroup.LayoutParams.MATCH_PARENT
            setSelection(selected)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) =
                    onPick(position)

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
    }

    /** Rounded text box in the theme colours. */
    private fun themedEditText(hintText: String, initial: String, numeric: Boolean = false): EditText =
        EditText(this).apply {
            hint = hintText
            setText(initial)
            maxLines = 1
            inputType = if (numeric) {
                InputType.TYPE_CLASS_NUMBER
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            }
            setTextColor(theme.text)
            setHintTextColor(theme.secondaryText)
            textSize = 16f
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rounded(theme.surface, 16)
        }

    private fun bodyText(message: String): TextView = TextView(this).apply {
        text = message
        textSize = 15f
        setTextColor(theme.text)
    }

    private fun dropdownBackground(): LayerDrawable {
        val t = theme
        val box = rounded(t.surface, 16)
        val arrow: Drawable = checkNotNull(getDrawable(R.drawable.ic_chevron_down)).mutate()
        arrow.setTint(t.text)
        return LayerDrawable(arrayOf(box, arrow)).apply {
            setLayerGravity(1, Gravity.END or Gravity.CENTER_VERTICAL)
            setLayerWidth(1, dp(24))
            setLayerHeight(1, dp(24))
            setLayerInsetRight(1, dp(14))
        }
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(radiusDp).toFloat()
        setColor(color)
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(theme.surface, 20)
        setPadding(dp(16), dp(16), dp(16), dp(16))
    }

    private fun text(size: Float) = TextView(this).apply {
        textSize = size
        setTextColor(theme.text)
    }

    private fun section(title: String) = text(18f).apply {
        text = title
        setTypeface(null, Typeface.BOLD)
    }

    private fun lp() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(12) }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val MENU_ACCOUNT = 1
    }
}
