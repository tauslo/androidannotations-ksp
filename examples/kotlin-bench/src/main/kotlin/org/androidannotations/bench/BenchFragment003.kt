package org.androidannotations.bench

import android.app.Fragment
import android.widget.TextView
import org.androidannotations.annotations.AfterViews
import org.androidannotations.annotations.EFragment
import org.androidannotations.annotations.FragmentArg
import org.androidannotations.annotations.FragmentById
import org.androidannotations.annotations.ViewById
import org.androidannotations.gradle.R

@EFragment(R.layout.fragment_main)
class BenchFragment003 : Fragment() {

    @FragmentArg("count_key")
    protected lateinit var count: String

    @ViewById(R.id.fragmentText)
    protected lateinit var fragmentText: TextView

    @FragmentById(R.id.childContainer)
    protected lateinit var childFragment: Fragment

    @AfterViews
    protected fun afterViews() {
        fragmentText.text = "count: " + count
    }
}
