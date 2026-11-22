package com.riverrunner.web

import com.riverrunner.engine.AchievementDef
import com.riverrunner.engine.Achievements
import com.riverrunner.engine.HandRecord
import com.riverrunner.engine.Progress
import kotlinx.browser.window
import kotlin.js.Date

internal class SaveSlot(
    val id: String,
    var name: String,
    val history: MutableList<HandRecord> = mutableListOf(),
    val counters: MutableMap<String, Long> = mutableMapOf(),
    val unlocked: MutableMap<String, Long> = mutableMapOf(),
)

internal class BrowserStore(defaultName: (Int) -> String) {
    private val fallback = mutableMapOf<String, String>()
    var writeFailed = false
        private set

    val slots = get("slots").orEmpty().lineSequence().mapNotNull { line ->
        val pieces = line.split('|', limit = 2)
        if (pieces.size != 2) null else loadSlot(pieces[0], pieces[1])
    }.toMutableList()
    var active = slots.firstOrNull { it.id == get("active") } ?: slots.firstOrNull()
        ?: newSlot(defaultName(1))
        private set

    init { select(active) }

    fun get(key: String): String? = fallback[key]
        ?: runCatching { window.localStorage.getItem("river-runner.v1.$key") }.getOrNull()

    fun put(key: String, value: String) {
        val saved = runCatching { window.localStorage.setItem("river-runner.v1.$key", value) }.isSuccess
        if (saved) fallback.remove(key) else {
            fallback[key] = value
            writeFailed = true
        }
    }

    fun flag(key: String, default: Boolean = false): Boolean = get(key)?.toBooleanStrictOrNull() ?: default

    fun add(name: String) {
        if (slots.size >= 6) return
        select(newSlot(name))
    }

    fun select(slot: SaveSlot) {
        active = slot
        put("active", slot.id)
        saveNames()
    }

    fun rename(slot: SaveSlot, name: String) {
        slot.name = safeName(name)
        saveNames()
    }

    fun remove(slot: SaveSlot) {
        if (slots.size <= 1 || !slots.remove(slot)) return
        if (slot == active) select(slots.first()) else saveNames()
        for (key in listOf("history", "counters", "unlocked")) {
            fallback.remove("$key.${slot.id}")
            runCatching { window.localStorage.removeItem("river-runner.v1.$key.${slot.id}") }
        }
    }

    fun record(hand: HandRecord) {
        active.history.add(0, hand)
        var ordinaryHands = 0
        active.history.removeAll { !it.favorite && ++ordinaryHands > 1000 }
        saveHistory()
    }

    fun toggleFavorite(hand: HandRecord): HandRecord {
        val index = active.history.indexOfFirst { it.id == hand.id }
        if (index < 0) return hand
        val updated = active.history[index].let { it.copy(favorite = !it.favorite) }
        active.history[index] = updated
        saveHistory()
        if (updated.favorite) recordProgress(mapOf(Progress.STARS to 1))
        return updated
    }

    fun clearHistory() {
        active.history.removeAll { !it.favorite }
        saveHistory()
    }

    fun recordProgress(delta: Map<String, Long>): List<AchievementDef> {
        Progress.apply(active.counters, delta)
        val unlocked = Achievements.newlyUnlocked(active.counters, active.unlocked.keys)
        for (badge in unlocked) active.unlocked[badge.id] = Date.now().toLong()
        put("counters.${active.id}", encodeMap(active.counters))
        if (unlocked.isNotEmpty()) put("unlocked.${active.id}", encodeMap(active.unlocked))
        return unlocked
    }

    private fun safeName(name: String): String = name.trim().take(40).replace(Regex("[|\\r\\n]"), " ")
    private fun newSlot(name: String): SaveSlot =
        SaveSlot("${Date.now().toLong()}-${kotlin.random.Random.nextInt()}", safeName(name)).also { slots += it }

    private fun saveNames() = put("slots", slots.joinToString("\n") { "${it.id}|${it.name}" })
    private fun saveHistory() = put("history.${active.id}", active.history.joinToString("\n") { it.encode() })

    private fun loadSlot(id: String, name: String) = SaveSlot(
        id, name,
        get("history.$id").orEmpty().lineSequence().mapNotNull(HandRecord::decode).toMutableList(),
        decodeMap(get("counters.$id")),
        decodeMap(get("unlocked.$id")),
    )
}

private fun encodeMap(values: Map<String, Long>) = values.entries.joinToString(",") { "${it.key}=${it.value}" }

private fun decodeMap(text: String?): MutableMap<String, Long> = text.orEmpty().split(',').mapNotNull {
    val key = it.substringBefore('=')
    it.substringAfter('=', "").toLongOrNull()?.let { value -> key to value }
}.toMap().toMutableMap()
