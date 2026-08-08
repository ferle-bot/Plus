package com.example.engine

import java.net.URI
import java.util.regex.Pattern

data class SplitArchivePackage(
    val packageName: String,
    val totalPartsFound: Int,
    val missingPartNumbers: List<Int>,
    val estimatedTotalSizeBytes: Long,
    val items: List<GrabbedLink>
)

data class BatchParseResult(
    val totalUrlsFound: Int,
    val splitPackages: List<SplitArchivePackage>,
    val standaloneLinks: List<GrabbedLink>
)

object BatchLinkParser {

    private val URL_PATTERN = Pattern.compile(
        "(?i)\\b(?:https?|ftp)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"
    )

    // Regex patterns for split archives (.part1.rar, .part01.rar, .001, .zip.001, .z01)
    private val PART_RAR_PATTERN = Pattern.compile("(?i)(.*?)[._-]?part0*(\\d+)\\.(rar|exe|7z|zip)$")
    private val NUMERIC_EXT_PATTERN = Pattern.compile("(?i)(.*?)\\.(\\d{3})$")
    private val ZIP_PART_PATTERN = Pattern.compile("(?i)(.*?)\\.(zip|z|7z)\\.0*(\\d+)$")

    fun parseTextBlob(text: String, sourceTitle: String = "Texto Pegado"): BatchParseResult {
        val extractedUrls = mutableListOf<String>()
        val matcher = URL_PATTERN.matcher(text)
        while (matcher.find()) {
            val url = matcher.group()
            if (!extractedUrls.contains(url)) {
                extractedUrls.add(url)
            }
        }

        val allLinks = extractedUrls.map { url ->
            val domain = extractDomain(url)
            val fileName = extractFileName(url)
            val mediaType = detectMediaType(fileName, url)

            GrabbedLink(
                url = url,
                title = fileName,
                suggestedFileName = fileName,
                mediaType = mediaType,
                estimatedSizeBytes = estimateFileSize(fileName, mediaType),
                thumbnailUrl = null,
                sourcePageUrl = url,
                domain = domain
            )
        }

        // Group split archive links into packages
        val packageMap = mutableMapOf<String, MutableList<Pair<Int, GrabbedLink>>>()
        val standalone = mutableListOf<GrabbedLink>()

        for (link in allLinks) {
            val fileName = link.suggestedFileName
            var pkgName: String? = null
            var partNum: Int? = null

            val rarMatch = PART_RAR_PATTERN.matcher(fileName)
            if (rarMatch.find()) {
                pkgName = rarMatch.group(1)?.trim('.', '_', '-', ' ') ?: "Paquete_Dividido"
                partNum = rarMatch.group(2)?.toIntOrNull()
            } else {
                val numMatch = NUMERIC_EXT_PATTERN.matcher(fileName)
                if (numMatch.find()) {
                    pkgName = numMatch.group(1)?.trim('.', '_', '-', ' ') ?: "Paquete_Dividido"
                    partNum = numMatch.group(2)?.toIntOrNull()
                } else {
                    val zipMatch = ZIP_PART_PATTERN.matcher(fileName)
                    if (zipMatch.find()) {
                        pkgName = zipMatch.group(1)?.trim('.', '_', '-', ' ') ?: "Paquete_Dividido"
                        partNum = zipMatch.group(3)?.toIntOrNull()
                    }
                }
            }

            if (pkgName != null && partNum != null) {
                packageMap.getOrPut(pkgName) { mutableListOf() }.add(partNum to link)
            } else {
                standalone.add(link)
            }
        }

        val splitPackages = packageMap.map { (pkgName, partPairs) ->
            val sorted = partPairs.sortedBy { it.first }
            val foundPartNumbers = sorted.map { it.first }.toSet()
            val maxPart = foundPartNumbers.maxOrNull() ?: 1
            val missing = (1..maxPart).filter { it !in foundPartNumbers }

            SplitArchivePackage(
                packageName = pkgName,
                totalPartsFound = sorted.size,
                missingPartNumbers = missing,
                estimatedTotalSizeBytes = sorted.sumOf { it.second.estimatedSizeBytes },
                items = sorted.map { it.second }
            )
        }

        return BatchParseResult(
            totalUrlsFound = extractedUrls.size,
            splitPackages = splitPackages,
            standaloneLinks = standalone
        )
    }

    private fun extractDomain(url: String): String {
        return try {
            val host = URI(url).host ?: "web"
            host.removePrefix("www.")
        } catch (_: Exception) {
            "web"
        }
    }

    private fun extractFileName(url: String): String {
        return try {
            val path = URI(url).path
            val lastSegment = path.substringAfterLast('/')
            if (lastSegment.isNotBlank() && lastSegment.contains(".")) {
                lastSegment
            } else {
                "archivo_${System.currentTimeMillis() % 10000}"
            }
        } catch (_: Exception) {
            "archivo_${System.currentTimeMillis() % 10000}"
        }
    }

    private fun detectMediaType(fileName: String, url: String): String {
        val lower = fileName.lowercase()
        val lowerUrl = url.lowercase()
        return when {
            lowerUrl.contains("tiktok.com") -> "VIDEO"
            lowerUrl.contains("instagram.com") -> "VIDEO"
            lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm") || lower.endsWith(".avi") -> "VIDEO"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".gif") -> "IMAGE"
            lower.endsWith(".mp3") || lower.endsWith(".flac") || lower.endsWith(".m4a") || lower.endsWith(".wav") -> "AUDIO"
            lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") || lower.endsWith(".tar") || lower.matches(Regex(".*\\.\\d{3}$")) -> "ARCHIVE"
            else -> "DOCUMENT"
        }
    }

    private fun estimateFileSize(fileName: String, mediaType: String): Long {
        return when (mediaType) {
            "VIDEO" -> 25_000_000L
            "ARCHIVE" -> 50_000_000L
            "AUDIO" -> 8_000_000L
            "IMAGE" -> 2_500_000L
            else -> 5_000_000L
        }
    }
}
