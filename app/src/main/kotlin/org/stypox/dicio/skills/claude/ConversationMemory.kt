package org.stypox.dicio.skills.claude

object ConversationMemory {
    data class Turn(val question: String, val answer: String)

    private const val MAX_TURNS = 6

    private val turns = ArrayDeque<Turn>()

    @Synchronized
    fun get(): List<Turn> = turns.toList()

    @Synchronized
    fun add(question: String, answer: String) {
        turns.addLast(Turn(question, answer))
        while (turns.size > MAX_TURNS) {
            turns.removeFirst()
        }
    }

    @Synchronized
    fun clear() {
        turns.clear()
    }
}
