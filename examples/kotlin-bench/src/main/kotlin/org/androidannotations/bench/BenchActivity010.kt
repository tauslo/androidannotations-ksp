package org.androidannotations.bench

import android.app.Activity
import android.widget.TextView
import org.androidannotations.annotations.AfterViews
import org.androidannotations.annotations.Background
import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.UiThread
import org.androidannotations.annotations.ViewById
import org.androidannotations.annotations.res.StringRes
import org.androidannotations.gradle.R

@EActivity(R.layout.main)
class BenchActivity010 : Activity() {
    @StringRes
    protected lateinit var hello: String

    @ViewById
    protected lateinit var helloTextView: TextView

    @AfterViews
    protected fun afterViews() {
        computeDateBackground()
    }

    @Background
    protected fun computeDateBackground() {
        updateHelloTextView(hello)
    }

    @UiThread
    protected fun updateHelloTextView(helloMessage: String) {
        helloTextView.text = helloMessage
    }
}
