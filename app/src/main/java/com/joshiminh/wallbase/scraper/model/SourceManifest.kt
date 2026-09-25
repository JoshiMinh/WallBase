package com.joshiminh.wallbase.scraper.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SourceManifest(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "version") val version: String = "1.0.0",
    @Json(name = "versionCode") val versionCode: Int = 1,
    @Json(name = "iconUrl") val iconUrl: String? = null,
    @Json(name = "baseUrl") val baseUrl: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "author") val author: String? = "Community",
    @Json(name = "isNsfw") val isNsfw: Boolean = false,
    @Json(name = "headers") val headers: Map<String, String>? = null,
    @Json(name = "feeds") val feeds: List<FeedDefinition> = emptyList(),
    @Json(name = "search") val search: FeedDefinition? = null,
    @Json(name = "scriptHook") val scriptHook: String? = null,
    @Json(name = "settings") val settings: List<SourceSettingField>? = null
)

@JsonClass(generateAdapter = true)
data class FeedDefinition(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "url") val url: String,
    @Json(name = "method") val method: String = "GET",
    @Json(name = "headers") val headers: Map<String, String>? = null,
    @Json(name = "body") val body: String? = null,
    @Json(name = "pagination") val pagination: PaginationConfig? = null,
    @Json(name = "extraction") val extraction: ExtractionRule
)

@JsonClass(generateAdapter = true)
data class PaginationConfig(
    @Json(name = "type") val type: String = "page_number", // "page_number", "cursor", "offset", "none"
    @Json(name = "startPage") val startPage: Int = 1,
    @Json(name = "step") val step: Int = 1,
    @Json(name = "paramName") val paramName: String = "page",
    @Json(name = "cursorPath") val cursorPath: String? = null
)

@JsonClass(generateAdapter = true)
data class ExtractionRule(
    @Json(name = "format") val format: String = "html", // "html", "json", "regex"
    @Json(name = "itemSelector") val itemSelector: String? = null,
    @Json(name = "nextCursorSelector") val nextCursorSelector: String? = null,
    @Json(name = "nextCursorJsonPath") val nextCursorJsonPath: String? = null,
    @Json(name = "fields") val fields: Map<String, FieldExtractor> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class FieldExtractor(
    @Json(name = "selector") val selector: String? = null,
    @Json(name = "attribute") val attribute: String? = null, // "href", "src", "text", "alt", etc.
    @Json(name = "jsonPath") val jsonPath: String? = null,
    @Json(name = "fallbackJsonPath") val fallbackJsonPath: String? = null,
    @Json(name = "fallbackSelector") val fallbackSelector: String? = null,
    @Json(name = "regex") val regex: String? = null,
    @Json(name = "regexReplace") val regexReplace: RegexReplaceRule? = null,
    @Json(name = "defaultValue") val defaultValue: String? = null,
    @Json(name = "transform") val transform: String? = null // "prepend_base_url", "ensure_https", "trim", "url_decode"
)

@JsonClass(generateAdapter = true)
data class RegexReplaceRule(
    @Json(name = "find") val find: String,
    @Json(name = "replace") val replace: String
)

@JsonClass(generateAdapter = true)
data class SourceSettingField(
    @Json(name = "key") val key: String,
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "type") val type: String = "string", // "string", "boolean", "select"
    @Json(name = "defaultValue") val defaultValue: String? = null,
    @Json(name = "options") val options: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class ExtensionRepo(
    @Json(name = "name") val name: String,
    @Json(name = "author") val author: String? = null,
    @Json(name = "website") val website: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "version") val version: Int = 1,
    @Json(name = "sources") val sources: List<ExtensionRepoItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ExtensionRepoItem(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "version") val version: String,
    @Json(name = "versionCode") val versionCode: Int = 1,
    @Json(name = "iconUrl") val iconUrl: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "author") val author: String? = null,
    @Json(name = "manifestUrl") val manifestUrl: String,
    @Json(name = "isNsfw") val isNsfw: Boolean = false
)

