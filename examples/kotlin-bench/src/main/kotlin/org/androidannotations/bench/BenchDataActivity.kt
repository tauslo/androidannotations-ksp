package org.androidannotations.bench

import android.app.Activity
import android.app.Fragment
import android.content.Context
import android.widget.FrameLayout
import androidx.databinding.ViewDataBinding
import org.androidannotations.annotations.BindingObject
import org.androidannotations.annotations.CustomTitle
import org.androidannotations.annotations.DataBound
import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.EFragment
import org.androidannotations.annotations.EViewGroup
import org.androidannotations.gradle.R

@DataBound
@CustomTitle(R.layout.bench_seek)
@EActivity(R.layout.bench_seek)
class BenchDataActivity : Activity() {

    @BindingObject
    lateinit var binding: ViewDataBinding

    @BindingObject
    fun takeBinding(binding: ViewDataBinding) {
    }
}

@DataBound
@EFragment(R.layout.bench_seek)
class BenchDataFragment : Fragment()

@DataBound
@EViewGroup(R.layout.bench_seek)
class BenchDataGroup(context: Context) : FrameLayout(context)
