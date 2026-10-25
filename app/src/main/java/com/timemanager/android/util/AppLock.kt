package com.timemanager.android.util

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.io.File
import java.nio.channels.FileLock

/**
 * Single-instance lock placeholder — Android uses single APK process; this mimics desktop lockfile
 * for debugging / testing. Real single-instance is enforced by Android itself.
 */
class AppLock(private val context: Context) {
    private var channel: java.nio.channels.FileChannel? = null
    private var lock: FileLock? = null

    fun tryAcquire(): Boolean = try {
        val f = File(context.filesDir, "timemanager.lock")
        channel = java.io.RandomAccessFile(f, "rw").channel
        lock = channel?.tryLock()
        lock != null
    } catch (_: Exception) { false }

    fun release() {
        try { lock?.release() } catch (_: Exception) {}
        try { channel?.close() } catch (_: Exception) {}
    }
}
