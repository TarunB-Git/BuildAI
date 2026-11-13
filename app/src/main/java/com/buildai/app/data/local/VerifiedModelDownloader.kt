package com.buildai.app.data.local

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Streams a pinned file, resumes interrupted responses, and accepts it only after a full hash check. */
object VerifiedModelDownloader {
    suspend fun download(url: URL, target: File, expectedBytes: Long, expectedSha256: String,
        onProgress: suspend (Long) -> Unit = {}) {
        require(expectedBytes > 0 && !target.exists())
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
        var interruptions = 0
        val buffer = ByteArray(256 * 1024)
        while (count < expectedBytes) {
            currentCoroutineContext().ensureActive()
            var connection: HttpURLConnection? = null
            try {
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "BuildAI/0.2")
                    if (count > 0) setRequestProperty("Range", "bytes=$count-")
                }
                val status = connection.responseCode
                if (count == 0L) check(status == HttpURLConnection.HTTP_OK || status == HttpURLConnection.HTTP_PARTIAL) {
                    "Download failed (HTTP $status). Try again later."
                } else {
                    check(status == HttpURLConnection.HTTP_PARTIAL &&
                        connection.getHeaderField("Content-Range")?.startsWith("bytes $count-") == true) {
                        "The download server could not resume this model. Try again."
                    }
                }
                connection.inputStream.buffered().use { input ->
                    FileOutputStream(target, count > 0).use { output ->
                        while (count < expectedBytes) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            check(count + read <= expectedBytes) { "Download exceeds the expected model size." }
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            count += read
                            onProgress(count)
                        }
                        output.fd.sync()
                    }
                }
            } catch (error: IOException) {
                if (interruptions >= 5) throw IOException("Model download was interrupted repeatedly.", error)
            } finally {
                connection?.disconnect()
            }
            if (count < expectedBytes) {
                if (interruptions >= 5) throw IOException("Model download was incomplete after retries.")
                interruptions++
                delay((1L shl interruptions.coerceAtMost(4)) * 1_000)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        check(hash.equals(expectedSha256, ignoreCase = true)) {
            "Model verification failed. The previous model is still available."
        }
    }
}
