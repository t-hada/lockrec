package dev.thada.lockrec

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/** 充電中に未処理の録音を窓ごとに文字起こしし、transcript.md を作る */
class TranscribeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pending = RecordingStore.all(applicationContext).filter {
            it.id != RecordingService.currentRecordingId && it.audio.exists() && !it.transcript.exists()
        }
        if (pending.isEmpty()) return Result.success()

        val model = RecordingStore.modelFile(applicationContext)
        if (!model.exists()) {
            Log.e(TAG, "model not found: $model")
            return Result.failure()
        }

        showProgress("文字起こし準備中")
        Whisper.load(model.path).use { whisper ->
            for (recording in pending) {
                transcribe(whisper, recording)
                if (isStopped) return Result.retry()
            }
        }
        PushWorker.enqueue(applicationContext)
        return Result.success()
    }

    private suspend fun transcribe(whisper: Whisper, recording: Recording) {
        val totalSamples = WavFile.sampleCount(recording.audio)
        if (totalSamples < Config.MIN_RECORDING_DURATION_S * Config.SAMPLE_RATE_HZ) {
            Log.i(TAG, "discard short recording: ${recording.id}")
            recording.dir.deleteRecursively()
            return
        }
        val windowSamples = Config.WINDOW_DURATION_S.toLong() * Config.SAMPLE_RATE_HZ
        val windowCount = Minutes.windowCount(totalSamples, windowSamples)
        recording.windowsDir.mkdirs()

        for (index in 0 until windowCount) {
            if (isStopped) return
            val windowFile = recording.window(index)
            if (windowFile.exists()) continue
            showProgress("文字起こし中 ${recording.id} (${index + 1}/$windowCount)")

            val startSample = index * windowSamples
            val count = minOf(windowSamples, totalSamples - startSample).toInt()
            val offsetMs = startSample * 1000 / Config.SAMPLE_RATE_HZ
            val segments = whisper.transcribe(WavFile.readSamples(recording.audio, startSample, count))
                .map { it.copy(startMs = it.startMs + offsetMs) }
            windowFile.writeTextAtomically(Minutes.encodeSegments(segments))
        }

        val segments = (0 until windowCount).flatMap { Minutes.decodeSegments(recording.window(it).readText()) }
        val durationMs = totalSamples * 1000 / Config.SAMPLE_RATE_HZ
        recording.transcript.writeTextAtomically(Minutes.markdown(recording.id, durationMs, segments))
        Log.i(TAG, "transcribed: ${recording.id}")
    }

    // 前景化できなくても 10 分枠で窓単位に進み、retry で続きから再開できるため処理は止めない
    private suspend fun showProgress(text: String) {
        try {
            setForeground(foregroundInfo(text))
        } catch (error: IllegalStateException) {
            Log.w(TAG, "setForeground failed", error)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo("文字起こし準備中")

    private fun foregroundInfo(text: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "文字起こし", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_record)
            .setContentTitle(text)
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    companion object {
        private const val TAG = "lockrec"
        private const val CHANNEL_ID = "transcribe"
        private const val NOTIFICATION_ID = 2
        private const val WORK_NAME = "transcribe"

        /** 充電中に走る文字起こしを予約 */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<TranscribeWorker>()
                .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
