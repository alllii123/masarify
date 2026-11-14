package com.masarify.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * عميل Gemini اختياري. المفتاح يبقى على الجهاز في DataStore ولا يُرسل لأي طرف ثالث
 * غير Google نفسها، ولا يُكتب في أي سجل (log).
 */
object CloudAiClient {

    data class Message(val role: String, val text: String)

    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"

    suspend fun ask(
        apiKey: String,
        model: String,
        style: String,
        context: String,
        history: List<Message>
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val system = buildString {
                append("أنت مساعد مالي شخصي داخل تطبيق «مصاريفي» للمستخدم في قطر. ")
                append("استخدم الريال القطري (ر.ق) في كل الأرقام. ")
                append("أجب بالعربية العامية الخليجية المبسطة، باختصار وبدون مقدمات طويلة. ")
                append("اعتمد فقط على الأرقام الموجودة في السياق ولا تخترع أرقامًا. ")
                append("لو طلب المستخدم نصيحة استثمارية أو قرضًا، اذكر أن هذا ليس استشارة مالية رسمية. ")
                append("الأسلوب المطلوب: $style. ")
                append("سياق بيانات المستخدم: $context")
            }

            val contents = JSONArray().apply {
                history.takeLast(12).forEach { message ->
                    put(
                        JSONObject().apply {
                            put("role", if (message.role == "user") "user" else "model")
                            put("parts", JSONArray().put(JSONObject().put("text", message.text)))
                        }
                    )
                }
                if (history.isEmpty()) {
                    put(
                        JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().put(JSONObject().put("text", "أعطني ملخصًا سريعًا لوضعي المالي.")))
                        }
                    )
                }
            }

            val body = JSONObject().apply {
                put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.4)
                    put("maxOutputTokens", 700)
                })
            }.toString()

            val url = URL("$ENDPOINT/${model.ifBlank { "gemini-2.0-flash" }}:generateContent")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 45_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("x-goog-api-key", apiKey)
            }

            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val payload = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

            if (code !in 200..299) {
                val reason = runCatching {
                    JSONObject(payload).getJSONObject("error").optString("message")
                }.getOrDefault("")
                error(when {
                    code == 401 || code == 403 -> "المفتاح غير مقبول من Google. تأكد من صحة المفتاح ومن تفعيل واجهة Gemini API."
                    code == 429 -> "وصلت حد الطلبات المسموح للمفتاح. جرّب بعد شوي أو استخدم الوضع المحلي."
                    reason.isNotBlank() -> "تعذّر الاتصال بالمساعد: $reason"
                    else -> "تعذّر الاتصال بالمساعد (رمز $code)."
                })
            }

            val text = JSONObject(payload)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .let { parts ->
                    buildString {
                        for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text"))
                    }
                }
                .trim()

            if (text.isBlank()) error("وصل رد فارغ من المساعد. جرّب صياغة ثانية أو الوضع المحلي.")
            text
        }
    }

    suspend fun checkKey(apiKey: String, model: String): Result<Unit> =
        ask(apiKey, model, "مختصر", "snapshot: currency=QAR", emptyList()).map { }
}
