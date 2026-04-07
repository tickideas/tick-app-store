package com.tickideas.appstore

import android.app.Application
import com.tickideas.appstore.data.AppRepository
import com.tickideas.appstore.data.AppStoreApi
import com.tickideas.appstore.download.DownloadHelper

class TickAppStore : Application() {

    val api: AppStoreApi by lazy {
        AppStoreApi.create(BuildConfig.API_BASE_URL)
    }

    val repository: AppRepository by lazy {
        AppRepository(api)
    }

    val downloadHelper: DownloadHelper by lazy {
        DownloadHelper(this)
    }
}
