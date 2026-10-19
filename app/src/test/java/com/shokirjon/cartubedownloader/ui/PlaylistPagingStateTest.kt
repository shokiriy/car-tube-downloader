package com.shokirjon.cartubedownloader.ui

import com.shokirjon.cartubedownloader.db.PlaylistEntry
import org.junit.Assert.*
import org.junit.Test

class PlaylistPagingStateTest {
    private fun entry(index: Int) = PlaylistEntry("PLtest", "id${index.toString().padStart(9, '0')}", "Video $index",
        "https://www.youtube.com/watch?v=id${index.toString().padStart(9, '0')}", null, index, false, false, 1L)

    @Test fun appendsPagesWithoutDuplicatesAndBlocksConcurrentRequests() {
        val state = PlaylistPagingState("PLtest", 24)
        assertEquals(0, state.begin())
        assertNull(state.begin())
        state.append((0 until 24).map(::entry))
        assertEquals(24, state.begin())
        state.append((20 until 44).map(::entry))
        assertEquals(44, state.items.size)
        assertEquals(43, state.items.last().position)
        assertEquals(44, state.begin())
        state.append((44 until 52).map(::entry))
        assertTrue(state.exhausted)
        assertNull(state.begin())
    }

    @Test fun failedPageCanRetryWithoutClearingPriorSelection() {
        val state = PlaylistPagingState("PLtest", 24)
        state.begin()
        state.append((0 until 24).map(::entry))
        state.update("id000000015") { it.copy(selected = true) }
        assertEquals(24, state.begin())
        state.fail()
        assertTrue(state.failed)
        assertEquals(24, state.begin())
        state.append((24 until 48).map(::entry))
        assertTrue(state.items[15].selected)
        assertFalse(state.failed)
    }
}
