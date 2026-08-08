package com.example.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

        // 1. First, try Cobalt API (universal extractor for Instagram, TikTok, YouTube, Twitter/X, Pinterest, etc.)
        val cobaltResult = tryCobaltExtractor(formattedUrl, domain)
        if (cobaltResult != null && cobaltResult.links.isNotEmpty()) {
            return@withContext cobaltResult
        }

        // 2. Platform-specific fallback extractors
        if (domain.contains("instagram.com") || domain.contains("instagr.am") || formattedUrl.contains("/reel/") || formattedUrl.contains("/p/")) {
            val instagramResult = handleInstagramSniffing(formattedUrl, domain)
            if (instagramResult.links.isNotEmpty()) {
                return@withContext instagramResult
            }
        }

        if (domain.contains("tiktok.com")) {
            val tiktokResult = handleTikTokSniffing(formattedUrl, domain)
            if (tiktokResult.links.isNotEmpty()) {
                return@withContext tiktokResult
            }
        }

        if (domain.contains("twitter.com") || domain.contains("x.com")) {
            val twitterResult = handleTwitterSniffing(formattedUrl, domain)
            if (twitterResult.links.isNotEmpty()) {
                return@withContext twitterResult
            }
        }

        if (domain.contains("pinterest.com") || domain.contains("pin.it")) {
            val pinterestResult = handlePinterestSniffing(formattedUrl, domain)
            if (pinterestResult.links.isNotEmpty()) {
                return@withContext pinterestResult
            }
        }

        // 3. Fallback: General JDownloader-style Web Sniffer
        return@withContext performRealWebSniffing(formattedUrl, domain)
    }

    /**
     * Cobalt Universal Media Extractor API
     */
    private fun tryCobaltExtractor(url: String, domain: String): WebGrabResult? {
        val cobaltEndpoints = listOf(
            "https://api.cobalt.tools/api/json",
            "https://co.wuk.sh/api/json"
        )

        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val jsonBody = "{\"url\":\"$url\"}".toRequestBody(jsonMediaType)

        for (endpoint in cobaltEndpoints) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .post(jsonBody)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/122.0.0.0 Safari/537.36")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val json = response.body?.string().orEmpty()

                    val links = mutableListOf<GrabbedLink>()

                    // Status: "picker" (Multiple items like Instagram Carousel / Photo posts)
                    if (json.contains("\"picker\"") || json.contains("\"status\":\"picker\"")) {
                        val pickerPattern = Pattern.compile("\"type\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"url\"\\s*:\\s*\"([^\"]+)\"")
                        val matcher = pickerPattern.matcher(json)
                        var index = 1
                        while (matcher.find()) {
                            val type = matcher.group(1).orEmpty()
                            val itemUrl = unescapeJsonUrl(matcher.group(2).orEmpty())
                            if (isValidMediaUrl(itemUrl)) {
                                val isVideo = type.contains("video") || itemUrl.contains(".mp4")
                                val mediaType = if (isVideo) "VIDEO" else "IMAGE"
                                val ext = if (isVideo) "mp4" else "jpg"
                                val fileName = "${domain.replace(".", "_")}_item_$index.$ext"
                                links.add(
                                    GrabbedLink(
                                        url = itemUrl,
                                        title = "$domain - Archivo $index ($mediaType)",
                                        suggestedFileName = fileName,
                                        mediaType = mediaType,
                                        estimatedSizeBytes = 0L,
                                        thumbnailUrl = if (!isVideo) itemUrl else null,
                                        dimensions = if (isVideo) "HD Video" else "High Res",
                                        sourcePageUrl = url,
                                        domain = domain
                                    )
                                )
                                index++
                            }
                        }
                    }

                    // Status: "stream", "redirect", "tunnel" (Single Video or Audio)
                    if (links.isEmpty()) {
                        val singleUrlPattern = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"")
                        val matcher = singleUrlPattern.matcher(json)
                        if (matcher.find()) {
                            val directUrl = unescapeJsonUrl(matcher.group(1).orEmpty())
                            if (isValidMediaUrl(directUrl) && !directUrl.contains("cobalt.tools/api")) {
                                val isVideo = directUrl.contains(".mp4") || directUrl.contains("video") || domain.contains("youtube") || domain.contains("tiktok") || domain.contains("instagram")
                                val mediaType = if (isVideo) "VIDEO" else "IMAGE"
                                val ext = if (isVideo) "mp4" else "jpg"
                                val fileName = "${domain.replace(".", "_")}_media.$ext"
                                links.add(
                                    GrabbedLink(
                                        url = directUrl,
                                        title = "$domain - Archivo multimedia HD",
                                        suggestedFileName = fileName,
                                        mediaType = mediaType,
                                        estimatedSizeBytes = 0L,
                                        thumbnailUrl = null,
                                        dimensions = "HD",
                                        sourcePageUrl = url,
                                        domain = domain
                                    )
                                )
                            }
                        }
                    }

                    if (links.isNotEmpty()) {
                        return WebGrabResult(
                            pageTitle = "Contenido de $domain (${links.size} detectados)",
                            sourceUrl = url,
                            domain = domain,
                            totalLinksFound = links.size,
                            links = links
                        )
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Instagram Sniffer using DDInstagram / VxInstagram & Social Bot User-Agents
     */
    private fun handleInstagramSniffing(url: String, domain: String): WebGrabResult {
        val foundLinks = mutableListOf<GrabbedLink>()
        val seenUrls = mutableSetOf<String>()

        val shortcodePattern = Pattern.compile("(?:/p/|/reel/|/reels/|/tv/)([A-Za-z0-9_-]+)")
        val matcher = shortcodePattern.matcher(url)
        val shortcode = if (matcher.find()) matcher.group(1) else null

        if (shortcode != null) {
            // Strategy 1: Fetch DDInstagram proxy with TelegramBot User-Agent (triggers full OG tags rendering)
            val ddUrl = "https://ddinstagram.com/p/$shortcode/"
            val ddResults = fetchWithBotUserAgent(ddUrl, url, domain, seenUrls, "Instagram ($shortcode)")
            foundLinks.addAll(ddResults)

            // Strategy 2: Fetch VxInstagram proxy
            if (foundLinks.isEmpty()) {
                val vxUrl = "https://vxinstagram.com/p/$shortcode/"
                val vxResults = fetchWithBotUserAgent(vxUrl, url, domain, seenUrls, "Instagram ($shortcode)")
                foundLinks.addAll(vxResults)
            }

            // Strategy 3: Try Instagram Embed Endpoint with Bot User-Agent
            if (foundLinks.isEmpty()) {
                val embedUrl = "https://www.instagram.com/p/$shortcode/embed/captioned/"
                val embedResults = fetchWithBotUserAgent(embedUrl, url, domain, seenUrls, "Instagram Embed ($shortcode)")
                foundLinks.addAll(embedResults)
            }
        } else {
            // Profile URL: e.g. instagram.com/username
            val cleanUsername = url.substringBefore("?").substringBefore("#").trimEnd('/').substringAfterLast('/')
            if (cleanUsername.isNotBlank() && cleanUsername != "instagram.com" && cleanUsername != "www.instagram.com") {
                val ddProfileUrl = "https://ddinstagram.com/$cleanUsername"
                val profileResults = fetchWithBotUserAgent(ddProfileUrl, url, domain, seenUrls, "@$cleanUsername")
                foundLinks.addAll(profileResults)
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
            errorMessage = "No se pudo extraer el contenido de Instagram. Asegúrate de que la publicación sea pública o el enlace sea correcto."
        )
    }

    /**
     * TikTok Sniffer using TikWM API
     */
    private fun handleTikTokSniffing(url: String, domain: String): WebGrabResult {
        val links = mutableListOf<GrabbedLink>()
        try {
            val formMediaType = "application/x-www-form-urlencoded".toMediaType()
            val body = "url=${java.net.URLEncoder.encode(url, "UTF-8")}&hd=1".toRequestBody(formMediaType)
            val request = Request.Builder()
                .url("https://www.tikwm.com/api/")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = response.body?.string().orEmpty()
                    val playPattern = Pattern.compile("\"play\"\\s*:\\s*\"([^\"]+)\"")
                    val matcher = playPattern.matcher(json)
                    if (matcher.find()) {
                        val videoUrl = unescapeJsonUrl(matcher.group(1).orEmpty())
                        val fullVidUrl = if (videoUrl.startsWith("//")) "https:$videoUrl" else videoUrl
                        if (isValidMediaUrl(fullVidUrl)) {
                            val titlePattern = Pattern.compile("\"title\"\\s*:\\s*\"([^\"]+)\"")
                            val titleMatcher = titlePattern.matcher(json)
                            val title = if (titleMatcher.find()) titleMatcher.group(1).orEmpty() else "TikTok Video Sin Marca de Agua"

                            val coverPattern = Pattern.compile("\"cover\"\\s*:\\s*\"([^\"]+)\"")
                            val coverMatcher = coverPattern.matcher(json)
                            val coverUrl = if (coverMatcher.find()) unescapeJsonUrl(coverMatcher.group(1).orEmpty()) else null

                            links.add(
                                GrabbedLink(
                                    url = fullVidUrl,
                                    title = title,
                                    suggestedFileName = "tiktok_${System.currentTimeMillis()}.mp4",
                                    mediaType = "VIDEO",
                                    estimatedSizeBytes = 0L,
                                    thumbnailUrl = coverUrl,
                                    dimensions = "1080p No Watermark",
                                    sourcePageUrl = url,
                                    domain = domain
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return WebGrabResult(
            pageTitle = "TikTok Video",
            sourceUrl = url,
            domain = domain,
            totalLinksFound = links.size,
            links = links
        )
    }

    /**
     * Twitter/X Sniffer using FxTwitter proxy
     */
    private fun handleTwitterSniffing(url: String, domain: String): WebGrabResult {
        val fxUrl = url.replace("twitter.com", "fxtwitter.com").replace("x.com", "fxtwitter.com")
        val links = fetchWithBotUserAgent(fxUrl, url, domain, mutableSetOf(), "Twitter/X Post")
        return WebGrabResult(
            pageTitle = "Publicación de Twitter/X",
            sourceUrl = url,
            domain = domain,
            totalLinksFound = links.size,
            links = links
        )
    }

    /**
     * Pinterest Sniffer
     */
    private fun handlePinterestSniffing(url: String, domain: String): WebGrabResult {
        val links = fetchWithBotUserAgent(url, url, domain, mutableSetOf(), "Pinterest Pin")
        return WebGrabResult(
            pageTitle = "Pinterest Media",
            sourceUrl = url,
            domain = domain,
            totalLinksFound = links.size,
            links = links
        )
    }

    /**
     * Fetch HTML using TelegramBot User-Agent (triggers OpenGraph server-side rendering on social platforms)
     */
    private fun fetchWithBotUserAgent(
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
                .header("User-Agent", "TelegramBot (like TwitterBot)")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val html = response.body?.string().orEmpty()

                // Extract OpenGraph Video <meta property="og:video" content="...">
                val ogVideoPattern = Pattern.compile("(?i)<meta[^>]+property=[\"'](?:og:video|og:video:secure_url|twitter:player:stream)[\"'][^>]+content=[\"']?([^\"'>\\s]+)[\"']?")
                val ogVidMatcher = ogVideoPattern.matcher(html)
                while (ogVidMatcher.find()) {
                    val vidUrl = unescapeJsonUrl(ogVidMatcher.group(1).orEmpty())
                    if (isValidMediaUrl(vidUrl) && seenUrls.add(vidUrl)) {
                        val fileName = extractFileNameFromUrl(vidUrl, "video_${results.size + 1}.mp4")
                        results.add(
                            GrabbedLink(
                                url = vidUrl,
                                title = "$titlePrefix - Video MP4 HD",
                                suggestedFileName = fileName,
                                mediaType = "VIDEO",
                                estimatedSizeBytes = 0L,
                                thumbnailUrl = null,
                                dimensions = "HD 1080p",
                                sourcePageUrl = originalUrl,
                                domain = domain
                            )
                        )
                    }
                }

                // Extract OpenGraph Image <meta property="og:image" content="...">
                val ogImgPattern = Pattern.compile("(?i)<meta[^>]+property=[\"'](?:og:image|twitter:image)[\"'][^>]+content=[\"']?([^\"'>\\s]+)[\"']?")
                val ogImgMatcher = ogImgPattern.matcher(html)
                while (ogImgMatcher.find()) {
                    val imgUrl = unescapeJsonUrl(ogImgMatcher.group(1).orEmpty())
                    if (isValidMediaUrl(imgUrl) && seenUrls.add(imgUrl)) {
                        val fileName = extractFileNameFromUrl(imgUrl, "foto_${results.size + 1}.jpg")
                        results.add(
                            GrabbedLink(
                                url = imgUrl,
                                title = "$titlePrefix - Imagen HD",
                                suggestedFileName = fileName,
                                mediaType = "IMAGE",
                                estimatedSizeBytes = 0L,
                                thumbnailUrl = imgUrl,
                                dimensions = "Alta Resolución",
                                sourcePageUrl = originalUrl,
                                domain = domain
                            )
                        )
                    }
                }

                // Deep search in script JSON for video_url and display_url
                val jsonVideoPattern = Pattern.compile("\"(?:video_url|contentUrl|downloadUrl|src)\"\\s*:\\s*\"([^\"]+)\"")
                val jsonVidMatcher = jsonVideoPattern.matcher(html)
                while (jsonVidMatcher.find()) {
                    val rawVid = unescapeJsonUrl(jsonVidMatcher.group(1).orEmpty())
                    if (isValidMediaUrl(rawVid) && (rawVid.contains(".mp4") || rawVid.contains(".mkv")) && seenUrls.add(rawVid)) {
                        val fileName = extractFileNameFromUrl(rawVid, "video_json_${results.size + 1}.mp4")
                        results.add(
                            GrabbedLink(
                                url = rawVid,
                                title = "$titlePrefix - Stream MP4",
                                suggestedFileName = fileName,
                                mediaType = "VIDEO",
                                estimatedSizeBytes = 0L,
                                thumbnailUrl = null,
                                dimensions = "HD",
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

    /**
     * General Web Sniffer (JDownloader style)
     */
    private fun performRealWebSniffing(url: String, domain: String): WebGrabResult {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
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

                // Direct file check
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

                // Parse HTML document
                val html = response.body?.string().orEmpty()
                val pageTitle = extractTitleFromHtml(html) ?: domain

                val foundLinks = mutableListOf<GrabbedLink>()
                val seenUrls = mutableSetOf<String>()

                // 1. Meta tags og:image and og:video
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

                // 2. Media Tags <video src="...">, <audio src="...">, <source src="...">
                val mediaSourcePattern = Pattern.compile("(?i)<(?:video|audio|source)[^>]+src=[\"']?([^\"'>\\s]+)[\"']?")
                val mediaMatcher = mediaSourcePattern.matcher(html)
                var mediaCount = 0
                while (mediaMatcher.find() && mediaCount < 25) {
                    val rawSrc = unescapeJsonUrl(mediaMatcher.group(1) ?: continue)
                    val fullUrl = resolveRelativeUrl(url, rawSrc)
                    if (isValidMediaUrl(fullUrl) && seenUrls.add(fullUrl)) {
                        val fileName = extractFileNameFromUrl(fullUrl, "media_${mediaCount + 1}.mp4")
                        val ext = fileName.substringAfterLast(".", "mp4").lowercase()
                        val type = classifyExtension(ext)
                        foundLinks.add(
                            GrabbedLink(
                                url = fullUrl,
                                title = "Medio embebido: $fileName",
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

                // 3. Anchor download links <a href="...">
                val anchorPattern = Pattern.compile("(?i)<a[^>]+href=[\"']?([^\"'>\\s]+)[\"']?")
                val anchorMatcher = anchorPattern.matcher(html)
                var fileCount = 0
                while (anchorMatcher.find() && fileCount < 30) {
                    val rawHref = unescapeJsonUrl(anchorMatcher.group(1) ?: continue)
                    val fullUrl = resolveRelativeUrl(url, rawHref)
                    val ext = fullUrl.substringAfterLast(".", "").lowercase().substringBefore("?").substringBefore("#")
                    if (ext in listOf("mp4", "mkv", "webm", "avi", "mp3", "flac", "pdf", "zip", "rar", "7z", "iso", "doc", "docx", "xls", "xlsx", "apk", "jpg", "jpeg", "png", "webp", "gif")) {
                        if (isValidMediaUrl(fullUrl) && seenUrls.add(fullUrl)) {
                            val fileName = extractFileNameFromUrl(fullUrl, "archivo_${fileCount + 1}.$ext")
                            val type = classifyExtension(ext)
                            foundLinks.add(
                                GrabbedLink(
                                    url = fullUrl,
                                    title = "Archivo descargable: $fileName",
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

                // 4. Images <img src="...">
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
