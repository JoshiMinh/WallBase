package com.joshiminh.wallbase.core.common

fun Throwable.userFacingMessage(defaultMessage: String): String {
    val rawMessage = localizedMessage ?: message ?: return defaultMessage
    val firstLine = rawMessage.lineSequence().firstOrNull()?.trim().orEmpty()
    if (firstLine.isEmpty()) return defaultMessage
    return if (firstLine.length <= 200) firstLine else firstLine.take(197) + "..."
}
