package app.gympulse.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdsTest {
    @Test
    fun newId_isVersion7AndSortableByTime() {
        val first = Ids.newId(nowMillis = 1_700_000_000_000)
        val second = Ids.newId(nowMillis = 1_700_000_000_001)
        assertEquals(36, first.length)
        assertEquals('7', first[14])
        assertTrue(first[19] in "89ab")
        assertTrue(first < second)
        assertNotEquals(Ids.newId(), Ids.newId())
    }
}
