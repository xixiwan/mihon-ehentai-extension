package eu.kanade.tachiyomi.extension.en.ehentai

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the configured browser User-Agent to every request, keeps site requests
 * at least `requestIntervalMs` apart, and sends the login cookie only to
 * e-hentai.org / exhentai.org (and their subdomains).
 *
 * The throttle lives here (not in the page fetch helper) so that every request
 * the app makes through the source client is covered — including the app-side
 * image URL resolution and original-image downloads.
 *
 * Referer headers are set at request-construction time (page requests use
 * `baseUrl/`, image requests use the viewer page URL) because the two kinds
 * of requests are built by different code paths.
 */
class EhentaiInterceptor(private val prefs: EhentaiPreferences) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder()

        builder.header("User-Agent", prefs.userAgent)

        val host = request.url.host
        if (prefs.isSiteHost(host)) {
            // One limiter for every site request (list / gallery / viewer / original
            // image downloads), so the interval also holds while several downloads
            // run in parallel. Image CDN hosts never reach this branch.
            EhentaiRateLimiter.acquire(prefs.requestIntervalMs)

            val cookie = prefs.cookie
            if (cookie.isNotEmpty()) {
                builder.header("Cookie", cookie)
            }
        }

        return chain.proceed(builder.build())
    }
}
