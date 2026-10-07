package com.fl0w.speye.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.fl0w.speye.MainActivity
import com.fl0w.speye.R
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

object TestVoiceManager {
    private const val NOTIFICATION_ID = 8888
    private const val CHANNEL_ID = "test_voice_channel"

    fun sendTestVoiceMessage(context: Context) {
        val appContext = context.applicationContext
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(nm)

        val audioFile = getOrCreateSampleAudioFile(appContext)
        val contentIntent = PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val person = Person.Builder()
            .setName("Alice")
            .setKey("alice_voice_test")
            .build()

        val extras = Bundle().apply {
            putBoolean("speye_test_voice_message", true)
            putString("speye_test_audio_path", audioFile.absolutePath)
        }

        val messagingStyle = NotificationCompat.MessagingStyle(person)
            .setConversationTitle("Alice")
            .addMessage(
                NotificationCompat.MessagingStyle.Message(
                    "Voice message (0:17)",
                    System.currentTimeMillis(),
                    person
                ).setData("audio/wav", android.net.Uri.fromFile(audioFile))
            )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setStyle(messagingStyle)
            .setContentTitle("Alice")
            .setContentText("Voice message (0:17)")
            .setContentIntent(contentIntent)
            .addExtras(extras)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
        SpeyeLogger.d("TestVoiceManager", "Dispatched test voice message notification with audio: ${audioFile.absolutePath}")
    }

    fun getOrCreateSampleAudioFile(context: Context): File {
        val voiceDir = File(context.filesDir, "voice").apply { mkdirs() }
        val testFile = File(voiceDir, "test_voice_sample.wav")
        if (!testFile.exists() || testFile.length() < 1000L) {
            writeSampleWavFile(testFile, durationSeconds = 17, sampleRate = 8000)
        }
        return testFile
    }

    private fun writeSampleWavFile(file: File, durationSeconds: Int, sampleRate: Int) {
        try {
            val numSamples = durationSeconds * sampleRate
            val dataSize = numSamples * 2 // 16-bit mono
            val totalSize = 36 + dataSize

            val header = ByteBuffer.allocate(44).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                put("RIFF".toByteArray())
                putInt(totalSize)
                put("WAVE".toByteArray())
                put("fmt ".toByteArray())
                putInt(16) // Subchunk1Size for PCM
                putShort(1) // AudioFormat 1 = PCM
                putShort(1) // NumChannels = 1 (mono)
                putInt(sampleRate) // SampleRate
                putInt(sampleRate * 2) // ByteRate = SampleRate * NumChannels * BitsPerSample/8
                putShort(2) // BlockAlign = NumChannels * BitsPerSample/8
                putShort(16) // BitsPerSample
                put("data".toByteArray())
                putInt(dataSize)
            }.array()

            FileOutputStream(file).use { out ->
                out.write(header)
                val buffer = ByteArray(2048)
                var bufferPos = 0
                val freq = 440.0 // A4 note with soft tremolo

                for (i in 0 until numSamples) {
                    val timeSec = i.toDouble() / sampleRate
                    // Soft envelope & gentle wobble tone
                    val envelope = (0.5 + 0.5 * sin(2.0 * Math.PI * 2.0 * timeSec))
                    val sampleVal = (sin(2.0 * Math.PI * freq * timeSec) * 12000 * envelope).toInt().toShort()

                    buffer[bufferPos++] = (sampleVal.toInt() and 0xFF).toByte()
                    buffer[bufferPos++] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()

                    if (bufferPos >= buffer.size) {
                        out.write(buffer, 0, bufferPos)
                        bufferPos = 0
                    }
                }
                if (bufferPos > 0) {
                    out.write(buffer, 0, bufferPos)
                }
                out.flush()
            }
        } catch (e: Exception) {
            SpeyeLogger.e("TestVoiceManager", "Error generating sample WAV file", e)
        }
    }

    private fun createChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Voice Test Notifications", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }
    }
}
