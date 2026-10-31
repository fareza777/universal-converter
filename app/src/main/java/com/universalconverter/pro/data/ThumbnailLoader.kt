package com.universalconverter.pro.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.universalconverter.pro.core.engine.ImageEngine
import com.universalconverter.pro.core.formats.FormatRegistry
import com.universalconverter.pro.core.mesh.MeshIO
import com.universalconverter.pro.core.mesh.MeshRenderOptions
import com.universalconverter.pro.core.mesh.MeshRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

class ThumbnailLoader(private val context: Context) {

    private val cache = object : LruCache<String, Bitmap>(memoryCacheSize()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val semaphore = Semaphore(2)
    private val imageEngine by lazy { ImageEngine(context) }

    suspend fun load(file: File, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "${file.absolutePath}:${file.lastModified()}:$sizePx"
        cache.get(key)?.let { return@withContext it }
        semaphore.withPermit {
            cache.get(key)?.let { return@withPermit it }
            val bitmap = buildThumbnail(file, sizePx)
            if (bitmap != null) cache.put(key, bitmap)
            bitmap
        }
    }

    private fun buildThumbnail(file: File, sizePx: Int): Bitmap? {
        if (!file.isFile) return null
        val spec = FormatRegistry.findForFileName(file.name) ?: return null
        return when (spec.family) {
            com.universalconverter.pro.core.formats.FormatFamily.IMAGE,
            com.universalconverter.pro.core.formats.FormatFamily.RAW -> imageThumbnail(file, spec.id, sizePx)
            com.universalconverter.pro.core.formats.FormatFamily.PDF -> pdfThumbnail(file, sizePx)
            com.universalconverter.pro.core.formats.FormatFamily.MESH -> meshThumbnail(file, spec.id, sizePx)
            else -> null
        }
    }

    private fun imageThumbnail(file: File, formatId: String, sizePx: Int): Bitmap? {
        return try {
            val decoded = this.imageEngine.decode(file, formatId, sizePx)
            val bitmap = decoded.bitmap
            if (bitmap.width > sizePx || bitmap.height > sizePx) {
                val scale = sizePx.toFloat() / maxOf(bitmap.width, bitmap.height)
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                    (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                    true,
                )
                if (scaled !== bitmap) bitmap.recycle()
                scaled
            } else bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun pdfThumbnail(file: File, sizePx: Int): Bitmap? {
        return try {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount == 0) return null
                renderer.openPage(0).use { page ->
                    val scale = sizePx.toFloat() / maxOf(page.width, page.height)
                    val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                    val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun meshThumbnail(file: File, formatId: String, sizePx: Int): Bitmap? {
        if (file.length() > 120L * 1024L * 1024L) return null
        return try {
            val mesh = MeshIO.read(file, formatId).mesh
            val pixels = MeshRenderer.render(
                mesh,
                MeshRenderOptions(width = sizePx, height = sizePx, supersample = 1, azimuthDegrees = 34f),
            )
            Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
        } catch (_: Exception) {
            null
        }
    }

    private fun memoryCacheSize(): Int {
        val max = Runtime.getRuntime().maxMemory()
        return (max / 8 / 1024).coerceAtLeast(16 * 1024).toInt()
    }
}