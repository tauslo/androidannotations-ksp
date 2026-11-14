package org.androidannotations.bench

import android.app.Service
import android.content.Intent
import android.os.IBinder
import org.androidannotations.annotations.AfterInject
import org.androidannotations.annotations.App
import org.androidannotations.annotations.Background
import org.androidannotations.annotations.Bean
import org.androidannotations.annotations.EService
import org.androidannotations.annotations.UiThread

@EService
class BenchService : Service() {

    @App
    lateinit var app: BenchApp

    @App
    fun injectApp(app: BenchApp) {
    }

    @Bean
    lateinit var bean103: BenchBean103

    @AfterInject
    fun afterInject() {
    }

    @UiThread
    fun uiWork() {
    }

    @Background
    fun bgWork() {
    }


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
