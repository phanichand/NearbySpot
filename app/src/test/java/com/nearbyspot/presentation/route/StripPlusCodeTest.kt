package com.nearbyspot.presentation.route

import com.nearbyspot.R
import org.junit.Assert.assertEquals
import org.junit.Test

class StripPlusCodeTest {

    @Test
    fun `leading plus code is removed`() {
        assertEquals(
            "NH766, Darbandora, Goa 403406, India",
            stripPlusCode("94RC+RJC, NH766, Darbandora, Goa 403406, India"),
        )
    }

    @Test
    fun `street addresses are unchanged`() {
        assertEquals("12 MG Road, Bengaluru", stripPlusCode("12 MG Road, Bengaluru"))
    }

    @Test
    fun `blank address stays blank`() {
        assertEquals("", stripPlusCode(""))
    }
}

class PlaceTypeLabelTest {

    @Test
    fun `labels follow API type order and are capped at two`() {
        assertEquals(
            listOf(R.string.place_type_seafood, R.string.place_type_bar),
            placeTypeLabelResIds(
                listOf("seafood_restaurant", "bar", "indian_restaurant", "restaurant", "food"),
            ),
        )
    }

    @Test
    fun `cafe and coffee shop share one label and unknown types are ignored`() {
        assertEquals(
            listOf(R.string.place_type_cafe),
            placeTypeLabelResIds(listOf("coffee_shop", "cafe", "point_of_interest")),
        )
        assertEquals(emptyList<Int>(), placeTypeLabelResIds(listOf("restaurant", "food")))
    }
}
