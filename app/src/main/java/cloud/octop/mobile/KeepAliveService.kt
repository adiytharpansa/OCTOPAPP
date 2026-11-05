package cloud.octop.mobile

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class KeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifier.createChannels(this)
        val n = NotificationCompat.Builder(this, Notifier.CH_KEEP)
            .setSmallIcon(R.drawable.ic_stat_octop)
            .setContentTitle("Octop terhubung")
            .setContentText("Menunggu balasan AI di latar belakang")
            .setContentIntent(Notifier.openAppIntent(this))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
        return START_NOT_STICKY
    }

    companion object {
        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, KeepAliveService::class.java))
        }
        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, KeepAliveService::class.java))
        }
    }
}
