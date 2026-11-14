package com.masarify.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * تحديث داخلي من GitHub Releases.
 *
 * الفكرة: بدل ما تنزّل APK من المتصفح وتمر بمشاكل التثبيت،
 * التطبيق نفسه يفحص آخر إصدار، ينزّل الملف داخل مجلد التطبيق،
 * ثم يفتح شاشة التثبيت الرسمية من أندرويد.
 *
 * لا يحتاج أي مفتاح ولا تسجيل دخول (المستودع عام لهذا الغرض فقط:
 * يقرأ بيانات الإصدارات، ولا يقرأ أي شيء حساس).
 */
object UpdateChecker {

    const val OWNER = "alllii123"
    const val REPO = "masarify"

    data class Release(
        val tag: String,
        val versionName: String,
        val notes: String,
        val apkUrl: String,
        val apkSize: Long,
        val publishedAt: String
    )

    /** يقرأ آخر إصدار من GitHub، ويعيد null لو لم يوجد APK مرفق. */
    suspend fun latest(): Result<Release?> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL("https://api.github.com/repos/$OWNER/$REPO/releases/latest").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Masarify-App")
            }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()

            if (code == 404) return@runCatching null
            if (code !in 200..299) error("تعذّر قراءة الإصدارات (رمز $code)")

            val root = JSONObject(body)
            val tag = root.optString("tag_name").removePrefix("v")
            val assets = root.optJSONArray("assets")
            var apkUrl = ""
            var apkSize = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true) && !name.contains("debug", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        apkSize = asset.optLong("size")
                        break
                    }
                }
            }
            if (apkUrl.isBlank()) return@runCatching null

            Release(
                tag = tag,
                versionName = tag,
                notes = root.optString("body").take(1200),
                apkUrl = apkUrl,
                apkSize = apkSize,
                publishedAt = root.optString("published_at").take(10)
            )
        }
    }

    /** هل الإصدار المرفوع أحدث من المثبّت؟ */
    fun isNewer(remote: String, current: String): Boolean {
        fun parts(value: String) = value.removePrefix("v").split(".").mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
        val a = parts(remote)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** ينزّل الـAPK داخل مجلد التطبيق الخاص (لا يحتاج أي إذن تخزين). */
    suspend fun download(context: Context, release: Release, onProgress: (Int) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val target = File(dir, "Masarify-${release.tag}.apk")

                val connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 25_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Masarify-App")
                }
                if (connection.responseCode !in 200..299) error("فشل التنزيل (رمز ${connection.responseCode})")

                val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        var done = 0L
                        while (input.read(buffer).also { read = it } > 0) {
                            output.write(buffer, 0, read)
                            done += read
                            if (total > 0) onProgress(((done * 100) / total).toInt().coerceIn(0, 99))
                        }
                    }
                }
                connection.disconnect()

                // تحقّق بسيط: حجم معقول + بصمة APK
                require(target.length() > 500_000) { "الملف المنزّل أصغر من المتوقع — قد يكون النقل فشل" }
                require(target.readBytes().take(2).toByteArray().contentEquals(byteArrayOf(0x50, 0x4B))) { "الملف ليس APK صالحًا" }

                onProgress(100)
                target
            }
        }

    /** يفتح شاشة تثبيت أندرويد الرسمية لملف داخل التطبيق. */
    fun install(context: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
