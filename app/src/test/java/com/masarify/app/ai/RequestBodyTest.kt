package com.masarify.app.ai

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * يتحقق من شكل جسم الطلب الذي يرسله التطبيق إلى Gemini.
 * الهدف: ضمان أن الدرجة «الأدنى» في سلّم التوافق تحتوي `contents` فقط،
 * لأن هذا هو سبب خطأ "Request contains an invalid argument" الذي واجهه المستخدم.
 */
class RequestBodyTest {

    private fun firstLevelBody(): JSONObject {
        // محاكاة ما يبنيه CloudAiClient في BodyLevel.MINIMAL
        val contents = JSONArray().put(
            JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", "كم باقي لي؟")))
            }
        )
        return JSONObject().put("contents", contents)
    }

    @Test
    fun `minimal body has contents only`() {
        val body = firstLevelBody()
        assertEquals(setOf("contents"), body.keys().asSequence().toSet())
        assertFalse(body.has("system_instruction"))
        assertFalse(body.has("generationConfig"))
    }

    @Test
    fun `contents must carry role and parts`() {
        val body = firstLevelBody()
        val first = body.getJSONArray("contents").getJSONObject(0)
        assertEquals("user", first.optString("role"))
        assertEquals(
            "كم باقي لي؟",
            first.getJSONArray("parts").getJSONObject(0).optString("text")
        )
    }

    @Test
    fun `thinkingConfig must never be sent`() {
        // الحقل الذي سبب الرفض سابقًا
        val encoded = firstLevelBody().toString()
        assertFalse(encoded.contains("thinkingConfig"))
    }

    @Test
    fun `sse parsing ignores non data lines`() {
        val sse = buildString {
            append("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"مرحبا\"}]}}]}\n")
            append("\n")
            append("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\" بك\"}]}}]}\n")
        }
        val builder = StringBuilder()
        sse.lineSequence().forEach { raw ->
            if (!raw.startsWith("data:")) return@forEach
            val json = raw.removePrefix("data:").trim()
            if (json.isEmpty()) return@forEach
            val text = JSONObject(json).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).optString("text")
            builder.append(text)
        }
        assertEquals("مرحبا بك", builder.toString())
    }

    @Test
    fun `model filter excludes non chat models`() {
        val available = listOf(
            "gemini-2.5-flash-lite",
            "gemini-2.5-pro-preview-tts",
            "gemini-3.1-flash-image",
            "text-embedding-004",
            "gemma-4-26b-a4b-it",
            "gemini-flash-latest"
        )
        val usable = available.filterNot {
            it.contains("tts") || it.contains("image") || it.contains("embedding") || it.startsWith("gemma")
        }
        assertEquals(listOf("gemini-2.5-flash-lite", "gemini-flash-latest"), usable)
    }
}
