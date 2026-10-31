package com.universalconverter.pro

import android.app.Application
import com.universalconverter.pro.ads.MonetizationManager
import com.universalconverter.pro.billing.BillingManager
import com.universalconverter.pro.core.convert.DeviceCaps
import com.universalconverter.pro.core.engine.ConversionEngine
import com.universalconverter.pro.data.ConversionQueue
import com.universalconverter.pro.data.DeviceCapsProbe
import com.universalconverter.pro.data.HistoryStore
import com.universalconverter.pro.data.SettingsStore
import com.universalconverter.pro.data.ThumbnailLoader
import com.universalconverter.pro.data.VaultStore

class AppContainer(application: Application) {
    val appContext: Application = application
    val settings = SettingsStore(application)
    val vault = VaultStore(application)
    val history = HistoryStore(application)
    val thumbnails = ThumbnailLoader(application)
    val caps: DeviceCaps = DeviceCapsProbe.probe()
    val engine = ConversionEngine(application, application.cacheDir)
    val queue = ConversionQueue(application, vault, history, settings, engine)
    val billing = BillingManager(application, settings)
    val monetization = MonetizationManager(application, settings)
}