package com.masarify.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * تشخيص شبكة مبسّط: يخبر المستخدم بالضبط وين المشكلة بدل رسالة «Timeout» عامة.
 *
 * كل خطوة لها مهلة قصيرة ومستقلة (8–12 ثانية) حتى لا يعلّق التشخيص
 * مدة دقيقتين كما حدث سابقًا بسبب اتصال واحد بطيء.
 */
object NetDiag {

    data class Line(val ok: Boolean, val text: String)

    private const val SHORT = 8_000

    suspend fun run(apiKey: String, model: String): List<Line> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Line>()

        // 1) الاتصال العام بالإنترنت
        out += step("اتصال الإنترنت") {
            val connection = (URL("https://clients3.google.com/generate_204").openConnection() as HttpURLConnection).apply {
                connectTimeout = SHORT
                readTimeout = SHORT
                instanceFollowRedirects = false
            }
            try {
                val code = connection.responseCode
                if (code in 200..399) Line(true, "متصل ✅ (رمز $code)")
                else Line(false, "الشبكة ترد برمز $code — قد تكون بوابة WiFi تحتاج تسجيل دخول")
            } finally {
                connection.disconnect()
            }
        }

        // 2) DNS
        out += step("تحليل اسم الخادم (DNS)") {
            val addresses = InetAddress.getAllByName("generativelanguage.googleapis.com")
            val v4 = addresses.count { it is java.net.Inet4Address }
            val v6 = addresses.count { it is java.net.Inet6Address }
            Line(true, "تم التحليل: IPv4=$v4، IPv6=$v6")
        }

        // 3) TLS إلى خادم Google — مع إعادة محاولة واحدة لأن أول اتصال على شبكات الجوال قد يعلّق
        out += step("الاتصال بخادم Gemini (TLS)") {
            var lastMs = 0L
            repeat(2) { attempt ->
                val start = System.currentTimeMillis()
                val connection = (URL("https://generativelanguage.googleapis.com/").openConnection() as HttpURLConnection).apply {
                    connectTimeout = SHORT
                    readTimeout = SHORT
                }
                val code = try {
                    connection.responseCode
                } finally {
                    connection.disconnect()
                }
                lastMs = System.currentTimeMillis() - start
                if (code in 200..499) {
                    // 404 على الجذر طبيعي: الخادم يستجيب لكن لا توجد صفحة على /
                    val note = if (attempt == 1) " (بالمحاولة الثانية)" else ""
                    return@step Line(true, "اتصال آمن ناجح في $lastMs مللي$note")
                }
            }
            Line(false, "فشل الاتصال بعد محاولتين (آخر محاولة ${lastMs} مللي)")
        }

        // 4) شكل المفتاح — الصيغة وحدها ليست حكمًا
        out += step("شكل مفتاح API") {
            when {
                apiKey.isBlank() -> Line(false, "المفتاح فارغ — أضفه ثم اضغط «حفظ المفتاح»")
                apiKey.length < 20 -> Line(false, "المفتاح قصير جدًا (${apiKey.length} حرفًا) — أعد نسخه")
                apiKey.startsWith("AIza") -> Line(true, "صيغة قديمة صحيحة (${apiKey.length} حرفًا)")
                apiKey.startsWith("AQ.") -> Line(true, "صيغة جوجل الجديدة (${apiKey.length} حرفًا)")
                else -> Line(true, "صيغة غير معروفة (${apiKey.length} حرفًا) — يحكم عليها فحص الصلاحية")
            }
        }

        // 5) صلاحية المفتاح
        out += step("صلاحية المفتاح على Google") {
            if (apiKey.isBlank()) return@step Line(false, "تخطّي: لا يوجد مفتاح")
            val connection = (URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1").openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("x-goog-api-key", apiKey)
            }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            when (code) {
                200 -> Line(true, "المفتاح مقبول ✅")
                400, 401, 403 -> {
                    val msg = runCatching { JSONObject(body).getJSONObject("error").optString("message") }.getOrDefault("")
                    Line(false, "المفتاح مرفوض: $msg")
                }
                429 -> Line(true, "المفتاح يعمل لكن الحصة مؤقتًا (429)")
                else -> Line(false, "رمز غير متوقع $code")
            }
        }

        // 6) توليد تجريبي عادي
        out += step("توليد تجريبي (طلب عادي)") {
            val start = System.currentTimeMillis()
            CloudAiClient.ask(apiKey, model, "مختصر", "snapshot: currency=QAR", emptyList()).fold(
                onSuccess = { reply ->
                    Line(true, "رد خلال ${System.currentTimeMillis() - start} مللي: «${reply.take(40)}»")
                },
                onFailure = { error -> Line(false, error.message ?: "فشل غير معروف") }
            )
        }

        // 7) توليد تجريبي بالبثّ — المسار البديل الذي يستخدمه التطبيق
        out += step("توليد تجريبي (بثّ)") {
            val start = System.currentTimeMillis()
            var firstByteAt = 0L
            CloudAiClient.askStreaming(apiKey, model, "مختصر", "snapshot: currency=QAR", emptyList()) { partial ->
                if (firstByteAt == 0L && partial.isNotEmpty()) firstByteAt = System.currentTimeMillis() - start
            }.fold(
                onSuccess = { reply ->
                    Line(
                        true,
                        "البثّ يعمل ✅ (أول جزء بعد ${if (firstByteAt > 0) firstByteAt else System.currentTimeMillis() - start} مللي، الكل ${System.currentTimeMillis() - start} مللي)"
                    )
                },
                onFailure = { error -> Line(false, "البثّ فشل: ${error.message}") }
            )
        }

        out
    }

    private inline fun step(name: String, block: () -> Line): Line = try {
        block()
    } catch (e: java.net.SocketTimeoutException) {
        Line(false, "$name: انتهت المدة. جرّب WiFi بدل بيانات الجوال أو العكس.")
    } catch (e: java.net.UnknownHostException) {
        Line(false, "$name: لا يوجد DNS — غالبًا WiFi يحتاج تسجيل دخول من المتصفح.")
    } catch (e: javax.net.ssl.SSLException) {
        Line(false, "$name: خطأ TLS (${e.message}). قد يكون VPN أو شبكة تقطع الاتصال المشفّر.")
    } catch (t: Throwable) {
        Line(false, "$name: ${t.javaClass.simpleName} — ${t.message ?: "بدون تفاصيل"}")
    }
}
