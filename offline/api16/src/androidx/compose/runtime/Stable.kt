package androidx.compose.runtime

/**
 * Compile-only stand-in for the Compose annotation the 1.6 API puts on `FilterList`.
 *
 * The extension APK never ships this class: the app provides the real one. Keeping the
 * annotation here lets the copied `source-api` sources compile without pulling the whole
 * Compose runtime into an offline build.
 */
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.FIELD,
    AnnotationTarget.LOCAL_VARIABLE,
    AnnotationTarget.VALUE_PARAMETER,
)
annotation class Stable
