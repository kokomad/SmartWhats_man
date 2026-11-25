package com.khedr.smartassistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.Notification

/** Sends the exact reviewed text through WhatsApp's notification reply PendingIntent. */
class ReplyApprovalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        val action = if (android.os.Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(EXTRA_ACTION, Notification.Action::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_ACTION)
        if (text.isNotBlank() && action != null) WhatsAppNotificationListener.sendReply(action, text)
    }
    companion object {
        const val EXTRA_TEXT = "reviewed_reply_text"
        const val EXTRA_ACTION = "whatsapp_reply_action"
    }
}
