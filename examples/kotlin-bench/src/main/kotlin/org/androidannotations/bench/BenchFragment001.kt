package org.androidannotations.bench

import android.app.Fragment
import android.widget.TextView
import org.androidannotations.annotations.AfterViews
import org.androidannotations.annotations.EFragment
import org.androidannotations.annotations.FragmentArg
import org.androidannotations.annotations.FragmentByTag
import org.androidannotations.annotations.ViewById
import org.androidannotations.gradle.R

@EFragment(R.layout.fragment_main)
class BenchFragment001 : Fragment() {

    @FragmentArg
    protected lateinit var title: String

    @ViewById
    protected lateinit var fragmentText: TextView

    @FragmentByTag
    protected lateinit var byTagFragment: Fragment

    @AfterViews
    protected fun afterViews() {
        fragmentText.text = title
    }
}
