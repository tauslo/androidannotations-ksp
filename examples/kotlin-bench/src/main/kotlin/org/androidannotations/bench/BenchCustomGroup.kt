package org.androidannotations.bench

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import org.androidannotations.annotations.AfterViews
import org.androidannotations.annotations.EViewGroup
import org.androidannotations.annotations.ViewById
import org.androidannotations.gradle.R

@EViewGroup(R.layout.bench_row)
class BenchCustomGroup(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    @ViewById
    protected lateinit var rowLabel: TextView

    @AfterViews
    protected fun afterRow() {
        rowLabel.text = "row"
    }
}
