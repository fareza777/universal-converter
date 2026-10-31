package com.universalconverter.pro.core.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.universalconverter.pro.core.convert.ConversionOptions
import com.universalconverter.pro.core.convert.PdfPageSize
import com.universalconverter.pro.core.image.BmpWriter
import com.universalconverter.pro.core.image.GifEncoder
import com.universalconverter.pro.core.image.ImageFormatException
import com.universalconverter.pro.core.image.PixelImage
import com.universalconverter.pro.core.image.TiffReader
import com.universalconverter.pro.core.image.TiffWriter
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

class ImageEngine(private val context: Context) {

    data class DecodeResult(
        val bitmap: Bitmap,
        val warnings: List<String>,
        val engine: String,
        val sourceWidth: Int,
        val sourceHeight: Int,
    )

    fun decode(file: File, formatId: String, maxEdge: Int): DecodeResult {
        val warnings = mutableListOf<String>()
        if (formatId == "tiff" || formatId == "dng") {
            val tiff = TiffReader.read(file, preferPreview = formatId == "dng")
            warnings += tiff.warnings
            val bitmap = if (tiff.embeddedJpeg != null) {
                val options = bitmapOptionsForBytes(tiff.embeddedJpeg, maxEdge)
                val decoded = BitmapFactory.decodeByteArray(tiff.embeddedJpeg, 0, tiff.embeddedJpeg.size, options)
                    ?: throw ImageFormatException("The camera preview inside this RAW file could not be decoded.")
                if (options.inSampleSize > 1) warnings += "Preview was sampled for memory (1:${options.inSampleSize})."
                decoded
            } else {
                val image = tiff.image ?: throw ImageFormatException("This TIFF has no decodable image.")
                Bitmap.createBitmap(image.argb, image.width, image.height, Bitmap.Config.ARGB_8888)
            }
            val scaled = scaleDown(bitmap, maxEdge, warnings)
            return DecodeResult(scaled, warnings, if (formatId == "dng") "DNG preview decoder" else "TIFF decoder", bitmap.width, bitmap.height)
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sourceWidth = bounds.outWidth
        var sourceHeight = bounds.outHeight
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            sourceWidth = 0
            sourceHeight = 0
        }
        val useImageDecoder = (formatId == "heic" || formatId == "avif") && Build.VERSION.SDK_INT >= 28
        val bitmap: Bitmap = if (useImageDecoder) {
            try {
                val source = ImageDecoder.createSource(file)
                val sample = computeSample(sourceWidth, sourceHeight, maxEdge)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    if (sample > 1) decoder.setTargetSampleSize(sample)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } catch (error: Exception) {
                throw ImageFormatException(
                    "This device cannot decode ${formatId.uppercase()} images. Try converting on a newer Android device.",
                    error,
                )
            }
        } else {
            val options = bitmapOptions(file, maxEdge, warnings)
            BitmapFactory.decodeFile(file.absolutePath, options)
                ?: throw ImageFormatException("The image could not be decoded. The file may be damaged or use an unsupported profile.")
        }
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            sourceWidth = bitmap.width
            sourceHeight = bitmap.height
        }
        var oriented = applyExifOrientation(bitmap, file)
        if (oriented !== bitmap) bitmap.recycle()
        oriented = scaleDown(oriented, maxEdge, warnings)
        if (formatId == "gif") warnings += "Animated GIFs are converted from their first frame."
        return DecodeResult(oriented, warnings, "Bitmap codec", sourceWidth, sourceHeight)
    }

    private fun bitmapOptions(file: File, maxEdge: Int, warnings: MutableList<String>): BitmapFactory.Options {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sample = computeSample(bounds.outWidth, bounds.outHeight, maxEdge)
        if (sample > 1) warnings += "Decoded at 1:$sample to stay inside the memory budget."
        return BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
    }

    private fun bitmapOptionsForBytes(bytes: ByteArray, maxEdge: Int): BitmapFactory.Options {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return BitmapFactory.Options().apply {
            inSampleSize = computeSample(bounds.outWidth, bounds.outHeight, maxEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
    }

    private fun computeSample(width: Int, height: Int, maxEdge: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (true) {
            val scaledWidth = width / sample
            val scaledHeight = height / sample
            val pixels = scaledWidth.toLong() * scaledHeight
            val tooManyPixels = pixels > 40_000_000L
            val tooWide = maxEdge > 0 && max(scaledWidth, scaledHeight) > maxEdge
            if (!tooManyPixels && !tooWide) break
            sample *= 2
            if (sample > 32) break
        }
        return sample
    }

    private fun scaleDown(bitmap: Bitmap, maxEdge: Int, warnings: MutableList<String>): Bitmap {
        if (maxEdge <= 0) return bitmap
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / largest
        val targetWidth = max(1, (bitmap.width * scale).roundToInt())
        val targetHeight = max(1, (bitmap.height * scale).roundToInt())
        val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        if (scaled !== bitmap) bitmap.recycle()
        warnings += "Resized to $targetWidth×$targetHeight."
        return scaled
    }

    private fun applyExifOrientation(bitmap: Bitmap, file: File): Bitmap {
        return try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
                else -> return bitmap
            }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            rotated
        } catch (_: Exception) {
            bitmap
        }
    }

    fun encode(
        bitmap: Bitmap,
        sourceFile: File,
        sourceFormatId: String,
        targetId: String,
        options: ConversionOptions,
        output: File,
        progress: (Float, String) -> Unit,
    ): EngineReport {
        val steps = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        progress(0.62f, "Preparing pixels")
        when (targetId) {
            "jpg" -> {
                val flattened = flattenAlpha(bitmap, options.imageMatte)
                output.outputStream().use { stream ->
                    if (!flattened.compress(Bitmap.CompressFormat.JPEG, options.imageQuality.coerceIn(30, 100), stream)) {
                        throw ImageFormatException("JPEG encoding failed.")
                    }
                }
                if (flattened !== bitmap) flattened.recycle()
                steps += "Flattened onto a matte"
                steps += "JPEG quality ${options.imageQuality.coerceIn(30, 100)}"
                if (options.keepMetadata) copyMetadata(sourceFile, output, warnings)
            }
            "png" -> {
                output.outputStream().use { stream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        throw ImageFormatException("PNG encoding failed.")
                    }
                }
                steps += "Lossless PNG"
            }
            "webp" -> {
                val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                output.outputStream().use { stream ->
                    if (!bitmap.compress(format, options.imageQuality.coerceIn(30, 100), stream)) {
                        throw ImageFormatException("WebP encoding failed.")
                    }
                }
                steps += "WebP quality ${options.imageQuality.coerceIn(30, 100)}"
            }
            "tiff" -> {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                TiffWriter.write(PixelImage(bitmap.width, bitmap.height, pixels), output, level = options.archiveLevel)
                steps += "Deflate-compressed TIFF"
            }
            "bmp" -> {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                BmpWriter.write(PixelImage(bitmap.width, bitmap.height, pixels), output)
                steps += "Uncompressed BMP"
            }
            "gif" -> {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                GifEncoder(output, bitmap.width, bitmap.height, loopForever = true).use { gif ->
                    gif.addFrame(pixels, delayCentiseconds = 10, maxColors = options.gifColors, dither = true, backgroundArgb = options.imageMatte)
                }
                steps += "GIF with 256-colour palette"
            }
            "pdf" -> {
                writeBitmapPdf(bitmap, output, options)
                steps += "PDF page ${options.pdfPage.label}"
            }
            else -> throw ImageFormatException("Unsupported image target $targetId.")
        }
        progress(1f, "Image ready")
        return EngineReport(steps, warnings)
    }

    fun writeBitmapPdf(bitmap: Bitmap, output: File, options: ConversionOptions) {
        val pageWidth: Int
        val pageHeight: Int
        when (options.pdfPage) {
            PdfPageSize.LETTER -> { pageWidth = 612; pageHeight = 792 }
            PdfPageSize.FIT -> {
                val landscape = bitmap.width >= bitmap.height
                val scale = minOf(842f / bitmap.width, 595f / bitmap.height)
                pageWidth = max(120, (bitmap.width * scale).roundToInt())
                pageHeight = max(120, (bitmap.height * scale).roundToInt())
                if (landscape && pageHeight > pageWidth) {
                    // Rotated fit still uses the same aspect ratio; no swap needed.
                }
            }
            PdfPageSize.A4 -> { pageWidth = 595; pageHeight = 842 }
        }
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)
        val margin = if (options.pdfPage == PdfPageSize.FIT) 0f else 32f
        val availableWidth = pageWidth - margin * 2
        val availableHeight = pageHeight - margin * 2
        val scale = minOf(availableWidth / bitmap.width, availableHeight / bitmap.height)
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val left = (pageWidth - drawWidth) / 2f
        val top = (pageHeight - drawHeight) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + drawWidth, top + drawHeight), paint)
        document.finishPage(page)
        output.outputStream().use { document.writeTo(it) }
        document.close()
    }

    private fun flattenAlpha(bitmap: Bitmap, matte: Int): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(matte)
        canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun copyMetadata(source: File, destination: File, warnings: MutableList<String>) {
        try {
            val from = ExifInterface(source.absolutePath)
            val to = ExifInterface(destination.absolutePath)
            val tags = listOf(
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_ARTIST,
                ExifInterface.TAG_COPYRIGHT,
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_LONGITUDE_REF,
                ExifInterface.TAG_GPS_ALTITUDE,
                ExifInterface.TAG_GPS_ALTITUDE_REF,
                ExifInterface.TAG_GPS_TIMESTAMP,
                ExifInterface.TAG_GPS_DATESTAMP,
            )
            for (tag in tags) {
                from.getAttribute(tag)?.let { to.setAttribute(tag, it) }
            }
            to.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            to.setAttribute(ExifInterface.TAG_SOFTWARE, "Universal Converter")
            to.saveAttributes()
        } catch (_: Exception) {
            warnings += "Capture metadata could not be copied to this JPEG."
        }
    }
}

data class EngineReport(
    val steps: List<String>,
    val warnings: List<String> = emptyList(),
)