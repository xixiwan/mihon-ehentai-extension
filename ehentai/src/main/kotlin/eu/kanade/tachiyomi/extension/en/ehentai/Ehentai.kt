package eu.kanade.tachiyomi.extension.en.ehentai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import rx.Observable
import androidx.preference.PreferenceScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

/**
 * E-Hentai (e-hentai.org) source for Mihon / Tachimanga / Tachiyomi forks.
 *
 * Built against extensions-lib 1.4 (tachiyomiorg, the classic RxJava 1
 * Observable API), so it loads in every app that supports the 1.4 extension
 * format: Mihon (lib 1.4 is in its SUPPORTED_LIB_VERSIONS), Tachimanga and
 * older Tachiyomi forks. The legacy API is intentionally used because the
 * modern suspend API (keiyoushi, lib 1.6) is not understood by Tachimanga
 * and other apps — that mismatch shows up as `java.lang.VerifyError` when
 * loading the class.
 *
 * Site facts (verified 2026-08-15):
 * - list/search/popular pages share one row structure (`table.itg tr` rows);
 * - search pagination is cursor based (`next`/`prev` parameters) — `page=N`
 *   is ignored by the server, so the cursor of the previous request is reused;
 * - the gallery page lists viewer URLs (`#gdt a[href*=/s/]`) with `?p=N`
 *   pagination (20 thumbnails per page);
 * - images are resolved lazily per page from the viewer page (`img#img`,
 *   original via the `/fullimg/` link when logged in).
 *
 * The login cookie is only ever sent to e-hentai.org / exhentai.org hosts;
 * exhentai.org additionally requires it (friendly error otherwise).
 */
class Ehentai : HttpSource(), ConfigurableSource {

    override val name = "E-Hentai"
    override val lang = "en"
    override val supportsLatest = false
    override val versionId = 1

    private val preferences: android.content.SharedPreferences by lazy {
        Injekt.get<android.app.Application>()
            .getSharedPreferences("source_$id", android.content.Context.MODE_PRIVATE)
    }

    private val prefs by lazy { EhentaiPreferences(preferences) }

    override val baseUrl: String
        get() = prefs.baseUrl

    override val client: OkHttpClient by lazy {
        network.client.newBuilder()
            .addInterceptor(EhentaiInterceptor(prefs))
            .build()
    }

    /** Cursor of the next results page, keyed by the requested search URL. */
    private val nextPageCursors = ConcurrentHashMap<String, String>()

    /**
     * URL requested for a page of a search, so its cursor can be found afterwards.
     *
     * The site ignores `page=N`, so page N+1 is requested with the cursor that
     * page N carried — and page N's URL is only known here, on the request side.
     * Looking the cursor up under the page-1 URL instead (1.4.4) made every page
     * from the third repeat the second one.
     */
    private data class RequestedPage(val searchUrl: String, val url: String)

    private val requestedPages = ConcurrentHashMap<Int, RequestedPage>()

    /**
     * Title each list row showed, keyed by `/g/<id>/<hash>/`.
     *
     * A gallery page carries both the English `#gn` and the Japanese `#gj`
     * heading, while a row only shows the one the site renders for the account,
     * so the detail page matches the list it was opened from (issue #4).
     */
    private val listTitles = ConcurrentHashMap<String, String>()

    /** `/g/<id>/<hash>/` of a gallery URL: the same key for a row URL and for the page URL. */
    private fun galleryKey(url: String): String {
        val start = url.indexOf("/g/")
        return if (start >= 0) url.substring(start) else url
    }

    /**
     * Viewer page -> image URL, so re-reading a chapter or retrying a download does
     * not request the same viewer page again. Bounded and TTL'd (see
     * [ViewerImageCache]); the site's image URLs stay valid for hours.
     */
    private val imageUrlCache = ViewerImageCache()

    // ------------------------------------------------------------------
    // Popular
    // ------------------------------------------------------------------

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/popular", pageHeaders())

    override fun popularMangaParse(response: Response): MangasPage {
        val url = response.request.url.toString()
        val html = response.body.string()
        val mangas = parseMangaList(Jsoup.parse(html, url)).onEach { it.setUrlWithoutDomain(it.url) }
        mangas.forEach { listTitles[galleryKey(it.url)] = it.title }
        return MangasPage(mangas, false)
    }

    // ------------------------------------------------------------------
    // Search (cursor based: the server ignores `page=N` and returns
    // `var nexturl="..."` instead; the cursor of the previous request is
    // reused, keyed by the URL that produced it)
    // ------------------------------------------------------------------

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val searchUrl = buildSearchParams(baseUrl, query, filters).build().toString()
        // A plain browse of the source (blank query, no filters) shows popular.
        if (query.isBlank() && filters.hasNoActiveFilters()) {
            return popularMangaRequest(page)
        }
        val url = if (page <= 1) {
            searchUrl
        } else {
            requestedPages[page - 1]
                ?.takeIf { it.searchUrl == searchUrl }
                ?.url
                ?.let { nextPageCursors[it] }
                ?: searchUrl
        }
        requestedPages[page] = RequestedPage(searchUrl, url)
        return GET(url, pageHeaders())
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val url = response.request.url.toString()
        val html = response.body.string()
        val nextUrl = parseNextUrl(html)
        if (nextUrl != null) {
            nextPageCursors[url] = nextUrl
        } else {
            nextPageCursors.remove(url)
        }
        val mangas = parseMangaList(Jsoup.parse(html, url)).onEach { it.setUrlWithoutDomain(it.url) }
        mangas.forEach { listTitles[galleryKey(it.url)] = it.title }
        return MangasPage(mangas, hasNextPage(html))
    }

    // ------------------------------------------------------------------
    // Latest (not supported)
    // ------------------------------------------------------------------

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")

    // ------------------------------------------------------------------
    // Details & chapters (one gallery = one chapter)
    // ------------------------------------------------------------------

    override fun mangaDetailsParse(response: Response): SManga {
        val url = response.request.url.toString()
        val doc = Jsoup.parse(response.body.string(), url)
        return parseGalleryDetails(doc, SManga.create(), listTitles[galleryKey(url)], prefs.titleLanguage)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val url = response.request.url.toString()
        val doc = Jsoup.parse(response.body.string(), url)
        val chapter = SChapter.create().apply {
            name = "Full Gallery"
            chapter_number = 1f
            date_upload = parsePostedDate(doc)
            scanlator = null
        }
        chapter.setUrlWithoutDomain(url)
        return listOf(chapter)
    }

    // ------------------------------------------------------------------
    // Pages
    // ------------------------------------------------------------------

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.fromCallable {
        val galleryUrl = "$baseUrl${chapter.url}"
        val viewerUrls = ArrayList<String>()
        val seen = HashSet<String>()
        var thumbnailPage = 0
        var pageCount = -1
        var expectedThumbPages = -1
        val startedAt = System.currentTimeMillis()

        while (true) {
            val url = if (thumbnailPage == 0) galleryUrl else "$galleryUrl?p=$thumbnailPage"
            val html = fetchPageHtml(url)
            val doc = Jsoup.parse(html, url)
            if (thumbnailPage == 0) {
                pageCount = parsePageCount(doc)
                expectedThumbPages = if (pageCount > 0) {
                    (pageCount + Constants.THUMBNAILS_PER_PAGE - 1) / Constants.THUMBNAILS_PER_PAGE
                } else {
                    -1
                }
            }
            // Only new links count: if the site answers `?p=N` with the same page
            // again, the loop must stop instead of requesting up to the page cap.
            val newLinks = parseViewerLinks(doc).filter { seen.add(it) }
            viewerUrls.addAll(newLinks)
            thumbnailPage++

            if (pageCount > 0 && viewerUrls.size >= pageCount) break
            if (newLinks.isEmpty()) break
            if (thumbnailPage > Constants.MAX_THUMB_PAGES) break
            if (expectedThumbPages > 0 && thumbnailPage > expectedThumbPages) break
            if (System.currentTimeMillis() - startedAt > Constants.PAGE_LIST_TIME_BUDGET_MS) break
        }

        val preResolve = prefs.preResolveImages
        viewerUrls.mapIndexed { index, viewerUrl ->
            if (preResolve) {
                Page(index, url = viewerUrl, imageUrl = resolveImageUrl(Page(index, viewerUrl)))
            } else {
                Page(index, url = viewerUrl)
            }
        }
    }

    // Not used (fetchPageList is overridden), kept for the abstract API.
    override fun pageListParse(response: Response): List<Page> {
        val url = response.request.url.toString()
        val links = parseViewerLinks(Jsoup.parse(response.body.string(), url))
        return links.mapIndexed { index, viewerUrl -> Page(index, url = viewerUrl) }
    }

    /**
     * Resolves the image URL of a viewer page.
     *
     * Overriding the observable (instead of relying on [imageUrlRequest] /
     * [imageUrlParse]) puts every viewer-page request on the cached, throttled,
     * retrying path of [fetchPageHtml] — this is the request E-Hentai sees once
     * per image while reading or downloading.
     */
    override fun fetchImageUrl(page: Page): Observable<String> =
        Observable.fromCallable { resolveImageUrl(page) }

    // Kept for clients that drive the lower-level pair directly; the normal path
    // is fetchImageUrl above.
    override fun imageUrlRequest(page: Page): Request = GET(page.url, pageHeaders())

    override fun imageUrlParse(response: Response): String {
        val url = response.request.url.toString()
        val doc = Jsoup.parse(response.body.string(), url)
        val wantOriginal = prefs.wantOriginal && prefs.cookie.isNotEmpty()
        return parseImageUrl(doc, wantOriginal)
    }

    private fun resolveImageUrl(page: Page): String = resolveImageUrl(page.url)

    private fun resolveImageUrl(viewerUrl: String): String {
        val wantOriginal = prefs.wantOriginal && prefs.cookie.isNotEmpty()
        // Quality is part of the key: the same viewer page yields a different URL
        // for standard and original images.
        val cacheKey = (if (wantOriginal) "original|" else "standard|") + viewerUrl
        imageUrlCache.get(cacheKey)?.let { return it }

        val html = fetchPageHtml(viewerUrl)
        val doc = Jsoup.parse(html, viewerUrl)
        val imageUrl = parseImageUrl(doc, wantOriginal)
        imageUrlCache.put(cacheKey, imageUrl)
        return imageUrl
    }

    override fun imageRequest(page: Page): Request {
        val imageUrl = page.imageUrl
            ?: throw Exception(
                "No image URL resolved for ${page.url}. Open the chapter again so the " +
                    "source can resolve the image address first.",
            )
        val headers = Headers.Builder()
            .add("Referer", page.url)
            .build()
        return GET(imageUrl, headers)
    }

    // ------------------------------------------------------------------
    // URLs
    // ------------------------------------------------------------------

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // ------------------------------------------------------------------
    // Filters & preferences
    // ------------------------------------------------------------------

    override fun getFilterList(): FilterList = ehentaiFilterList()

    override fun setupPreferenceScreen(screen: PreferenceScreen) = prefs.setupPreferenceScreen(screen)

    // ------------------------------------------------------------------
    // Networking helpers
    // ------------------------------------------------------------------

    /**
     * Fetches a page-type resource (list / gallery / viewer) as a string.
     *
     * The request is throttled by the interceptor (every e-hentai.org /
     * exhentai.org request is), retried with backoff when the site answers
     * 429/503/509, and rejected when the body is one of the site's throttle
     * pages (IP ban / image quota / sad panda) — otherwise those pages parse as
     * "empty" and the user only sees a generic failure.
     */
    private fun fetchPageHtml(url: String): String {
        checkExhentaiAccess()
        var attempt = 0
        while (true) {
            val response = try {
                client.newCall(GET(url, pageHeaders())).execute()
            } catch (e: Exception) {
                throw Exception(networkErrorMessage(url, e.message ?: e.javaClass.simpleName), e)
            }
            var retryAfterMs = -1L
            try {
                if (!response.isSuccessful) {
                    if (Constants.isRetryableHttpCode(response.code) &&
                        attempt < Constants.MAX_REQUEST_RETRIES
                    ) {
                        retryAfterMs = retryDelayMs(response.header("Retry-After"), attempt)
                    } else {
                        throw Exception(httpErrorMessage(response.code, url))
                    }
                } else {
                    val html = response.body.string()
                    detectSiteBlock(html)?.let { throw Exception(siteBlockErrorMessage(it, url)) }
                    return html
                }
            } finally {
                // Close before backing off so a held connection is not kept during the sleep.
                response.close()
            }
            attempt++
            Thread.sleep(retryAfterMs)
        }
    }

    private fun pageHeaders(): Headers = Headers.Builder()
        .add("Referer", "$baseUrl/")
        .build()

    /** exhentai.org requires a login cookie; fail with a readable message instead of a bare 403. */
    private fun checkExhentaiAccess() {
        if (prefs.isExhentai() && prefs.cookie.isEmpty()) {
            throw Exception(
                "exhentai.org requires a login cookie. Fill in the 会员 ID / 密码哈希 " +
                    "(ipb_member_id / ipb_pass_hash) preferences in the source settings first.",
            )
        }
    }

}
