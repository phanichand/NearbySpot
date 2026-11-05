package com.nearbyspot.presentation.route

import com.nearbyspot.domain.model.PickKind
import com.nearbyspot.domain.model.Place
import com.nearbyspot.domain.model.AlongRoutePlace
import com.nearbyspot.domain.model.RouteSection
import com.nearbyspot.domain.model.SectionPick
import com.nearbyspot.domain.model.SectionStatus
import com.nearbyspot.domain.usecase.RecencySignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteSectionPresentationTest {

    private fun pick(
        id: String,
        extraMinutes: Int = 0,
        extraStars: Double = 0.0,
        recency: RecencySignal? = null,
    ) = SectionPick(
        candidate = AlongRoutePlace(
            place = Place(id, id, "", 0.0, 0.0, 4.4, 1_200, null, true, emptyList(), emptyList(), emptyList()),
            reachableSeconds = 900,
            detourSeconds = 0,
            nearestRoutePointIndex = 0,
            score = 0.0,
        ),
        kinds = setOf(PickKind.POPULAR),
        extraMinutesVsBaseline = extraMinutes,
        extraStarsVsBaseline = extraStars,
        recencySignal = recency,
    )

    private fun section(index: Int, vararg picks: SectionPick) = RouteSection(
        index = index,
        startRouteIndex = 0,
        endRouteIndex = 0,
        aheadSeconds = index * 3_600,
        status = SectionStatus.LOADED,
        picks = picks.toList(),
    )

    @Test
    fun `first section title is Next stop`() {
        assertEquals(SectionTitle.NextStop, sectionTitle(aheadSeconds = 0))
    }

    @Test
    fun `later section title rounds hours ahead and is at least one`() {
        assertEquals(SectionTitle.HoursIn(1), sectionTitle(2_700))
        assertEquals(SectionTitle.HoursIn(2), sectionTitle(6_300))
        assertEquals(SectionTitle.HoursIn(5), sectionTitle(17_100))
    }

    @Test
    fun `pick reason omits the comparison when the extras are zero`() {
        val reason = pickReason(pick("a"))

        assertNull(reason.extraMinutes)
        assertNull(reason.extraStars)
        assertEquals(4.4, reason.rating, 1e-9)
        assertEquals(1_200, reason.reviewCount)
    }

    @Test
    fun `pick reason includes the comparison when the pick costs extra time`() {
        val reason = pickReason(pick("a", extraMinutes = 22, extraStars = 0.4))

        assertEquals(22, reason.extraMinutes)
        assertEquals(0.4, reason.extraStars!!, 1e-9)
    }

    @Test
    fun `pick reason includes the recent reviews line only with a signal`() {
        val without = pickReason(pick("a"))
        val with = pickReason(pick("a", recency = RecencySignal(recentCount = 20, recentPositiveCount = 18)))

        assertNull(without.recentPositive)
        assertEquals(18, with.recentPositive)
        assertEquals(20, with.recentTotal)
    }

    @Test
    fun `timeline item index counts headers and the picks of expanded sections only`() {
        val sections = listOf(
            section(0, pick("a"), pick("b")),
            section(1, pick("c")),
            section(2, pick("d")),
        )

        assertEquals(2, timelineItemIndex(sections, expandedIndex = null, placeId = "b"))
        assertNull(timelineItemIndex(sections, expandedIndex = null, placeId = "c"))
        assertEquals(4, timelineItemIndex(sections, expandedIndex = 1, placeId = "c"))
        assertEquals(5, timelineItemIndex(sections, expandedIndex = 2, placeId = "d"))
    }
}
