package org.androidannotations.bench

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.FragmentById
import org.androidannotations.annotations.FragmentByTag
import org.androidannotations.gradle.R

@EActivity
class BenchAndroidXHost : FragmentActivity() {

    @FragmentById(R.id.childContainer)
    protected lateinit var childById: Fragment

    @FragmentByTag("p1Tag")
    protected lateinit var childByTag: Fragment
}
