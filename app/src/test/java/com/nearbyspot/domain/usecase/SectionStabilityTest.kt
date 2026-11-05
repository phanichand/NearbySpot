package com.nearbyspot.domain.usecase

import com.nearbyspot.domain.model.PickKind
import com.nearbyspot.domain.model.RouteSection
import com.nearbyspot.domain.model.SectionPick
import com.nearbyspot.domain.model.SectionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SectionStabilityTest {

    private fun pick(
        id: String,
        rating: Double,
        reachMinutes: Int,
        kinds: Set<PickKind> = setOf(PickKind.POPULAR),
        routeIndex: Int = 20,
    ) =
        SectionPick(
            candidate = alongRoutePlace(id, rating = rating, reachMinutes = reachMinutes)
                .copy(nearestRoutePointIndex = routeIndex),
            kinds = kinds,
            extraMinutesVsBaseline = 0,
            extraStarsVsBaseline = 0.0,
            recencySignal = null,
        )

    private fun section(vararg picks: SectionPick) = RouteSection(
        index = 0,
        startRouteIndex = 0,
        endRouteIndex = 50,
        aheadSeconds = 0,
        status = SectionStatus.LOADED,
        picks = picks.toList(),
    )

    @Test
    fun `merge returns the fresh section when nothing was shown`() {
        val fresh = section(pick("b", 4.5, 15))

        assertEquals(fresh, SectionStability.merge(section(), fresh, currentRouteIndex = 0))
    }

    @Test
    fun `merge keeps a shown pick unless the fresh one clears the margin`() {
        val shown = section(pick("a", 4.5, 15))

        val slightlyBetter = SectionStability.merge(shown, section(pick("b", 4.55, 15)), currentRouteIndex = 0)
        val clearlyBetter = SectionStability.merge(shown, section(pick("b", 4.9, 15)), currentRouteIndex = 0)

        assertEquals("a", slightlyBetter.picks.single().candidate.place.placeId)
        assertEquals("b", clearlyBetter.picks.single().candidate.place.placeId)
    }

    @Test
    fun `merge drops a shown pick that is no longer within 45 minutes`() {
        val shown = section(pick("a", 4.5, 46))

        val merged = SectionStability.merge(shown, section(pick("b", 4.5, 15)), currentRouteIndex = 0)

        assertEquals("b", merged.picks.single().candidate.place.placeId)
    }

    @Test
    fun `merge uses fresh data when the same place is in both`() {
        val shown = section(pick("a", 4.5, 30))

        val merged = SectionStability.merge(shown, section(pick("a", 4.5, 12)), currentRouteIndex = 0)

        assertEquals(12 * 60, merged.picks.single().candidate.reachableSeconds)
    }

    @Test
    fun `merge keeps the fresh section range status and note`() {
        val fresh = section(pick("b", 4.5, 15)).copy(endRouteIndex = 77)

        assertEquals(77, SectionStability.merge(section(pick("a", 4.5, 15)), fresh, currentRouteIndex = 0).endRouteIndex)
    }

    @Test
    fun `merge drops a shown pick the traveller has already driven past`() {
        val shown = section(pick("passed", 4.5, 15, routeIndex = 10))
        val fresh = section(pick("ahead", 4.5, 15, routeIndex = 40))

        val merged = SectionStability.merge(shown, fresh, currentRouteIndex = 25)

        assertEquals("ahead", merged.picks.single().candidate.place.placeId)
    }

    @Test
    fun `merge does not keep a shown pick when the fresh section has no qualifying place`() {
        val shown = section(pick("a", 4.5, 15))
        val fresh = section().copy(status = SectionStatus.NO_QUALIFYING_PLACE)

        val merged = SectionStability.merge(shown, fresh, currentRouteIndex = 0)

        assertEquals(SectionStatus.NO_QUALIFYING_PLACE, merged.status)
        assertEquals(emptyList<SectionPick>(), merged.picks)
    }
}
