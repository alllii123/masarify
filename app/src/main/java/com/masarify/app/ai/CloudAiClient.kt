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
 * عميل Gemini.
 *
 * ── درس من الميدان ──────────────────────────────────────────────────────────
 * أضفنا سابقًا `thinkingConfig.thinkingBudget = 0` لتسريع الردود، فردّت Google:
 * «Request contains an invalid argument» (400). الموديلات تختلف في دعم هذا الحقل
 * ولا تُفصح عن السبب بوضوح.
 *
 * الحل: «سلّم توافق» — نجرّب جسم الطلب بالكامل، وإذا جاء 400 نُنقص الحقول
 * الاختيارية واحدًا واحدًا حتى ينجح. لا نعتمد على تخمين أي حقل يقبله الموديل.
 *
 * ولماذا HTTP/1.1 وبدون إعادة استخدام اتصالات: على شبكات الجوال هنا
 * نجح GET وفشل POST على اتصال HTTP/2 معاد استخدامه.
 */
object CloudAiClient {

    data class Message(val role: String, val text: String)

    private const val HOST = "https://generativelanguage.googleapis.com/v1beta"
    private const val CONNECT_TIMEOUT = 12L
    private const val READ_TIMEOUT = 30L
    private const val CALL_TIMEOUT = 40L

    /** كم مرة نعيد نفس الموديل قبل الانتقال لغيره. */
    private const val RETRIES_PER_MODEL = 2

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build()
    }

    /**
     * ترتيب المحاولة عند الفشل.
     *
     * `gemini-flash-latest` أولًا لأن الاسم يُوجّه دائمًا لأحدث موديل flash متاح،
     * فلا يمكن أن «يتقاعد» تحت أقدامنا كما حدث مع 2.0-flash. أما 2.5-flash-lite
     * فقد أعلنت Google تقاعده في 2026-10-20، لذلك لا يصلح أن يكون الخيار الأول.
     */
    val candidateModels = listOf(
        "gemini-flash-latest",
        "gemini-2.5-flash-lite",
        "gemini-2.5-flash",
        "gemini-3.6-flash"
    )

    /** يُعرض للمستخدم كاختيارات موصى بها. */
    val suggestedModels = listOf(
        "gemini-flash-latest" to "الأحدث (موصى به)",
        "gemini-2.5-flash-lite" to "الأسرع",
        "gemini-2.5-flash" to "متوازن",
        "gemini-2.5-pro" to "الأدق والأبطأ"
    )

    // ── سلّم التوافق ────────────────────────────────────────────────────────
    // كل درجة أقل تعني حقولًا أقل. ننزل درجة واحدة عند 400 فقط.
    private enum class BodyLevel {
        FULL,          // systemInstruction + maxOutputTokens + temperature
        NO_SYSTEM,     // بدون systemInstruction (يُدمج في أول رسالة)
        MINIMAL        // contents فقط
    }

    // ────────────────────────────────────────────────────────────────────────
    suspend fun listModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$HOST/models?pageSize=200")
                .header("x-goog-api-key", apiKey)
                .header("Accept", "application/json")
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
                        // نستبعد موديلات الصوت والصور لأنها لا تُستخدم في المحادثة النصية
                        val unusable = name.contains("tts") || name.contains("image") ||
                            name.contains("embedding") || name.contains("aqa") ||
                            name.startsWith("gemma")
                        if (canGenerate && name.isNotBlank() && !unusable) add(name)
                    }
                }
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    /** الطلب العادي (غير متدفّق). */
    suspend fun ask(
        apiKey: String,
        model: String,
        style: String,
        context: String,
        history: List<Message>
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            attempt(apiKey, model, style, context, history, streaming = false) { }
        }
    }

    /** البثّ: يظهر النص أثناء وصوله. المسار المفضّل في التطبيق. */
    suspend fun askStreaming(
        apiKey: String,
        model: String,
        style: String,
        context: String,
        history: List<Message>,
        onChunk: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            attempt(apiKey, model, style, context, history, streaming = true, onChunk = onChunk)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    private suspend fun attempt(
        apiKey: String,
        model: String,
        style: String,
        context: String,
        history: List<Message>,
        streaming: Boolean,
        onChunk: (String) -> Unit
    ): String {
        val models = (listOf(model.ifBlank { "" }) + candidateModels).filter { it.isNotBlank() }.distinct()
        var lastError = "تعذّر الاتصال بالمساعد."
        val tried = mutableListOf<String>()
        var sawFatal = false

        for (candidate in models) {
            // كل موديل يُجرّب RETRIES_PER_MODEL مرات، لأن 503/429 عادةً عابرة
            for (retry in 0 until RETRIES_PER_MODEL) {
                if (retry > 0) delay(700L * retry)

                for (level in BodyLevel.entries) {
                    val url = if (streaming) {
                        "$HOST/models/$candidate:streamGenerateContent?alt=sse"
                    } else {
                        "$HOST/models/$candidate:generateContent"
                    }

                    val request = Request.Builder()
                        .url(url)
                        .header("x-goog-api-key", apiKey)
                        .header("Content-Type", "application/json")
                        .post(buildBody(style, context, history, level).toRequestBody(JSON))
                        .build()

                    val result = try {
                        client.newCall(request).execute().use { response ->
                            val payload = response.body?.string().orEmpty()
                            when {
                                response.code == 404 -> Trial.Skip("الموديل «$candidate» غير متاح لمفتاحك")
                                // 400 = حقل غير مدعوم: ننزل درجة في سلّم التوافق
                                response.code == 400 -> Trial.Downgrade(snippet(payload))
                                // 401/403 حتمية: المفتاح أو المشروع
                                response.code == 401 || response.code == 403 ->
                                    Trial.Fatal(friendlyHttpError(response.code, payload))
                                // 429 = حصة: نجرب موديلًا آخر بدل الاستسلام
                                response.code == 429 -> Trial.Skip("تجاوزت الحصة مؤقتًا على «$candidate»")
                                // 500/502/503/504 = عطل مؤقت عند Google: نعيد ثم ننتقل
                                response.code == 500 || response.code == 502 ||
                                    response.code == 503 || response.code == 504 ->
                                    Trial.RetryOrFail("خدمة Google مشغولة (${response.code})")
                                !response.isSuccessful -> Trial.RetryOrFail(friendlyHttpError(response.code, payload))
                                else -> {
                                    val text = if (streaming) parseSse(payload, onChunk) else extractText(payload)
                                    Trial.Ok(text)
                                }
                            }
                        }
                    } catch (e: SocketTimeoutException) {
                        Trial.RetryOrFail("انتهت مدة الانتظار — جرّب WiFi أو أعد المحاولة")
                    } catch (e: UnknownHostException) {
                        Trial.RetryOrFail("تعذّر تحليل اسم الخادم — تحقّق من الشبكة")
                    } catch (e: IOException) {
                        Trial.RetryOrFail("انقطاع الشبكة: ${e.message ?: "غير معروف"}")
                    }

                    when (result) {
                        is Trial.Ok -> return result.text
                        is Trial.Fatal -> {
                            lastError = result.message
                            sawFatal = true
                        }
                        is Trial.Skip -> {
                            lastError = result.reason
                            break
                        }
                        is Trial.Downgrade -> {
                            lastError = "الطلب غير مدعوم: ${result.reason}"
                            tried += "$candidate/$level"
                            continue
                        }
                        is Trial.RetryOrFail -> {
                            lastError = result.reason
                            tried += "$candidate"
                            continue
                        }
                    }
                }

                // خطأ حتمي (401/403) لا يتغير بإعادة المحاولة
                if (sawFatal) error(lastError)
            }
        }

        error(buildString {
            append(lastError)
            if (tried.isNotEmpty()) {
                append("\n\nجرّبت: ")
                append(tried.distinct().take(4).joinToString("، "))
                append("\nجرّب موديلًا آخر من الإعدادات، أو استخدم الوضع المحلي.")
            }
        })
    }

    private sealed interface Trial {
        data class Ok(val text: String) : Trial
        data class Fatal(val message: String) : Trial
        data class Skip(val reason: String) : Trial
        data class Downgrade(val reason: String) : Trial
        data class RetryOrFail(val reason: String) : Trial
    }

    // ────────────────────────────────────────────────────────────────────────
    private fun buildBody(
        style: String,
        context: String,
        history: List<Message>,
        level: BodyLevel
    ): String {
        val system = systemPrompt(style, context)

        val contents = JSONArray().apply {
            history.takeLast(10).forEach { message ->
                put(turn(message.role == "user", message.text))
            }
            if (history.isEmpty()) {
                put(turn(true, "أعطني ملخصًا سريعًا لوضعي المالي."))
            }
            // في الدرجات الأدنى ندمج التعليمات داخل أول رسالة مستخدم
            if (level != BodyLevel.FULL && length() > 0) {
                val first = getJSONObject(0)
                if (first.optString("role") == "user") {
                    val parts = first.getJSONArray("parts")
                    val original = parts.getJSONObject(0).optString("text")
                    parts.put(0, JSONObject().put("text", "$system\n\nسؤال المستخدم: $original"))
                }
            }
        }

        return JSONObject().apply {
            put("contents", contents)
            if (level == BodyLevel.FULL) {
                put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.4)
                    put("maxOutputTokens", 2048)
                })
            }
        }.toString()
    }

    private fun turn(isUser: Boolean, text: String): JSONObject = JSONObject().apply {
        put("role", if (isUser) "user" else "model")
        put("parts", JSONArray().put(JSONObject().put("text", text)))
    }

    private fun systemPrompt(style: String, context: String) = buildString {
        append("أنت مساعد مالي شخصي داخل تطبيق «محفظتي» في قطر. ")
        append("استخدم الريال القطري (ر.ق) في كل الأرقام. ")
        append("أجب بالعربية العامية الخليجية المبسطة، باختصار وبدون مقدمات طويلة. ")
        append("اعتمد فقط على الأرقام الموجودة في السياق ولا تخترع أرقامًا. ")
        append("لو طُلب منك نصيحة استثمار أو قرض، وضّح أن هذا ليس استشارة مالية رسمية. ")
        append("الأسلوب المطلوب: $style. ")
        append("سياق بيانات المستخدم: $context")
    }

    // ────────────────────────────────────────────────────────────────────────
    private fun parseSse(payload: String, onChunk: (String) -> Unit): String {
        val builder = StringBuilder()
        payload.lineSequence().forEach { raw ->
            if (!raw.startsWith("data:")) return@forEach
            val json = raw.removePrefix("data:").trim()
            if (json.isEmpty() || json == "[DONE]") return@forEach
            val delta = runCatching { textFromJson(json) }.getOrNull().orEmpty()
            if (delta.isNotEmpty()) {
                builder.append(delta)
                onChunk(builder.toString())
            }
        }
        if (builder.isBlank()) {
            // أحيانًا يرجع الخادم جسمًا عاديًا بدل SSE
            val plain = runCatching { extractText(payload) }.getOrNull().orEmpty()
            if (plain.isNotBlank()) {
                onChunk(plain)
                return plain
            }
            error("رد فارغ من المساعد. جرّب موديلًا آخر من الإعدادات.")
        }
        return builder.toString()
    }

    private fun extractText(payload: String): String {
        val text = runCatching { textFromJson(payload) }.getOrNull().orEmpty()
        if (text.isBlank()) {
            val finish = runCatching {
                JSONObject(payload).getJSONArray("candidates").getJSONObject(0).optString("finishReason")
            }.getOrDefault("")
            val block = runCatching {
                JSONObject(payload).getJSONObject("promptFeedback").optString("blockReason")
            }.getOrDefault("")
            error(
                when {
                    block.isNotBlank() -> "حجبت Google الطلب ($block). أعد صياغة السؤال بشكل مالي واضح."
                    finish.isNotBlank() -> "رد فارغ (${finish}). جرّب موديل lite أو سؤالًا أقصر."
                    else -> "رد فارغ. جرّب موديل lite من الإعدادات."
                }
            )
        }
        return text
    }

    private fun textFromJson(payload: String): String {
        val root = JSONObject(payload)
        val candidates = root.optJSONArray("candidates") ?: return ""
        if (candidates.length() == 0) return ""
        val content = candidates.getJSONObject(0).optJSONObject("content") ?: return ""
        val parts = content.optJSONArray("parts") ?: return ""
        return buildString {
            for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text"))
        }.trim()
    }

    private fun snippet(payload: String): String = runCatching {
        JSONObject(payload).getJSONObject("error").optString("message")
    }.getOrDefault("").ifBlank { payload.take(120) }

    suspend fun checkKey(apiKey: String, model: String): Result<String> =
        ask(apiKey, model, "مختصر جدًا", "snapshot: currency=QAR", emptyList())

    private fun friendlyHttpError(code: Int, payload: String): String {
        val detail = runCatching {
            JSONObject(payload).getJSONObject("error").optString("message")
        }.getOrDefault("")
        return when (code) {
            400 -> "الطلب غير مقبول${if (detail.isNotBlank()) ": $detail" else ""}"
            401 -> "المفتاح غير مقبول${if (detail.isNotBlank()) ": $detail" else ""}. تأكد من نسخه كاملًا من AI Studio."
            403 -> "الوصول ممنوع. تأكد أن Generative Language API مفعّلة على مشروعك."
            404 -> "الموديل غير متاح لمفتاحك. جرّب «تحديث الموديلات»."
            429 -> "تجاوزت حد الطلبات. انتظر دقيقة أو استخدم الوضع المحلي."
            500, 503 -> "خدمة Google مشغولة. جرّب بعد قليل."
            else -> "فشل الاتصال (رمز $code)${if (detail.isNotBlank()) ": $detail" else "."}"
        }
    }
}
