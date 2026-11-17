package org.androidannotations.bench

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.res.ColorStateListRes
import org.androidannotations.annotations.res.DrawableRes
import org.androidannotations.annotations.res.StringArrayRes
import org.androidannotations.annotations.res.TextRes
import org.androidannotations.gradle.R

@EActivity(R.layout.bench_activity_102)
class BenchActivity102 : Activity() {

    @DrawableRes(R.drawable.icon)
    protected lateinit var benchDrawable: Drawable

    @ColorStateListRes(R.color.benchAccent)
    protected lateinit var benchColorStateList: ColorStateList

    @StringArrayRes(R.array.benchStrings)
    protected lateinit var benchStringArray: Array<String>

    @TextRes(R.string.hello)
    protected lateinit var benchText: CharSequence
}
