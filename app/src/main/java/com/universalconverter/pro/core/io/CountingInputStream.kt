package com.universalconverter.pro.core.io

import java.io.FilterInputStream
import java.io.InputStream

class CountingInputStream(input: InputStream) : FilterInputStream(input) {
    var count: Long = 0L
        private set

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) count++
        return value
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val read = super.read(b, off, len)
        if (read > 0) count += read
        return read
    }

    override fun skip(n: Long): Long {
        val skipped = super.skip(n)
        if (skipped > 0) count += skipped
        return skipped
    }
}