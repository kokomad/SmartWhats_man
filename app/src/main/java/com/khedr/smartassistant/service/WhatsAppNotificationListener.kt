package com.khedr.smartassistant.service

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.khedr.smartassistant.data.AssistantStore
import com.khedr.smartassistant.data.SecureApiKeyStore
import com.khedr.smartassistant.ai.GeminiFlashClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Reads notification previews and exposes WhatsApp's inline reply action when available. */
class WhatsAppNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.whatsapp" && sbn.packageName != "com.whatsapp.w4b") return
        val store = AssistantStore(this)
        if (!store.enabled()) return
        val extras = sbn.notification.extras
        val contact = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val message = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (contact.isBlank() || message.isBlank() || contact !in store.allowedContacts()) return

        // Generate in background; the app never processes contacts outside the user's allow-list.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val canned = store.replies(contact)
            val key = SecureApiKeyStore(this@WhatsAppNotificationListener).read()
            val ai = if (!key.isNullOrBlank()) GeminiFlashClient.generate(key, message, contact, store.profile(contact), canned) else null
            val proposed = if (ai?.success == true) ReplyComposer.clean(ai.text) else ReplyComposer.compose(message, canned, store.profile(contact))
            if (proposed.isBlank()) return@launch
            if (store.autoSend() && ai?.success == true) {
                // Auto-send only a successfully generated AI response; otherwise ask the user.
                replyInline(sbn, proposed)
            } else {
                ReplyReviewNotifier.show(this@WhatsAppNotificationListener, contact, message, proposed, sbn)
            }
        }
    }

    private fun replyInline(sbn: StatusBarNotification, text: String) {
        val actions = sbn.notification.actions ?: return
        val action = actions.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true } ?: return
        sendReply(action, text)
    }

    companion object {
        fun sendReply(action: Notification.Action, text: String) {
            val input = action.remoteInputs?.firstOrNull { it.allowFreeFormInput } ?: return
            val intent = Intent()
            val results = android.os.Bundle().apply { putCharSequence(input.resultKey, text) }
            RemoteInput.addResultsToIntent(arrayOf(input), intent, results)
            try { action.actionIntent.send(null, 0, intent) } catch (_: Exception) { }
        }
    }
}

/** Reply cleanup and canned fallback used when Gemini is not configured or unavailable. */
object ReplyComposer {
    fun clean(text: String): String = text.replace(Regex("[\\t ]{2,}"), " ")
        .replace(Regex("([😂🤣])\\1{2,}"), "$1")
        .replace(Regex("\\n{3,}"), "\\n\\n").trim().take(1200)

    fun compose(message: String, canned: List<String>, profile: String): String {
        val clean = message.lowercase()
        val selected = when {
            clean.contains("السلام") -> canned.firstOrNull { it.contains("وعليكم") || it.contains("سلام") }
            clean.contains("كيفك") || clean.contains("كيف حالك") -> canned.firstOrNull { it.contains("الحمد") || it.contains("تمام") }
            clean.contains("شكرا") || clean.contains("شكراً") -> canned.firstOrNull { it.contains("عفو") || it.contains("أهلا") }
            else -> null
        }
        // No invented personal commitments: return a neutral, reviewable reply if no match.
        return selected ?: canned.firstOrNull() ?: "وصلتني رسالتك، سأرد عليك قريباً."
    }
}
