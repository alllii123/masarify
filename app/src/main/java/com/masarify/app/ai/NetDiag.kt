package com.masarify.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * تشخيص شبكة مبسّط: يخبر المستخدم بالضبط وين المشكلة،
 * بدل رسالة عامة مثل «Timeout».
 *
 * يعمل على مراحل، ويعيد قائمة نتائج مقروءة تظهر في الإعدادات وقابلة للنسخ.
 */
object NetDiag {

    data class Line(val ok: Boolean, val text: String)

    suspend fun run(apiKey: String, model: String): List<Line> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Line>()

        // 1) الاتصال العام بالإنترنت
        out += step("اتصال الإنترنت") {
            val connection = (URL("https://clients3.google.com/generate_204").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = false
            }
            val code = connection.responseCode
            connection.disconnect()
            if (code in 200..399) Line(true, "متصل ✅ (رمز $code)")
            else Line(false, "الشبكة ترد لكن برمز غير متوقع: $code — قد تكون شبكة محمية أو بوابة WiFi تحتاج تسجيل دخول")
        }

        // 2) DNS
        out += step("تحليل اسم الخادم (DNS)") {
            val addresses = InetAddress.getAllByName("generativelanguage.googleapis.com")
            val v4 = addresses.count { it is java.net.Inet4Address }
            val v6 = addresses.count { it is java.net.Inet6Address }
            Line(true, "تم التحليل: IPv4=$v4، IPv6=$v6")
        }

        // 3) TLS إلى خادم Google
        out += step("الاتصال بخادم Gemini (TLS)") {
            val start = System.currentTimeMillis()
            val connection = (URL("https://generativelanguage.googleapis.com/").openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            val code = connection.responseCode
            val ms = System.currentTimeMillis() - start
            val type = connection.contentType ?: ""
            connection.disconnect()
            when {
                // 404 على الجذر أمر طبيعي: الخادم يستجيب لكن لا توجد صفحة على /
                code in 200..499 -> Line(true, "اتصال آمن ناجح في $ms مللي (TLS: ${type.substringBefore(';')})")
                else -> Line(false, "فشل الاتصال برمز $code")
            }
        }

        // 4) حضور المفتاح
        // توجد صيغتان مقبولتان الآن:
        //  - القديمة: AIza… وطولها 39 حرفًا
        //  - الجديدة: AQ.Ab8… وطولها 53 حرفًا
        // الصيغة وحدها لا تكفي للحكم: الحكم النهائي هو رد Google في الخطوتين 5 و6.
        out += step("شكل مفتاح API") {
            when {
                apiKey.isBlank() -> Line(false, "المفتاح فارغ — أضفه ثم اضغط «حفظ المفتاح»")
                apiKey.length < 20 -> Line(false, "المفتاح قصير جدًا (${apiKey.length} حرفًا) — أعد نسخه")
                apiKey.startsWith("AIza") -> Line(true, "صيغة قديمة صحيحة (${apiKey.length} حرفًا)")
                apiKey.startsWith("AQ.") -> Line(true, "صيغة جديدة صحيحة (${apiKey.length} حرفًا) — تُقبل من Google")
                else -> Line(true, "صيغة غير معروفة (${apiKey.length} حرفًا) — سيحددها فحص الصلاحية بالأسفل")
            }
        }

        // 5) صلاحية المفتاح عبر قراءة الموديلات
        out += step("صلاحية المفتاح على Google") {
            if (apiKey.isBlank()) return@step Line(false, "تخطّي: لا يوجد مفتاح")
            val connection = (URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1").openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
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
                    Line(false, "المفتاح مرفوض: $msg — تأكد أنك أنشأته من AI Studio وأن Generative Language API مفعّلة")
                }
                429 -> Line(true, "المفتاح يعمل لكن الحصة مؤقتًا 429")
                else -> Line(false, "رمز غير متوقع $code")
            }
        }

        // 6) تجربة توليد فعلية بالموديل المحدد
        out += step("توليد تجريبي بالموديل $model") {
            val start = System.currentTimeMillis()
            CloudAiClient.ask(apiKey, model, "مختصر", "snapshot: currency=QAR", emptyList()).fold(
                onSuccess = { reply ->
                    val ms = System.currentTimeMillis() - start
                    Line(true, "رد خلال $ms مللي: «${reply.take(60)}»")
                },
                onFailure = { error -> Line(false, error.message ?: "فشل غير معروف") }
            )
        }

        out
    }

    private inline fun step(name: String, block: () -> Line): Line = try {
        block()
    } catch (e: java.net.SocketTimeoutException) {
        Line(false, "$name: انتهت المدة (Timeout). جرّب WiFi بدل بيانات الجوال أو العكس، وأوقف VPN.")
    } catch (e: java.net.UnknownHostException) {
        Line(false, "$name: لا يوجد DNS. عادة شبكة WiFi تحتاج تسجيل دخول من المتصفح أولًا.")
    } catch (e: javax.net.ssl.SSLException) {
        Line(false, "$name: خطأ TLS (${e.message}). قد يكون VPN أو شبكة تقطع الاتصال المشفّر.")
    } catch (t: Throwable) {
        Line(false, "$name: ${t.javaClass.simpleName} — ${t.message ?: "بدون تفاصيل"}")
    }
}
