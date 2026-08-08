package com.example.lolipet.ai

import java.util.concurrent.CopyOnWriteArrayList

/** 对话上下文管理器（保留最近 N 条历史） */
object ChatSession {
    private const val MAX_HISTORY = 12
    private val history = CopyOnWriteArrayList<DeepSeekClient.ChatMessage>()

    fun addUser(text: String) {
        history.add(DeepSeekClient.ChatMessage("user", text))
        trim()
    }

    fun addAssistant(text: String) {
        history.add(DeepSeekClient.ChatMessage("assistant", text))
        trim()
    }

    fun getMessages(): List<DeepSeekClient.ChatMessage> = history.toList()

    fun clear() = history.clear()

    private fun trim() {
        while (history.size > MAX_HISTORY) {
            history.removeAt(0)
        }
    }
}
