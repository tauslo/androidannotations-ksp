package org.androidannotations.bench

import android.app.Activity
import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.InstanceState
import org.androidannotations.gradle.R

@EActivity(R.layout.bench_activity_108)
class BenchActivity108 : Activity() {

    @InstanceState
    protected lateinit var parcelableProp: BenchParcelable

    @InstanceState
    protected lateinit var parcelableListProp: ArrayList<BenchParcelable>

    @InstanceState
    protected lateinit var parcelableArrayProp: Array<BenchParcelable>

    @InstanceState
    protected lateinit var serializableProp: BenchSerializable

    @InstanceState
    protected lateinit var serializableArrayProp: Array<BenchSerializable>

    @InstanceState
    protected lateinit var mapProp: HashMap<String, String>
}
