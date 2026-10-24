package dev.thada.lockrec

import android.content.Context
import java.io.File

/**
 * 録音1件のディレクトリ。状態はファイルの有無で表す
 *
 * audio.wav → windows/NNNN.tsv（窓ごとの文字起こし）→ transcript.md → pushed.txt（push 先パス）
 */
class Recording(val dir: File) {
    val id: String get() = dir.name
    val audio: File get() = File(dir, "audio.wav")
    val windowsDir: File get() = File(dir, "windows")
    val transcript: File get() = File(dir, "transcript.md")
    val pushedMarker: File get() = File(dir, "pushed.txt")

    fun window(index: Int): File = File(windowsDir, "%04d.tsv".format(index))
}

/** 録音ディレクトリ群の所在と列挙 */
object RecordingStore {
    fun root(context: Context): File = File(context.filesDir, "recordings").apply { mkdirs() }

    fun create(context: Context, id: String): Recording =
        Recording(File(root(context), id).apply { mkdirs() })

    /** 録音 ID 順（= 時刻順）の全件 */
    fun all(context: Context): List<Recording> =
        root(context).listFiles { file -> file.isDirectory }.orEmpty().sortedBy { it.name }.map(::Recording)

    fun modelFile(context: Context): File = File(context.filesDir, "models/${Config.MODEL_FILE_NAME}")

    fun tokenFile(context: Context): File = File(context.filesDir, Config.TOKEN_FILE_NAME)
}

/** 書き込み途中のファイルを読まれないよう一時ファイル経由で置く */
fun File.writeTextAtomically(text: String) {
    val temporary = File(parentFile, "$name.tmp")
    temporary.writeText(text)
    check(temporary.renameTo(this)) { "rename failed: $temporary" }
}
