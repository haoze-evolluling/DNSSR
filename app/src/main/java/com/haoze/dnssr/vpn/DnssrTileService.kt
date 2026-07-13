package com.haoze.dnssr.vpn

import android.app.PendingIntent
import android.annotation.SuppressLint
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.haoze.dnssr.MainActivity

class DnssrTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (DnsVpnService.isRunning(this)) {
            startService(DnsVpnService.stopIntent(this))
            updateTile(running = false)
            return
        }

        if (VpnService.prepare(this) == null) {
            ContextCompat.startForegroundService(this, DnsVpnService.startIntent(this))
            updateTile(running = true)
        } else {
            openMainForVpnPermission()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openMainForVpnPermission() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_AUTO_START_VPN, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile(running: Boolean = DnsVpnService.isRunning(this)) {
        qsTile?.apply {
            state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "DNSSR"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = if (running) "已开启" else "已关闭"
            }
            updateTile()
        }
    }
}
