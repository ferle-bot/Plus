package com.example.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class LinkGrabberEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun examinePageUrl(rawUrl: String): WebGrabResult = withContext(Dispatchers.IO) {
        val trimmedUrl = rawUrl.trim()
        if (trimmedUrl.isBlank()) {
            return@withContext WebGrabResult(
                pageTitle = "URL Vacía",
                sourceUrl = rawUrl,
                domain = "",
                totalLinksFound = 0,
                links = emptyList(),
                errorMessage = "Por favor ingresa una URL válida."
            )
        }

        val formattedUrl = if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            "https://$trimmedUrl"
        } else trimmedUrl

        val domain = extractDomain(formattedUrl)

        // Check if URL is Instagram or Social Media
        if (domain.contains("instagram.com") || domain.contains("instagr.am") || formattedUrl.contains("/reel/") || formattedUrl.contains("/p/")) {
            val instagramResult = handleInstagramSniffing(formattedUrl, domain)
            if (instagramResult.links.isNotEmpty()) {
                return@withContext instagramResult
            }
        }

        return@withContext performRealWebSniffing(formattedUrl, domain)
    }

    private fun handleInstagramSniffing(url: String, domain: String): WebGrabResult {
        val foundLinks = mutableListOf<GrabbedLink>()
        val seenUrls = mutableSetOf<String>()

        // Extract shortcode if post/reel: instagram.com/p/CODE/ or instagram.com/reel/CODE/ or /reels/CODE/ or /tv/CODE/
        val shortcodePattern = Pattern.compile("(?:/p/|/reel/|/reels/|/tv/)([A-Za-z0-9_-]+)")
        val matcher = shortcodePattern.matcher(url)
        val shortcode = if (matcher.find()) matcher.group(1) else null

        if (shortcode != null) {
            // Method 1: Try Instagram Embed Endpoint (Public & Login-Free)
            val embedUrl = "https://www.instagram.com/p/$shortcode/embed/captioned/"
            val embedResult = fetchAndParseHtml(embedUrl, url, domain, seenUrls, "Instagram Post ($shortcode)")
            foundLinks.addAll(embedResult)

            // Method 2: Try DDInstagram / VXInstagram OpenGraph Proxy if Method 1 returned no video
            if (foundLinks.none { it.mediaType == "VIDEO" }) {
                val proxyUrl = "https://ddinstagram.com/p/$shortcode/"
                val proxyResult = fetchAndParseHtml(proxyUrl, url, domain, seenUrls, "Instagram Media ($shortcode)")
                foundLinks.addAll(proxyResult)
            }

            // Method 3: Try Instagram oEmbed API
            if (foundLinks.isEmpty()) {
                val oembedUrl = "https://www.instagram.com/oembed/?url=https://www.instagram.com/p/$shortcode/"
                val oembedResult = fetchOembed(oembedUrl, url, domain, seenUrls)
                foundLinks.addAll(oembedResult)
            }
        } else {
            // It's a profile or general Instagram page (e.g. instagram.com/username)
            val cleanUsername = url.substringBefore("?").substringBefore("#").trimEnd('/').substringAfterLast('/')
            if (cleanUsername.isNotBlank() && cleanUsername != "instagram.com" && cleanUsername != "www.instagram.com") {
                val profileEmbedUrl = "https://www.instagram.com/$cleanUsername/embed/"
                val profileResult = fetchAndParseHtml(profileEmbedUrl, url, domain, seenUrls, "Perfil de Instagram (@$cleanUsername)")
                foundLinks.addAll(profileResult)

                val ddProfileUrl = "https://ddinstagram.com/$cleanUsername"
                val ddProfileResult = fetchAndParseHtml(ddProfileUrl, url, domain, seenUrls, "Fotos/Videos de @$cleanUsername")
                foundLinks.addAll(ddProfileResult)
            }
        }

        if (foundLinks.isNotEmpty()) {
            return WebGrabResult(
                pageTitle = "Contenido de Instagram (${foundLinks.size} detectados)",
                sourceUrl = url,
                domain = domain,
                totalLinksFound = foundLinks.size,
                links = foundLinks
            )
        }

        return WebGrabResult(
            pageTitle = domain,
            sourceUrl = url,
            domain = domain,
            totalLinksFound = 0,
            links = emptyList(),
            errorMessage = "No se pudieron obtener medios públicos de esta cuenta/enlace de Instagram. Verifica que la publicación o perfil sea público."
        )
    }

    private fun fetchAndParseHtml(
        fetchUrl: String,
        originalUrl: String,
        domain: String,
        seenUrls: MutableSet<String>,
        titlePrefix: String
    ): List<GrabbedLink> {
        val results = mutableListOf<GrabbedLink>()
        try {
            val request = Request.Builder()
                .url(fetchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val html = response.body?.string().orEmpty()

                // Extract OpenGraph Video <meta property="og:video" content="...">
                val ogVideoPattern = Pattern.compile("(?i)<meta[^>]+property=[\"']og:video[\"'][^>]+content=[\"']?([^\"'>\\s]+)[\"']?")
                val ogVidMatcher = ogVideoPattern.matcher(html)
                while (ogVidMatcher.find()) {
                    val vidUrl = unescapeJsonUrl(ogVidMatcher.group(1).orEmpty())
                    if (isValidMediaUrl(vidUrl) && seenUrls.add(vidUrl)) {
                        val fileName = extractFileNameFromUrl(vidUrl, "instagram_reel_${results.size + 1}.mp4")
                        results.add(
                            GrabbedLink(
                                url = vidUrl,
                                title = "$titlePrefix - Reel/Video HD",
                                suggestedFileName = fileName,
                                mediaType = "VIDEO",
                                estimatedSizeBytes = 0L,
                                thumbnailUrl = null,
                                dimensions = "Reel 1080p",
                                sourcePageUrl = originalUrl,
                                domain = domain
                            )
                        )
                    }
                }

                // Extract OpenGraph Image <meta property="og:image" content="...">
                val ogImgPattern = Pattern.compile("(?i)<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']?([^\"'>\\s]+)[\"']?")
                val ogImgMatcher = ogImgPattern.matcher(html)
                while (ogImgMatcher.find()) {
                    val imgUrl = unescapeJsonUrl(ogImgMatcher.group(1).orEmpty())
                    if (isValidMediaUrl(imgUrl) && seenUrls.add(imgUrl)) {
                        val fileName = extractFileNameFromUrl(imgUrl, "instagram_photo_${results.size + 1}.jpg")
                        results.add(
                            GrabbedLink(
                                url = imgUrl,
                                title = "$titlePrefix - Imagen HD",
                                suggestedFileName = fileName,
                                mediaType = "IMAGE",
                                estimatedSizeBytes = 0L,
                                thumbnailUrl = imgUrl,
                                dimensions = "High Res",
                                sourcePageUrl = originalUrl,
                                domain = domain
                            )
                        )
                    }
                }

                // Deep regex search in script JSON for "video_url" and "display_url"
                val jsonVideoPattern = Pattern.compile("\"video_url\"\\s*:\\s*\"([^\"]+)\"")
                val jsonVidMatcher = jsonVideoPattern.matcher(html)
                while (jsonVidMatcher.find()) {
                    val rawVid = unescapeJsonUrl(jsonVidMatcher.group(1).orEmpty())
                    if (isValidMediaUrl(rawVid) && seenUrls.add(rawVid)) {
                        val fileName = extractFileNameFromUrl(rawVid, "instagram_video_${results.size + 1}.mp4")
                        results.add(
                            GrabbedLink(
                                url = rawVid,
                                title = "$titlePrefix - Video MP4",
                                suggestedFileName = fileName,
                                mediaType = "VIDEO",
                                estimatedSizeBytes = 0L,
                                thumbnailUrl = null,
                                dimensions = "Video HD",
                                sourcePageUrl = originalUrl,
                                domain = domain
                            )
                        )
                    }
                }

                val jsonImagePattern = Pattern.compile("\"(?:display_url|display_resources|thumbnail_src|profile_pic_url_hd)\"\\s*:\\s*\"([^\"]+)\"")
                val jsonImgMatcher = jsonImagePattern.matcher(html)
                while (jsonImgMatcher.find()) {
                    val rawImg = unescapeJsonUrl(jsonImgMatcher.group(1).orEmpty())
                    if (isValidMediaUrl(rawImg) && seenUrls.add(rawImg)) {
                        val fileName = extractFileNameFromUrl(rawImg, "instagram_img_${results.size + 1}.jpg")
                        results.add(
                            GrabbedLink(
                                url = rawImg,
                                title = "$titlePrefix - Foto",
                                suggestedFileName = fileName,
                                mediaType = "IMAGE",
                                estimatedSizeBytes = 0L,
                                thumbnailUrl = rawImg,
                                dimensions = "1080x1080",
                                sourcePageUrl = originalUrl,
                                domain = domain
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        return results
    }

    private fun fetchOembed(oembedUrl: String, originalUrl: String, domain: String, seenUrls: MutableSet<String>): List<GrabbedLink> {
        val results = mutableListOf<GrabbedLink>()
        try {
            val request = Request.Builder().url(oembedUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val json = response.body?.string().orEmpty()
                val thumbPattern = Pattern.compile("\"thumbnail_url\"\\s*:\\s*\"([^\"]+)\"")
                val matcher = thumbPattern.matcher(json)
                if (matcher.find()) {
                    val thumbUrl = unescapeJsonUrl(matcher.group(1).orEmpty())
                    if (isValidMediaUrl(thumbUrl) && seenUrls.add(thumbUrl)) {
                        val fileName = extractFileNameFromUrl(thumbUrl, "instagram_oembed.jpg")
                        results.add(
                            GrabbedLink(
                                url = thumbUrl,
                                title = "Instagram OEmbed Preview",
                                suggestedFileName = fileName,
                                mediaType = "IMAGE",
                                estimatedSizeBytes = 0L,
                                thumbnailUrl = thumbUrl,
                                dimensions = null,
                                sourcePageUrl = originalUrl,
                                domain = domain
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        return results
    }

    private fun performRealWebSniffing(url: String, domain: String): WebGrabResult {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return WebGrabResult(
                        pageTitle = domain,
                        sourceUrl = url,
                        domain = domain,
                        totalLinksFound = 0,
                        links = emptyList(),
                        errorMessage = "Error al conectar con la página web: HTTP ${response.code} ${response.message}"
                    )
                }

                val contentType = response.header("Content-Type").orEmpty().lowercase()
                val contentDisposition = response.header("Content-Disposition").orEmpty()
                val contentLength = response.body?.contentLength() ?: -1L

                // Check if the URL points directly to a downloadable file
                if (isDirectFileContentType(contentType) || isDirectFileDisposition(contentDisposition) || isDirectFileExtension(url)) {
                    val fileName = extractFileNameFromHeaderOrUrl(url, contentDisposition)
                    val mediaType = classifyMediaType(contentType, fileName)

                    val directLink = GrabbedLink(
                        url = response.request.url.toString(),
                        title = fileName,
                        suggestedFileName = fileName,
                        mediaType = mediaType,
                        estimatedSizeBytes = if (contentLength > 0) contentLength else 0L,
                        thumbnailUrl = if (mediaType == "IMAGE") url else null,
                        dimensions = null,
                        sourcePageUrl = url,
                        domain = domain
                    )

                    return WebGrabResult(
                        pageTitle = fileName,
                        sourceUrl = url,
                        domain = domain,
                        totalLinksFound = 1,
                        links = listOf(directLink)
                    )
                }

                // Parse HTML document for downloadable media/file links
                val html = response.body?.string().orEmpty()
                val pageTitle = extractTitleFromHtml(html) ?: domain

                val foundLinks = mutableListOf<GrabbedLink>()
                val seenUrls = mutableSetOf<String>()

                // 1. Extract Meta tags og:image and og:video
                val ogPattern = Pattern.compile("(?i)<meta[^>]+property=[\"']og:(image|video)[\"'][^>]+content=[\"']?([^\"'>\\s]+)[\"']?")
                val ogMatcher = ogPattern.matcher(html)
                while (ogMatcher.find()) {
                    val ogType = ogMatcher.group(1) ?: "image"
                    val rawContent = unescapeJsonUrl(ogMatcher.group(2) ?: continue)
                    val fullUrl = resolveRelativeUrl(url, rawContent)
                    if (isValidMediaUrl(fullUrl) && seenUrls.add(fullUrl)) {
                        val isVideo = ogType.equals("video", ignoreCase = true)
                        val fileName = extractFileNameFromUrl(fullUrl, if (isVideo) "video_principal.mp4" else "imagen_principal.jpg")
                        foundLinks.add(
                            GrabbedLink(
                                url = fullUrl,
                                title = if (isVideo) "Video principal (og:video)" else "Imagen principal (og:image)",
                                suggestedFileName = fileName,
                                mediaType = if (isVideo) "VIDEO" else "IMAGE",
                                estimatedSizeBytes = 0L,
                                thumbnailUrl = if (!isVideo) fullUrl else null,
                                dimensions = null,
                                sourcePageUrl = url,
                                domain = domain
                            )
                        )
                    }
                }

                // 2. Deep JSON Media URL Extractor for modern web pages
                val jsonMediaPattern = Pattern.compile("\"(?:video_url|contentUrl|media_url|download_url|src)\"\\s*:\\s*\"([^\"]+)\"")
                val jsonMatcher = jsonMediaPattern.matcher(html)
                var jsonCount = 0
                while (jsonMatcher.find() && jsonCount < 15) {
                    val rawUrlCandidate = unescapeJsonUrl(jsonMatcher.group(1).orEmpty())
                    val fullUrl = resolveRelativeUrl(url, rawUrlCandidate)
                    if (isValidMediaUrl(fullUrl) && (fullUrl.contains(".mp4") || fullUrl.contains(".mkv") || fullUrl.contains(".jpg") || fullUrl.contains(".png")) && seenUrls.add(fullUrl)) {
                        val isVideo = fullUrl.contains(".mp4") || fullUrl.contains(".mkv")
                        val fileName = extractFileNameFromUrl(fullUrl, if (isVideo) "media_json_$jsonCount.mp4" else "media_json_$jsonCount.jpg")
                        foundLinks.add(
                            GrabbedLink(
                                url = fullUrl,
                                title = "Medio embebido: $fileName",
                                suggestedFileName = fileName,
                                mediaType = if (isVideo) "VIDEO" else "IMAGE",
                                estimatedSizeBytes = 0L,
                                thumbnailUrl = if (!isVideo) fullUrl else null,
                                dimensions = null,
                                sourcePageUrl = url,
                                domain = domain
                            )
                        )
                        jsonCount++
                    }
                }

                // 3. Extract <img src="...">
                val imgPattern = Pattern.compile("(?i)<img[^>]+src=[\"']?([^\"'>\\s]+)[\"']?")
                val imgMatcher = imgPattern.matcher(html)
                var imgCount = 0
                while (imgMatcher.find() && imgCount < 30) {
                    val rawSrc = unescapeJsonUrl(imgMatcher.group(1) ?: continue)
                    val fullUrl = resolveRelativeUrl(url, rawSrc)
                    if (isValidMediaUrl(fullUrl) && seenUrls.add(fullUrl)) {
                        val fileName = extractFileNameFromUrl(fullUrl, "imagen_${imgCount + 1}.jpg")
                        foundLinks.add(
                            GrabbedLink(
                                url = fullUrl,
                                title = "Imagen: $fileName",
                                suggestedFileName = fileName,
                                mediaType = "IMAGE",
                                estimatedSizeBytes = 0L,
                                thumbnailUrl = fullUrl,
                                dimensions = null,
                                sourcePageUrl = url,
                                domain = domain
                            )
                        )
                        imgCount++
                    }
                }

                // 4. Extract <video src="...">, <audio src="...">, <source src="...">
                val mediaSourcePattern = Pattern.compile("(?i)<(?:video|audio|source)[^>]+src=[\"']?([^\"'>\\s]+)[\"']?")
                val mediaMatcher = mediaSourcePattern.matcher(html)
                var mediaCount = 0
                while (mediaMatcher.find() && mediaCount < 20) {
                    val rawSrc = unescapeJsonUrl(mediaMatcher.group(1) ?: continue)
                    val fullUrl = resolveRelativeUrl(url, rawSrc)
                    if (isValidMediaUrl(fullUrl) && seenUrls.add(fullUrl)) {
                        val fileName = extractFileNameFromUrl(fullUrl, "media_${mediaCount + 1}.mp4")
                        val ext = fileName.substringAfterLast(".", "mp4").lowercase()
                        val type = classifyExtension(ext)
                        foundLinks.add(
                            GrabbedLink(
                                url = fullUrl,
                                title = "Medio: $fileName",
                                suggestedFileName = fileName,
                                mediaType = type,
                                estimatedSizeBytes = 0L,
                                thumbnailUrl = null,
                                dimensions = if (type == "VIDEO") "Video Web" else null,
                                sourcePageUrl = url,
                                domain = domain
                            )
                        )
                        mediaCount++
                    }
                }

                // 5. Extract Anchor download links <a href="...">
                val anchorPattern = Pattern.compile("(?i)<a[^>]+href=[\"']?([^\"'>\\s]+)[\"']?")
                val anchorMatcher = anchorPattern.matcher(html)
                var fileCount = 0
                while (anchorMatcher.find() && fileCount < 25) {
                    val rawHref = unescapeJsonUrl(anchorMatcher.group(1) ?: continue)
                    val fullUrl = resolveRelativeUrl(url, rawHref)
                    val ext = fullUrl.substringAfterLast(".", "").lowercase().substringBefore("?").substringBefore("#")
                    if (ext in listOf("mp4", "mkv", "mp3", "pdf", "zip", "rar", "7z", "iso", "doc", "docx", "xls", "xlsx", "apk", "jpg", "jpeg", "png", "webp", "gif")) {
                        if (isValidMediaUrl(fullUrl) && seenUrls.add(fullUrl)) {
                            val fileName = extractFileNameFromUrl(fullUrl, "archivo_${fileCount + 1}.$ext")
                            val type = classifyExtension(ext)
                            foundLinks.add(
                                GrabbedLink(
                                    url = fullUrl,
                                    title = "Archivo: $fileName",
                                    suggestedFileName = fileName,
                                    mediaType = type,
                                    estimatedSizeBytes = 0L,
                                    thumbnailUrl = if (type == "IMAGE") fullUrl else null,
                                    dimensions = null,
                                    sourcePageUrl = url,
                                    domain = domain
                                )
                            )
                            fileCount++
                        }
                    }
                }

                if (foundLinks.isEmpty()) {
                    return WebGrabResult(
                        pageTitle = pageTitle,
                        sourceUrl = url,
                        domain = domain,
                        totalLinksFound = 0,
                        links = emptyList(),
                        errorMessage = "No se encontraron archivos o medios descargables en esta página."
                    )
                }

                return WebGrabResult(
                    pageTitle = pageTitle,
                    sourceUrl = url,
                    domain = domain,
                    totalLinksFound = foundLinks.size,
                    links = foundLinks
                )
            }
        } catch (e: Exception) {
            return WebGrabResult(
                pageTitle = domain,
                sourceUrl = url,
                domain = domain,
                totalLinksFound = 0,
                links = emptyList(),
                errorMessage = "Error de red: ${e.localizedMessage ?: e.message ?: "No se pudo conectar a la URL"}"
            )
        }
    }

    private fun unescapeJsonUrl(raw: String): String {
        return raw
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\u003d", "=")
            .replace("&amp;", "&")
            .trim()
    }

    private fun isDirectFileContentType(contentType: String): Boolean {
        return contentType.startsWith("image/") ||
                contentType.startsWith("video/") ||
                contentType.startsWith("audio/") ||
                contentType.contains("application/pdf") ||
                contentType.contains("application/zip") ||
                contentType.contains("application/x-rar") ||
                contentType.contains("application/octet-stream")
    }

    private fun isDirectFileDisposition(disposition: String): Boolean {
        return disposition.lowercase().contains("attachment")
    }

    private fun isDirectFileExtension(url: String): Boolean {
        val cleanPath = url.substringBefore("?").substringBefore("#").lowercase()
        val ext = cleanPath.substringAfterLast(".", "")
        return ext in listOf("jpg", "jpeg", "png", "webp", "gif", "mp4", "mkv", "webm", "avi", "mp3", "flac", "pdf", "zip", "rar", "7z", "apk", "iso", "doc", "docx")
    }

    private fun classifyMediaType(contentType: String, fileName: String): String {
        val ext = fileName.substringAfterLast(".", "").lowercase()
        if (contentType.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "webp", "gif")) return "IMAGE"
        if (contentType.startsWith("video/") || ext in listOf("mp4", "mkv", "webm", "avi", "mov")) return "VIDEO"
        if (contentType.startsWith("audio/") || ext in listOf("mp3", "flac", "wav", "aac", "ogg")) return "AUDIO"
        if (contentType.contains("zip") || contentType.contains("rar") || ext in listOf("zip", "rar", "7z", "tar", "gz")) return "ARCHIVE"
        if (contentType.contains("pdf") || ext in listOf("pdf", "doc", "docx", "txt", "epub")) return "DOCUMENT"
        return "OTHER"
    }

    private fun classifyExtension(ext: String): String {
        return when (ext) {
            "jpg", "jpeg", "png", "webp", "gif", "svg", "bmp" -> "IMAGE"
            "mp4", "mkv", "webm", "avi", "mov", "m4v" -> "VIDEO"
            "mp3", "flac", "aac", "wav", "m4a", "ogg" -> "AUDIO"
            "zip", "rar", "7z", "tar", "gz", "bz2" -> "ARCHIVE"
            "pdf", "doc", "docx", "epub", "txt" -> "DOCUMENT"
            else -> "OTHER"
        }
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = URI(url)
            val host = uri.host ?: url
            if (host.startsWith("www.")) host.substring(4) else host
        } catch (e: Exception) {
            "sitio.com"
        }
    }

    private fun extractTitleFromHtml(html: String): String? {
        val pattern = Pattern.compile("(?i)<title>(.*?)</title>")
        val matcher = pattern.matcher(html)
        return if (matcher.find()) matcher.group(1)?.trim() else null
    }

    private fun extractFileNameFromHeaderOrUrl(url: String, disposition: String): String {
        if (disposition.isNotBlank() && disposition.contains("filename=")) {
            val name = disposition.substringAfter("filename=").replace("\"", "").substringBefore(";")
            if (name.isNotBlank()) return name.trim()
        }
        return extractFileNameFromUrl(url, "descarga.bin")
    }

    private fun extractFileNameFromUrl(url: String, fallback: String): String {
        val rawName = url.substringBefore("?").substringBefore("#").substringAfterLast("/")
        return if (rawName.isNotBlank() && rawName.contains(".")) rawName.trim() else fallback
    }

    private fun resolveRelativeUrl(baseUrl: String, relative: String): String {
        if (relative.startsWith("data:") || relative.startsWith("javascript:")) return ""
        return try {
            val base = URI(baseUrl)
            val resolved = base.resolve(relative)
            resolved.toString()
        } catch (e: Exception) {
            if (relative.startsWith("http")) relative else ""
        }
    }

    private fun isValidMediaUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }
}
