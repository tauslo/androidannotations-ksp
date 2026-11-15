package org.androidannotations.bench

import android.app.Activity
import android.app.Fragment
import android.content.Context
import android.content.Intent
import android.view.Window
import android.widget.Button
import android.widget.TextView
import org.androidannotations.annotations.Bean
import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.FragmentById
import org.androidannotations.annotations.FromHtml
import org.androidannotations.annotations.Fullscreen
import org.androidannotations.annotations.NonConfigurationInstance
import org.androidannotations.annotations.Receiver
import org.androidannotations.annotations.ViewById
import org.androidannotations.annotations.ViewsById
import org.androidannotations.annotations.WindowFeature
import org.androidannotations.gradle.R

@EActivity(R.layout.bench_p1)
@Fullscreen
@WindowFeature(Window.FEATURE_NO_TITLE)
class BenchP1Activity : Activity() {

    @ViewsById(R.id.p1ButtonA, R.id.p1ButtonB)
    protected lateinit var p1Buttons: MutableList<Button>

    @ViewsById(R.id.p1ButtonA, R.id.p1ButtonB)
    protected lateinit var p1ButtonArray: Array<Button>

    @ViewById(R.id.p1Html)
    @FromHtml(R.string.benchP1Html)
    protected lateinit var p1Html: TextView

    @FragmentById(R.id.childContainer)
    protected lateinit var nativeChild: Fragment

    @NonConfigurationInstance
    protected var keptLabel: String? = null

    @NonConfigurationInstance
    @Bean
    protected lateinit var keptBean: BenchBean103

    @Receiver(actions = ["android.intent.action.VIEW"], dataSchemes = ["http"])
    protected fun onViewUrl(intent: Intent) {
    }

    @Receiver(actions = ["android.net.wifi.WIFI_STATE_CHANGED"], registerAt = Receiver.RegisterAt.OnResumeOnPause)
    protected fun onWifi(context: Context, @Receiver.Extra state: Int) {
    }
}
