package org.androidannotations.bench

import android.app.Fragment
import android.widget.Button
import org.androidannotations.annotations.AfterInject
import org.androidannotations.annotations.Background
import org.androidannotations.annotations.Bean
import org.androidannotations.annotations.EFragment
import org.androidannotations.annotations.FragmentArg
import org.androidannotations.annotations.UiThread
import org.androidannotations.annotations.ViewById
import org.androidannotations.gradle.R

@EFragment(R.layout.bench_fragment_112)
class BenchFragment112 : Fragment() {

    @ViewById
    protected lateinit var button: Button

    @FragmentArg
    protected lateinit var title: String

    @Bean
    protected lateinit var bean005: BenchBean005

    @Bean
    protected lateinit var bean102: BenchBean102

    @Bean
    fun singleInjection(bean: BenchBean006) {
        println(bean)
    }

    fun multiInjection(@Bean b7: BenchBean007, @Bean b8: BenchBean008) {
        println(b7)
        println(b8)
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
        button.text = title
    }
}
