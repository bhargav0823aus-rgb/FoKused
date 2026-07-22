package com.focusgate.launcher.model

/** One bubble in the chat. Ephemeral by design — no persistence in FocusGate. */
data class ChatMessage(
    val id: Long,
    val text: String,
    val fromUser: Boolean,
    val isError: Boolean = false,
)
