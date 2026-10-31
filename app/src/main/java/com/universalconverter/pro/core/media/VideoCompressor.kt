package com.universalconverter.pro.core.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import com.universalconverter.pro.core.convert.ConversionOptions
import com.universalconverter.pro.core.engine.EngineReport
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

class VideoCompressor(
    private val cacheDir: File,
    private val audioPipeline: AudioPipeline,
) {

    fun compress(
        source: File,
        sourceFormat: String,
        options: ConversionOptions,
        output: File,
        progress: (Float, String) -> Unit,
    ): EngineReport {
        val warnings = mutableListOf<String>()
        val steps = mutableListOf<String>()
        val tempDir = File(cacheDir, "video").apply { mkdirs() }
        val videoTemp = File(tempDir, "video-${System.nanoTime()}.mp4")
        var audioTemp: File? = null
        var pcm: PcmData? = null
        try {
            progress(0.02f, "Inspecting video")
            val probe = MediaExtractor()
            var videoTrack = -1
            var videoFormat: MediaFormat? = null
            var audioTrack = -1
            var audioMime: String? = null
            try {
                probe.setDataSource(source.absolutePath)
                for (i in 0 until probe.trackCount) {
                    val format = probe.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    if (mime.startsWith("video/") && videoTrack < 0) {
                        videoTrack = i
                        videoFormat = format
                    } else if (mime.startsWith("audio/") && audioTrack < 0) {
                        audioTrack = i
                        audioMime = mime
                    }
                }
            } finally {
                probe.release()
            }
            if (videoTrack < 0 || videoFormat == null) throw MediaException("This file has no video track to compress.")
            val width = videoFormat.getInteger(MediaFormat.KEY_WIDTH, 0)
            val height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT, 0)
            if (width <= 0 || height <= 0) throw MediaException("The video dimensions could not be read.")
            val rotation = if (videoFormat.containsKey(MediaFormat.KEY_ROTATION)) videoFormat.getInteger(MediaFormat.KEY_ROTATION) else 0
            val frameRate = if (videoFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
            } else 30f
            val targetFps = if (frameRate in 10f..90f) frameRate.roundToInt() else 30
            val targetHeight = when {
                options.videoHeight <= 0 -> height
                options.videoHeight >= height -> height
                else -> options.videoHeight
            }
            var targetWidth = (width.toFloat() / height * targetHeight).roundToInt()
            if (targetWidth % 2 != 0) targetWidth++
            var evenHeight = targetHeight
            if (evenHeight % 2 != 0) evenHeight++
            val bitrate = if (options.videoBitrateKbps > 0) {
                val requested = options.videoBitrateKbps * 1000
                val pixelScale = (targetWidth.toFloat() * evenHeight / (1920f * 1080f)).coerceIn(0.05f, 1f)
                (requested * pixelScale).toInt().coerceIn(650_000, requested)
            } else {
                (targetWidth.toLong() * evenHeight * targetFps * 0.09).toInt().coerceIn(900_000, 24_000_000)
            }
            steps += "Video ${width}×$height → ${targetWidth}×$evenHeight"
            steps += "${bitrate / 1000} kbps H.264, $targetFps fps"
            if (rotation != 0) steps += "Rotation ${rotation}° preserved"
            if (audioTrack >= 0 && audioMime != null) {
                steps += "Audio track found (${audioMime.substringAfterLast('/')})"
            } else {
                warnings += "This video has no audio track."
            }

            // Audio: copy AAC when possible, otherwise re-encode through the PCM pipeline.
            if (audioTrack >= 0 && audioMime != null) {
                if (audioMime == MediaFormat.MIMETYPE_AUDIO_AAC) {
                    progress(0.08f, "Audio will be copied")
                } else {
                    progress(0.06f, "Preparing audio")
                    val decoded = audioPipeline.decodeToPcm(source, sourceFormat, "video-audio") { fraction, _ ->
                        progress(0.06f + fraction * 0.12f, "Decoding audio")
                    }
                    pcm = decoded
                    val encoded = File(tempDir, "audio-${System.nanoTime()}.m4a")
                    audioTemp = encoded
                    audioPipeline.encodeAac(decoded, encoded, options.audioBitrateKbps) { fraction, _ ->
                        progress(0.18f + fraction * 0.08f, "Encoding AAC")
                    }
                    warnings += "The soundtrack was re-encoded to AAC."
                }
            }

            progress(0.27f, "Transcoding video")
            transcodeVideo(source, videoTrack, targetWidth, evenHeight, targetFps, bitrate, rotation, videoTemp) { fraction, stage ->
                progress(0.27f + fraction * 0.45f, stage)
            }
            progress(0.74f, "Muxing tracks")
            val audioFile = audioTemp ?: if (audioTrack >= 0 && audioMime == MediaFormat.MIMETYPE_AUDIO_AAC) source else null
            muxFinal(videoTemp, audioFile, output, rotation) { fraction ->
                progress(0.74f + fraction * 0.24f, "Muxing")
            }
            progress(1f, "Video ready")
            return EngineReport(steps, warnings)
        } finally {
            videoTemp.delete()
            audioTemp?.delete()
            pcm?.rawFile?.delete()
        }
    }

    private fun transcodeVideo(
        source: File,
        sourceTrack: Int,
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int,
        rotation: Int,
        output: File,
        progress: (Float, String) -> Unit,
    ) {
        val extractor = MediaExtractor()
        extractor.setDataSource(source.absolutePath)
        extractor.selectTrack(sourceTrack)
        val sourceFormat = extractor.getTrackFormat(sourceTrack)
        val mime = sourceFormat.getString(MediaFormat.KEY_MIME)
            ?: throw MediaException("The video codec is missing.")
        val duration = if (sourceFormat.containsKey(MediaFormat.KEY_DURATION)) sourceFormat.getLong(MediaFormat.KEY_DURATION) else 0L
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var surface: Surface? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var trackIndex = -1
        try {
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
                )
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = encoder.createInputSurface()
            encoder.start()

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(sourceFormat, surface, null, 0)
            decoder.start()

            if (output.exists()) output.delete()
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val decoderInfo = MediaCodec.BufferInfo()
            val encoderInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var decoderDone = false
            var eosSignalled = false
            var encoderDone = false
            var lastProgress = 0f
            while (!encoderDone) {
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = decoder.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                if (!decoderDone) {
                    val index = decoder.dequeueOutputBuffer(decoderInfo, 10_000)
                    if (index >= 0) {
                        val render = decoderInfo.size > 0
                        decoder.releaseOutputBuffer(index, render)
                        if (decoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) decoderDone = true
                    }
                }
                if (decoderDone && !eosSignalled) {
                    encoder.signalEndOfInputStream()
                    eosSignalled = true
                }
                var drainedAny = false
                while (true) {
                    val index = encoder.dequeueOutputBuffer(encoderInfo, if (decoderDone) 10_000 else 0)
                    if (index == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val outputFormat = encoder.outputFormat
                        if (rotation != 0) outputFormat.setInteger(MediaFormat.KEY_ROTATION, rotation)
                        trackIndex = muxer.addTrack(outputFormat)
                        muxer.start()
                        muxerStarted = true
                        continue
                    }
                    if (index >= 0) {
                        drainedAny = true
                        if (encoderInfo.size > 0 && muxerStarted && encoderInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            val buffer = encoder.getOutputBuffer(index)!!
                            buffer.position(encoderInfo.offset)
                            buffer.limit(encoderInfo.offset + encoderInfo.size)
                            muxer.writeSampleData(trackIndex, buffer, encoderInfo)
                        }
                        encoder.releaseOutputBuffer(index, false)
                        if (encoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            encoderDone = true
                            break
                        }
                    }
                }
                val time = extractor.sampleTime.coerceAtLeast(0)
                if (duration > 0) {
                    val fraction = (time.toDouble() / duration).coerceIn(0.0, 1.0).toFloat()
                    if (fraction - lastProgress > 0.02f) {
                        lastProgress = fraction
                        progress(fraction * 0.92f, "Encoding H.264")
                    }
                }
                if (!drainedAny && decoderDone && !encoderDone) {
                    // Give the encoder a moment when the pipeline is between frames.
                    Thread.sleep(2)
                }
            }
            if (!muxerStarted) throw MediaException("The H.264 encoder produced no track.")
            progress(1f, "Video encoded")
        } catch (error: MediaException) {
            throw error
        } catch (error: Exception) {
            throw MediaException("The video could not be transcoded on this device.", error)
        } finally {
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            surface?.release()
            try { encoder?.stop() } catch (_: Exception) {}
            try { encoder?.release() } catch (_: Exception) {}
            if (muxerStarted) {
                try { muxer?.stop() } catch (_: Exception) {}
            }
            try { muxer?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }

    private fun muxFinal(videoFile: File, audioFile: File?, output: File, rotation: Int, progress: (Float) -> Unit) {
        val videoExtractor = MediaExtractor()
        val audioExtractor = if (audioFile != null) MediaExtractor() else null
        var muxer: MediaMuxer? = null
        var started = false
        try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            var videoTrackIndex = -1
            for (i in 0 until videoExtractor.trackCount) {
                if (videoExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("video/")) {
                    videoTrackIndex = i
                    break
                }
            }
            if (videoTrackIndex < 0) throw MediaException("The transcoded video has no video track.")
            videoExtractor.selectTrack(videoTrackIndex)
            var audioTrackIndex = -1
            if (audioExtractor != null && audioFile != null) {
                audioExtractor.setDataSource(audioFile.absolutePath)
                for (i in 0 until audioExtractor.trackCount) {
                    if (audioExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")) {
                        audioTrackIndex = i
                        break
                    }
                }
                if (audioTrackIndex >= 0) audioExtractor.selectTrack(audioTrackIndex)
            }
            val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)
            if (rotation != 0) videoFormat.setInteger(MediaFormat.KEY_ROTATION, rotation)
            if (output.exists()) output.delete()
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val videoOut = muxer.addTrack(videoFormat)
            var audioOut = -1
            if (audioExtractor != null && audioTrackIndex >= 0) {
                audioOut = muxer.addTrack(audioExtractor.getTrackFormat(audioTrackIndex))
            }
            muxer.start()
            started = true
            val videoBuffer = ByteBuffer.allocateDirect(16 * 1024 * 1024)
            val audioBuffer = ByteBuffer.allocateDirect(4 * 1024 * 1024)
            val videoInfo = MediaCodec.BufferInfo()
            val audioInfo = MediaCodec.BufferInfo()
            var videoHasSample = loadSample(videoExtractor, videoBuffer, videoInfo)
            var audioHasSample = if (audioExtractor != null && audioTrackIndex >= 0) loadSample(audioExtractor, audioBuffer, audioInfo) else false
            var lastVideoTime = 0L
            val totalDuration = max(1L, videoFormat.getLongOrDefault(MediaFormat.KEY_DURATION, 1L))
            while (videoHasSample || audioHasSample) {
                val takeVideo = videoHasSample && (!audioHasSample || videoInfo.presentationTimeUs <= audioInfo.presentationTimeUs)
                if (takeVideo) {
                    muxer.writeSampleData(videoOut, videoBuffer, videoInfo)
                    lastVideoTime = videoInfo.presentationTimeUs
                    videoExtractor.advance()
                    videoHasSample = loadSample(videoExtractor, videoBuffer, videoInfo)
                } else if (audioHasSample) {
                    muxer.writeSampleData(audioOut, audioBuffer, audioInfo)
                    audioExtractor!!.advance()
                    audioHasSample = loadSample(audioExtractor, audioBuffer, audioInfo)
                }
                val fraction = (lastVideoTime.toDouble() / totalDuration).coerceIn(0.0, 1.0).toFloat()
                progress(fraction * 0.97f)
            }
            progress(1f)
        } catch (error: MediaException) {
            throw error
        } catch (error: Exception) {
            throw MediaException("The video and audio tracks could not be combined.", error)
        } finally {
            videoExtractor.release()
            audioExtractor?.release()
            if (started) {
                try { muxer?.stop() } catch (_: Exception) {}
            }
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    private fun loadSample(extractor: MediaExtractor, buffer: ByteBuffer, info: MediaCodec.BufferInfo): Boolean {
        buffer.clear()
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) return false
        info.set(0, size, extractor.sampleTime, if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
        return true
    }

    private fun MediaFormat.getLongOrDefault(key: String, fallback: Long): Long =
        if (containsKey(key)) getLong(key) else fallback
}

class MediaException(message: String, cause: Throwable? = null) : Exception(message, cause)