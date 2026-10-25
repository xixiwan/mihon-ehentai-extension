package eu.kanade.tachiyomi.network

import okhttp3.OkHttpClient

/**
 * Compile-only stand-in for Mihon's `NetworkHelper`.
 *
 * The 1.6 API's `HttpSource` reads `network.client` and `network.defaultUserAgentProvider()`;
 * that is all this copy has to declare. The app supplies the real class at runtime (it is one
 * of the few types still exposed through Injekt, see Mihon's `MetroInjektRegistrar`).
 */
class NetworkHelper {

    val client: OkHttpClient
        get() = throw UnsupportedOperationException("App-provided at runtime")

    @Deprecated("The regular client handles Cloudflare by default")
    @Suppress("UNUSED")
    val cloudflareClient: OkHttpClient
        get() = client

    fun defaultUserAgentProvider(): String = throw UnsupportedOperationException("App-provided at runtime")
}
