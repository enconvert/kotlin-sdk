/**
 * JSON helpers shared by the V1 client and the V2 namespace.
 *
 * Wire bodies are assembled with [buildJsonObject] plus the `putIfNotNull`
 * overloads below (mirrors the Node SDK's "only set fields are serialized"
 * behavior). Responses are read back with the small [JsonObject] accessor
 * functions (`str`, `optStr`, `intOr`, ...), each of which guards for a
 * missing/wrong-typed field exactly like the Node SDK's `str`/`optStr`/`num`
 * helpers, since the API omits null fields (`response_model_exclude_none`).
 *
 * User-data payloads (extraction schemas, extracted data, tracked fields,
 * diff changes, search "extra" fields, ...) are untyped on the wire, so they
 * are represented as `Map<String, Any?>` on the Kotlin side and converted
 * losslessly to/from [JsonElement] by [anyToJsonElement] / [JsonElement.toKotlin].
 */

package com.enconvert

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

// ---------------------------------------------------------------------------
// Arbitrary user-data <-> JsonElement
// ---------------------------------------------------------------------------

/** Convert an arbitrary Kotlin value (as produced by user code) into a [JsonElement]. */
@Suppress("UNCHECKED_CAST")
public fun anyToJsonElement(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Int -> JsonPrimitive(value)
    is Long -> JsonPrimitive(value)
    is Double -> JsonPrimitive(value)
    is Float -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value.toDouble())
    is Map<*, *> -> JsonObject((value as Map<String, Any?>).mapValues { anyToJsonElement(it.value) })
    is List<*> -> JsonArray(value.map { anyToJsonElement(it) })
    is Array<*> -> JsonArray(value.map { anyToJsonElement(it) })
    else -> throw EnconvertException("Unsupported value type for JSON encoding: ${value::class}")
}

/** Convert a [JsonElement] into plain Kotlin values (`Map`, `List`, `String`, `Long`, `Double`, `Boolean`, `null`). */
public fun JsonElement.toKotlin(): Any? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> when {
        isString -> content
        booleanOrNull != null -> booleanOrNull
        longOrNull != null -> longOrNull
        doubleOrNull != null -> doubleOrNull
        else -> content
    }
    is JsonObject -> entries.associate { (k, v) -> k to v.toKotlin() }
    is JsonArray -> map { it.toKotlin() }
}

/** Convert a `Map<String, Any?>` payload into a [JsonObject]. */
public fun Map<String, Any?>.toJsonObject(): JsonObject = JsonObject(mapValues { anyToJsonElement(it.value) })

// ---------------------------------------------------------------------------
// JsonObjectBuilder "only if set" helpers
// ---------------------------------------------------------------------------

public fun JsonObjectBuilder.putIfNotNull(key: String, value: String?) {
    if (value != null) put(key, value)
}

public fun JsonObjectBuilder.putIfNotNull(key: String, value: Int?) {
    if (value != null) put(key, value)
}

public fun JsonObjectBuilder.putIfNotNull(key: String, value: Long?) {
    if (value != null) put(key, value)
}

public fun JsonObjectBuilder.putIfNotNull(key: String, value: Double?) {
    if (value != null) put(key, value)
}

public fun JsonObjectBuilder.putIfNotNull(key: String, value: Boolean?) {
    if (value != null) put(key, value)
}

public fun JsonObjectBuilder.putIfNotNull(key: String, value: JsonElement?) {
    if (value != null) put(key, value)
}

public fun JsonObjectBuilder.putIfNotNull(key: String, value: List<String>?) {
    if (value != null) put(key, JsonArray(value.map { JsonPrimitive(it) }))
}

public fun JsonObjectBuilder.putIfNotNullMap(key: String, value: Map<String, Any?>?) {
    if (value != null) put(key, value.toJsonObject())
}

public fun JsonObjectBuilder.putIfNotNullStringMap(key: String, value: Map<String, String>?) {
    if (value != null) put(key, JsonObject(value.mapValues { JsonPrimitive(it.value) }))
}

// ---------------------------------------------------------------------------
// JsonObject readers (response mapping) — every accessor guards for a
// missing or wrong-typed field, since the API omits null fields.
// ---------------------------------------------------------------------------

public fun JsonObject.str(key: String, fallback: String = ""): String {
    val e = this[key]
    return if (e is JsonPrimitive && e.isString) e.content else fallback
}

public fun JsonObject.optStr(key: String): String? {
    val e = this[key]
    return if (e is JsonPrimitive && e.isString) e.content else null
}

public fun JsonObject.intOr(key: String, fallback: Int = 0): Int =
    (this[key] as? JsonPrimitive)?.intOrNull ?: fallback

public fun JsonObject.optInt(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

public fun JsonObject.longOr(key: String, fallback: Long = 0L): Long =
    (this[key] as? JsonPrimitive)?.longOrNull ?: fallback

public fun JsonObject.optLong(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

public fun JsonObject.doubleOr(key: String, fallback: Double = 0.0): Double =
    (this[key] as? JsonPrimitive)?.doubleOrNull ?: fallback

public fun JsonObject.optDouble(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

public fun JsonObject.boolOr(key: String, fallback: Boolean = false): Boolean =
    (this[key] as? JsonPrimitive)?.booleanOrNull ?: fallback

/** False only when the field is present and literally `false` (mirrors `d.x !== false`). */
public fun JsonObject.isNotFalse(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull != false

public fun JsonObject.strArr(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content } ?: emptyList()

@Suppress("UNCHECKED_CAST")
public fun JsonObject.optObj(key: String): Map<String, Any?>? =
    (this[key] as? JsonObject)?.toKotlin() as? Map<String, Any?>

public fun JsonObject.objArr(key: String): List<JsonObject> =
    (this[key] as? JsonArray)?.filterIsInstance<JsonObject>() ?: emptyList()

public fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
