package eu.kanade.tachiyomi.extension.en.ehentai

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.DEFAULT_BASE_URL
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.DEFAULT_USER_AGENT
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.DOMAIN_CUSTOM
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.DOMAIN_EHENTAI
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.DOMAIN_EXHENTAI
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.EXHENTAI_BASE_URL
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.PREF_COOKIE_LEGACY
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.PREF_CUSTOM_DOMAIN
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.PREF_DOMAIN
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.PREF_IGNEOUS
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.PREF_IMAGE_QUALITY
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.PREF_MEMBER_ID
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.PREF_PASS_HASH
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.PREF_PRE_RESOLVE_IMAGES
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.PREF_REQUEST_INTERVAL
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.PREF_USER_AGENT
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.QUALITY_ORIGINAL
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.QUALITY_STANDARD
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.PREF_TITLE_LANGUAGE
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.TITLE_LANGUAGE_EN
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.TITLE_LANGUAGE_JP
import eu.kanade.tachiyomi.extension.en.ehentai.Constants.TITLE_LANGUAGE_LIST

/**
 * Thin wrapper over the source-scoped [SharedPreferences].
 *
 * Values are read on every access (no caching) so that preference changes
 * take effect without restarting the app.
 */
class EhentaiPreferences(private val preferences: SharedPreferences) {

    val baseUrl: String
        get() = when (preferences.getString(PREF_DOMAIN, DOMAIN_EHENTAI)) {
            DOMAIN_EXHENTAI -> EXHENTAI_BASE_URL
            DOMAIN_CUSTOM -> {
                val custom = preferences.getString(PREF_CUSTOM_DOMAIN, "")
                    ?.trim()
                    .orEmpty()
                    .removeSuffix("/")
                custom.ifEmpty { DEFAULT_BASE_URL }
            }
            else -> DEFAULT_BASE_URL
        }

    /** Raw preference value of the domain selector ("e-hentai.org" / "exhentai.org" / "custom"). */
    val domainValue: String
        get() = preferences.getString(PREF_DOMAIN, DOMAIN_EHENTAI) ?: DOMAIN_EHENTAI

    /** E-Hentai member id (`ipb_member_id`). Empty = not logged in. */
    val memberId: String
        get() = preferences.getString(PREF_MEMBER_ID, null)?.trim().orEmpty()

    /** E-Hentai password hash (`ipb_pass_hash`). Empty = not logged in. */
    val passHash: String
        get() = preferences.getString(PREF_PASS_HASH, null)?.trim().orEmpty()

    /** E-Hentai igneous cookie (optional for most accounts). */
    val igneous: String
        get() = preferences.getString(PREF_IGNEOUS, null)?.trim().orEmpty()

    /**
     * Full login cookie string assembled from the three parts, e.g.
     * `ipb_member_id=…; ipb_pass_hash=…; igneous=…`. Only non-empty parts
     * are included; empty when not logged in.
     */
    val cookie: String
        get() {
            migrateLegacyCookie()
            return listOf(
                memberId.takeIf { it.isNotEmpty() }?.let { "ipb_member_id=$it" },
                passHash.takeIf { it.isNotEmpty() }?.let { "ipb_pass_hash=$it" },
                igneous.takeIf { it.isNotEmpty() }?.let { "igneous=$it" },
            ).filterNotNull().joinToString("; ")
        }

    /**
     * One-time migration from the pre-1.4.3 combined `cookie` preference.
     * Runs before the parts are read; no-op once any of the three keys
     * exists (i.e. the user has already saved values via the new UI).
     */
    private fun migrateLegacyCookie() {
        if (preferences.contains(PREF_MEMBER_ID) || preferences.contains(PREF_PASS_HASH) || preferences.contains(PREF_IGNEOUS)) {
            return
        }
        val legacy = preferences.getString(PREF_COOKIE_LEGACY, null)?.trim().orEmpty()
        val match = LEGACY_COOKIE_REGEX.find(legacy) ?: return
        preferences.edit()
            .putString(PREF_MEMBER_ID, match.groupValues[1])
            .putString(PREF_PASS_HASH, match.groupValues[2])
            .apply()
        match.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }?.let {
            preferences.edit().putString(PREF_IGNEOUS, it.trim()).apply()
        }
        preferences.edit().remove(PREF_COOKIE_LEGACY).apply()
    }

    private val LEGACY_COOKIE_REGEX =
        Regex("""ipb_member_id\s*=\s*([^;\s]+)\s*;\s*ipb_pass_hash\s*=\s*([^;\s]+)(?:\s*;\s*igneous\s*=\s*([^;\s]+))?""")

    val userAgent: String
        get() = preferences.getString(PREF_USER_AGENT, DEFAULT_USER_AGENT)
            ?.trim()
            ?.ifBlank { DEFAULT_USER_AGENT }
            ?: DEFAULT_USER_AGENT

    /** Whether the user asked for original images (requires a valid login cookie). */
    val wantOriginal: Boolean
        get() = preferences.getString(PREF_IMAGE_QUALITY, QUALITY_STANDARD) == QUALITY_ORIGINAL

    val preResolveImages: Boolean
        get() = preferences.getBoolean(PREF_PRE_RESOLVE_IMAGES, false)

    /**
     * Which heading of the gallery page becomes the entry title (详情页/书库标题):
     * [TITLE_LANGUAGE_LIST] follows the language the results row showed,
     * [TITLE_LANGUAGE_EN] / [TITLE_LANGUAGE_JP] force one.
     */
    val titleLanguage: String
        get() = preferences.getString(PREF_TITLE_LANGUAGE, TITLE_LANGUAGE_LIST) ?: TITLE_LANGUAGE_LIST

    /** Delay between page-type requests (list / gallery / viewer), 0 = disabled. */
    val requestIntervalMs: Long
        get() = preferences.getString(PREF_REQUEST_INTERVAL, "0")?.toLongOrNull() ?: 0L

    fun isExhentai(): Boolean = domainValue == DOMAIN_EXHENTAI

    /**
     * True when the host (or any subdomain) belongs to the e-hentai family,
     * i.e. the only place the login cookie may be sent.
     */
    fun isSiteHost(host: String): Boolean {
        return host == DOMAIN_EHENTAI || host.endsWith(".$DOMAIN_EHENTAI") ||
            host == DOMAIN_EXHENTAI || host.endsWith(".$DOMAIN_EXHENTAI")
    }

    fun setupPreferenceScreen(screen: PreferenceScreen) {
        val context = screen.context

        ListPreference(context).apply {
            key = PREF_DOMAIN
            title = "站点域名 (Domain)"
            summary = "exhentai.org 需要先填写登录 Cookie，否则无法访问"
            entries = arrayOf("e-hentai.org（默认）", "exhentai.org（需登录 Cookie）", "自定义（镜像）")
            entryValues = arrayOf(DOMAIN_EHENTAI, DOMAIN_EXHENTAI, DOMAIN_CUSTOM)
            setDefaultValue(DOMAIN_EHENTAI)
        }.let { screen.addPreference(it) }

        EditTextPreference(context).apply {
            key = PREF_CUSTOM_DOMAIN
            title = "自定义域名 (Custom domain)"
            summary = "仅当域名选择「自定义」时生效，例如 https://example.com（不要带结尾斜杠）"
            dialogTitle = "自定义域名"
            setDefaultValue("")
        }.let { screen.addPreference(it) }

        EditTextPreference(context).apply {
            key = PREF_MEMBER_ID
            title = "会员 ID (Member ID)"
            summary = "ipb_member_id 的值。留空则不发送登录 Cookie。敏感信息仅保存在本机，请勿分享。"
            dialogTitle = "会员 ID (ipb_member_id)"
        }.let { screen.addPreference(it) }

        EditTextPreference(context).apply {
            key = PREF_PASS_HASH
            title = "密码哈希 (Pass Hash)"
            summary = "ipb_pass_hash 的值。exhentai.org 必填。"
            dialogTitle = "密码哈希 (ipb_pass_hash)"
        }.let { screen.addPreference(it) }

        EditTextPreference(context).apply {
            key = PREF_IGNEOUS
            title = "Ignéous Cookie（可选）"
            summary = "igneous 的值；大多数账号无需填写。"
            dialogTitle = "Ignéous (igneous)"
        }.let { screen.addPreference(it) }

        EditTextPreference(context).apply {
            key = PREF_USER_AGENT
            title = "User-Agent"
            summary = "默认使用浏览器 UA；若被 Cloudflare 拦截（403/503）可尝试更换"
            dialogTitle = "User-Agent"
            setDefaultValue(DEFAULT_USER_AGENT)
        }.let { screen.addPreference(it) }

        ListPreference(context).apply {
            key = PREF_TITLE_LANGUAGE
            title = "标题语言 (Title language)"
            summary = "详情页与书库用哪种标题：默认跟随搜索列表里显示的那种；也可强制英文或日语。" +
                "搜索列表本身由站点按账号设置渲染，插件改不了它"
            entries = arrayOf("跟随列表（默认）", "英文标题（#gn）", "日语标题（#gj）")
            entryValues = arrayOf(TITLE_LANGUAGE_LIST, TITLE_LANGUAGE_EN, TITLE_LANGUAGE_JP)
            setDefaultValue(TITLE_LANGUAGE_LIST)
        }.let { screen.addPreference(it) }

        ListPreference(context).apply {
            key = PREF_IMAGE_QUALITY
            title = "图片质量 (Image quality)"
            summary = "原图需要有效登录 Cookie；获取失败时自动回退标准图"
            entries = arrayOf("标准图（默认）", "原图")
            entryValues = arrayOf(QUALITY_STANDARD, QUALITY_ORIGINAL)
            setDefaultValue(QUALITY_STANDARD)
        }.let { screen.addPreference(it) }

        SwitchPreferenceCompat(context).apply {
            key = PREF_PRE_RESOLVE_IMAGES
            title = "预解析图片地址"
            summary = "进入阅读前就解析全部图片地址；大画廊会明显变慢，默认关闭"
            setDefaultValue(false)
        }.let { screen.addPreference(it) }

        ListPreference(context).apply {
            key = PREF_REQUEST_INTERVAL
            title = "请求间隔 (Request interval)"
            summary = "页面类请求（列表/详情/查看页）之间的等待时间，用于避免 429；图片下载不受影响"
            entries = arrayOf("无（默认）", "0.5 秒", "1 秒", "2 秒")
            entryValues = arrayOf("0", "500", "1000", "2000")
            setDefaultValue("0")
        }.let { screen.addPreference(it) }
    }
}
