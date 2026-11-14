package com.masarify.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * عميل Gemini اختياري.
 *
 * - المفتاح يبقى على الجهاز في DataStore ولا يُسجّل في أي log.
 * - يجرّب تلقائيًا أكثر من اسم موديل، لأن Google تُعيد تسمية/إنهاء الموديلات.
 * - يحوّل أخطاء الشبكة إلى رسائل عربية مفهومة بدل Timeout غامض.
 */
object CloudAiClient {

    data class Message(val role: String, val text: String)

    private const val HOST = "https://generativelanguage.googleapis.com/v1beta"

    /** مهلة أطول من السابق (كانت 30 ثانية إجمالًا) لأن الشبكات المتنقلة قد تبطئ. */
    private const val CONNECT_TIMEOUT = 25_000
    private const val READ_TIMEOUT = 90_000

    /** الأحدث أولًا. لو رجع 404 ننتقل للتالي. */
    val candidateModels = listOf(
        "gemini-flash-latest",
        "gemini-3.5-flash",
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-3.1-flash-lite"
    )

    /** يقرأ الموديلات المتاحة فعليًا على مفتاح المستخدم. */
    suspend fun listModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL("$HOST/models?pageSize=200").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("x-goog-api-key", apiKey)
            }
            val code = connection.responseCode
            val payload = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (code !in 200..299) error(friendlyHttpError(code, payload))

            val models = JSONObject(payload).optJSONArray("models") ?: JSONArray()
            buildList {
                for (i in 0 until models.length()) {
                    val model = models.getJSONObject(i)
                    val methods = model.optJSONArray("supportedGenerationMethods")
                    val canGenerate = methods == null || (0 until methods.length()).any {
                        methods.optString(it).contains("generateContent")
                    }
                    val name = model.optString("name").removePrefix("models/")
                    if (canGenerate && name.isNotBlank()) add(name)
                }
            }
        }
    }

    suspend fun ask(
        apiKey: String,
        model: String,
        style: String,
        context: String,
        history: List<Message>
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val system = buildString {
                append("أنت مساعد مالي شخصي داخل تطبيق «مصاريفي» في قطر. ")
                append("استخدم الريال القطري (ر.ق) في كل الأرقام. ")
                append("أجب بالعربية العامية الخليجية المبسطة، باختصار وبدون مقدمات طويلة. ")
                append("اعتمد فقط على الأرقام الموجودة في السياق ولا تخترع أرقامًا. ")
                append("لو طُلب منك نصيحة استثمار أو قرض، وضّح أن هذا ليس استشارة مالية رسمية. ")
                append("الأسلوب المطلوب: $style. ")
                append("سياق بيانات المستخدم: $context")
            }

            val contents = JSONArray().apply {
                history.takeLast(10).forEach { message ->
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
                    put("maxOutputTokens", 900)
                })
            }.toString()

            val modelsToTry = (listOf(model.ifBlank { "" }) + candidateModels).filter { it.isNotBlank() }.distinct()
            var lastError = "تعذّر الاتصال بالمساعد."

            for (candidate in modelsToTry) {
                val connection = (URL("$HOST/models/$candidate:generateContent").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT
                    readTimeout = READ_TIMEOUT
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("x-goog-api-key", apiKey)
                }

                try {
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val code = connection.responseCode
                    val payload = (if (code in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

                    if (code == 404) {
                        lastError = "الموديل «$candidate» غير متاح لمفتاحك."
                        continue // جرّب الموديل التالي
                    }
                    if (code !in 200..299) error(friendlyHttpError(code, payload))

                    val root = JSONObject(payload)
                    if (root.has("promptFeedback")) {
                        val reason = root.getJSONObject("promptFeedback").optString("blockReason")
                        if (reason.isNotBlank()) error("تم حجب الطلب من Google ($reason). أعد صياغة السؤال بشكل مالي واضح.")
                    }

                    val text = runCatching {
                        root.getJSONArray("candidates").getJSONObject(0)
                            .getJSONObject("content").getJSONArray("parts")
                            .let { parts ->
                                buildString {
                                    for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text"))
                                }
                            }
                    }.getOrNull()?.trim().orEmpty()

                    if (text.isBlank()) error("وصل رد فارغ من المساعد. جرّب صيغة سؤال ثانية.")
                    return@runCatching text
                } catch (e: java.net.SocketTimeoutException) {
                    error("انتهت مدة الانتظار بدون رد. تحقق من الإنترنت أو جرّب موديل أسرع مثل gemini-2.5-flash.")
                } catch (e: java.net.UnknownHostException) {
                    error("لا يوجد اتصال بالإنترنت. الوضع المحلي يشتغل بدون إنترنت.")
                } catch (e: java.io.IOException) {
                    error("انقطاع في الشبكة: ${e.message ?: "تحقق من الاتصال"}")
                } finally {
                    connection.disconnect()
                }
            }
            error("$lastError جرّب تحديث الموديلات من الإعدادات.")
        }
    }

    suspend fun checkKey(apiKey: String, model: String): Result<String> =
        ask(apiKey, model, "مختصر جدًا", "snapshot: currency=QAR", emptyList())

    private fun friendlyHttpError(code: Int, payload: String): String {
        val detail = runCatching {
            JSONObject(payload).getJSONObject("error").optString("message")
        }.getOrDefault("")
        return when (code) {
            400 -> "الطلب غير صالح${if (detail.isNotBlank()) ": $detail" else "."}"
            401, 403 -> "المفتاح غير مقبول. تأكد أنك فعّلت Generative Language API وأن المفتاح صحيح."
            404 -> "الموديل غير موجود. جرّب «تحديث الموديلات» في الإعدادات."
            429 -> "تجاوزت حد الطلبات المجاني للمفتاح. انتظر دقيقة أو استخدم الوضع المحلي."
            500, 503 -> "خدمة Google مشغولة حاليًا. جرّب بعد قليل."
            else -> "فشل الاتصال (رمز $code)${if (detail.isNotBlank()) ": $detail" else "."}"
        }
    }
}
