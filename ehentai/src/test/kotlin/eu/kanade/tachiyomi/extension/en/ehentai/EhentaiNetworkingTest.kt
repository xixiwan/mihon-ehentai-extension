package eu.kanade.tachiyomi.extension.en.ehentai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests for the throttle-page detection, the viewer → image URL cache and the
 * retry delay used when E-Hentai answers with a rate-limit status.
 */
class EhentaiNetworkingTest {

    // ------------------------------------------------------------------
    // detectSiteBlock
    // ------------------------------------------------------------------

    @Test
    fun `detectSiteBlock - IP ban page`() {
        val html = "<html><body>Your IP address has been temporarily banned " +
            "for excessive pageloads. The ban expires in 1 days.</body></html>"
        assertEquals(SiteBlockKind.IP_BAN, detectSiteBlock(html))
    }

    @Test
    fun `detectSiteBlock - image limit notice`() {
        val html = "<html><body><p>You have exceeded your image viewing limits. " +
            "Please wait before viewing more images.</p></body></html>"
        assertEquals(SiteBlockKind.IMAGE_LIMIT, detectSiteBlock(html))
    }

    @Test
    fun `detectSiteBlock - exhentai sad panda`() {
        val html = "<html><body><img src=\"https://exhentai.org/img/sadpanda.png\"></body></html>"
        assertEquals(SiteBlockKind.SAD_PANDA, detectSiteBlock(html))
    }

    @Test
    fun `detectSiteBlock - ordinary pages are not flagged`() {
        val pages = listOf("search.html", "gallery.html", "viewer.html")
        pages.forEach { name ->
            val html = File("src/test/resources/$name").readText()
            assertNull("$name must not look like a throttle page", detectSiteBlock(html))
        }
    }

    @Test
    fun `detectSiteBlock - a marker far down a long page is ignored`() {
        val filler = "<div>gallery description</div>".repeat(600)
        val html = filler + "temporarily banned for excessive pageloads"
        assertTrue(html.length > Constants.SITE_BLOCK_SCAN_LIMIT)
        assertNull(detectSiteBlock(html))
    }

    // ------------------------------------------------------------------
    // ViewerImageCache
    // ------------------------------------------------------------------

    private class TestClock(var now: Long = 1_000L) : () -> Long {
        override fun invoke(): Long = now
    }

    @Test
    fun `viewer image cache - returns the value within the ttl`() {
        val clock = TestClock()
        val cache = ViewerImageCache(ttlMs = 1_000L, maxEntries = 10, clock = clock)

        cache.put("standard|/s/abc/1-1", "https://img/1.webp")
        clock.now += 999L
        assertEquals("https://img/1.webp", cache.get("standard|/s/abc/1-1"))
    }

    @Test
    fun `viewer image cache - expires after the ttl`() {
        val clock = TestClock()
        val cache = ViewerImageCache(ttlMs = 1_000L, maxEntries = 10, clock = clock)

        cache.put("standard|/s/abc/1-1", "https://img/1.webp")
        clock.now += 1_001L
        assertNull(cache.get("standard|/s/abc/1-1"))
        assertEquals(0, cache.size())
    }

    @Test
    fun `viewer image cache - quality is part of the key`() {
        val clock = TestClock()
        val cache = ViewerImageCache(ttlMs = 1_000L, maxEntries = 10, clock = clock)

        cache.put("standard|/s/abc/1-1", "https://img/standard.webp")
        cache.put("original|/s/abc/1-1", "https://img/original.jpg")
        assertEquals("https://img/standard.webp", cache.get("standard|/s/abc/1-1"))
        assertEquals("https://img/original.jpg", cache.get("original|/s/abc/1-1"))
    }

    @Test
    fun `viewer image cache - stays within the entry bound`() {
        val clock = TestClock()
        val cache = ViewerImageCache(ttlMs = 1_000L, maxEntries = 2, clock = clock)

        cache.put("a", "1")
        cache.put("b", "2")
        cache.put("c", "3")

        assertTrue(cache.size() <= 2)
        assertEquals("3", cache.get("c"))
    }

    // ------------------------------------------------------------------
    // retryDelayMs
    // ------------------------------------------------------------------

    @Test
    fun `retryDelayMs - backs off exponentially without a hint`() {
        assertEquals(1_000L, retryDelayMs(null, 0))
        assertEquals(2_000L, retryDelayMs(null, 1))
        assertEquals(4_000L, retryDelayMs(null, 2))
    }

    @Test
    fun `retryDelayMs - honours Retry-After seconds`() {
        assertEquals(3_000L, retryDelayMs("3", 0))
        assertEquals(3_000L, retryDelayMs(" 3 ", 2))
    }

    @Test
    fun `retryDelayMs - caps a long Retry-After`() {
        assertEquals(10_000L, retryDelayMs("600", 0))
    }
}
