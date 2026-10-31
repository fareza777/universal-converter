package com.universalconverter.pro.core.io

class FloatList(initialCapacity: Int = 1024) {
    var data = FloatArray(initialCapacity.coerceAtLeast(16))
        private set
    var size = 0
        private set

    fun add(value: Float) {
        ensure(1)
        data[size++] = value
    }

    fun add(values: FloatArray) {
        ensure(values.size)
        values.copyInto(data, size)
        size += values.size
    }

    private fun ensure(extra: Int) {
        if (size + extra <= data.size) return
        var next = data.size * 2
        while (next < size + extra) next *= 2
        data = data.copyOf(next)
    }

    fun toArray(): FloatArray = data.copyOf(size)

    fun trimTo(size: Int) {
        require(size in 0..this.size)
        this.size = size
    }
}

class IntList(initialCapacity: Int = 1024) {
    var data = IntArray(initialCapacity.coerceAtLeast(16))
        private set
    var size = 0
        private set

    fun add(value: Int) {
        ensure(1)
        data[size++] = value
    }

    fun addAll(values: IntArray) {
        ensure(values.size)
        values.copyInto(data, size)
        size += values.size
    }

    private fun ensure(extra: Int) {
        if (size + extra <= data.size) return
        var next = data.size * 2
        while (next < size + extra) next *= 2
        data = data.copyOf(next)
    }

    fun toArray(): IntArray = data.copyOf(size)
}

class ByteList(initialCapacity: Int = 4096) {
    var data = ByteArray(initialCapacity.coerceAtLeast(64))
        private set
    var size = 0
        private set

    fun writeByte(value: Int) {
        ensure(1)
        data[size++] = value.toByte()
    }

    fun writeBytes(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
        ensure(length)
        bytes.copyInto(data, size, offset, offset + length)
        size += length
    }

    fun writeAscii(text: String) {
        writeBytes(text.toByteArray(Charsets.US_ASCII))
    }

    fun writeU16LE(value: Int) {
        writeByte(value and 0xFF)
        writeByte((value ushr 8) and 0xFF)
    }

    fun writeU32LE(value: Int) {
        writeByte(value and 0xFF)
        writeByte((value ushr 8) and 0xFF)
        writeByte((value ushr 16) and 0xFF)
        writeByte((value ushr 24) and 0xFF)
    }

    fun writeFloatLE(value: Float) {
        writeU32LE(value.toRawBits())
    }

    private fun ensure(extra: Int) {
        if (size + extra <= data.size) return
        var next = data.size * 2
        while (next < size + extra) next *= 2
        data = data.copyOf(next)
    }

    fun toByteArray(): ByteArray = data.copyOf(size)
}