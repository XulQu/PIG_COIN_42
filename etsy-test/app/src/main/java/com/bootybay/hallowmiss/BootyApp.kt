package com.bootybay.hallowmiss

import android.app.Application
import com.bootybay.hallowmiss.work.Notify

class BootyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notify.channels(this)
    }
}
