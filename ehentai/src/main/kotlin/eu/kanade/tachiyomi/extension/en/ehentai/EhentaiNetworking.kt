package eu.kanade.tachiyomi.extension.en.ehentai

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Shared networking helpers for both builds (extensions-lib 1.4 and 1.6).
 *
 * Everything here exists because E-Hentai rate-limits by IP: a burst of page-type
 * requests (list / gallery / viewer) is answered with a ban page, an "image limit"
 * placeholder or exhentai's "sad panda", and every later request fails until the
 * limit lifts. Keeping the request rate down and telling those pages apart from
 * ordinary layout changes is what keeps the source usable during long downloads.
 */

// ---------------------------------------------------------------------------
// Request rate
// ---------------------------------------------------------------------------

/**
 * Serialises requests to e-hentai.org / exhentai.org across all threads of the
 * process, so the configured interval holds even while several downloads run.
 *
 * Only site hosts go through here; image CDN requests (hath.network, ehgt.org)
 * are not rate-limited by the site and are left alone.
 */
internal object EhentaiRateLimiter {

    private val lastRequestAt = AtomicLong(0L)

    /** Blocks until at least [intervalMs] has passed since the previous site request. */
    fun acquire(intervalMs: Long) {
        if (intervalMs <= 0L) return
        while (true) {
            val now = System.currentTimeMillis()
            val last = lastRequestAt.get()
            val waitMs = intervalMs - (now - last)
            if (waitMs <= 0L) {
                if (lastRequestAt.compareAndSet(last, now)) return
            } else {
                Thread.sleep(waitMs)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Viewer page -> image URL cache
// ---------------------------------------------------------------------------

/**
 * Remembers the image URL resolved from a viewer page, so re-reading a chapter
 * (or retrying a failed download) does not hit the site once per page again.
 *
 * The image URLs carry a `keystamp` that the site expires after a few hours;
 * [Constants.VIEWER_IMAGE_CACHE_TTL_MS] (30 minutes) is well inside that window.
 * [ttlMs] and [clock] are injectable for tests.
 */
class ViewerImageCache(
    private val ttlMs: Long = Constants.VIEWER_IMAGE_CACHE_TTL_MS,
    private val maxEntries: Int = Constants.VIEWER_IMAGE_CACHE_MAX_ENTRIES,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private data class Entry(val imageUrl: String, val expiresAt: Long)

    private val entries = ConcurrentHashMap<String, Entry>()

    fun get(key: String): String? {
        val entry = entries[key] ?: return null
        if (entry.expiresAt <= clock()) {
            entries.remove(key, entry)
            return null
        }
        return entry.imageUrl
    }

    fun put(key: String, imageUrl: String) {
        if (entries.size >= maxEntries) {
            val now = clock()
            entries.entries.removeAll { it.value.expiresAt <= now }
            if (entries.size >= maxEntries) entries.clear()
        }
        entries[key] = Entry(imageUrl, clock() + ttlMs)
    }

    fun size(): Int = entries.size
}

// ---------------------------------------------------------------------------
// Site block pages
// ---------------------------------------------------------------------------

/** What the site served instead of the requested page. */
enum class SiteBlockKind {
    /** IP temporarily banned for excessive page loads (E-Hentai ban page). */
    IP_BAN,

    /** Image viewing quota exhausted (image loads return a notice instead). */
    IMAGE_LIMIT,

    /** exhentai's "sad panda": invalid/missing login cookie or refused network exit. */
    SAD_PANDA,
}

/**
 * Markers of the pages E-Hentai serves when it is throttling. Deliberately
 * narrow phrases (not just "banned") so gallery text cannot trigger them.
 * Only the first [Constants.SITE_BLOCK_SCAN_LIMIT] characters are inspected:
 * those pages carry the notice at the top, while a long gallery description
 * must not turn a normal page into a false positive.
 */
private val SITE_BLOCK_MARKERS: List<Pair<SiteBlockKind, List<String>>> = listOf(
    SiteBlockKind.IP_BAN to listOf(
        "temporarily banned for excessive",
        "excessive pageloads",
        "excessive page loads",
    ),
    SiteBlockKind.IMAGE_LIMIT to listOf(
        "image viewing limits",
        "image limit reached",
        "you have exceeded your image",
    ),
    SiteBlockKind.SAD_PANDA to listOf(
        "sadpanda",
    ),
)

/** The throttling page [html] represents, or null when it is a normal page. */
fun detectSiteBlock(html: String): SiteBlockKind? {
    val head = if (html.length > Constants.SITE_BLOCK_SCAN_LIMIT) {
        html.substring(0, Constants.SITE_BLOCK_SCAN_LIMIT)
    } else {
        html
    }
    return SITE_BLOCK_MARKERS.firstOrNull { (_, markers) ->
        markers.any { head.contains(it, ignoreCase = true) }
    }?.first
}

// ---------------------------------------------------------------------------
// Error messages (shared by both builds so the advice stays identical)
// ---------------------------------------------------------------------------

fun httpErrorMessage(code: Int, url: String): String {
    val hint = when {
        Constants.isRetryableHttpCode(code) ->
            "站点限流或暂时不可用，已按上限重试。请把插件设置里的「请求间隔」调大、" +
                "降低同时下载的数量，稍后再试。"
        code == 403 || code == 503 ->
            "可能是 Cloudflare 拦截或登录 Cookie 失效；可尝试更换 User-Agent，" +
                "并确认 Cookie 仍然有效。"
        else -> "请检查网络连接，稍后再试。"
    }
    return "E-Hentai 返回 HTTP $code（$url）。$hint"
}

fun networkErrorMessage(url: String, cause: String): String =
    "无法连接 $url（$cause）。请检查网络连接与 User-Agent / 登录 Cookie 设置。"

fun siteBlockErrorMessage(kind: SiteBlockKind, url: String): String = when (kind) {
    SiteBlockKind.IP_BAN ->
        "站点提示当前 IP 被临时封禁（excessive pageloads，$url）。请停止请求并等待解除：" +
            "把「请求间隔」调大、降低并发下载数量；如使用代理或共享 IP，可更换出口后再试。"
    SiteBlockKind.IMAGE_LIMIT ->
        "站点图片浏览配额已用尽（$url）。配额会随时间恢复（约每分钟几点），" +
            "请稍后再试，并降低同时下载的数量。"
    SiteBlockKind.SAD_PANDA ->
        "exhentai 返回了「sad panda」页面（$url）。通常是登录 Cookie 无效或缺失，" +
            "或当前网络出口不被站点接受；请检查会员 ID / 密码哈希，必要时更换网络后再试。"
}

/**
 * Delay before retrying a retryable response: the server's Retry-After when it
 * sent one, otherwise 1s, 2s, 4s… capped at [Constants.RETRY_MAX_DELAY_MS].
 */
fun retryDelayMs(retryAfterHeader: String?, attempt: Int): Long {
    val hinted = retryAfterHeader?.trim()?.toLongOrNull()?.times(1000L)
    return (hinted ?: (Constants.RETRY_BASE_DELAY_MS shl attempt))
        .coerceIn(500L, Constants.RETRY_MAX_DELAY_MS)
}
