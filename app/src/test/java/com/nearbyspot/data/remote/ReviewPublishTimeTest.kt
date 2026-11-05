package com.nearbyspot.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReviewPublishTimeTest {

    @Test
    fun `parses an RFC 3339 timestamp to epoch millis`() {
        assertEquals(86_400_000L, parseReviewPublishTime("1970-01-02T00:00:00Z"))
        assertEquals(86_400_123L, parseReviewPublishTime("1970-01-02T00:00:00.123456Z"))
    }

    @Test
    fun `null blank or malformed timestamps return null`() {
        assertNull(parseReviewPublishTime(null))
        assertNull(parseReviewPublishTime(""))
        assertNull(parseReviewPublishTime("two months ago"))
    }
}
