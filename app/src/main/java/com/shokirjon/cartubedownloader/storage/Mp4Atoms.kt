package com.shokirjon.cartubedownloader.storage

import java.io.File
import java.io.RandomAccessFile

/** Bounded, read-only inspection for diagnostics; this does not replace a media decoder. */
internal object Mp4Atoms {
    fun describe(file: File): String = RandomAccessFile(file, "r").use { input ->
        val length = input.length()
        var offset = 0L
        val atoms = mutableListOf<String>()
        while (offset + 8 <= length && atoms.size < 32) {
            input.seek(offset)
            val shortSize = input.readInt().toLong() and 0xffffffffL
            val typeBytes = ByteArray(4).also { input.readFully(it) }
            val type = String(typeBytes, Charsets.US_ASCII)
            val headerSize = if (shortSize == 1L) 16L else 8L
            val size = when (shortSize) {
                0L -> length - offset
                1L -> if (offset + 16 <= length) input.readLong() else -1L
                else -> shortSize
            }
            if (size < headerSize || size > length - offset) {
                atoms.add("$type:truncated@$offset(size=$size)")
                break
            }
            atoms.add(type)
            offset += size
        }
        "atoms=${atoms.joinToString(",")}; scanned=$offset/$length"
    }
}
