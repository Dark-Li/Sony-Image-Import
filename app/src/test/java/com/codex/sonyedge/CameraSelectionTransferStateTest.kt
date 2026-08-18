package com.codex.sonyedge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraSelectionTransferStateTest {
    @Test
    fun activeDownloadIsNotTerminal() {
        assertFalse(
            isCameraSelectionTransferTerminal(
                SonyEdgeUiState(downloadState = DownloadService.STATE_FILE_PROGRESS)
            )
        )
    }

    @Test
    fun queuedDownloadRemainsActive() {
        assertTrue(
            isDownloadTransferActive(
                SonyEdgeUiState(queuedTransferCount = 1)
            )
        )
    }

    @Test
    fun completedDownloadIsTerminal() {
        assertTrue(
            isCameraSelectionTransferTerminal(
                SonyEdgeUiState(downloadState = DownloadService.STATE_DONE)
            )
        )
    }

    @Test
    fun cancelledOrFatalDownloadIsTerminal() {
        assertTrue(
            isCameraSelectionTransferTerminal(
                SonyEdgeUiState(downloadState = DownloadService.STATE_CANCELLED)
            )
        )
        assertTrue(
            isCameraSelectionTransferTerminal(
                SonyEdgeUiState(downloadState = DownloadService.STATE_FATAL)
            )
        )
    }
}
