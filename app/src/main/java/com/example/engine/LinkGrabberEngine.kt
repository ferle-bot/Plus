package com.example.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
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

        // 1. Priority check for TikTok
        if (domain.contains("tiktok.com") || formattedUrl.contains("tiktok.com")) {
            val tiktokResult = handleTikTokSniffing(formattedUrl, domain)
            if (tiktokResult.links.isNotEmpty()) {
                return@withContext tiktokResult
            }
        }

        // 2. Priority check for Instagram
        if (domain.contains("instagram.com") || domain.contains("instagr.am") || formattedUrl.contains("/reel/") || formattedUrl.contains("/p/")) {
            val instagramResult = handleInstagramSniffing(formattedUrl, domain)
            if (instagramResult.links.isNotEmpty()) {
                return@withContext instagramResult
            }
        }

        // 3. Try Cobalt Universal API for YouTube, Twitter, Pinterest, etc.
        val cobaltResult = tryCobaltExtractor(formattedUrl, domain)
        if (cobaltResult != null && cobaltResult.links.isNotEmpty()) {
            return@withContext cobaltResult
        }

        // 4. Twitter / X specific handler
        if (domain.contains("twitter.com") || domain.contains("x.com")) {
            val twitterResult = handleTwitterSniffing(formattedUrl, domain)
            if (twitterResult.links.isNotEmpty()) {
                return@withContext twitterResult
            }
        }

        // 5. Pinterest specific handler
        if (domain.contains("pinterest.com") || domain.contains("pin.it")) {
            val pinterestResult = handlePinterestSniffing(formattedUrl, domain)
            if (pinterestResult.links.isNotEmpty()) {
                return@withContext pinterestResult
            }
        }

        // 6. Fallback: General JDownloader-style Web Sniffer
        return@withContext performRealWebSniffing(formattedUrl, domain)
    }

    /**
     * TikTok Dedicated Multi-Strategy Extractor
     */
    private fun handleTikTokSniffing(rawUrl: String, domain: String): WebGrabResult {
        val expandedUrl = expandShortenedUrl(rawUrl)
        val links = mutableListOf<GrabbedLink>()

        // Strategy A: TikWM API
        val tikWmLinks = tryTikWmApi(expandedUrl, domain)
        if (tikWmLinks.isNotEmpty()) {
            return WebGrabResult(
                pageTitle = "TikTok Media",
                sourceUrl = rawUrl,
                domain = domain,
                totalLinksFound = tikWmLinks.size,
                links = tikWmLinks
            )
        }

        // Strategy B: TiklyDown API
        val tiklyLinks = tryTiklyDownApi(expandedUrl, domain)
        if (tiklyLinks.isNotEmpty()) {
            return WebGrabResult(
                pageTitle = "TikTok Media",
                sourceUrl = rawUrl,
                domain = domain,
                totalLinksFound = tiklyLinks.size,
                links = tiklyLinks
            )
        }

        // Strategy C: Cobalt API
        val cobaltResult = tryCobaltExtractor(expandedUrl, domain)
        if (cobaltResult != null && cobaltResult.links.isNotEmpty()) {
            return cobaltResult
        }

        // Strategy D: TikTok oEmbed + Web Page Sniffing
        val webLinks = fetchWithBotUserAgent(expandedUrl, rawUrl, domain, mutableSetOf(), "TikTok Video")
        if (webLinks.isNotEmpty()) {
            return WebGrabResult(
                pageTitle = "TikTok Media",
                sourceUrl = rawUrl,
                domain = domain,
                totalLinksFound = webLinks.size,
                links = webLinks
            )
        }

        return WebGrabResult(
            pageTitle = domain,
            sourceUrl = rawUrl,
            domain = domain,
            totalLinksFound = 0,
            links = emptyList(),
            errorMessage = "No se pudo extraer el video o fotos de TikTok. Verifica que la publicación sea pública y el enlace sea correcto."
        )
    }

    private fun tryTikWmApi(url: String, domain: String): List<GrabbedLink> {
        val links = mutableListOf<GrabbedLink>()
        try {
            val formMediaType = "application/x-www-form-urlencoded; charset=utf-8".toMediaType()
            val body = "url=${URLEncoder.encode(url, "UTF-8")}&hd=1".toRequestBody(formMediaType)
            val request = Request.Builder()
                .url("https://www.tikwm.com/api/")
                .post(body)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val jsonStr = response.body?.string().orEmpty()
                val json = JSONObject(jsonStr)
                if (json.optInt("code", -1) == 0 && json.has("data")) {
                    val data = json.getJSONObject("data")
                    val title = data.optString("title", "TikTok Video")
                    val cover = data.optString("cover", null)

                    // Check if it's a photo slideshow
                    if (data.has("images")) {
                        val imagesArr = data.getJSONArray("images")
                        for (i in 0 until imagesArr.length()) {
                            val imgUrl = imagesArr.getString(i)
                            if (isValidMediaUrl(imgUrl)) {
                                links.add(
                                    GrabbedLink(
                                        url = imgUrl,
                                        title = "$title - Foto ${i + 1}",
                                        suggestedFileName = "tiktok_photo_${System.currentTimeMillis()}_${i + 1}.jpg",
                                        mediaType = "IMAGE",
                                        estimatedSizeBytes = 0L,
                                        thumbnailUrl = imgUrl,
                                        dimensions = "HD Photo",
                                        sourcePageUrl = url,
                                        domain = domain
                                    )
                                )
                            }
                        }
                    }

                    // Video (play / hdplay)
                    val playUrl = if (data.has("hdplay") && data.getString("hdplay").isNotBlank()) {
                        data.getString("hdplay")
                    } else if (data.has("play")) {
                        data.getString("play")
                    } else ""

                    if (playUrl.isNotBlank()) {
                        val fullVidUrl = if (playUrl.startsWith("//")) "https:$playUrl" else playUrl
                        if (isValidMediaUrl(fullVidUrl)) {
                            links.add(
                                GrabbedLink(
                                    url = fullVidUrl,
                                    title = title,
                                    suggestedFileName = "tiktok_${System.currentTimeMillis()}.mp4",
                                    mediaType = "VIDEO",
                                    estimatedSizeBytes = 0L,
                                    thumbnailUrl = cover,
                                    dimensions = "1080p Sin Marca de Agua",
                                    sourcePageUrl = url,
                                    domain = domain
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return links
    }

    private fun tryTiklyDownApi(url: String, domain: String): List<GrabbedLink> {
        val links = mutableListOf<GrabbedLink>()
        try {
            val request = Request.Builder()
                .url("https://api.tiklydown.eu.org/api/download?url=${URLEncoder.encode(url, "UTF-8")}")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val jsonStr = response.body?.string().orEmpty()
                val json = JSONObject(jsonStr)
                val title = json.optString("title", "TikTok Video")

                if (json.has("video")) {
                    val videoObj = json.getJSONObject("video")
                    val videoUrl = videoObj.optString("noWatermark", videoObj.optString("watermark", ""))
                    if (isValidMediaUrl(videoUrl)) {
                        links.add(
                            GrabbedLink(
                                url = videoUrl,
                                title = title,
                                suggestedFileName = "tiktok_${System.currentTimeMillis()}.mp4",
                                mediaType = "VIDEO",
                                estimatedSizeBytes = 0L,
                                thumbnailUrl = null,
                                dimensions = "HD No Watermark",
                                sourcePageUrl = url,
                                domain = domain
                            )
                        )
                    }
                }

                if (json.has("images")) {
                    val imgArr = json.getJSONArray("images")
                    for (i in 0 until imgArr.length()) {
                        val imgObj = imgArr.getJSONObject(i)
                        val imgUrl = imgObj.optString("url", "")
                        if (isValidMediaUrl(imgUrl)) {
                            links.add(
                                GrabbedLink(
                                    url = imgUrl,
                                    title = "$title - Imagen ${i + 1}",
                                    suggestedFileName = "tiktok_img_${i + 1}.jpg",
                                    mediaType = "IMAGE",
                                    estimatedSizeBytes = 0L,
                                    thumbnailUrl = imgUrl,
                                    dimensions = "Alta Calidad",
                                    sourcePageUrl = url,
                                    domain = domain
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return links
    }

    /**
     * Instagram Dedicated Multi-Strategy Extractor
     */
    private fun handleInstagramSniffing(url: String, domain: String): WebGrabResult {
        val shortcodePattern = Pattern.compile("(?:/p/|/reel/|/reels/|/tv/)([A-Za-z0-9_-]+)")
        val matcher = shortcodePattern.matcher(url)
        val shortcode = if (matcher.find()) matcher.group(1) else null

        if (shortcode != null) {
            // Strategy 1: Instagram Internal API v1 by Shortcode
            val igApiLinks = tryInstagramInternalApi(shortcode, url, domain)
            if (igApiLinks.isNotEmpty()) {
                return WebGrabResult(
                    pageTitle = "Publicación de Instagram ($shortcode)",
                    sourceUrl = url,
                    domain = domain,
                    totalLinksFound = igApiLinks.size,
                    links = igApiLinks
                )
            }

            // Strategy 2: Instagram Embed Scraper
            val embedLinks = tryInstagramEmbedScraper(shortcode, url, domain)
            if (embedLinks.isNotEmpty()) {
                return WebGrabResult(
                    pageTitle = "Publicación de Instagram ($shortcode)",
                    sourceUrl = url,
                    domain = domain,
                    totalLinksFound = embedLinks.size,
                    links = embedLinks
                )
            }

            // Strategy 3: DDInstagram & VxInstagram Proxies
            val ddUrl = "https://ddinstagram.com/p/$shortcode/"
            val ddResults = fetchWithBotUserAgent(ddUrl, url, domain, mutableSetOf(), "Instagram ($shortcode)")
            if (ddResults.isNotEmpty()) {
                return WebGrabResult(
                    pageTitle = "Publicación de Instagram ($shortcode)",
                    sourceUrl = url,
                    domain = domain,
                    totalLinksFound = ddResults.size,
                    links = ddResults
                )
            }

            val vxUrl = "https://vxinstagram.com/p/$shortcode/"
            val vxResults = fetchWithBotUserAgent(vxUrl, url, domain, mutableSetOf(), "Instagram ($shortcode)")
            if (vxResults.isNotEmpty()) {
                return WebGrabResult(
                    pageTitle = "Publicación de Instagram ($shortcode)",
                    sourceUrl = url,
                    domain = domain,
                    totalLinksFound = vxResults.size,
                    links = vxResults
                )
            }

            // Strategy 4: Cobalt Extractor
            val cobaltResult = tryCobaltExtractor(url, domain)
            if (cobaltResult != null && cobaltResult.links.isNotEmpty()) {
                return cobaltResult
            }
        }

        return WebGrabResult(
            pageTitle = domain,
            sourceUrl = url,
            domain = domain,
            totalLinksFound = 0,
            links = emptyList(),
            errorMessage = "No se pudo extraer el contenido de Instagram. Verifica que la publicación sea pública (Reel, Foto o Carousel) y vuelve a intentarlo."
        )
    }

    private fun tryInstagramInternalApi(shortcode: String, sourceUrl: String, domain: String): List<GrabbedLink> {
        val links = mutableListOf<GrabbedLink>()
        try {
            val apiUrl = "https://www.instagram.com/api/v1/media/by/shortcode/$shortcode/"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Instagram 275.0.0.27.98 Android (30/11; 320dpi; 720x1280; Xiaomi; Redmi 9A; dandelion; mt6762; es_US; 314665272)")
                .header("X-IG-App-ID", "936619743392459")
                .header("Accept", "*/*")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val jsonStr = response.body?.string().orEmpty()
                if (jsonStr.isBlank() || !jsonStr.contains("items")) return emptyList()

                val json = JSONObject(jsonStr)
                if (json.has("items")) {
                    val items = json.getJSONArray("items")
                    if (items.length() > 0) {
                        val item = items.getJSONObject(0)
                        parseInstagramItemObject(item, links, sourceUrl, domain)
                    }
                }
            }
        } catch (_: Exception) {}
        return links
    }

    private fun parseInstagramItemObject(item: JSONObject, links: MutableList<GrabbedLink>, sourceUrl: String, domain: String) {
        val captionText = if (item.has("caption") && !item.isNull("caption")) {
            item.getJSONObject("caption").optString("text", "Instagram Media")
        } else "Instagram Media"

        // Carousel posts
        if (item.has("carousel_media")) {
            val carousel = item.getJSONArray("carousel_media")
            for (i in 0 until carousel.length()) {
                val carItem = carousel.getJSONObject(i)
                parseSingleInstagramMedia(carItem, links, "$captionText (Slide ${i + 1})", sourceUrl, domain)
            }
        } else {
            parseSingleInstagramMedia(item, links, captionText, sourceUrl, domain)
        }
    }

    private fun parseSingleInstagramMedia(item: JSONObject, links: MutableList<GrabbedLink>, title: String, sourceUrl: String, domain: String) {
        // Video check
        if (item.has("video_versions")) {
            val videos = item.getJSONArray("video_versions")
            if (videos.length() > 0) {
                val bestVid = videos.getJSONObject(0)
                val vidUrl = bestVid.optString("url", "")
                if (isValidMediaUrl(vidUrl)) {
                    links.add(
                        GrabbedLink(
                            url = vidUrl,
                            title = title,
                            suggestedFileName = "instagram_video_${System.currentTimeMillis()}_${links.size + 1}.mp4",
                            mediaType = "VIDEO",
                            estimatedSizeBytes = 0L,
                            thumbnailUrl = null,
                            dimensions = "1080p HD Video",
                            sourcePageUrl = sourceUrl,
                            domain = domain
                        )
                    )
                    return
                }
            }
        }

        // Image check
        if (item.has("image_versions2")) {
            val imgObj = item.getJSONObject("image_versions2")
            if (imgObj.has("candidates")) {
                val candidates = imgObj.getJSONArray("candidates")
                if (candidates.length() > 0) {
                    val bestImg = candidates.getJSONObject(0)
                    val imgUrl = bestImg.optString("url", "")
                    if (isValidMediaUrl(imgUrl)) {
                        links.add(
                            GrabbedLink(
                                url = imgUrl,
                                title = title,
                                suggestedFileName = "instagram_photo_${System.currentTimeMillis()}_${links.size + 1}.jpg",
                                mediaType = "IMAGE",
                                estimatedSizeBytes = 0L,
                                thumbnailUrl = imgUrl,
                                dimensions = "Alta Resolución",
                                sourcePageUrl = sourceUrl,
                                domain = domain
                            )
                        )
                    }
                }
            }
        }
    }

    private fun tryInstagramEmbedScraper(shortcode: String, sourceUrl: String, domain: String): List<GrabbedLink> {
        val links = mutableListOf<GrabbedLink>()
        try {
            val embedUrl = "https://www.instagram.com/p/$shortcode/embed/captioned/"
            val request = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val html = response.body?.string().orEmpty()

                // Look for Video <video src="..."> or "video_url":"..."
                val videoPattern = Pattern.compile("(?i)<video[^>]+src=[\"']([^\"']+)[\"']")
                val vidMatcher = videoPattern.matcher(html)
                while (vidMatcher.find()) {
                    val rawUrl = unescapeJsonUrl(vidMatcher.group(1).orEmpty())
                    if (isValidMediaUrl(rawUrl)) {
                        links.add(
                            GrabbedLink(
                                url = rawUrl,
                                title = "Instagram Reel / Video HD",
                                suggestedFileName = "instagram_$shortcode.mp4",
                                mediaType = "VIDEO",
                                estimatedSizeBytes = 0L,
                                thumbnailUrl = null,
                                dimensions = "HD Video",
                                sourcePageUrl = sourceUrl,
                                domain = domain
                            )
                        )
                    }
                }

                if (links.isEmpty()) {
                    val jsonVidPattern = Pattern.compile("\"video_url\"\\s*:\\s*\"([^\"]+)\"")
                    val jsonVidMatcher = jsonVidPattern.matcher(html)
                    while (jsonVidMatcher.find()) {
                        val rawUrl = unescapeJsonUrl(jsonVidMatcher.group(1).orEmpty())
                        if (isValidMediaUrl(rawUrl)) {
                            links.add(
                                GrabbedLink(
                                    url = rawUrl,
                                    title = "Instagram Video HD",
                                    suggestedFileName = "instagram_$shortcode.mp4",
                                    mediaType = "VIDEO",
                                    estimatedSizeBytes = 0L,
                                    thumbnailUrl = null,
                                    dimensions = "HD",
                                    sourcePageUrl = sourceUrl,
                                    domain = domain
                                )
                            )
                        }
                    }
                }

                // Look for Image <img class="EmbeddedMediaImage" src="..."> or display_url
                val imgPattern = Pattern.compile("(?i)<img[^>]+class=[\"'][^\"']*EmbeddedMediaImage[^\"']*[\"'][^>]+src=[\"']([^\"']+)[\"']")
                val imgMatcher = imgPattern.matcher(html)
                while (imgMatcher.find()) {
                    val rawUrl = unescapeJsonUrl(imgMatcher.group(1).orEmpty())
                    if (isValidMediaUrl(rawUrl)) {
                        links.add(
                            GrabbedLink(
                                url = rawUrl,
                                title = "Instagram Photo HD",
                                suggestedFileName = "instagram_$shortcode.jpg",
                                mediaType = "IMAGE",
                                estimatedSizeBytes = 0L,
                                thumbnailUrl = rawUrl,
                                dimensions = "Alta Calidad",
                                sourcePageUrl = sourceUrl,
                                domain = domain
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        return links
    }

    /**
     * Cobalt Universal Extractor
     */
    private fun tryCobaltExtractor(url: String, domain: String): WebGrabResult? {
        val cobaltEndpoints = listOf(
            "https://api.cobalt.tools/",
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
                    if (json.isBlank()) return@use

                    val links = mutableListOf<GrabbedLink>()

                    // Status "picker" (Multiple items)
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

                    // Single item
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
     * Twitter / X Sniffer
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
     * HTML Fetching with Social Bot User-Agent (TelegramBot / OpenGraph)
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
            }
        } catch (_: Exception) {}
        return results
    }

    /**
     * General JDownloader-style Web Sniffer
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

                val html = response.body?.string().orEmpty()
                val pageTitle = extractTitleFromHtml(html) ?: domain

                val foundLinks = mutableListOf<GrabbedLink>()
                val seenUrls = mutableSetOf<String>()

                // og:image / og:video
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

                // Media tags <video>, <audio>, <source>
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

                // Download links <a href="...">
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

    private fun expandShortenedUrl(url: String): String {
        return try {
            val request = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            client.newCall(request).execute().use { response ->
                response.request.url.toString()
            }
        } catch (_: Exception) {
            url
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
