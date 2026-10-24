package dev.thada.lockrec

import java.io.Closeable

/** whisper.cpp コンテキストの JNI ラッパ */
class Whisper private constructor(private val handle: Long) : Closeable {

    /**
     * 16kHz mono の波形を文字起こし
     *
     * Returns:
     *     波形先頭を 0 とした区間列
     */
    fun transcribe(samples: FloatArray): List<Segment> {
        val status = nativeTranscribe(handle, samples, Config.WHISPER_THREADS, Config.WHISPER_LANGUAGE)
        check(status == 0) { "whisper_full failed: $status" }
        return (0 until nativeSegmentCount(handle)).map { index ->
            Segment(
                startMs = nativeSegmentStartCs(handle, index) * 10,
                text = String(nativeSegmentText(handle, index), Charsets.UTF_8),
            )
        }
    }

    override fun close() = nativeFree(handle)

    companion object {
        init {
            System.loadLibrary("lockrec_whisper")
        }

        /** モデルを読み込む */
        fun load(modelPath: String): Whisper {
            val handle = nativeInit(modelPath)
            check(handle != 0L) { "failed to load model: $modelPath" }
            return Whisper(handle)
        }

        @JvmStatic private external fun nativeInit(modelPath: String): Long
        @JvmStatic private external fun nativeFree(handle: Long)
        @JvmStatic private external fun nativeTranscribe(handle: Long, samples: FloatArray, threads: Int, language: String): Int
        @JvmStatic private external fun nativeSegmentCount(handle: Long): Int
        @JvmStatic private external fun nativeSegmentStartCs(handle: Long, index: Int): Long
        @JvmStatic private external fun nativeSegmentText(handle: Long, index: Int): ByteArray
    }
}
