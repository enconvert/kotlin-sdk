/**
 * Format tables mirroring the gateway's CONVERTER_MAP (api/v1/convert.py).
 *
 * [IMPLEMENTED_CONVERSIONS] is the client-side gate: the gateway returns 503
 * for any `{input}-to-{output}` endpoint not in its CONVERTER_MAP, so we
 * reject unsupported pairs here with a useful message instead of paying a
 * network round-trip for a guaranteed failure.
 */

package com.enconvert

/** The 43 implemented `{input}-to-{output}` conversion endpoints. */
public val IMPLEMENTED_CONVERSIONS: Set<String> = setOf(
    // Structured text (13)
    "json-to-xml",
    "xml-to-json",
    "json-to-yaml",
    "yaml-to-json",
    "csv-to-json",
    "json-to-csv",
    "json-to-toml",
    "toml-to-json",
    "csv-to-xml",
    "xml-to-csv",
    "markdown-to-html",
    "markdown-to-pdf",
    "html-to-pdf",
    // Documents (9) — EPUB→PDF now flows through anything-to-pdf, not a dedicated pair.
    "doc-to-pdf",
    "excel-to-pdf",
    "ppt-to-pdf",
    "odt-to-pdf",
    "ods-to-pdf",
    "odp-to-pdf",
    "ots-to-pdf",
    "pages-to-pdf",
    "numbers-to-pdf",
    // Images (21)
    "jpeg-to-png",
    "png-to-jpeg",
    "jpeg-to-svg",
    "svg-to-jpeg",
    "jpeg-to-heic",
    "heic-to-jpeg",
    "jpeg-to-webp",
    "webp-to-jpeg",
    "png-to-svg",
    "svg-to-png",
    "png-to-heic",
    "heic-to-png",
    "png-to-webp",
    "webp-to-png",
    "svg-to-heic",
    "heic-to-svg",
    "svg-to-webp",
    "webp-to-svg",
    "heic-to-webp",
    "webp-to-heic",
    "pdf-to-jpeg",
)

/** Extension -> API format name (input side), for image conversions. */
public val IMAGE_FORMATS: Map<String, String> = mapOf(
    ".jpg" to "jpeg",
    ".jpeg" to "jpeg",
    ".png" to "png",
    ".svg" to "svg",
    ".heic" to "heic",
    ".webp" to "webp",
    // PDF is an image input solely for pdf-to-jpeg (rasterization).
    ".pdf" to "pdf",
)

/** Extension -> API format name (input side), for document conversions. */
public val DOCUMENT_FORMATS: Map<String, String> = mapOf(
    ".doc" to "doc",
    ".docx" to "doc",
    ".xls" to "excel",
    ".xlsx" to "excel",
    ".ppt" to "ppt",
    ".pptx" to "ppt",
    ".html" to "html",
    ".htm" to "html",
    ".odt" to "odt",
    ".ods" to "ods",
    ".odp" to "odp",
    ".ots" to "ots",
    ".pages" to "pages",
    ".numbers" to "numbers",
    // .epub has no dedicated document pair — use convertToPdf / convertToMarkdown.
    ".md" to "markdown",
    ".markdown" to "markdown",
    ".csv" to "csv",
    ".json" to "json",
    ".xml" to "xml",
    ".yaml" to "yaml",
    ".yml" to "yaml",
    ".toml" to "toml",
)

/** Extension -> MIME type, used for multipart uploads and raw-bytes fallback. */
public val MIME_BY_EXT: Map<String, String> = mapOf(
    ".jpg" to "image/jpeg",
    ".jpeg" to "image/jpeg",
    ".png" to "image/png",
    ".svg" to "image/svg+xml",
    ".heic" to "image/heic",
    ".webp" to "image/webp",
    ".pdf" to "application/pdf",
    ".doc" to "application/msword",
    ".docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    ".xls" to "application/vnd.ms-excel",
    ".xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    ".ppt" to "application/vnd.ms-powerpoint",
    ".pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    ".html" to "text/html",
    ".htm" to "text/html",
    ".odt" to "application/vnd.oasis.opendocument.text",
    ".ods" to "application/vnd.oasis.opendocument.spreadsheet",
    ".odp" to "application/vnd.oasis.opendocument.presentation",
    ".epub" to "application/epub+zip",
    ".md" to "text/markdown",
    ".markdown" to "text/markdown",
    ".csv" to "text/csv",
    ".json" to "application/json",
    ".xml" to "application/xml",
    ".yaml" to "application/x-yaml",
    ".yml" to "application/x-yaml",
    ".toml" to "application/toml",
)

// Common aliases users pass that differ from the API's canonical format names.
private val OUTPUT_FORMAT_ALIASES: Map<String, String> = mapOf(
    "jpg" to "jpeg",
    "yml" to "yaml",
    "htm" to "html",
    "md" to "markdown",
)

/** Return the lowercase extension of [name] (including the leading dot), or "" if none. */
public fun extOf(name: String): String {
    val i = name.lastIndexOf('.')
    return if (i == -1) "" else name.substring(i).lowercase()
}

/** Resolve a filename's MIME type, or `application/octet-stream` if unknown. */
public fun mimeFor(name: String): String = MIME_BY_EXT[extOf(name)] ?: "application/octet-stream"

/** Map a filename's extension to its API input format, or throw. */
public fun resolveInputFormat(name: String, map: Map<String, String>): String {
    val ext = extOf(name)
    val fmt = map[ext]
    if (fmt == null) {
        val supported = map.keys.toSortedSet().joinToString(", ")
        throw EnconvertException("Unsupported file extension '$ext'. Supported: $supported")
    }
    return fmt
}

/** Lowercase, strip a leading dot, and resolve aliases (jpg, yml, htm, md). */
public fun normalizeOutputFormat(fmt: String): String {
    val f = fmt.lowercase().removePrefix(".")
    return OUTPUT_FORMAT_ALIASES[f] ?: f
}

/** List the output formats the API implements for a given input format. */
public fun validOutputsFor(inputFormat: String): List<String> {
    val prefix = "$inputFormat-to-"
    return IMPLEMENTED_CONVERSIONS
        .filter { it.startsWith(prefix) }
        .map { it.substring(prefix.length) }
        .sorted()
}

/**
 * Assert `{input}-to-{output}` is an implemented endpoint and return its
 * name. Throws with the list of valid outputs for that input otherwise.
 */
public fun assertConversionImplemented(inputFormat: String, outputFormat: String): String {
    val endpoint = "$inputFormat-to-$outputFormat"
    if (endpoint !in IMPLEMENTED_CONVERSIONS) {
        val outputs = validOutputsFor(inputFormat)
        val hint = if (outputs.isNotEmpty()) {
            "Supported outputs for '$inputFormat': ${outputs.joinToString(", ")}"
        } else {
            "No conversions are available for input format '$inputFormat'"
        }
        throw EnconvertException("Conversion '$inputFormat' to '$outputFormat' is not supported. $hint.")
    }
    return endpoint
}
