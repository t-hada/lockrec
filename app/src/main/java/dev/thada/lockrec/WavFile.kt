package dev.thada.lockrec

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 16bit PCM mono WAV の読み書き */
object WavFile {
    const val HEADER_BYTES = 44
    private const val BYTES_PER_SAMPLE = 2

    /** データ長を確定したヘッダ */
    fun header(dataBytes: Long, sampleRateHz: Int): ByteArray =
        ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt((36 + dataBytes).toInt())
            put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1)
            putInt(sampleRateHz); putInt(sampleRateHz * BYTES_PER_SAMPLE)
            putShort(BYTES_PER_SAMPLE.toShort()); putShort(16)
            put("data".toByteArray()); putInt(dataBytes.toInt())
        }.array()

    /** 録音を途中で落とされたファイルでも読めるよう、ヘッダではなくファイル長から求める */
    fun sampleCount(file: File): Long = maxOf(0L, file.length() - HEADER_BYTES) / BYTES_PER_SAMPLE

    /** 指定範囲を [-1, 1] の float 列で読む */
    fun readSamples(file: File, startSample: Long, count: Int): FloatArray {
        val bytes = ByteArray(count * BYTES_PER_SAMPLE)
        RandomAccessFile(file, "r").use { input ->
            input.seek(HEADER_BYTES + startSample * BYTES_PER_SAMPLE)
            input.readFully(bytes)
        }
        val shorts = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return FloatArray(count) { shorts.get(it) / 32768f }
    }

    /** ファイル長に合わせてヘッダを書き直す */
    fun finalizeHeader(file: File, sampleRateHz: Int) {
        RandomAccessFile(file, "rw").use { output ->
            output.seek(0)
            output.write(header(file.length() - HEADER_BYTES, sampleRateHz))
        }
    }
}
