package org.androidannotations.bench

import android.app.Activity
import android.view.View
import android.widget.Button
import org.androidannotations.annotations.Click
import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.LongClick
import org.androidannotations.annotations.ViewById
import org.androidannotations.gradle.R

@EActivity(R.layout.bench_activity_101)
class BenchActivity101 : Activity() {

    @Click(R.id.launchButton)
    protected fun launch() {
        BenchActivity101_.intent(this).start()
    }

    @Click
    protected fun noParam() {
    }

    @Click(R.id.clickViewButton)
    protected fun clickWithView(view: View) {
    }

    @Click
    protected fun casted(button: Button) {
    }

    @Click(value = [R.id.multiA, R.id.multiB])
    protected fun multiId() {
    }

    @Click(resName = ["resNameButton"])
    protected fun byResName() {
    }

    @LongClick
    protected fun longVoid() {
    }

    @LongClick
    protected fun longBoolean(view: View): Boolean {
        return true
    }

    @ViewById(R.id.methodTarget)
    protected lateinit var targetView: View

    @ViewById(R.id.methodTarget)
    protected fun onMethodTarget(view: Button) {
        println(view)
    }

    @ViewById
    protected fun onMethodFirst(view: View) {
        println(view)
    }

    @ViewById(R.id.secondTarget)
    protected fun onMethodSecond(view: Button) {
        println(view)
    }

    @ViewById(R.id.secondTarget)
    protected lateinit var secondView: View
}
