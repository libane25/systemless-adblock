package se.liban.adblock.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import se.liban.adblock.R
import se.liban.adblock.data.HostsManager
import se.liban.adblock.ui.MainActivity
import se.liban.adblock.data.SettingsRepository
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object VpnStatus {
    val running = MutableStateFlow(false)
}

class AdBlockVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private var relay: TrafficRelay? = null
    private val stopped = AtomicBoolean(false)
    private val session = AtomicInteger(0)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            ACTION_RESTART -> {
                restartTunnel()
                return START_STICKY
            }
        }
        stopped.set(false)
        startForegroundWithType(buildNotification())
        if (worker == null) startVpn()
        return START_STICKY
    }

    override fun onRevoke() {
        stopVpn()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun startVpn() {
        val domains = HostsManager.blocklistFile(this)
        if (!domains.isFile || domains.length() == 0L) {
            VpnStatus.running.value = false
            stopSelf()
            return
        }
        val blocked = HashSet<String>(80_000)
        domains.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val host = line.trim().lowercase()
                if (host.isNotEmpty() && !host.startsWith("#")) blocked.add(host)
            }
        }
        val established = Builder()
            .setSession("AdBlock VPN")
            .setMtu(1500)
            .addAddress("10.66.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .addDisallowedApplication(packageName)
            .setBlocking(true)
            .also { builder ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val unmetered = SettingsRepository(this).current().vpnUnmetered
                    builder.setMetered(!unmetered)
                }
            }
            .establish()
        if (established == null) {
            Log.e(TAG, "VPN establish returned null")
            VpnStatus.running.value = false
            stopSelf()
            return
        }
        tun = established
        val input = FileInputStream(established.fileDescriptor)
        val output = FileOutputStream(established.fileDescriptor)
        val engine = TrafficRelay(
            blocked = blocked,
            protectSocket = { socket -> protect(socket) },
            protectDatagram = { socket -> protect(socket) },
            output = output
        )
        relay = engine
        VpnStatus.running.value = true
        val generation = session.get()
        worker = Thread({
            val buffer = ByteArray(32767)
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val count = input.read(buffer)
                    if (count <= 0) continue
                    engine.onPacket(buffer.copyOf(count), count)
                }
            } catch (_: Exception) {
            } finally {
                engine.shutdown()
                if (session.get() == generation) VpnStatus.running.value = false
            }
        }, "adblock-vpn").also { it.start() }
    }

    private fun teardownTunnel() {
        session.incrementAndGet()
        val oldWorker = worker
        val oldRelay = relay
        worker = null
        relay = null
        oldWorker?.interrupt()
        oldRelay?.shutdown()
        try {
            tun?.close()
        } catch (_: Exception) {
        }
        tun = null
        VpnStatus.running.value = false
    }

    private fun stopVpn() {
        if (!stopped.compareAndSet(false, true)) return
        teardownTunnel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun restartTunnel() {
        teardownTunnel()
        stopped.set(false)
        startForegroundWithType(buildNotification())
        startVpn()
    }

    private fun startForegroundWithType(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ad blocking",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("AdBlock VPN is on")
            .setContentText("Ads and trackers are blocked locally. Traffic is not sent to a VPN server.")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "AdBlockVpn"
        private const val CHANNEL_ID = "adblock_vpn"
        private const val NOTIFICATION_ID = 66
        const val ACTION_STOP = "se.liban.adblock.vpn.STOP"
        const val ACTION_RESTART = "se.liban.adblock.vpn.RESTART"

        fun start(context: Context) {
            val intent = Intent(context, AdBlockVpnService::class.java)
            context.startForegroundService(intent)
        }

        fun restart(context: Context) {
            val intent = Intent(context, AdBlockVpnService::class.java).setAction(ACTION_RESTART)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AdBlockVpnService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
