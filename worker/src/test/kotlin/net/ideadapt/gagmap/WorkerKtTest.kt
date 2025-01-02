package net.ideadapt.gagmap

import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorkerKtTest {

    @Test
    fun `extractTemporalRefs day`() {
        val refs = extractTemporalRefs(
            """
        lorem 14. Januar 2022 ipsum
        lorem 4. Februar 2022 ipsum
   """
        )

        assertEquals("14. Januar 2022", refs[0].literal)
        assertEquals(Instant.parse("2022-01-14T00:00:00Z"), refs[0].start)
        assertEquals(Instant.parse("2022-01-14T23:59:59Z"), refs[0].end)
        assertEquals("4. Februar 2022", refs[1].literal)
    }

    @Test
    fun `extractTemporalRefs month`() {
        val refs = extractTemporalRefs(
            """
        lorem Oktober 2023 ipsum
   """
        )

        assertEquals("Oktober 2023", refs[0].literal)
        assertEquals(Instant.parse("2023-10-01T00:00:00Z"), refs[0].start)
        assertEquals(Instant.parse("2023-10-01T23:59:59Z"), refs[0].end)
    }

    @Test
    fun `extractTemporalRefs Jahrhundert`() {
        val refs = extractTemporalRefs(
            """
        lorem 19. Jahrhundert vdZw ipsum
        lorem 19. Jahrhunderts ipsum
        lorem 1. Jahrhundert ipsum
   """
        )

        assertEquals("19. Jahrhundert vdZw", refs[0].literal)
        assertEquals(Instant.parse("-1900-01-01T00:00:00Z"), refs[0].start)
        assertEquals(Instant.parse("-1801-12-31T23:59:59Z"), refs[0].end)
        assertEquals("19. Jahrhunderts", refs[1].literal)
        assertEquals(Instant.parse("1801-01-01T00:00:00Z"), refs[1].start)
        assertEquals(Instant.parse("1900-12-31T23:59:59Z"), refs[1].end)
        assertEquals("1. Jahrhundert", refs[2].literal)
        assertEquals(Instant.parse("0001-01-01T00:00:00Z"), refs[2].start)
        assertEquals(Instant.parse("0100-12-31T23:59:59Z"), refs[2].end)
    }

    @Test
    fun `extractTemporalRefs year`() {
        val refs = extractTemporalRefs(
            """
        lorem Jahr 2022 vdZw ipsum
        lorem Jahr 2022 ipsum
   """
        )

        assertEquals("Jahr 2022 vdZw", refs[0].literal)
        assertEquals(Instant.parse("-2022-01-01T00:00:00Z"), refs[0].start)
        assertEquals(Instant.parse("-2022-12-31T23:59:59Z"), refs[0].end)
        assertEquals("Jahr 2022", refs[1].literal)
        assertEquals(Instant.parse("2022-01-01T00:00:00Z"), refs[1].start)
        assertEquals(Instant.parse("2022-12-31T23:59:59Z"), refs[1].end)
    }

    @Test
    fun `extractTemporalRefs Jahrzehnt bzw Dekade absolute`() {
        val refs = extractTemporalRefs(
            """
        lorem 2000er Jahre ipsum
        lorem 1980er Jahren ipsum
        lorem 1980er Jahre vdZw ipsum
        lorem 0er Jahre ipsum
        lorem 0er Jahre vdZw ipsum
   """
        )

        assertEquals("2000er Jahre", refs[0].literal)
        assertEquals(Instant.parse("2000-01-01T00:00:00Z"), refs[0].start)
        assertEquals(Instant.parse("2009-12-31T23:59:59Z"), refs[0].end)
        assertEquals("1980er Jahren", refs[1].literal)
        assertEquals(Instant.parse("1980-01-01T00:00:00Z"), refs[1].start)
        assertEquals(Instant.parse("1989-12-31T23:59:59Z"), refs[1].end)
        assertEquals("1980er Jahre vdZw", refs[2].literal)
        assertEquals(Instant.parse("-1989-01-01T00:00:00Z"), refs[2].start)
        assertEquals(Instant.parse("-1980-12-31T23:59:59Z"), refs[2].end)
        assertEquals("0er Jahre", refs[3].literal)
        assertEquals(Instant.parse("0001-01-01T00:00:00Z"), refs[3].start)
        assertEquals(Instant.parse("0009-12-31T23:59:59Z"), refs[3].end)
        assertEquals("0er Jahre vdZw", refs[4].literal)
        assertEquals(Instant.parse("-0009-01-01T00:00:00Z"), refs[4].start)
        assertEquals(Instant.parse("-0001-12-31T23:59:59Z"), refs[4].end)
    }

    @Test
    fun `extractTemporalRefs Jahrzehnt bzw Dekade relative`() {
        val refs = extractTemporalRefs(
            """
        lorem 60er Jahren des 19. Jahrhundert ipsum
        lorem 20er Jahren des 16. Jahrhunderts vdZw ipsum
        lorem 80er Jahre des 19. JH ipsum
   """
        )

        assertEquals("60er Jahren des 19. Jahrhundert", refs[0].literal)
        assertEquals(Instant.parse("1860-01-01T00:00:00Z"), refs[0].start)
        assertEquals(Instant.parse("1869-12-31T23:59:59Z"), refs[0].end)
        assertEquals("20er Jahren des 16. Jahrhunderts vdZw", refs[1].literal)
        assertEquals(Instant.parse("-1529-01-01T00:00:00Z"), refs[1].start)
        assertEquals(Instant.parse("-1520-12-31T23:59:59Z"), refs[1].end)
        assertEquals("80er Jahre des 19. JH", refs[2].literal)
        assertEquals(Instant.parse("1880-01-01T00:00:00Z"), refs[2].start)
        assertEquals(Instant.parse("1889-12-31T23:59:59Z"), refs[2].end)
    }
}
