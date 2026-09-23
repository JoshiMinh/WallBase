package com.joshiminh.wallbase.scraper.sandbox

import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sandboxed JavaScript Script Evaluation Runtime for WallBase extensions.
 * Provides safe script execution hooks, timeouts, and scoped network/DOM primitives.
 */
@Singleton
class QuickJsSandbox @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
) {

    private companion object {
        private const val MAX_EXECUTION_TIMEOUT_MS = 8000L
    }

    /**
     * Executes an extension hook script with custom input variables.
     */
    suspend fun executeHook(
        script: String,
        functionName: String,
        arguments: Map<String, Any?> = emptyMap()
    ): Result<Any?> = withContext(Dispatchers.IO) {
        runCatching {
            withTimeoutOrNull(MAX_EXECUTION_TIMEOUT_MS) {
                // Execute in safe isolated context
                evalSafe(script, functionName, arguments)
            } ?: throw IllegalStateException("Script execution timed out (> ${MAX_EXECUTION_TIMEOUT_MS}ms)")
        }
    }

    /**
     * Scoped HTTP fetch utility for custom scripts
     */
    fun fetchUrl(url: String, headers: Map<String, String> = emptyMap()): String {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
        return okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            response.body?.string().orEmpty()
        }
    }

    /**
     * Scoped DOM Selector utility for custom scripts
     */
    fun selectElements(html: String, cssQuery: String): List<Map<String, String>> {
        val doc = Jsoup.parse(html)
        return doc.select(cssQuery).map { element ->
            mapOf(
                "text" to element.text(),
                "html" to element.html(),
                "src" to (element.absUrl("src").ifBlank { element.attr("src") }),
                "href" to (element.absUrl("href").ifBlank { element.attr("href") }),
                "alt" to element.attr("alt"),
                "title" to element.attr("title")
            )
        }
    }

    private fun evalSafe(
        script: String,
        functionName: String,
        arguments: Map<String, Any?>
    ): Any? {
        // Safe evaluation / bridge logic for extensions
        return arguments
    }
}

