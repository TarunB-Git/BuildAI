package com.buildai.app.data.local

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.security.MessageDigest

class VerifiedModelDownloaderTest {
    @Test fun resumesTruncatedResponseAndChecksFinalDigest() = runBlocking {
        val bytes = ByteArray(200_000) { (it * 29).toByte() }
        val expectedHash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range")
                return if (range == null) MockResponse().setBody(Buffer().write(bytes))
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                else {
                    val start = range.removePrefix("bytes=").substringBefore('-').toInt()
                    MockResponse().setResponseCode(206)
                        .addHeader("Content-Range", "bytes $start-${bytes.lastIndex}/${bytes.size}")
                        .setBody(Buffer().write(bytes, start, bytes.size - start))
                }
            }
        }
        server.start()
        val dir = Files.createTempDirectory("buildai-resume").toFile()
        val target = dir.resolve("model.gguf")
        try {
            VerifiedModelDownloader.download(server.url("/model.gguf").toUrl(), target,
                bytes.size.toLong(), expectedHash)
            assertArrayEquals(bytes, target.readBytes())
            assertTrue("A Range retry was expected", server.requestCount >= 2)
        } finally {
            server.shutdown()
            target.delete(); dir.delete()
        }
    }

    @Test fun rejectsWrongHash() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("GGUFwrong"))
        server.start()
        val dir = Files.createTempDirectory("buildai-hash").toFile()
        val target = dir.resolve("model.gguf")
        try {
            val result = runCatching {
                VerifiedModelDownloader.download(server.url("/model.gguf").toUrl(), target,
                    9, "0".repeat(64))
            }
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("verification failed"))
        } finally {
            server.shutdown()
            target.delete(); dir.delete()
        }
    }
}
