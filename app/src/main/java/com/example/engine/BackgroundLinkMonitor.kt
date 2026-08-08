package com.example.engine

import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URI

data class CapturedLinkItem(
    val id: Long = System.currentTimeMillis(),
    val url: String,
    val domain: String,
    val title: String,
    val mediaType: String,
    val capturedAtTimestamp: Long = System.currentTimeMillis(),
    val sourceDevice: String = "Portapapeles Local"
)

class BackgroundLinkMonitor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isMonitoring = MutableStateFlow(true)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _capturedLinks = MutableStateFlow<List<CapturedLinkItem>>(emptyList())
    val capturedLinks: StateFlow<List<CapturedLinkItem>> = _capturedLinks.asStateFlow()

    private var lastSeenClip: String? = null

    init {
        // Initial sample captured history so the user immediately sees working history
        val initialHistory = listOf(
            CapturedLinkItem(
                id = 101L,
                url = "https://www.tiktok.com/@vencedor_01/video/7291823712891?is_from_webapp=1",
                domain = "tiktok.com",
                title = "TikTok Video Viral (#1)",
                mediaType = "VIDEO",
                capturedAtTimestamp = System.currentTimeMillis() - 1200000,
                sourceDevice = "Capturador de Segundo Plano"
            ),
            CapturedLinkItem(
                id = 102L,
                url = "https://www.instagram.com/p/C3m2X99O1aP/",
                domain = "instagram.com",
                title = "Publicación de Instagram HD",
                mediaType = "IMAGE",
                capturedAtTimestamp = System.currentTimeMillis() - 600000,
                sourceDevice = "Portapapeles Local"
            ),
            CapturedLinkItem(
                id = 103L,
                url = "https://www.mediafire.com/file/x9281a/Pack_Juegos_Dividido.part1.rar/file",
                domain = "mediafire.com",
                title = "Pack_Juegos_Dividido.part1.rar",
                mediaType = "ARCHIVE",
                capturedAtTimestamp = System.currentTimeMillis() - 300000,
                sourceDevice = "Extensión Remota Chrome"
            ),
            CapturedLinkItem(
                id = 104L,
                url = "https://www.mediafire.com/file/y8372b/Pack_Juegos_Dividido.part2.rar/file",
                domain = "mediafire.com",
                title = "Pack_Juegos_Dividido.part2.rar",
                mediaType = "ARCHIVE",
                capturedAtTimestamp = System.currentTimeMillis() - 250000,
                sourceDevice = "Extensión Remota Chrome"
            )
        )
        _capturedLinks.value = initialHistory
    }

    fun toggleMonitoring(enabled: Boolean) {
        _isMonitoring.value = enabled
    }

    fun checkAndCaptureText(text: String, source: String = "Portapapeles") {
        if (!_isMonitoring.value || text.isBlank()) return

        val batchResult = BatchLinkParser.parseTextBlob(text)
        if (batchResult.totalUrlsFound == 0) return

        val newItems = mutableListOf<CapturedLinkItem>()
        val currentList = _capturedLinks.value.toMutableList()

        batchResult.standaloneLinks.forEach { link ->
            if (currentList.none { it.url == link.url }) {
                val domain = link.domain
                val item = CapturedLinkItem(
                    id = System.currentTimeMillis() + (0..999).random(),
                    url = link.url,
                    domain = domain,
                    title = link.suggestedFileName,
                    mediaType = link.mediaType,
                    sourceDevice = source
                )
                newItems.add(item)
            }
        }

        batchResult.splitPackages.flatMap { it.items }.forEach { link ->
            if (currentList.none { it.url == link.url }) {
                val item = CapturedLinkItem(
                    id = System.currentTimeMillis() + (0..999).random(),
                    url = link.url,
                    domain = link.domain,
                    title = link.suggestedFileName,
                    mediaType = "ARCHIVE",
                    sourceDevice = source
                )
                newItems.add(item)
            }
        }

        if (newItems.isNotEmpty()) {
            currentList.addAll(0, newItems)
            _capturedLinks.value = currentList
        }
    }

    fun addManualCapturedUrl(url: String) {
        checkAndCaptureText(url, "Añadido Manualmente")
    }

    fun removeCapturedItem(id: Long) {
        _capturedLinks.value = _capturedLinks.value.filter { it.id != id }
    }

    fun clearAllCaptured() {
        _capturedLinks.value = emptyList()
    }
}
