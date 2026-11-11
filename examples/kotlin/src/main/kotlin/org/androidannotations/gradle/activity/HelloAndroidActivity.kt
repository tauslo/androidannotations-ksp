package org.androidannotations.gradle.activity

import android.graphics.Point
import android.widget.Button
import android.widget.TextView
import org.androidannotations.annotations.AfterExtras
import org.androidannotations.annotations.AfterViews
import org.androidannotations.annotations.Background
import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.Extra
import org.androidannotations.annotations.UiThread
import org.androidannotations.annotations.ViewById
import org.androidannotations.annotations.res.StringRes
import org.androidannotations.gradle.R
import java.util.Date

@EActivity(R.layout.main)
class HelloAndroidActivity : HelloAndroidBase() {

    @StringRes
    protected lateinit var hello: String

    @ViewById
    protected lateinit var helloTextView: TextView

    @ViewById(resName = "launchButton")
    protected lateinit var launchButton: Button

    @Extra
    protected var myIntExtra: Int = 0

    @Extra
    protected var myStringExtra: String? = null

    @Extra
    protected var myIntsExtra: IntArray? = null

    @Extra
    protected var myPointExtra: Point? = null

    @Extra
    protected var myDateExtra: Date? = null

    @Extra
    protected var myPointsListExtra: ArrayList<Point>? = null

    @AfterViews
    protected fun afterViews() {
        computeDateBackground()
        launchButton.setOnClickListener {
            HelloAndroidActivity_.intent(this)
                .myIntExtra(42)
                .myStringExtra("hello from IntentBuilder_")
                .myPointExtra(Point(3, 7))
                .extraFromBase(7)
                .start()
        }
    }

    @AfterExtras
    protected fun onExtrasInjected() {
        // Runs after the extras above were restored (even when the intent bundle is empty).
    }

    @Background
    protected fun computeDateBackground() {
        val now = Date()
        val helloMessage = String.format(hello, now.toString())

        updateHelloTextView(helloMessage)
    }

    @UiThread
    protected fun updateHelloTextView(helloMessage: String) {
        helloTextView.text = helloMessage
    }
}
