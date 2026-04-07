package com.tickideas.appstore.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

// --- Response models ---

data class AppVersion(
    val versionName: String,
    val versionCode: Int,
    val apkSize: Long?,
    val releaseNotes: String?,
    val createdAt: String?
)

data class AppInfo(
    val id: String,
    val name: String,
    val packageName: String,
    val description: String?,
    val iconUrl: String?,
    val latestVersion: AppVersion?,
    val updatedAt: String?
)

data class AppListResponse(
    val apps: List<AppInfo>
)

data class AppVersionDetail(
    val id: String,
    val versionName: String,
    val versionCode: Int,
    val apkSize: Long?,
    val releaseNotes: String?,
    val createdAt: String?
)

data class AppDetail(
    val id: String,
    val name: String,
    val packageName: String,
    val description: String?,
    val iconUrl: String?,
    val versions: List<AppVersionDetail>,
    val createdAt: String?,
    val updatedAt: String?
)

data class StoreVersion(
    val versionName: String,
    val versionCode: Int
)

// --- Retrofit service ---

interface AppStoreApi {

    @GET("/api/apps")
    suspend fun getApps(): AppListResponse

    @GET("/api/apps/{id}")
    suspend fun getApp(@Path("id") id: String): AppDetail

    @GET("/api/store/version")
    suspend fun getStoreVersion(): StoreVersion

    companion object {
        fun create(baseUrl: String): AppStoreApi {
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AppStoreApi::class.java)
        }
    }
}
