package com.masarify.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * عميل Gemini اختياري مبني على OkHttp.
 *
 * لماذا OkHttp بدل HttpURLConnection؟
 * على شبكات الجوال يحدث كثيرًا أن الاتصال الأول يبقى معلّقًا (شوهد 111 ثانية)
 * ثم يُرفض طلب ثانٍ بـ UnknownHostException. OkHttp يعيد استخدام الاتصال،
 * ويتعامل مع IPv4/IPv6 وTLS بشكل أفضل، ويعطي مهلات واضحة لكل مرحلة.
 *
 * - المفتاح يبقى على الجهاز في DataStore ولا يُسجّل في أي log.
 * - يجرّب أكثر من اسم موديل لأن Google تُعيد تسمية/إنهاء الموديلات.
 */
object CloudAiClient {

    data class Message(val role: String, val text: String)

    private const val HOST = "https://generativelanguage.googleapis.com/v1beta"
    private const val CONNECT_TIMEOUT = 12L
    private const val READ_TIMEOUT = 30L
    /** إجمالي الطلب: 35 ثانية كحد أقصى، فلا ينتظر المستخدم أكثر. */
    private const val CALL_TIMEOUT = 35L

    private val JSON = "application/json; charset=utf-8".toMediaType()

    /**
     * HTTP/1.1 مقصود، لا HTTP/2:
     * على شبكات الجوال في قطر لوحظ أن اتصال HTTP/2 المعاد استخدامه يُعلّق عند POST
     * بينما GET ينجح. HTTP/1.1 يتصرف بشكل متوقع مع هذه الشبكات،
     * وفرق السرعة لا يذكر في طلبات قصيرة مثل هذه.
     */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT, TimeUnit.SECONDS)
            // لا نعيد استخدام الاتصالات: كل طلب يفتح اتصالًا نظيفًا،
            // فلا نرث اتصالًا ميتًا من طلب سابق.
            .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build()
    }

    /** الأسرع أولًا: موديلات lite ترد عادة في أقل من ثانية. */
    val candidateModels = listOf(
        "gemini-2.5-flash-lite",
        "gemini-flash-latest",
        "gemini-3.5-flash-lite",
        "gemini-2.5-flash",
        "gemini-3.5-flash"
    )

    val suggestedModels = listOf(
        "gemini-2.5-flash-lite" to "الأسرع (موصى به)",
        "gemini-flash-latest" to "الأحدث",
        "gemini-2.5-flash" to "متوازن",
        "gemini-2.5-pro" to "الأدق والأبطأ"
    )

    // ────────────────────────────────────────────────────────────────────────
    suspend fun listModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$HOST/models?pageSize=200")
                .header("x-goog-api-key", apiKey)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) error(friendlyHttpError(response.code, payload))

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
    }

    // ────────────────────────────────────────────────────────────────────────
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

            fun buildBody(withThinkingConfig: Boolean): String = JSONObject().apply {
                put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.4)
                    put("maxOutputTokens", 800)
                    if (withThinkingConfig) put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
                })
            }.toString()

            val modelsToTry = (listOf(model.ifBlank { "" }) + candidateModels).filter { it.isNotBlank() }.distinct()
            var lastError = "تعذّر الاتصال بالمساعد."

            for (candidate in modelsToTry) {
                var withThinking = true
                var attempt = 0
                while (attempt < 2) {
                    attempt++
                    val request = Request.Builder()
                        .url("$HOST/models/$candidate:generateContent")
                        .header("x-goog-api-key", apiKey)
                        .header("Content-Type", "application/json")
                        .post(buildBody(withThinking).toRequestBody(JSON))
                        .build()

                    val outcome = try {
                        client.newCall(request).execute().use { response ->
                            val payload = response.body?.string().orEmpty()
                            when {
                                response.code == 404 -> Outcome.Skip
                                response.code == 400 && payload.contains("thinking", true) && withThinking -> {
                                    withThinking = false
                                    Outcome.Retry
                                }
                                !response.isSuccessful -> Outcome.Fail(friendlyHttpError(response.code, payload))
                                else -> Outcome.Ok(extractText(payload))
                            }
                        }
                    } catch (e: SocketTimeoutException) {
                        Outcome.Fail("انتهت مدة الانتظار (${CALL_TIMEOUT}ث). جرّب WiFi بدل بيانات الجوال، أو بدّل الوضع إلى «محلي».")
                    } catch (e: UnknownHostException) {
                        Outcome.Retry
                    } catch (e: java.net.ConnectException) {
                        Outcome.Retry
                    } catch (e: IOException) {
                        Outcome.Retry
                    }

                    when (outcome) {
                        is Outcome.Ok -> return@runCatching outcome.text
                        is Outcome.Fail -> error(outcome.message)
                        Outcome.Skip -> {
                            lastError = "الموديل «$candidate» غير متاح لمفتاحك."
                            break
                        }
                        Outcome.Retry -> {
                            // إعادة محاولة واحدة بعد مهلة قصيرة — تعالج انقطاعات شبكة الجوال
                            if (attempt < 2) delay(700) else {
                                lastError = "تعذّر الاتصال بالشبكة. جرّب تشغيل/إيقاف WiFi ثم أعد المحاولة."
                                break
                            }
                        }
                    }
                }
            }
            error(lastError)
        }
    }

    /**
     * نسخة تدعم البثّ (streamGenerateContent): ترسل النص تدريجيًا.
     * مفيدة لو الطلب العادي يعلّق على شبكة معينة، لأن أول بايت يصل بسرعة
     * ويُبطل انتظار الخادم الطويل.
     */
    suspend fun askStreaming(
        apiKey: String,
        model: String,
        style: String,
        context: String,
        history: List<Message>,
        onChunk: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildRequestBody(style, context, history, "stream")
            val candidates = (listOf(model.ifBlank { "" }) + candidateModels).filter { it.isNotBlank() }.distinct()
            var lastError = "تعذّر الاتصال."

            for (candidate in candidates) {
                val request = Request.Builder()
                    .url("$HOST/models/$candidate:streamGenerateContent?alt=sse")
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .post(body.toRequestBody(JSON))
                    .build()

                try {
                    client.newCall(request).execute().use { response ->
                        if (response.code == 404) {
                            lastError = "الموديل «$candidate» غير متاح."
                            return@use
                        }
                        if (!response.isSuccessful) {
                            error(friendlyHttpError(response.code, response.body?.string().orEmpty()))
                        }

                        val source = response.body?.source() ?: error("لا يوجد رد")
                        val builder = StringBuilder()
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) continue
                            val json = line.removePrefix("data:").trim()
                            if (json.isEmpty() || json == "[DONE]") continue
                            val delta = runCatching {
                                JSONObject(json).getJSONArray("candidates").getJSONObject(0)
                                    .getJSONObject("content").getJSONArray("parts").let { parts ->
                                        buildString {
                                            for (i in 0 until parts.length()) {
                                                append(parts.getJSONObject(i).optString("text"))
                                            }
                                        }
                                    }
                            }.getOrDefault("")
                            if (delta.isNotEmpty()) {
                                builder.append(delta)
                                onChunk(builder.toString())
                            }
                        }
                        if (builder.isBlank()) error("رد فارغ من البثّ.")
                        return@runCatching builder.toString()
                    }
                } catch (e: IOException) {
                    lastError = "انقطاع الشبكة أثناء البثّ: ${e.message ?: "غير معروف"}"
                }
            }
            error(lastError)
        }
    }

    /** يبني جسم الطلب مرة واحدة ليستخدمه النمط العادي والبثّ. */
    private fun buildRequestBody(
        style: String,
        context: String,
        history: List<Message>,
        mode: String
    ): String {
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
        return JSONObject().apply {
            put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.4)
                put("maxOutputTokens", 800)
                put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
            })
        }.toString()
    }

    private sealed interface Outcome {
        data class Ok(val text: String) : Outcome
        data class Fail(val message: String) : Outcome
        data object Skip : Outcome
        data object Retry : Outcome
    }

    private fun extractText(payload: String): String {
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

        if (text.isBlank()) {
            // موديلات التفكير قد ترجع محتوى فارغًا لو استهلكت الرموز في التفكير
            val finish = runCatching {
                root.getJSONArray("candidates").getJSONObject(0).optString("finishReason")
            }.getOrDefault("")
            error("وصل رد فارغ${if (finish.isNotBlank()) " ($finish)" else ""}. جرّب موديل lite أو أعد صياغة السؤال.")
        }
        return text
    }

    suspend fun checkKey(apiKey: String, model: String): Result<String> =
        ask(apiKey, model, "مختصر جدًا", "snapshot: currency=QAR", emptyList())

    private fun friendlyHttpError(code: Int, payload: String): String {
        val detail = runCatching {
            JSONObject(payload).getJSONObject("error").optString("message")
        }.getOrDefault("")
        return when (code) {
            400 -> "الطلب غير صالح${if (detail.isNotBlank()) ": $detail" else "."}"
            401, 403 -> "المفتاح غير مقبول. تأكد أن Generative Language API مفعّلة على مشروعك."
            404 -> "الموديل غير موجود لمفتاحك. جرّب «تحديث الموديلات»."
            429 -> "تجاوزت حد الطلبات للمفتاح. انتظر دقيقة أو استخدم الوضع المحلي."
            500, 503 -> "خدمة Google مشغولة حاليًا. جرّب بعد قليل."
            else -> "فشل الاتصال (رمز $code)${if (detail.isNotBlank()) ": $detail" else "."}"
        }
    }
}
