package org.androidannotations.bench

import android.app.Fragment
import android.app.NotificationManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import org.androidannotations.annotations.EFragment
import org.androidannotations.annotations.SystemService
import org.androidannotations.gradle.R

@EFragment(R.layout.bench_fragment_111)
class BenchFragment111 : Fragment() {

    @SystemService
    protected lateinit var notificationManager: NotificationManager

    @SystemService
    protected lateinit var wifiManager: WifiManager

    @SystemService
    protected lateinit var powerManager: PowerManager

    @SystemService
    fun singleInjection(service: PowerManager) {
        println(service)
    }
}
