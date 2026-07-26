package com.codex.sonyedge.ui.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import com.codex.sonyedge.CameraContentItem
import com.codex.sonyedge.CameraWifiBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 相机图片三级加载与缓存（内存 LruCache 128MiB / 磁盘 512MiB / 7 天 TTL）。
 * 从 SonyEdgeUi.kt 原样迁出，行为不变。
 */
object CameraImageLoader {

    private const val DISK_CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1000L
    private const val DISK_CACHE_CLEANUP_INTERVAL_MS = 12L * 60L * 60L * 1000L
    private const val DISK_CACHE_MAX_BYTES = 512L * 1024L * 1024L
    private val diskCacheCleanupLock = Any()
    private var lastDiskCacheCleanupAt = 0L

    private val memoryCache = object : LruCache<String, Bitmap>(128 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun cachedBitmap(url: String?, maxDimension: Int, trimLetterbox: Boolean = false): Bitmap? =
        memoryCache.get(memoryKey(url.orEmpty(), maxDimension, trimLetterbox))

    private fun memoryKey(url: String, maxDimension: Int, trimLetterbox: Boolean): String =
        if (trimLetterbox) "$url@$maxDimension@trim" else "$url@$maxDimension"

    /**
     * [trimLetterbox]：解码后裁掉图片自带的纯黑 letterbox 边（相机缩略图常见），
     * 供网格/胶片条 Crop 铺满使用；磁盘缓存仍存原始字节，两种形态共享。
     */
    suspend fun loadBitmap(
        context: Context,
        url: String,
        maxDimension: Int,
        trimLetterbox: Boolean = false
    ): Bitmap? =
        withContext(Dispatchers.IO) {
            if (url.isBlank()) return@withContext null
            cleanupExpiredCache(context)
            val memoryKey = memoryKey(url, maxDimension, trimLetterbox)
            memoryCache.get(memoryKey)?.let { return@withContext it }
            val diskFile = cacheFile(context, url)
            if (diskFile.isFile && diskFile.length() > 0) {
                decodeSampledBitmap(diskFile.readBytes(), maxDimension, trimLetterbox)?.let { bitmap ->
                    runCatching { diskFile.setLastModified(System.currentTimeMillis()) }
                    memoryCache.put(memoryKey, bitmap)
                    return@withContext bitmap
                }
            }
            var wifiLease: CameraWifiBinding.Lease? = null
            var connection: HttpURLConnection? = null
            try {
                wifiLease = CameraWifiBinding.acquire(context)
                connection = wifiLease.network.openConnection(URL(url)) as HttpURLConnection
                connection.connectTimeout = 3500
                connection.readTimeout = 9000
                connection.setRequestProperty("Accept", "image/*,*/*")
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    Log.w("SonyEdge-Preview", "HTTP $responseCode from ${url.substringBefore('?')}")
                    return@withContext null
                }
                val bytes = connection.inputStream.use { it.readBytes() }
                Log.d("SonyEdge-Preview", "Loaded ${bytes.size} bytes from ${url.substringBefore('?')}")
                if (bytes.isNotEmpty()) {
                    runCatching {
                        diskFile.parentFile?.mkdirs()
                        diskFile.writeBytes(bytes)
                    }
                }
                val bitmap = decodeSampledBitmap(bytes, maxDimension, trimLetterbox)
                if (bitmap != null) memoryCache.put(memoryKey, bitmap)
                bitmap
            } catch (exception: Exception) {
                Log.w(
                    "SonyEdge-Preview",
                    "Failed to load ${url.substringBefore('?')}: ${exception.javaClass.simpleName}: ${exception.message}"
                )
                null
            } finally {
                connection?.disconnect()
                wifiLease?.close()
            }
        }

    fun cleanupExpiredCache(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        synchronized(diskCacheCleanupLock) {
            if (!force && now - lastDiskCacheCleanupAt < DISK_CACHE_CLEANUP_INTERVAL_MS) return
            lastDiskCacheCleanupAt = now
        }
        val cacheDir = File(context.cacheDir, "sonyedge-image-cache")
        val expiresBefore = now - DISK_CACHE_TTL_MS
        runCatching {
            val files = cacheDir.listFiles()
                ?.filter { it.isFile && it.length() > 0 }
                .orEmpty()
            files.forEach { file ->
                if (file.isFile && file.lastModified() in 1 until expiresBefore) {
                    file.delete()
                }
            }
            var totalBytes = cacheDir.listFiles()
                ?.filter { it.isFile && it.length() > 0 }
                ?.sumOf { it.length() }
                ?: 0L
            if (totalBytes > DISK_CACHE_MAX_BYTES) {
                cacheDir.listFiles()
                    ?.filter { it.isFile && it.length() > 0 }
                    ?.sortedBy { it.lastModified() }
                    ?.forEach { file ->
                        if (totalBytes <= DISK_CACHE_MAX_BYTES) return@forEach
                        val fileBytes = file.length()
                        if (file.delete()) totalBytes -= fileBytes
                    }
            }
        }
    }

    /** 设置页「清除缩略图缓存」：清空内存与磁盘缓存。 */
    suspend fun clearAllCaches(context: Context): Unit = withContext(Dispatchers.IO) {
        memoryCache.evictAll()
        runCatching {
            File(context.cacheDir, "sonyedge-image-cache").listFiles()?.forEach { it.delete() }
        }
    }

    private fun cacheFile(context: Context, url: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        val name = digest.joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, "sonyedge-image-cache"), "$name.img")
    }

    private fun decodeSampledBitmap(
        bytes: ByteArray,
        maxDimension: Int,
        trimLetterbox: Boolean = false
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val oriented = applyExifOrientation(bytes, decoded)
        return if (trimLetterbox) trimLetterboxBars(oriented) else oriented
    }

    /**
     * 裁掉四周近乎纯黑的 letterbox 边。仅当整行/整列几乎全黑（亮度 ≤20，允许 1 个采样点例外）
     * 才视为黑边；每侧最多裁 1/3，避免误裁夜景等真实暗部内容。
     */
    private fun trimLetterboxBars(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        if (width < 16 || height < 16) return source

        fun isDark(pixel: Int): Boolean {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            return r <= 20 && g <= 20 && b <= 20
        }

        fun rowIsBlack(y: Int): Boolean {
            val step = (width / 24).coerceAtLeast(1)
            var dark = 0
            var count = 0
            var x = 0
            while (x < width) {
                if (isDark(source.getPixel(x, y))) dark++
                count++
                x += step
            }
            return dark >= count - 1
        }

        fun colIsBlack(x: Int): Boolean {
            val step = (height / 24).coerceAtLeast(1)
            var dark = 0
            var count = 0
            var y = 0
            while (y < height) {
                if (isDark(source.getPixel(x, y))) dark++
                count++
                y += step
            }
            return dark >= count - 1
        }

        val maxTrimY = height / 3
        val maxTrimX = width / 3
        var top = 0
        while (top < maxTrimY && rowIsBlack(top)) top++
        var bottom = height - 1
        while (height - 1 - bottom < maxTrimY && bottom > top && rowIsBlack(bottom)) bottom--
        var left = 0
        while (left < maxTrimX && colIsBlack(left)) left++
        var right = width - 1
        while (width - 1 - right < maxTrimX && right > left && colIsBlack(right)) right--

        if (top == 0 && bottom == height - 1 && left == 0 && right == width - 1) return source
        val newWidth = right - left + 1
        val newHeight = bottom - top + 1
        if (newWidth < width / 2 && newHeight < height / 2) return source
        return runCatching {
            Bitmap.createBitmap(source, left, top, newWidth, newHeight).also { trimmed ->
                if (trimmed != source) source.recycle()
            }
        }.getOrElse { source }
    }

    private fun applyExifOrientation(bytes: ByteArray, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { rotated ->
                if (rotated != bitmap) bitmap.recycle()
            }
        }.getOrElse { bitmap }
    }
}

fun previewPrimaryUrl(item: CameraContentItem): String {
    val large = item.fullPreviewUrl().orEmpty()
    if (isDecodableStillUrl(large)) return large
    return previewFallbackUrl(item)
}

fun previewFallbackUrl(item: CameraContentItem): String {
    val thumbnail = item.previewThumbnailUrl().orEmpty()
    if (isDecodableStillUrl(thumbnail)) return thumbnail
    return ""
}

fun isDecodableStillUrl(url: String): Boolean {
    if (url.isBlank()) return false
    val lower = url.lowercase()
    return lower.contains(".jpg") ||
        lower.contains(".jpeg") ||
        lower.contains("%2fjpeg") ||
        lower.contains("image/jpeg") ||
        lower.contains("image%2fjpeg")
}

fun isVideoItem(item: CameraContentItem): Boolean {
    val title = item.title.lowercase()
    val kind = item.contentKind.lowercase()
    return title.endsWith(".mp4") || title.endsWith(".mov") || kind.contains("video") || kind.contains("movie")
}
