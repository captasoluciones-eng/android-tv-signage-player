package com.captasoluciones.signage.data

import android.content.Context
import coil.ImageLoader
import com.captasoluciones.signage.data.local.DeviceDataStore
import com.captasoluciones.signage.data.local.EventLogBuffer
import com.captasoluciones.signage.data.remote.ETagCacheInterceptor
import com.captasoluciones.signage.data.remote.PlaylistApi
import com.captasoluciones.signage.data.repository.PlaylistRepository
import com.captasoluciones.signage.service.HeartbeatManager
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Hand-rolled dependency container (no DI framework needed for this app's scope).
 * Everything here is a process-wide singleton created once in [com.captasoluciones.signage.SignageApplication].
 */
class AppContainer(private val context: Context) {

    val eventLog = EventLogBuffer()

    val deviceDataStore = DeviceDataStore(context)

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val cacheMetaStore = ConcurrentHashMap<String, ETagCacheInterceptor.CacheMeta>()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(ETagCacheInterceptor(cacheMetaStore))
        .build()

    // The base URL configured here is a harmless placeholder: every call goes through
    // an @Url-annotated, fully-qualified URL built at runtime from the on-device
    // configured base URL, since that value is not known at compile time.
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://signage-placeholder.invalid/")
        .client(okHttpClient)
        .build()

    val playlistApi: PlaylistApi = retrofit.create(PlaylistApi::class.java)

    val playlistRepository = PlaylistRepository(playlistApi, json, eventLog)

    val heartbeatManager = HeartbeatManager(playlistApi, json, eventLog)

    val imageLoader: ImageLoader = ImageLoader.Builder(context)
        .crossfade(true)
        .build()
}
