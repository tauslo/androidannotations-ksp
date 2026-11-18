package org.androidannotations.bench

import android.app.Activity
import android.os.Bundle
import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.InstanceState
import org.androidannotations.gradle.R

@EActivity(R.layout.bench_activity_107)
class BenchActivity107 : Activity() {

    @InstanceState
    protected lateinit var stringProp: String

    @InstanceState
    protected lateinit var bundleProp: Bundle

    @InstanceState
    protected lateinit var intArrayProp: IntArray

    @InstanceState
    protected lateinit var stringArrayProp: Array<String>

    @InstanceState
    protected lateinit var stringListProp: ArrayList<String>
}
