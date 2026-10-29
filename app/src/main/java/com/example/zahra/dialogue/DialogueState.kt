package com.example.zahra.dialogue

/** Session state only. Values point to an actual user turn; never reconstruct from a greeting. */
enum class Slot { CITY, DATE, TIME, CURRENCY, UNIT, TOPIC, PERSON, LANGUAGE, CODE_LANG }
enum class SlotSource { USER, INFERRED, DEFAULT }
data class SlotValue(val value: String, val confidence: Double, val source: SlotSource, val turn: Int, val expiresAfterTurn: Int? = null)
data class DialogueTurn(val user: String, val response: String, val intent: String, val entities: List<String> = emptyList(), val id: Int = 0)
data class Clarification(val original: String, val slot: Slot, val options: List<String>)

class DialogueState {
    private val history = mutableListOf<DialogueTurn>()
    private val values = mutableMapOf<Slot, SlotValue>()
    private var turnIndex = 0
    private val recentLimit = 20
    val turns: List<DialogueTurn> get() = history.toList()
    val slots: Map<Slot, SlotValue> get() = values.filterValues { it.expiresAfterTurn == null || turnIndex <= it.expiresAfterTurn }
    var lastUnitId: String? = null
        private set
    private var lastUnitTurn: Int? = null
    var mood: String? = null
        private set
    var pendingClarification: Clarification? = null
    var lastWasClarification: Boolean = false
    val lastEntity: String? get() = slots[Slot.TOPIC]?.value ?: slots[Slot.CITY]?.value
    val lastRoute: String? get() = history.lastOrNull()?.intent

    fun record(user: String, response: String, intent: String, city: String? = null, topic: String? = null, date: String? = null, entities: List<String> = emptyList(), extraSlots: Map<Slot, String> = emptyMap(), unitId: String? = null, moodName: String? = null) {
        val index = ++turnIndex
        val priorIntent = history.lastOrNull()?.intent
        if ((topic != null && values[Slot.TOPIC]?.value != topic) ||
            (priorIntent != null && intent != priorIntent && intent !in setOf("clarification", "unknown", "dialogue_abstain"))) {
            values.remove(Slot.DATE)
            lastUnitId = null
            lastUnitTurn = null
        }
        if (lastUnitTurn?.let { index - it > 3 } == true) { lastUnitId = null; lastUnitTurn = null }
        if (unitId != null) { lastUnitId = unitId; lastUnitTurn = index }
        mood = moodName
        city?.let { values[Slot.CITY] = SlotValue(it, 1.0, SlotSource.USER, index) }
        topic?.let { values[Slot.TOPIC] = SlotValue(it, 1.0, SlotSource.USER, index, index + 3) }
        date?.let { values[Slot.DATE] = SlotValue(it, 1.0, SlotSource.USER, index, index + 1) }
        extraSlots.forEach { (slot, value) ->
            if (slot !in setOf(Slot.CITY, Slot.DATE, Slot.TOPIC) && value.isNotBlank())
                values[slot] = SlotValue(value, 1.0, SlotSource.USER, index, index + 3)
        }
        history.add(DialogueTurn(user, response, intent, (listOfNotNull(city, topic) + entities).distinct(), index))
        // Keep the city source even when it falls outside the recent window; other
        // live values expire within the window. Slots retain absolute turn numbers.
        val citySource = values[Slot.CITY]?.turn
        while (history.size > recentLimit + if (citySource != null && citySource <= index - recentLimit) 1 else 0) {
            val removable = history.indexOfFirst { it.id != citySource }
            if (removable < 0) break
            history.removeAt(removable)
        }
    }

    fun invalidateTopic() { values.remove(Slot.TOPIC); values.remove(Slot.DATE); lastUnitId = null; lastUnitTurn = null }

    fun clearConversation(): Int {
        val count = history.size
        history.clear(); values.clear(); turnIndex = 0; lastUnitId = null; lastUnitTurn = null; mood = null; pendingClarification = null; lastWasClarification = false
        return count
    }
}
