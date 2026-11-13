package org.androidannotations.bench

import android.app.Activity
import android.content.res.XmlResourceParser
import android.view.animation.Animation
import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.res.AnimationRes
import org.androidannotations.annotations.res.HtmlRes
import org.androidannotations.annotations.res.LayoutRes
import org.androidannotations.gradle.R

@EActivity(R.layout.bench_activity_103)
class BenchActivity103 : Activity() {

    @AnimationRes(R.anim.bench_anim)
    protected lateinit var benchAnim: Animation

    @HtmlRes(R.string.benchHtml)
    protected lateinit var benchHtml: CharSequence

    @LayoutRes(R.layout.bench_activity_103)
    protected lateinit var benchLayout: XmlResourceParser
}
