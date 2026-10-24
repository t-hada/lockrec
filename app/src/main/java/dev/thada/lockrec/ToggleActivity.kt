package dev.thada.lockrec

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle

/** ロック画面ショートカット・ランチャーの入口。画面は出さず録音をトグルして閉じる */
class ToggleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        val missing = REQUIRED_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) toggleAndFinish()
        else requestPermissions(missing.toTypedArray(), REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) toggleAndFinish()
        else finish()
    }

    // モデル・トークン未配置で失敗した録音も、次にボタンを押したとき拾い直す
    private fun toggleAndFinish() {
        RecordingService.toggle(this)
        TranscribeWorker.enqueue(this)
        PushWorker.enqueue(this)
        finish()
    }

    private companion object {
        const val REQUEST_CODE = 1
        val REQUIRED_PERMISSIONS = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    }
}
