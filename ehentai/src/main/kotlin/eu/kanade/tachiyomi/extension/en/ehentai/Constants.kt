package eu.kanade.tachiyomi.extension.en.ehentai

/**
 * Centralized constants for the E-Hentai extension.
 *
 * Site structure was verified against the live site on 2026-08-15
 * (see VERIFICATION.md at the project root for the full record and
 * any differences from older documentation).
 */
object Constants {

    const val DEFAULT_BASE_URL = "https://e-hentai.org"
    const val EXHENTAI_BASE_URL = "https://exhentai.org"

    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    // ------------------------------------------------------------------
    // Preference keys
    // ------------------------------------------------------------------
    const val PREF_DOMAIN = "domain"
    const val PREF_CUSTOM_DOMAIN = "custom_domain"

    // Login cookie split into three parts (since v1.4.3). Each part is
    // entered separately; the full `ipb_member_id=…; ipb_pass_hash=…;
    // igneous=…` string is assembled in EhentaiPreferences.
    const val PREF_MEMBER_ID = "member_id"
    const val PREF_PASS_HASH = "pass_hash"
    const val PREF_IGNEOUS = "igneous"

    /** Legacy combined-cookie preference (pre-1.4.3), migrated once on first access. */
    const val PREF_COOKIE_LEGACY = "cookie"

    const val PREF_USER_AGENT = "user_agent"
    const val PREF_IMAGE_QUALITY = "image_quality"
    const val PREF_PRE_RESOLVE_IMAGES = "pre_resolve_images"
    const val PREF_REQUEST_INTERVAL = "request_interval"
    const val PREF_TITLE_LANGUAGE = "title_language"

    // Domain preference values
    const val DOMAIN_EHENTAI = "e-hentai.org"
    const val DOMAIN_EXHENTAI = "exhentai.org"
    const val DOMAIN_CUSTOM = "custom"

    // Image quality preference values
    const val QUALITY_STANDARD = "standard"
    const val QUALITY_ORIGINAL = "original"

    // ------------------------------------------------------------------
    // Title-language preference values (which heading of the gallery page
    // becomes the entry title). The gallery page carries the English `#gn`
    // and the Japanese `#gj` heading; a results row shows only the one the
    // site renders for the account, and which one that is cannot be
    // requested — it is an account setting on the site.
    //
    // LIST (the default) keeps the entry in the language the row showed, so
    // the detail page agrees with the list it was opened from.
    // ------------------------------------------------------------------
    const val TITLE_LANGUAGE_LIST = "list"
    const val TITLE_LANGUAGE_EN = "en"
    const val TITLE_LANGUAGE_JP = "jp"

    /**
     * Label in front of the heading that did not become the entry title; that
     * heading is put into the description, where the library search finds it
     * (issue #4). Plain text on purpose — older clients render the description as
     * plain text, where Markdown markup would show up literally.
     */
    const val SUBTITLE_LABEL = "其他语言标题："

    // ------------------------------------------------------------------
    // Category bitmasks used by the `f_cats` search parameter.
    //
    // Verified 2026-08-15: `f_cats` is now an EXCLUSION mask — bits are set
    // for the categories the visitor has toggled OFF in the UI (the server
    // shows everything except those categories). To show a single category,
    // send `1023 xor bit`. Category bit values follow the category panel
    // ids on the site (cat_2 = Doujinshi, cat_4 = Manga, ...).
    // ------------------------------------------------------------------
    const val CAT_MISC = 1
    const val CAT_DOUJINSHI = 2
    const val CAT_MANGA = 4
    const val CAT_ARTIST_CG = 8
    const val CAT_GAME_CG = 16
    const val CAT_IMAGE_SET = 32
    const val CAT_COSPLAY = 64
    const val CAT_ASIAN_PORN = 128
    const val CAT_NON_H = 256
    const val CAT_WESTERN = 512
    const val CAT_ALL = 1023

    // ------------------------------------------------------------------
    // Gallery thumbnails per page on the gallery page (`?p=N` pagination)
    // ------------------------------------------------------------------
    const val THUMBNAILS_PER_PAGE = 20

    /** Safety cap for huge galleries (20_000 pages at 20/page). */
    const val MAX_THUMB_PAGES = 1000

    // ------------------------------------------------------------------
    // HTML selectors (centralized so a site redesign only touches this file)
    // ------------------------------------------------------------------
    const val LIST_ROW_SELECTOR = "table.itg tr:has(td.gl3c)"
    const val LIST_COVER_SELECTOR = "td.gl2c img"
    const val LIST_TITLE_SELECTOR = "a[href*=/g/] div.glink"
    const val LIST_LINK_SELECTOR = "a[href*=/g/]"
    const val LIST_TAGS_SELECTOR = "div.gt"
    const val LIST_POSTED_SELECTOR = "div[id^=posted_]:not([id^=postedpop_])"

    const val GALLERY_TITLE_EN = "#gn"
    const val GALLERY_TITLE_JP = "#gj"
    const val GALLERY_COVER = "#gd1"
    const val GALLERY_META_ROWS = "#gdd table tr"
    const val GALLERY_UPLOADER = "#gdn a[href*=/uploader/]"
    const val GALLERY_TAG_ROWS = "#taglist table tr"
    const val GALLERY_TAG_NAMESPACE = "td.tc"
    const val GALLERY_DESCRIPTION = "#gd2"
    const val GALLERY_VIEWER_LINKS = "#gdt a[href*=/s/]"
    const val GALLERY_PAGE_COUNT_TEXT = "p.gpc"

    const val VIEWER_IMAGE = "img#img"
    const val VIEWER_ORIGINAL_LINK = "a[href*=/fullimg/]"
}
