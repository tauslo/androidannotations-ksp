package org.androidannotations.bench

import android.app.Fragment
import android.view.View
import org.androidannotations.annotations.Click
import org.androidannotations.annotations.EFragment
import org.androidannotations.annotations.LongClick
import org.androidannotations.gradle.R

@EFragment(R.layout.bench_fragment_104)
class BenchFragment104 : Fragment() {

    @Click(R.id.fragBtnA)
    protected fun fragExplicit() {
    }

    @Click
    protected fun fragImplicit() {
    }

    @Click(R.id.fragBtnB)
    protected fun fragViewParam(view: View) {
    }

    @LongClick
    protected fun fragLongBool(view: View): Boolean {
        return true
    }
}
