# Enconvert Kotlin SDK

Honest eyes for your AI agent — the Kotlin / JVM SDK for [Enconvert](https://enconvert.com). Requires JDK 17+.

Read any web page or file into clean Markdown, JSON, or screenshots, and get a `renderQuality` score (0.0–1.0) on **every** read — so a blocked, challenge, or empty-SPA page comes back flagged with a low score and warnings, never mistaken for real content. Perceive, discover, look up, distill, ingest, and watch the web; convert 40+ file and document formats through the same key.

> Wiring an agent (Claude, Cursor, Windsurf, n8n, …)? The [MCP server](https://enconvert.com/mcp) is the native path — `npx @enconvert/mcp setup`. This SDK is the programmatic REST path for everything else.

## Install

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.enconvert:enconvert-kotlin:0.0.1")
}
```

### Maven

```xml
<dependency>
  <groupId>com.enconvert</groupId>
  <artifactId>enconvert-kotlin</artifactId>
  <version>0.0.1</version>
</dependency>
```

## Quick Start

```kotlin
import com.enconvert.Enconvert
import com.enconvert.PerceiveOptions
import com.enconvert.PerceiveOutputName

val client = Enconvert(apiKey = "sk_...")

// Read a page the way your agent should — with a quality score attached.
val op = client.v2.perceive(
    "https://example.com",
    PerceiveOptions(outputs = listOf(PerceiveOutputName.MARKDOWN, PerceiveOutputName.STRUCTURED)),
)
println("${op.outputs["markdown"]?.url} ${op.renderQuality}") // e.g. 0.93
```

---

# V2 — agent-ready data (`client.v2`)

The V2 namespace turns web pages into agent-ready data: render, search, extract, ingest, and monitor. All V2 endpoints require a **private API key** and are plan-gated — a disabled feature or exhausted monthly quota throws `QuotaException` (HTTP 402).

Every render carries `renderQuality` (0.0–1.0). A low score means the page didn't render cleanly (challenge page, cookie wall, empty shell); the content is still returned, flagged, so a bad read never quietly enters your agent's context.

### Perceive — render a URL into artifacts

```kotlin
import com.enconvert.PerceiveBatchOptions
import com.enconvert.PerceiveExtractName
import com.enconvert.PerceiveOptions
import com.enconvert.PerceiveOutputName

val op = client.v2.perceive(
    "https://example.com",
    PerceiveOptions(
        outputs = listOf(PerceiveOutputName.MARKDOWN, PerceiveOutputName.SCREENSHOT, PerceiveOutputName.STRUCTURED),
        extract = listOf(PerceiveExtractName.TABLES, PerceiveExtractName.METADATA),
    ),
)
println(op.renderQuality)        // honesty score, 0.0-1.0
println(op.outputs["markdown"]?.url) // 15-min signed URL
println(op.structured)

// Re-sign artifact URLs later:
val again = client.v2.getPerceiveOperation(op.operationId)

// Batch (<=1000 URLs; small batches run inline, larger return "queued" — poll):
val batch = client.v2.perceiveBatch(
    listOf("https://a.com", "https://b.com"),
    PerceiveBatchOptions(
        options = PerceiveOptions(outputs = listOf(PerceiveOutputName.MARKDOWN)),
        outputMode = com.enconvert.PerceiveBatchOutputMode.ZIP,
    ),
)
val done = client.v2.getPerceiveBatch(batch.jobId)
```

### Discover — enumerate a site's URLs (no rendering)

```kotlin
import com.enconvert.DiscoverMode
import com.enconvert.DiscoverOptions

val found = client.v2.discover(
    "https://example.com",
    DiscoverOptions(
        mode = DiscoverMode.HYBRID, // SITEMAP | CRAWL | HYBRID
        maxUrls = 200,
        excludePatterns = listOf("/tag/"),
    ),
)
println("${found.total} ${found.urls}")
```

### Lookup — web search with optional auto-perceive

```kotlin
import com.enconvert.LookupCategory
import com.enconvert.LookupOptions

val search = client.v2.lookup(
    "best static site generators",
    LookupOptions(
        category = LookupCategory.WEB, // WEB | NEWS | IMAGES | SCHOLAR | PATENTS | MAPS
        numResults = 10,
        perceiveTop = 3, // auto-render top 3 results (uses perceive quota)
    ),
)
for (hit in search.results) println("${hit.title} ${hit.url} ${hit.perceive?.renderQuality}")
```

### Distill — schema-driven structured extraction

```kotlin
import com.enconvert.CssField
import com.enconvert.CssFieldType
import com.enconvert.CssSchema
import com.enconvert.DistillOptions

val extraction = client.v2.distill(
    DistillOptions(
        urls = listOf("https://example.com/pricing"),
        schema = mapOf("plans" to "list of plan names with monthly prices"),
        cssSchema = CssSchema( // optional free CSS pass before the LLM tier
            baseSelector = ".plan-card",
            fields = listOf(
                CssField(name = "name", type = CssFieldType.TEXT, selector = "h3"),
                CssField(name = "price", type = CssFieldType.TEXT, selector = ".price"),
            ),
        ),
    ),
)
println("${extraction.results[0].data} ${extraction.results[0].extractionTier}")

// Or discover-then-distill:
client.v2.distill(
    DistillOptions(
        discoverFrom = com.enconvert.DistillDiscoverFrom(url = "https://example.com", mode = DiscoverMode.SITEMAP, maxPages = 10),
        schema = mapOf("title" to "page title", "summary" to "one-line summary"),
    ),
)
```

### Ingest — site or files to RAG-ready JSONL (always async)

Turn a whole site — or a set of uploaded documents — into chunked, RAG-ready JSONL through one pipeline.

```kotlin
import com.enconvert.FileInput
import com.enconvert.IngestChunkOptions
import com.enconvert.IngestFilesOptions
import com.enconvert.IngestMode
import com.enconvert.IngestOptions
import com.enconvert.IngestStatus
import java.nio.file.Files
import java.nio.file.Path

// From a site:
val job = client.v2.ingest(
    IngestOptions(
        mode = IngestMode.SITEMAP,
        url = "https://docs.example.com",
        maxPages = 100,
        chunk = IngestChunkOptions(maxWords = 512, sentenceOverlap = 1),
        webhookUrl = "https://my.app/hooks/enconvert",
    ),
)

// Or from uploaded files (PDF, DOCX, PPTX, XLSX, CSV, HTML, EPUB, TXT/MD, legacy/ODF office):
val handbook = Path.of("handbook.pdf")
val notes = Path.of("notes.docx")
val fileJob = client.v2.ingestFiles(
    listOf(
        FileInput(data = Files.readAllBytes(handbook), filename = handbook.fileName?.toString() ?: "handbook.pdf"),
        FileInput(data = Files.readAllBytes(notes), filename = notes.fileName?.toString() ?: "notes.docx"),
    ),
    IngestFilesOptions(chunk = IngestChunkOptions(maxWords = 512, sentenceOverlap = 1)),
)

val status = client.v2.getIngestJob(job.jobId) // poll
if (status.status == IngestStatus.COMPLETED) println(status.outputUrl) // JSONL

client.v2.listIngestJobs(com.enconvert.V2ListOptions(limit = 20))
client.v2.cancelIngestJob(job.jobId) // idempotent

// Webhook signing (HMAC):
val secret = client.v2.getWebhookSecret()
println("${secret.secret} ${secret.signatureHeader}")
client.v2.rotateWebhookSecret()          // invalidates old secret
client.v2.retryIngestWebhook(job.jobId)  // re-deliver
```

### Watch — recurring change monitoring

```kotlin
import com.enconvert.WatchCreateOptions
import com.enconvert.WatchDiffMode
import com.enconvert.WatcherUpdate
import com.enconvert.WatcherUpdateStatus

val watcher = client.v2.createWatcher(
    "https://example.com/pricing",
    WatchCreateOptions(
        frequencyMinutes = 60,       // hourly floor
        diffMode = WatchDiffMode.AUTO,
        webhookUrl = "https://my.app/hooks/changes",
        notifyEmail = true,
    ),
)

client.v2.listWatchers()
client.v2.getWatcher(watcher.watcherId)
client.v2.getWatcherSnapshots(watcher.watcherId, com.enconvert.SnapshotListOptions(limit = 10))
client.v2.updateWatcher(watcher.watcherId, WatcherUpdate(status = WatcherUpdateStatus.PAUSED))
client.v2.updateWatcher(watcher.watcherId, WatcherUpdate(webhookUrl = "")) // clears webhook
client.v2.deleteWatcher(watcher.watcherId) // soft-delete, idempotent
```

### V2 error handling

```kotlin
import com.enconvert.QuotaException

try {
    client.v2.ingest(com.enconvert.IngestOptions(urls = listOf("https://example.com")))
} catch (e: QuotaException) {
    System.err.println("Upgrade plan or wait for quota reset")
}
```

---

# File conversion

The same key also converts 40+ formats. Two "anything → X" endpoints auto-detect the input; the format-specific methods below give you a validated, typed path.

### Anything to Markdown / PDF

```kotlin
import com.enconvert.ConvertToMarkdownOptions
import com.enconvert.ConvertToPdfOptions
import com.enconvert.PdfOptions

// Any document → clean Markdown (a RAG-ingestion building block):
client.convertToMarkdown("report.docx", ConvertToMarkdownOptions(saveTo = "report.md"))
// PDF, DOCX, PPTX, XLSX, CSV, HTML, EPUB, TXT/MD, and legacy/ODF office. (Images not supported.)

// Almost anything → PDF:
client.convertToPdf("slides.pptx", ConvertToPdfOptions(saveTo = "slides.pdf"))
// office/ODF/Pages/Numbers/RTF/CSV, HTML, Markdown, text, images, SVG, EPUB, or a PDF passthrough.
// Only pdfOptions.grayscale is honored on this endpoint:
client.convertToPdf("scan.pdf", ConvertToPdfOptions(pdfOptions = PdfOptions(grayscale = true), saveTo = "gray.pdf"))
```

`convertToMarkdown` / `convertToPdf` accept a path `String`, a `java.nio.file.Path`, a bare `ByteArray`, or a `FileInput(data, filename, contentType?)` — same overloads as `convertImage` / `convertDocument` below.

### Image conversion

```kotlin
import com.enconvert.ConvertImageOptions

val result = client.convertImage(
    "photo.heic",
    ConvertImageOptions(outputFormat = "webp", saveTo = "photo.webp"),
)
```

Any pair among `jpeg`, `png`, `svg`, `heic`, `webp` — plus PDF rasterization (`pdf` → `jpeg`). Unsupported pairs throw before any request is made:

```kotlin
import com.enconvert.validOutputsFor

validOutputsFor("json") // [csv, toml, xml, yaml]
validOutputsFor("pdf")  // [jpeg]
```

`convertImage` / `convertDocument` accept a path `String`, a `java.nio.file.Path`, a bare `ByteArray` (filename defaults to `upload.bin`), or a `FileInput(data, filename, contentType?)` for raw bytes with an explicit filename.

### Document & data conversion

```kotlin
import com.enconvert.ConvertDocumentOptions

client.convertDocument("report.docx", ConvertDocumentOptions(saveTo = "report.pdf"))
client.convertDocument("data.json", ConvertDocumentOptions(outputFormat = "yaml", saveTo = "data.yaml"))
client.convertDocument("notes.md", ConvertDocumentOptions(outputFormat = "html", saveTo = "notes.html"))
```

Supported inputs: `doc`/`docx`, `xls`/`xlsx`, `ppt`/`pptx`, `odt`, `ods`, `odp`, `ots`, `pages`, `numbers`, `html`, `markdown`, `csv`, `json`, `xml`, `yaml`, `toml`. (EPUB → use `convertToPdf` / `convertToMarkdown`.)

| Input | Outputs |
|-------|---------|
| json | csv, toml, xml, yaml |
| xml | csv, json |
| yaml | json |
| csv | json, xml |
| toml | json |
| markdown | html, pdf |
| html | pdf |
| doc, excel, ppt, odt, ods, odp, ots, pages, numbers | pdf |
| jpeg, png, svg, heic, webp | each other (all 20 pairs) |
| pdf | jpeg |

### URL to PDF / Screenshot / Markdown

```kotlin
import com.enconvert.UrlToMarkdownOptions
import com.enconvert.UrlToPdfOptions
import com.enconvert.UrlToScreenshotOptions
import com.enconvert.UrlRenderOptions

client.convertUrlToPdf("https://example.com", UrlToPdfOptions(saveTo = "page.pdf"))
client.convertUrlToScreenshot(
    "https://example.com",
    UrlToScreenshotOptions(render = UrlRenderOptions(viewportWidth = 1440), saveTo = "shot.png"),
)
client.convertUrlToMarkdown("https://example.com/article", UrlToMarkdownOptions(saveTo = "article.md"))
```

### Website to PDF / Screenshot (whole-site batch)

Discover every page of a website (sitemap, or full crawl on higher plans), convert each in the background, and receive a single ZIP. Requires a private API key with crawl access.

```kotlin
import com.enconvert.CrawlMode
import com.enconvert.WaitForBatchOptions
import com.enconvert.WebsiteConversionOptions
import com.enconvert.WebsiteToPdfOptions

val batch = client.convertWebsiteToPdf(
    "https://example.com",
    WebsiteToPdfOptions(website = WebsiteConversionOptions(crawlMode = CrawlMode.SITEMAP)),
)
val status = client.waitForBatch(batch.batchId, WaitForBatchOptions(saveTo = "site.zip"))
println("${status.completed} of ${status.total} pages converted")
```

`convertWebsiteToScreenshot` works the same way and produces a ZIP of PNGs.

### PDF options & authenticated pages

```kotlin
import com.enconvert.HttpBasicAuth
import com.enconvert.PdfMargins
import com.enconvert.PdfOptions
import com.enconvert.PdfOrientation
import com.enconvert.UrlRenderOptions
import com.enconvert.UrlToPdfOptions

client.convertUrlToPdf(
    "https://internal.example.com/report",
    UrlToPdfOptions(
        render = UrlRenderOptions(auth = HttpBasicAuth(username = "user", password = "pass")), // or cookies / headers, plan-gated
        pdfOptions = PdfOptions(pageSize = "A4", orientation = PdfOrientation.LANDSCAPE, margins = PdfMargins(top = 10.0, bottom = 10.0)),
        saveTo = "report.pdf",
    ),
)
```

Do not combine `auth` with an `Authorization` header — the API rejects the conflict.

### Job status (async polling)

```kotlin
import com.enconvert.JobStatusValue

val status = client.getJobStatus("job_abc123")
if (status.status == JobStatusValue.SUCCESS) println(status.presignedUrl)
```

---

## Error Handling

```kotlin
import com.enconvert.ApiException
import com.enconvert.AuthenticationException
import com.enconvert.QuotaException
import com.enconvert.RateLimitException

try {
    client.v2.perceive("https://example.com")
} catch (e: AuthenticationException) {
    System.err.println("Invalid API key")
} catch (e: QuotaException) {
    System.err.println("Plan feature off or quota exhausted")
} catch (e: RateLimitException) {
    System.err.println("Too many requests — slow down")
} catch (e: ApiException) {
    System.err.println("API error [${e.statusCode}]: ${e.message}")
}
```

## Configuration

```kotlin
val client = Enconvert(
    apiKey = "sk_...",
    timeout = 300_000, // ms, default
    baseUrl = "https://api.enconvert.com", // default
)
```

## Get an API Key

Sign up at [enconvert.com](https://enconvert.com). Free tier: 100 ops/month, no credit card.

## License

MIT
