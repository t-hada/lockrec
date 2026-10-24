package dev.thada.lockrec

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.os.PowerManager
import android.service.quicksettings.TileService
import android.util.Log
import java.io.FileOutputStream
import java.time.LocalDateTime

/** マイク録音の前景サービス。開始/停止は [toggle] から */
class RecordingService : Service() {
    private var recorder: AudioRecord? = null
    private var thread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var recording: Recording? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (recording == null) start()
            ACTION_STOP -> stop()
        }
        return START_NOT_STICKY
    }

    private fun start() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO not granted")
            stopSelf()
            return
        }
        val startedAt = LocalDateTime.now()
        val target = RecordingStore.create(this, Minutes.recordingId(startedAt))
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)

        val bufferBytes = AudioRecord.getMinBufferSize(
            Config.SAMPLE_RATE_HZ, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        ) * 4
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC, Config.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes,
        )
        // 通話中などマイクを取れないときは録れていないのに「録音中」と見せないよう即終了する
        if (audioRecord.state == AudioRecord.STATE_INITIALIZED) audioRecord.startRecording()
        if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            Log.e(TAG, "microphone unavailable")
            audioRecord.release()
            target.dir.deleteRecursively()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lockrec:recording").apply { acquire() }
        recorder = audioRecord
        recording = target
        currentRecordingId = target.id
        thread = Thread({ capture(audioRecord, target) }, "lockrec-capture").apply { start() }
        refreshTile(this)
    }

    private fun capture(audioRecord: AudioRecord, target: Recording) {
        val buffer = ByteArray(Config.SAMPLE_RATE_HZ)
        FileOutputStream(target.audio).use { output ->
            output.write(WavFile.header(0, Config.SAMPLE_RATE_HZ))
            while (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) output.write(buffer, 0, read)
                else if (read < 0) {
                    Log.e(TAG, "AudioRecord.read failed: $read")
                    break
                }
            }
        }
    }

    private fun stop() {
        val target = recording
        recorder?.run {
            stop()
            thread?.join()
            release()
        }
        wakeLock?.release()
        recorder = null
        thread = null
        wakeLock = null
        recording = null
        currentRecordingId = null

        if (target != null) {
            WavFile.finalizeHeader(target.audio, Config.SAMPLE_RATE_HZ)
            TranscribeWorker.enqueue(this)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        refreshTile(this)
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "録音中", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        val stopIntent = PendingIntent.getService(
            this, 0, Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_record)
            .setContentTitle("録音中")
            .setColor(getColor(R.color.recording))
            .setColorized(true)
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis())
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .addAction(Notification.Action.Builder(null, "停止", stopIntent).build())
            .build()
    }

    companion object {
        private const val TAG = "lockrec"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_START = "dev.thada.lockrec.START"
        private const val ACTION_STOP = "dev.thada.lockrec.STOP"

        /** 録音中の ID。文字起こしはこの録音を対象外にする */
        @Volatile
        var currentRecordingId: String? = null
            private set

        val isRecording: Boolean get() = currentRecordingId != null

        /** 録音中なら停止、そうでなければ開始 */
        fun toggle(context: Context) {
            val action = if (isRecording) ACTION_STOP else ACTION_START
            val intent = Intent(context, RecordingService::class.java).setAction(action)
            if (action == ACTION_START) context.startForegroundService(intent) else context.startService(intent)
        }

        private fun refreshTile(context: Context) =
            TileService.requestListeningState(context, ComponentName(context, RecordTileService::class.java))
    }
}
