package org.androidannotations.bench

import android.app.Fragment
import org.androidannotations.annotations.EFragment
import org.androidannotations.annotations.FragmentArg
import org.androidannotations.gradle.R

@EFragment(R.layout.fragment_simple)
class BenchFragment002 : Fragment() {

    @FragmentArg("label")
    protected lateinit var label: String
}
