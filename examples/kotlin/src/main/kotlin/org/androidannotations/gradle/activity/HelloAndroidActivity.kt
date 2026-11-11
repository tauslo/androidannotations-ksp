package org.androidannotations.gradle.activity

import android.graphics.Point
import android.widget.Button
import android.widget.TextView
import org.androidannotations.annotations.AfterExtras
import org.androidannotations.annotations.AfterViews
import org.androidannotations.annotations.Background
import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.EBean
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
        val greeting = HelloAndroidActivity_.Greeting_.getInstance_(this)
        android.util.Log.d("HelloAndroid", "nested @EBean text: " + greeting.message())
        val counter = HelloAndroidActivity_.Counter_.getInstance_(this)
        val deduped = counter === HelloAndroidActivity_.Counter_.getInstance_(this)
        android.util.Log.d("HelloAndroid", "activity-scoped @EBean count: " + counter.bump() + ", BeanHolder dedup: " + deduped)
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

    /** Nested @EBean: generated as a static nested class inside HelloAndroidActivity_ (APT parity). */
    @EBean
    class Greeting {

        @StringRes(resName = "hello")
        var text: String? = null

        fun message(): String = text ?: ""
    }

    /** Nested @EBean with Activity scope: getInstance_ performs a BeanHolder lookup on the generated activity (APT parity). */
    @EBean(scope = EBean.Scope.Activity)
    class Counter {

        var count: Int = 0

        fun bump(): Int {
            count++
            return count
        }
    }
}
