/**
 * V2 API option and result types.
 *
 * Field names are camelCase on the SDK surface and mapped to the API's
 * snake_case wire format by [EnconvertV2]. User-data payloads (extraction
 * schemas, extracted data, tracked fields, diff changes, search extras) pass
 * through untouched as `Map<String, Any?>`.
 */

package com.enconvert

// ---------------------------------------------------------------------------
// Perceive
// ---------------------------------------------------------------------------

public enum class PerceiveOutputName(internal val wire: String) {
    MARKDOWN("markdown"),
    MARKDOWN_FIT("markdown_fit"),
    HTML_CLEANED("html_cleaned"),
    HTML_RAW("html_raw"),
    SCREENSHOT("screenshot"),
    SCREENSHOT_FULL_PAGE("screenshot_full_page"),
    PDF("pdf"),
    LINKS("links"),
    IMAGES("images"),
    STRUCTURED("structured"),
    ;

    public companion object {
        public fun fromWire(wire: String?): PerceiveOutputName? = entries.firstOrNull { it.wire == wire }
    }
}

public enum class PerceiveExtractName(internal val wire: String) {
    TABLES("tables"),
    PRICES("prices"),
    CONTACTS("contacts"),
    METADATA("metadata"),
    MAIN_CONTENT("main_content"),
    HEADINGS("headings"),
    STRUCTURED_DATA("structured_data"),
    TECHNOLOGIES("technologies"),
    ALL("all"),
    ;

    public companion object {
        public fun fromWire(wire: String?): PerceiveExtractName? = entries.firstOrNull { it.wire == wire }
    }
}

public enum class PerceiveResourceType(internal val wire: String) {
    IMAGE("image"),
    MEDIA("media"),
    FONT("font"),
    STYLESHEET("stylesheet"),
    SCRIPT("script"),
    XHR("xhr"),
    FETCH("fetch"),
    WEBSOCKET("websocket"),
    MANIFEST("manifest"),
    OTHER("other"),
    ;

    public companion object {
        public fun fromWire(wire: String?): PerceiveResourceType? = entries.firstOrNull { it.wire == wire }
    }
}

public enum class PerceiveCacheMode(internal val wire: String) {
    ENABLED("enabled"),
    BYPASS("bypass"),
    REFRESH("refresh"),
    ;

    public companion object {
        public fun fromWire(wire: String?): PerceiveCacheMode? = entries.firstOrNull { it.wire == wire }
    }
}

public enum class PerceiveStatus(internal val wire: String) {
    QUEUED("queued"),
    PROCESSING("processing"),
    COMPLETED("completed"),
    FAILED("failed"),
    ;

    public companion object {
        public fun fromWire(wire: String?): PerceiveStatus? = entries.firstOrNull { it.wire == wire }
    }
}

public data class PerceiveViewport(
    /** 320-3840, default 1920. */
    val width: Int? = null,
    /** 240-2160, default 1080. */
    val height: Int? = null,
)

/** Per-render options shared by perceive and perceiveBatch. */
public data class PerceiveOptions(
    /** Artifacts to produce. Default: [markdown, structured]. */
    val outputs: List<PerceiveOutputName>? = null,
    /** Heuristic extraction targets. Unsupported members yield warnings. */
    val extract: List<PerceiveExtractName>? = null,
    /** JSON schema for structured extraction (LLM tier, plan-gated). */
    val schema: Map<String, Any?>? = null,
    /** CSS selector (optionally "css:...") or "js:<expr>" to await. */
    val waitFor: String? = null,
    /** 0-60000, default 30000. */
    val waitTimeoutMs: Int? = null,
    /** JavaScript executed after navigation. Max 20000 chars. */
    val jsCode: String? = null,
    val viewport: PerceiveViewport? = null,
    val headers: Map<String, String>? = null,
    val cookies: List<BrowserCookie>? = null,
    /** HTTP Basic Auth (plan-gated). */
    val auth: HttpBasicAuth? = null,
    /** Not yet available server-side — currently rejected with 422. */
    val proxyUrl: String? = null,
    /** Not yet available server-side — currently rejected with 422. */
    val geolocation: Map<String, Any?>? = null,
    /** Not yet available server-side — currently rejected with 422. */
    val actionChain: List<Map<String, Any?>>? = null,
    /** Default "enabled" (1h cache). "bypass" skips, "refresh" re-renders. */
    val cacheMode: PerceiveCacheMode? = null,
    /** Only meaningful when outputs includes PDF. */
    val pdfOptions: PdfOptions? = null,
    /** Resource types the browser should not load. */
    val blockResources: List<PerceiveResourceType>? = null,
    val respectRobots: Boolean? = null,
    val mobile: Boolean? = null,
)

public enum class PerceiveBatchOutputMode(internal val wire: String) {
    MANIFEST("manifest"),
    ZIP("zip"),
    ;

    public companion object {
        public fun fromWire(wire: String?): PerceiveBatchOutputMode? = entries.firstOrNull { it.wire == wire }
    }
}

/** Options for perceiveBatch: shared render options plus the output mode. */
public data class PerceiveBatchOptions(
    val options: PerceiveOptions = PerceiveOptions(),
    /** MANIFEST (default) or ZIP (bundle all artifacts once complete). */
    val outputMode: PerceiveBatchOutputMode? = null,
)

/** A rendered output stored server-side, addressed by signed URL. */
public data class V2OutputArtifact(
    /** Pre-signed download URL (15 minutes). Re-signed on every status GET. */
    val url: String? = null,
    val objectKey: String = "",
    val sizeBytes: Long = 0,
    val contentType: String = "application/octet-stream",
    val expiresIn: Int = 900,
)

public data class V2Tokens(
    val input: Int = 0,
    val output: Int = 0,
)

public enum class PerceiveExtractionTier(internal val wire: String) {
    HEURISTIC("heuristic"),
    CSS("css"),
    LLM("llm"),
    ;

    public companion object {
        public fun fromWire(wire: String?): PerceiveExtractionTier? = entries.firstOrNull { it.wire == wire }
    }
}

public data class PerceiveResult(
    val operationId: String,
    val status: PerceiveStatus,
    val url: String,
    val urlFinal: String? = null,
    val contentHash: String? = null,
    /** 0.0-1.0 render quality score. */
    val renderQuality: Double? = null,
    val cacheHit: Boolean = false,
    /** Keyed by output name (e.g. "markdown", "screenshot_full_page"). */
    val outputs: Map<String, V2OutputArtifact> = emptyMap(),
    /** Present when extract/schema was requested. Shape is caller-defined. */
    val structured: Map<String, Any?>? = null,
    val extractionTier: PerceiveExtractionTier? = null,
    val tokens: V2Tokens = V2Tokens(),
    val costCents: Double = 0.0,
    val durationMs: Long? = null,
    val error: String? = null,
    val warnings: List<String> = emptyList(),
)

public enum class PerceiveBatchStatus(internal val wire: String) {
    QUEUED("queued"),
    PROCESSING("processing"),
    COMPLETED("completed"),
    FAILED("failed"),
    PARTIAL("partial"),
    ;

    public companion object {
        public fun fromWire(wire: String?): PerceiveBatchStatus? = entries.firstOrNull { it.wire == wire }
    }
}

public data class PerceiveBatchResult(
    val jobId: String,
    val status: PerceiveBatchStatus,
    val outputMode: PerceiveBatchOutputMode = PerceiveBatchOutputMode.MANIFEST,
    val total: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
    val pending: Int = 0,
    /** Bundle of every successful artifact (outputMode ZIP, once done). */
    val zip: V2OutputArtifact? = null,
    /** One entry per URL. Empty on the initial 202 — poll getPerceiveBatch. */
    val items: List<PerceiveResult> = emptyList(),
    val warnings: List<String> = emptyList(),
)

// ---------------------------------------------------------------------------
// Discover
// ---------------------------------------------------------------------------

public enum class DiscoverMode(internal val wire: String) {
    SITEMAP("sitemap"),
    CRAWL("crawl"),
    HYBRID("hybrid"),
    ;

    public companion object {
        public fun fromWire(wire: String?): DiscoverMode? = entries.firstOrNull { it.wire == wire }
    }
}

public data class DiscoverOptions(
    /** Default HYBRID (sitemap + HTTP crawl). */
    val mode: DiscoverMode? = null,
    /** 1-1000, default 100. */
    val maxUrls: Int? = null,
    /** 1-5, default 2. */
    val maxDepth: Int? = null,
    /** Regex allowlist (re.search semantics), max 50. */
    val includePatterns: List<String>? = null,
    /** Regex denylist, applied after includePatterns, max 50. */
    val excludePatterns: List<String>? = null,
    /** Default true. */
    val sameDomainOnly: Boolean? = null,
    val respectRobots: Boolean? = null,
)

public data class DiscoverResult(
    val url: String,
    val mode: DiscoverMode,
    val total: Int = 0,
    val urls: List<String> = emptyList(),
    val pagesCrawled: Int = 0,
    /** True when more URLs were found than maxUrls allowed. */
    val truncated: Boolean = false,
    val robotsRespected: Boolean = false,
    /** Raw counts per source before dedup, e.g. {sitemap: 42, crawl: 30}. */
    val sources: Map<String, Int> = emptyMap(),
    val warnings: List<String> = emptyList(),
)

// ---------------------------------------------------------------------------
// Lookup
// ---------------------------------------------------------------------------

public enum class LookupCategory(internal val wire: String) {
    WEB("web"),
    NEWS("news"),
    IMAGES("images"),
    SCHOLAR("scholar"),
    PATENTS("patents"),
    MAPS("maps"),
    ;

    public companion object {
        public fun fromWire(wire: String?): LookupCategory? = entries.firstOrNull { it.wire == wire }
    }
}

public enum class LookupTimeFilter(internal val wire: String) {
    HOUR("hour"),
    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    YEAR("year"),
    ;

    public companion object {
        public fun fromWire(wire: String?): LookupTimeFilter? = entries.firstOrNull { it.wire == wire }
    }
}

public data class LookupOptions(
    /** Default WEB. */
    val category: LookupCategory? = null,
    /** Google "gl" country code, e.g. "us", "in". */
    val country: String? = null,
    /** Google "hl" interface language, e.g. "en". */
    val locale: String? = null,
    val timeFilter: LookupTimeFilter? = null,
    /** 1-100, default 10. */
    val numResults: Int? = null,
    /** 1-10, default 1. */
    val page: Int? = null,
    /** Free-text location, e.g. "Austin, Texas". */
    val location: String? = null,
    /** Default true. */
    val autocorrect: Boolean? = null,
    /**
     * Auto-perceive the top-N result URLs (0-10, default 0). Each consumes
     * one perceive-quota unit and runs a full browser render.
     */
    val perceiveTop: Int? = null,
)

/** One search hit, optionally carrying its full perceive result. */
public data class LookupItem(
    val title: String? = null,
    val url: String? = null,
    val snippet: String? = null,
    val position: Int? = null,
    val source: String? = null,
    val date: String? = null,
    val imageUrl: String? = null,
    val thumbnailUrl: String? = null,
    /** Provider-specific passthrough fields. */
    val extra: Map<String, Any?> = emptyMap(),
    /** Present for the top-N results when perceiveTop > 0 and it succeeded. */
    val perceive: PerceiveResult? = null,
)

public data class LookupResult(
    /** Audit row id; null when the audit write failed (results valid). */
    val lookupId: Long? = null,
    val query: String,
    val category: LookupCategory,
    val country: String? = null,
    val locale: String? = null,
    val timeFilter: LookupTimeFilter? = null,
    val total: Int = 0,
    val results: List<LookupItem> = emptyList(),
    /** How many results were actually perceived (may be below requested). */
    val perceiveTop: Int = 0,
    val perceiveOperationIds: List<String> = emptyList(),
    val answerBox: Map<String, Any?>? = null,
    val knowledgeGraph: Map<String, Any?>? = null,
    /** Search-provider credits consumed. */
    val credits: Int? = null,
    val costCents: Double = 0.0,
    val warnings: List<String> = emptyList(),
)

// ---------------------------------------------------------------------------
// Distill
// ---------------------------------------------------------------------------

public enum class CssFieldType(internal val wire: String) {
    TEXT("text"),
    ATTRIBUTE("attribute"),
    HTML("html"),
    REGEX("regex"),
    NESTED("nested"),
    LIST("list"),
    NESTED_LIST("nested_list"),
    ;

    public companion object {
        public fun fromWire(wire: String?): CssFieldType? = entries.firstOrNull { it.wire == wire }
    }
}

/** One field of a CSS extraction schema (recursive for nested types). */
public data class CssField(
    val name: String,
    val type: CssFieldType,
    val selector: String? = null,
    /** Required when type is ATTRIBUTE. */
    val attribute: String? = null,
    /** Required when type is REGEX. Compiled server-side; ReDoS-screened. */
    val pattern: String? = null,
    val default: Any? = null,
    val transform: CssFieldTransform? = null,
    /** Required (non-empty) for NESTED / LIST / NESTED_LIST. Max depth 5. */
    val fields: List<CssField>? = null,
)

public enum class CssFieldTransform(internal val wire: String) {
    LOWERCASE("lowercase"),
    UPPERCASE("uppercase"),
    STRIP("strip"),
    ;

    public companion object {
        public fun fromWire(wire: String?): CssFieldTransform? = entries.firstOrNull { it.wire == wire }
    }
}

/** Free CSS extraction pass run before any LLM escalation. */
public data class CssSchema(
    /** Matches the repeating container; one extracted record per match. */
    val baseSelector: String,
    val fields: List<CssField>,
    val name: String? = null,
    /**
     * Top-level output-schema property the CSS records fill. Array property
     * receives the full list; scalar/object the first record. Inferred when
     * omitted and the schema has exactly one array property.
     */
    val targetField: String? = null,
)

public data class DistillDiscoverFrom(
    val url: String,
    /** Default HYBRID. */
    val mode: DiscoverMode? = null,
    /** 1-50, default 10. Cap on URLs discovered AND distilled. */
    val maxPages: Int? = null,
)

public data class DistillOptions(
    /** Explicit URLs to distill (max 50). Exactly one of urls/discoverFrom. */
    val urls: List<String>? = null,
    /** Discover a site's URLs first, then distill each. */
    val discoverFrom: DistillDiscoverFrom? = null,
    /**
     * Required output shape: a JSON-Schema object
     * ({type: "object", properties: {...}}) or a flat {field: description}
     * map. The response data matches this shape.
     */
    val schema: Map<String, Any?>,
    /** Optional free CSS pass; missing fields escalate to the LLM tier. */
    val cssSchema: CssSchema? = null,
    val waitFor: String? = null,
    /** 0-60000, default 30000. */
    val waitTimeoutMs: Int? = null,
    val headers: Map<String, String>? = null,
    val cookies: List<BrowserCookie>? = null,
    val respectRobots: Boolean? = null,
)

public enum class DistillExtractionTier(internal val wire: String) {
    CSS("css"),
    LLM("llm"),
    MIXED("mixed"),
    NONE("none"),
    ;

    public companion object {
        public fun fromWire(wire: String?): DistillExtractionTier? = entries.firstOrNull { it.wire == wire }
    }
}

public enum class DistillItemStatus(internal val wire: String) {
    COMPLETED("completed"),
    FAILED("failed"),
    ;

    public companion object {
        public fun fromWire(wire: String?): DistillItemStatus? = entries.firstOrNull { it.wire == wire }
    }
}

public data class DistillItem(
    val url: String,
    val urlFinal: String? = null,
    val status: DistillItemStatus = DistillItemStatus.COMPLETED,
    /** Extracted data matching the requested schema. */
    val data: Map<String, Any?>? = null,
    val extractionTier: DistillExtractionTier = DistillExtractionTier.NONE,
    val fieldsFromCss: Int = 0,
    val fieldsFromLlm: Int = 0,
    val renderQuality: Double? = null,
    val tokens: V2Tokens = V2Tokens(),
    val costCents: Double = 0.0,
    val error: String? = null,
    val warnings: List<String> = emptyList(),
)

public data class DistillResult(
    val operationId: String,
    val total: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
    val results: List<DistillItem> = emptyList(),
    val totalCostCents: Double = 0.0,
    val warnings: List<String> = emptyList(),
)

// ---------------------------------------------------------------------------
// Ingest
// ---------------------------------------------------------------------------

public enum class IngestMode(internal val wire: String) {
    URLS("urls"),
    SITEMAP("sitemap"),
    CRAWL("crawl"),
    FILES("files"),
    ;

    public companion object {
        public fun fromWire(wire: String?): IngestMode? = entries.firstOrNull { it.wire == wire }
    }
}

public enum class IngestStatus(internal val wire: String) {
    QUEUED("queued"),
    DISCOVERING("discovering"),
    PROCESSING("processing"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELED("canceled"),
    ;

    public companion object {
        public fun fromWire(wire: String?): IngestStatus? = entries.firstOrNull { it.wire == wire }
    }
}

public data class IngestChunkOptions(
    /** Words per chunk, 32-4000, default 512. */
    val maxWords: Int? = null,
    /** Sentences repeated between consecutive chunks, 0-10, default 1. */
    val sentenceOverlap: Int? = null,
)

public data class IngestOptions(
    /** Default URLS. */
    val mode: IngestMode? = null,
    /** Seed URL — required for SITEMAP/CRAWL, forbidden for URLS. */
    val url: String? = null,
    /** Explicit URLs (max 1000) — required for URLS, forbidden otherwise. */
    val urls: List<String>? = null,
    /** Discovery cap for sitemap/crawl, 1-1000, default 50. */
    val maxPages: Int? = null,
    /** 1-5, default 2. */
    val maxDepth: Int? = null,
    /** Default true. */
    val sameDomainOnly: Boolean? = null,
    val includePatterns: List<String>? = null,
    val excludePatterns: List<String>? = null,
    val respectRobots: Boolean? = null,
    val waitFor: String? = null,
    val waitTimeoutMs: Int? = null,
    val chunk: IngestChunkOptions? = null,
    /** Completion webhook, HMAC-signed (see getWebhookSecret). */
    val webhookUrl: String? = null,
)

/**
 * Options for `ingestFiles` (POST /v2/ingest/files). Uploaded documents are
 * converted to Markdown and chunked through the same pipeline as `ingest`.
 */
public data class IngestFilesOptions(
    /** Heading-aware chunker parameters. */
    val chunk: IngestChunkOptions? = null,
    /** Completion webhook, HMAC-signed (see getWebhookSecret). */
    val webhookUrl: String? = null,
)

public data class IngestJob(
    val jobId: String,
    val status: IngestStatus,
    val mode: IngestMode,
    val pagesDiscovered: Int = 0,
    val pagesProcessed: Int = 0,
    val pagesFailed: Int = 0,
    val totalChunks: Int = 0,
    /** Signed URL to the final JSONL, once completed. */
    val outputUrl: String? = null,
    val errorMessage: String? = null,
    val webhookUrl: String? = null,
    val webhookDelivered: Boolean = false,
    val createdAt: String? = null,
    val completedAt: String? = null,
    val warnings: List<String> = emptyList(),
)

/** Compact job row from listIngestJobs (webhookUrl replaced by a flag). */
public data class IngestJobSummary(
    val jobId: String,
    val status: IngestStatus,
    val mode: IngestMode,
    val pagesDiscovered: Int = 0,
    val pagesProcessed: Int = 0,
    val pagesFailed: Int = 0,
    val totalChunks: Int = 0,
    val outputUrl: String? = null,
    val errorMessage: String? = null,
    val webhookConfigured: Boolean = false,
    val webhookDelivered: Boolean = false,
    val createdAt: String? = null,
    val completedAt: String? = null,
)

public data class IngestJobList(
    val jobs: List<IngestJobSummary> = emptyList(),
    val skip: Int = 0,
    val limit: Int = 20,
    val hasMore: Boolean = false,
)

public data class WebhookSecret(
    val secret: String,
    /** Header carrying the HMAC signature, e.g. "X-Enconvert-Signature". */
    val signatureHeader: String,
    val timestampHeader: String,
    val signatureScheme: String,
    val replayToleranceSeconds: Int = 0,
    /** True when this response just replaced the previous secret. */
    val rotated: Boolean = false,
)

public data class WebhookRetryResult(
    val jobId: String,
    val delivered: Boolean,
    val attempts: Int,
    /** HTTP status of the last attempt; null on network error. */
    val statusCode: Int? = null,
    val detail: String,
)

// ---------------------------------------------------------------------------
// Watch
// ---------------------------------------------------------------------------

public enum class WatchDiffMode(internal val wire: String) {
    AUTO("auto"),
    TEXT("text"),
    STRUCTURED("structured"),
    TABLES("tables"),
    METADATA("metadata"),
    ;

    public companion object {
        public fun fromWire(wire: String?): WatchDiffMode? = entries.firstOrNull { it.wire == wire }
    }
}

public enum class WatcherStatus(internal val wire: String) {
    ACTIVE("active"),
    PAUSED("paused"),
    DELETED("deleted"),
    ;

    public companion object {
        public fun fromWire(wire: String?): WatcherStatus? = entries.firstOrNull { it.wire == wire }
    }
}

/** Subset of [WatcherStatus] settable via updateWatcher (deletion goes through deleteWatcher). */
public enum class WatcherUpdateStatus(internal val wire: String) {
    ACTIVE("active"),
    PAUSED("paused"),
    ;

    public companion object {
        public fun fromWire(wire: String?): WatcherUpdateStatus? = entries.firstOrNull { it.wire == wire }
    }
}

public data class WatchCreateOptions(
    /** Minutes between checks, 60-43200 (hourly floor is hard). Default 60. */
    val frequencyMinutes: Int? = null,
    /** Default AUTO (diff engine picks by content type). */
    val diffMode: WatchDiffMode? = null,
    /** Optional field/selector subset for the diff engine. */
    val trackFields: Map<String, Any?>? = null,
    /** Change-notification webhook, HMAC-signed. */
    val webhookUrl: String? = null,
    /** Email the project owner on changes. Default true. */
    val notifyEmail: Boolean? = null,
)

public data class WatcherUpdate(
    /** 60-43200. */
    val frequencyMinutes: Int? = null,
    val diffMode: WatchDiffMode? = null,
    val trackFields: Map<String, Any?>? = null,
    /** An empty string "" explicitly clears the webhook. */
    val webhookUrl: String? = null,
    val notifyEmail: Boolean? = null,
    /** ACTIVE or PAUSED. Deleting goes through deleteWatcher. */
    val status: WatcherUpdateStatus? = null,
)

public data class Watcher(
    val watcherId: String,
    val url: String,
    val status: WatcherStatus,
    val frequencyMinutes: Int = 0,
    val diffMode: WatchDiffMode,
    val trackFields: Map<String, Any?>? = null,
    val webhookUrl: String? = null,
    val notifyEmail: Boolean = true,
    val consecutiveErrors: Int = 0,
    val checksCount: Int = 0,
    val lastCheckAt: String? = null,
    val nextCheckAt: String? = null,
    val lastChangeAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** Compact watcher row from listWatchers. */
public data class WatcherSummary(
    val watcherId: String,
    val url: String,
    val status: WatcherStatus,
    val frequencyMinutes: Int = 0,
    val checksCount: Int = 0,
    val consecutiveErrors: Int = 0,
    val lastCheckAt: String? = null,
    val nextCheckAt: String? = null,
    val lastChangeAt: String? = null,
    val createdAt: String? = null,
)

public data class WatcherList(
    val watchers: List<WatcherSummary> = emptyList(),
    val skip: Int = 0,
    val limit: Int = 20,
    val hasMore: Boolean = false,
)

public data class WatcherSnapshot(
    val checkedAt: String,
    val hasChanges: Boolean,
    /** 0.0-1.0 similarity to the previous capture. */
    val similarity: Double? = null,
    val renderQuality: Double? = null,
    val changeCount: Int = 0,
    /** Diff entries. Values are untrusted page content — escape before render. */
    val changes: List<Map<String, Any?>> = emptyList(),
)

public data class WatcherSnapshotList(
    val watcherId: String,
    val snapshots: List<WatcherSnapshot> = emptyList(),
    val limit: Int = 20,
)

// ---------------------------------------------------------------------------
// Shared list options
// ---------------------------------------------------------------------------

public data class V2ListOptions(
    /** Rows to skip (default 0). */
    val skip: Int? = null,
    /** Page size, 1-100 (default 20). */
    val limit: Int? = null,
)

public data class SnapshotListOptions(
    /** Page size, 1-100 (default 20). */
    val limit: Int? = null,
)
