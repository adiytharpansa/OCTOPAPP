package cloud.octop.mobile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.concurrent.atomic.AtomicInteger

object Notifier {
    const val CH_REPLY = "reply"
    const val CH_KEEP = "keepalive"
    private const val TAG_REPLY = "octop_reply"
    private val nextId = AtomicInteger(100)

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_REPLY, "Balasan AI", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Pemberitahuan saat AI selesai membalas"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_KEEP, "Koneksi latar belakang", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Menjaga koneksi agar balasan AI tetap diterima"
                setShowBadge(false)
            }
        )
    }

    fun openAppIntent(ctx: Context): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun clean(s: String): String =
        s.replace(Regex("[*_`#>]+"), "").replace(Regex("\\s+"), " ").trim()

    fun showReply(ctx: Context, rawText: String) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        val text = clean(rawText)
        if (text.isEmpty()) return
        val short = if (text.length > 120) text.take(120).trimEnd() + "…" else text
        val long = if (text.length > 600) text.take(600).trimEnd() + "…" else text
        val n = NotificationCompat.Builder(ctx, CH_REPLY)
            .setSmallIcon(R.drawable.ic_stat_octop)
            .setContentTitle("Balasan baru dari AI")
            .setContentText(short)
            .setStyle(NotificationCompat.BigTextStyle().bigText(long))
            .setContentIntent(openAppIntent(ctx))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup("octop_replies")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(ctx, CH_REPLY)
                    .setSmallIcon(R.drawable.ic_stat_octop)
                    .setContentTitle("Balasan baru dari AI")
                    .build()
            )
            .build()
        try {
            nm.notify(TAG_REPLY, nextId.getAndIncrement(), n)
        } catch (_: SecurityException) { }
    }

    fun clearReplies(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.activeNotifications.filter { it.tag == TAG_REPLY }.forEach { nm.cancel(it.tag, it.id) }
    }
}
