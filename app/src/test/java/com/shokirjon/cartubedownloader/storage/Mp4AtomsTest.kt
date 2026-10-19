package com.shokirjon.cartubedownloader.storage

import java.io.DataOutputStream
import java.nio.file.Files
import org.junit.Assert.assertTrue
import org.junit.Test

class Mp4AtomsTest {
    @Test fun reportsTruncatedAtomWithoutReadingMediaIntoMemory() {
        val file = Files.createTempFile("cartube", ".mp4").toFile()
        try {
            DataOutputStream(file.outputStream()).use {
                it.writeInt(8); it.writeBytes("ftyp")
                it.writeInt(1024); it.writeBytes("mdat")
            }
            val result = Mp4Atoms.describe(file)
            assertTrue(result.contains("ftyp"))
            assertTrue(result.contains("mdat:truncated"))
        } finally { file.delete() }
    }
}
