package com.universalconverter.pro.data

import android.media.MediaCodecList
import android.os.Build
import com.universalconverter.pro.core.convert.DeviceCaps

object DeviceCapsProbe {

    fun probe(): DeviceCaps {
        return try {
            val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            fun hasEncoder(mime: String): Boolean = codecs.any { info ->
                info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
            }
            val audio = buildSet {
                if (hasEncoder(DeviceCaps.AAC)) add(DeviceCaps.AAC)
                if (hasEncoder(DeviceCaps.FLAC)) add(DeviceCaps.FLAC)
                if (hasEncoder(DeviceCaps.OPUS)) add(DeviceCaps.OPUS)
                if (hasEncoder(DeviceCaps.MP3)) add(DeviceCaps.MP3)
            }
            DeviceCaps(
                audioEncoders = audio,
                videoAvcEncoder = hasEncoder("video/avc"),
                oggMuxer = Build.VERSION.SDK_INT >= 29,
                platformLabel = "Android ${Build.VERSION.RELEASE}",
            )
        } catch (_: Exception) {
            DeviceCaps(
                audioEncoders = setOf(DeviceCaps.AAC),
                videoAvcEncoder = true,
                oggMuxer = Build.VERSION.SDK_INT >= 29,
                platformLabel = "Android ${Build.VERSION.RELEASE}",
            )
        }
    }
}