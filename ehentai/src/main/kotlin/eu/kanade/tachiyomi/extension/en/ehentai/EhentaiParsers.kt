package eu.kanade.tachiyomi.extension.en.ehentai

import eu.kanade.tachiyomi.extension.en.ehentai.Constants.GALLERY_COVER
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.GALLERY_DESCRIPTION
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.GALLERY_META_ROWS
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.GALLERY_PAGE_COUNT_TEXT
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.GALLERY_TAG_NAMESPACE
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.GALLERY_TAG_ROWS
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.GALLERY_TITLE_EN
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.GALLERY_TITLE_JP
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.GALLERY_UPLOADER
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.GALLERY_VIEWER_LINKS
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.LIST_COVER_SELECTOR
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.LIST_LINK_SELECTOR
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.LIST_ROW_SELECTOR
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.LIST_TAGS_SELECTOR
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.LIST_TITLE_SELECTOR
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.TITLE_LANGUAGE_EN
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.TITLE_LANGUAGE_LIST
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.VIEWER_IMAGE
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.VIEWER_ORIGINAL_LINK
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

// ---------------------------------------------------------------------------
// List pages (search results / popular / front page share one structure)
// ---------------------------------------------------------------------------

/**
 * Parses every gallery row of a list page into an [SManga].
 * URLs are absolute; the caller converts them with `setUrlWithoutDomain`.
 *
 * The [factory] seam exists for unit tests (the compile-only extensions-lib
 * stubs `SManga.create()`); production uses the default.
 */
fun parseMangaList(doc: Document, factory: () -> SManga = { SManga.create() }): List<SManga> {
    return doc.select(LIST_ROW_SELECTOR).mapNotNull { row -> parseMangaRow(row, factory()) }
}

private fun parseMangaRow(row: Element, manga: SManga): SManga? {
    val link = row.selectFirst(LIST_LINK_SELECTOR) ?: return null
    val title = row.selectFirst(LIST_TITLE_SELECTOR)?.text() ?: return null

    return manga.apply {
        url = link.absUrl("href")
        this.title = title
        thumbnail_url = parseCoverUrl(row)
        genre = parseListTags(row)
    }
}

/**
 * Cover of a list row: the `data-src` (lazy-loaded) or `src` attribute of the
 * cover image. Ignores placeholder GIFs and the small torrent/arrow icons.
 */
private fun parseCoverUrl(row: Element): String? {
    return row.select(LIST_COVER_SELECTOR).firstOrNull { img ->
        val src = img.attr("data-src").ifBlank { img.attr("src") }
        !src.startsWith("data:") &&
            !src.contains("/g/t.png") &&
            !src.contains("/g/td.png")
    }?.let { img -> img.attr("data-src").ifBlank { img.attr("src") } }
}

/** Inline tags of a list row: `div.gt` elements whose `title` is `namespace:tag`. */
private fun parseListTags(row: Element): String? {
    val tags = row.select(LIST_TAGS_SELECTOR).mapNotNull { it.attr("title").takeIf { t -> t.isNotBlank() } }
    return tags.joinToString(", ").ifEmpty { null }
}

// ---------------------------------------------------------------------------
// Pagination (cursor based: `var nexturl="..."` / `var prevurl="..."`)
// ---------------------------------------------------------------------------

private val NEXT_URL_REGEX = Regex("""var\s+nexturl\s*=\s*"([^"]*)"""")

/** The absolute URL of the next results page, or null on the last page. */
fun parseNextUrl(html: String): String? {
    return NEXT_URL_REGEX.find(html)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
}

fun hasNextPage(html: String): Boolean = !parseNextUrl(html).isNullOrEmpty()

// ---------------------------------------------------------------------------
// Gallery detail page
// ---------------------------------------------------------------------------

/**
 * Fills [manga] with the gallery page's metadata (title, cover, uploader, tags, …).
 *
 * [listTitle] is the title the results list showed for this gallery. The gallery
 * page carries both the English `#gn` and the Japanese `#gj` heading, while a row
 * shows only the one the site renders for the account, so the list decides which
 * heading the entry gets (issue #4). Without it — the entry is opened from the
 * library or by a deep link — the English heading wins, as in 1.4.4.
 *
 * [language] is the 标题语言 preference: [TITLE_LANGUAGE_LIST] follows [listTitle],
 * anything else forces that heading.
 */
fun parseGalleryDetails(
    doc: Document,
    manga: SManga,
    listTitle: String? = null,
    language: String = TITLE_LANGUAGE_LIST,
): SManga {
    val titleEn = doc.selectFirst(GALLERY_TITLE_EN)?.text()
    val titleJp = doc.selectFirst(GALLERY_TITLE_JP)?.text()
    manga.title = pickGalleryTitle(titleEn, titleJp, listTitle, language) ?: manga.title
    manga.thumbnail_url = parseGalleryCover(doc) ?: manga.thumbnail_url
    manga.author = parseUploader(doc)
    manga.genre = parseTags(doc)
    manga.description = parseDescription(doc)
    manga.status = SManga.UNKNOWN
    manga.initialized = true
    return manga
}

/**
 * Heading of the gallery page to use as the entry title.
 *
 * [language] forces a heading; [TITLE_LANGUAGE_LIST] (the default) instead picks
 * the one [listTitle] corresponds to, so that the detail page agrees with the
 * list it was opened from (issue #4). Falls back to the English heading.
 */
private fun pickGalleryTitle(titleEn: String?, titleJp: String?, listTitle: String?, language: String): String? {
    val en = titleEn?.let { normalizeTitle(it) }?.takeIf { it.isNotEmpty() }
    val jp = titleJp?.let { normalizeTitle(it) }?.takeIf { it.isNotEmpty() }
    if (language != TITLE_LANGUAGE_LIST) {
        return if (language == TITLE_LANGUAGE_EN) en ?: jp else jp ?: en
    }
    val listed = listTitle?.let { normalizeTitle(it) }?.takeIf { it.isNotEmpty() }
    if (listed == null) return en ?: jp
    if (jp != null && sameTitle(listed, jp)) return jp
    if (en != null && sameTitle(listed, en)) return en
    return en ?: jp
}

/** Collapses whitespace (including `&nbsp;`) so two renderings of a title compare equal. */
private fun normalizeTitle(text: String): String =
    text.replace('\u00a0', ' ').replace(Regex("\\s+"), " ").trim()

/** True when the listed title is this heading, possibly with decoration around it. */
private fun sameTitle(listed: String, heading: String): Boolean =
    listed == heading || listed.contains(heading) || heading.contains(listed)

/**
 * Cover of the gallery page. The new layout uses a CSS background image
 * (`#gd1 div[style*="url(...)"]`) instead of an `<img>`.
 */
fun parseGalleryCover(doc: Document): String? {
    val style = doc.selectFirst("$GALLERY_COVER div[style*=url]")?.attr("style") ?: return null
    return COVER_URL_REGEX.find(style)?.groupValues?.get(1)
}

private val COVER_URL_REGEX = Regex("""url\((https?://[^)]+)\)""")

/** Metadata table value, e.g. parseMeta(doc, "Posted") or parseMeta(doc, "Length"). */
fun parseMeta(doc: Document, name: String): String? {
    return doc.select(GALLERY_META_ROWS).firstOrNull { row ->
        row.selectFirst("td.gdt1")?.text()?.trimEnd(':')?.equals(name, ignoreCase = true) == true
    }?.selectFirst("td.gdt2")?.text()?.trim()
}

fun parseUploader(doc: Document): String? = doc.selectFirst(GALLERY_UPLOADER)?.text()

/** All tags as `namespace:tag` joined with ", " (without the namespace column headers). */
fun parseTags(doc: Document): String? {
    val parts = doc.select(GALLERY_TAG_ROWS).mapNotNull { row ->
        val namespace = row.selectFirst(GALLERY_TAG_NAMESPACE)?.text()?.trimEnd(':') ?: return@mapNotNull null
        val tag = row.select("td:eq(1) a").firstOrNull()?.text() ?: return@mapNotNull null
        "$namespace:$tag"
    }
    return parts.joinToString(", ").ifEmpty { null }
}

/**
 * `#gd2` contains the `#gn`/`#gj` headings and optionally a free-text
 * description; strip the headings to get the description itself.
 */
fun parseDescription(doc: Document): String? {
    val container = doc.selectFirst(GALLERY_DESCRIPTION) ?: return null
    var text = container.text().trim()
    doc.selectFirst(GALLERY_TITLE_EN)?.text()?.let { text = text.trim().removePrefix(it).trim() }
    doc.selectFirst(GALLERY_TITLE_JP)?.text()?.let { text = text.trim().removePrefix(it).trim() }
    return text.ifEmpty { null }
}

/** Posted date of the gallery, epoch millis; 0 when unparseable. */
fun parsePostedDate(doc: Document): Long = parseDateToEpoch(parseMeta(doc, "Posted"))

/**
 * Total page count of the gallery. Uses the "Length: N pages" metadata row,
 * falling back to the "Showing x - y of z images" footer.
 * Returns -1 when unknown.
 */
fun parsePageCount(doc: Document): Int {
    parseMeta(doc, "Length")?.let { text ->
        PAGE_COUNT_REGEX.find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
    }
    doc.selectFirst(GALLERY_PAGE_COUNT_TEXT)?.text()?.let { text ->
        SHOWING_REGEX.find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
    }
    return -1
}

private val PAGE_COUNT_REGEX = Regex("""(\d+)\s+pages""")
private val SHOWING_REGEX = Regex("""of\s+(\d+)\s+images""")

/** Viewer page URLs of one thumbnail page (`#gdt a[href*=/s/]`), absolute. */
fun parseViewerLinks(doc: Document): List<String> {
    return doc.select(GALLERY_VIEWER_LINKS).map { it.absUrl("href") }
}

// ---------------------------------------------------------------------------
// Viewer page (single image)
// ---------------------------------------------------------------------------

/**
 * Image URL of a viewer page. With [wantOriginal] and a present `/fullimg/`
 * link the original file URL is returned; otherwise the standard `img#img`
 * src. Throws when no image could be found (callers surface the error in
 * the reader, which then retries/aborts that page normally).
 */
fun parseImageUrl(doc: Document, wantOriginal: Boolean): String {
    if (wantOriginal) {
        doc.selectFirst(VIEWER_ORIGINAL_LINK)?.let { return it.absUrl("href") }
    }
    return doc.selectFirst(VIEWER_IMAGE)?.attr("src")
        ?: throw Exception("Could not find an image in the viewer page")
}

// ---------------------------------------------------------------------------
// Dates
// ---------------------------------------------------------------------------

private val DATE_FORMATS = arrayOf(
    "yyyy-MM-dd HH:mm", // current site format, e.g. "2026-08-15 05:47"
    "dd MMMM yyyy, HH:mm", // legacy format, e.g. "17 September 2024, 12:00"
)

/** Parses a site date string to epoch millis; 0L on failure. */
fun parseDateToEpoch(text: String?): Long {
    if (text.isNullOrBlank()) return 0L
    val value = text.trim()
    for (format in DATE_FORMATS) {
        try {
            return SimpleDateFormat(format, Locale.US).parse(value)?.time ?: 0L
        } catch (_: ParseException) {
            // try the next format
        }
    }
    return 0L
}
