/**
 * Hand-built `multipart/form-data` body. `java.net.http.HttpClient` has no
 * built-in multipart support, and file bytes must never be routed through a
 * `String` (which would corrupt binary content), so each part is kept as its
 * own byte array and streamed via [java.net.http.HttpRequest.BodyPublishers.ofByteArrays].
 */

package com.enconvert

import java.net.http.HttpRequest
import java.nio.charset.StandardCharsets
import java.util.UUID

internal class MultipartBody private constructor(
    val contentType: String,
    private val parts: List<ByteArray>,
) {
    fun bodyPublisher(): HttpRequest.BodyPublisher = HttpRequest.BodyPublishers.ofByteArrays(parts)

    class Builder {
        private val boundary = "----EnconvertBoundary" + UUID.randomUUID().toString().replace("-", "")
        private val parts = mutableListOf<ByteArray>()

        fun addField(name: String, value: String): Builder {
            parts += (
                "--$boundary\r\n" +
                    "Content-Disposition: form-data; name=\"${escape(name)}\"\r\n\r\n" +
                    "$value\r\n"
                ).toByteArray(StandardCharsets.UTF_8)
            return this
        }

        fun addFile(name: String, filename: String, contentType: String, bytes: ByteArray): Builder {
            parts += (
                "--$boundary\r\n" +
                    "Content-Disposition: form-data; name=\"${escape(name)}\"; filename=\"${escape(filename)}\"\r\n" +
                    "Content-Type: $contentType\r\n\r\n"
                ).toByteArray(StandardCharsets.UTF_8)
            parts += bytes
            parts += "\r\n".toByteArray(StandardCharsets.UTF_8)
            return this
        }

        fun build(): MultipartBody {
            parts += "--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8)
            return MultipartBody("multipart/form-data; boundary=$boundary", parts.toList())
        }

        private fun escape(value: String): String = value.replace("\"", "\\\"")
    }

    companion object {
        fun builder(): Builder = Builder()
    }
}
