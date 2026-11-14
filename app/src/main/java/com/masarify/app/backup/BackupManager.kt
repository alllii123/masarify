package com.masarify.app.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.masarify.app.data.FinanceRepository
import com.masarify.app.data.SettingsRepository
import com.masarify.app.data.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * نسخ واستعادة محلية تعمل بدون إنترنت، وتصلح كجسر لـ Google Drive:
 * التطبيق ينتج ملف SQLite/JSON وتضعه أنت في Drive (أو أي مكان) ثم تستعيده على الجوال الجديد.
 *
 * سبب هذا التصميم: ربط Drive الرسمي يحتاج مشروع Google Cloud + OAuth client،
 * وهذا لا يمكن تجهيزه من داخل الجوال بدون أسرار. طريقة الملف:
 * - لا أسرار ولا OAuth.
 * - تعمل مع Drive و OneDrive وواتساب والكابل وذاكرة USB.
 * - قابلة للمراجعة والفحص.
 */
object BackupManager {

    const val BACKUP_MIME = "application/json"

    /** ملف النسخة الاحتياطية الكامل (JSON قابل للقراءة). */
    suspend fun writeFullBackup(context: Context, repository: FinanceRepository, settings: SettingsRepository): File =
        withContext(Dispatchers.IO) {
            val payload = BackupPayload(
                createdAt = System.currentTimeMillis(),
                accounts = repository.accountsOnce(),
                transactions = repository.transactionsOnce(),
                smsDrafts = repository.draftsOnce(),
                settings = settings.settings.first()
            )
            val dir = File(context.cacheDir, "backups").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
            val file = File(dir, "masarify-backup-$stamp.json")
            file.writeText(BackupCodec.encode(payload))
            cleanupOldFiles(dir)
            file
        }

    /** نسخة مصغّرة أخف للنقل السريع. */
    suspend fun writeSummary(context: Context, repository: FinanceRepository, settings: SettingsRepository): File =
        withContext(Dispatchers.IO) {
            val payload = BackupPayload(
                createdAt = System.currentTimeMillis(),
                accounts = repository.accountsOnce(),
                transactions = repository.transactionsOnce(),
                smsDrafts = emptyList(),
                settings = settings.settings.first()
            )
            val dir = File(context.cacheDir, "backups").apply { mkdirs() }
            val file = File(dir, "masarify-summary-${System.currentTimeMillis()}.json")
            file.writeText(BackupCodec.encode(payload))
            file
        }

    /**
     * يشارك الملف عبر أي تطبيق على الجهاز (Drive، Files، واتساب، إيميل).
     * المستخدم يختار «حفظ في Drive» فيظهر الملف هناك.
     */
    fun shareFile(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = BACKUP_MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية من مصاريفي — ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "حفظ النسخة الاحتياطية في").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    /** يفتح اختيار ملف للاستعادة. */
    fun pickBackupIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain", "application/octet-stream"))
    }

    suspend fun readBackup(context: Context, uri: Uri): Result<BackupCodec.Parsed> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("تعذّر قراءة الملف")
            require(raw.length < 40 * 1024 * 1024) { "الملف أكبر من الحد المتوقع" }
            BackupCodec.decode(raw).getOrThrow()
        }
    }

    private fun cleanupOldFiles(dir: File) {
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(5)?.forEach { it.delete() }
    }
}

/** نتيجة الاستعادة النهائية. */
data class RestoreResult(val accounts: Int, val transactions: Int, val replacedExisting: Boolean)
