package dev.thada.lockrec

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class MinutesTest {
    @Test
    fun recordingIdRoundTrips() {
        val start = LocalDateTime.of(2026, 10, 23, 9, 5, 7)
        assertEquals("20261023_090507", Minutes.recordingId(start))
        assertEquals(start, Minutes.startOf("20261023_090507"))
    }

    @Test
    fun remotePathAddsSuffixFromSecondAttempt() {
        assertEquals("02_minute/20261023_0905.md", Minutes.remotePath("20261023_090507", 1))
        assertEquals("02_minute/20261023_0905_2.md", Minutes.remotePath("20261023_090507", 2))
        assertEquals("02_minute/20261023_0905_3.md", Minutes.remotePath("20261023_090507", 3))
    }

    @Test
    fun windowCountRoundsUp() {
        assertEquals(0, Minutes.windowCount(0, 100))
        assertEquals(1, Minutes.windowCount(1, 100))
        assertEquals(1, Minutes.windowCount(100, 100))
        assertEquals(2, Minutes.windowCount(101, 100))
    }

    @Test
    fun timestampSwitchesToHoursAfterOneHour() {
        assertEquals("[0:06]", Minutes.timestamp(6_900))
        assertEquals("[12:34]", Minutes.timestamp((12 * 60 + 34) * 1000L))
        assertEquals("[1:02:03]", Minutes.timestamp((3600 + 2 * 60 + 3) * 1000L))
    }

    @Test
    fun paragraphsGroupByDurationAndSkipBlank() {
        val segments = listOf(
            Segment(0, " a "), Segment(10_000, "b"), Segment(20_000, "  "),
            Segment(30_000, "c"), Segment(61_000, "d"),
        )
        assertEquals(
            listOf(Segment(0, "a b"), Segment(30_000, "c"), Segment(61_000, "d")),
            Minutes.paragraphs(segments, 30_000),
        )
    }

    @Test
    fun segmentsRoundTripThroughWindowFile() {
        val segments = listOf(Segment(0, "こんにちは"), Segment(1_500, "改行\nとタブ\tを含む"))
        assertEquals(
            listOf(Segment(0, "こんにちは"), Segment(1_500, "改行 とタブ を含む")),
            Minutes.decodeSegments(Minutes.encodeSegments(segments)),
        )
    }

    @Test
    fun markdownHasHeaderAndParagraphs() {
        val markdown = Minutes.markdown(
            "20261023_090507", 90_500,
            listOf(Segment(0, "はじめます"), Segment(40_000, "次の話題")),
        )
        assertEquals("録音: 2026-10-23 09:05（2分）\n\n[0:00] はじめます\n\n[0:40] 次の話題\n", markdown)
    }
}
