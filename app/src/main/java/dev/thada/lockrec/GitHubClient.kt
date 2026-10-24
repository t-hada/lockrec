package dev.thada.lockrec

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

class GitHubException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** GitHub Contents API の最小クライアント（vault リポジトリ固定） */
class GitHubClient(private val token: String) {

    /** パスにあるファイルの本文。無ければ null */
    fun existingContent(path: String): String? {
        val (status, body) = request("GET", "${contentsUrl(path)}?ref=${Config.GITHUB_BRANCH}", null)
        return when (status) {
            404 -> null
            200 -> String(Base64.getMimeDecoder().decode(JSONObject(body).getString("content")), Charsets.UTF_8)
            else -> throw GitHubException("GET $path: $status $body")
        }
    }

    /** 新規ファイルとしてコミット */
    fun create(path: String, content: String, message: String) {
        val payload = JSONObject()
            .put("message", message)
            .put("content", Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8)))
            .put("branch", Config.GITHUB_BRANCH)
        val (status, body) = request("PUT", contentsUrl(path), payload.toString())
        if (status != 201) throw GitHubException("PUT $path: $status $body")
    }

    private fun contentsUrl(path: String): String =
        "https://api.github.com/repos/${Config.GITHUB_OWNER}/${Config.GITHUB_REPO}/contents/" +
            path.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    private fun request(method: String, url: String, body: String?): Pair<Int, String> {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            return status to text
        } catch (error: IOException) {
            throw GitHubException("$method $url", error)
        }
    }
}
