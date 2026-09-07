package com.captasoluciones.signage.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap

/**
 * Adds conditional-GET headers (If-None-Match / If-Modified-Since) based on the
 * ETag / Last-Modified headers seen on the previous 200 response for the same URL,
 * so an unchanged playlist comes back as a cheap 304 instead of a full re-download.
 *
 * The cache is a simple in-memory map (keyed by full request URL, including the
 * `deviceId` query parameter) living for the process lifetime. It intentionally is
 * not persisted to disk: on process restart we simply re-fetch once, which is
 * negligible network cost compared to the complexity of persisting HTTP cache
 * metadata correctly.
 */
class ETagCacheInterceptor(
    private val cache: ConcurrentHashMap<String, CacheMeta>
) : Interceptor {

    data class CacheMeta(val etag: String? = null, val lastModified: String? = null)

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val key = original.url.toString()
        val meta = cache[key]

        val requestBuilder = original.newBuilder()
        if (original.method == "GET") {
            meta?.etag?.let { requestBuilder.header("If-None-Match", it) }
            meta?.lastModified?.let { requestBuilder.header("If-Modified-Since", it) }
        }

        val response = chain.proceed(requestBuilder.build())

        if (response.code == 200) {
            val etag = response.header("ETag")
            val lastModified = response.header("Last-Modified")
            if (etag != null || lastModified != null) {
                cache[key] = CacheMeta(etag, lastModified)
            } else {
                cache.remove(key)
            }
        }

        return response
    }
}
