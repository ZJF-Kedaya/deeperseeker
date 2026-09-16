package com.deeperseeker.app

import android.app.Application
import com.deeperseeker.app.di.ServiceLocator

class DeeperSeekerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(applicationContext)
    }
}