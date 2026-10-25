package mihon.core.common.extensions

import kotlinx.serialization.json.JsonObject

/**
 * Compile-only stand-in for Mihon's `JsonObject.EMPTY`, which the copied `SMangaImpl` /
 * `SChapterImpl` sources use to initialise their `memo` field.
 *
 * The app supplies the real implementation at runtime; nothing here ships in the APK.
 */
val JsonObject.Companion.EMPTY: JsonObject
    inline get() = JsonObject(emptyMap())
