package dev.thada.lockrec

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** 文字起こし済みで未 push の議事録を vault に置く */
class PushWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pending = RecordingStore.all(applicationContext).filter {
            it.transcript.exists() && !it.pushedMarker.exists()
        }
        if (pending.isEmpty()) return Result.success()

        val tokenFile = RecordingStore.tokenFile(applicationContext)
        if (!tokenFile.exists()) {
            Log.e(TAG, "token not found: $tokenFile")
            return Result.failure()
        }
        val client = GitHubClient(tokenFile.readText().trim())
        return try {
            for (recording in pending) push(client, recording)
            Result.success()
        } catch (error: GitHubException) {
            Log.e(TAG, "push failed", error)
            Result.retry()
        }
    }

    private fun push(client: GitHubClient, recording: Recording) {
        val content = recording.transcript.readText()
        var attempt = 1
        while (true) {
            val path = Minutes.remotePath(recording.id, attempt)
            when (client.existingContent(path)) {
                null -> {
                    client.create(path, content, "minute: ${path.substringAfterLast('/')}")
                    break
                }
                // 前回 push 成功後にマーカーだけ書けなかった場合
                content -> break
                else -> attempt++
            }
        }
        recording.pushedMarker.writeTextAtomically(Minutes.remotePath(recording.id, attempt))
        Log.i(TAG, "pushed: ${recording.id} -> ${recording.pushedMarker.readText()}")
    }

    companion object {
        private const val TAG = "lockrec"
        private const val WORK_NAME = "push"

        /** ネットワーク接続時に走る push を予約 */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<PushWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
