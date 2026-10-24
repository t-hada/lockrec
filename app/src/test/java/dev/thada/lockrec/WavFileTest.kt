package dev.thada.lockrec

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavFileTest {
    @Test
    fun readsSamplesAfterHeaderAndFinalizes() {
        val file = File.createTempFile("lockrec", ".wav")
        val pcm = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(0).putShort(16384).putShort(-32768).putShort(-16384).array()
        file.writeBytes(WavFile.header(0, 16_000) + pcm)

        assertEquals(4L, WavFile.sampleCount(file))
        assertArrayEquals(floatArrayOf(0.5f, -1f), WavFile.readSamples(file, 1, 2), 0f)

        WavFile.finalizeHeader(file, 16_000)
        val header = ByteBuffer.wrap(file.readBytes(), 0, WavFile.HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(36 + 8, header.getInt(4))
        assertEquals(8, header.getInt(40))
        file.delete()
    }
}
