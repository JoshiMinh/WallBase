package com.joshiminh.wallbase.network

import java.io.IOException
import java.net.HttpURLConnection

@Throws(IOException::class)
fun HttpURLConnection.readResponseOrThrow(): String {
    val code = responseCode
    val stream = if (code in 200..299) inputStream else errorStream
    val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    if (code !in 200..299) {
        val message = if (body.isBlank()) "HTTP $code" else "HTTP $code: $body"
        throw IOException("Request failed: $message")
    }
    return body
}
