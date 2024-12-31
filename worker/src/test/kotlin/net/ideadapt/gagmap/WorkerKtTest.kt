package net.ideadapt.gagmap

import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorkerKtTest {

    @Test
    fun extractTemporalRefs() {
        val refs = extractTemporalRefs(
            """
        lorem 14. Januar 2022 ipsum
        lorem 4. Februar 2022 ipsum
        lorem Oktober 2023 ipsum
        lorem 19. Jahrhundert vdZw ipsum
        lorem 19. Jahrhundert ipsum
        lorem 1. Jahrhundert ipsum
        lorem Jahr 2022 vdZw ipsum
        lorem Jahr 2022 ipsum
        lorem 2000er Jahre ipsum
        lorem 80er Jahre ipsum
   """
        )

        assertEquals("14. Januar 2022", refs[0].literal)
        assertEquals(Instant.parse("2022-01-14T00:00:00Z"), refs[0].start)
        assertEquals(Instant.parse("2022-01-14T23:59:59Z"), refs[0].end)
        assertEquals("4. Februar 2022", refs[1].literal)
        assertEquals("Oktober 2023", refs[2].literal)
        assertEquals(Instant.parse("2023-10-01T00:00:00Z"), refs[2].start)
        assertEquals(Instant.parse("2023-10-01T23:59:59Z"), refs[2].end)
        assertEquals("19. Jh. vdZw", refs[3].literal)
        assertEquals(Instant.parse("-1900-01-01T00:00:00Z"), refs[3].start)
        assertEquals(Instant.parse("-1801-12-31T23:59:59Z"), refs[3].end)
        assertEquals("19. Jh.", refs[4].literal)
        assertEquals(Instant.parse("1801-01-01T00:00:00Z"), refs[4].start)
        assertEquals(Instant.parse("1900-12-31T23:59:59Z"), refs[4].end)
        assertEquals("1. Jh.", refs[5].literal)
        assertEquals(Instant.parse("0001-01-01T00:00:00Z"), refs[5].start)
        assertEquals(Instant.parse("0100-12-31T23:59:59Z"), refs[5].end)
        assertEquals("2022 vdZw", refs[6].literal)
        assertEquals("2022", refs[7].literal)
        assertEquals("2000er Jahre", refs[8].literal)
        assertEquals("1980er Jahre", refs[9].literal)
    }
}
