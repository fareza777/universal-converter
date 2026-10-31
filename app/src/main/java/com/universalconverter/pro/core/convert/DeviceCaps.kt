package com.universalconverter.pro.core.convert

data class DeviceCaps(
    val audioEncoders: Set<String>,
    val videoAvcEncoder: Boolean,
    val oggMuxer: Boolean,
    val platformLabel: String,
) {
    val hasAac: Boolean get() = DeviceCaps.AAC in audioEncoders
    val hasFlac: Boolean get() = DeviceCaps.FLAC in audioEncoders
    val hasOpus: Boolean get() = DeviceCaps.OPUS in audioEncoders
    val hasMp3: Boolean get() = DeviceCaps.MP3 in audioEncoders

    companion object {
        const val AAC = "audio/mp4a-latm"
        const val FLAC = "audio/flac"
        const val OPUS = "audio/opus"
        const val MP3 = "audio/mpeg"

        val Universal = DeviceCaps(
            audioEncoders = setOf(AAC),
            videoAvcEncoder = true,
            oggMuxer = true,
            platformLabel = "Device codecs",
        )
    }
}