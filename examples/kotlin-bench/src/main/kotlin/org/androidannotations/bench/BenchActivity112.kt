package org.androidannotations.bench

import android.app.Activity
import android.widget.Button
import org.androidannotations.annotations.AfterInject
import org.androidannotations.annotations.Background
import org.androidannotations.annotations.Bean
import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.UiThread
import org.androidannotations.annotations.ViewById
import org.androidannotations.gradle.R

@EActivity(R.layout.bench_activity_112)
class BenchActivity112 : Activity() {

    @ViewById
    protected lateinit var button: Button

    @Bean
    protected lateinit var bean001: BenchBean001

    @Bean
    protected lateinit var bean101: BenchBean101

    @Bean
    fun singleInjection(bean: BenchBean002) {
        println(bean)
    }

    fun multiInjection(@Bean b3: BenchBean003, @Bean b4: BenchBean004) {
        println(b3)
        println(b4)
    }

    @AfterInject
    protected fun afterInject() {
        println("after inject")
    }

    @Background
    protected fun computeDateBackground() {
        updateButton()
    }

    @UiThread
    protected fun updateButton() {
        button.text = "Bench 112"
    }
}
