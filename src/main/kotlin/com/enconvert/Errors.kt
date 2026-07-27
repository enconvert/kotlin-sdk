/** Enconvert SDK exceptions. */

package com.enconvert

/** Base class for every exception raised by this SDK. */
public open class EnconvertException(message: String) : Exception(message)

/**
 * An HTTP-level failure returned by the API. [statusCode] is the raw HTTP
 * status code; [message] carries the extracted server error message and
 * renders as `"[<statusCode>] <message>"`.
 */
public open class ApiException(
    public val statusCode: Int,
    message: String,
) : EnconvertException("[$statusCode] $message")

/** HTTP 401/403 — invalid or missing API key. */
public class AuthenticationException(
    message: String = "Invalid or missing API key",
) : ApiException(401, message)

/** HTTP 429 — too many requests. */
public class RateLimitException(
    message: String = "Rate limit exceeded",
) : ApiException(429, message)

/**
 * HTTP 402 — plan or quota gate. V2 endpoints raise this when the feature is
 * not enabled on the plan or the monthly quota is exhausted.
 */
public class QuotaException(
    message: String = "Plan feature not enabled or quota exhausted",
) : ApiException(402, message)
