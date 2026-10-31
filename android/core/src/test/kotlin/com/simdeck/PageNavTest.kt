package com.simdeck

import com.simdeck.model.Page
import com.simdeck.profiles.LayoutOps
import kotlin.test.Test
import kotlin.test.assertEquals

/** Page switch controls, which in split view (1.6) turn the page of their own half. */
class PageNavTest {
    @Test fun pageControlsWrapAndFindTheirTarget() {
        val pages = listOf(Page("drive", "Drive"), Page("cab", "Cab"), Page("crane", "Crane"))
        assertEquals(1, LayoutOps.pageAfter(pages, 0, null))
        assertEquals(1, LayoutOps.pageAfter(pages, 0, "next"))
        assertEquals(0, LayoutOps.pageAfter(pages, 2, "next"))
        assertEquals(2, LayoutOps.pageAfter(pages, 0, "prev"))
        assertEquals(2, LayoutOps.pageAfter(pages, 0, "crane"))
        assertEquals(1, LayoutOps.pageAfter(pages, 0, "2"))
        assertEquals(2, LayoutOps.pageAfter(pages, 0, "99"))
        assertEquals(1, LayoutOps.pageAfter(pages, 1, "nowhere"))
        assertEquals(0, LayoutOps.pageAfter(emptyList(), 0, "next"))
    }
}
