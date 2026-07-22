package com.focusgate.launcher.schedule

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * FoKused's shared, process-wide state (SharedPreferences-backed singleton):
 *  - app→category tags, used only to pick a cost tier (entertainment vs normal),
 *  - the **coin balance** — earned by keeping the screen OFF, spent to open apps,
 *  - the active approved session (so the eject service leaves a paid app alone).
 *
 * Shared by the chat gate ([com.focusgate.launcher.ui.ChatViewModel]), the earning
 * receiver ([com.focusgate.launcher.FoKusedApplication]) and the eject service
 * ([com.focusgate.launcher.service.FocusAccessibilityService]).
 */
class ScheduleRepository private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("focusgate_schedule", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val catSerializer = MapSerializer(String.serializer(), Category.serializer())

    private val _categories = MutableStateFlow(loadCategories())
    val categories: StateFlow<Map<String, Category>> = _categories.asStateFlow()

    private val _coins = MutableStateFlow(prefs.getInt(KEY_COINS, START_COINS))
    val coins: StateFlow<Int> = _coins.asStateFlow()

    @Volatile private var sessionPkg: String? = null
    @Volatile private var sessionEndMillis: Long = 0L

    // ---- categories & cost tier ----

    fun hasSeenIntro(): Boolean = prefs.getBoolean(KEY_INTRO, false)
    fun markIntroSeen() = prefs.edit { putBoolean(KEY_INTRO, true) }

    fun hasCategories(): Boolean = _categories.value.isNotEmpty()

    fun categoryOf(pkg: String): Category =
        _categories.value[pkg] ?: AppCategorizer.defaultFor(pkg)

    fun setCategories(map: Map<String, Category>) {
        _categories.value = map
        prefs.edit { putString(KEY_CATEGORIES, json.encodeToString(catSerializer, map)) }
    }

    private fun loadCategories(): Map<String, Category> {
        val raw = prefs.getString(KEY_CATEGORIES, null) ?: return emptyMap()
        return runCatching { json.decodeFromString(catSerializer, raw) }.getOrDefault(emptyMap())
    }

    /** Coins per minute: entertainment is 10× the price of normal apps. */
    fun rateFor(pkg: String): Int = when (categoryOf(pkg)) {
        Category.SOCIAL, Category.VIDEO, Category.GAMES -> ENTERTAINMENT_RATE
        else -> NORMAL_RATE
    }

    fun costFor(pkg: String, minutes: Int): Int = rateFor(pkg) * minutes

    fun isEntertainment(pkg: String): Boolean = rateFor(pkg) >= ENTERTAINMENT_RATE

    // ---- coins ----

    fun canAfford(cost: Int): Boolean = _coins.value >= cost

    fun spend(cost: Int): Boolean {
        if (_coins.value < cost) return false
        setCoins(_coins.value - cost)
        return true
    }

    private fun setCoins(value: Int) {
        _coins.value = value.coerceAtLeast(0)
        prefs.edit { putInt(KEY_COINS, _coins.value) }
    }

    // ---- screen-off earning (1 coin / minute the screen is off) ----

    fun onScreenOff() {
        prefs.edit { putLong(KEY_OFF_AT, System.currentTimeMillis()) }
    }

    /**
     * Award coins for the minutes the screen has been off since [onScreenOff],
     * accumulate them for a later "you earned X" chat note, and clear the marker.
     * Safe to call from the SCREEN_ON receiver AND on app resume (idempotent —
     * the marker is consumed once). Returns minutes awarded this call.
     */
    fun settleScreenOff(): Int {
        val offAt = prefs.getLong(KEY_OFF_AT, 0L)
        if (offAt <= 0L) return 0
        val mins = ((System.currentTimeMillis() - offAt) / 60_000L).toInt().coerceAtLeast(0)
        prefs.edit { remove(KEY_OFF_AT) }
        if (mins > 0) {
            setCoins(_coins.value + mins)
            prefs.edit { putInt(KEY_PENDING, prefs.getInt(KEY_PENDING, 0) + mins) }
        }
        return mins
    }

    /** Earned-but-not-yet-announced coins; reading clears the counter. */
    fun takePendingAnnounce(): Int {
        val p = prefs.getInt(KEY_PENDING, 0)
        if (p != 0) prefs.edit { remove(KEY_PENDING) }
        return p
    }

    // ---- active approved session ----

    fun startSession(pkg: String, minutes: Int) {
        sessionPkg = pkg
        sessionEndMillis = System.currentTimeMillis() + minutes * 60_000L
    }

    fun clearSession() {
        sessionPkg = null
        sessionEndMillis = 0L
    }

    fun isActiveSession(pkg: String): Boolean =
        pkg == sessionPkg && System.currentTimeMillis() < sessionEndMillis

    companion object {
        private const val KEY_CATEGORIES = "app_categories"
        private const val KEY_INTRO = "seen_intro"
        private const val KEY_COINS = "coins"
        private const val KEY_OFF_AT = "screen_off_at"
        private const val KEY_PENDING = "pending_earned"
        private const val START_COINS = 30
        private const val NORMAL_RATE = 1
        private const val ENTERTAINMENT_RATE = 10

        @Volatile private var instance: ScheduleRepository? = null

        fun getInstance(context: Context): ScheduleRepository =
            instance ?: synchronized(this) {
                instance ?: ScheduleRepository(context.applicationContext).also { instance = it }
            }
    }
}
