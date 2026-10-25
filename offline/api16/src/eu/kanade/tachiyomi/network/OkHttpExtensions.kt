package eu.kanade.tachiyomi.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Compile-only stand-in for Mihon's `OkHttpExtensions.kt`.
 *
 * Only the entry points the 1.6 API sources reference are declared, with the same names and
 * signatures so the compiled extension links against the app's real class
 * (`eu.kanade.tachiyomi.network.OkHttpExtensionsKt`) at runtime.
 */

@Deprecated("Use suspend APIs instead")
fun Call.asObservableSuccess(): Observable<Response> =
    throw UnsupportedOperationException("App-provided at runtime")

suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation {
        try {
            cancel()
        } catch (_: Throwable) {
            // ignore
        }
    }

    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }
        },
    )
}

suspend fun Call.awaitSuccess(): Response {
    val response = await()
    if (!response.isSuccessful) {
        response.close()
        throw HttpException(response.code)
    }
    return response
}

fun OkHttpClient.newCachelessCallWithProgress(
    request: Request,
    listener: ProgressListener,
    existingSize: Long = 0L,
): Call = newCall(request)
