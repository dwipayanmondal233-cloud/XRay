package com.example.xray

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class XRayTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        update()
    }

    override fun onClick() {
        super.onClick()
        val svc = XRayService.instance
        if (svc == null) {
            Toast.makeText(
                this,
                "Turn on the XRay service in Accessibility first.",
                Toast.LENGTH_LONG
            ).show()
            update()
            return
        }
        svc.xrayFromShade()
    }

    private fun update() {
        val t = qsTile ?: return
        t.state = if (XRayService.instance != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "XRay"
        t.updateTile()
    }
}