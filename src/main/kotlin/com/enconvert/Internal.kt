/** Shared internal helpers used by both the V1 client and the V2 namespace. */

package com.enconvert

import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Raw HTTP response, decoupling the V1/V2 request logic from the transport. */
public data class HttpResponseData(val statusCode: Int, val bodyText: String) {
    /** Parse the body as a JSON object (empty object for a blank body). */
    public fun bodyAsJsonObject(): JsonObject =
        Json.parseToJsonElement(bodyText.ifBlank { "{}" }).jsonObject
}

/**
 * Authenticated, timeout-wrapped request function bound to the client's base
 * URL and API key. The V1 [Enconvert] client hands this to [EnconvertV2] so
 * the V2 namespace never needs its own transport.
 */
public typealias RequestFn = (path: String, method: String, jsonBody: String?) -> HttpResponseData

/**
 * Multipart POST function bound to the client's base URL, API key, and
 * timeout — the multipart counterpart of [RequestFn], used by
 * [EnconvertV2.ingestFiles]. Internal because [MultipartBody] is a
 * hand-built wire type, not part of the public API surface.
 */
internal typealias MultipartRequestFn = (path: String, body: MultipartBody) -> HttpResponseData

/**
 * Raw-byte HTTP response for the V2 direct-download paths: body bytes plus
 * the response headers (names lowercased), which carry the artifact metadata.
 */
public class RawHttpResponseData(
    public val statusCode: Int,
    public val bodyBytes: ByteArray,
    public val headers: Map<String, String>,
)

/**
 * Raw-byte counterpart of [RequestFn], used by [EnconvertV2.perceiveDirect]
 * and [EnconvertV2.downloadPerceiveArtifact].
 */
public typealias RawRequestFn = (path: String, method: String, jsonBody: String?) -> RawHttpResponseData

/** Generate a 32-character hex job id (UUIDv4 with dashes removed). */
public fun newJobId(): String = UUID.randomUUID().toString().replace("-", "")

/** Sleep for [ms] milliseconds (used by job/batch polling). */
public fun sleepMillis(ms: Long) {
    if (ms > 0) Thread.sleep(ms)
}

/** [raiseForStatus] counterpart for raw-byte responses (error bodies are JSON text). */
public fun raiseForStatusRaw(response: RawHttpResponseData) {
    if (response.statusCode < 400) return
    raiseForStatus(HttpResponseData(response.statusCode, response.bodyBytes.toString(Charsets.UTF_8)))
}

/** Inspect an HTTP response and throw the matching [EnconvertException] subtype for status >= 400. */
public fun raiseForStatus(response: HttpResponseData) {
    if (response.statusCode < 400) return
    val message = extractErrorMessage(response.bodyText, response.statusCode)
    when (response.statusCode) {
        401, 403 -> throw AuthenticationException(message)
        402 -> throw QuotaException(message)
        429 -> throw RateLimitException(message)
        else -> throw ApiException(response.statusCode, message)
    }
}

private fun extractErrorMessage(bodyText: String, status: Int): String {
    if (bodyText.isBlank()) return "HTTP $status"
    return try {
        val obj = Json.parseToJsonElement(bodyText).jsonObject
        obj.optStr("detail") ?: obj.optStr("error") ?: bodyText
    } catch (e: Exception) {
        bodyText
    }
}

/** Serialize [PdfOptions] to snake_case, only-if-set (mirrors internal.ts's serializePdfOptions). */
public fun serializePdfOptions(o: PdfOptions): JsonObject = buildJsonObject {
    putIfNotNull("page_size", o.pageSize)
    putIfNotNull("page_width", o.pageWidth)
    putIfNotNull("page_height", o.pageHeight)
    o.orientation?.let { put("orientation", it.wire) }
    o.margins?.let { m ->
        put(
            "margins",
            buildJsonObject {
                putIfNotNull("top", m.top)
                putIfNotNull("bottom", m.bottom)
                putIfNotNull("left", m.left)
                putIfNotNull("right", m.right)
            },
        )
    }
    putIfNotNull("scale", o.scale)
    putIfNotNull("grayscale", o.grayscale)
    o.header?.let { h ->
        put(
            "header",
            buildJsonObject {
                putIfNotNull("content", h.content)
                putIfNotNull("height", h.height)
            },
        )
    }
    o.footer?.let { f ->
        put(
            "footer",
            buildJsonObject {
                putIfNotNull("content", f.content)
                putIfNotNull("height", f.height)
            },
        )
    }
}
