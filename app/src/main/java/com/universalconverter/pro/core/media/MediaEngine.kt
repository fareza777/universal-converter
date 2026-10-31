package com.universalconverter.pro.core.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import com.universalconverter.pro.core.convert.ConversionOptions
import com.universalconverter.pro.core.engine.EngineReport
import com.universalconverter.pro.core.image.GifEncoder
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MediaEngine(cacheDir: File) {

    private val cacheDir = cacheDir
    private val audioPipeline = AudioPipeline(cacheDir)
    private val videoCompressor = VideoCompressor(cacheDir, audioPipeline)

    fun convert(
        source: File,
        sourceFormat: String,
        targetId: String,
        options: ConversionOptions,
        output: File,
        progress: (Float, String) -> Unit,
    ): EngineReport {
        val isAudio = sourceFormat in setOf("mp3", "m4a", "wav", "flac", "ogg", "opus", "aiff")
        if (isAudio || targetId == "m4a" || targetId == "wav") {
            return convertAudio(source, sourceFormat, targetId, options, output, progress)
        }
        if (!isAudio) {
            when (targetId) {
                "mp4" -> return videoCompressor.compress(source, sourceFormat, options, output, progress)
                "gif" -> return videoToGif(source, options, output, progress)
                "contact_sheet" -> return contactSheet(source, options, output, progress)
            }
        }
        throw MediaException("Unsupported media target $targetId.")
    }

    private fun convertAudio(
        source: File,
        sourceFormat: String,
        targetId: String,
        options: ConversionOptions,
        output: File,
        progress: (Float, String) -> Unit,
    ): EngineReport {
        val pcm = audioPipeline.decodeToPcm(source, sourceFormat, "audio") { fraction, stage ->
            progress(fraction * 0.6f, stage)
        }
        return try {
            val steps = mutableListOf<String>()
            val warnings = pcm.warnings.toMutableList()
            steps += "${pcm.channels} channel(s), ${pcm.sampleRate} Hz"
            when (targetId) {
                "wav" -> {
                    progress(0.65f, "Writing WAV")
                    audioPipeline.writeWav(pcm, output)
                    steps += "16-bit PCM WAV"
                }
                "m4a" -> {
                    progress(0.65f, "Encoding AAC")
                    audioPipeline.encodeAac(pcm, output, options.audioBitrateKbps) { fraction, stage ->
                        progress(0.65f + fraction * 0.33f, stage)
                    }
                    steps += "AAC ${options.audioBitrateKbps} kbps in MP4"
                }
                else -> throw MediaException("Unsupported audio target $targetId.")
            }
            progress(1f, "Audio ready")
            EngineReport(steps, warnings)
        } finally {
            pcm.rawFile.delete()
        }
    }

    private fun videoToGif(
        source: File,
        options: ConversionOptions,
        output: File,
        progress: (Float, String) -> Unit,
    ): EngineReport {
        val retriever = MediaMetadataRetriever()
        val warnings = mutableListOf<String>()
        try {
            retriever.setDataSource(source.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (durationMs <= 0) throw MediaException("The video duration could not be read.")
            val info = videoInfo(retriever)
            val start = (options.trimStartMs ?: 0L).coerceIn(0L, max(0L, durationMs - 250))
            var end = (options.trimEndMs ?: durationMs).coerceIn(start + 250, durationMs)
            if (end - start > 15_000L) {
                end = start + 15_000L
                warnings += "GIF is limited to the first 15 seconds. Trim the video for longer clips."
            }
            val fps = options.gifFps.coerceIn(4, 24)
            val frames = ((end - start) / 1000.0 * fps).roundToInt().coerceIn(2, 120)
            val intervalMs = (end - start).toDouble() / frames
            val delay = (intervalMs / 10.0).roundToInt().coerceIn(2, 100)
            val targetWidth = options.gifWidth.coerceIn(160, 720)
            val targetHeight = max(2, (targetWidth / info.aspect).roundToInt())
            GifEncoder(output, targetWidth, targetHeight, loopForever = true).use { gif ->
                for (frame in 0 until frames) {
                    val timeMs = start + (intervalMs * frame).toLong()
                    val bitmap = frameAt(retriever, timeMs * 1000L, info.rotation) ?: continue
                    val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
                    if (scaled !== bitmap) bitmap.recycle()
                    val pixels = IntArray(targetWidth * targetHeight)
                    scaled.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
                    scaled.recycle()
                    gif.addFrame(pixels, delay, options.gifColors.coerceIn(32, 256), dither = true, backgroundArgb = 0xFF000000.toInt())
                    progress((frame + 1).toFloat() / frames, "Rendering GIF frame ${frame + 1}")
                }
            }
            val steps = mutableListOf<String>()
            steps += "$frames frames at $targetWidth×$targetHeight"
            steps += "Looping GIF89a"
            return EngineReport(steps, warnings)
        } catch (error: MediaException) {
            throw error
        } catch (error: Exception) {
            throw MediaException("The video could not be turned into a GIF.", error)
        } finally {
            retriever.release()
        }
    }

    private fun contactSheet(
        source: File,
        options: ConversionOptions,
        output: File,
        progress: (Float, String) -> Unit,
    ): EngineReport {
        val retriever = MediaMetadataRetriever()
        val warnings = mutableListOf<String>()
        try {
            retriever.setDataSource(source.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (durationMs <= 0) throw MediaException("The video duration could not be read.")
            val info = videoInfo(retriever)
            val start = (options.trimStartMs ?: 0L).coerceIn(0L, max(0L, durationMs - 250))
            val end = (options.trimEndMs ?: durationMs).coerceIn(start + 250, durationMs)
            val count = options.contactCount.coerceIn(4, 24)
            val columns = options.contactColumns.coerceIn(1, 6)
            val rows = (count + columns - 1) / columns
            val margin = 22
            var cellWidth = min(options.imageMaxEdge.takeIf { it > 0 } ?: 320, 360)
            val gap = 12
            val headerHeight = 64
            val labelHeight = 22
            val maxSheetWidth = 4096
            val available = maxSheetWidth - margin * 2 - gap * (columns - 1)
            cellWidth = min(cellWidth, available / columns)
            var cellHeight = (cellWidth / info.aspect).roundToInt().coerceAtMost(440)
            val sheetWidth = margin * 2 + columns * cellWidth + (columns - 1) * gap
            val sheetHeight = margin * 2 + headerHeight + rows * (cellHeight + labelHeight) + (rows - 1) * gap
            val sheet = Bitmap.createBitmap(sheetWidth, sheetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet)
            canvas.drawColor(Color.rgb(18, 17, 16))
            val eyebrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(224, 118, 79)
                textSize = 10f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                letterSpacing = 0.18f
            }
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(245, 240, 231)
                textSize = 20f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(169, 160, 147)
                textSize = 11f
                typeface = Typeface.MONOSPACE
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(200, 190, 174)
                textSize = 11f
                typeface = Typeface.MONOSPACE
            }
            canvas.drawText("CONTACT SHEET", margin.toFloat(), (margin + 8).toFloat(), eyebrowPaint)
            val title = source.nameWithoutExtension.take(64)
            canvas.drawText(title, margin.toFloat(), (margin + 34).toFloat(), titlePaint)
            canvas.drawText(
                "${durationMs / 1000}s · ${info.width}×${info.height} · $count stills",
                (sheetWidth - margin).toFloat() - metaPaint.measureText("${durationMs / 1000}s · ${info.width}×${info.height} · $count stills"),
                (margin + 34).toFloat(),
                metaPaint,
            )
            canvas.drawRect(
                margin.toFloat(), (margin + 44).toFloat(),
                sheetWidth - margin.toFloat(), (margin + 45).toFloat(),
                Paint().apply { color = Color.rgb(55, 50, 44) },
            )
            val cellBg = Paint().apply { color = Color.rgb(31, 28, 24) }
            for (index in 0 until count) {
                val column = index % columns
                val row = index / columns
                val left = margin + column * (cellWidth + gap)
                val top = margin + headerHeight + row * (cellHeight + labelHeight + gap)
                canvas.drawRoundRect(
                    RectF(left.toFloat(), top.toFloat(), (left + cellWidth).toFloat(), (top + cellHeight).toFloat()),
                    10f, 10f, cellBg,
                )
                val timeMs = start + (end - start) * index / max(1, count - 1)
                val bitmap = frameAt(retriever, timeMs * 1000L, info.rotation)
                if (bitmap != null) {
                    val scale = min(cellWidth.toFloat() / bitmap.width, cellHeight.toFloat() / bitmap.height)
                    val drawWidth = bitmap.width * scale
                    val drawHeight = bitmap.height * scale
                    val dx = left + (cellWidth - drawWidth) / 2f
                    val dy = top + (cellHeight - drawHeight) / 2f
                    canvas.drawBitmap(bitmap, null, RectF(dx, dy, dx + drawWidth, dy + drawHeight), Paint(Paint.FILTER_BITMAP_FLAG))
                    bitmap.recycle()
                }
                val seconds = timeMs / 1000.0
                canvas.drawText(
                    String.format(java.util.Locale.US, "%02d:%04.1f", (seconds / 60).toInt(), seconds % 60),
                    left.toFloat(),
                    (top + cellHeight + 15).toFloat(),
                    labelPaint,
                )
                progress((index + 1).toFloat() / count, "Capturing still ${index + 1}")
            }
            val step = mutableListOf<String>()
            step += "$count stills in a $columns-column sheet"
            progress(0.95f, "Writing JPEG")
            output.outputStream().use { stream ->
                sheet.compress(Bitmap.CompressFormat.JPEG, options.imageQuality.coerceIn(50, 100), stream)
            }
            sheet.recycle()
            return EngineReport(step, warnings)
        } catch (error: MediaException) {
            throw error
        } catch (error: Exception) {
            throw MediaException("The contact sheet could not be rendered.", error)
        } finally {
            retriever.release()
        }
    }

    private data class VideoInfo(val width: Int, val height: Int, val rotation: Int, val aspect: Float)

    private fun videoInfo(retriever: MediaMetadataRetriever): VideoInfo {
        var width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        var height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        var rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (width <= 0 || height <= 0) {
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (frame != null) {
                width = frame.width
                height = frame.height
                frame.recycle()
            }
        }
        if (width <= 0 || height <= 0) throw MediaException("The video dimensions could not be read.")
        rotation = ((rotation % 360) + 360) % 360
        val displayWidth = if (rotation == 90 || rotation == 270) height else width
        val displayHeight = if (rotation == 90 || rotation == 270) width else height
        return VideoInfo(width, height, rotation, displayWidth.toFloat() / displayHeight)
    }

    private fun frameAt(retriever: MediaMetadataRetriever, timeUs: Long, rotation: Int): Bitmap? {
        val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: return null
        if (rotation == 0) return frame
        return try {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, matrix, true)
            if (rotated !== frame) frame.recycle()
            rotated
        } catch (_: Exception) {
            frame
        }
    }
}