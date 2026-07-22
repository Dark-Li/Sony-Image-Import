package com.codex.sonyedge

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/** Owns an XPush session until it is atomically handed to DownloadService or aborted. */
class XPushSessionGuard(
    context: Context,
    val client: XPushListClient
) {
    private enum class State {
        Pending,
        Aborting,
        HandedOff,
        Ended
    }

    private val appContext = context.applicationContext
    private var state = State.Pending

    fun handoffToService(startService: () -> Unit): Boolean = synchronized(this) {
        if (state != State.Pending) return@synchronized false
        startService()
        state = State.HandedOff
        true
    }

    fun abort(reason: String, log: (String) -> Unit): Boolean {
        synchronized(this) {
            if (state != State.Pending) return false
            state = State.Aborting
        }

        var lease: CameraWifiBinding.Lease? = null
        return try {
            lease = CameraWifiBinding.acquire(appContext)
            client.transferEnd(1)
            synchronized(this) { state = State.Ended }
            log("XPush session aborted: $reason")
            true
        } catch (ex: Exception) {
            synchronized(this) {
                if (state == State.Aborting) state = State.Pending
            }
            log("XPush abort failed ($reason): ${ex.message}")
            false
        } finally {
            lease?.close()
        }
    }

    fun needsAbort(): Boolean = synchronized(this) {
        state == State.Pending || state == State.Aborting
    }
}

/** Keeps failed XPush cleanup sessions owned after their ViewModel has been destroyed. */
object XPushCleanupCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scheduled = Collections.newSetFromMap(
        ConcurrentHashMap<XPushSessionGuard, Boolean>()
    )
    private val retryDelaysMs = longArrayOf(0, 500, 1_000, 2_000, 4_000, 8_000)

    fun schedule(guard: XPushSessionGuard, reason: String, log: (String) -> Unit) {
        if (!scheduled.add(guard)) return
        scope.launch {
            try {
                for ((attempt, retryDelay) in retryDelaysMs.withIndex()) {
                    if (retryDelay > 0) delay(retryDelay)
                    if (!guard.needsAbort()) return@launch
                    if (guard.abort(reason, log)) return@launch
                    log("XPush cleanup will retry after attempt ${attempt + 1}/${retryDelaysMs.size}")
                }
                if (guard.needsAbort()) {
                    log("XPush cleanup exhausted retries; camera session must expire on the camera")
                }
            } finally {
                scheduled.remove(guard)
            }
        }
    }
}
