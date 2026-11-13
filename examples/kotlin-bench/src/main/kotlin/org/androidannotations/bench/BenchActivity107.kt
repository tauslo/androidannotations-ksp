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
    protected lateinit var intProp: Int

    @InstanceState
    protected lateinit var booleanProp: Boolean

    @InstanceState
    protected lateinit var floatProp: Float

    @InstanceState
    protected lateinit var longProp: Long

    @InstanceState
    protected lateinit var shortProp: Short

    @InstanceState
    protected lateinit var charProp: Char

    @InstanceState
    protected lateinit var byteProp: Byte

    @InstanceState
    protected lateinit var doubleProp: Double

    @InstanceState
    protected lateinit var bundleProp: Bundle

    @InstanceState
    protected lateinit var intArrayProp: IntArray

    @InstanceState
    protected lateinit var stringArrayProp: Array<String>

    @InstanceState
    protected lateinit var stringListProp: ArrayList<String>
}
