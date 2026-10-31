package com.universalconverter.pro

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class ConverterApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        container = AppContainer(this)
    }
}