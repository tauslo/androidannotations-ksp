package org.androidannotations.bench;

import android.app.Activity;

import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.InstanceState;
import org.androidannotations.annotations.res.BooleanRes;
import org.androidannotations.annotations.res.ColorRes;
import org.androidannotations.annotations.res.DimensionPixelOffsetRes;
import org.androidannotations.annotations.res.DimensionPixelSizeRes;
import org.androidannotations.annotations.res.DimensionRes;
import org.androidannotations.annotations.res.IntegerRes;
import org.androidannotations.gradle.R;

@EActivity(R.layout.bench_activity_102)
public class BenchActivity113 extends Activity {

    @ColorRes(R.color.benchAccent)
    protected int benchColor = 0;

    @BooleanRes(R.bool.benchFlag)
    protected boolean benchBool = false;

    @IntegerRes(R.integer.benchNumber)
    protected int benchInt = 0;

    @DimensionRes(R.dimen.benchPadding)
    protected float benchDimen = 0f;

    @DimensionPixelSizeRes(R.dimen.benchPadding)
    protected int benchPx = 0;

    @DimensionPixelOffsetRes(R.dimen.benchPadding)
    protected int benchPxOffset = 0;

    @InstanceState
    protected int intProp = 0;

    @InstanceState
    protected boolean booleanProp = false;

    @InstanceState
    protected float floatProp = 0f;

    @InstanceState
    protected long longProp = 0L;

    @InstanceState
    protected short shortProp = 0;

    @InstanceState
    protected char charProp = 0;

    @InstanceState
    protected byte byteProp = 0;

    @InstanceState
    protected double doubleProp = 0.0d;
}
