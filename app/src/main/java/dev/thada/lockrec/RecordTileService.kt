package dev.thada.lockrec

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** クイック設定タイル。録音中は点灯し、押すと録音をトグル */
class RecordTileService : TileService() {
    override fun onStartListening() {
        val tile = qsTile ?: return
        tile.state = if (RecordingService.isRecording) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (RecordingService.isRecording) "録音中" else null
        tile.updateTile()
    }

    // マイクの前景サービスは「画面表示中のアプリから開始」が条件のため、ToggleActivity を経由する
    override fun onClick() {
        val intent = Intent(this, ToggleActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
    }
}
