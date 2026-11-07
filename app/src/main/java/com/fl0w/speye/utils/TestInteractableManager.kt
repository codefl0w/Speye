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
            }
        }
    }

    private fun ensureReceiverRegistered(context: Context) {
        if (receiverRegistered) return
        val appContext = context.applicationContext
        val filter = IntentFilter().apply {
            addAction(ACTION_CONFIRM)
            addAction(ACTION_REJECT)
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

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

        val confirmIntent = Intent(ACTION_CONFIRM).setPackage(appContext.packageName)
        val confirmPendingIntent = PendingIntent.getBroadcast(appContext, 101, confirmIntent, flags)

        val rejectIntent = Intent(ACTION_REJECT).setPackage(appContext.packageName)
        val rejectPendingIntent = PendingIntent.getBroadcast(appContext, 102, rejectIntent, flags)

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Speye Bank • Payment Request")
            .setContentText("Approve transfer of $240.00 to Acme Corp (Ref #4821)?")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Approve transfer of $240.00 to Acme Corp (Ref #4821)? Confirm or reject this payment request below.")
            )
            .addAction(android.R.drawable.checkbox_on_background, "Confirm", confirmPendingIntent)
            .addAction(android.R.drawable.ic_delete, "Reject", rejectPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        nm.notify(TEST_INTERACTABLE_ID, notification)
    }
}

