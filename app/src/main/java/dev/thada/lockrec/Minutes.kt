package dev.thada.lockrec

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 文字起こしの1区間 */
data class Segment(val startMs: Long, val text: String)

/** Android 非依存の整形・命名ロジック（JVM 単体テスト対象） */
object Minutes {
    private val ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    private val HEADER_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** 録音開始時刻から録音 ID（ディレクトリ名）を生成 */
    fun recordingId(start: LocalDateTime): String = start.format(ID_FORMAT)

    /** 録音 ID から開始時刻を復元 */
    fun startOf(recordingId: String): LocalDateTime = LocalDateTime.parse(recordingId, ID_FORMAT)

    /**
     * vault 上の配置パス
     *
     * Args:
     *     recordingId: 録音 ID（yyyyMMdd_HHmmss）
     *     attempt: 1 始まり。同名が既にあるとき 2, 3, ... で `_2` 等を付ける
     */
    fun remotePath(recordingId: String, attempt: Int): String {
        val base = recordingId.substring(0, "yyyyMMdd_HHmm".length)
        val suffix = if (attempt <= 1) "" else "_$attempt"
        return "${Config.MINUTE_DIR}/$base$suffix.md"
    }

    /** 全サンプル数を文字起こし窓の数に分割 */
    fun windowCount(totalSamples: Long, windowSamples: Long): Int =
        ((totalSamples + windowSamples - 1) / windowSamples).toInt()

    /** `[m:ss]` / `[h:mm:ss]` 形式のタイムスタンプ */
    fun timestamp(ms: Long): String {
        val totalS = ms / 1000
        val hours = totalS / 3600
        val minutes = (totalS % 3600) / 60
        val seconds = totalS % 60
        return if (hours > 0) "[%d:%02d:%02d]".format(hours, minutes, seconds)
        else "[%d:%02d]".format(minutes, seconds)
    }

    /**
     * 区間を段落単位（先頭から paragraphMs 以上）にまとめる
     *
     * Time: O(n) / Space: O(n)
     */
    fun paragraphs(segments: List<Segment>, paragraphMs: Long): List<Segment> {
        val result = mutableListOf<Segment>()
        var startMs = 0L
        val buffer = StringBuilder()
        for (segment in segments) {
            val text = segment.text.trim()
            if (text.isEmpty()) continue
            if (buffer.isEmpty()) startMs = segment.startMs
            else if (segment.startMs - startMs >= paragraphMs) {
                result += Segment(startMs, buffer.toString())
                buffer.clear()
                startMs = segment.startMs
            }
            if (buffer.isNotEmpty()) buffer.append(' ')
            buffer.append(text)
        }
        if (buffer.isNotEmpty()) result += Segment(startMs, buffer.toString())
        return result
    }

    /** 議事録 md 本文 */
    fun markdown(recordingId: String, durationMs: Long, segments: List<Segment>): String {
        val start = startOf(recordingId)
        val durationMin = (durationMs + 59_999) / 60_000
        val body = paragraphs(segments, Config.PARAGRAPH_DURATION_S * 1000L)
            .joinToString("\n\n") { "${timestamp(it.startMs)} ${it.text}" }
        return "録音: ${start.format(HEADER_FORMAT)}（${durationMin}分）\n\n$body\n"
    }

    /** 窓ファイル（1行 = `開始ms<TAB>テキスト`）へ直列化 */
    fun encodeSegments(segments: List<Segment>): String =
        segments.joinToString("") { "${it.startMs}\t${it.text.replace('\n', ' ').replace('\t', ' ')}\n" }

    /** 窓ファイルから復元 */
    fun decodeSegments(content: String): List<Segment> =
        content.lineSequence().filter { it.isNotEmpty() }.map { line ->
            val tab = line.indexOf('\t')
            Segment(line.substring(0, tab).toLong(), line.substring(tab + 1))
        }.toList()
}
