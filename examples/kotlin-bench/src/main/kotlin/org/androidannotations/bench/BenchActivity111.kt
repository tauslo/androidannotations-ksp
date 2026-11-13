package org.androidannotations.bench

import android.app.Activity
import android.app.NotificationManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.SystemService
import org.androidannotations.annotations.res.StringRes
import org.androidannotations.gradle.R

@EActivity(R.layout.bench_activity_111)
class BenchActivity111 : Activity() {

    @StringRes(R.string.app_name)
    protected lateinit var appName: String

    @SystemService
    protected lateinit var notificationManager: NotificationManager

    @SystemService
    protected lateinit var wifiManager: WifiManager

    @SystemService
    protected lateinit var audioManager: AudioManager

    @SystemService
    protected lateinit var windowManager: WindowManager

    @SystemService
    protected lateinit var powerManager: PowerManager

    @SystemService
    protected lateinit var inputMethodManager: InputMethodManager

    @SystemService
    fun singleInjection(service: NotificationManager) {
        println(service)
    }

    fun multiInjection(@SystemService wifi: WifiManager, @SystemService audio: AudioManager) {
        println(wifi)
        println(audio)
    }
}
