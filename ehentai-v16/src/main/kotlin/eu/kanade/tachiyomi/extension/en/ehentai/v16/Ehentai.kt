package eu.kanade.tachiyomi.extension.en.ehentai.v16

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.en.ehentai.Constants
import eu.kanade.tachiyomi.extension.en.ehentai.EhentaiInterceptor
import eu.kanade.tachiyomi.extension.en.ehentai.EhentaiPreferences
import eu.kanade.tachiyomi.extension.en.ehentai.buildSearchParams
import eu.kanade.tachiyomi.extension.en.ehentai.ehentaiFilterList
import eu.kanade.tachiyomi.extension.en.ehentai.hasNextPage
import eu.kanade.tachiyomi.extension.en.ehentai.hasNoActiveFilters
import eu.kanade.tachiyomi.extension.en.ehentai.parseGalleryDetails
import eu.kanade.tachiyomi.extension.en.ehentai.parseImageUrl
import eu.kanade.tachiyomi.extension.en.ehentai.parseMangaList
import eu.kanade.tachiyomi.extension.en.ehentai.parseNextUrl
import eu.kanade.tachiyomi.extension.en.ehentai.parsePageCount
import eu.kanade.tachiyomi.extension.en.ehentai.parsePostedDate
import eu.kanade.tachiyomi.extension.en.ehentai.parseViewerLinks
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * E-Hentai (e-hentai.org) source, tachiyomix 1.6 build.
 *
 * The 1.6 API is the suspend one: instead of overriding the deprecated
 * `popularMangaRequest` / `popularMangaParse` pairs, the source implements
 *
 *  - [getPopularManga] / [getSearchManga] for the browse screens,
 *  - [getMangaUpdate] for details + chapter list (one gallery = one chapter),
 *  - [getPageList] and [getImageUrl] for reading.
 *
 * All parsing lives in [eu.kanade.tachiyomi.extension.en.ehentai.EhentaiParsers], which is
 * shared with the 1.4 build; only the API glue differs, so site fixes stay in one place.
 *
 * Site facts (verified 2026-08-15, see VERIFICATION.md):
 * - list/search/popular pages share one row structure (`table.itg tr`);
 * - search pagination is cursor based (`next`/`prev` parameters) — `page=N` is ignored
 *   by the server, so the cursor of the previous request is reused;
 * - the gallery page lists viewer URLs (`#gdt a[href*=/s/]`) with `?p=N` pagination;
 * - images are resolved lazily per page from the viewer page (`img#img`, original via
 *   the `/fullimg/` link when logged in).
 */
class Ehentai : HttpSource(), ConfigurableSource {

    override val name = "E-Hentai"
    override val lang = "en"
    override val supportsLatest = false

    /**
     * Deliberately the same as the 1.4 build's, so the generated source id matches and a
     * library entry keeps its source when the user switches builds.
     */
    override val versionId = 1

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", Context.MODE_PRIVATE)
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
     * The site ignores `page=N`, so page N+1 is requested with the cursor that page N
     * carried — and page N's URL is only known here, on the request side.
     */
    private data class RequestedPage(val searchUrl: String, val url: String)

    private val requestedPages = ConcurrentHashMap<Int, RequestedPage>()

    /**
     * Title each list row showed, keyed by `/g/<id>/<hash>/`.
     *
     * A gallery page carries both the English `#gn` and the Japanese `#gj` heading, while
     * a row only shows the one the site renders for the account, so the detail page can
     * match the list it was opened from (issue #4).
     */
    private val listTitles = ConcurrentHashMap<String, String>()

    /** `/g/<id>/<hash>/` of a gallery URL: the same key for a row URL and for the page URL. */
    private fun galleryKey(url: String): String {
        val start = url.indexOf("/g/")
        return if (start >= 0) url.substring(start) else url
    }

    /** Timestamp of the last page-type request, for the request-interval preference. */
    private val lastPageRequestAt = AtomicLong(0L)

    // ------------------------------------------------------------------
    // Popular
    // ------------------------------------------------------------------

    override suspend fun getPopularManga(page: Int): MangasPage = withContext(Dispatchers.IO) {
        val url = "$baseUrl/popular"
        val html = fetchPageHtml(url)
        val mangas = parseMangaList(Jsoup.parse(html, url)).onEach { it.setUrlWithoutDomain(it.url) }
        mangas.forEach { listTitles[galleryKey(it.url)] = it.title }
        MangasPage(mangas, false)
    }

    // ------------------------------------------------------------------
    // Search (cursor based: the server ignores `page=N` and returns
    // `var nexturl="..."` instead; the cursor of the previous request is
    // reused, keyed by the URL that produced it)
    // ------------------------------------------------------------------

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        withContext(Dispatchers.IO) {
            // A plain browse of the source (blank query, no filters) shows popular.
            if (query.isBlank() && filters.hasNoActiveFilters()) {
                return@withContext getPopularManga(page)
            }

            val searchUrl = buildSearchParams(baseUrl, query, filters).build().toString()
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

            val html = fetchPageHtml(url)
            val nextUrl = parseNextUrl(html)
            if (nextUrl != null) {
                nextPageCursors[url] = nextUrl
            } else {
                nextPageCursors.remove(url)
            }

            val mangas = parseMangaList(Jsoup.parse(html, url)).onEach { it.setUrlWithoutDomain(it.url) }
            mangas.forEach { listTitles[galleryKey(it.url)] = it.title }
            MangasPage(mangas, hasNextPage(html))
        }

    // ------------------------------------------------------------------
    // Latest (not supported)
    // ------------------------------------------------------------------

    override suspend fun getLatestUpdates(page: Int): MangasPage =
        throw UnsupportedOperationException("E-Hentai has no latest-updates feed")

    // ------------------------------------------------------------------
    // Details & chapters (one gallery = one chapter)
    // ------------------------------------------------------------------

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = withContext(Dispatchers.IO) {
        if (!fetchDetails && !fetchChapters) {
            return@withContext SMangaUpdate(manga, chapters)
        }

        val url = "$baseUrl${manga.url}"
        val doc = Jsoup.parse(fetchPageHtml(url), url)

        val updatedManga = if (fetchDetails) {
            parseGalleryDetails(doc, manga, listTitles[galleryKey(url)], prefs.titleLanguage)
        } else {
            manga
        }

        val updatedChapters = if (fetchChapters) {
            val chapter = SChapter.create().apply {
                name = "Full Gallery"
                chapter_number = 1f
                date_upload = parsePostedDate(doc)
                scanlator = null
            }
            chapter.setUrlWithoutDomain(url)
            listOf(chapter)
        } else {
            chapters
        }

        SMangaUpdate(updatedManga, updatedChapters)
    }

    // ------------------------------------------------------------------
    // Pages
    // ------------------------------------------------------------------

    override suspend fun getPageList(chapter: SChapter): List<Page> = withContext(Dispatchers.IO) {
        val galleryUrl = "$baseUrl${chapter.url}"
        val viewerUrls = ArrayList<String>()
        var thumbnailPage = 0
        var pageCount = -1

        while (true) {
            val url = if (thumbnailPage == 0) galleryUrl else "$galleryUrl?p=$thumbnailPage"
            val doc = Jsoup.parse(fetchPageHtml(url), url)
            if (thumbnailPage == 0) {
                pageCount = parsePageCount(doc)
            }
            val links = parseViewerLinks(doc)
            viewerUrls.addAll(links)
            thumbnailPage++

            if (pageCount > 0 && viewerUrls.size >= pageCount) break
            if (links.isEmpty()) break
            if (thumbnailPage > Constants.MAX_THUMB_PAGES) break
        }

        val preResolve = prefs.preResolveImages
        viewerUrls.mapIndexed { index, viewerUrl ->
            if (preResolve) {
                Page(index, url = viewerUrl, imageUrl = resolveImageUrl(viewerUrl))
            } else {
                Page(index, url = viewerUrl)
            }
        }
    }

    /**
     * Resolves the image URL of a viewer page.
     *
     * [Page.imageUrl] is left null by [getPageList] unless the 预解析 preference is on,
     * so the app calls this once per page while reading.
     */
    override suspend fun getImageUrl(page: Page): String = withContext(Dispatchers.IO) {
        resolveImageUrl(page.url)
    }

    private fun resolveImageUrl(viewerUrl: String): String {
        val doc = Jsoup.parse(fetchPageHtml(viewerUrl), viewerUrl)
        val wantOriginal = prefs.wantOriginal && prefs.cookie.isNotEmpty()
        return parseImageUrl(doc, wantOriginal)
    }

    override fun imageRequest(page: Page): Request {
        val headers = Headers.Builder()
            .add("Referer", page.url)
            .build()
        return GET(page.imageUrl!!, headers)
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
     * Applies the exhentai cookie check, the request-interval throttle and a page
     * Referer. Image downloads do NOT go through this helper.
     */
    private fun fetchPageHtml(url: String): String {
        checkExhentaiAccess()
        throttlePageRequest()
        return try {
            client.newCall(GET(url, pageHeaders())).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}")
                }
                response.body.string()
            }
        } catch (e: Exception) {
            throw Exception(
                "Failed to fetch $url (${e.message}). Check your network connection and the " +
                    "User-Agent / 登录 Cookie settings of the E-Hentai source.",
                e,
            )
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

    /**
     * Simple throttle between page-type requests (list / gallery / viewer).
     * The first request after a pause is not delayed; subsequent ones wait so that the
     * gap between request starts is at least the configured interval.
     */
    private fun throttlePageRequest() {
        val intervalMs = prefs.requestIntervalMs
        if (intervalMs <= 0L) return
        while (true) {
            val now = System.currentTimeMillis()
            val last = lastPageRequestAt.get()
            val waitMs = intervalMs - (now - last)
            if (waitMs <= 0L) {
                if (lastPageRequestAt.compareAndSet(last, now)) return
            } else {
                Thread.sleep(waitMs)
            }
        }
    }
}
