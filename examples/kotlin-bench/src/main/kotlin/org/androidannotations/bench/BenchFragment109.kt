package org.androidannotations.bench

import android.app.Fragment
import org.androidannotations.annotations.EFragment
import org.androidannotations.annotations.InstanceState
import org.androidannotations.gradle.R

@EFragment(R.layout.bench_fragment_109)
class BenchFragment109 : Fragment() {

    @InstanceState
    protected lateinit var stringProp: String

    @InstanceState
    protected lateinit var intProp: Int

    @InstanceState
    protected lateinit var stringListProp: ArrayList<String>
}
