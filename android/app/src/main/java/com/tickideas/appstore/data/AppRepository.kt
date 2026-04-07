package com.tickideas.appstore.data

class AppRepository(private val api: AppStoreApi) {

    suspend fun getApps(): List<AppInfo> = api.getApps().apps

    suspend fun getApp(id: String): AppDetail = api.getApp(id)

    suspend fun getStoreVersion(): StoreVersion = api.getStoreVersion()

    fun getDownloadUrl(baseUrl: String, appId: String): String {
        return "$baseUrl/api/apps/$appId/download"
    }

    fun getVersionDownloadUrl(baseUrl: String, appId: String, versionId: String): String {
        return "$baseUrl/api/apps/$appId/download/$versionId"
    }
}
