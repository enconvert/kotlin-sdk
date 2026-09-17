/**
 * V2 API namespace, reached as `client.v2`.
 *
 * One method per V2 endpoint (21 total across six groups: perceive,
 * discover, lookup, distill, ingest, watch). Options are Kotlin data
 * classes and serialized to the API's snake_case wire format; responses are
 * mapped back to Kotlin data classes. User-data payloads (schemas, extracted
 * data, tracked fields, diff changes) pass through untouched as
 * `Map<String, Any?>`.
 *
 * All V2 endpoints require a private API key (public keys are rejected) and
 * are plan-gated: a disabled feature or exhausted monthly quota raises
 * [QuotaException] (HTTP 402).
 */

package com.enconvert

import java.net.URLEncoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

public class EnconvertV2 internal constructor(
    private val request: RequestFn,
    private val multipartRequest: MultipartRequestFn,
    private val rawRequest: RawRequestFn,
) {

    // ------------------------------------------------------------------
    // Perceive — render a URL into agent-ready artifacts
    // ------------------------------------------------------------------

    /**
     * Render one URL into the requested outputs (markdown, screenshots, PDF,
     * links, structured data, ...). Synchronous: returns the completed
     * operation with 15-minute signed artifact URLs.
     */
    public fun perceive(url: String, opts: PerceiveOptions = PerceiveOptions()): PerceiveResult {
        val body = buildJsonObject {
            serializePerceiveOptionsInto(this, opts)
            put("url", url)
        }
        return toPerceiveResult(post("/v2/perceive", body))
    }

    /**
     * Re-fetch a perceive operation by id (`per_...`). Artifact URLs are
     * freshly re-signed on every call.
     */
    public fun getPerceiveOperation(operationId: String): PerceiveResult =
        toPerceiveResult(get("/v2/perceive/${encodePathSegment(operationId)}"))

    /**
     * Perceive up to 1000 URLs with one shared options block. Small batches
     * run inline (completed result); larger ones return status "queued" —
     * poll [getPerceiveBatch] with the jobId.
     */
    public fun perceiveBatch(urls: List<String>, opts: PerceiveBatchOptions = PerceiveBatchOptions()): PerceiveBatchResult {
        val body = buildJsonObject {
            put("urls", JsonArray(urls.map { JsonPrimitive(it) }))
            put("options", serializePerceiveOptions(opts.options))
            opts.outputMode?.let { put("output_mode", it.wire) }
        }
        return toPerceiveBatchResult(post("/v2/perceive/batch", body))
    }

    /** Poll a perceive batch by jobId. Items fill in as URLs complete. */
    public fun getPerceiveBatch(jobId: String): PerceiveBatchResult =
        toPerceiveBatchResult(get("/v2/perceive/batch/${encodePathSegment(jobId)}"))

    /**
     * Render one URL and stream the artifact bytes back directly
     * (direct_download), skipping the JSON envelope and the signed-URL round
     * trip. Requires exactly one artifact-producing output in
     * [PerceiveOptions.outputs]; metadata is returned via response headers.
     */
    public fun perceiveDirect(url: String, opts: PerceiveOptions = PerceiveOptions()): PerceiveDirectResult {
        val outputs = opts.outputs ?: listOf(PerceiveOutputName.MARKDOWN, PerceiveOutputName.STRUCTURED)
        val artifactCount = outputs.count { it in ARTIFACT_OUTPUTS }
        if (artifactCount != 1) {
            val valid = ARTIFACT_OUTPUTS.joinToString(", ") { it.wire }
            throw EnconvertException(
                "perceiveDirect: requires exactly one artifact-producing output ($valid); got $artifactCount",
            )
        }
        val body = buildJsonObject {
            serializePerceiveOptionsInto(this, opts)
            put("url", url)
            put("direct_download", true)
        }
        val response = rawRequest("/v2/perceive", "POST", body.toString())
        raiseForStatusRaw(response)
        return toPerceiveDirectResult(response)
    }

    /**
     * Stream one stored artifact of an earlier perceive operation. [output]
     * may be omitted when the operation produced exactly one artifact
     * (otherwise 400 listing the available outputs); 410 once the artifact
     * passes the plan's retention window.
     */
    public fun downloadPerceiveArtifact(operationId: String, output: PerceiveOutputName? = null): PerceiveDirectResult {
        var path = "/v2/perceive/${encodePathSegment(operationId)}?direct_download=true"
        output?.let { path += "&output=${it.wire}" }
        val response = rawRequest(path, "GET", null)
        raiseForStatusRaw(response)
        return toPerceiveDirectResult(response)
    }

    // ------------------------------------------------------------------
    // Discover — enumerate a site's URLs without rendering
    // ------------------------------------------------------------------

    /**
     * List a site's URLs via sitemap, HTTP crawl, or both. No browser
     * rendering — fast and does not consume perceive quota.
     */
    public fun discover(url: String, opts: DiscoverOptions = DiscoverOptions()): DiscoverResult {
        val body = buildJsonObject {
            put("url", url)
            opts.mode?.let { put("mode", it.wire) }
            putIfNotNull("max_urls", opts.maxUrls)
            putIfNotNull("max_depth", opts.maxDepth)
            putIfNotNull("include_patterns", opts.includePatterns)
            putIfNotNull("exclude_patterns", opts.excludePatterns)
            putIfNotNull("same_domain_only", opts.sameDomainOnly)
            putIfNotNull("respect_robots", opts.respectRobots)
        }
        return toDiscoverResult(post("/v2/discover", body))
    }

    // ------------------------------------------------------------------
    // Lookup — web search with optional auto-perceive
    // ------------------------------------------------------------------

    /**
     * Run a categorized web search. With perceiveTop > 0, the top-N result
     * URLs are auto-perceived (each consumes one perceive-quota unit) and
     * carry their full [PerceiveResult] inline.
     */
    public fun lookup(query: String, opts: LookupOptions = LookupOptions()): LookupResult {
        val body = buildJsonObject {
            put("query", query)
            opts.category?.let { put("category", it.wire) }
            putIfNotNull("country", opts.country)
            putIfNotNull("locale", opts.locale)
            opts.timeFilter?.let { put("time_filter", it.wire) }
            putIfNotNull("num_results", opts.numResults)
            putIfNotNull("page", opts.page)
            putIfNotNull("location", opts.location)
            putIfNotNull("autocorrect", opts.autocorrect)
            putIfNotNull("perceive_top", opts.perceiveTop)
        }
        return toLookupResult(post("/v2/lookup", body))
    }

    // ------------------------------------------------------------------
    // Distill — schema-driven structured extraction
    // ------------------------------------------------------------------

    /**
     * Extract structured data matching `schema` from explicit URLs or from
     * a discovered site. An optional cssSchema answers fields for free;
     * anything it misses escalates to the LLM tier (plan-gated).
     */
    public fun distill(opts: DistillOptions): DistillResult {
        val hasUrls = !opts.urls.isNullOrEmpty()
        val hasDiscover = opts.discoverFrom != null
        if (hasUrls == hasDiscover) {
            throw EnconvertException("distill: provide exactly one of 'urls' or 'discoverFrom'")
        }

        val body = buildJsonObject {
            put("schema", opts.schema.toJsonObject())
            if (hasUrls) putIfNotNull("urls", opts.urls)
            opts.discoverFrom?.let { df ->
                put(
                    "discover_from",
                    buildJsonObject {
                        put("url", df.url)
                        df.mode?.let { put("mode", it.wire) }
                        putIfNotNull("max_pages", df.maxPages)
                    },
                )
            }
            opts.cssSchema?.let { put("css_schema", serializeCssSchema(it)) }
            putIfNotNull("wait_for", opts.waitFor)
            putIfNotNull("wait_timeout_ms", opts.waitTimeoutMs)
            putIfNotNullStringMap("headers", opts.headers)
            opts.cookies?.let { put("cookies", JsonArray(it.map(::serializeCookie))) }
            putIfNotNull("respect_robots", opts.respectRobots)
        }
        return toDistillResult(post("/v2/distill", body))
    }

    // ------------------------------------------------------------------
    // Ingest — site to RAG-ready JSONL chunks (always async)
    // ------------------------------------------------------------------

    /**
     * Start an ingest job: turn explicit URLs or a discovered site into
     * chunked, RAG-ready JSONL. Always asynchronous — returns the queued
     * job; poll [getIngestJob] or configure webhookUrl for completion.
     */
    public fun ingest(opts: IngestOptions): IngestJob {
        val mode = opts.mode ?: IngestMode.URLS
        if (mode == IngestMode.URLS) {
            if (opts.urls.isNullOrEmpty()) {
                throw EnconvertException("ingest: mode 'urls' requires a non-empty 'urls' list")
            }
            if (opts.url != null) throw EnconvertException("ingest: mode 'urls' does not accept 'url'")
        } else {
            if (opts.url.isNullOrEmpty()) {
                throw EnconvertException("ingest: mode '${mode.wire}' requires a seed 'url'")
            }
            if (opts.urls != null) throw EnconvertException("ingest: mode '${mode.wire}' does not accept 'urls'")
        }

        val body = buildJsonObject {
            opts.mode?.let { put("mode", it.wire) }
            putIfNotNull("url", opts.url)
            putIfNotNull("urls", opts.urls)
            putIfNotNull("max_pages", opts.maxPages)
            putIfNotNull("max_depth", opts.maxDepth)
            putIfNotNull("same_domain_only", opts.sameDomainOnly)
            putIfNotNull("include_patterns", opts.includePatterns)
            putIfNotNull("exclude_patterns", opts.excludePatterns)
            putIfNotNull("respect_robots", opts.respectRobots)
            putIfNotNull("wait_for", opts.waitFor)
            putIfNotNull("wait_timeout_ms", opts.waitTimeoutMs)
            opts.chunk?.let { c ->
                put(
                    "chunk",
                    buildJsonObject {
                        putIfNotNull("max_words", c.maxWords)
                        putIfNotNull("sentence_overlap", c.sentenceOverlap)
                    },
                )
            }
            putIfNotNull("webhook_url", opts.webhookUrl)
        }
        return toIngestJob(post("/v2/ingest", body))
    }

    /**
     * Ingest one or more uploaded FILES into RAG-ready JSONL chunks — the file
     * counterpart of [ingest], sharing the same job lifecycle (mode "files").
     * PDF, DOCX, PPTX, XLSX, CSV, HTML, EPUB, TXT/MD and legacy/ODF office are
     * accepted. Always asynchronous; poll [getIngestJob] or configure a webhook.
     */
    public fun ingestFiles(files: List<FileInput>, opts: IngestFilesOptions = IngestFilesOptions()): IngestJob {
        if (files.isEmpty()) throw EnconvertException("ingestFiles: provide at least one file")
        val multipart = MultipartBody.builder()
        for (file in files) {
            multipart.addFile("files", file.filename, file.contentType ?: mimeFor(file.filename), file.data)
        }
        opts.chunk?.maxWords?.let { multipart.addField("max_words", it.toString()) }
        opts.chunk?.sentenceOverlap?.let { multipart.addField("sentence_overlap", it.toString()) }
        opts.webhookUrl?.let { multipart.addField("webhook_url", it) }
        val response = multipartRequest("/v2/ingest/files", multipart.build())
        raiseForStatus(response)
        return toIngestJob(response.bodyAsJsonObject())
    }

    /** List ingest jobs, newest first. */
    public fun listIngestJobs(opts: V2ListOptions = V2ListOptions()): IngestJobList {
        val d = get("/v2/ingest${listQuery(opts)}")
        return IngestJobList(
            jobs = d.objArr("jobs").map(::toIngestJobSummary),
            skip = d.intOr("skip"),
            limit = d.intOr("limit", 20),
            hasMore = d.boolOr("has_more"),
        )
    }

    /** Get one ingest job by id (`ing_...`). */
    public fun getIngestJob(jobId: String): IngestJob = toIngestJob(get("/v2/ingest/${encodePathSegment(jobId)}"))

    /**
     * Cancel a queued/processing ingest job. Idempotent: canceling an
     * already-terminal job returns it unchanged.
     */
    public fun cancelIngestJob(jobId: String): IngestJob = toIngestJob(delete("/v2/ingest/${encodePathSegment(jobId)}"))

    /**
     * Re-deliver the completion webhook of a completed job (409 if the job
     * is not completed, 400 if it has no webhook configured).
     */
    public fun retryIngestWebhook(jobId: String): WebhookRetryResult {
        val d = post("/v2/ingest/${encodePathSegment(jobId)}/retry-webhook")
        return WebhookRetryResult(
            jobId = d.str("job_id"),
            delivered = d.boolOr("delivered"),
            attempts = d.intOr("attempts"),
            statusCode = d.optInt("status_code"),
            detail = d.str("detail"),
        )
    }

    /**
     * Get (creating on first call) the project's webhook signing secret and
     * the header/scheme details needed to verify deliveries.
     */
    public fun getWebhookSecret(): WebhookSecret = toWebhookSecret(get("/v2/ingest/webhook-secret"))

    /**
     * Rotate the webhook signing secret. Signatures made with the previous
     * secret stop verifying immediately.
     */
    public fun rotateWebhookSecret(): WebhookSecret = toWebhookSecret(post("/v2/ingest/webhook-secret/rotate"))

    // ------------------------------------------------------------------
    // Watch — recurring change monitoring
    // ------------------------------------------------------------------

    /**
     * Create a watcher that re-renders `url` on a fixed cadence (hourly
     * floor) and notifies on changes via email and/or webhook.
     */
    public fun createWatcher(url: String, opts: WatchCreateOptions = WatchCreateOptions()): Watcher {
        val body = buildJsonObject {
            put("url", url)
            putIfNotNull("frequency_minutes", opts.frequencyMinutes)
            opts.diffMode?.let { put("diff_mode", it.wire) }
            putIfNotNullMap("track_fields", opts.trackFields)
            putIfNotNull("webhook_url", opts.webhookUrl)
            putIfNotNull("notify_email", opts.notifyEmail)
        }
        return toWatcher(post("/v2/watch", body))
    }

    /** List watchers, newest first. */
    public fun listWatchers(opts: V2ListOptions = V2ListOptions()): WatcherList {
        val d = get("/v2/watch${listQuery(opts)}")
        return WatcherList(
            watchers = d.objArr("watchers").map(::toWatcherSummary),
            skip = d.intOr("skip"),
            limit = d.intOr("limit", 20),
            hasMore = d.boolOr("has_more"),
        )
    }

    /** Get one watcher by id (`wat_...`). Deleted watchers read as 404. */
    public fun getWatcher(watcherId: String): Watcher = toWatcher(get("/v2/watch/${encodePathSegment(watcherId)}"))

    /** Page through a watcher's check history, newest first. */
    public fun getWatcherSnapshots(watcherId: String, opts: SnapshotListOptions = SnapshotListOptions()): WatcherSnapshotList {
        val query = opts.limit?.let { "?limit=$it" } ?: ""
        val d = get("/v2/watch/${encodePathSegment(watcherId)}/snapshots$query")
        return WatcherSnapshotList(
            watcherId = d.str("watcher_id"),
            snapshots = d.objArr("snapshots").map(::toWatcherSnapshot),
            limit = d.intOr("limit", 20),
        )
    }

    /**
     * Update a watcher. At least one field is required. Set webhookUrl to
     * "" to clear the webhook; resuming a paused watcher re-checks the
     * plan's watcher cap.
     */
    public fun updateWatcher(watcherId: String, updates: WatcherUpdate): Watcher {
        val body = buildJsonObject {
            putIfNotNull("frequency_minutes", updates.frequencyMinutes)
            updates.diffMode?.let { put("diff_mode", it.wire) }
            putIfNotNullMap("track_fields", updates.trackFields)
            putIfNotNull("webhook_url", updates.webhookUrl)
            putIfNotNull("notify_email", updates.notifyEmail)
            updates.status?.let { put("status", it.wire) }
        }
        if (body.isEmpty()) throw EnconvertException("updateWatcher: provide at least one field to update")
        return toWatcher(patch("/v2/watch/${encodePathSegment(watcherId)}", body))
    }

    /**
     * Soft-delete a watcher (idempotent). Returns the tombstoned watcher
     * with status "deleted".
     */
    public fun deleteWatcher(watcherId: String): Watcher = toWatcher(delete("/v2/watch/${encodePathSegment(watcherId)}"))

    // ------------------------------------------------------------------
    // HTTP helpers
    // ------------------------------------------------------------------

    private fun raw(path: String, method: String, jsonBody: String?): JsonObject {
        val response = request(path, method, jsonBody)
        raiseForStatus(response)
        return response.bodyAsJsonObject()
    }

    private fun post(path: String, body: JsonObject? = null): JsonObject = raw(path, "POST", body?.toString())

    private fun get(path: String): JsonObject = raw(path, "GET", null)

    private fun patch(path: String, body: JsonObject): JsonObject = raw(path, "PATCH", body.toString())

    private fun delete(path: String): JsonObject = raw(path, "DELETE", null)
}

// ----------------------------------------------------------------------
// Path segment encoding
// ----------------------------------------------------------------------

private fun encodePathSegment(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

/** Artifact-producing outputs accepted by [EnconvertV2.perceiveDirect] (everything except STRUCTURED, which is inline JSON). */
private val ARTIFACT_OUTPUTS: List<PerceiveOutputName> =
    PerceiveOutputName.entries.filter { it != PerceiveOutputName.STRUCTURED }

// ----------------------------------------------------------------------
// Request serializers
// ----------------------------------------------------------------------

private fun serializePerceiveOptions(o: PerceiveOptions): JsonObject = buildJsonObject { serializePerceiveOptionsInto(this, o) }

private fun serializePerceiveOptionsInto(builder: JsonObjectBuilder, o: PerceiveOptions) {
    with(builder) {
        o.outputs?.let { put("outputs", JsonArray(it.map { v -> JsonPrimitive(v.wire) })) }
        o.extract?.let { put("extract", JsonArray(it.map { v -> JsonPrimitive(v.wire) })) }
        putIfNotNullMap("schema", o.schema)
        putIfNotNull("wait_for", o.waitFor)
        putIfNotNull("wait_timeout_ms", o.waitTimeoutMs)
        putIfNotNull("js_code", o.jsCode)
        o.viewport?.let { v ->
            put(
                "viewport",
                buildJsonObject {
                    putIfNotNull("width", v.width)
                    putIfNotNull("height", v.height)
                },
            )
        }
        putIfNotNullStringMap("headers", o.headers)
        o.cookies?.let { put("cookies", JsonArray(it.map(::serializeCookie))) }
        o.auth?.let { put("auth", serializeAuth(it)) }
        putIfNotNull("proxy_url", o.proxyUrl)
        putIfNotNullMap("geolocation", o.geolocation)
        o.actionChain?.let { put("action_chain", JsonArray(it.map { m -> m.toJsonObject() })) }
        o.cacheMode?.let { put("cache_mode", it.wire) }
        o.pdfOptions?.let { put("pdf_options", serializePdfOptions(it)) }
        o.blockResources?.let { put("block_resources", JsonArray(it.map { v -> JsonPrimitive(v.wire) })) }
        putIfNotNull("respect_robots", o.respectRobots)
        putIfNotNull("mobile", o.mobile)
        putIfNotNull("only_main_content", o.onlyMainContent)
        putIfNotNull("direct_download", o.directDownload)
    }
}

/**
 * Cookies are passed through to the wire with the SDK's own camelCase field
 * names (`httpOnly`, `sameSite`) — mirrors the Node SDK, which assigns
 * `opts.cookies` onto the request body untransformed rather than snake-casing
 * it. `internal` (not `private`) so the V1 client can reuse it verbatim.
 */
internal fun serializeCookie(c: BrowserCookie): JsonObject = buildJsonObject {
    put("name", c.name)
    put("value", c.value)
    putIfNotNull("domain", c.domain)
    putIfNotNull("url", c.url)
    putIfNotNull("path", c.path)
    putIfNotNull("expires", c.expires)
    putIfNotNull("httpOnly", c.httpOnly)
    putIfNotNull("secure", c.secure)
    c.sameSite?.let { put("sameSite", it.wire) }
}

/** `internal` (not `private`) so the V1 client can reuse it verbatim. */
internal fun serializeAuth(a: HttpBasicAuth): JsonObject = buildJsonObject {
    put("username", a.username)
    put("password", a.password)
}

private fun serializeCssField(f: CssField): JsonObject = buildJsonObject {
    put("name", f.name)
    put("type", f.type.wire)
    putIfNotNull("selector", f.selector)
    putIfNotNull("attribute", f.attribute)
    putIfNotNull("pattern", f.pattern)
    f.default?.let { put("default", anyToJsonElement(it)) }
    f.transform?.let { put("transform", it.wire) }
    f.fields?.let { put("fields", JsonArray(it.map(::serializeCssField))) }
}

/**
 * `baseSelector` (and `name`) are sent as-is; only `targetField` is
 * snake-cased — mirrors v2.ts's serializeCssSchema verbatim.
 */
private fun serializeCssSchema(s: CssSchema): JsonObject = buildJsonObject {
    put("baseSelector", s.baseSelector)
    put("fields", JsonArray(s.fields.map(::serializeCssField)))
    putIfNotNull("name", s.name)
    putIfNotNull("target_field", s.targetField)
}

// ----------------------------------------------------------------------
// Response mappers. Every field access is guarded — the API omits null
// fields entirely (response_model_exclude_none).
// ----------------------------------------------------------------------

private fun JsonObject.intValueMap(key: String): Map<String, Int> =
    (this[key] as? JsonObject)?.entries?.associate { (k, v) -> k to ((v as? JsonPrimitive)?.intOrNull ?: 0) } ?: emptyMap()

private fun JsonObject.doubleValueMap(key: String): Map<String, Double> =
    (this[key] as? JsonObject)?.entries?.associate { (k, v) -> k to ((v as? JsonPrimitive)?.doubleOrNull ?: 0.0) } ?: emptyMap()

private fun toTokens(o: JsonObject?): V2Tokens = V2Tokens(
    input = o?.intOr("input") ?: 0,
    output = o?.intOr("output") ?: 0,
)

private fun toOutputArtifact(o: JsonObject?): V2OutputArtifact {
    val d = o ?: JsonObject(emptyMap())
    return V2OutputArtifact(
        url = d.optStr("url"),
        objectKey = d.str("object_key"),
        sizeBytes = d.longOr("size_bytes"),
        contentType = d.str("content_type", "application/octet-stream"),
        expiresIn = d.intOr("expires_in", 900),
    )
}

private fun toPerceiveResult(d: JsonObject): PerceiveResult {
    val rawOutputs = d.obj("outputs") ?: JsonObject(emptyMap())
    val outputs = rawOutputs.entries.associate { (name, artifact) -> name to toOutputArtifact(artifact as? JsonObject) }
    return PerceiveResult(
        operationId = d.str("operation_id"),
        status = PerceiveStatus.fromWire(d.optStr("status")) ?: PerceiveStatus.QUEUED,
        url = d.str("url"),
        urlFinal = d.optStr("url_final"),
        contentHash = d.optStr("content_hash"),
        renderQuality = d.optDouble("render_quality"),
        statusCode = d.optInt("status_code"),
        deductions = d.doubleValueMap("deductions"),
        cacheHit = d.boolOr("cache_hit"),
        outputs = outputs,
        structured = d.optObj("structured"),
        extractionTier = PerceiveExtractionTier.fromWire(d.optStr("extraction_tier")),
        tokens = toTokens(d.obj("tokens")),
        costCents = d.doubleOr("cost_cents"),
        durationMs = d.optLong("duration_ms"),
        error = d.optStr("error"),
        warnings = d.strArr("warnings"),
        optionsEcho = d.optObj("options_echo"),
    )
}

/** Builds a direct-download result from the raw body + response headers (names lowercased). */
private fun toPerceiveDirectResult(response: RawHttpResponseData): PerceiveDirectResult {
    val headers = response.headers
    return PerceiveDirectResult(
        content = response.bodyBytes,
        contentType = headers["content-type"] ?: "application/octet-stream",
        filename = headers["content-disposition"]?.let(::filenameFromContentDisposition),
        operationId = headers["x-operation-id"] ?: "",
        objectKey = headers["x-object-key"] ?: "",
        cacheHit = headers["x-cache-hit"] == "true",
        renderQuality = headers["x-render-quality"]?.toDoubleOrNull(),
        sourceStatusCode = headers["x-source-status-code"]?.toIntOrNull(),
        contentHash = headers["x-content-hash"],
        warningsCount = headers["x-warnings-count"]?.toIntOrNull() ?: 0,
    )
}

/** Extracts the filename="..." (or bare-token) value from a Content-Disposition header, or null when it carries none. */
private fun filenameFromContentDisposition(value: String): String? {
    for (part in value.split(";")) {
        val trimmed = part.trim()
        if (!trimmed.lowercase().startsWith("filename=")) continue
        var filename = trimmed.substring("filename=".length)
        if (filename.length >= 2 && filename.startsWith("\"") && filename.endsWith("\"")) {
            filename = filename.substring(1, filename.length - 1)
        }
        return filename.ifEmpty { null }
    }
    return null
}

private fun toPerceiveBatchResult(d: JsonObject): PerceiveBatchResult = PerceiveBatchResult(
    jobId = d.str("job_id"),
    status = PerceiveBatchStatus.fromWire(d.optStr("status")) ?: PerceiveBatchStatus.QUEUED,
    outputMode = PerceiveBatchOutputMode.fromWire(d.optStr("output_mode")) ?: PerceiveBatchOutputMode.MANIFEST,
    total = d.intOr("total"),
    completed = d.intOr("completed"),
    failed = d.intOr("failed"),
    pending = d.intOr("pending"),
    zip = d.obj("zip")?.let { toOutputArtifact(it) },
    items = d.objArr("items").map(::toPerceiveResult),
    warnings = d.strArr("warnings"),
)

private fun toDiscoverResult(d: JsonObject): DiscoverResult = DiscoverResult(
    url = d.str("url"),
    mode = DiscoverMode.fromWire(d.optStr("mode")) ?: DiscoverMode.HYBRID,
    total = d.intOr("total"),
    urls = d.strArr("urls"),
    pagesCrawled = d.intOr("pages_crawled"),
    truncated = d.boolOr("truncated"),
    robotsRespected = d.boolOr("robots_respected"),
    sources = d.intValueMap("sources"),
    warnings = d.strArr("warnings"),
)

private fun toLookupItem(d: JsonObject): LookupItem = LookupItem(
    title = d.optStr("title"),
    url = d.optStr("url"),
    snippet = d.optStr("snippet"),
    position = d.optInt("position"),
    source = d.optStr("source"),
    date = d.optStr("date"),
    imageUrl = d.optStr("image_url"),
    thumbnailUrl = d.optStr("thumbnail_url"),
    extra = d.optObj("extra") ?: emptyMap(),
    perceive = d.obj("perceive")?.let { toPerceiveResult(it) },
)

private fun toLookupResult(d: JsonObject): LookupResult = LookupResult(
    lookupId = d.optLong("lookup_id"),
    query = d.str("query"),
    category = LookupCategory.fromWire(d.optStr("category")) ?: LookupCategory.WEB,
    country = d.optStr("country"),
    locale = d.optStr("locale"),
    timeFilter = LookupTimeFilter.fromWire(d.optStr("time_filter")),
    total = d.intOr("total"),
    results = d.objArr("results").map(::toLookupItem),
    perceiveTop = d.intOr("perceive_top"),
    perceiveOperationIds = d.strArr("perceive_operation_ids"),
    answerBox = d.optObj("answer_box"),
    knowledgeGraph = d.optObj("knowledge_graph"),
    credits = d.optInt("credits"),
    costCents = d.doubleOr("cost_cents"),
    warnings = d.strArr("warnings"),
)

private fun toDistillItem(d: JsonObject): DistillItem = DistillItem(
    url = d.str("url"),
    urlFinal = d.optStr("url_final"),
    status = DistillItemStatus.fromWire(d.optStr("status")) ?: DistillItemStatus.COMPLETED,
    data = d.optObj("data"),
    extractionTier = DistillExtractionTier.fromWire(d.optStr("extraction_tier")) ?: DistillExtractionTier.NONE,
    fieldsFromCss = d.intOr("fields_from_css"),
    fieldsFromLlm = d.intOr("fields_from_llm"),
    renderQuality = d.optDouble("render_quality"),
    tokens = toTokens(d.obj("tokens")),
    costCents = d.doubleOr("cost_cents"),
    error = d.optStr("error"),
    warnings = d.strArr("warnings"),
)

private fun toDistillResult(d: JsonObject): DistillResult = DistillResult(
    operationId = d.str("operation_id"),
    total = d.intOr("total"),
    completed = d.intOr("completed"),
    failed = d.intOr("failed"),
    results = d.objArr("results").map(::toDistillItem),
    totalCostCents = d.doubleOr("total_cost_cents"),
    warnings = d.strArr("warnings"),
)

private fun toIngestJob(d: JsonObject): IngestJob = IngestJob(
    jobId = d.str("job_id"),
    status = IngestStatus.fromWire(d.optStr("status")) ?: IngestStatus.QUEUED,
    mode = IngestMode.fromWire(d.optStr("mode")) ?: IngestMode.URLS,
    pagesDiscovered = d.intOr("pages_discovered"),
    pagesProcessed = d.intOr("pages_processed"),
    pagesFailed = d.intOr("pages_failed"),
    totalChunks = d.intOr("total_chunks"),
    outputUrl = d.optStr("output_url"),
    errorMessage = d.optStr("error_message"),
    webhookUrl = d.optStr("webhook_url"),
    webhookDelivered = d.boolOr("webhook_delivered"),
    createdAt = d.optStr("created_at"),
    completedAt = d.optStr("completed_at"),
    warnings = d.strArr("warnings"),
)

private fun toIngestJobSummary(d: JsonObject): IngestJobSummary = IngestJobSummary(
    jobId = d.str("job_id"),
    status = IngestStatus.fromWire(d.optStr("status")) ?: IngestStatus.QUEUED,
    mode = IngestMode.fromWire(d.optStr("mode")) ?: IngestMode.URLS,
    pagesDiscovered = d.intOr("pages_discovered"),
    pagesProcessed = d.intOr("pages_processed"),
    pagesFailed = d.intOr("pages_failed"),
    totalChunks = d.intOr("total_chunks"),
    outputUrl = d.optStr("output_url"),
    errorMessage = d.optStr("error_message"),
    webhookConfigured = d.boolOr("webhook_configured"),
    webhookDelivered = d.boolOr("webhook_delivered"),
    createdAt = d.optStr("created_at"),
    completedAt = d.optStr("completed_at"),
)

private fun toWebhookSecret(d: JsonObject): WebhookSecret = WebhookSecret(
    secret = d.str("secret"),
    signatureHeader = d.str("signature_header"),
    timestampHeader = d.str("timestamp_header"),
    signatureScheme = d.str("signature_scheme"),
    replayToleranceSeconds = d.intOr("replay_tolerance_seconds"),
    rotated = d.boolOr("rotated"),
)

private fun toWatcher(d: JsonObject): Watcher = Watcher(
    watcherId = d.str("watcher_id"),
    url = d.str("url"),
    status = WatcherStatus.fromWire(d.optStr("status")) ?: WatcherStatus.ACTIVE,
    frequencyMinutes = d.intOr("frequency_minutes"),
    diffMode = WatchDiffMode.fromWire(d.optStr("diff_mode")) ?: WatchDiffMode.AUTO,
    trackFields = d.optObj("track_fields"),
    webhookUrl = d.optStr("webhook_url"),
    notifyEmail = d.isNotFalse("notify_email"),
    consecutiveErrors = d.intOr("consecutive_errors"),
    checksCount = d.intOr("checks_count"),
    lastCheckAt = d.optStr("last_check_at"),
    nextCheckAt = d.optStr("next_check_at"),
    lastChangeAt = d.optStr("last_change_at"),
    createdAt = d.optStr("created_at"),
    updatedAt = d.optStr("updated_at"),
)

private fun toWatcherSummary(d: JsonObject): WatcherSummary = WatcherSummary(
    watcherId = d.str("watcher_id"),
    url = d.str("url"),
    status = WatcherStatus.fromWire(d.optStr("status")) ?: WatcherStatus.ACTIVE,
    frequencyMinutes = d.intOr("frequency_minutes"),
    checksCount = d.intOr("checks_count"),
    consecutiveErrors = d.intOr("consecutive_errors"),
    lastCheckAt = d.optStr("last_check_at"),
    nextCheckAt = d.optStr("next_check_at"),
    lastChangeAt = d.optStr("last_change_at"),
    createdAt = d.optStr("created_at"),
)

@Suppress("UNCHECKED_CAST")
private fun toWatcherSnapshot(d: JsonObject): WatcherSnapshot = WatcherSnapshot(
    checkedAt = d.str("checked_at"),
    hasChanges = d.boolOr("has_changes"),
    similarity = d.optDouble("similarity"),
    renderQuality = d.optDouble("render_quality"),
    changeCount = d.intOr("change_count"),
    changes = d.objArr("changes").map { it.toKotlin() as Map<String, Any?> },
)

// ----------------------------------------------------------------------
// List query strings
// ----------------------------------------------------------------------

private fun listQuery(opts: V2ListOptions): String {
    val params = mutableListOf<String>()
    opts.skip?.let { params += "skip=$it" }
    opts.limit?.let { params += "limit=$it" }
    return if (params.isEmpty()) "" else "?" + params.joinToString("&")
}
