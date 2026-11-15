package org.androidannotations.bench

import android.app.Activity
import android.app.Fragment
import android.content.Context
import org.androidannotations.annotations.EBean
import org.androidannotations.annotations.RootContext
import org.androidannotations.annotations.RootFragment

@EBean
class BenchRootBean {

    @RootContext
    lateinit var context: Context

    @RootContext
    lateinit var activity: Activity

    @RootFragment
    lateinit var fragment: Fragment
}
