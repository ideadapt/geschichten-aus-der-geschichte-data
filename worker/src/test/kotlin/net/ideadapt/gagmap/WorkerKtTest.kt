package net.ideadapt.gagmap

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorkerKtTest {

    @Test
    fun extractTemporalRefs() {
        val refs = extractTemporalRefs(
            """
        lorem 14. September 2022 ipsum
        lorem September 2022 ipsum
        lorem 19. Jahrhundert vdZw ipsum
        lorem 19. Jahrhundert ipsum
        lorem Jahr 2022 vdZw ipsum
        lorem Jahr 2022 ipsum
        lorem 2000er Jahre ipsum
   """
        )
        assertEquals(7, refs.size)
    }
}
