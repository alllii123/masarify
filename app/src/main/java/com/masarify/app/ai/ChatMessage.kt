package com.masarify.app.ai

import java.time.LocalDateTime
import java.util.UUID

/** رسالة واحدة في محادثة المساعد. */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,             // "user" أو "model"
    val text: String,
    val time: LocalDateTime = LocalDateTime.now(),
    val source: String? = null,   // "local" أو "cloud" أو "error"
    val error: String? = null,
    /** زمن رد السحابة بالمللي — يظهر للمستخدم ليعرف السرعة الفعلية. */
    val elapsedMs: Long? = null,
    val model: String? = null
)
