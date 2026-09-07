package com.captasoluciones.signage.data.remote

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Both endpoints take a fully-qualified [Url] because the effective base URL is only
 * known at runtime (configured on-device), not at compile time. The Retrofit instance
 * itself is built with a harmless placeholder base URL that is always overridden.
 *
 * Bodies are read/written as raw okhttp3 types and parsed manually with kotlinx.serialization
 * in [com.captasoluciones.signage.data.repository.PlaylistRepository] so that empty
 * (304 Not Modified) bodies never trip up a converter.
 */
interface PlaylistApi {

    @GET
    suspend fun getPlaylist(@Url url: String): Response<ResponseBody>

    @Headers("Content-Type: application/json; charset=utf-8")
    @POST
    suspend fun postHeartbeat(@Url url: String, @Body body: RequestBody): Response<ResponseBody>
}
