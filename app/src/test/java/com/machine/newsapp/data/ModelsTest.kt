package com.machine.newsapp.data

import com.machine.newsapp.notifications.nextMorning
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

class ModelsTest {
    @Test fun `links allow only public web schemes`() {
        assertTrue(isWebUrl("https://example.com/a"))
        assertFalse(isWebUrl("javascript:alert(1)"))
        assertFalse(isWebUrl("file:///tmp/a"))
        assertFalse(isWebUrl("https://user:secret@example.com"))
        assertFalse(isWebUrl("http://example.com/feed", httpsOnly = true))
    }
    @Test fun `expiration includes the final local day`() {
        val deal = Deal("Free tool", "Free software", "https://example.com", "Example", "Dev tools", "2026-10-09")
        assertTrue(deal.isActive(LocalDate.parse("2026-10-09")))
        assertFalse(deal.isActive(LocalDate.parse("2026-10-10")))
        assertFalse(deal.copy(expires = "invalid").isActive())
        assertFalse(deal.copy(title = "Free SIM card").isSoftwareOffer())
    }
    @Test fun `time ago handles future and invalid timestamps`() {
        val now = Instant.parse("2026-10-09T09:00:00Z")
        assertEquals("1h ago", timeAgo("2026-10-09T08:00:00Z", now))
        assertEquals("Just now", timeAgo("2026-10-10T08:00:00Z", now))
        assertEquals("Recently", timeAgo("invalid", now))
    }
    @Test fun `daily schedule chooses next local nine including DST`() {
        val before = ZonedDateTime.parse("2026-10-09T08:59:00+05:30[Asia/Kolkata]")
        assertEquals("2026-10-09T09:00+05:30[Asia/Kolkata]", nextMorning(before).toString())
        assertEquals(10, nextMorning(before.plusMinutes(1)).dayOfMonth)
        val dst = ZonedDateTime.parse("2026-03-07T10:00:00-05:00[America/New_York]")
        assertEquals("2026-03-08T09:00-04:00[America/New_York]", nextMorning(dst).toString())
    }
}
