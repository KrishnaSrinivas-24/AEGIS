package com.krishna.aegis.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    object Completed : DownloadState()
    data class Failed(val error: String) : DownloadState()
}

object ModelDownloadManager {
    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    fun updateProgress(progressPercent: Int, bytesDownloaded: Long, totalBytes: Long) {
        _state.value = DownloadState.Downloading(progressPercent, bytesDownloaded, totalBytes)
    }

    fun complete() {
        _state.value = DownloadState.Completed
    }

    fun fail(error: String) {
        _state.value = DownloadState.Failed(error)
    }

    fun reset() {
        _state.value = DownloadState.Idle
    }
}
