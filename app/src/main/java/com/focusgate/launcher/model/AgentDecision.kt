package com.focusgate.launcher.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The compact contract the model is instructed to return:
 * {"app":"...","purpose":"...","category":"tool|distraction","minutes":N,
 *  "verdict":"approve|question","reply":"..."}
 *
 * Every field has a default so a partially-formed object still deserializes;
 * the safe fallback verdict is "question" (never auto-approve on garbage).
 */
@Serializable
data class AgentDecision(
    val app: String = "",
    val purpose: String = "",
    val category: String = "distraction",
    val minutes: Int = 0,
    val verdict: String = "question",
    val reply: String = "",
)

object DecisionParser {

    private val json = Json {
        ignoreUnknownKeys = true   // extra fields the model invents are dropped
        isLenient = true           // tolerates quoted numbers / unquoted strings
        coerceInputValues = true   // null or wrong-typed fields fall back to defaults
    }

    /**
     * Small on-device models routinely wrap JSON in prose or ```json fences
     * despite instructions. Cut from the first '{' to the last '}' and parse
     * only that slice; anything unparseable returns null and the caller shows
     * a retry message instead of acting.
     */
    fun parse(raw: String): AgentDecision? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            json.decodeFromString<AgentDecision>(raw.substring(start, end + 1))
        } catch (_: Exception) {
            null
        }
    }
}
