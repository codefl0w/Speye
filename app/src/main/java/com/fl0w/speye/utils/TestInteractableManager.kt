package com.fl0w.speye.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

object TestInteractableManager {
    private const val TAG = "TestInteractableMgr"
    private const val CHANNEL_ID = "test_channel"
    const val TEST_INTERACTABLE_ID = 8891

    const val ACTION_CONFIRM = "com.fl0w.speye.ACTION_TEST_BANK_CONFIRM"
    const val ACTION_REJECT = "com.fl0w.speye.ACTION_TEST_BANK_REJECT"
    const val ACTION_REPLY = "com.fl0w.speye.ACTION_TEST_BANK_REPLY"
    const val KEY_TEXT_REPLY = "key_text_reply"

    private var receiverRegistered = false

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            ensureChannel(nm)

            when (action) {
                ACTION_CONFIRM -> {
                    SpeyeLogger.d(TAG, "Bank test payment CONFIRMED via notification action")
                    val updated = NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("Speye Bank • Payment Request")
                        .setContentText("Payment of $240.00 to Acme Corp CONFIRMED.")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setAutoCancel(true)
                        .build()
                    nm.notify(TEST_INTERACTABLE_ID, updated)
                    Toast.makeText(context, "Payment Confirmed ($240.00)", Toast.LENGTH_SHORT).show()
                }
                ACTION_REJECT -> {
                    SpeyeLogger.d(TAG, "Bank test payment REJECTED via notification action")
                    val updated = NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("Speye Bank • Payment Request")
                        .setContentText("Payment of $240.00 to Acme Corp REJECTED.")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setAutoCancel(true)
                        .build()
                    nm.notify(TEST_INTERACTABLE_ID, updated)
                    Toast.makeText(context, "Payment Rejected ($240.00)", Toast.LENGTH_SHORT).show()
                }
                ACTION_REPLY -> {
                    val results = androidx.core.app.RemoteInput.getResultsFromIntent(intent)
                    val replyText = results?.getCharSequence(KEY_TEXT_REPLY)?.toString()?.trim().orEmpty()
                    SpeyeLogger.d(TAG, "Bank test payment REPLIED via RemoteInput: '$replyText'")
                    val updated = NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("Speye Bank • Payment Request")
                        .setContentText("Reply sent: $replyText")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setAutoCancel(true)
                        .build()
                    nm.notify(TEST_INTERACTABLE_ID, updated)
                    Toast.makeText(context, "Reply sent: $replyText", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun ensureReceiverRegistered(context: Context) {
        if (receiverRegistered) return
        val appContext = context.applicationContext
        val filter = IntentFilter().apply {
            addAction(ACTION_CONFIRM)
            addAction(ACTION_REJECT)
            addAction(ACTION_REPLY)
        }
        ContextCompat.registerReceiver(
            appContext,
            actionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Test Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(channel)
        }
    }

    fun sendTestInteractableNotification(context: Context) {
        val appContext = context.applicationContext
        ensureReceiverRegistered(appContext)

        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)

        val immutableFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val mutableFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)

        val confirmIntent = Intent(ACTION_CONFIRM).setPackage(appContext.packageName)
        val confirmPendingIntent = PendingIntent.getBroadcast(appContext, 101, confirmIntent, immutableFlags)

        val rejectIntent = Intent(ACTION_REJECT).setPackage(appContext.packageName)
        val rejectPendingIntent = PendingIntent.getBroadcast(appContext, 102, rejectIntent, immutableFlags)

        val replyIntent = Intent(ACTION_REPLY).setPackage(appContext.packageName)
        val replyPendingIntent = PendingIntent.getBroadcast(appContext, 103, replyIntent, mutableFlags)
        val remoteInput = androidx.core.app.RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Write a reply...")
            .build()
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.sym_action_chat,
            "Reply",
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Speye Bank • Payment Request")
            .setContentText("Approve transfer of $240.00 to Acme Corp (Ref #4821)?")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Approve transfer of $240.00 to Acme Corp (Ref #4821)? Confirm, reject, or reply below.")
            )
            .addAction(android.R.drawable.checkbox_on_background, "Confirm", confirmPendingIntent)
            .addAction(android.R.drawable.ic_delete, "Reject", rejectPendingIntent)
            .addAction(replyAction)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        nm.notify(TEST_INTERACTABLE_ID, notification)
    }
}

