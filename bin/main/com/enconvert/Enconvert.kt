/** Enconvert API client (Kotlin/JVM, JDK 17+). */

package com.enconvert

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Duration
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val DEFAULT_BASE_URL = "https://api.enconvert.com"
private const val DEFAULT_TIMEOUT_MS = 300_000L

/** Sent on every API request, used for traffic attribution (overridable via the `userAgent` constructor parameter). */
private const val DEFAULT_USER_AGENT = "enconvert-sdk/$VERSION (kotlin)"
private const val DEFAULT_BATCH_POLL_INTERVAL_MS = 5_000L
private const val DEFAULT_BATCH_TIMEOUT_MS = 1_800_000L

/**
 * Enconvert file conversion client.
 *
 * ```kotlin
 * val client = Enconvert(apiKey = "sk_...")
 * val result = client.convertUrlToPdf("https://example.com")
 * println(result.presignedUrl)
 * ```
 */
public class Enconvert(
    apiKey: String,
    timeout: Long = DEFAULT_TIMEOUT_MS,
    baseUrl: String = DEFAULT_BASE_URL,
    userAgent: String = DEFAULT_USER_AGENT,
) {
    init {
        require(apiKey.isNotBlank()) { "Enconvert: 'apiKey' is required" }
    }

    private val apiKeyValue: String = apiKey
    private val baseUrlValue: String = baseUrl.trimEnd('/')
    private val timeoutMillis: Long = timeout
    private val userAgentValue: String = userAgent
    private val httpClient: HttpClient = HttpClient.newHttpClient()

    /**
     * V2 API namespace: perceive, discover, lookup, distill, ingest, watch.
     * Requires a private API key; endpoints are plan-gated ([QuotaException] on 402).
     */
    public val v2: EnconvertV2 = EnconvertV2(
        request = { path, method, jsonBody -> sendJson(path, method, jsonBody) },
        multipartRequest = { path, body -> sendMultipart(path, body) },
        rawRequest = { path, method, jsonBody -> sendRaw(path, method, jsonBody) },
    )

    // ------------------------------------------------------------------
    // URL conversions (single page)
    // ------------------------------------------------------------------

    /** Convert a URL to PDF. */
    public fun convertUrlToPdf(url: String, opts: UrlToPdfOptions = UrlToPdfOptions()): ConversionResult {
        val body = buildJsonObject {
            putUrlBody(url, opts.render)
            put("single_page", opts.singlePage)
            opts.pdfOptions?.let { put("pdf_options", serializePdfOptions(it)) }
        }
        val result = toConversionResult(postJson("/v1/convert/url-to-pdf", body))
        opts.saveTo?.let { download(result.presignedUrl, it) }
        return result
    }

    /** Convert a URL to a PNG screenshot. */
    public fun convertUrlToScreenshot(url: String, opts: UrlToScreenshotOptions = UrlToScreenshotOptions()): ConversionResult {
        val body = buildJsonObject { putUrlBody(url, opts.render) }
        val result = toConversionResult(postJson("/v1/convert/url-to-screenshot", body))
        opts.saveTo?.let { download(result.presignedUrl, it) }
        return result
    }

    /**
     * Convert a URL to clean GitHub-Flavored Markdown with YAML frontmatter
     * (title, description, url, links, images). Strips nav/footer/ads/scripts
     * and extracts the main article content.
     */
    public fun convertUrlToMarkdown(url: String, opts: UrlToMarkdownOptions = UrlToMarkdownOptions()): ConversionResult {
        val body = buildJsonObject { putUrlBody(url, opts.render) }
        val result = toConversionResult(postJson("/v1/convert/url-to-markdown", body))
        opts.saveTo?.let { download(result.presignedUrl, it) }
        return result
    }

    // ------------------------------------------------------------------
    // Website conversions (async batch, whole-site crawl)
    // ------------------------------------------------------------------

    /**
     * Convert every discovered page of a website to PDF. Async-only: pages are
     * discovered via sitemap or full crawl (plan-dependent), converted in the
     * background, and bundled into a single ZIP. Poll with [getBatchStatus] or
     * block with [waitForBatch]. Requires a private API key with crawl access.
     */
    public fun convertWebsiteToPdf(url: String, opts: WebsiteToPdfOptions = WebsiteToPdfOptions()): BatchSubmission {
        val body = buildJsonObject {
            putWebsiteBody(url, opts.website)
            opts.singlePage?.let { put("single_page", it) }
            opts.pdfOptions?.let { put("pdf_options", serializePdfOptions(it)) }
        }
        // No job-polling fallback: website submissions have no per-job row, so a
        // 5xx here means the submission itself failed and must surface directly.
        return toBatchSubmission(postJson("/v1/convert/website-to-pdf", body, jobFallback = false))
    }

    /**
     * Screenshot every discovered page of a website (PNG). Async-only, bundled
     * into a single ZIP. Poll with [getBatchStatus] or block with [waitForBatch].
     * Requires a private API key with crawl access.
     */
    public fun convertWebsiteToScreenshot(url: String, opts: WebsiteToScreenshotOptions = WebsiteToScreenshotOptions()): BatchSubmission {
        val body = buildJsonObject { putWebsiteBody(url, opts.website) }
        return toBatchSubmission(postJson("/v1/convert/website-to-screenshot", body, jobFallback = false))
    }

    // ------------------------------------------------------------------
    // File conversions — image
    // ------------------------------------------------------------------

    /**
     * Convert an image between formats (jpeg, png, svg, heic, webp), or
     * rasterize a PDF to JPEG. Only pairs implemented by the API are accepted;
     * unsupported pairs throw before any request is made.
     */
    public fun convertImage(path: String, opts: ConvertImageOptions): ConversionResult = convertImageCore(readFilePart(path), opts)

    /** @see convertImage */
    public fun convertImage(path: Path, opts: ConvertImageOptions): ConversionResult = convertImageCore(readFilePart(path), opts)

    /** @see convertImage */
    public fun convertImage(bytes: ByteArray, opts: ConvertImageOptions): ConversionResult = convertImageCore(wrapBytes(bytes), opts)

    /** @see convertImage */
    public fun convertImage(file: FileInput, opts: ConvertImageOptions): ConversionResult = convertImageCore(wrapFileInput(file), opts)

    private fun convertImageCore(part: FilePart, opts: ConvertImageOptions): ConversionResult {
        val inputFormat = resolveInputFormat(part.filename, IMAGE_FORMATS)
        val outputFmt = normalizeOutputFormat(opts.outputFormat)
        val endpoint = "/v1/convert/${assertConversionImplemented(inputFormat, outputFmt)}"
        val result = toConversionResult(postFile(endpoint, part, outputFilename = opts.outputFilename))
        opts.saveTo?.let { download(result.presignedUrl, it) }
        return result
    }

    // ------------------------------------------------------------------
    // File conversions — document
    // ------------------------------------------------------------------

    /**
     * Convert a document (doc, excel, ppt, odt, ods, odp, ots, pages, numbers,
     * html, markdown, csv, json, xml, yaml, toml). Output defaults to pdf.
     * Only pairs implemented by the API are accepted; unsupported pairs throw
     * before any request is made. (EPUB has no dedicated pair — use
     * [convertToPdf] / [convertToMarkdown].)
     */
    public fun convertDocument(path: String, opts: ConvertDocumentOptions = ConvertDocumentOptions()): ConversionResult =
        convertDocumentCore(readFilePart(path), opts)

    /** @see convertDocument */
    public fun convertDocument(path: Path, opts: ConvertDocumentOptions = ConvertDocumentOptions()): ConversionResult =
        convertDocumentCore(readFilePart(path), opts)

    /** @see convertDocument */
    public fun convertDocument(bytes: ByteArray, opts: ConvertDocumentOptions = ConvertDocumentOptions()): ConversionResult =
        convertDocumentCore(wrapBytes(bytes), opts)

    /** @see convertDocument */
    public fun convertDocument(file: FileInput, opts: ConvertDocumentOptions = ConvertDocumentOptions()): ConversionResult =
        convertDocumentCore(wrapFileInput(file), opts)

    private fun convertDocumentCore(part: FilePart, opts: ConvertDocumentOptions): ConversionResult {
        val inputFormat = resolveInputFormat(part.filename, DOCUMENT_FORMATS)
        val outputFmt = normalizeOutputFormat(opts.outputFormat ?: "pdf")
        val endpoint = "/v1/convert/${assertConversionImplemented(inputFormat, outputFmt)}"
        val result = toConversionResult(
            postFile(endpoint, part, outputFilename = opts.outputFilename, pdfOptions = opts.pdfOptions),
        )
        opts.saveTo?.let { download(result.presignedUrl, it) }
        return result
    }

    // ------------------------------------------------------------------
    // File conversions — anything to Markdown / PDF
    // ------------------------------------------------------------------

    /**
     * Convert an uploaded file of (almost) any document format to clean Markdown
     * — PDF, DOCX, PPTX, XLSX, CSV, HTML, EPUB, TXT/MD, and legacy/ODF office.
     * The format is auto-detected server-side; a RAG-ingestion building block.
     * Images are not supported. No client-side format resolution — any file
     * is uploaded as-is and the server decides.
     */
    public fun convertToMarkdown(path: String, opts: ConvertToMarkdownOptions = ConvertToMarkdownOptions()): ConversionResult =
        convertToMarkdownCore(readFilePart(path), opts)

    /** @see convertToMarkdown */
    public fun convertToMarkdown(path: Path, opts: ConvertToMarkdownOptions = ConvertToMarkdownOptions()): ConversionResult =
        convertToMarkdownCore(readFilePart(path), opts)

    /** @see convertToMarkdown */
    public fun convertToMarkdown(bytes: ByteArray, opts: ConvertToMarkdownOptions = ConvertToMarkdownOptions()): ConversionResult =
        convertToMarkdownCore(wrapBytes(bytes), opts)

    /** @see convertToMarkdown */
    public fun convertToMarkdown(file: FileInput, opts: ConvertToMarkdownOptions = ConvertToMarkdownOptions()): ConversionResult =
        convertToMarkdownCore(wrapFileInput(file), opts)

    private fun convertToMarkdownCore(part: FilePart, opts: ConvertToMarkdownOptions): ConversionResult {
        val result = toConversionResult(
            postFile("/v1/convert/anything-to-markdown", part, outputFilename = opts.outputFilename),
        )
        opts.saveTo?.let { download(result.presignedUrl, it) }
        return result
    }

    /**
     * Convert an uploaded file of (almost) any format to PDF — office/ODF/Pages/
     * Numbers/RTF/CSV, HTML, Markdown, text, raster images, SVG, EPUB, or an
     * existing PDF (passthrough/normalise). The format is auto-detected
     * server-side. Only `pdfOptions.grayscale` is honored on this endpoint.
     * No client-side format resolution — any file is uploaded as-is.
     */
    public fun convertToPdf(path: String, opts: ConvertToPdfOptions = ConvertToPdfOptions()): ConversionResult =
        convertToPdfCore(readFilePart(path), opts)

    /** @see convertToPdf */
    public fun convertToPdf(path: Path, opts: ConvertToPdfOptions = ConvertToPdfOptions()): ConversionResult =
        convertToPdfCore(readFilePart(path), opts)

    /** @see convertToPdf */
    public fun convertToPdf(bytes: ByteArray, opts: ConvertToPdfOptions = ConvertToPdfOptions()): ConversionResult =
        convertToPdfCore(wrapBytes(bytes), opts)

    /** @see convertToPdf */
    public fun convertToPdf(file: FileInput, opts: ConvertToPdfOptions = ConvertToPdfOptions()): ConversionResult =
        convertToPdfCore(wrapFileInput(file), opts)

    private fun convertToPdfCore(part: FilePart, opts: ConvertToPdfOptions): ConversionResult {
        val result = toConversionResult(
            postFile("/v1/convert/anything-to-pdf", part, outputFilename = opts.outputFilename, pdfOptions = opts.pdfOptions),
        )
        opts.saveTo?.let { download(result.presignedUrl, it) }
        return result
    }

    // ------------------------------------------------------------------
    // Job + batch status
    // ------------------------------------------------------------------

    /** Poll the status of an async conversion job. */
    public fun getJobStatus(jobId: String): JobStatus {
        val resp = sendJson("/v1/convert/status/$jobId", "GET", null)
        raiseForStatus(resp)
        return toJobStatus(resp.bodyAsJsonObject())
    }

    /**
     * Get the status of an async batch (website conversion). Returns aggregate
     * counts, per-URL statuses, and download URLs. Private API keys only.
     */
    public fun getBatchStatus(batchId: String): BatchStatus {
        val resp = sendJson("/v1/convert/batch/$batchId", "GET", null)
        raiseForStatus(resp)
        return toBatchStatus(resp.bodyAsJsonObject())
    }

    /**
     * Poll a batch until it leaves "processing", then return its final status.
     * With `saveTo`, downloads the batch ZIP once available. Throws
     * [ApiException] 504 on timeout.
     */
    public fun waitForBatch(batchId: String, opts: WaitForBatchOptions = WaitForBatchOptions()): BatchStatus {
        val intervalMs = opts.intervalMs ?: DEFAULT_BATCH_POLL_INTERVAL_MS
        val timeoutMs = opts.timeoutMs ?: DEFAULT_BATCH_TIMEOUT_MS
        val deadline = System.currentTimeMillis() + timeoutMs

        while (true) {
            val status = getBatchStatus(batchId)
            if (status.status != BatchStatusValue.PROCESSING) {
                opts.saveTo?.let { saveTo ->
                    val zipUrl = status.zipDownloadUrl
                        ?: throw ApiException(
                            500,
                            "Batch $batchId finished with status '${status.status.wire}' but no ZIP is available to save",
                        )
                    download(zipUrl, saveTo)
                }
                return status
            }
            if (System.currentTimeMillis() >= deadline) {
                throw ApiException(504, "Batch $batchId did not complete within ${timeoutMs}ms")
            }
            sleepMillis(intervalMs)
        }
    }

    // ------------------------------------------------------------------
    // Internal helpers (mirror of the Node SDK's postJson / postFile /
    // pollJob / download / toFilePart / raiseForStatus)
    // ------------------------------------------------------------------

    private fun postJson(endpoint: String, body: JsonObject, jobFallback: Boolean = true): JsonObject {
        val jobId = if (jobFallback) newJobId() else null
        val fullBody = if (jobId != null) {
            JsonObject(linkedMapOf<String, JsonElement>("job_id" to JsonPrimitive(jobId)).apply { putAll(body) })
        } else {
            body
        }
        try {
            val resp = sendJson(endpoint, "POST", fullBody.toString())
            raiseForStatus(resp)
            val data = resp.bodyAsJsonObject()
            // Some success responses omit job_id (URL sync path); backfill the
            // client-generated id so callers can still poll getJobStatus with it.
            return if (jobId != null) withDefaultJobId(data, jobId) else data
        } catch (e: ApiException) {
            if (jobId != null && e.statusCode >= 500) return pollJob(jobId)
            throw e
        }
    }

    private fun postFile(
        endpoint: String,
        part: FilePart,
        outputFilename: String? = null,
        pdfOptions: PdfOptions? = null,
    ): JsonObject {
        val jobId = newJobId()
        val multipart = MultipartBody.builder()
            .addFile("file", part.filename, part.contentType, part.bytes)
            .addField("direct_download", "false")
            .addField("job_id", jobId)
        if (outputFilename != null) multipart.addField("output_filename", outputFilename)
        if (pdfOptions != null) multipart.addField("pdf_options", serializePdfOptions(pdfOptions).toString())

        try {
            val resp = sendMultipart(endpoint, multipart.build())
            raiseForStatus(resp)
            return withDefaultJobId(resp.bodyAsJsonObject(), jobId)
        } catch (e: ApiException) {
            if (e.statusCode >= 500) return pollJob(jobId)
            throw e
        }
    }

    /** Poll job status until success/failure. Used as fallback when the HTTP request fails. */
    private fun pollJob(jobId: String, maxWaitMs: Long = 300_000, intervalMs: Long = 3_000): JsonObject {
        val deadline = System.currentTimeMillis() + maxWaitMs
        while (System.currentTimeMillis() < deadline) {
            sleepMillis(intervalMs)
            val resp = sendJson("/v1/convert/status/$jobId", "GET", null)
            if (resp.statusCode == 404) continue
            raiseForStatus(resp)
            val data = resp.bodyAsJsonObject()
            when (data.optStr("status")) {
                "success" -> return data
                "failed" -> throw ApiException(500, data.optStr("error") ?: "Conversion failed")
            }
        }
        throw ApiException(504, "Conversion timed out")
    }

    /** Save a presigned URL to a local file. Streams the response to disk. Sends no API key — it's a signed URL. */
    private fun download(url: String, dest: String) {
        val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis(timeoutMillis)).GET().build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in 200..299) {
            val message = response.body().bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            throw ApiException(response.statusCode(), "Failed to download: ${message.ifBlank { "HTTP ${response.statusCode()}" }}")
        }
        val destPath = Paths.get(dest)
        destPath.toAbsolutePath().parent?.let { Files.createDirectories(it) }
        response.body().use { input ->
            Files.newOutputStream(
                destPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            ).use { output -> input.copyTo(output) }
        }
    }

    private fun readFilePart(path: String): FilePart {
        val p = Paths.get(path)
        val filename = p.fileName?.toString() ?: path
        return FilePart(Files.readAllBytes(p), filename, mimeFor(filename))
    }

    private fun readFilePart(path: Path): FilePart {
        val filename = path.fileName?.toString() ?: path.toString()
        return FilePart(Files.readAllBytes(path), filename, mimeFor(filename))
    }

    private fun wrapBytes(bytes: ByteArray): FilePart = FilePart(bytes, "upload.bin", "application/octet-stream")

    private fun wrapFileInput(file: FileInput): FilePart =
        FilePart(file.data, file.filename, file.contentType ?: mimeFor(file.filename))

    /** Centralized JSON request with API key + timeout. Also used as the [RequestFn] handed to [EnconvertV2]. */
    private fun sendJson(path: String, method: String, jsonBody: String?): HttpResponseData {
        val bodyPublisher = jsonBody?.let { HttpRequest.BodyPublishers.ofString(it, StandardCharsets.UTF_8) }
            ?: HttpRequest.BodyPublishers.noBody()
        val builder = HttpRequest.newBuilder(URI.create(baseUrlValue + path))
            .timeout(Duration.ofMillis(timeoutMillis))
            .header("X-API-Key", apiKeyValue)
            .header("User-Agent", userAgentValue)
        when (method) {
            "GET" -> builder.GET()
            "POST" -> builder.header("content-type", "application/json").POST(bodyPublisher)
            "PATCH" -> builder.header("content-type", "application/json").method("PATCH", bodyPublisher)
            "DELETE" -> builder.method("DELETE", bodyPublisher)
            else -> builder.method(method, bodyPublisher)
        }
        val response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        return HttpResponseData(response.statusCode(), response.body())
    }

    /**
     * [sendJson] counterpart returning raw bytes + response headers (names
     * lowercased) — used by the V2 direct-download paths, whose artifact
     * metadata rides on headers instead of a JSON body.
     */
    private fun sendRaw(path: String, method: String, jsonBody: String?): RawHttpResponseData {
        val bodyPublisher = jsonBody?.let { HttpRequest.BodyPublishers.ofString(it, StandardCharsets.UTF_8) }
            ?: HttpRequest.BodyPublishers.noBody()
        val builder = HttpRequest.newBuilder(URI.create(baseUrlValue + path))
            .timeout(Duration.ofMillis(timeoutMillis))
            .header("X-API-Key", apiKeyValue)
            .header("User-Agent", userAgentValue)
        when (method) {
            "GET" -> builder.GET()
            "POST" -> builder.header("content-type", "application/json").POST(bodyPublisher)
            else -> builder.method(method, bodyPublisher)
        }
        val response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
        val headers = mutableMapOf<String, String>()
        for ((name, values) in response.headers().map()) {
            values.firstOrNull()?.let { headers[name.lowercase()] = it }
        }
        return RawHttpResponseData(response.statusCode(), response.body(), headers)
    }

    private fun sendMultipart(path: String, body: MultipartBody): HttpResponseData {
        val request = HttpRequest.newBuilder(URI.create(baseUrlValue + path))
            .timeout(Duration.ofMillis(timeoutMillis))
            .header("X-API-Key", apiKeyValue)
            .header("User-Agent", userAgentValue)
            .header("content-type", body.contentType)
            .POST(body.bodyPublisher())
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        return HttpResponseData(response.statusCode(), response.body())
    }
}

// ----------------------------------------------------------------------
// Module-level helpers (same role as the Node SDK's module-level functions)
// ----------------------------------------------------------------------

private data class FilePart(val bytes: ByteArray, val filename: String, val contentType: String)

private fun withDefaultJobId(data: JsonObject, jobId: String): JsonObject {
    if ("job_id" in data) return data
    return JsonObject(linkedMapOf<String, JsonElement>("job_id" to JsonPrimitive(jobId)).apply { putAll(data) })
}

/** Request body shared by all single-URL conversions. */
private fun JsonObjectBuilder.putUrlBody(url: String, render: UrlRenderOptions) {
    put("url", url)
    put("direct_download", false)
    put("viewport_width", render.viewportWidth ?: 1920)
    put("viewport_height", render.viewportHeight ?: 1080)
    put("load_media", render.loadMedia ?: true)
    put("enable_scroll", render.enableScroll ?: true)
    putIfNotNull("output_filename", render.outputFilename)
    putBrowserAccess(render)
}

/**
 * Request body for website (whole-site) conversions. Render options are only
 * sent when set — the gateway applies the same defaults per page.
 */
private fun JsonObjectBuilder.putWebsiteBody(url: String, w: WebsiteConversionOptions) {
    put("url", url)
    w.crawlMode?.let { put("crawl_mode", it.wire) }
    putIfNotNull("include_patterns", w.includePatterns)
    putIfNotNull("exclude_patterns", w.excludePatterns)
    putIfNotNull("notification_email", w.notificationEmail)
    putIfNotNull("callback_url", w.callbackUrl)
    putIfNotNull("output_filename", w.render.outputFilename)
    putIfNotNull("viewport_width", w.render.viewportWidth)
    putIfNotNull("viewport_height", w.render.viewportHeight)
    putIfNotNull("load_media", w.render.loadMedia)
    putIfNotNull("enable_scroll", w.render.enableScroll)
    putBrowserAccess(w.render)
}

/** Attach the plan-gated auth/cookies/headers fields when provided. */
private fun JsonObjectBuilder.putBrowserAccess(render: UrlRenderOptions) {
    render.auth?.let { put("auth", serializeAuth(it)) }
    render.cookies?.let { put("cookies", JsonArray(it.map(::serializeCookie))) }
    putIfNotNullStringMap("headers", render.headers)
}

private fun toConversionResult(d: JsonObject): ConversionResult {
    // Job-status fallback responses omit `filename`; recover it from the
    // object key so callers never see a blank string unexpectedly.
    val objectKey = d.str("object_key")
    val filename = d.optStr("filename") ?: objectKey.substringAfterLast('/', objectKey)
    return ConversionResult(
        presignedUrl = d.str("presigned_url"),
        objectKey = objectKey,
        filename = filename,
        fileSize = d.optLong("file_size"),
        conversionTimeSeconds = d.optDouble("conversion_time_seconds"),
        jobId = d.optStr("job_id"),
    )
}

private fun toJobStatus(d: JsonObject): JobStatus = JobStatus(
    status = JobStatusValue.fromWire(d.optStr("status")) ?: JobStatusValue.PROCESSING,
    presignedUrl = d.optStr("presigned_url"),
    objectKey = d.optStr("object_key"),
    error = d.optStr("error"),
)

private fun toBatchSubmission(d: JsonObject): BatchSubmission = BatchSubmission(
    batchId = d.str("batch_id"),
    status = d.optStr("status") ?: "processing",
    urlCount = d.intOr("url_count"),
    totalDiscovered = d.optInt("total_discovered"),
    discoveryMethod = d.optStr("discovery_method"),
    outputFormat = d.optStr("output_format"),
)

private fun toBatchStatus(d: JsonObject): BatchStatus = BatchStatus(
    batchId = d.str("batch_id"),
    status = BatchStatusValue.fromWire(d.optStr("status")) ?: BatchStatusValue.PROCESSING,
    total = d.intOr("total"),
    completed = d.intOr("completed"),
    failed = d.intOr("failed"),
    inProgress = d.intOr("in_progress"),
    outputMode = BatchOutputMode.fromWire(d.optStr("output_mode")) ?: BatchOutputMode.INDIVIDUAL,
    zipDownloadUrl = d.optStr("zip_download_url"),
    items = d.objArr("items").map { item ->
        BatchItem(
            sourceUrl = item.str("source_url"),
            status = item.str("status"),
            downloadUrl = item.optStr("download_url"),
            outputFileSize = item.optLong("output_file_size"),
            duration = item.optStr("duration"),
        )
    },
)
