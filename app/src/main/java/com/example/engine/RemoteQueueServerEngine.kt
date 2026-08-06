package com.example.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RemoteLogMessage(
    val timestamp: Long = System.currentTimeMillis(),
    val clientIp: String,
    val action: String,
    val details: String
)

class RemoteQueueServerEngine {
    private val _isServerRunning = MutableStateFlow(true)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _remoteLogs = MutableStateFlow<List<RemoteLogMessage>>(
        listOf(
            RemoteLogMessage(
                clientIp = "192.168.1.105 (Navegador Chrome Extension v2.4)",
                action = "PAIR_SUCCESS",
                details = "Extensión conectada mediante PIN de seguridad"
            ),
            RemoteLogMessage(
                clientIp = "192.168.1.105",
                action = "INSPECT_URL",
                details = "Examinado enlace desde extensión: instagram.com/profile"
            )
        )
    )
    val remoteLogs: StateFlow<List<RemoteLogMessage>> = _remoteLogs.asStateFlow()

    fun toggleServer(enable: Boolean) {
        _isServerRunning.value = enable
    }

    fun addRemoteLog(clientIp: String, action: String, details: String) {
        val newLog = RemoteLogMessage(clientIp = clientIp, action = action, details = details)
        _remoteLogs.value = listOf(newLog) + _remoteLogs.value.take(20)
    }
}
