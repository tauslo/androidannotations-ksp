package org.androidannotations.bench

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.View
import org.androidannotations.annotations.AfterViews
import org.androidannotations.annotations.Bean
import org.androidannotations.annotations.Click
import org.androidannotations.annotations.EView
import org.androidannotations.annotations.Receiver
import org.androidannotations.gradle.R

@EView
class BenchCustomView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    @Bean
    protected lateinit var bean: BenchBean103

    @Click(R.id.p1ButtonA)
    protected fun onP1Click() {
    }

    @AfterViews
    protected fun afterCustomView() {
    }

    @Receiver(actions = ["org.androidannotations.bench.VIEW"], registerAt = Receiver.RegisterAt.OnAttachOnDetach)
    protected fun onViewBroadcast(intent: Intent) {
    }
}
