package com.mathis.mandala.app

import com.mathis.mandala.editing.Editing
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentImagesTest {
    @Test
    fun recentImagesKeepTheLastFewAndFindEqualSettings() {
        val recent = RecentImages<com.mathis.mandala.core.MandalaParameters, String>(2)
        val a = Editing.initial(1uL)
        val b = Editing.initial(2uL)
        recent[a] = "a"
        recent[b] = "b"
        assertEquals("a", recent[a.copy()])
        recent[Editing.initial(3uL)] = "c"
        // b was used least recently once a was read, so b fell out.
        assertEquals(null, recent[b])
        assertEquals(2, recent.size)
    }
}
