package dev.thada.lockrec

/** アプリ全体の固定値。deploy.sh と共有する値は lockrec.properties が一次情報 */
object Config {
    const val SAMPLE_RATE_HZ = 16_000
    const val WINDOW_DURATION_S = 300
    const val PARAGRAPH_DURATION_S = 30
    const val MIN_RECORDING_DURATION_S = 1

    const val WHISPER_LANGUAGE = "ja"
    const val WHISPER_THREADS = 6
    const val MODEL_FILE_NAME = BuildConfig.MODEL_FILE

    const val GITHUB_OWNER = BuildConfig.GITHUB_OWNER
    const val GITHUB_REPO = BuildConfig.GITHUB_REPO
    const val GITHUB_BRANCH = BuildConfig.GITHUB_BRANCH
    const val MINUTE_DIR = BuildConfig.MINUTE_DIR
    const val TOKEN_FILE_NAME = "github_token"
}
