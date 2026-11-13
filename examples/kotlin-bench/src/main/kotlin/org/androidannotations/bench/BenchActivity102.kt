package org.androidannotations.bench

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.res.BooleanRes
import org.androidannotations.annotations.res.ColorRes
import org.androidannotations.annotations.res.ColorStateListRes
import org.androidannotations.annotations.res.DimensionPixelOffsetRes
import org.androidannotations.annotations.res.DimensionPixelSizeRes
import org.androidannotations.annotations.res.DimensionRes
import org.androidannotations.annotations.res.DrawableRes
import org.androidannotations.annotations.res.IntegerRes
import org.androidannotations.annotations.res.StringArrayRes
import org.androidannotations.annotations.res.TextRes
import org.androidannotations.gradle.R

@EActivity(R.layout.bench_activity_102)
class BenchActivity102 : Activity() {

    @ColorRes(R.color.benchAccent)
    protected var benchColor: Int = 0

    @DrawableRes(R.drawable.icon)
    protected lateinit var benchDrawable: Drawable

    @ColorStateListRes(R.color.benchAccent)
    protected lateinit var benchColorStateList: ColorStateList

    @BooleanRes(R.bool.benchFlag)
    protected var benchBool: Boolean = false

    @IntegerRes(R.integer.benchNumber)
    protected var benchInt: Int = 0

    @StringArrayRes(R.array.benchStrings)
    protected lateinit var benchStringArray: Array<String>

    @TextRes(R.string.hello)
    protected lateinit var benchText: CharSequence

    @DimensionRes(R.dimen.benchPadding)
    protected var benchDimen: Float = 0f

    @DimensionPixelSizeRes(R.dimen.benchPadding)
    protected var benchPx: Int = 0

    @DimensionPixelOffsetRes(R.dimen.benchPadding)
    protected var benchPxOffset: Int = 0
}
