package com.jetsetter.pro.core.ai

/**
 * One chat turn for the IRIS assistant. [role] uses Gemini's roles: "user" or "model"
 * (model = assistant). Decoupled from the UI's ChatMessage and from any provider DTO.
 */
data class AiMessage(val role: String, val text: String)
