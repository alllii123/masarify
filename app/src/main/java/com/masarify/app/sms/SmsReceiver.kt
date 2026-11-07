package com.masarify.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.masarify.app.MasarifyApp
import com.masarify.app.data.SmsDraftEntity
import com.masarify.app.notifications.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as MasarifyApp
                val settings = app.settings.settings.first()
                if (!settings.smsEnabled) return@launch
                val allowed = settings.smsSenders.split(',', '،', '\n').map { it.trim() }.filter { it.isNotEmpty() }
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                messages.forEach { message ->
                    val sender = message.displayOriginatingAddress.orEmpty()
                    if (allowed.isNotEmpty() && allowed.none { sender.contains(it, ignoreCase = true) }) return@forEach
                    val body = message.displayMessageBody.orEmpty()
                    val parsed = SmsParser.parse(body, sender) ?: return@forEach
                    val timestamp = message.timestampMillis
                    val hash = SmsParser.hash(sender, timestamp, body)
                    val draft = SmsDraftEntity(
                        type = parsed.type,
                        amount = parsed.amount,
                        merchant = parsed.merchant,
                        sender = sender,
                        accountHint = parsed.accountHint,
                        category = parsed.category,
                        timestamp = timestamp,
                        messageHash = hash
                    )
                    val id = app.database.smsDraftDao().insert(draft)
                    if (id != -1L) Notifier.showDraft(context, id, parsed.category == "راتب", parsed.amount)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
