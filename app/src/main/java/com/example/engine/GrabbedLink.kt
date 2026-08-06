package com.example.engine

data class GrabbedLink(
    val url: String,
    val title: String,
    val suggestedFileName: String,
    val mediaType: String, // "IMAGE", "VIDEO", "AUDIO", "DOCUMENT", "ARCHIVE"
    val estimatedSizeBytes: Long,
    val thumbnailUrl: String?,
    val dimensions: String? = null,
    val sourcePageUrl: String,
    val domain: String,
    val isSelected: Boolean = true
)

data class WebGrabResult(
    val pageTitle: String,
    val sourceUrl: String,
    val domain: String,
    val totalLinksFound: Int,
    val links: List<GrabbedLink>
)
