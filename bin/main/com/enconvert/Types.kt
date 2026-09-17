/** Enconvert SDK response and option types (V1). */

package com.enconvert

// ---------------------------------------------------------------------------
// Conversion result / job status
// ---------------------------------------------------------------------------

public data class ConversionResult(
    val presignedUrl: String,
    val objectKey: String,
    val filename: String,
    val fileSize: Long? = null,
    val conversionTimeSeconds: Double? = null,
    val jobId: String? = null,
)

public enum class JobStatusValue(internal val wire: String) {
    PROCESSING("processing"),
    SUCCESS("success"),
    FAILED("failed"),
    ;

    public companion object {
        public fun fromWire(wire: String?): JobStatusValue? = entries.firstOrNull { it.wire == wire }
    }
}

public data class JobStatus(
    val status: JobStatusValue,
    val presignedUrl: String? = null,
    val objectKey: String? = null,
    val error: String? = null,
)

// ---------------------------------------------------------------------------
// PDF options
// ---------------------------------------------------------------------------

public data class PdfMargins(
    val top: Double? = null,
    val bottom: Double? = null,
    val left: Double? = null,
    val right: Double? = null,
)

/** Header or footer block rendered on each PDF page. */
public data class PdfHeaderFooter(
    /** Text content, max 2000 characters. */
    val content: String? = null,
    /** Block height. */
    val height: Double? = null,
)

public enum class PdfOrientation(internal val wire: String) {
    PORTRAIT("portrait"),
    LANDSCAPE("landscape"),
    ;

    public companion object {
        public fun fromWire(wire: String?): PdfOrientation? = entries.firstOrNull { it.wire == wire }
    }
}

public data class PdfOptions(
    val pageSize: String? = null,
    /** Custom page width; overrides pageSize when set together with pageHeight. */
    val pageWidth: Double? = null,
    /** Custom page height; overrides pageSize when set together with pageWidth. */
    val pageHeight: Double? = null,
    val orientation: PdfOrientation? = null,
    val margins: PdfMargins? = null,
    val scale: Double? = null,
    val grayscale: Boolean? = null,
    val header: PdfHeaderFooter? = null,
    val footer: PdfHeaderFooter? = null,
)

// ---------------------------------------------------------------------------
// Browser access (auth / cookies / headers) — plan-gated
// ---------------------------------------------------------------------------

/** HTTP Basic Auth credentials for pages behind a login (plan-gated). */
public data class HttpBasicAuth(
    val username: String,
    val password: String,
)

public enum class SameSite(internal val wire: String) {
    STRICT("Strict"),
    LAX("Lax"),
    NONE("None"),
    ;

    public companion object {
        public fun fromWire(wire: String?): SameSite? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * Cookie injected into the browser context before rendering (plan-gated).
 * The API requires `name`, `value`, and either `domain` or `url`.
 * When `domain` is set without `path`, the API defaults `path` to "/".
 */
public data class BrowserCookie(
    val name: String,
    val value: String,
    val domain: String? = null,
    val url: String? = null,
    val path: String? = null,
    val expires: Double? = null,
    val httpOnly: Boolean? = null,
    val secure: Boolean? = null,
    val sameSite: SameSite? = null,
)

// ---------------------------------------------------------------------------
// File input accepted by convertImage / convertDocument
// ---------------------------------------------------------------------------

/**
 * Raw bytes with an explicit filename (and optional content type), accepted
 * by `convertImage` / `convertDocument` alongside a plain path `String` or
 * `java.nio.file.Path`, or bare `ByteArray` (filename defaults to
 * "upload.bin", content type to `application/octet-stream`).
 */
public data class FileInput(
    val data: ByteArray,
    val filename: String,
    val contentType: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileInput) return false
        return data.contentEquals(other.data) && filename == other.filename && contentType == other.contentType
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + filename.hashCode()
        result = 31 * result + (contentType?.hashCode() ?: 0)
        return result
    }
}

// ---------------------------------------------------------------------------
// URL conversions (single page)
// ---------------------------------------------------------------------------

/** Options shared by all URL-based conversions (single page and website). */
public data class UrlRenderOptions(
    val viewportWidth: Int? = null,
    val viewportHeight: Int? = null,
    val loadMedia: Boolean? = null,
    val enableScroll: Boolean? = null,
    val outputFilename: String? = null,
    /** HTTP Basic Auth for protected pages (plan-gated). */
    val auth: HttpBasicAuth? = null,
    /** Cookies injected before rendering, max 50 (plan-gated). */
    val cookies: List<BrowserCookie>? = null,
    /** Extra request headers, max 20; hop-by-hop headers rejected (plan-gated). */
    val headers: Map<String, String>? = null,
)

public data class UrlToPdfOptions(
    val render: UrlRenderOptions = UrlRenderOptions(),
    val saveTo: String? = null,
    val singlePage: Boolean = true,
    val pdfOptions: PdfOptions? = null,
)

public data class UrlToScreenshotOptions(
    val render: UrlRenderOptions = UrlRenderOptions(),
    val saveTo: String? = null,
)

public data class UrlToMarkdownOptions(
    val render: UrlRenderOptions = UrlRenderOptions(),
    val saveTo: String? = null,
)

public data class ConvertImageOptions(
    val outputFormat: String,
    val saveTo: String? = null,
    val outputFilename: String? = null,
)

public data class ConvertDocumentOptions(
    /** Defaults to "pdf" when not set. */
    val outputFormat: String? = null,
    val saveTo: String? = null,
    val outputFilename: String? = null,
    val pdfOptions: PdfOptions? = null,
)

/** Options for `convertToMarkdown` (anything-to-markdown). */
public data class ConvertToMarkdownOptions(
    val saveTo: String? = null,
    val outputFilename: String? = null,
)

/** Options for `convertToPdf` (anything-to-pdf). */
public data class ConvertToPdfOptions(
    val saveTo: String? = null,
    val outputFilename: String? = null,
    /** Only `grayscale` is honored by the anything-to-pdf endpoint. */
    val pdfOptions: PdfOptions? = null,
)

// ---------------------------------------------------------------------------
// Website conversions (async batch, whole-site crawl)
// ---------------------------------------------------------------------------

/**
 * URL discovery strategy for website conversions.
 * - AUTO: highest mode the plan allows (default)
 * - SITEMAP: sitemap.xml only (Starter and above)
 * - FULL: sitemap plus BFS crawl (Pro/Business)
 */
public enum class CrawlMode(internal val wire: String) {
    AUTO("auto"),
    SITEMAP("sitemap"),
    FULL("full"),
    ;

    public companion object {
        public fun fromWire(wire: String?): CrawlMode? = entries.firstOrNull { it.wire == wire }
    }
}

/** Options shared by convertWebsiteToPdf / convertWebsiteToScreenshot. */
public data class WebsiteConversionOptions(
    val render: UrlRenderOptions = UrlRenderOptions(),
    val crawlMode: CrawlMode? = null,
    /** Only crawl URLs matching these patterns (full crawl mode). */
    val includePatterns: List<String>? = null,
    /** Skip URLs matching these patterns (full crawl mode). */
    val excludePatterns: List<String>? = null,
    /** Email notified on completion. Defaults to the project owner's email. */
    val notificationEmail: String? = null,
    /** Webhook POSTed when the batch finishes (plan-gated). */
    val callbackUrl: String? = null,
)

public data class WebsiteToPdfOptions(
    val website: WebsiteConversionOptions = WebsiteConversionOptions(),
    val singlePage: Boolean? = null,
    val pdfOptions: PdfOptions? = null,
)

public data class WebsiteToScreenshotOptions(
    val website: WebsiteConversionOptions = WebsiteConversionOptions(),
)

/** 202 response from an async batch submission (website conversions). */
public data class BatchSubmission(
    val batchId: String,
    /** Always "processing" on submission. */
    val status: String,
    /** Number of pages queued for conversion. */
    val urlCount: Int,
    /** Total URLs found during discovery (before plan limits applied). */
    val totalDiscovered: Int? = null,
    /** How URLs were discovered: "sitemap" or "full_crawl". */
    val discoveryMethod: String? = null,
    /** Output packaging, "zip" for website conversions. */
    val outputFormat: String? = null,
)

public enum class BatchStatusValue(internal val wire: String) {
    PROCESSING("processing"),
    COMPLETED("completed"),
    PARTIAL("partial"),
    FAILED("failed"),
    ;

    public companion object {
        public fun fromWire(wire: String?): BatchStatusValue? = entries.firstOrNull { it.wire == wire }
    }
}

public enum class BatchOutputMode(internal val wire: String) {
    ZIP("zip"),
    INDIVIDUAL("individual"),
    ;

    public companion object {
        public fun fromWire(wire: String?): BatchOutputMode? = entries.firstOrNull { it.wire == wire }
    }
}

/** Per-URL entry in a batch status response. */
public data class BatchItem(
    val sourceUrl: String,
    /** Raw activity status: "In Progress", "Success", or "Failed". */
    val status: String,
    val downloadUrl: String? = null,
    val outputFileSize: Long? = null,
    val duration: String? = null,
)

public data class BatchStatus(
    val batchId: String,
    val status: BatchStatusValue,
    val total: Int,
    val completed: Int,
    val failed: Int,
    val inProgress: Int,
    val outputMode: BatchOutputMode,
    /** Presigned URL of the bundled ZIP when outputMode is ZIP. */
    val zipDownloadUrl: String? = null,
    val items: List<BatchItem> = emptyList(),
)

public data class WaitForBatchOptions(
    /** Poll interval in milliseconds. Defaults to 5_000. */
    val intervalMs: Long? = null,
    /** Give up after this many milliseconds. Defaults to 1_800_000 (30 minutes). */
    val timeoutMs: Long? = null,
    /** Save the batch ZIP to this local path once available. */
    val saveTo: String? = null,
)
